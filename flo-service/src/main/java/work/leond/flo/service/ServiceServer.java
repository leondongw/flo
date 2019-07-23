package work.leond.flo.service;

import static work.leond.flo.service.Util.in;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.AttributeKey;
import work.leond.flo.conf.Conf;
import work.leond.flo.conf.Util.BoolProp;
import work.leond.flo.conf.Util.IntProp;
import work.leond.flo.conf.Util.LongProp;
import work.leond.flo.conf.Util.Prop;
import work.leond.flo.conf.Util.PropertyReader;
import work.leond.flo.conf.Util.StrProp;
import work.leond.flo.service.Service.Status;
import work.leond.flo.service.Util.NamedThreadFactory;
import work.leond.flo.service.util.NamedTuple;

/**
 * ServiceServer is a container of services.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public final class ServiceServer {

  /*
   * ServiceServer class structure and relation.
   * 
   * Container  Service  Protocol     Port  Networker  Container
   *         1--n     1--n      n-----1  n--1       1--1
   * example:
   *     /----- A ------ 80.http  --- 80 -\
   *    /          \---- 81.http2 \ /      \
   *   C                           x- 81 --- N ------- C
   *    \          /---- 80.http  /        /
   *     \----- B ------ 82.http2 --- 82 -/
   */

  private static final Logger logger = LoggerFactory.getLogger(ServiceServer.class);

  /*
   * Part 1: container of service servers and networkers. Functions: 1 Manage
   * service servers. 2 Manage ports, convert messages, dispatch requests.
   */

  private Map<String, ServiceServer> servers;
  private Networker networker;
  private CopyOnWriteArrayList<FuncFilter> funcFilters;

  /* Part 1.1: Manage service servers. */

  /** New container of ServiceServers. */
  ServiceServer() {
    servers = new ConcurrentHashMap<String, ServiceServer>();
    networker = new Networker();
    funcFilters = new CopyOnWriteArrayList();

    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      public void run() {
        shutdown();
      }
    }, "ss_shutdown"));
  }

  /**
   * <p>
   * Start service if not started.
   * </p>
   * <p>
   * {service} must has one and only one interface, or
   * {@link #start(Object, Class)} should be used.
   * </p>
   * 
   * @param service An instance of service.
   */
  public <I> void start(I service) {
    start(service, null);
  }

  /**
   * Start service if not started.
   * 
   * @param service An instance of service.
   * @param itface  The interface of service. Can be null if service has only
   *                one interface.
   */
  public <I> void start(I service, Class<I> itface) {
    if (service == null) {
      throw new ServiceException("service is required");
    }

    if (itface == null) {
      Class<?>[] itfaces = service.getClass().getInterfaces();
      if (itfaces.length != 1) {
        throw new ServiceException(
            "service must has one and only one interface if not specified");
      }
      itface = (Class) itfaces[0];
    } else {
      if (!itface.isInterface()) {
        throw new ServiceException("itface must be a interface");
      }
    }

    serverOrNew(service, itface).start();
  }

  public void stop(String name) {
    ServiceServer server = serverByName(name);
    if (server == null) {
      logger.info("service to stop not found '{}'", name);
      return;
    }

    await(server.stop());
  }

  public void addFilter(FuncFilter funcFilter) {
    funcFilters.add(funcFilter);
  }

  private void shutdown() {
    // stop all servers
    List<Future> stopFutures = new ArrayList<>();
    for (ServiceServer server : servers.values()) {
      stopFutures.add(server.stop());
    }
    // wait all stop done
    for (Future stopFuture : stopFutures) {
      await(stopFuture);
    }
    // give some time to response
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {}

    // stop networker
    networker.shutdown();
  }

  private ServiceServer serverOrNew(Object service, Class<?> itface) {
    String name = Service.nameOf(itface);

    ServiceServer server = serverByName(name);
    if (server == null) {
      server = new ServiceServer(this, service, itface);
      ServiceServer server1 = servers.putIfAbsent(server.name, server);
      server = server1 == null ? server : server1;

    } else {
      server.service(service);
    }

    return server;
  }

  private ServiceServer serverByName(String name) {
    if (name == null || name.isEmpty()) {
      return null;
    }
    return servers.get(name);
  }

  private static <V> V await(Future<V> future) {
    try {
      return future.get();
    } catch (InterruptedException | ExecutionException e) {
      return null;
    }
  }


  /* Part 1.2: Manage ports, codec messages, dispatch requests. */

  /**
   * <p>
   * Reson portContexts use ConcurrentHashMap not HashMap:
   * Method get(key) can return null because table in HashMap may be empty
   * when resizing.
   * </p>
   */
  private static class Networker {

    private static final AttributeKey<PortContext> CHANNEL_ATTR_PORT_CONTEXT =
        AttributeKey.valueOf("flo.portContext");

    private ServerBootstrap bootstrap;
    private EventLoopGroup  acceptGroup;
    private EventLoopGroup  netGroup;
    private Map<Integer,PortContext> portContexts = new ConcurrentHashMap<>();


    public Networker() {
      try {
        acceptGroup = new NioEventLoopGroup(
            2,
            new NamedThreadFactory("ss_apt_"));
        netGroup = new NioEventLoopGroup(
            Math.max(1, Runtime.getRuntime().availableProcessors() * 2),
            new NamedThreadFactory("ss_net_"));

        bootstrap = new ServerBootstrap();
        final Dispatcher dispatcher = new Dispatcher();
        bootstrap.group(acceptGroup, netGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
              protected void initChannel(SocketChannel ch) throws Exception {

                PortContext portContext = portContexts.get(
                    ch.localAddress().getPort());
                if (portContext == null) {
                  // should not occur
                  ch.close();
                  return;
                }

                ch.attr(CHANNEL_ATTR_PORT_CONTEXT).set(portContext);

                ch.pipeline()
                    .addLast(portContext.protocol.serverHandlers())
                    .addLast(dispatcher);
              }
            })
            .option(ChannelOption.SO_REUSEADDR, true);

      } catch (Throwable e) {
        throw new ServiceException("Networker init fail", e);
      }
    }

    // Function: manage ports.

    public synchronized void add(Protocol protocol) {
      if (protocol == null) {return;}

      int port = protocol.port();
      PortContext portContext = portContexts.get(port);

      // create PortContext and bind port if none
      if (portContext == null) {
        portContext = new PortContext();
        portContext.port = port;
        portContext.protocol = Protocol.of(protocol.name());

        // bind port
        portContexts.put(port, portContext);
        try {
          portContext.channel =
              bootstrap.bind(portContext.port).sync().channel();

        } catch (Throwable e) {
          throw new ServiceException("Port bind fail", e);

        } finally {
          if (portContext.channel == null) {
            portContexts.remove(port);
          }
        }

      } else {
        // check must be same portocol
        if (! portContext.protocol.name().equals(protocol.name())) {
          throw new ServiceException(
              "Protocol conflict, " +
              portContext.port + " " + portContext.protocol.name() +
              " not " + protocol.name());
        }
      }

      portContext.protocols.add(protocol);
    }

    public synchronized void remove(Protocol protocol) {
      if (protocol == null) {return;}

      PortContext portContext = portContexts.get(protocol.port());
      if (portContext == null) {return;}

      portContext.protocols.remove(protocol);

      // remove PortConext and close channel if no protocols left
      if (portContext.protocols.isEmpty()) {
        portContexts.remove(portContext.port, portContext);
        try {
          portContext.channel.close();
        } catch (Throwable e) {
          logger.error("Networker close channel fail", e);
        }
      }
    }

    public void shutdown() {
      try {
        acceptGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        netGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
      } catch (Throwable e) {
        logger.warn("Networker shutdown fail ignored", e);
      }
    }

    // Function: codec messages, dispatch requests

    private void dispatch(Req req) {

      // Steps:
      // Step 1 check
      // Step 2 decode
      // Step 3 dispatch
      //
      // Reason of doing check in net thread is to fast fail before
      // go through request queue


      // Step 1 check
      if (req.func == null) {
        writeResp(req.resp().ex(ServiceException.NOT_FOUND));
        return;
      }

      // Step 2 decode
      try {
        req.protocol.decodeReq(req);
      } catch (Throwable e) {
        logger.error("Protocol decodeReq fail", e);
        writeResp(req.resp().ex(ServiceException.BAD_REQUEST));
        return;
      }

      // Step 3 dispatch
      req.protocol.serviceServer.dispatch(req);
    }

    private void beforeResp(Resp resp) {
      // encode resp
      try {
        resp.req().protocol.encodeResp(resp);
      } catch (Throwable e) {
        logger.error("Protocol encodeResp fail", e);
        resp.ex(ServiceException.INTERNAL_ERROR);
        return;
      }
    }


    private static final class PortContext {
      int            port;
      Protocol       protocol;
      List<Protocol> protocols = new CopyOnWriteArrayList<>();
      Channel        channel;
    }

    private final class Dispatcher extends AbstractCodec {

      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg)
          throws Exception {
  
        // pass none Req
        if (!(msg instanceof Req)) {
          ctx.fireChannelRead(msg);
          return;
        }
  
        // set Req channel
        Req req = (Req) msg;
        req.channel = ctx.channel();
  
        // set Req.protocol
        PortContext portContext =
            ctx.channel().attr(CHANNEL_ATTR_PORT_CONTEXT).get();
        if (req.serviceName() != null) {
          for (Protocol protocol : portContext.protocols) {
            if (protocol.serviceServer.accept(req)) {
              req.protocol = protocol;
              break;
            }
          }
        }
        if (req.protocol != null) {
          req.func = req.protocol.serviceServer.funcFor(req);
        } else {
          req.protocol = portContext.protocol;
        }
  
        dispatch(req);
      }
  
      @Override
      public void write(
          ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
          throws Exception {
  
        if (! (msg instanceof Resp)) {
          ctx.write(msg, promise);
          return;
        }
  
        beforeResp((Resp) msg);
  
        ctx.write(msg, promise);
      }
  
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Networker fail", cause);
        ctx.close();
      }
  
    }

  }










  /* Part 2: service server (that serves one service)
     Functions:
     1 serves service by protocols
     2 reg service to conf
     3 watch conf for service control
   */

  private static interface Meta {

    StrProp    shortName       = new StrProp   ("shortName");
    LongProp   stopReqDuration = new LongProp  ("stopReqDuration", 3000, 0);
    LongProp   stopRespTimeout = new LongProp  ("stopRespTimeout", -1, -1);
    IntProp    queueMax        = new IntProp   ("queueMax", -1, 0);
    ThreadProp threadMax       = new ThreadProp("threadMax", "5cpu", "1");
    BoolProp   logReq          = new BoolProp  ("logReq", true);
    BoolProp   logReqParam     = new BoolProp  ("logReqParam", true);

    class ThreadProp extends Prop<Integer> {

      private String dft;
      private String min;
      private String max;

      public ThreadProp(String key, String dft, String min) {
        this(key, dft, min, null);
      }

      public ThreadProp(String key, String dft, String min, String max) {
        super(key);
        this.dft = dft;
        this.min = min;
        this.max = max;
      }

      @Override
      protected Integer val(String strVal) {
        if (strVal == null || strVal.isEmpty()) {
          return valOf(dft);
        }

        Integer val = valOf(strVal);

        if (val == null) {
          logger.warn(
              "{} invalid value \"{}\", not valid, use default {}",
              key, strVal, dft);
          return valOf(dft);
        }

        Integer min = valOf(this.min);
        if (min != null && val < min) {
          logger.warn(
              "{} invalid value \"{}\", less than min, use min {}",
              key, strVal, this.min);
          return min;
        }

        Integer max = valOf(this.max);
        if (max != null && val > max) {
          logger.warn(
              "{} invalid value \"{}\", greater than max, use max {}",
              key, strVal, this.max);
          return max;
        }

        return val;
      }

      private static Integer valOf(String strVal) {
        if (strVal == null) {return null;}

        int multiple = 1;
        if (strVal.endsWith("cpu")) {
          strVal = strVal.substring(0, strVal.length() - 3);
          multiple = Runtime.getRuntime().availableProcessors();
        }

        int val = 0;
        try {
          val = Integer.parseInt(strVal);
        } catch (NumberFormatException e) {
          return null;
        }

        val = val * multiple;

        return val;
      }

    }

  }

  private static class StatusFuture implements Future<Boolean> {

    private CountDownLatch done = new CountDownLatch(1);
    private Boolean v;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return false;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public boolean isDone() {
      return done.getCount() == 0;
    }

    @Override
    public Boolean get() throws InterruptedException {
      done.await();
      return v;
    }

    @Override
    public Boolean get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      done.await(timeout, unit);
      return v;
    }

    public StatusFuture complete(boolean v) {
      this.v = v;
      done.countDown();
      return this;
    }

    public void awaitUninterruptibly() {
      boolean interrupted = false;

      while (!isDone()) {
        try {
          done.await();
        } catch (InterruptedException e) {
          // ignore interrupt
          interrupted = true;
        }
      }

      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }

  }

  private class StopThread extends Thread {

    StatusFuture stopFuture;

    StopThread(StatusFuture stopFuture) {
      super("ss_stop");
      setDaemon(true);

      this.stopFuture = stopFuture;
    }

    @Override
    public void run() {
      stopAsync(stopFuture);
    }

  }

  private class ExecuteTask implements Runnable {

    Req req;

    ExecuteTask(Req req) {
      this.req = req;
    }

    @Override
    public void run() {
      execute(req);
    }

  }

  private ServiceServer      container;
  private String             name;      // service name
  private Object             service;   // instance of service
  private Map<String,Func>   funcs;     // funcs by id
  //
  private AtomicInteger      status;
  private String             host;
  private Long               startTime;
  //
  private String             template;
  private String             shortName;
  private long               stopReqDuration;
  private long               stopRespTimeout;
  private int                queueMax;
  private int                threadMax;
  private boolean            logReq;
  private boolean            logReqParam;
  private List<Protocol>     protocols;
  //
  private StatusFuture       startFuture;
  private StatusFuture       stopFuture;
  private ThreadPoolExecutor executor;
  private AtomicLong         queueSize;


  private ServiceServer(
      ServiceServer container, Object service, Class<?> itface) {

    this.container = container;
    this.name      = Service.nameOf(itface);
    this.service   = service;
    this.funcs     = funcsOfItface(itface);

    status = new AtomicInteger(Status.stopped.id());
    startFuture = new StatusFuture().complete(false);
    stopFuture = new StatusFuture().complete(true);
  }

  private static Map<String,Func> funcsOfItface(Class<?> itface) {
    Method[] ms = itface.getMethods();
    Map<String,Func> funcs = new HashMap<>(ms.length + ms.length / 2 + 1);

    for (Method m : ms) {
      Func func = new Func();

      // name
      func.name(m.getName());

      // method
      func.method(m);

      // params
      Parameter[] mps = m.getParameters();
      NamedTuple.Element<?>[] paramDefines =
          new NamedTuple.Element<?>[mps.length];
      for (int i = 0; i < mps.length; i++) {
        Parameter mp = mps[i];
        paramDefines[i] = NamedTuple.Element.of(
            mp.getName(), mp.getParameterizedType());
      }
      func.params(new NamedTuple(paramDefines));

      // ret
      func.ret(new NamedTuple(NamedTuple.Element.of(
          null, m.getGenericReturnType())));

      // put into func
      funcs.put(func.name(), func);
    }

    return funcs;
  }

  /** Init by template of service from conf. */
  private synchronized void init() {
    // host = getIp;
    template = "base";

    PropertyReader pr = new PropertyReader(Conf.get(
      "flo.service/" + name + "/template/" + template));
    Properties p = pr.getProperties();

    shortName       = Meta.shortName      .val(p);
    stopReqDuration = Meta.stopReqDuration.val(p);
    stopRespTimeout = Meta.stopRespTimeout.val(p);
    queueMax        = Meta.queueMax       .val(p);
    threadMax       = Meta.threadMax      .val(p);
    logReq          = Meta.logReq         .val(p);
    logReqParam     = Meta.logReqParam    .val(p);

    // find ports from "procotol.{port}=xxx"
    Set<Integer> ports = new LinkedHashSet<>();
    for (Map.Entry pe : p.entrySet()) {
      String key = (String) pe.getKey();
      if (! key.startsWith("protocol.")) {
        continue;
      }
      int dotIndex = key.indexOf(".", 9);
      if (dotIndex >= 0) {
        continue;
      }
      Integer port = Util.str2int(key.substring(9), null);
      if (port == null) {
        continue;
      }
      ports.add(port);
    }

    // compose protocols
    protocols = new ArrayList<>(ports.size());
    for (int port : ports) {
      String protocolName = pr.get("protocol." + port);
      Protocol protocol = Protocol.of(protocolName);

      protocol.port = port;
      protocol.loadPropsFrom(p);
      if (! protocol.statusIsOn()) {
        continue;
      }
      protocol.serviceServer = this;

      protocols.add(protocol);
    }

    executor = new ThreadPoolExecutor(
        threadMax, threadMax, 1, TimeUnit.MINUTES,
        new LinkedBlockingQueue<>(), new NamedThreadFactory("ss_svc_"));
    queueSize = new AtomicLong();
  }

  private synchronized Future start() {
    while (!status.compareAndSet(Status.stopped.id(), Status.starting.id())) {
      if (in(status.get(), Status.starting.id(), Status.started.id())) {
        return startFuture;
      } else {
        // wait stopped, then start
        stopFuture.awaitUninterruptibly();
      }
    }

    try {
      init();

      logger.info("Service starting {}", name);

      for (Protocol p : protocols) {
        logger.info("Service protocol starting {} {} {}", name, p.port, p.name);
        container.networker.add(p);
      }

      startTime = System.currentTimeMillis();
      status.set(Status.started.id());
      logger.info("Service started {}", name);
      return startFuture = new StatusFuture().complete(true);

    } catch (Throwable e) {
      logger.error("Service start fail", e);

      // remove (started) protocols
      try {
        for (Protocol p : protocols) {
          container.networker.remove(p);
        }
      } catch (Throwable e2) {
        // should not occur
        logger.error("Service start rollback fail", e);
      } finally {
        status.set(Status.stopped.id());
      }

      return startFuture = new StatusFuture().complete(false);
    }
  }

  private synchronized Future stop() {
    while (!status.compareAndSet(Status.started.id(), Status.stoping.id())) {
      if (in(status.get(), Status.stoping.id(), Status.stopped.id())) {
        return stopFuture;

      } else {
        // wait for started, then stop
        startFuture.awaitUninterruptibly();
      }
    }

    logger.info("Service stoping {}", name);

    stopFuture = new StatusFuture();
    new StopThread(stopFuture).start();

    return stopFuture;
  }

  private void stopAsync(StatusFuture stopFuture) {
    try {
      if (stopReqDuration > 0) {
        sleepSilently(stopReqDuration);
      }

      for (Protocol p : protocols) {
        logger.info("Service protocol stoping {} {} {}", name, p.port, p.name);
        container.networker.remove(p);
      }

      if (stopRespTimeout == 0) {
        executor.shutdownNow();

      } else {
        executor.shutdown();

        if (stopRespTimeout < 0) {
          while (!executor.isTerminated()) {
            executor.awaitTermination(1, TimeUnit.MINUTES);
          }

        } else {
          executor.awaitTermination(stopRespTimeout, TimeUnit.MILLISECONDS);
          if (!executor.isTerminated()) {
            executor.shutdownNow();
          }
        }
      }

      logger.info("Service stopped {}", name);

    } catch (Throwable e) {
      // should not occur
      logger.error("Service stop fail", e);

    } finally {
      status.set(Status.stopped.id());
      stopFuture.complete(true);
    }
  }

  private void service(Object service) {
    if (service == this.service) {return;}
    this.service = service;
  }

  private boolean accept(Req<?,?> req) {
    if (!in(status.get(), Status.started.id(), Status.stoping.id())) {
      return false;
    }
    return shortName.equals(req.serviceName()) ||
        name.equals(req.serviceName());
  }

  private Func funcFor(Req<?,?> req) {
    return funcs.get(req.funcName());
  }

  private void dispatch(Req req) {
    try {
      // check queueMax
      boolean queueMaxReached = false;
      if (queueMax >= 0 && queueSize.get() >= queueMax) {
        queueMaxReached = true;
      } else if (queueSize.incrementAndGet() > queueMax) {
        queueMaxReached = true;
        queueSize.decrementAndGet();
      }
      if (queueMaxReached) {
        writeResp(req.resp().ex(ServiceException.TOO_MANY_REQUESTS));
        return;
      }

      executor.execute(new ExecuteTask(req));

    } catch (Throwable e) {
      logger.error("Service dispatch fail", e);
      writeResp(req.resp().ex(ServiceException.INTERNAL_ERROR));
    }
  }

  private void execute(Req req){
    // log req
    if (logReq) {
      if (logReqParam) {
        logger.info("req {}.{} {}", name, req.funcName(), req.params());
      } else {
        logger.info("req {}.{}", name, req.funcName());
      }
    }

    // prepare
    queueSize.decrementAndGet();
    Req.current(req);

    // funcFilters beforeFunc
    int funcFilterLen = container.funcFilters.size();
    for (int i = 0; i < funcFilterLen; i++) {
      FuncFilter funcFilter = container.funcFilters.get(i);
      try {
        funcFilter.beforeFunc(req);
      } catch (Throwable e) {
        logger.error("beforeFunc fail", e);
        writeResp(req.resp().ex(ServiceException.INTERNAL_ERROR));
        return;
      }
    }

    // execute
    try {
      Object ret = req.func.method().invoke(
          service, req.func.params().values());
      // TODO refine performance
      if (ret instanceof Future) {
        ret = ((Future)ret).get();
      }
      req.resp().ret(ret);

    } catch (Throwable e) {
      req.resp().ex(e);
    }

    // funcFilters afterFunc
    for (int i = funcFilterLen - 1; i >= 0; i--) {
      FuncFilter funcFilter = container.funcFilters.get(i);
      try {
        funcFilter.afterFunc(req.resp());
      } catch (Throwable e) {
        logger.error("afterFunc fail", e);
        writeResp(req.resp().ex(ServiceException.INTERNAL_ERROR));
        return;
      }
    }

    // log resp
    if (logReq) {
      if (logReqParam) {
        logger.info("resp {}.{} {} {}", name, req.funcName(), req.params(), req.resp().ret());
      } else {
        logger.info("resp {}.{} {}", name, req.funcName(), req.resp().ret());
      }
    }

    // write resp
    writeResp(req.resp());
  }

  private static void writeResp(Resp resp) {
    resp.req().channel.writeAndFlush(resp);
  }

  private static void sleepSilently(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      // silently
    }
  }

}
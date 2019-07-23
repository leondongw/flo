package work.leond.flo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandler;
import work.leond.flo.conf.Util.OptProp;

/**
 * Protocol hanles three thins:<br/>
 * 1 Encode / decode between bytes and protocol message.
 * 2 Encode / decode between protocol message and Req&Resp.
 * 3 Hold protocol informations of a service.
 *
 * @param <Q> class of request
 * @param <P> class of response
 */
@SuppressWarnings({ "rawtypes" })
abstract class Protocol<Q extends Req<Q, P>, P extends Resp<Q, P>> {

  private static final Logger logger = LoggerFactory.getLogger(Protocol.class);

  String             name;
  String             status;
  int                port;
  Map<String,String> props;
  // package scope
  ServiceServer serviceServer;


  protected Protocol(String name) {
    this.name = name;
  }

  public String name() {
    return name;
  }

  public int port() {
    return port;
  }

  public Protocol port(int port) {
    this.port = port;
    return this;
  }

  public String status() {
    return status;
  }

  public Protocol status(String status) {
    this.status = status;
    return this;
  }

  public String prop(String key) {
    return props != null ? props.get(key) : null;
  }

  /** Load props from properties. */
  Protocol loadPropsFrom(Properties ps) {
    String protocolPrefix = "protocol." + port + ".";
    status = Meta.status.val(ps, protocolPrefix);

    String propPrefix = protocolPrefix + "prop.";
    int propStartIndex = propPrefix.length();
    for (Map.Entry p : ps.entrySet()) {
      String key = (String) p.getKey();
      String val = (String) p.getValue();
      if (key == null) {continue;}

      if (key.startsWith(propPrefix)) {
        if (props == null) {
          props = new HashMap<>();
        }
        props.put(key.substring(propStartIndex), val);
      }
    }

    return this;
  }

  public boolean statusIsOn() {
    return Meta.Status.on.name().equals(status);
  }

  /** Thread-safe client handler handles Req to bytes and bytes to Resp. */
  public abstract ChannelHandler[] clientHandlers();

  /** Thread-safe server handler handles bytes to Req and Resp to bytes. */
  public abstract ChannelHandler[] serverHandlers();

  public abstract void encodeReq(Q req);

  public abstract void decodeReq(Q req);

  public abstract void encodeResp(P resp);

  public abstract void decodeResp(P resp);




  public static interface Name {
    String HTTP = "http";
  }

  private static interface Meta {

    enum Status {
      on, off
    }

    OptProp status = new OptProp("status", Status.on, Status.values());

  }

  /** Map of key: protocol name, value: protocol class. */
  private static final Map<String, Class<? extends Protocol>> protocolClasses =
      new ConcurrentHashMap<>();
  static {
    register(Name.HTTP, HttpProtocol.class);
  }

  public static void register(
      String protocol, Class<? extends Protocol> protocolClass) {
    protocolClasses.put(protocol, protocolClass);
  }

  /** Get a new instance of specified protocol. */
  static Protocol of(String name) throws ServiceException {
    Class<? extends Protocol> protocolClass = protocolClasses.get(name);
    if (protocolClass == null) {
      throw new ServiceException("Protocol unregistered " + name);
    }

    try {
      return protocolClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      throw new ServiceException("Protocol newInstance fail " + name, e);
    }
  }

}
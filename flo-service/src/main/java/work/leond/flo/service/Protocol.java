package work.leond.flo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.channel.ChannelHandler;
import work.leond.flo.conf.Util.OptProp;

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

  public Protocol setFrom(Properties allProps) {
    String protocolPrefix = "protocol." + port + ".";
    status = Meta.status.val(allProps, protocolPrefix);

    String propPrefix = protocolPrefix + "prop.";
    int propStartIndex = propPrefix.length();
    for (Map.Entry p : allProps.entrySet()) {
      String key = (String) p.getKey();
      String val = (String) p.getValue();
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




  private static interface Meta {

    enum Status {
      on, off
    }

    OptProp status = new OptProp("status", Status.on, Status.values());

  }

  public static interface Name {
    String HTTP = "http";
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
  public static Protocol of(String name) {
    Class<? extends Protocol> protocolClass = protocolClasses.get(name);
    if (protocolClass == null) {
      logger.info("Protocol unregistered {}", name);
      return null;
    }

    try {
      return protocolClass.newInstance();
    } catch (InstantiationException | IllegalAccessException e) {
      logger.error("Protocol newInstance fail " + name, e);
      return null;
    }
  }

}
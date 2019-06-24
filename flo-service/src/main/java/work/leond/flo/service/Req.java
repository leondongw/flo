package work.leond.flo.service;

import java.util.Map;
import java.util.TreeMap;

import io.netty.channel.ChannelHandlerContext;
import work.leond.flo.service.util.NamedTuple;

/**
 * An Req (request) is composed of:
 * <ul>
 * <li>service. It is the id or short id of service.</li>
 * <li>func. It is the function (method) name to call.</li>
 * <li>params. It is the named parameters for the function call.</li>
 * <li>headers. Usually it has some protocol related things like HTTP headers
 *     and framework related things user do not concern.</li>
 * <li>attrs. Attributes is a map for user and framework to store things
 *     in request lifetime.</li>
 * <li>resp. Response is generated when generating request.</li>
 * </ul>
 * 
 * Current request can be get by {@code Req.current()} .
 */
@SuppressWarnings("unchecked")
public abstract class Req<Q extends Req<Q,P>, P extends Resp<Q,P>> {

  private static ThreadLocal<Req<?,?>> threadLocal =
      new ThreadLocal<Req<?,?>>();

  /** Get current request. */
  public static Req<?,?> current() {
    return threadLocal.get();
  }

  static void current(Req<?,?> req) {
    threadLocal.set(req);
  }



  private P                     resp;
  private String                serviceName;
  private String                funcName;
  private NamedTuple            params;
  private Map<String,String>    headers = new TreeMap<String,String>();
  private Map<String,Object>    attrs   = new TreeMap<String,Object>();
  // package scope set
  Protocol<Q,P>         protocol;
  Func                  func;
  ChannelHandlerContext ctx;


  protected     Req() {}

  public P      resp() {
    return resp;
  }

  protected Q   resp(P resp) {
    this.resp = resp;
    return (Q) this;
  }

  public String serviceName() {
    return this.serviceName;
  }

  public Q      serviceName(String serviceName) {
    this.serviceName = serviceName;
    return (Q) this;
  }

  public String funcName() {
    return funcName;
  }

  public Q      funcName(String funcName) {
    this.funcName = funcName;
    return (Q) this;
  }

  /** Get request parameters. */
  public NamedTuple params() {
    return params;
  }

  /** set request parameters. */
  public Q          params(NamedTuple params) {
    this.params = params;
    return (Q) this;
  }

  /** Get request parameter value. */
  public Object param(String name) {
    return params.get(name);
  }

  /** Set request parameter value. */
  public Q      param(String name, Object value) {
    params.value(name, value);
    return (Q) this;
  }

  /** Get headers. */
  public Map<String,String> headers() {
    return headers;
  }

  /** Get header value. */
  public String header(String name) {
    return headers.get(name);
  }

  /** Set header value. */
  public Q      header(String name, String value) {
    headers.put(name, value);
    return (Q) this;
  }

  /** Get request attributes. */
  public Map<String,? extends Object> attrs() {
    return attrs;
  }

  /** Get attribute value. */
  public Object attr(String name) {
    return attrs.get(name);
  }

  /** Set attribute value. */
  public Q      attr(String name, Object value) {
    attrs.put(name, value);
    return (Q) this;
  }

  public Protocol<Q,P> protocol() {
    return protocol;
  }

  public Func   func() {
    return func;
  }

}

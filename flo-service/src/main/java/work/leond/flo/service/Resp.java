package work.leond.flo.service;

import java.util.Map;
import java.util.TreeMap;

/**
 * An Resp (response) is composed of:
 * <ul>
 * <li>ret. return of the function call.</li>
 * <li>headers. Usually it has some protocol related things like HTTP headers
 *     and framework related things user do not concern.</li>
 * <li>attrs. Attributes is a map for user and framework to store things
 *     in request lifetime.</li>
 * <li>req. the request.</li>
 * </ul>
 * 
 * Current response can be get by {@code Req.current().resp()} .
 */
@SuppressWarnings("unchecked")
public abstract class Resp<Q extends Req<Q,P>, P extends Resp<Q,P>> {

  private Q                  req;
  private Object             ret;
  private Throwable          ex;
  private Map<String,String> headers = new TreeMap<String,String>();
  private Map<String,Object> attrs   = new TreeMap<String,Object>();

  protected Resp(Q req) {
    this.req = req;
  }

  public Q      req() {
    return req;
  }

  public Object ret() {
    return ret;
  }

  public P      ret(Object ret) {
    this.ret = ret;
    return (P) this;
  }

  public Throwable ex() {
    return ex;
  }

  public P         ex(Throwable ex) {
    this.ex = ex;
    return (P) this;
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
  public P      header(String name, String value) {
    headers.put(name, value);
    return (P) this;
  }

  /** Get attributes. */
  public Map<String,Object> attrs() {
    return attrs;
  }

  /** Get attribute value. */
  public Object attr(String name) {
    return attrs.get(name);
  }

  /** Set attribute value. */
  public P      attr(String name, Object value) {
    attrs.put(name, value);
    return (P) this;
  }

}

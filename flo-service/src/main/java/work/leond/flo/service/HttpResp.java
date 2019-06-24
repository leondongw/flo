package work.leond.flo.service;

import java.nio.charset.Charset;

public class HttpResp extends Resp<HttpReq, HttpResp> {

  private String  content;
  private int     status = 200;
  private String  contentType;
  private Charset charset;
  private String  cors;

  public HttpResp(HttpReq req) {
    super(req);
  }

  public String content() {
    return content;
  }

  public HttpResp content(String content) {
    this.content = content;
    return this;
  }

  public int status() {
    return status;
  }

  public HttpResp status(int status) {
    this.status = status;
    return this;
  }

  public String contentType() {
    return contentType;
  }

  public HttpResp contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  public Charset charset() {
    return charset;
  }

  /**
   * Redundant from contentType. Just for better performance.
   * If charset is set, codec will use it instead of get from contentType.
   */
  public HttpResp charset(Charset charset) {
    this.charset = charset;
    return this;
  }

  public String cors() {
    return cors;
  }

  public HttpResp cors(String cors) {
    this.cors = cors;
    return this;
  }

}

package work.leond.flo.service;

import java.util.Map;
import java.util.TreeMap;


public class HttpReq extends Req<HttpReq,HttpResp> {

  String                   remoteIp;
  boolean                  isKeepAlive;
  String                   method;
  String                   path;
  String                   contentType;
  String                   content;
  Map<String,String>       namedContents;
  Map<String,HttpDataFile> files;


  public HttpReq() {
    super();
    resp(new HttpResp(this));
  }

  public boolean isKeepAlive() {
    return isKeepAlive;
  }

  public String method() {
    return method;
  }

  public String path() {
    return path;
  }

  public String contentType() {
    return contentType;
  }

  public String content() {
    return content;
  }

  /** contents in URL parameters or multipart contents. */
  public Map<String,String> namedContents() {
    return namedContents;
  }

  public Map<String,HttpDataFile> files() {
    return files;
  }


  void namedContentsEnsure() {
    if (namedContents == null) {
      namedContents = new TreeMap<String,String>();
    }
  }

  void filesEnsure() {
    if (files == null) {
      files = new TreeMap<String,HttpDataFile>();
    }
  }


  public static String remoteIpProxied(HttpReq req) {
    String ip = req.headers().get("X-Forwarded-For");
    if (ip == null || ip.isEmpty() || ip.equals("unknown")) {
      ip = req.header("Proxy-Client-IP");
    }
    if (ip == null || ip.isEmpty() || ip.equals("unknown")) {
      ip = req.header("WL-Proxy-Client-IP");
    }
    if (ip == null || ip.isEmpty() || ip.equals("unknown")) {
      ip = req.remoteIp;
    }

    if (ip == null || ip.isEmpty() || ip.equals("unknown")) {
      ip = "";
    } else {
      ip = ip.split(",")[0];
    }

    return ip.isEmpty() ? null : ip;
  }

}

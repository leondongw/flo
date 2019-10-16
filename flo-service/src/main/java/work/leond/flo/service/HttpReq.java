package work.leond.flo.service;

import java.util.Map;
import java.util.TreeMap;


/**
 * <pre>
 * path is URI path
 *
 * content is:
 * 1 HTTP body if content-type not form or multipart
 *
 * namedContents include:
 * 1 URI queries
 * 2 parameters for content-type application/x-www-form-urlencoded
 * 3 contents (except file) for content-type multipart/form-data
 *
 * files include:
 * 1 files for content-type multipart/form-data
 * </pre>
 */
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


  String namedContent(String key, String value) {
    if (namedContents == null) {
      namedContents = new TreeMap<>();
    }
    return namedContents.put(key, value);
  }

  HttpDataFile file(String key, HttpDataFile value) {
    if (files == null) {
      files = new TreeMap<>();
    }
    return files.put(key, value);
  }

}
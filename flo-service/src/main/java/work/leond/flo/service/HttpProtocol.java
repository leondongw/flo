package work.leond.flo.service;

import static work.leond.flo.service.Util.UTF8;
import static work.leond.flo.service.Util.v;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import work.leond.flo.service.util.NamedTuple;

class HttpProtocol extends Protocol<HttpReq, HttpResp> {

  private static final Logger logger =
      LoggerFactory.getLogger(HttpProtocol.class);
  private static final ClientCodec clientCodec = new ClientCodec();
  private static final ServerCodec serverCodec = new ServerCodec();


  public HttpProtocol() {
    super(Protocol.Name.HTTP);
  }

  @Override
  public ChannelHandler[] clientHandlers() {
    return new ChannelHandler[] {
      // TODO maybe write a sharable codec for less memory
      new HttpClientCodec(),
      new HttpObjectAggregator(1 * 1024 * 1024),
      clientCodec
    };
  }

  @Override
  public ChannelHandler[] serverHandlers() {
    return new ChannelHandler[] {
      // TODO maybe write a sharable codec for less memory
      new HttpServerCodec(),
      new HttpObjectAggregator(1 * 1024 * 1024),
      serverCodec
    };
  }

  @Override
  public void encodeReq(HttpReq req) {
  }

  @Override
  @SuppressWarnings({"rawtypes", "unchecked"})
  public void decodeReq(HttpReq req) {
    // support only json response for now

    Map contentMap = fromJson(req.content, Map.class);

    req.params(req.func.params().clone());
    for (int i = req.params().size() - 1; i >= 0; i--) {
      NamedTuple.Element param = req.params().get(i);

      if (req.namedContents != null &&
          req.namedContents.containsKey(param.name())) {
        param.value(fromJson(
            req.namedContents.get(param.name()), param.type()));

      } else if (contentMap != null && contentMap.containsKey(param.name())) {
        // TODO refine performance
        param.value(fromJson(
            toJson(contentMap.get(param.name())), param.type()));
      }
    }
  }

  @Override
  public void encodeResp(HttpResp resp) {
    // support only json response for now

    // content type
    if (resp.contentType() == null) {
      resp.contentType(ContentType.jsonUtf8());
      resp.charset(Util.UTF8);
    }

    // cors
    resp.cors(resp.req().protocol.prop("cors"));

    // non 200
    Throwable ex;
    if ((ex = resp.ex()) != null) {
      if (ex == ServiceException.BAD_REQUEST) {
        resp.status(400);
      } else if (ex == ServiceException.NOT_FOUND) {
        resp.status(404);
      } else if (ex == ServiceException.TOO_MANY_REQUESTS) {
        resp.status(429);
      } else {
        resp.status(500);
      }
      return;
    }

    // content
    if (resp.content() == null) {
      if (resp.ret() != null) {
        resp.content(toJson(resp.ret()));
      }
    }
  }

  @Override
  public void decodeResp(HttpResp resp) {
  }


  private static final class TypeRef extends TypeReference<Object> {

    private Type type;
    
    public TypeRef(Type type) {
      super();
      this.type = type;
    }

    public Type getType() {
      return type;
    }

  }

  private static final ObjectMapper Om = new ObjectMapper();
  static {
    // serial
    Om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    Om.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    Om.disable(SerializationFeature.WRITE_NULL_MAP_VALUES);
    Om.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
    Om.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    // deserial
    Om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @SuppressWarnings("unchecked")
  private static <T> T  fromJson(String json, Type type) {
    if (json == null) {return null;}

    if (String.class.equals(type)) {
      return (T) json;
    }

    if (json.isEmpty()) {return null;}

    try {
      return Om.readValue(json, new TypeRef(type));
    } catch (Exception e) {
      throw new ServiceException("fromJson fail", e);
    }
  }

  private static String toJson(Object obj) {
    try {
      return Om.writeValueAsString(obj);
    } catch (Exception e) {
      logger.error("toJson fail", e);
      return null;
    }
  }




  public static interface ContentType {
    static String json() {
      return "application/json";
    }

    static String json(Charset charset) {
      if (charset == null) {
        return json();
      }
      return "application/json; charset=" + charset.name();
    }

    static String jsonUtf8() {
      return "application/json; charset=utf-8";
    }
  }

  public static interface Prop {
    String cors = "cors";
  }

  private static class ClientCodec extends AbstractCodec {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msgObj)
        throws Exception {
    }

    @Override
    public void write(
        ChannelHandlerContext ctx, Object msgObj, ChannelPromise promise)
        throws Exception {
    }

  }

  private static class ServerCodec extends AbstractCodec {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msgObj)
        throws Exception {

      // pass non FullHttpRequest
      if (!(msgObj instanceof FullHttpRequest)) {
        ctx.fireChannelRead(msgObj);
        return;
      }

      // FullHttpRequest to HttpReq

      HttpReq req = new HttpReq();
      FullHttpRequest msg = (FullHttpRequest) msgObj;
      Charset msgCharset = HttpUtil.getCharset(msg, UTF8);

      try {
        req.remoteIp = ((InetSocketAddress) ctx.channel().remoteAddress())
            .getAddress().getHostAddress();
      } catch (Exception e) {
        // ignore class cast exception
      }

      req.isKeepAlive = HttpUtil.isKeepAlive(msg);
      req.method = msg.method().name();

      // path
      String uri = msg.uri();
      int uriQueryIndex = uri.indexOf("?");
      req.path = uriQueryIndex < 0 ? uri : uri.substring(0, uriQueryIndex);
      String uriQuery = uriQueryIndex < 0 ?
          null : uri.substring(uriQueryIndex + 1);

      // headers
      for (Map.Entry<String, String> e : msg.headers().entries()) {
        req.header(e.getKey(), e.getValue());
      }

      // content and named contents and files
      String contentType = msg.headers().get(HttpHeaderNames.CONTENT_TYPE);
      req.contentType = contentType;
      boolean isMultipart =
          contentType != null &&
          contentType.toLowerCase().startsWith("multipart/form-data");
      boolean isFormUrlencoded =
          "application/x-www-form-urlencoded".equalsIgnoreCase(contentType);
      // boolean isJson =
      //     contentType == null ||
      //     contentType.toLowerCase().startsWith(ContentType.json());

      // from path/URL parameters
      if (uriQuery != null && !uriQuery.isEmpty()) {
        if (!decodeUrlencoded(uriQuery, req, ctx.channel())) {
          return;
        }
      }

      if (isFormUrlencoded) {
        // for content-type application/x-www-form-urlencoded
        if (!decodeUrlencoded(
            msg.content().toString(UTF8), req, ctx.channel())) {
          return;
        }

      } else if (isMultipart) {
        // for content-type multipart/form-data
        HttpPostMultipartRequestDecoder decoder =
            new HttpPostMultipartRequestDecoder(
                new DefaultHttpDataFactory(false), msg);
        for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
          String dataName = URLDecoder.decode(data.getName(), "UTF-8");

          if (data.getHttpDataType() == HttpDataType.FileUpload) {
            FileUpload f = (FileUpload) data;
            ByteBuf fbyte = f.getByteBuf();
            int flen = (int) f.length();
            byte[] fcontent;

            if (fbyte.hasArray() &&
                fbyte.arrayOffset() == 0 &&
                fbyte.array().length == flen) {
              fcontent = fbyte.array();
            } else {
              fcontent = new byte[flen];
              fbyte.readerIndex(0);
              fbyte.readBytes(fcontent);
            }

            req.file(dataName, new HttpDataFile(
                f.getFilename(), f.getContentType(), fcontent));

          } else {
            Attribute data2 = (Attribute) data;
            req.namedContent(dataName, data2.getValue());
          }
        }
        decoder.destroy();

      } else {
        // for other content-types
        req.content = msg.content().toString(msgCharset);
      }

      msg.content().release();

      // decode Req service, func
      String path = req.path;
      if (path.startsWith("/")) { // should always be true
        path = path.substring(1);
      }
      int funcIndex = path.lastIndexOf("/");
      if (funcIndex >= 0) {
        req.serviceName(path.substring(0, funcIndex));
        req.funcName(path.substring(funcIndex + 1));
      } else {
        req.serviceName(path);
        req.funcName("");
      }

      // pass req to next channel handler
      ctx.fireChannelRead(req);
    }

    private static boolean decodeUrlencoded(
        String raw, HttpReq req, Channel channel) {

      if (raw == null || raw.isEmpty()) {
        return true;
      }

      try {
        String[] raws2 = raw.split("&");
        for (String raw2 : raws2) {
          String[] raws3 = raw2.split("=");
          String k = URLDecoder.decode(raws3[0], "UTF-8");
          String v = raws3.length > 1 ?
              URLDecoder.decode(raws3[1], "UTF-8") : "";
          req.namedContent(k, v);
        }
        return true;

      } catch (Exception e) {
        logger.warn("decodeUrlencoded fail", e);
        writeBadRequest(req, channel);
        return false;
      }
    }

    @Override
    public void write(
        ChannelHandlerContext ctx, Object msgObj, ChannelPromise promise)
        throws Exception {

      // pass non Resp
      if (! (msgObj instanceof HttpResp)) {
        ctx.write(msgObj, promise);
        return;
      }


      // HttpResp to HttpResponse

      HttpResp resp = (HttpResp) msgObj;
      HttpReq  req  = resp.req();

      // content
      FullHttpResponse httpResponse = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1,
          HttpResponseStatus.valueOf(resp.status()),
          Unpooled.copiedBuffer(v(resp.content(), ""), getCharset(resp)));

      // headers
      for (Map.Entry<String,String> e : resp.headers().entrySet()) {
        httpResponse.headers().set(e.getKey(), e.getValue());
      }
      if (req.isKeepAlive) {
        httpResponse.headers().set(
            HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      }
      if (resp.contentType() != null) {
        httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE,
            resp.contentType());
      }
      httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH,
        httpResponse.content().readableBytes());
      if (resp.cors() != null && !resp.cors().isEmpty()) {
        httpResponse.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN,
          resp.cors());
      }


      // write message and flush
      ctx.writeAndFlush(httpResponse);
    }

    private static void writeBadRequest(HttpReq req, Channel channel) {

      FullHttpResponse httpResponse = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1,
          HttpResponseStatus.BAD_REQUEST);

      if (req.isKeepAlive) {
        httpResponse.headers().set(
            HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      }

      channel.writeAndFlush(httpResponse);
    }

    private static Charset getCharset(HttpResp resp) {
      if (resp.charset() != null) {
        return resp.charset();
      }

      String contentType = resp.contentType();
      if (contentType == null) {
        return UTF8;
      }

      String charset = null;
      int    charsetIndex = contentType.indexOf("charset=");
      if (charsetIndex >= 0) {
        charsetIndex += 8;
      }

      if (charsetIndex >= 0 && contentType.length() > charsetIndex) {
        charset = contentType.substring(charsetIndex);
      }

      if (charset == null || charset.isEmpty() ||
          "UTF-8".equalsIgnoreCase(charset)) {
        return UTF8;
      }
      try {
        return Charset.forName(charset);
      } catch (Exception e) {
        logger.warn("No charset '" + charset +"', use UTF-8");
      }
      return UTF8;
    }

  }

}
package work.leond.flo.service;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

class Util {

  public static final class NamedThreadFactory implements ThreadFactory {

    private String nameBeforeId;
    private AtomicInteger id = new AtomicInteger();

    public NamedThreadFactory(String nameBeforeId) {
      this.nameBeforeId = nameBeforeId;
    }

    public Thread newThread(Runnable r) {
      return new Thread(r, nameBeforeId + id.incrementAndGet());
    }

  }

  public static final Charset UTF8 = Charset.forName("UTF-8");

  @SafeVarargs
  public static <T> T   v(T... vs) {
    for (T v : vs) {
      if (v != null) {return v;}
    }
    return null;
  }

  public static boolean in(int v, int... vs) {
    for (int v1 : vs) {
      if (v1 == v) {
        return true;
      }
    }
    return false;
  }

  public static Integer str2int(String str, Integer dft) {
    try {
      return Integer.parseInt(str);
    } catch (NumberFormatException e) {
      return dft;
    }
  }

  public static Boolean int2bool(Integer i) {
    if (i == null) {return null;}
    return i != 0;
  }

  public static int     usablePort() {
    try {
        ServerSocket serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        serverSocket.close();
        return port;
    } catch (IOException e) {
        return 0;
    }
  }

}
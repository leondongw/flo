package work.leond.flo.conf;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * <pre>
 * Standalone Configuration.
 * 
 * source priority:
 * 1 system property
 * 2 system env
 * 3 file
 * 4 classpath file
 * </pre>
 */
final class StandaloneConfer implements Confer {


  private static final String KEY_FILE           = "flo.conf.file";
  private static final String KEY_CLASSPATH_FILE = "flo.conf.classpathFile";
  private static final String DFT_VAL_FILE           = "flo.conf";
  private static final String DFT_VAL_CLASSPATH_FILE = "flo.conf";

  private static SortedMap<String,String> props;
  static {init();}




  public List<Kv> range(String key, String rangeEnd, Watcher watcher) {
    return range(key, rangeEnd);
    //do not support watch
  }

  public void watchStop(Watcher watcher) {
    //do not support watch
  }

  public void set(List<Kv> kvs, boolean expireWhenDisconnect) {
    // do not support set
  }




  private static void init() {
    String file = floConf(KEY_FILE, DFT_VAL_FILE);
    String classpathFile = floConf(KEY_CLASSPATH_FILE, DFT_VAL_CLASSPATH_FILE);

    props = new TreeMap<String,String>(System.getenv());
    propsAdd(props, propsOfFile(file));
    propsAdd(props, propsOfClasspathFile(classpathFile));
  }

  private static String floConf(String key, String defaultValue) {
    String v = System.getProperty(key);
    if (v != null) {return v;}

    v = System.getenv(key);
    if (v != null) {return v;}

    return defaultValue;
  }

  private static void propsAdd(SortedMap<String,String> props, Map<String,String> ps) {
    for (Map.Entry<String,String> e : ps.entrySet()) {
      props.putIfAbsent(e.getKey(), e.getValue());
    }
  }
  
  private static Map<String,String> propsOfFile(String path) {
    try {
      return propsOf(new FileInputStream(path));
    } catch (IOException e) {
      return Collections.emptyMap();
    }
  }

  private static Map<String,String> propsOfClasspathFile(String path) {
    return propsOf(Conf.class.getClassLoader().getResourceAsStream(path));
  }

  @SuppressWarnings("unchecked")
  private static Map<String,String> propsOf(InputStream in) {
    Properties p = new Properties();

    if (in != null) {
      try {
        p.load(in);
      } catch (IOException e) {}
    }
    
    return (Map<String,String>)(Map<?,?>)p;
  }

  private static boolean  keyIsInvalid(String key) {
    return key == null || key.isEmpty();
  }

  private static List<Kv> range(String key, String rangeEnd) {
    if (key == null) {return Collections.emptyList();}

    //single key
    if (keyIsInvalid(rangeEnd)) {
      String v = single(key);
      if (v == null) {return Collections.emptyList();}
      return Arrays.asList(new Kv[]{new Kv(key, v)});
    }

    //range keys
    ArrayList<Kv> kvs = new ArrayList<Kv>();
    boolean started   = Conf.keyOfInfinite().equals(key);
    boolean endAtLast = Conf.keyOfInfinite().equals(rangeEnd);
    
    for (Entry<String,String> e : props.entrySet()) {
      if (!started) {
        started = e.getKey().compareTo(key) >= 0;
        if (!started) {continue;}
      }

      if (!endAtLast) {
        if (e.getKey().compareTo(rangeEnd) >= 0) {break;}
      }

      if (e.getValue() != null) {
        kvs.add(new Kv(e.getKey(), e.getValue()));
      }
    }

    return kvs;
  }

  private static String   single(String key) {
    if (key == null) {return null;}
    return props.get(key);
  }

}

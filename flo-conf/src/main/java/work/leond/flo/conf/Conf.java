package work.leond.flo.conf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * <pre>
 * Configuration.
 * 
 * source priority:
 * 1 etcd            (can watch changes)
 * 2 system env      (do not watch changes)
 * 3 file            (do not watch changes)
 * 4 classpath file  (do not watch changes)
 * </pre>
 */
public final class Conf {

  private static Confer standaloneConfer = new StandaloneConfer();
  private static Confer etcdConfer = new EtcdConfer();


  private static class KvComparator implements Comparator<Kv> {

    public int compare(Kv o1, Kv o2) {
      boolean o1Null = o1 == null || o1.getKey() == null;
      boolean o2Null = o2 == null || o2.getKey() == null;

      if (o1Null && o2Null) {
        return 0;
      }
      if (o1Null) {
        return -1;
      }
      if (o2Null) {
        return 1;
      }
      return o1.getKey().compareTo(o2.getKey());
    }

  }
  private static KvComparator kvComparator = new KvComparator();


  // key

  public  static String   keyOfInfinite() {
    return "\0";
  }

  public  static String   rangeEnd(String key) {
    if (key == null) {return null;}
    if (key.isEmpty()) {return "";}
    int len = key.length();
    return key.substring(0, len - 1) + (char)(key.charAt(len - 1) + 1);
  }


  // get

  public  static List<Kv> range(String key, String rangeEnd, Watcher watcher) {
    List<Kv> kvsLocal = standaloneConfer.range(key, rangeEnd, null);
    List<Kv> kvsEtcd = etcdConfer.range(key, rangeEnd, watcher);

    // merge

    if (kvsEtcd.isEmpty()) {
      return kvsLocal;
    }
    if (kvsLocal.isEmpty()) {
      return kvsEtcd;
    }

    TreeSet<Kv> kvSet = new TreeSet<Kv>(kvComparator);
    for (Kv kv : kvsLocal) {
      kvSet.add(kv);
    }
    for (Kv kv : kvsEtcd) {
      kvSet.add(kv);
    }
    return new ArrayList<Kv>(kvSet);
  }

  public  static List<Kv> range(String key, String rangeEnd) {
    return range(key, rangeEnd, null);
  }

  public  static String   get(String key, String defaultValue, Watcher watcher) {
    List<Kv> kvs = range(key, null, watcher);
    String v = kvs.isEmpty() ? null : kvs.get(0).getValue();
    return v != null ? v : defaultValue;
  }

  public  static String   get(String key, String defaultValue) {
    return get(key, defaultValue, null);
  }

  public  static String   get(String key) {
    return get(key, null);
  }

  public  static Integer  getInt(String key, Integer defaultValue) {
    return toInt(get(key), defaultValue);
  }

  public  static Integer  getInt(String key) {
    return getInt(key, null);
  }

  public  static Long     getLong(String key, Long defaultValue) {
    return toLong(get(key), defaultValue);
  }

  public  static Long     getLong(String key) {
    return getLong(key, null);
  }

  public  static void     watchStop(Watcher watcher) {
    standaloneConfer.watchStop(watcher);
    etcdConfer.watchStop(watcher);
  }


  // set

  public  static void     set(List<Kv> kvs, boolean expireWhenDisconnect) {
    etcdConfer.set(kvs, expireWhenDisconnect);
  }

  public  static void     set(
      String key, String value, boolean expireWhenDisconnect) {
    set(Arrays.asList(new Kv(key, value)), expireWhenDisconnect);
  }


  // util

  private static Integer toInt(String str, Integer defaultValue) {
    if (str != null && !str.isEmpty()) {
      try {
        return Integer.parseInt(str);
      } catch (NumberFormatException e) {}
    }
    return defaultValue;
  }

  private static Long    toLong(String str, Long defaultValue) {
    if (str != null && !str.isEmpty()) {
      try {
        return Long.parseLong(str);
      } catch (NumberFormatException e) {}
    }
    return defaultValue;
  }

}

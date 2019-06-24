package work.leond.flo.conf;

import java.util.List;

/**
 * (internal use) interface for extensible.
 */
interface Confer {

  List<Kv> range(String key, String rangeEnd, Watcher watcher);
  void     watchStop(Watcher watcher);
  void     set(List<Kv> kvs, boolean expireWhenDisconnect);

}
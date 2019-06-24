package work.leond.flo.conf;

import java.util.Collections;
import java.util.List;

final class EtcdConfer implements Confer {

  public List<Kv> range(String key, String rangeEnd, Watcher watcher) {
    // TODO range
    return Collections.emptyList();
  }

  public void watchStop(Watcher watcher) {
    // TODO watchStop
  }

  public void set(List<Kv> kvs, boolean expireWhenDisconnect) {
		// TODO set
	}

}

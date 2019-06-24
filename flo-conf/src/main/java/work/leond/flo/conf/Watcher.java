package work.leond.flo.conf;

import java.util.List;

public interface Watcher {
  void changed(List<Event> events);
}

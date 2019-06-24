package work.leond.flo.service;

public final class Service {

  private static ServiceServer server = new ServiceServer();

  public static ServiceServer server() {
    return server;
  }

  public static String nameOf(Class<?> itface) {
    return itface.getCanonicalName();
  }




  static enum Status {
    stopped  ( 0),
    start    (10),
    starting (11),
    started  (12),
    stop     (20),
    stoping  (21),
    ;

    private int id;

    private Status(int id) {
      this.id = id;
    }

    public int id() {
      return id;
    }

    public static Status of(int id) {
      for (Status status : values()) {
        if (status.id == id) {
          return status;
        }
      }
      return null;
    }

    public static String[] names() {
      Status[] statuses = values();
      String[] names = new String[statuses.length];

      int i = 0;
      for (Status status : statuses) {
        names[i++] = status.name();
      }

      return names;
    }
  }

}
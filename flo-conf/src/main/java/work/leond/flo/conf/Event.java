package work.leond.flo.conf;

public class Event {

  public static final int TYPE_MODIFY = 1;
  public static final int TYPE_DELETE = 2;
  
  private Kv  kv;
  private int type;

  public Kv getKv() {
    return kv;
  }

  public void setKv(Kv kv) {
    this.kv = kv;
  }

  public int getType() {
    return type;
  }

  public void setType(int type) {
    this.type = type;
  }
  
}

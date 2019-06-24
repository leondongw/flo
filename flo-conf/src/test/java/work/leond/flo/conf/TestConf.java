package work.leond.flo.conf;

public class TestConf {
  
  public static void main(String[] args) {
    
    o("a1 = " + Conf.get("a1"));

    o("a9 = " + Conf.get("a9"));
    o("a9 (default) = " + Conf.get("a9", "default alpha9"));

    o("\nb/* = " + Conf.range("b/", Conf.rangeEnd("b/")));
    o("\n*~B = " + Conf.range(Conf.keyOfInfinite(), "B"));
    o("\na~* = " + Conf.range("a", Conf.keyOfInfinite()));
  }
  
  private static void o(Object o) {
    System.out.println(o);
  }

}

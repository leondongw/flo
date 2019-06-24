package work.leond.flo.db;

final class Util {


  public static Integer str2int(String str, Integer dft) {
    try {
      return Integer.parseInt(str);
    } catch (NumberFormatException e) {
      return dft;
    }
  }

  @SafeVarargs
  public static <T> T   v(T... vs) {
    for (T v : vs) {
      if (v != null) {return v;}
    }
    return null;
  }

  public static String camelToSnake(String camel) {
    final int diff = 'A' - 'a';

    if (camel == null) {
      return null;
    }
    StringBuilder buf = new StringBuilder(camel.length() + 2);
    for (int i = 0; i < camel.length(); i++) {
      char c = camel.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        if (i > 0) {buf.append("_");} //no "_" for 1st char
        c -= diff;
      }
      buf.append(c);
    }

    return buf.toString();
  }
}

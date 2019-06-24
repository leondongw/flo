package work.leond.flo.conf;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Util {

  private static final Logger logger = LoggerFactory.getLogger(Util.class);


  public static abstract class Prop<T> {
    public String key;

    public Prop(String key) {
      this.key = key;
    }

    public T val(Properties p) {
      return val(p == null ? null : p.getProperty(key));
    }

    public T val(Properties p, String keyPrefix) {
      return val(p == null ? null : p.getProperty(keyPrefix + key));
    }

    protected abstract T val(String val);
  }

  public static class StrProp extends Prop<String> {

    public StrProp(String key) {
      super(key);
    }

    protected String val(String val) {
      return val;
    }
  }

  public static class BoolProp extends Prop<Boolean> {
    private Boolean  dft;
    private boolean  ignoreInvalid;

    public BoolProp(String key, Boolean dft) {
      this(key, dft, true);
    }

    public BoolProp(String key, Boolean dft, boolean ignoreInvalid) {
      super(key);
      this.dft = dft;
      this.ignoreInvalid = ignoreInvalid;
    }

    protected Boolean val(String val) {
      if (val == null || val.isEmpty()) {
        return dft;
      }

      if ("true".equals(val)) {
    	return true;
      } else if ("false".equals(val)) {
    	return false;
      } else if (ignoreInvalid) {
        return dft;
      } else {
        throw new ConfException(key + " invalid value \"" + val + "\"");
      }
    }
  }

  public static class IntProp extends Prop<Integer> {
    private int dft;
    private int min;
    private int max;

    public IntProp(String key, int dft, int min) {
      this(key, dft, min, Integer.MAX_VALUE);
    }

    public IntProp(String key, int dft, int min, int max) {
      super(key);
      this.dft = dft;
      this.min = min;
      this.max = max;
    }

    protected Integer val(String val) {
      if (val == null || val.isEmpty()) {
        return dft;
      }

      int intVal = 0;
      try {
        intVal = Integer.parseInt(val);
      } catch (NumberFormatException e) {
        logger.warn(
          "{} invalid value \"{}\", not a int number, use default {}",
          key, val, dft);
        return dft;
      }

      if (intVal < min) {
        logger.warn(
          "{} invalid value \"{}\", less than min, use min {}",
          key, val, min);
      }

      if (intVal > max) {
        logger.warn(
          "{} invalid value \"{}\", greater than max, use max {}",
          key, val, max);
      }

      return intVal;
    }
  }

  public static class LongProp extends Prop<Long> {
    private long dft;
    private long min;
    private long max;

    public LongProp(String key) {
      this(key, 0, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    public LongProp(String key, long dft, long min) {
      this(key, dft, min, Long.MAX_VALUE);
    }

    public LongProp(String key, long dft, long min, long max) {
      super(key);
      this.dft = dft;
      this.min = min;
      this.max = max;
    }

    protected Long val(String val) {
      if (val == null || val.isEmpty()) {
        return dft;
      }

      long longVal = 0;
      try {
        longVal = Long.parseLong(val);
      } catch (NumberFormatException e) {
        logger.warn(
          "{} invalid value \"{}\", not a int number, use default {}",
          key, val, dft);
        return dft;
      }

      if (longVal < min) {
        logger.warn(
          "{} invalid value \"{}\", less than min, use min {}",
          key, val, min);
      }

      if (longVal > max) {
        logger.warn(
          "{} invalid value \"{}\", greater than max, use max {}",
          key, val, max);
      }

      return longVal;
    }
  }

  public static class OptProp extends Prop<String> {
    private String   dft;
    private String[] options;

    public <T extends Enum<?>> OptProp(String key, T dft, T[] options) {
      this(key, dft.name(), strsOf(options));
    }

    public OptProp(String key, String dft, String... options) {
      super(key);
      this.dft = dft;
      this.options = options;
      Arrays.sort(this.options);
    }

    private static <T extends Enum<?>> String[] strsOf(T[] enums) {
      String[] strs = new String[enums.length];
      for (int i = 0; i < enums.length; i++) {
        strs[i] = enums[i].name();
      }
      return strs;
    }

    protected String val(String val) {
      if (val == null || val.isEmpty()) {
        return dft;
      }

      if (Arrays.binarySearch(options, val) >= 0) {
        return val;
      }

      throw new ConfException(key + " invalid value \"" + val + "\"");
    }
  }

  public static class PropertyReader {

    private Properties p;

    public PropertyReader(String s) {
      p = new Properties();
      if (s != null) {
        try {
			p.load(new StringReader(s));
		} catch (IOException e) {
			// should not happen
		}
      }
    }

    public Properties getProperties() {
      return p;
    }

    public Integer getInt(String name) {
      return getInt(name, null);
    }

    public Integer getInt(String name, Integer dft) {
      try {
        return Integer.parseInt(p.getProperty(name));
      } catch (NumberFormatException e) {
        return dft;
      }
    }

    public String get(String name) {
      return p.getProperty(name);
    }

  }

}
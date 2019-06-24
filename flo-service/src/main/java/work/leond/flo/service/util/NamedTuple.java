package work.leond.flo.service.util;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * NamedTuple is a array which each element can has a unique name or null name.
 * It is mutable for value and immutable for name, index and length.
 * </p>
 * <p>
 * Index starts from 0.
 * </p>
 * <p>
 * If an element has a null name, it can not be set value by
 * {@code set(String name)}.
 * </p>
 */
public class NamedTuple implements Cloneable {

  private Element<?>[]           elements;
  private Map<String,Element<?>> elementsByName;

  public NamedTuple(Element<?>... elements) {
    int len = elements.length;

    this.elements = new Element[len];
    elementsByName = new HashMap<String,Element<?>>(len + len / 2 + 1, 1.0f);

    Element<?> elem = null;
    for (int i = 0; i < len; i++) {
      elem = elements[i];

      if (elem == null) {
        throw new IllegalArgumentException("Element must not be null");
      }
      if (elem.type == null) {
        throw new IllegalArgumentException("Element type must not be null");
      }

      elem = elem.clone();
      this.elements[i] = elem;

      if (elem.name != null) {
        if (elementsByName.putIfAbsent(elem.name, elem) != null) {
          throw new IllegalArgumentException(
              "Element name must be unique or null");
        }
      }

    }
  }

  public String toString() {
    /* {0(x):aa, 1(y):bb}
     */
    StringBuilder str = new StringBuilder(elements.length * 20);

    str.append("{");
    
    for (int i = 0; i < elements.length; i++) {
      Element<?> ele = elements[i];
      str.append(i);
      if (ele.name != null) {
        str.append("(").append(ele.name).append(")");
      }
      str.append(":").append(ele.value);
      if (i < elements.length - 1) {
        str.append(",");
      }
    }

    str.append("}");

    return str.toString();
  }

  public NamedTuple clone() {
    return new NamedTuple(elements);
  }

  public Element<?> get(int index) {
    if (index < 0 || index >= elements.length) {
      return null;
    }
    return elements[index];
  }

  public Element<?> get(String name) {
    return elementsByName.get(name);
  }

  public Object value(int index) {
    if (index < 0 || index >= elements.length) {
      return null;
    }
    return elements[index].value;
  }

  public Object value(String name) {
    Element<?> element = elementsByName.get(name);
    return element != null ? element.value : null;
  }

  /**
   * Set value at index, or set multiple values start from index.
   */
  @SuppressWarnings("unchecked")
  public <T> void value(int index, Object value) {
    if (index < 0 || index >= elements.length) {
      return;
    }
    Element<T> elem = (Element<T>) elements[index];
    elem.value = (T) value;
  }

  @SuppressWarnings("unchecked")
  public <T> void value(String name, Object value) {
    Element<T> element = (Element<T>) elementsByName.get(name);
    if (element == null) {
      return;
    }
    element.value = (T) value;
  }

  public Object[] values() {
    Object[] values = new Object[elements.length];

    for (int i = 0; i < elements.length; i++) {
      values[i] = elements[i].value;
    }

    return values;
  }

  public int size() {
    return elements.length;
  }


  public static final class Element<T> implements Cloneable {

    private String name;
    private Type   type;
    private T      value;

    private Element(String name, Type type) {
      this.name = name;
      this.type = type;
    }

    private Element(String name, Type type, T value) {
      this.name = name;
      this.type = type;
      this.value = value;
    }

    public Element<T> clone() {
      return new Element<T>(name, type, value);
    }

    public  String name() {
      return name;
    }

    public  Type type() {
      return type;
    }

    public  T value() {
      return value;
    }

    public  Element<T> value(T value) {
      this.value = value;
      return this;
    }


    public  static <T> Element<T> of(String name, Type type) {
      return new Element<T>(name, type);
    }

  }

}
package work.leond.flo.service;

import java.lang.reflect.Method;
import java.util.Map;

import work.leond.flo.service.util.NamedTuple;

class Func {

  private String      name;
  private Method      method;
  private NamedTuple  params;
  private NamedTuple  ret;
  // package scoped
  int                 queueMax;
  boolean             logReq;
  boolean             logReqParam;
  Map<String,Boolean> logReqParams;


  public String name() {
    return name;
  }
  public Func name(String name) {
    this.name = name;
    return this;
  }
  public Method method() {
    return method;
  }
  public Func method(Method method) {
    this.method = method;
    return this;
  }
  public NamedTuple params() {
    return params;
  }
  public Func params(NamedTuple params) {
    this.params = params;
    return this;
  }
  public NamedTuple ret() {
    return ret;
  }
  public Func ret(NamedTuple ret) {
    this.ret = ret;
    return this;
  }

}
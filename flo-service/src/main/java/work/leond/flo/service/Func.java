package work.leond.flo.service;

import java.lang.reflect.Method;
import java.util.Map;

import work.leond.flo.service.util.NamedTuple;

class Func {

  String     name;
  Method     method;
  NamedTuple params;
  NamedTuple ret;
  //
  int        queueMax;
  boolean    logReq;
  boolean    logReqParam;
  Map<String,Boolean> logReqParams;

}

package work.leond.flo.service.test;

import work.leond.flo.service.FuncFilter;
import work.leond.flo.service.Req;
import work.leond.flo.service.Resp;
import work.leond.flo.service.Service;

import java.util.HashMap;
import java.util.Map;

public class TestService {

  public  static void main(String[] args) throws Exception {

    // ATTENTION
    // use javac -parameters in java1.8 to keep parameter name in class file

    Service.server().add(new FuncFilter() {
      @Override
      public void beforeFunc(Req req) {
        System.out.println("beforeFunc: " + req);
      }

      @Override
      public void afterFunc(Resp resp) {
        System.out.println("afterFunc: " + resp);

        Map<String,Object> r = new HashMap<>();
        if (resp.ex() != null) {
          r.put("code", 1);
          r.put("msg", resp.ex().toString());

        } else {
          r.put("code", 0);
          r.put("data", resp.ret());
        }

        resp.ret(r);
        resp.ex(null);
      }
    });

    Service.server().start(new UserServiceImpl());

    // POST
    // http://127.0.0.1:8080/test.user/getUser
    // body: {"id":11}
  }

}

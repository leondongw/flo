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

    Service.server().addFilter(new FuncFilter() {
      @Override
      public void beforeFunc(Req req) {
        String requesterId = req.header("_requester_id");
        User requester = new User();
        try {
          requester.setId(Long.parseLong(requesterId));
        } catch (NumberFormatException e) {
          requester = null;
        }
        req.param("requester", requester);
      }

      @Override
      public void afterFunc(Resp resp) {
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
    // http://127.0.0.1:8080/flo.u/getUser
    // body: {"id":11}
  }

}

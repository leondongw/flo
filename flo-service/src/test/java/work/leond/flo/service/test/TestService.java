package work.leond.flo.service.test;

import work.leond.flo.service.Service;

public class TestService {

  public  static void main(String[] args) throws Exception {

    // ATTENTION
    // use javac -parameters in java1.8 to keep parameter name in class file

    Service.server().start(new UserServiceImpl());

    // POST
    // http://127.0.0.1:8080/test.user/getUser
    // body: {"id":11}
  }

}

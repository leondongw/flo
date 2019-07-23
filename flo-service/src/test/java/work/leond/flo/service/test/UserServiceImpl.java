package work.leond.flo.service.test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class UserServiceImpl implements UserService {

  public Future<User> getUser(User userReq, Long id) {
    User fake = new User();
    fake.id = id;
    fake.nickname = "fake";
    fake.gender   = 1;
    fake.setBirthday(20110203);

    if (userReq == null || userReq.id != id) {
      fake.gender = null;
      fake.setBirthday(null);
    }

    CompletableFuture<User> r = new CompletableFuture<User>();
    r.complete(fake);

    try {
      Thread.sleep(10 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    return r;
  }

  public User getUser2(User userReq, Long id) {
    try {
      return getUser(userReq, id).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void modUser(User user) {
    int a = 1 / 0;
  }

}

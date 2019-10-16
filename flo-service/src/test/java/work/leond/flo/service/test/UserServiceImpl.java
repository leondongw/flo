package work.leond.flo.service.test;

import java.util.concurrent.CompletableFuture;

public class UserServiceImpl implements UserService {

  public CompletableFuture<User> getUser(User requester, Long id) {
    User fake = new User();
    fake.setId(id);
    fake.setNickname("fake");
    fake.setGender(1);
    fake.setBirthday(20110203);

    // only self can see detail

    if (requester == null || !requester.getId().equals(id)) {
      fake.setGender(null);
      fake.setBirthday(null);
    }

    CompletableFuture<User> r = new CompletableFuture<>();
    new Thread(() -> {
      try {
        Thread.sleep(1000L);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      r.complete(fake);
    }).start();

    return r;
  }

  public User getUser2(User requester, Long id) {
    try {
      return getUser(requester, id).get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void modUser(User requester, User user) {
    throw new RuntimeException("test");
  }

}

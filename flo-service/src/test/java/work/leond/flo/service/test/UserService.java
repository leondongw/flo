package work.leond.flo.service.test;

import java.util.concurrent.CompletableFuture;

public interface UserService {

  CompletableFuture<User> getUser(User requester, Long id);

  User getUser2(User requester, Long id);

  void modUser(User requester, User user);

}

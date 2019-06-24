package work.leond.flo.service.test;

import java.util.concurrent.Future;

public interface UserService {

  Future<User> getUser(User userReq, Long id);

  User getUser2(User userReq, Long id);

  void modUser(User user);

}

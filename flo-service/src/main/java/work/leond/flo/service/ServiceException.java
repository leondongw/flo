package work.leond.flo.service;

public class ServiceException extends RuntimeException {

  private static final long serialVersionUID = -282210561220562769L;

  public ServiceException() {
    super();
  }

  public ServiceException(String msg) {
    super(msg);
  }

  public ServiceException(Throwable cause) {
    super(cause);
  }

  public ServiceException(String msg, Throwable cause) {
    super(msg, cause);
  }




  public static final ServiceException BAD_REQUEST =
      new ServiceException("Bad request");
  public static final ServiceException NOT_FOUND =
      new ServiceException("Not found");
  public static final ServiceException TOO_MANY_REQUESTS =
      new ServiceException("Too many requests");
  public static final ServiceException INTERNAL_ERROR =
      new ServiceException("Internal error");

}

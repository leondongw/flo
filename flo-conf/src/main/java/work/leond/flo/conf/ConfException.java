package work.leond.flo.conf;

public class ConfException extends RuntimeException {

  private static final long serialVersionUID = 19823090923329L;
  
  public  ConfException() {
    super();
  }
  
  public  ConfException(String message) {
    super(message);
  }
  
  public  ConfException(Throwable cause) {
    super(cause);
  }

  public  ConfException(String message, Throwable cause) {
    super(message, cause);
  }

}
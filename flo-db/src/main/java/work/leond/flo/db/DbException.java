package work.leond.flo.db;

public class DbException extends RuntimeException {

  private static final long serialVersionUID = 2224606427837153733L;
  
  public  DbException() {
    super();
  }
  
  public  DbException(String message) {
    super(message);
  }
  
  public  DbException(Throwable cause) {
    super(cause);
  }

  public  DbException(String message, Throwable cause) {
    super(message, cause);
  }
  
}

package work.leond.flo.service.test;

public class User {
  public  Long    id;
  public  Long    ctime;
  public  String  nickname;
  public  Integer gender;
  public  Integer birthday;
  public  String  birthdayStr;
  public  String  json;

  public  void   setBirthday(Integer birthday) {
    this.birthday = birthday;

    if (birthday == null) {
      this.birthdayStr = null;
    } else {
      this.birthdayStr = "" + (birthday / 10000)
          + "." + ((birthday % 10000) / 100)
          + "." + (birthday % 100);
    }
  }

  public  String toString() {
    return "{id:" + id
        + ", ctime:" + ctime
        + ", nickname:" + nickname
        + ", gender:" + gender
        + ", birthday:" + birthday
        + ", birthdayStr:" + birthdayStr
        + ", json:" + json
        + "}";
  }
}
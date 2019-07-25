package work.leond.flo.service.test;

public class User {
  private Long    id;
  private Long    ctime;
  private String  nickname;
  private Integer gender;
  private Integer birthday;
  private String  json;


  // toString

  @Override
  public String toString() {
    return "{" +
        (id != null ? ("id=" + id) : "") +
        (ctime != null ? (", ctime=" + ctime) : "") +
        (nickname != null ? (", nickname='" + nickname + '\'') : "") +
        (gender != null ? (", gender=" + gender) : "") +
        (birthday != null ? (", birthday=" + birthday) : "") +
        (json != null ? (", json='" + json + '\'') : "") +
        '}';
  }

  // getter / setter

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getCtime() {
    return ctime;
  }

  public void setCtime(Long ctime) {
    this.ctime = ctime;
  }

  public String getNickname() {
    return nickname;
  }

  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  public Integer getGender() {
    return gender;
  }

  public void setGender(Integer gender) {
    this.gender = gender;
  }

  public Integer getBirthday() {
    return birthday;
  }

  public  void   setBirthday(Integer birthday) {
    this.birthday = birthday;
  }

  public String getJson() {
    return json;
  }

  public void setJson(String json) {
    this.json = json;
  }

}
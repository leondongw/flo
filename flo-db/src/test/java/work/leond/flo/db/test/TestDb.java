package work.leond.flo.db.test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import work.leond.flo.db.Db;

public class TestDb {

  @Db.OrmClass(table = "user")
  public  static class User {
    @Db.OrmField(id = true, autoIncre = true)
    private Long    id;
    private Long    createTime;
    private String  nickname;
    @Db.OrmIgnore
    private String  birthday;
    @Db.OrmField(column = "birthday")
    private Integer birthdayInDb;


    public  User() {}

    public  User(Long id) {
      this.id = id;
    }

    public  String toString() {
      return
          "{id:" + id +
          ", createTime:" + createTime +
          ", nickname:" + nickname +
          ", birthday:" + birthday +
          ", birthdayInDb:" + birthdayInDb +
          "}";
    }

    public static Integer toBirthdayInDb(String birthday) {
      SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
      Calendar c = Calendar.getInstance();
      try {
        c.setTime(df.parse(birthday));
      } catch (ParseException e) {
        e.printStackTrace();
      }
      return
          (c.get(Calendar.YEAR) * 10000) +
          ((c.get(Calendar.MONTH) + 1) * 100) +
          (c.get(Calendar.DATE));
    }

    public static String toBirthday(Integer birthdayInDb) {
      SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
      Calendar c = Calendar.getInstance();
      c.set(Calendar.YEAR, birthdayInDb / 10000);
      c.set(Calendar.MONTH, (birthdayInDb % 10000) / 100 - 1);
      c.set(Calendar.DATE, birthdayInDb % 100);
      return df.format(c.getTime());
    }

    // special getter/setter
    public void setBirthday(String birthday) {
      this.birthday = birthday;
      this.birthdayInDb = toBirthdayInDb(birthday);
    }

    public void setBirthdayInDb(Integer birthdayInDb) {
      this.birthdayInDb = birthdayInDb;
      this.birthday = toBirthday(birthdayInDb);
    }

    // getter/setter
    public Long getId() {
      return id;
    }
    public void setId(Long id) {
      this.id = id;
    }
    public Long getCreateTime() {
      return createTime;
    }
    public void setCreateTime(Long createTime) {
      this.createTime = createTime;
    }
    public String getNickname() {
      return nickname;
    }
    public void setNickname(String nickname) {
      this.nickname = nickname;
    }
    public String getBirthday() {
      return birthday;
    }
    public Integer getBirthdayInDb() {
      return birthdayInDb;
    }

  }

  public  static void main(String[] args) throws Exception {
    /*
      create database flo_db_test default charset=utf8;
     */

    Db db = Db.getDb("flo.db.test");

    // create table using low-level JDBC-like API
    db.execute(new Db.Update().sql(
      "create table if not exists `user` (" +
      "  `id`           bigint       not null auto_increment," +
      "  `create_time`  bigint       not null," +
      "  `nickname`     varchar(20)  not null," +
      "  `birthday`     int                  ," +
      "  primary key (`id`)," +
      "  index i_user_nickname (`nickname`)" +
      ") engine=InnoDB default charset=utf8mb4"
    ));

    //add five users if not enough
    Long count = db.queryOne(new Db.Query<Long>()
      .sql("select count(*) from user"));
    if (count < 5) {
      System.out.println("\nAdd users.");

      // use high-level API
      for (int i = 0; i < 2; i++) {
        User user = new User();
        user.setCreateTime(System.currentTimeMillis());
        user.setNickname("name" + i);
        user.setBirthday((1990 + i * 5) + "-02-03");
        db.add(user);
      }

      // or low-level JDBC-like API
      Db.Batch batch = new Db.Batch();
      batch.sql(
          "insert into user(create_time,nickname,birthday)" +
          " values(?,?,?)");
      List<Object[]> params = new ArrayList<Object[]>();
      for (int i = 2; i < 5; i++) {
        params.add(new Object[] {
            System.currentTimeMillis(),
            "name" + i,
            ((1990 + i * 5) * 10000) + 203
        });
      }
      batch.params(params);
      db.execute(batch);
    }


    // list first three users
    List<User> users = db.execute(new Db.Query<User>(
      User.class, "select * from user limit 3"));
    System.out.println("\nlist users:");
    for (User user : users) {
      System.out.println(user);
    }
    // list three users by birthday in 200X
    users = db.execute(new Db.Query<User>(
        User.class,
        "select * from user where birthday between ? and ? limit 3",
        User.toBirthdayInDb("2000-01-01"),
        User.toBirthdayInDb("2009-12-31")));
    System.out.println("\nlist users by birthday:");  
    for (User user : users) {
      System.out.println(user);
    }


    // get min id
    Long userIdMin = db.queryOne(new Db.Query<Long>()
        .sql("select min(id) from user"));

    // get min-id user
    User user = db.get(new User(userIdMin));
    System.out.println("\nget user:");
    System.out.println(user);

    // update the user's birthday
    user.setBirthday("1991-05-12");
    db.update(user);
    System.out.println("\nupdated user:");
    System.out.println(db.get(new User(user.getId())));

    // delete the user
    db.delete(user);
    System.out.println("\ndeleted user:");
    System.out.println(db.get(new User(user.getId())));
  }

}

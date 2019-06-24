flo-db is a database framework,
with high-level ORM API and low-level JDBC-like API,<br/>
has a pool connect to databases by configuration,
and auto reconnect when configuration changes.

# Configuration
flo-db uses flo-conf for configuration.<br/>


## Configuration key of server
Key for a database server configuration is "db/{db_name}/server/{instance_name}".<br/>
For example: "db/flo.db.test/server/master".<br/>
<br/>
Keys structure is like:
```
db/flo.db.user/server/master
db/flo.db.user/server/slave1
db/flo.db.user/server/slave2
db/flo.db.memo/server/master
db/flo.db.memo/server/slave
db/flo.db.user/server/master
```
more friendly look is:
```
db/
  flo.db.user/
    server/
      master
      slave1
      slave2
  flo.db.memo/
    server/
      master
      slave
  flo.db.user/
    server/
      master
```


## Configuration value format of server
Value for a database server configuration is in properties format.<br/>
It has below keys:<br/>
(all time unit is millisecond)<br/>
(all options is case-sensitive)
- status  : on (default) | off
- jdbcUrl
- user
- password
- replica  : master (default) | slave
- poolSizeMin : default 1
- poolSizeMax : default 10
- poolIdleMax : default 600000 (10 minutes)
- hikari.\*  : you can set [HikariCP knobs](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby) directly where "\*" stands for knob name


## Configuration value Example
In file flo.conf it can be like:
```
db/flo.db.test/server/master=\
  jdbc_url=jdbc:mysql://127.0.0.1:3306/db_name?characterEncoding=utf-8&connectTimeout=3000\n\
  user=username\n\
  password=password\n\
  replica=master\n\
  pool_size_min=1\n\
  pool_size_max=10\n\
  pool_idle_max=600000
```
or
```
db/flo.db.test/server/master=\
  hikari.dataSourceClassName=org.postgresql.ds.PGSimpleDataSource\n\
  hikari.dataSource.serverName=localhost\n\
  hikari.dataSource.portNumber=5432\n\
  hikari.dataSource.databaseName=mydb\n\
  hikari.dataSource.user=test\n\
  hikari.dataSource.password=test\n\
  replica=master\n\
  pool_size_min=1\n\
  pool_size_max=10\n\
  pool_idle_max=600000
```


# Coding Example
```java
Db db = Db.getDb("flo.db.test");

// high-level ORM API
User user = new User();
user.setNickname("name");
db.add(user);

// low-level JDBC-like API
Long count = db.queryOne(new Db.Query<Long>()
    .sql("select count(*) from user"));
```

You can see test for more examples.
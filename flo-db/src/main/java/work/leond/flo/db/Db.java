package work.leond.flo.db;

import static work.leond.flo.db.Util.camelToSnake;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.leond.flo.conf.Conf;
import work.leond.flo.conf.Event;
import work.leond.flo.conf.Kv;
import work.leond.flo.conf.Watcher;
import work.leond.flo.conf.Util.IntProp;
import work.leond.flo.conf.Util.LongProp;
import work.leond.flo.conf.Util.OptProp;
import work.leond.flo.conf.Util.PropertyReader;
import work.leond.flo.conf.Util.StrProp;

/**
 * <pre>
 * Database framework,
 * with high-level ORM API and low-level JDBC-like API,
 * has a pool of databases,
 * each database has some servers like one master and two slaves,
 * each server has a pool of connections,
 * and can auto reconnect when server's configuration changes.
 * </pre>
 */
public final class Db {

  // Part 1: pool of db

  private static final Logger logger = LoggerFactory.getLogger(Db.class);
  // init as no more then 10 dbs usually
  private static final ConcurrentHashMap<String, Db> dbs = new ConcurrentHashMap<String, Db>(16, 0.9f, 16);
  private static final ConcurrentHashMap<String, Object> dbLocks = new ConcurrentHashMap<String, Object>(16, 0.9f, 16);

  /** get Db instance for database {name} */
  public static Db getDb(String name) {
    Db db = dbs.get(name);
    if (db == null) {
      Object lock = getDbLock(name);
      synchronized (lock) {
        db = dbs.get(name);
        if (db == null) {
          db = new Db(name);
          dbs.put(name, db);
        }
      }
    }
    return db;
  }

  private static Object getDbLock(String name) {
    Object lock = dbLocks.get(name);
    if (lock == null) {
      lock = new Object();
      Object lock2 = dbLocks.putIfAbsent(name, lock);
      if (lock2 != null) {
        lock = lock2;
      }
    }
      return lock;
  }
















  // Part 2: db is a pool of servers, server is a pool of connections.
  // one db usually has serveral servers, like one master and two slaves.
  // one server is a pool of connections to this server.

  public  static enum ReplicaUse {
    MASTER        (true , false),
    MASTER_OR_ANY (true , true ),
    SLAVE         (false, false),
    SLAVE_OR_ANY  (false, true ),
    ANY           (null , true ),
    ;

    private Boolean master;
    private boolean orAny;

    private ReplicaUse (Boolean master, boolean orAny) {
      this.master = master;
      this.orAny = orAny;
    }
  }

  private static class Server {

    private static interface Meta {

      // path

      static String pathPrefix(String db) {
        return "db/" + db + "/server/";
      }

      static String pathToId(String db, String key) {
        return key.substring(pathPrefix(db).length());
      }

      // properties
  
      enum Status {
        on, off
      }

      enum Replica {
        master, slave
      }

      OptProp  status      = new OptProp("status", Status.on, Status.values());
      StrProp  jdbcUrl     = new StrProp("jdbcUrl");
      StrProp  user        = new StrProp("user");
      StrProp  password    = new StrProp("password");
      OptProp  replica     = new OptProp("replica",
          Replica.master, Replica.values());
      IntProp  poolSizeMin = new IntProp("poolSizeMin", 1, 0);
      IntProp  poolSizeMax = new IntProp("poolSizeMax", 10, 1);
      LongProp poolIdleMax = new LongProp("poolIdleMax",
          10 * 60 * 1000L, 1 * 60 * 1000L);

      String  hikariPrefix   = "hikari.";

      static boolean isForHikari(String key) {
        return key != null && key.startsWith(hikariPrefix);
      }

      static String  toHikariKey(String key) {
        return key.substring(hikariPrefix.length());
      }
    }

    public  String     db;
    public  String     id;
    public  String     value;   // conf content
    public  boolean    deleted;
    public  String     status;
    public  String     replica;
    public  HikariDataSource dataSource;

    public  static Server of(String db, Kv kv, boolean deleted) {
      Server server = new Server();
      server.db = db;
      server.id = Meta.pathToId(db, kv.getKey());
      server.value = kv.getValue();
      server.deleted = deleted;
      return server;
    }

    public  String toString() {
      return
          "{db:" + db +
          ", id:" + id +
          ", status:" + status +
          ", replica:" + replica +
          "}";
    }

    private String name() {
      return db + "." + id;
    }

    public  boolean statusIsOn() {
      return Meta.Status.on.name().equals(status);
    }

    public  boolean replicaIsMaster() {
      return Meta.Replica.master.name().equals(replica);
    }

    public  boolean init() {
      try {
        // TODO rename Db.Server to Db.Client or something
        logger.info("Db server init start {}", name());

        // parse configuration
        Properties p = new PropertyReader(value).getProperties();
        status  = Meta.status.val(p);
        replica = Meta.replica.val(p);
        Properties hikariProps = new Properties();
        for (String key : p.stringPropertyNames()) {
          if (!Meta.isForHikari(key)) {continue;}
          String val = p.getProperty(key);
          if (val == null) {continue;}
          hikariProps.setProperty(Meta.toHikariKey(key), val);
        }
        if (!statusIsOn()) {
          return true;
        }

        // create hikari data source
        // directly-set hikari config has higher priority then pool config
        HikariConfig hikariConfig = new HikariConfig(hikariProps);
        if (hikariConfig.getJdbcUrl() == null) {
          hikariConfig.setJdbcUrl(Meta.jdbcUrl.val(p));
        }
        if (hikariConfig.getUsername() == null) {
          hikariConfig.setUsername(Meta.user.val(p));
        }
        if (hikariConfig.getPassword() == null) {
          hikariConfig.setPassword(Meta.password.val(p));
        }
        if (hikariConfig.getMinimumIdle() == 0) {
          hikariConfig.setMinimumIdle(Meta.poolSizeMin.val(p));
        }
        if (hikariConfig.getMaximumPoolSize() == 0) {
          hikariConfig.setMaximumPoolSize(Meta.poolSizeMax.val(p));
        }
        if (hikariConfig.getIdleTimeout() == 0) {
          hikariConfig.setIdleTimeout(Meta.poolIdleMax.val(p));
        }
        if (hikariConfig.getPoolName() == null) {
          hikariConfig.setPoolName(name());
        }
        dataSource = new HikariDataSource(hikariConfig);

        logger.info("Db server init succ {}", name());
        return true;

      } catch (Exception e) {
        logger.error("Db server init fail " + name(), e);
        return false;
      }
    }

    public  void close() {
      if (dataSource != null) {
        try {
          dataSource.close();
        } catch (Exception e) {
          logger.info("Db server close fail ignore " + name(), e);
        }
      }
    }

  }

  private static class ServerGroup {
    public  List<Server> servers = new ArrayList<Server>();
    public  List<Server> masters = new ArrayList<Server>();
    public  List<Server> slaves  = new ArrayList<Server>();
  }

  private        class ServerWatcher implements Watcher {
    public void changed(List<Event> events) {
      if (events == null || events.isEmpty()) {return;}

      List<Server> serversChanged = new ArrayList<Server>(events.size());
      for (Event e : events) {
        serversChanged.add(Server.of(
            name, e.getKv(), e.getType() == Event.TYPE_DELETE));
      }

      serverChanged(serversChanged);
    }
  }

  private static class ServerCloseThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
      Thread thread = new Thread(r, "db_server_close");
      thread.setDaemon(true);
      return thread;
    }
  }


  private String          name;
  private ServerGroup     serverGroup = new ServerGroup();
  private AtomicInteger   serverIndex = new AtomicInteger();
  private AtomicInteger   masterIndex = new AtomicInteger();
  private AtomicInteger   slaveIndex  = new AtomicInteger();
  private ExecutorService serverCloseExecutor = new ThreadPoolExecutor(
      0, 1, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
      new ServerCloseThreadFactory());

  private Db(String name) {
    this.name = name;

    // init servers
    String confPathPrefixOfServer = Server.Meta.pathPrefix(name);
    List<Kv> kvs = Conf.range(
        confPathPrefixOfServer, Conf.rangeEnd(confPathPrefixOfServer),
        new ServerWatcher());
    List<Server> servers = new ArrayList<Server>(kvs.size());
    for (Kv kv : kvs) {
      servers.add(Server.of(name, kv, false));
    }
    serverChanged(servers);
  }

  private synchronized void serverChanged(List<Server> serversChanged) {
    if (serversChanged == null || serversChanged.isEmpty()) {return;}

    // add none-exist ones, replace exist ones, remove deleted ones
    List<Server> servers2 = new ArrayList<Server>(this.serverGroup.servers);
    List<Server> serversRemoved = new ArrayList<Server>();

    for (Server serverChanged : serversChanged) {
      int serverChangedIndex = -1;
      for (int i = servers2.size() - 1; i >= 0; i--) {
        if (serverChanged.id.equals(servers2.get(i).id)) {
          serverChangedIndex = i;
          break;
        }
      }

      boolean modify = serverChangedIndex >= 0;

      // delete
      if (serverChanged.deleted) {
        if (modify) {
          serversRemoved.add(servers2.remove(serverChangedIndex));
        }
        continue;
      }

      // try init server by new conf,
      // and use old one if new conf init fail
      if (!serverChanged.init()) {
        logger.error(
          "Db serverChange fail when init by new conf, " +
          "keep using old conf, {}",
          serverChanged.name());
        continue;
      }

      // handle status not on as deleted
      if (!serverChanged.statusIsOn()) {
        if (modify) {
          serversRemoved.add(servers2.remove(serverChangedIndex));
        }
        continue;
      }

      // mofify or add keeping order
      if (modify) {
        serversRemoved.add(servers2.set(serverChangedIndex, serverChanged));
      } else {
        int i = 0;
        for (; i < servers2.size(); i++) {
          if (serverChanged.id.compareTo(servers2.get(i).id) < 0) {
            servers2.add(i, serverChanged);
            break;
          }
        }
        if (i >= servers2.size()) {
          servers2.add(serverChanged);
        }
      }
    }

    // change server group
    ServerGroup serverGroup2 = new ServerGroup();
    serverGroup2.servers = servers2;
    for (Server server : serverGroup2.servers) {
      if (server.replicaIsMaster()) {
        serverGroup2.masters.add(server);
      } else {
        serverGroup2.slaves.add(server);
      }
    }
    this.serverGroup = serverGroup2;

    // close removed servers
    for (Server server : serversRemoved) {
      server.deleted = true;
      serverCloseExecutor.submit(new Runnable(){
        public void run() {
          server.close();
        }
      });
    }
  }

  private Connection getConnFromDb(ReplicaUse replicaUse) {
    if (replicaUse == null) {
      replicaUse = ReplicaUse.ANY;
    }

    for (;;) {
      // Get a connection from a server in serverGroup.
      // If a dataSource is closed, then serverGroup must have changed,
      // retry from the new serverGroup.

      ServerGroup   sg = serverGroup;
      List<Server>  ss = null;
      AtomicInteger index = null;
      if (replicaUse.master != null) {
        ss = replicaUse.master ? sg.masters : sg.slaves;
        index = replicaUse.master ? masterIndex : slaveIndex;
      }
      if (replicaUse.master == null ||
          (replicaUse.orAny && ss.isEmpty())) {
        ss = sg.servers;
        index = serverIndex;
      }

      Server s = null;
      if (ss.isEmpty()) {
        // s = null;
      } else if (ss.size() == 1) {
        s = ss.get(0);
      } else {
        s = ss.get(index.getAndIncrement() % ss.size());
      }

      if (s == null) {
        throw new DbException("Db getConnection fail as no server");
      }
      if (s.deleted || s.dataSource.isClosed()) {
        continue;
      }
      try {
        return s.dataSource.getConnection();
      } catch (Exception e) {
        if (s.dataSource.isClosed()) {
          continue;
        }
        throw new DbException("Db getConnection fail", e);
      }
    }
  }
















  // Part 3.1: database object-relation-mapping

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD})
  public  static @interface OrmIgnore {
    boolean value() default true;
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.TYPE)
  @Inherited
  public  static @interface OrmClass {
    String table() default "";
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.METHOD})
  public  static @interface OrmField {
    String  column   () default "";
    boolean id       () default false;
    boolean autoIncre() default false;
  }

  /** Field Column Mapping */
  private static class Fcm {

    private String   fname;
    private String   cname;
    private Class<?> fclass;
    private boolean  fclassIsNumber;
    private Field    f;
    private Method   fgetter;
    private Method   fsetter;
    private Boolean  id;
    private Boolean  autoIncre;
    private Boolean  ignore;

    public  Fcm(String fname) {
      this.fname = fname ;
    }

    public  void    fclass(Class<?> fclass) {
      this.fclass = fclass;
      this.fclassIsNumber = Number.class.isAssignableFrom(fclass);
    }

    public  boolean id() {
      return id != null && id;
    }

    public  boolean autoIncre() {
      return autoIncre != null && autoIncre;
    }

    public  boolean isValid() {
      return fclass != null;
    }

    public  boolean fgetable() {
      return fgetter != null || f != null;
    }

    public  Object  fget(Object o) throws DbException {
      try {
        if (fgetter != null) {
          return fgetter.invoke(o);
        } else if (f != null) {
          return f.get(o);
        } else {
          throw new DbException("ungetable " + fname);
        }
      } catch (Exception e) {
        throw new DbException("Db getField fail, " + fname, e);
      }
    }

    @SuppressWarnings("unused")
    public  boolean fsetable() {
      return fsetter != null || f != null;
    }

    public  void    fset(Object o, Object v) throws DbException {
      try {
        v = convertNumberIfNeed(v);
        if (fsetter != null) {
          fsetter.invoke(o, v);
        } else if (f != null) {
          f.set(o, v);
        }
      } catch (Exception e) {
        throw new DbException("Db setField fail, " + fname , e);
      }
    }

    /* for the cases like: type from db is BigInteger and field type is Long. */
    private Object  convertNumberIfNeed(Object v) {
      if (v == null) {return null;}
      if (!fclassIsNumber) {return v;}

      Class<?> vclass = v.getClass();
      if (vclass == fclass) {return v;}

      if (Number.class.isAssignableFrom(vclass)) {
        Number vn = Number.class.cast(v);
        if (fclass == Byte   .class) {return vn.byteValue ();}
        if (fclass == Short  .class) {return vn.shortValue();}
        if (fclass == Integer.class) {return vn.intValue();}
        if (fclass == Long   .class) {return vn.longValue();}
        if (fclass == BigInteger.class) {
          return BigInteger.valueOf(vn.longValue());
        }
        if (fclass == BigDecimal.class) {
          return BigDecimal.valueOf(vn.doubleValue());
        }
      }

      return v;
    }
  
  }

  /** Class Table Mapping */
  private static class Ctm<C> {

    private static final ConcurrentHashMap<Class<?>, Ctm<?>> ctms =
        new ConcurrentHashMap<Class<?>, Ctm<?>>(100);

    @SuppressWarnings("unchecked")
    public  static <C> Ctm<C> of(Class<C> c) {
      Ctm<C> ctm = (Ctm<C>) ctms.get(c);
      if (ctm != null) {return ctm;}
  
      ctm = new Ctm<C>(c);
      Ctm<C> ctm2 = (Ctm<C>) ctms.putIfAbsent(c, ctm);
      if (ctm2 != null) {ctm = ctm2;}
      return ctm;
    }


    private Class<C>  c;
    private String    t;
    private List<Fcm> fcms = new ArrayList<Fcm>();
    private Map<String, Fcm> fcmsByFname = new HashMap<String, Fcm>();
    private Map<String, Fcm> fcmsByCname = new HashMap<String, Fcm>();

    private Ctm(Class<C> c) {
      // class
      this.c = c;

      // table
      OrmClass ormClass = c.getAnnotation(OrmClass.class);
      if (ormClass != null) {
        t = ormClass.table();
      } else {
        t = camelToSnake(c.getSimpleName());
      }


      // use getter/setter if exists, else use public field

      // map fcm by getters/setters
      for (Method m : c.getMethods()) {
        boolean mIsGetter = methodIsGetter(m);
        boolean mIsSetter = methodIsSetter(m);
        if (! (mIsGetter || mIsSetter)) {continue;}

        Fcm fcm = fcmAnno(m, fnameOfGsetter(m.getName()));
        if (fcm == null) {continue;}
        m.setAccessible(true);

        if (mIsGetter) {
          fcm.fgetter = m;
          if (fcm.fclass == null) {
            fcm.fclass(m.getReturnType());
          }
        } else if (mIsSetter) {
          fcm.fsetter = m;
          if (fcm.fclass == null) {
            fcm.fclass(m.getParameterTypes()[0]);
          }
        }
      }

      // map fcm by pubilc fields
      for (Field f : c.getFields()) {
        Fcm fcm = fcmAnno(f, f.getName());
        if (fcm == null) {continue;}
        f.setAccessible(true);

        fcm.f = f;
        if (fcm.fclass == null) {
          fcm.fclass(f.getType());
        }
      }

      // map fcm's annotation properties by super classes' fields.
      for (Fcm fcm : fcms) {
        for (Class<?> c2 = c;
            c2 != null && !Object.class.equals(c2);
            c2 = c2.getSuperclass()) {

          Field f2 = null;
          try {
            f2 = c2.getDeclaredField(fcm.fname);
          } catch (NoSuchFieldException e) {
            continue;
          } catch (Exception e) {
            logger.error("Ctm of abnormal when get field of super class", e);
            break;
          }

          fcmAnno(f2, f2.getName());
          break;
        }
      }

      // remove ignored or invalid fcms.
      // complete fcm.cname.
      // complete fcmsByCname.
      for (int i = fcms.size() - 1; i >= 0; i--) {
        Fcm fcm = fcms.get(i);

        if ((fcm.ignore != null && fcm.ignore) || !fcm.isValid()) {
          fcms.remove(i);
          fcmsByFname.remove(fcm.fname);
          continue;
        }

        if (fcm.cname == null) {
          fcm.cname = camelToSnake(fcm.fname);
        }

        fcmsByCname.put(fcm.cname, fcm);
      }
    }

    private <F extends AccessibleObject & Member> Fcm fcmAnno(
        F f, String fname) {

      if ((f.getModifiers() & Modifier.STATIC) > 0) {
        return null;
      }

      OrmField ormField = f.getAnnotation(OrmField.class);
      OrmIgnore ormIgnore = f.getAnnotation(OrmIgnore.class);

      Fcm fcm = fcmsByFname.get(fname);
      if (fcm == null) {
        fcm = new Fcm(fname);
        fcms.add(fcm);
        fcmsByFname.put(fcm.fname, fcm);
      }

      if (ormField != null) {
        String ormCname = ormField.column().trim();
        if (fcm.cname == null && !ormCname.isEmpty()) {
          fcm.cname = ormCname;
        }
        if (fcm.id == null) {
          fcm.id = ormField.id();
        }
        if (fcm.autoIncre == null) {
          fcm.autoIncre = ormField.autoIncre();
        }
      }

      if (ormIgnore != null && fcm.ignore == null) {
        fcm.ignore = ormIgnore.value();
      }

      return fcm;
    }

    private boolean methodIsGetter(Method m) {
      // rule:
      //   name: getXxx or isXxx
      //   no parameter
      //   return non void

      String name = m.getName();

      if (!
          ((name.length() > 3 && name.startsWith("get")) ||
          (name.length() > 2 && name.startsWith("is")))) {
        return false;
      }
      if ("getClass".equals(name)) {
        return false;
      }

      if (m.getParameterTypes().length > 0) {
        return false;
      }

      if (m.getReturnType().equals(void.class)) {
        return false;
      }

      return true;
    }

    private boolean methodIsSetter(Method m) {
      // rule:
      //   name: setXxx
      //   one parameter
      //   return void

      String name = m.getName();

      if (! (name.length() > 3 && name.startsWith("set"))) {
        return false;
      }

      if (m.getParameterTypes().length != 1) {
        return false;
      }

      if (!m.getReturnType().equals(void.class)) {
        return false;
      }

      return true;
    }

    private String fnameOfGsetter(String name) {
      char char0 = name.charAt(
        name.startsWith("is") ? 2 : 3);
      if (char0 >= 'A' && char0 <= 'Z') {
          char0 += ('a' - 'A');
      }
      return char0 + name.substring(4);
    }

  }




  // Part 3.2: database operate

  // Part 3.2.1: low-level JDBC-like API

  /** Operation */
  public  static abstract class Op<T extends Op<T, O>, O> {

    private ReplicaUse replicaUse = ReplicaUse.MASTER_OR_ANY;

    public abstract O execute(Connection conn);

    public  ReplicaUse replicaUse() {
      return replicaUse;
    }

    @SuppressWarnings("unchecked")
    public  T replicaUse(ReplicaUse replicaUse) {
      this.replicaUse = replicaUse;
      return (T) this;
    }

  }

  /**
   * <pre>
   * How to use:
   *   1 set sql
   *   2 set params if has
   *   3 set ORM Class if need, or Map will be used
   *   4 (execute)
   * </pre>
   */
  public  static          class Query<O>  extends Op<Query<O>, List<O>> {
    /* ORM Class is not acquirable from Query variable defination
     * because of java's Type Erasure.
     */

    private String    sql;
    private Object[]  params;
    // for result
    private int       possibleCount = 0;
    private String[]  cnames;
    private Ctm<O>    ctm;
    private Fcm[]     fcms;


    public  Query() {}

    public  Query(Class<O> ormClass, String sql, Object... params) {
      ormClass(ormClass);
      this.sql = sql;
      this.params = params;
    }


    public  String   sql() {
      return sql;
    }

    public  Query<O> sql(String sql) {
      this.sql = sql;
      return this;
    }

    public  Object[] params() {
      return params;
    }

    public  Query<O> params(Object... params) {
      this.params = params;
      return this;
    }

    public  int      possibleCount() {
      return possibleCount;
    }

    /**
     * (optional) possible count of result.
     * It is used to init the list to hold results,
     * so a good value can save memory.
     */
    public  Query<O> possibleCount(int possibleCount) {
      this.possibleCount = possibleCount;
      return this;
    }

    public  Class<O> ormClass() {
      return ctm == null ? null : ctm.c;
    }

    public  Query<O> ormClass(Class<O> ormClass) {
      ctm = Ctm.of(ormClass);
      return this;
    }


    public  List<O>  execute(Connection con) {

      logger.debug("Db query sql, '{}' {}", sql, params);
      PreparedStatement stm = null;

      try {
        stm = con.prepareStatement(sql);

        if (params != null) {
          for (int i = 0; i < params.length; i++) {
            stm.setObject(i + 1, params[i]);
          }
        }

        ResultSet rs = stm.executeQuery();

        List<O> r = possibleCount > 0
            ? new ArrayList<O>(possibleCount)
            : new ArrayList<O>();
        while (rs.next()) {
          r.add(result(rs));
        }

        logger.debug("Db query return, {}", r);
        return r;

      } catch (SQLException e) {
        throw new SqlException(e);

      } finally {
        close(stm);
      }
    }

    @SuppressWarnings("unchecked")
    public  O        result(ResultSet rs) throws SQLException {
      // try orm
      if (initRsCtm(rs)) {
        try {
          Object o = ctm.c.newInstance();
          for (int i = 0; i < fcms.length; i++) {
            Object v = rs.getObject(i + 1);
            if (v == null) {continue;}
            if (fcms[i] == null) {continue;}
            fcms[i].fset(o, v);
          }
          return (O) o;

        } catch (Exception e) {
          throw new DbException("Db orm fail", e);
        }
      }

      // try map as simple object or map
      if (cnames.length == 1) {
        return (O) rs.getObject(1);
      } else {
        Map<String, Object> map = new HashMap<String, Object>(
            cnames.length + cnames.length / 2);
        for (int i = 0; i < cnames.length; i++) {
          map.put(cnames[i], rs.getObject(i + 1));
        }
        return (O) map;
      }
    }

    private boolean  initRsCtm(ResultSet rs) throws SQLException {
      if (this.cnames != null) {
        return fcms != null;
      }

      synchronized (this) {
        ResultSetMetaData meta = rs.getMetaData();
        int ccount = meta.getColumnCount();

        String[] cnames = new String[ccount];
        fcms = ctm != null ? new Fcm[ccount] : null;

        for (int i = 0; i < ccount; i++) {
          String cname = meta.getColumnLabel(i + 1).toLowerCase();
          cnames[i] = cname;
          if (ctm != null) {
            fcms[i] = ctm.fcmsByCname.get(cname);
          }
        }
        this.cnames = cnames;

        return fcms != null;
      }
    }

  }

  /**
   * <pre>
   * How to use:
   *   1 set sql
   *   2 set params if has
   *   3 set returnGeneratedKeys to true if need
   *   4 (execute)
   * </pre>
   */
  public  static          class Update    extends Op<Update, Integer> {

    private String    sql;
    private Object[]  params;
    private boolean   returnGeneratedKeys = false;
    private Object    generatedKeys;


    public  Update() {}

    public  Update(String sql, Object... params) {
      this.sql = sql;
      this.params = params;
    }

    public  String   sql() {
      return sql;
    }

    public  Update   sql(String sql) {
      this.sql = sql;
      return this;
    }

    public  Object[] params() {
      return params;
    }

    public  Update   params(Object... params) {
      this.params = params;
      return this;
    }

    public  boolean  returnGeneratedKeys() {
      return returnGeneratedKeys;
    }

    public  Update   returnGeneratedKeys(boolean returnGeneratedKeys) {
      this.returnGeneratedKeys = returnGeneratedKeys;
      return this;
    }

    public  Object   generatedKeys() {
      return generatedKeys;
    }

    public  Integer  execute(Connection db) {
      logger.debug("Db update sql, '{}' {}", sql, params);
      PreparedStatement stm = null;

      try {
        generatedKeys = null;

        if (returnGeneratedKeys) {
          stm = db.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        } else {
          stm = db.prepareStatement(sql);
        }

        if (params != null) {
          for (int i = 0; i < params.length; i++) {
            stm.setObject(i + 1, params[i]);
          }
        }

        int r = stm.executeUpdate();
        if (returnGeneratedKeys) {
          ResultSet rs = stm.getGeneratedKeys();
          if (rs.next()) {
            generatedKeys = generatedKeys(rs);
          }
        }

        if (returnGeneratedKeys) {
          logger.debug("Db update return, {} {}", r, generatedKeys);
        } else {
          logger.debug("Db update return, {}", r);
        }
        return r;

      } catch (SQLException e) {
        throw new SqlException(e);

      } finally {
        close(stm);
      }
    }

    private Object   generatedKeys(ResultSet rs) throws SQLException {
      ResultSetMetaData meta = rs.getMetaData();
      int ccount = meta.getColumnCount();

      if (ccount <= 0) {
        return null;

      } else if (ccount == 1) {
        return generatedKeyFix(rs.getObject(1));

      } else {
        Map<String, Object> map =
            new HashMap<String, Object>(ccount + ccount / 3 + 1, 0.75f);
        for (int i = 1; i <= ccount; i++) {
          map.put(meta.getColumnName(i), generatedKeyFix(rs.getObject(i)));
        }
        return map;
      }
    }

    private Object   generatedKeyFix(Object r) {
      // type: BigInteger => Long
      if (BigInteger.class.isInstance(r)) {
        if (((BigInteger)r).bitLength() < 64) {
          r = ((BigInteger)r).longValue();
        }
      }
      return r;
    }

  }

  /**
   * <pre>
   * How to use:
   *   Method 1:
   *     1 set sql
   *     2 set params if has
   *     3 (execute)
   *   Method 2:
   *     1 set sqls
   *     2 (execute)
   * </pre>
   */
  public  static          class Batch     extends Op<Batch, int[]> {

    private String         sql;
    private List<Object[]> params;
    private List<String>   sqls;


    public  String         sql() {
      return sql;
    }

    public  Batch          sql(String sql) {
      this.sql = sql;
      return this;
    }

    public  List<Object[]> params() {
      return params;
    }

    public  Batch          params(List<Object[]> params) {
      this.params = params;
      return this;
    }

    public  List<String>   sqls() {
      return sqls;
    }

    public  Batch          sqls(List<String> sqls) {
      this.sqls = sqls;
      return this;
    }

    public  int[] execute(Connection db) {
      Statement         stm = null;

      try {
        if (sql != null) {
          logger.debug("Db batch sql, {}", sql);
          PreparedStatement stm2 = db.prepareStatement(sql);
          stm = stm2;
          for (Object[] p : params) {
            if (p != null) {
              for (int i = 0; i < p.length; i++) {
                stm2.setObject(i + 1, p[i]);
              }
            }
            stm2.addBatch();
          }
        } else {
          logger.debug("Db batch sqls");
          stm = db.createStatement();
          for (String sql : sqls) {
            stm.addBatch(sql);
          }
        }

        int[] r = stm.executeBatch();

        logger.debug("Db batch return, {}", r);
        return r;

      } catch (SQLException e) {
        throw new SqlException(e);

      } finally {
        close(stm);
      }
    }

  }


  /** MUST CLOSE CONNECTION AFTER USING !!! */
  public  Connection  getConnection(ReplicaUse replicaUse) {
    return getConnFromDb(replicaUse);
  }

  public  <O> O       execute(Op<?, O> op) {
    Connection conn = null;
    try {
      conn = getConnForOp(op);
      return op.execute(conn);

    } finally {
      close(conn);
    }
  }

  /** convenient method for query just one record */
  public  <O> O       queryOne(Query<O> op) {
    List<O> r = execute(op.possibleCount(1));
    return r.isEmpty() ? null : r.get(0);
  }

  private Connection  getConnForOp(Op<?,?> op) {
    return getConnFromDb(op.replicaUse);
  }

  private static void close(Connection conn) {
    if (conn == null) {return;}
    try {conn.close();} catch (Exception e) {}
  }

  private static void close(Statement stm) {
    if (stm == null) {return;}
    try {stm.close();} catch (Exception e) {}
  }


  // Part 3.2.2: high-level ORM API

  /**
   * Get an object by id.
   * @param id an object with id filed(s) set.
   * @return an object queried from db by id or null if no such
   */
  public  <O> O       get(O id) {
    @SuppressWarnings("unchecked")
    Class<O> claz = (Class<O>) id.getClass();
    Ctm<O> ctm = Ctm.of(claz);

    StringBuilder sql    = new StringBuilder();
    List<Object>  params = new ArrayList<Object>(1);
    sql.append("select * from ").append(ctm.t);
    whereIds(id, ctm, sql, params);

    return queryOne(new Query<O>(claz, sql.toString(), params.toArray()));
  }

  /**
   * Add an object.
   * @param o an object to add
   * @return if success
   */
  public  boolean     add(Object o) {
    if (o == null) {return false;}

    Ctm<?> ctm = Ctm.of(o.getClass());
    StringBuilder sql = new StringBuilder();
    StringBuilder qms = new StringBuilder();
    List<Object>  params = new ArrayList<Object>();
    List<Fcm>     autoIncres = new ArrayList<Fcm>();

    // compose sql
    sql.append("insert into ").append(ctm.t).append("(");
    boolean first = true;
    for (Fcm fcm : ctm.fcms) {
      if (!fcm.fgetable()) {continue;}
      Object v = fcm.fget(o);

      if (fcm.autoIncre() && v == null) {
        autoIncres.add(fcm);
        continue;
      }

      sql.append(first ? "" : ",").append(fcm.cname);
      qms.append(first ? "" : ",").append("?");
      params.add(v);
      first = false;
    }
    sql.append(") value(").append(qms.toString()).append(")");

    // execute sql
    Update insert = new Update()
        .sql(sql.toString())
        .params(params.toArray())
        .returnGeneratedKeys(!autoIncres.isEmpty());
    int r = execute(insert);

    // set autoIncres (generated keys) into object
    if (!autoIncres.isEmpty() && insert.generatedKeys() != null) {
      if (autoIncres.size() == 1) {
        autoIncres.get(0).fset(o, insert.generatedKeys());
      } else {
        @SuppressWarnings("unchecked")
        Map<String,Object> generatedKeys =
            (Map<String,Object>) insert.generatedKeys();
        for (Fcm fcm : autoIncres) {
          Object generatedKey = generatedKeys.get(fcm.cname);
          if (generatedKey == null) {continue;}
          fcm.fset(o, generatedKey);
        }
      }
    }

    return r > 0;
  }

  /**
   * Update an object's fields by id.
   * @param o an object to update
   * @return if success
   */
  public  boolean     update(Object o) {
    return update(o, false);
  }

  /**
   * Update an object's (non-null if specified) fields by id.
   * @param o an object to update
   * @param onlyNonNullFields update only non-null fields
   * @return if success
   */
  public  boolean     update(Object o, boolean onlyNonNullFields) {
    Ctm<?> ctm = Ctm.of(o.getClass());
    StringBuilder sql = new StringBuilder();
    List<Object>  params = new ArrayList<Object>();

    // update {table}
    sql.append("update ").append(ctm.t);

    // set xx=?,xy=?
    boolean first = true;
    for (Fcm fcm : ctm.fcms) {
      if (!fcm.fgetable()) {continue;}
      Object v = fcm.fget(o);
      if (onlyNonNullFields && v == null) {
        continue;
      }

      sql.append(first ? " set " : ",").append(fcm.cname).append("=?");
      params.add(v);
      first = false;
    }
    if (params.isEmpty()) {
      throw new DbException("No fields to mod");
    }

    // where {id}=?
    first = true;
    for (Fcm fcm : ctm.fcms) {
      if (!fcm.id()) {continue;}
      if (!fcm.fgetable()) {
        throw new DbException("Id not specified");
      }
      Object v = fcm.fget(o);

      sql.append(first ? " where " : " and ").append(fcm.cname).append("=?");
      params.add(v);
      first = false;
    }

    return execute(new Update(sql.toString(), params.toArray())) > 0;
  }

  /**
   * Delete an object by id.
   * @param id an object with id filed(s) set.
   * @return if success
   */
  public  boolean     delete(Object id) {
    Ctm<?> ctm = Ctm.of(id.getClass());

    StringBuilder sql    = new StringBuilder();
    List<Object>  params = new ArrayList<Object>(1);
    sql.append("delete from ").append(ctm.t);
    whereIds(id, ctm, sql, params);

    return execute(new Update(sql.toString(), params.toArray())) > 0;
  }

  private void        whereIds(
      Object id, Ctm<?> ctm, StringBuilder sql, List<Object>  params) {
    boolean first  = true;
    for (Fcm fcm : ctm.fcms) {
      if (!fcm.id()) {continue;}
      if (!fcm.fgetable()) {
        throw new DbException("Id not specified");
      }
      sql.append(first ? " where " : " and ").append(fcm.cname).append("=?");
      params.add(fcm.fget(id));
      first = false;
    }
    if (params.isEmpty()) {
      throw new DbException("Id Field not specified");
    }
  }

}

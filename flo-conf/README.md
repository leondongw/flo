flo-conf provides function of configuration.

# Configuration method and priority
1. etcd (not implemented yet)<br/>
   Only this one support watch changes.
2. System env<br/>
   eg: in shell script, "export key=value" or 'env "key=value" bash'.
3. File specified by key "flo.conf.file" (default: "flo.conf").<br/>
   And file format is properties.
4. Class-path file specified by key "flo.conf.classpathFile" (default: "flo.conf").<br/>
   And file format is properties.

# Configuration keys
- flo.* : Keys start with "flo." is reserved.<br/>
  Aware that a few "flo.conf.*" keys are special, may not follow the configuration method and priority, see following for these keys.
- flo.conf.file: The conf file path.<br/>
  Default: "flo.conf".<br/>
  This value is only read from system properties or system env once when initializing.<br/>
  Note that this value is read from system properties first which can be set by like
  "java -Dflo.con.file=path/to/file"
- flo.conf.classpathFile: The conf class-path file path.<br/>
  Default: "flo.conf".<br/>
  This value is only read from system properties or system env once when initializing.<br/>
  Note that this value is read from system properties first which can be set by like
  "java -Dflo.con.classpathFile=path/to/classpathFile"

# Coding method
```java
Conf.get("key");
```
or see the test code.
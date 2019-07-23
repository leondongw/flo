package work.leond.flo.service;

public interface FuncFilter {

    /** Called before func execute. */
    void beforeFunc(Req req);

    /** Called after func execute. */
    void afterFunc(Resp resp);

}
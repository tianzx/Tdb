package net.tianzx.backend.tm;

//TM 通过维护 XID 文件来维护事务的状态
public interface TransactionManager {
    long begin();
    void commit(long xid);
    void abort(long xid);
    boolean isActive(long xid);
    boolean isCommitted(long xid);
    boolean isAborted(long xid);
    void close();
}

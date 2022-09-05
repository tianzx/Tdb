package net.tianzx.backend.dm.page;

import net.tianzx.backend.dm.pagecache.PageCache;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PageImpl implements Page{
    //    页号从 1 开始
    private int pageNumber;
    private byte[] data;
    private boolean dirty;
    private Lock lock;

    private PageCache pc;

    public PageImpl(int pageNumber, byte[] data, PageCache pc) {
        this.pageNumber = pageNumber;
        this.data = data;
        this.pc = pc;
        lock = new ReentrantLock();
    }

    @Override
    public void lock() {

    }

    @Override
    public void unlock() {

    }

    @Override
    public void release() {
        pc.release(this);
    }

    @Override
    public void setDirty(boolean dirty) {

    }

    @Override
    public boolean isDirty() {
        return false;
    }

    @Override
    public int getPageNumber() {
        return 0;
    }

    @Override
    public byte[] getData() {
        return new byte[0];
    }
}

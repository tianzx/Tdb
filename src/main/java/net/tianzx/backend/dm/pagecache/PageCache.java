package net.tianzx.backend.dm.pagecache;

import net.tianzx.backend.dm.page.Page;

public interface PageCache {
    //8K
    public static final int PAGE_SIZE = 1 << 13;

    int newPage(byte[] initData);
    Page getPage(int pgno) throws Exception;
    void close();
    void release(Page page);

    void truncateByBgno(int maxPgno);
    int getPageNumber();
    void flushPage(Page pg);
}

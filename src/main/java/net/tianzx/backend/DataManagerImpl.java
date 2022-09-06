package net.tianzx.backend;

import net.tianzx.backend.common.AbstractCache;
import net.tianzx.backend.dm.Recover;
import net.tianzx.backend.dm.dataitem.DataItem;
import net.tianzx.backend.dm.dataitem.DataItemImpl;
import net.tianzx.backend.dm.logger.Logger;
import net.tianzx.backend.dm.page.Page;
import net.tianzx.backend.dm.page.PageOne;
import net.tianzx.backend.dm.page.PageX;
import net.tianzx.backend.dm.pageIndex.PageIndex;
import net.tianzx.backend.dm.pageIndex.PageInfo;
import net.tianzx.backend.dm.pagecache.PageCache;
import net.tianzx.backend.tm.TransactionManager;
import net.tianzx.backend.utils.Panic;
import net.tianzx.backend.utils.Types;
import net.tianzx.common.Error;

public class DataManagerImpl extends AbstractCache<DataItem> implements DataManager {
    TransactionManager tm;
    PageCache pc;
    Logger logger;
    PageIndex pIndex;
    Page pageOne;

    public DataManagerImpl(int maxResource) {
        super(maxResource);
    }

    public DataManagerImpl(PageCache pc, Logger logger, TransactionManager tm) {
        super(0);
        this.pc = pc;
        this.logger = logger;
        this.tm = tm;
        this.pIndex = new PageIndex();
    }

    @Override
    public DataItem read(long uid) throws Exception {
        DataItemImpl di = (DataItemImpl) super.get(uid);
        if (!di.isValid()) {
            di.release();
            return null;
        }
        return di;
    }

    /**
     * 在 pageIndex 中获取一个足以存储插入内容的页面的页号，获取页面后，首先需要写入插入日志，接着才可以通过 pageX 插入数据，并返回插入位置的偏移。最后需要将页面信息重新插入 pageIndex。
     *
     * @param xid
     * @param data
     * @return
     * @throws Exception
     */
    @Override
    public long insert(long xid, byte[] data) throws Exception {
        byte[] raw = DataItem.wrapDataItemRaw(data);
        if (raw.length > PageX.MAX_FREE_SPACE) {
            throw Error.DataTooLargeException;
        }
        // 尝试获取可用页
        PageInfo pi = null;
        for (int i = 0; i < 5; i++) {
            pi = pIndex.select(raw.length);
            if (pi != null) {
                break;
            } else {
                int newPgno = pc.newPage(PageX.initRaw());
                pIndex.add(newPgno, PageX.MAX_FREE_SPACE);
            }
        }
        if (pi == null) {
            throw Error.DatabaseBusyException;
        }
        Page pg = null;
        int freeSpace = 0;
        try {
            pg = pc.getPage(pi.pgno);
            //build redo log
            byte[] log = Recover.insertLog(xid, pg, raw);
            logger.log(log);
            //data log
            short offset = PageX.insert(pg, raw);

            pg.release();
            return Types.addressToUid(pi.pgno, offset);
        } finally {
            // 将取出的pg重新插入pIndex
            if(pg != null) {
                pIndex.add(pi.pgno, PageX.getFreeSpace(pg));
            } else {
                pIndex.add(pi.pgno, freeSpace);
            }
        }
    }

    @Override
    public void close() {
        super.close();
        logger.close();

        PageOne.setVcClose(pageOne);
        pageOne.release();
        pc.close();
    }

    /**
     * DataManager 是 DM 层直接对外提供方法的类，同时，也实现成 DataItem 对象的缓存。DataItem 存储的 key，是由页号和页内偏移组成的一个 8 字节无符号整数，页号和偏移各占 4 字节。
     * DataItem 缓存，getForCache()，只需要从 key 中解析出页号，从 pageCache 中获取到页面，再根据偏移，解析出 DataItem 即可：
     *
     * @param uid == pageNum + offerset
     * @return
     * @throws Exception
     */

    @Override
    protected DataItem getForCache(long uid) throws Exception {
        short offset = (short) (uid & ((1L << 16) - 1));
        uid >>>= 32;
        int pgno = (int) (uid & ((1L << 32) - 1));
        Page pg = pc.getPage(pgno);
        return DataItem.parseDataItem(pg, offset, this);
    }

    @Override
    protected void releaseForCache(DataItem di) {
        di.page().release();
    }

    // 为xid生成update日志
    public void logDataItem(long xid, DataItem di) {
        byte[] log = Recover.updateLog(xid, di);
        logger.log(log);
    }

    public void releaseDataItem(DataItem di) {
        super.release(di.getUid());
    }

    // 在创建文件时初始化PageOne
    void initPageOne() {
        int pgno = pc.newPage(PageOne.InitRaw());
        assert pgno == 1;
        try {
            pageOne = pc.getPage(pgno);
        } catch (Exception e) {
            Panic.panic(e);
        }
        pc.flushPage(pageOne);
    }

    // 在打开已有文件时时读入PageOne，并验证正确性
    boolean loadCheckPageOne() {
        try {
            pageOne = pc.getPage(1);
        } catch (Exception e) {
            Panic.panic(e);
        }
        return PageOne.checkVc(pageOne);
    }

    // 初始化pageIndex
    void fillPageIndex() {
        int pageNumber = pc.getPageNumber();
        for (int i = 2; i <= pageNumber; i++) {
            Page pg = null;
            try {
                pg = pc.getPage(i);
            } catch (Exception e) {
                Panic.panic(e);
            }
            pIndex.add(pg.getPageNumber(), PageX.getFreeSpace(pg));
            pg.release();
        }
    }
}

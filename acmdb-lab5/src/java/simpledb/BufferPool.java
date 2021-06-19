package simpledb;

import java.io.*;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 *
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int PAGE_SIZE = 4096;

    private static int pageSize = PAGE_SIZE;
    private final LockArranger lockArranger;

    private int numPages;
    private Map<PageId, Page> pid2pg;


    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        this.numPages = numPages;
        this.lockArranger = LockArranger.GetlockArranger();
        pid2pg = new ConcurrentHashMap<>();
    }

    public static int getPageSize() {
      return pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
    	BufferPool.pageSize = pageSize;
    }

    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
    	BufferPool.pageSize = PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, an page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public  Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {

        if (pid == null) {
            throw new DbException("PID is NULL!");
        }

        lockArranger.acquireLock(tid, pid, perm);

        if(pid2pg.containsKey(pid)) {
            return pid2pg.get(pid);
        }
        else {
            if(pid2pg.size() >= numPages) {
                evictPage();
            }
            DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
            Page page = dbFile.readPage(pid);
            pid2pg.put(pid, page);
            return page;
        }
    }

    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void releasePage(TransactionId tid, PageId pid) {

        lockArranger.releasePage(tid, pid);
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */

    public void transactionComplete(TransactionId tid) throws IOException {
        transactionComplete(tid, true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for lab1|lab2
        return lockArranger.holdsLock(tid, p);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */

    public void transactionComplete(TransactionId tid, Boolean commit) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2

        ConcurrentHashMap<TransactionId, ConcurrentLinkedDeque<PageId>> tid2dirtypageIds
                = lockArranger.getTransactionDirtiedPages();

        if (tid2dirtypageIds.containsKey(tid)) {
            for (PageId pid : tid2dirtypageIds.get(tid)) {
                if (commit) {
                    flushPage(pid);
                    try {
                        this.getPage(tid, pid, Permissions.READ_WRITE);
                    } catch (Exception e) {
                    }
                } else {
                    int tableId = pid.getTableId();
                    Page page = Database.getCatalog().getDatabaseFile(tableId).readPage(pid);
                    pid2pg.replace(pid, page);
                    page.markDirty(false, null);
                }
            }
        }
        lockArranger.releasePages(tid);
    }

    private void insertPageToBufferPool(Page page) throws DbException {
        if (pid2pg.size() == this.numPages) {
            evictPage();
        }
        pid2pg.put(page.getId(), page);
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */


    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        DbFile tableFile = Database.getCatalog().getDatabaseFile(tableId);
        ArrayList<Page> dirtyPages = tableFile.insertTuple(tid, t);
//        System.out.println(dirtyPages);
//        assert(false);
        for(Page dirtyPage : dirtyPages) {
            dirtyPage.markDirty(true, tid );
            discardPage(dirtyPage.getId());
            insertPageToBufferPool(dirtyPage);
        }
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */

    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        int tableId = t.getRecordId().getPageId().getTableId();
        DbFile tableFile = Database.getCatalog().getDatabaseFile(tableId);
        ArrayList<Page> dirtyPages = tableFile.deleteTuple(tid, t);
        for(Page dirtyPage : dirtyPages) {
            dirtyPage.markDirty(true, tid);
            insertPageToBufferPool(dirtyPage);
        }
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        for(PageId pid : pid2pg.keySet())
            flushPage(pid);
    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.

        Also used by B+ tree files to ensure that deleted pages
        are removed from the cache so they can be reused safely
    */
    public synchronized void discardPage(PageId pid) {
        pid2pg.remove(pid);
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        if(pid2pg.containsKey(pid)) {
            Page page = pid2pg.get(pid);
            if(page.isDirty() == null)
                return;
            int tableId = page.getId().getTableId();
            DbFile dbFile = Database.getCatalog().getDatabaseFile(tableId);
            dbFile.writePage(page);
            page.markDirty(false, null);
        }
    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException {

        if (pid2pg.size() == 0)
            throw new DbException("Buffer is empty!");

//        PageId pid = pid2pg.keySet().iterator().next();
        PageId pid = null;

        for (PageId npid : pid2pg.keySet()) {
            Page page = pid2pg.get(npid);
            if (page != null && page.isDirty() == null) {
                pid = npid;
                break;
            }
        }

        if (pid == null)
            throw new DbException("Can't find the pid to evict.");

        try {
            flushPage(pid);
            discardPage(pid);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

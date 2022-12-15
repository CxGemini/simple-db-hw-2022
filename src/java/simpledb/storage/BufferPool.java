package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Permissions;
import simpledb.storage.dbfile.DbFile;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    /**
     * Default number of pages passed to the constructor. This is used by
     * other classes. BufferPool should use the numPages argument to the
     * constructor instead.
     */
    public static final int DEFAULT_PAGES = 50;
    /**
     * Bytes per page, including header.
     */
    private static final int DEFAULT_PAGE_SIZE = 4096;
    private static int pageSize = DEFAULT_PAGE_SIZE;
    private final int numPages;

    private LRUCache lruCache;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        this.lruCache = new LRUCache(numPages);
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
        BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid  the ID of the transaction requesting the page
     * @param pid  the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
            throws TransactionAbortedException, DbException {
        // some code goes here
        // TODO 事务..
        // bufferPool应直接放在直接内存
        if (lruCache.get(pid) == null) {
            DbFile file = Database.getCatalog().getDatabaseFile(pid.getTableId());
            Page page = file.readPage(pid);
            lruCache.put(pid, page);
        }
        return lruCache.get(pid);

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
    public void unsafeReleasePage(TransactionId tid, PageId pid) {
        // TODO: some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) {
        // TODO: some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Return true if the specified transaction has a lock on the specified page
     */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // TODO: some code goes here
        // not necessary for lab1|lab2
        return false;
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid    the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit) {
        // TODO: some code goes here
        // not necessary for lab1|lab2
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other
     * pages that are updated (Lock acquisition is not needed for lab2).
     * May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid     the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t       the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        DbFile f = Database.getCatalog().getDatabaseFile(tableId);
        updateBufferPool(f.insertTuple(tid, t), tid);
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     * <p>
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have
     * been dirtied to the cache (replacing any existing versions of those pages) so
     * that future requests see up-to-date pages.
     *
     * @param tid the transaction deleting the tuple.
     * @param t   the tuple to delete
     */
    public void deleteTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        DbFile updateFile = Database.getCatalog().getDatabaseFile(t.getRecordId().getPageId().getTableId());
        List<Page> updatePages = updateFile.deleteTuple(tid, t);
        updateBufferPool(updatePages, tid);
    }

    /**
     * update:delete ; add
     *
     * @param updatePages 需要变为脏页的页列表
     * @param tid         the transaction to updating.
     */
    public void updateBufferPool(List<Page> updatePages, TransactionId tid) {
        for (Page page : updatePages) {
            page.markDirty(true, tid);
            // update bufferPool
            lruCache.put(page.getId(), page);
        }

    }


    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     * break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        // some code goes here
        // not necessary for lab1
        for (Map.Entry<PageId, LRUCache.Node> group : lruCache.getEntrySet()) {
            Page page = group.getValue().val;
            if (page.isDirty() != null) {
                this.flushPage(group.getKey());
            }
        }

    }

    /**
     * Remove the specific page id from the buffer pool.
     * Needed by the recovery manager to ensure that the
     * buffer pool doesn't keep a rolled back page in its
     * cache.
     * <p>
     * Also used by B+ tree files to ensure that deleted pages
     * are removed from the cache so they can be reused safely
     */
    public synchronized void removePage(PageId pid) {
        // some code goes here
        // not necessary for lab1
        if(pid != null){
            lruCache.removeByKey(pid);
        }

    }

    /**
     * Flushes a certain page to disk
     *
     * @param pid an ID indicating the page to flush
     */
    private synchronized void flushPage(PageId pid) throws IOException {
        // some code goes here
        // not necessary for lab1
        Page target = lruCache.get(pid);
        if(target == null){
            return;
        }
        TransactionId tid = target.isDirty();
        if (tid != null) {
            Page before = target.getBeforeImage();
            Database.getLogFile().logWrite(tid, before,target);
            Database.getCatalog().getDatabaseFile(pid.getTableId()).writePage(target);
        }
    }

    /**
     * Write all pages of the specified transaction to disk.
     */
    public synchronized void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        for (Map.Entry<PageId, LRUCache.Node> group : this.lruCache.getEntrySet()) {
            PageId pid = group.getKey();
            Page flushPage = group.getValue().val;
            TransactionId flushPageDirty = flushPage.isDirty();
            Page before = flushPage.getBeforeImage();
            // 涉及到事务就应该setBeforeImage
            flushPage.setBeforeImage();
            if (flushPageDirty != null && flushPageDirty.equals(tid)) {
                Database.getLogFile().logWrite(tid, before, flushPage);
                Database.getCatalog().getDatabaseFile(pid.getTableId()).writePage(flushPage);

            }
        }
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     * 直接在LRU中实现
     */
    private synchronized void evictPage() {
        // some code goes here
        // not necessary for lab1

    }

    private static class LRUCache {
        int cap,size;
        ConcurrentHashMap<PageId,Node> map ;
        Node head = new Node(null ,null);
        Node tail = new Node(null ,null);

        public LRUCache(int capacity) {
            this.cap = capacity;
            map = new ConcurrentHashMap<>();
            head.next = tail;
            tail.pre = head;
            size = 0;
        }

        public synchronized Page get(PageId key) {
            if(map.containsKey(key)){
                remove(map.get(key));
                moveToHead(map.get(key));
                return map.get(key).val ;
            }else{
                return null;
            }

        }

        public synchronized void put(PageId key, Page value) {
            Node newNode = new Node(key, value);
            if(map.containsKey(key)){
                remove(map.get(key));
            }else{
                size++;
                if(size > cap){
                    Node removeNode = tail.pre;
                    // 丢弃不是脏页的页
                    while (removeNode.val.isDirty() != null && removeNode != head){
                        removeNode = removeNode.pre;
                    }
                    if(removeNode != head && removeNode != tail){
                        map.remove(tail.pre.key);
                        remove(tail.pre);
                        size--;
                    }

                }
            }
            moveToHead(newNode);
            map.put(key,newNode);
        }
        public synchronized void remove(Node node){
            Node pre = node.pre;
            Node next = node.next;
            pre.next = next;
            next.pre = pre;
        }
        public synchronized void removeByKey(PageId key){
            Node node = map.get(key);
            remove(node);
        }


        public synchronized void moveToHead(Node node){
            Node next = head.next;

            head.next = node;
            node.pre = head;

            node.next = next;
            next.pre = node;
        }

        public synchronized int getSize(){
            return this.size;
        }


        private static class Node{
            PageId key;
            Page val;
            Node pre;
            Node next;
            public Node(PageId key ,Page val){
                this.key = key;
                this.val = val;
            }
        }

        public Set<Map.Entry<PageId, Node>> getEntrySet(){
            return map.entrySet();
        }

    }

}

package simpledb.storage;

import enums.LockType;
import lombok.*;
import simpledb.LogUtils;
import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Permissions;
import simpledb.execution.SeqScan;
import simpledb.index.BTreePageId;
import simpledb.storage.dbfile.DbFile;
import simpledb.storage.dbfile.HeapFile;
import simpledb.storage.iterator.DbFileIterator;
import simpledb.transaction.Transaction;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.IOException;
import java.util.*;
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
    private static final int X_LOCK_WAIT = 100;
    private static final int S_LOCK_WAIT = 100;
    private static final int RETRY_MAX = 3;
    private static int pageSize = DEFAULT_PAGE_SIZE;
    private final int numPages;

    private LRUCache lruCache;

    private LockManager lockManager;

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        this.lruCache = new LRUCache(numPages);
        this.lockManager = new LockManager();
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
        LockType lockType;
        int retry = 2;
        if(pid instanceof BTreePageId){
            retry = 2;
        }
        if(perm == Permissions.READ_ONLY){
            lockType = LockType.SHARE_LOCK;
        }else {
            lockType = LockType.EXCLUSIVE_LOCK;
        }
        try {
            // 如果获取lock失败（重试'RETRY_MAX'次）则直接放弃事务
            if (!lockManager.acquireLock(pid,tid,lockType,retry)){

                // 获取锁失败，回滚事务
                LogUtils.writeLog(LogUtils.ERROR,"tid:"+tid+"获取"+perm+"权限失败，进行回滚！！！");
                throw new TransactionAbortedException();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.out.println("Method 「 getPage 」获取锁发生异常！！！");
        }
        // bufferPool应直接放在直接内存
        if (!lruCache.contain(pid)) {
            DbFile file = Database.getCatalog().getDatabaseFile(pid.getTableId());
            Page page = file.readPage(pid);
            lruCache.put(pid, page);
        }
        return lruCache.get(pid);

    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) {
        // some code goes here
        // not necessary for lab1|lab2
        transactionComplete(tid,true);
    }



    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid    the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit) {
        // some code goes here
        // not necessary for lab1|lab2
        if(commit){
            try {
                flushPages(tid);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
           rollBack(tid);
        }
        lockManager.releasePagesByTid(tid);
    }

    @SneakyThrows
    public synchronized void rollBack(TransactionId tid){
        for (Map.Entry<PageId, LRUCache.Node> group : lruCache.getEntrySet()) {
            PageId pageId = group.getKey();
            Page page = group.getValue().val;
            if (tid.equals(page.isDirty())) {
                int tableId = pageId.getTableId();
                DbFile table = Database.getCatalog().getDatabaseFile(tableId);
                Page readPage = table.readPage(pageId);
                lruCache.removeByKey(group.getKey());
                lruCache.put(pageId,readPage);
            }
        }
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
    public synchronized void insertTuple(TransactionId tid, int tableId, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        LogUtils.writeLog(LogUtils.INFO, "「 thread -"+Thread.currentThread().getName() +" 」:insert a t「"+t.toString()+"」 into table :" +
                tableId+" and tid is :"+tid);
        DbFile f = Database.getCatalog().getDatabaseFile(tableId);
        updateBufferPool(f.insertTuple(tid, t), tid);
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
    public synchronized void unsafeReleasePage(TransactionId tid, PageId pid) {
        // some code goes here
        // not necessary for lab1|lab2
        lockManager.releasePage(tid,pid);
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
    public synchronized void deleteTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        LogUtils.writeLog(LogUtils.INFO,"「 thread -"+Thread.currentThread().getName() +" 」:delete a t「"+t.toString()+"」 and tid is :"+tid);
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
    public void updateBufferPool(List<Page> updatePages, TransactionId tid) throws DbException {
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

    void look(HeapFile hf, Transaction t, int v1, boolean present)
            throws DbException, TransactionAbortedException {
        int count = 0;
        SeqScan scan = new SeqScan(t.getId(), hf.getId(), "");
        scan.open();
        while(scan.hasNext()){
            Tuple tu = scan.next();
            int x = ((IntField)tu.getField(0)).getValue();
            if(x == v1)
                count = count + 1;
        }
        scan.close();
        if(count > 1)
            throw new RuntimeException("LogTest: tuple repeated");
        if(present && count < 1)
            throw new RuntimeException("LogTest: tuple missing");
        if(!present && count > 0)
            throw new RuntimeException("LogTest: tuple present but shouldn't be");
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
            // 涉及到事务提交就应该setBeforeImage，更新数据，方便后续的事务终止能回退此版本
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
            if(contain(key)){
                remove(map.get(key));
                moveToHead(map.get(key));
                return map.get(key).val ;
            }else{
                return null;
            }

        }

        public synchronized void put(PageId key, Page value) throws DbException {
            Node newNode = new Node(key, value);
            if(contain(key)){
                remove(map.get(key));
            }else{
                size++;
                if(size > cap){
                    Node removeNode = tail.pre;
                    // 丢弃不是脏页的页
                    while (removeNode.val.isDirty() != null){
                        removeNode = removeNode.pre;
                        if(removeNode == head || removeNode == tail){
                            throw new DbException("没有合适的页存储空间或者所有页都为脏页！！");
                        }
                    }
                    map.remove(removeNode.key);
                    remove(removeNode);
                    size--;
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
            Node node = getNodeByKey(key);
            if(node != null){
                remove(node);
                map.remove(Objects.requireNonNull(get(key)).getId());

            }else {
                LogUtils.writeLog(LogUtils.INFO,"「 LRU缓存 」:需要删除的节点已经不存在！");
            }

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

        public synchronized boolean contain(PageId key){
            for (PageId pageId : map.keySet()) {
                if (pageId.equals(key)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 因为传进来的都是new需要做等值判断
         */
        public synchronized Node getNodeByKey(PageId key){
            for (PageId pageId : map.keySet()) {
                if (pageId.equals(key)) {
                    return map.get(pageId);
                }
            }
            return null;
        }

        @Data
        private static class Node{
            PageId key;
            Page val;
            Node pre;
            Node next;
            public Node(PageId key ,Page val){
                this.key = key;
                this.val = val;
            }

            @Override
            public String toString(){
                return "Node: 「 key:"+ key +";value:" + val +" 」";
            }
        }

        public Set<Map.Entry<PageId, Node>> getEntrySet(){
            return map.entrySet();
        }
        @Override
        public String toString(){
            return "LRU: 「 cap:"+ cap +"size:" + size +" 」";
        }


    }

    @AllArgsConstructor
    @Data
    private static class PageLock{
        private TransactionId tid;
        private PageId pid;
        private LockType type;

    }

    private static class LockManager {
        @Getter
        public ConcurrentHashMap<PageId, ConcurrentHashMap<TransactionId, PageLock>> lockMap;

        public LockManager() {
            lockMap = new ConcurrentHashMap<>();
        }

        /**
         * Return true if the specified transaction has a lock on the specified page
         */
        public boolean holdsLock(TransactionId tid, PageId p) {
            // some code goes here
            // not necessary for lab1|lab2
            if(lockMap.get(p) == null){
                return false;
            }
            return lockMap.get(p).get(tid) != null;
        }



        public synchronized boolean acquireLock(PageId pageId, TransactionId tid, LockType requestLock, int reTry) throws TransactionAbortedException, InterruptedException {
            // 重传达到3次
            if (reTry == RETRY_MAX) return false;
            // 用于打印log
            // 页面上不存在锁
            if (lockMap.get(pageId) == null) {
                return putLock(tid,pageId,requestLock);
            }

            // 页面上存在锁
            ConcurrentHashMap<TransactionId, PageLock> tidLocksMap = lockMap.get(pageId);

            if (tidLocksMap.get(tid) == null) {
                // 页面上的锁不是自己的
                // 请求的为X锁
                if (requestLock == LockType.EXCLUSIVE_LOCK) {
                    wait(X_LOCK_WAIT);
                    return acquireLock(pageId, tid, requestLock, reTry + 1);
                } else if (requestLock == LockType.SHARE_LOCK) {
                    // 页面上是否都是读锁 -> 页面上的锁大于1个，就都是读锁
                    // 因为排它锁只能被一个事务占有
                    if (tidLocksMap.size() > 1) {
                        // 都是读锁直接获取
                        return putLock(tid,pageId,requestLock);
                    } else {
                        Collection<PageLock> values = tidLocksMap.values();
                        for (PageLock value : values) {
                            // 存在的唯一的一个锁为X锁
                            if (value.getType() == LockType.EXCLUSIVE_LOCK) {
                                wait(S_LOCK_WAIT);
                                return acquireLock(pageId, tid, requestLock, reTry + 1);
                            } else {
                                return putLock(tid,pageId,requestLock);
                            }
                        }
                    }
                }
            }else {
                if (requestLock == LockType.SHARE_LOCK) {
                    tidLocksMap.remove(tid);
                    return putLock(tid,pageId,requestLock);
                }else {
                    // 判断自己的锁是否为排它锁，如果是直接获取
                    if(tidLocksMap.get(tid).getType() == LockType.EXCLUSIVE_LOCK){
                        return true;
                    }else {
                        // 拥有的是读锁，判断是否还存在别的读锁
                        if(tidLocksMap.size() > 1){
                            wait(S_LOCK_WAIT);
                            return acquireLock(pageId, tid, requestLock, reTry + 1);
                        }else{
                            // 只有自己拥有一个读锁，进行锁升级
                            tidLocksMap.remove(tid);
                            return putLock(tid,pageId,requestLock);
                        }
                    }
                }
            }
            return false;
        }

        public boolean putLock(TransactionId tid, PageId pageId, LockType requestLock){
            ConcurrentHashMap<TransactionId, PageLock> tidLocksMap = lockMap.get(pageId);
            // 页面上一个锁都没
            if(tidLocksMap == null){
                tidLocksMap = new ConcurrentHashMap<>();
                lockMap.put(pageId,tidLocksMap);
            }
            PageLock pageLock = new PageLock(tid, pageId, requestLock);
            tidLocksMap.put(tid, pageLock);
            lockMap.put(pageId, tidLocksMap);
            return true;
        }


        /**
         * 释放某个事务上所有页的锁
         */
        public synchronized void releasePagesByTid(TransactionId tid){
            Set<PageId> pageIds = lockMap.keySet();
            for (PageId pageId : pageIds){
                releasePage(tid,pageId);
            }
        }


        /**
         * 释放某个页上tid的锁
         */
        public synchronized void releasePage(TransactionId tid, PageId pid) {
            if (holdsLock(tid,pid)){
                ConcurrentHashMap<TransactionId,PageLock> tidLocks = lockMap.get(pid);
                tidLocks.remove(tid);
                if (tidLocks.size() == 0){
                    lockMap.remove(pid);
                }
                // 释放锁时就唤醒正在等待的线程,因为wait与notifyAll都需要在同步代码块里，所以需要加synchronized
                this.notifyAll();
            }
        }


    }



}

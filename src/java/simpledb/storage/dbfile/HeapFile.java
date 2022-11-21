package simpledb.storage.dbfile;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Debug;
import simpledb.common.Permissions;
import simpledb.storage.*;
import simpledb.storage.iterator.DbFileIterator;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 *
 * @author Sam Madden
 * @see HeapPage#HeapPage
 */

public class HeapFile implements DbFile {

    /**
     * f the file that stores the on-disk backing store for this heap file.
     */
    private final File f;

    /**
     * 文件描述（consist of records）
     */
    private final TupleDesc td;


    /**
     * 写在内部类的原因是：DbFileIterator is the iterator interface that all SimpleDB Dbfile should
     */
    private static final class HeapFileIterator implements DbFileIterator {
        private final HeapFile heapFile;
        private final TransactionId tid;

        /**
         * 存储了堆文件迭代器
         */
        private Iterator<Tuple> tupleIterator;
        private int index;

        public HeapFileIterator(HeapFile file,TransactionId tid){
            this.heapFile = file;
            this.tid = tid;
        }
        @Override
        public void open() throws DbException, TransactionAbortedException {
            index = 0;
            tupleIterator = getTupleIterator(index);
        }

        private Iterator<Tuple> getTupleIterator(int pageNumber) throws TransactionAbortedException, DbException{
            if(pageNumber >= 0 && pageNumber < heapFile.numPages()){
                HeapPageId pid = new HeapPageId(heapFile.getId(),pageNumber);
                HeapPage page = (HeapPage)Database.getBufferPool().getPage(tid, pid, Permissions.READ_ONLY);
                return page.iterator();
            }else{
                throw new DbException(String.format("heapFile %d  does not exist in page[%d]!", pageNumber,heapFile.getId()));
            }
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            // TODO Auto-generated method stub
            if(tupleIterator == null){
                return false;
            }

            if(tupleIterator.hasNext()){
                return true;
            }else{
                if(index < (heapFile.numPages()-1)){
                    index++;
                    tupleIterator = getTupleIterator(index);
                    return tupleIterator.hasNext();
                }else{
                    return false;
                }
            }
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            if(tupleIterator == null || !tupleIterator.hasNext()){
                throw new NoSuchElementException();
            }
            return tupleIterator.next();
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            close();
            open();
        }

        @Override
        public void close() {
            tupleIterator = null;
        }

    }

    /**
     * Constructs a heap file backed by the specified file.
     *
     * @param f the file that stores the on-disk backing store for this heap
     *          file.
     */
    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        this.f = f;
        this.td = td;
    }

    /**
     * Returns the File backing this HeapFile on disk.
     *
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return f;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     *
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        return f.getAbsoluteFile().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     *
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return this.td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // some code goes here
        int tableId = pid.getTableId();
        int pgNo = pid.getPageNumber();
        int offset = pgNo * BufferPool.getPageSize();
        RandomAccessFile randomAccessFile = null;

        try{
            randomAccessFile = new RandomAccessFile(f,"r");
            // 起码有pgNo页那么大小就应该大于pgNo
            if((long) (pgNo + 1) *BufferPool.getPageSize() > randomAccessFile.length()){
                randomAccessFile.close();
                throw new IllegalArgumentException(String.format("table %d page %d is invalid", tableId, pgNo));
            }
            byte[] bytes = new byte[BufferPool.getPageSize()];
            // 移动偏移量到文件开头，并计算是否change
            randomAccessFile.seek(offset);
            int read = randomAccessFile.read(bytes,0,BufferPool.getPageSize());
            // Do not load the entire table into memory on the open() call
            // -- this will cause an out of memory error for very large tables.
            if(read != BufferPool.getPageSize()){
                throw new IllegalArgumentException(String.format("table %d page %d read %d bytes not equal to BufferPool.getPageSize() ", tableId, pgNo, read));
            }
            HeapPageId id = new HeapPageId(pid.getTableId(),pid.getPageNumber());
            return new HeapPage(id,bytes);
        }catch (IOException e){
            e.printStackTrace();
        }finally {
            try{
                if(randomAccessFile != null){
                    randomAccessFile.close();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        throw new IllegalArgumentException(String.format("table %d page %d is invalid", tableId, pgNo));
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // TODO: some code goes here
        // not necessary for lab1
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        // 通过文件长度算出所在bufferPool所需的页数
        System.out.println(String.format("file length :「 %d 」,BufferPool PageSize :「 %d 」 ",getFile().length(),BufferPool.getPageSize()));
        return (int) Math.floor(getFile().length() * 1.0 / BufferPool.getPageSize());
    }

    // see DbFile.java for javadocs
    public List<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // TODO: some code goes here
        return null;
        // not necessary for lab1
    }

    // see DbFile.java for javadocs
    public List<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // TODO: some code goes here
        return null;
        // not necessary for lab1
    }

    // see DbFile.java for javadocs

    /**
     * HeapFile与Heap为一一对应的关系，所以其实是获取BufferPool中对应页的元组迭代器
     */
    public DbFileIterator iterator(TransactionId tid) {
        //some code goes here
        return new HeapFileIterator(this,tid);

    }

}

package simpledb.storage;

import java.util.Objects;

/**
 * Unique identifier for HeapPage objects.
 */
public class HeapPageId implements PageId {

    /**
     * The table that is being referenced
     */
    private int tableId;

    /**
     * The page number in that table.
     */
    private int pgNo;
    /**
     * Constructor. Create a page id structure for a specific page of a
     * specific table.
     * 理解为是表id（HeapPageId)与其子页的关系映射的数据结构
     * 如表1-1 表1-2...
     * @param tableId The table that is being referenced
     * @param pgNo    The page number in that table.
     */
    public HeapPageId(int tableId, int pgNo) {
        //  some code goes here
        this.tableId = tableId;
        this.pgNo = pgNo;
    }

    /**
     * @return the table associated with this PageId
     */
    public int getTableId() {
        // TODO: some code goes here
        return tableId;
    }

    /**
     * @return the page number in the table getTableId() associated with
     *         this PageId
     */
    public int getPageNumber() {
        // TODO: some code goes here
        return pgNo;
    }

    public void setTableId(int tableId){
        this.tableId = tableId;
    }

    public void setPgNo(int pgNo){
        this.pgNo = pgNo;
    }

    /**
     * @return a hash code for this page, represented by a combination of
     *         the table number and the page number (needed if a PageId is used as a
     *         key in a hash table in the BufferPool, for example.)
     * @see BufferPool
     */
    public int hashCode() {
        // TODO: some code goes here
        // throw new UnsupportedOperationException("implement this");
        return getPageNumber()*1000 + getTableId();
    }

    /**
     * Compares one PageId to another.
     *
     * @param o The object to compare against (must be a PageId)
     * @return true if the objects are equal (e.g., page numbers and table
     *         ids are the same)
     */
    public boolean equals(Object o) {
        // TODO: some code goes here
        if (!(o instanceof  HeapPageId)) { return false; }

        HeapPageId other = (HeapPageId) o;
        if (this.getPageNumber() == other.getPageNumber() && this.getTableId() == other.getTableId()) {
            return true;
        }
        return false;
    }

    /**
     * Return a representation of this object as an array of
     * integers, for writing to disk.  Size of returned array must contain
     * number of integers that corresponds to number of args to one of the
     * constructors.
     */
    public int[] serialize() {
        int[] data = new int[2];

        data[0] = getTableId();
        data[1] = getPageNumber();

        return data;
    }

}

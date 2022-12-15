package simpledb.execution;

import simpledb.common.DbException;
import simpledb.common.Type;
import simpledb.storage.*;
import simpledb.transaction.TransactionAbortedException;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Knows how to compute some aggregate over a set of IntFields.
 */
public class IntegerAggregator implements Aggregator {

    private static final long serialVersionUID = 1L;

    private static final Field NO_GROUP_FIELD = new StringField("NO_GROUP_FIELD",20);

    /**
     * 需要分组的字段的索引（从0开始
     */
    private int groupByIndex;

    /**
     * 需要分组的字段类型
     */
    private Type groupByType;

    /**
     * 需要聚合的字段的索引（从0开始
     */
    private int aggregateIndex;

    /**
     * 需要聚合的操作
     */
    private Op aggOp;

    /**
     * 分组计算Map: MAX,MIN,COUNT,SUM,理论上都只需要这一个calMap计算出来，如果是计算平均值，值则需要计算每个数出现的次数
     */
    private Map<Field, GroupCalResult> groupCalMap;

    private Map<Field,Tuple> resultMap;



    /**
     * for groupCalMap
     */
    private static class GroupCalResult {

        public static final Integer DEFAULT_COUNT = 0;
        public static final Integer Deactivate_COUNT = -1;
        public static final Integer DEFAULT_RES = 0;
        public static final Integer Deactivate_RES = -1;
        /**
         * 当前分组计算的结果：SUM、AVG、MIN、MAX、SUM
         */
        private Integer result;

        /**
         * 当前Field出现的频度
         */
        private Integer count;

        public GroupCalResult(int result,int count){
            this.result = result;
            this.count = count;
        }
    }

    /**
     *  聚合后Tuple的desc
     *  Each tuple in the result is a pair of the form (groupValue, aggregateValue), unless the value of the group by
     *  field was Aggregator.NO_GROUPING, in which case the result is a single tuple of the form (aggregateValue).
     */
    private TupleDesc aggDesc;
    /**
     * Aggregate constructor
     *
     * @param gbField     the 0-based index of the group-by field in the tuple, or
     *                    NO_GROUPING if there is no grouping
     * @param gbFieldType the type of the group by field (e.g., Type.INT_TYPE), or null
     *                    if there is no grouping
     * @param afield      the 0-based index of the aggregate field in the tuple
     * @param what        the aggregation operator
     */

    public IntegerAggregator(int gbField, Type gbFieldType, int afield, Op what) {
        // some code goes here
        this.groupByIndex = gbField;
        this.groupByType = gbFieldType;
        this.aggregateIndex = afield;
        this.aggOp = what;

        this.groupCalMap = new ConcurrentHashMap<>();
        this.resultMap = new ConcurrentHashMap<>();

        if (this.groupByIndex >= 0) {
            // 有groupBy
            this.aggDesc = new TupleDesc(new Type[]{this.groupByType,Type.INT_TYPE}, new String[]{"groupVal","aggregateVal"});
        } else {
            // 无groupBy
            this.aggDesc = new TupleDesc(new Type[]{Type.INT_TYPE}, new String[]{"aggregateVal"});
        }
    }

    /**
     * Merge a new tuple into the aggregate, grouping as indicated in the
     * constructor
     *
     * @param tup the Tuple containing an aggregate field and a group-by field
     */
    public void mergeTupleIntoGroup(Tuple tup) {
        // some code goes here
        Field groupByField = this.groupByIndex == NO_GROUPING ? NO_GROUP_FIELD : tup.getField(this.groupByIndex);
        if(!NO_GROUP_FIELD.equals(groupByField) && groupByField.getType() != groupByType){
            throw new IllegalArgumentException("Except groupType is: 「"+ this.groupByType + " 」,But given "+ groupByField.getType());
        }
        if(!(tup.getField(this.aggregateIndex) instanceof  IntField)){
            throw new IllegalArgumentException("Except aggType is: 「 IntField 」" + ",But given "+ tup.getField(this.aggregateIndex).getType());
        }

        IntField aggField = (IntField) tup.getField(this.aggregateIndex);
        int curVal = aggField.getValue();

        // 如果没有分组，则groupByIndex = -1 ,如果没有分组的情况直接为null的话那么concurrentHashMap就不适合 , 则需要赋默认值
        // 不考虑并发的话，则直接用HashMap不需要默认值
        // 1、store
        switch (this.aggOp){
            case MIN:
                this.groupCalMap.put(groupByField,new GroupCalResult(Math.min(groupCalMap.getOrDefault(groupByField,
                            new GroupCalResult(Integer.MAX_VALUE,GroupCalResult.Deactivate_COUNT)).result,curVal),GroupCalResult.Deactivate_COUNT));
                break;
            case MAX:
                this.groupCalMap.put(groupByField,new GroupCalResult(Math.max(groupCalMap.getOrDefault(groupByField,
                        new GroupCalResult(Integer.MIN_VALUE,GroupCalResult.Deactivate_COUNT)).result,curVal),GroupCalResult.Deactivate_COUNT));
                break;
            case SUM:
                this.groupCalMap.put(groupByField,new GroupCalResult(groupCalMap.getOrDefault(groupByField,
                            new GroupCalResult(GroupCalResult.DEFAULT_RES,GroupCalResult.Deactivate_COUNT)).result+curVal, GroupCalResult.Deactivate_COUNT));
                break;
            case COUNT:
                this.groupCalMap.put(groupByField,new GroupCalResult(GroupCalResult.Deactivate_RES, groupCalMap.getOrDefault(groupByField,
                            new GroupCalResult(GroupCalResult.Deactivate_RES,GroupCalResult.DEFAULT_COUNT)).count+1));
                break;
            case AVG:
                GroupCalResult pre = this.groupCalMap.getOrDefault(groupByField, new GroupCalResult(GroupCalResult.DEFAULT_RES, GroupCalResult.DEFAULT_COUNT));
                this.groupCalMap.put(groupByField,new GroupCalResult(pre.result+curVal,pre.count+1));
                break;
            // TODO:in lab7
            case SC_AVG:
                break;
            // TODO:in lab7
            case SUM_COUNT:

        }

        // 2、cal
        Tuple curCalTuple = new Tuple(aggDesc);
        int curCalRes = 0;
        if(this.aggOp == Op.MIN || this.aggOp == Op.MAX || this.aggOp == Op.SUM){
            curCalRes = this.groupCalMap.get(groupByField).result;
        }else if(this.aggOp == Op.COUNT){
            curCalRes = this.groupCalMap.get(groupByField).count;
        }else if(this.aggOp == Op.AVG){
            // 因为是IntField所以必然精度会有问题
            curCalRes = this.groupCalMap.get(groupByField).result / this.groupCalMap.get(groupByField).count;
        }
        if (this.groupByIndex >= 0) {
            // 有groupBy
           curCalTuple.setField(0,groupByField);
           curCalTuple.setField(1,new IntField(curCalRes));
        } else {
            // 无groupBy
            curCalTuple.setField(0,new IntField(curCalRes));
        }

        // 3、update
        resultMap.put(groupByField,curCalTuple);


    }

    /**
     * Create a OpIterator over group aggregate results.
     *
     * @return a OpIterator whose tuples are the pair (groupVal, aggregateVal)
     *         if using group, or a single (aggregateVal) if no grouping. The
     *         aggregateVal is determined by the type of aggregate specified in
     *         the constructor.
     */
    public OpIterator iterator() {
        // some code goes here
        return new IntAggTupIterator();
    }

    private class IntAggTupIterator implements OpIterator {
        private boolean open = false;
        private Iterator<Map.Entry<Field, Tuple>> iter;

        @Override
        public void open() throws DbException, TransactionAbortedException {
            iter = resultMap.entrySet().iterator();
            open = true;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public boolean hasNext() throws DbException, TransactionAbortedException {
            if (open && iter.hasNext()) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Tuple next() throws DbException, TransactionAbortedException, NoSuchElementException {
            return iter.next().getValue();
        }

        @Override
        public void rewind() throws DbException, TransactionAbortedException {
            this.close();
            this.open();
        }

        @Override
        public TupleDesc getTupleDesc() {
            return aggDesc;
        }
    }

}

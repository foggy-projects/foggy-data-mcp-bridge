package com.foggyframework.dataset.resultset.query;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.resultset.*;
import com.foggyframework.dataset.resultset.support.*;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import org.springframework.context.ApplicationContext;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import com.foggyframework.dataset.resultset.Record;
@SuppressWarnings({"rawtypes", "unchecked"})
public class ResultSetQueryImpl implements ResultSetQuery {

    public abstract class Join {
        ListResultSet<?> joinResultSet;
        SelectColumn[] on = new SelectColumn[2];
        /**
         * start 1
         */
        int position = 1;

        Join next;

        public Join(ListResultSet<?> joinResultSet) {
            super();
            this.joinResultSet = joinResultSet;
        }

        public Join add(LeftJoin join) {
            join.position = position + 1;
            next = join;
            return next;
        }

        public abstract JoinType getJoinType();

        public abstract List<Record<?>> join() throws SQLException;

        abstract void join(JoinRecord joinRecord, List<Record<?>> data) throws SQLException;

        /**
         * 需要定义通过SelectColumn以区别是哪个表的字段,特别的,在于多表连接的情况
         *
         * @param leftColumn
         * @param rightColumn
         * @return
         */
        public ResultSetQuery on(SelectColumn leftColumn, SelectColumn rightColumn) {
            on[0] = leftColumn;
            on[1] = rightColumn;
            return ResultSetQueryImpl.this;
        }

        void startJoin() throws SQLException {
            if (next != null) {
                next.startJoin();
            }
        }

        void stopJoin() {
            if (next != null) {
                next.stopJoin();
            }
        }
    }

    ;

    static class JoinRecord extends BaseRecord<Object> implements Record<Object> {
        Record<?>[] records;

        public JoinRecord(Record<?>[] records) {
            super(0);
            this.records = records;
        }

        @Override
        public boolean canSet(String name) {
            return true;
        }

        public JoinRecord copy() {
            JoinRecord j = new JoinRecord(Arrays.copyOf(records, records.length));
            return j;
        }

        @Override
        public ListResultSetMetaData<Object> getMetaData() {
            return null;
        }

        @Override
        public Object getObject(int index) throws SQLException {
            return null;
        }

        @Override
        public Object getObject(SelectColumn sc) throws SQLException {
            return records[sc.position].getObject(sc);
        }

        @Override
        public Object getObject(String columnName) throws SQLException {
            return null;
        }

        @Override
        public Object getValue() {
            return null;
        }

        @Override
        public void set(int index, Object v) throws SQLException {

        }

        @Override
        public void set(SelectColumn sc, Object object) throws SQLException {
            records[sc.position].set(sc, object);
        }

        @Override
        public void set(String columnName, Object v) throws SQLException {

        }

        @Override
        public void set(String columnName, Object v, boolean errorIfNotFound) throws SQLException {

        }

        @Override
        public void setValue(Object t) {

        }

        @Override
        public String toJson() {
            throw new UnsupportedOperationException();
        }

    }

    @Deprecated
    public enum JoinType {
        LEFT, RIGHT, INNER
    }

    static class Key {
        Object[] values;

        public Key(Object[] values) {
            super();
            this.values = values;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            Key other = (Key) obj;
            if (!Arrays.equals(values, other.values))
                return false;
            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(values);
            return result;
        }
    }

    public class LeftJoin extends Join {

        ResultSetIndex index;

        public LeftJoin(ListResultSet<?> joinResultSet) {
            super(joinResultSet);
        }

        @Override
        public JoinType getJoinType() {
            return JoinType.LEFT;
        }

        @Override
        public List<Record<?>> join() throws SQLException {

            startJoin();

            List<Record<?>> data = new ArrayList<Record<?>>();
            baseResultSet.absolute(0);
            JoinRecord joinRecord = null;

            int size = 2;
            Join start = next;
            while (start != null) {
                start = start.next;
                size = size + 1;
            }
            while (baseResultSet.next()) {
                joinRecord = new JoinRecord(new Record[size]);
                joinRecord.records[0] = baseResultSet.getRecord();
                join(joinRecord, data);
            }

            stopJoin();

            return data;
        }

        @Override
        void join(JoinRecord joinRecord, List<Record<?>> data) throws SQLException {
            Object k = joinRecord.getObject(on[0]);
            Record<Object> rec = index.queryFrist(k);
            if (rec == null) {
                joinRecord.records[position] = Record.EMPTY;
            } else {
                joinRecord.records[position] = rec;

                // 判断还有没有记录
                int i = 1;
                Record<Object> n = index.next(k, i);
                while (n != null) {
                    JoinRecord jr = joinRecord.copy();
                    jr.records[position] = n;

                    if (next == null) {
                        data.add(jr);
                    } else {
                        next.join(jr, data);
                    }

                    i++;
                    n = index.next(k, i);
                }
            }
            if (next == null) {
                data.add(joinRecord);
            } else {
                next.join(joinRecord, data);
            }
        }

        @Override
        void startJoin() throws SQLException {
            super.startJoin();

            if (index == null)
                index = joinResultSet.index(joinResultSet.isUnique(on[1].as), 0, new Object[]{on[1].as});
        }

        // /**
        // * hash join
        // */
        // public JoinResultSet join(JoinResultSet joinResultSet)
        // throws SQLException {
        //
        // /**
        // * 采用简单的Hash Join方式，为右表的连接字段创建索引
        // */
        // ResultSetIndex index = joinResultSet.index(on[1].as,
        // joinResultSet.isUnique(on[1].as), 0);
        //
        // int i = 0;
        //
        // baseResultSet.absolute(0);
        // while (baseResultSet.next()) {
        // ArrayRecord<Object> rec = (ArrayRecord<Object>) newMeta
        // .newRecord(i);
        // Record<?> leftRec = baseResultSet.getRecord();
        // Record<?> rightRec = index.queryFrist(leftRec.getObject(on[0]));
        // for (SelectColumn sc : selectColumns) {
        // rec.values[sc.index] = sc.function.execute(baseResultSet
        // .getRecord());
        // }
        // }
        //
        // return joinResultSet;
        // }

        @Override
        void stopJoin() {
            super.stopJoin();
        }

    }

    public static class SqlEqExp implements Exp {
        SelectColumn sc;
        Object v;

        public SqlEqExp(SelectColumn sc, Object v) {
            super();
            this.sc = sc;
            this.v = v;
        }

        @Override
        public Object evalValue(ExpEvaluator ee) {
            WhereExpEvaluator wee = (WhereExpEvaluator) ee;
            Record<?> rec = wee.getRecord();
            try {
                Object o = rec.getObject(sc);
                return o == null ? false : v.equals(o);
            } catch (SQLException e) {
                throw RX.throwB(e);
            }

        }

        @Override
        public Class getReturnType(ExpEvaluator ee) {
            return Boolean.class;
        }

    }

    public static class SqlLikeExp implements Exp {
        SelectColumn sc;
        String v;

        public SqlLikeExp(SelectColumn sc, String v) {
            super();
            this.sc = sc;
            this.v = (v == null ? "" : v);
        }

        @Override
        public Object evalValue(ExpEvaluator ee) {
            WhereExpEvaluator wee = (WhereExpEvaluator) ee;
            Record<?> rec = wee.getRecord();
            try {
                Object o = rec.getObject(sc);
                // TODO bug 需要使用正则表达式等...
                return o == null ? false : (o.toString().indexOf(v) >= 0);
            } catch (SQLException e) {
                throw RX.throwB(e);
            }

        }

        @Override
        public Class getReturnType(ExpEvaluator ee) {
            return Boolean.class;
        }

    }

    private class Where {
        Exp whereExp;

        public void and(Exp exp) {
            if (whereExp == null) {
                whereExp = exp;
            } else {
                throw new UnsupportedOperationException();
            }
        }

        List<Record<?>> filt(List<Record<?>> data) {
            List<Record<?>> fdata = new ArrayList<Record<?>>();
            WhereExpEvaluator ee = new WhereExpEvaluator();
            for (Record<?> rec : data) {
                ee.record = rec;
                if (mach(rec, ee)) {
                    fdata.add(rec);
                }
            }
            return fdata;
        }

        private boolean mach(Record<?> rec, WhereExpEvaluator ee) {
            try {
                return (Boolean) whereExp.evalResult(ee);
            } catch (IllegalArgumentException e) {
                throw RX.throwB(e);
            }
        }
    }

    class WhereExpEvaluator extends DefaultExpEvaluator {

        Record<?> record;

        public WhereExpEvaluator() {
            super(null, null);
        }

        public WhereExpEvaluator(ApplicationContext appCtx, FsscriptClosure fScriptClosure) {
            super(appCtx, fScriptClosure);
        }

        @Override
        public ExpEvaluator clone() {
            WhereExpEvaluator expEvaluator = new WhereExpEvaluator(null,null);
            expEvaluator.setAppCtx(getAppCtx());

            expEvaluator.setStack(new Stack<>());

            expEvaluator.getStack().addAll(getStack());

            expEvaluator.setExpFactory(getExpFactory());
            expEvaluator.record= this.record;
            return expEvaluator;
        }

        public Record<?> getRecord() {
            return record;
        }

    }

    Where where;

    ListResultSet<?> baseResultSet;

    Join join = null;

    List<SelectColumn> groupBy = new ArrayList<SelectColumn>();

    List<SelectColumn> selectColumns = new ArrayList<SelectColumn>();

    public ResultSetQueryImpl(ListResultSet<?> baseResultSet) {
        super();
        this.baseResultSet = baseResultSet;
    }

    private ListResultSet<?> create(ListResultSetMetaData<?> meta, List<Record<?>> data) {
        PagingResultSet prs = baseResultSet.getDecorate(PagingResultSet.class);
        if (prs != null) {
            ListPagingResultSet rs = new ListPagingResultSet(meta, data, prs.getTotal(), prs.getStart(),
                    prs.getLimit());
            return rs;
        } else {
            ListResultSetSupport<Object> rs = new ListResultSetSupport(meta, data);
            return rs;
        }
    }

    private ListResultSet<?> doGroupBy(List<Record<?>> data)
            throws SQLException, IllegalArgumentException {
        if (!groupBy.isEmpty()) {

            /** 处理合并分组信息 ********************************************/
            LinkedHashMap<Key, RecordList<?>> key2RecordList = new LinkedHashMap<Key, RecordList<?>>();
            int gl = groupBy.size();
            Object[] values = null;
            RecordList<?> rl = null;
            int i = 0;

            for (Record<?> rec : data) {
                values = new Object[gl];

                i = 0;
                for (SelectColumn g : groupBy) {
                    values[i] = rec.getObject(g);
                    i++;
                }

                Key key = new Key(values);

                if (key2RecordList.containsKey(key)) {
                    rl = key2RecordList.get(key);
                } else {
                    rl = new RecordListImpl<Object>((ListResultSet) null);
                    key2RecordList.put(key, rl);
                }

                rl.add((Record) rec);
            }
            /***************************************************/

            /** 创建新的结果集 ********************************/
            List<Record<?>> newData = new ArrayList<Record<?>>();
            ArrayList xx = new ArrayList<String>();
            for (SelectColumn sc : selectColumns) {
                xx.add(sc.as);
            }
            ListResultSetMetaDataSupport<?> newMeta = new ListResultSetMetaDataSupport<Object>(xx);

            int j = 0;
            for (Key k : key2RecordList.keySet()) {
                rl = key2RecordList.get(k);
                ArrayRecord<?> rec = (ArrayRecord<?>) newMeta.newRecord(j);
                i = 0;
                for (SelectColumn sc : selectColumns) {
                    rec.values[i] = sc.function.apply(new Object[]{rl});
                    i++;
                }
                newData.add(rec);

                j++;
            }

            // ListResultSetSupport<Object> newRs = new ListResultSetSupport(
            // newMeta, newData);
            List<String> x = new ArrayList<String>(groupBy.size());
            for (SelectColumn g : groupBy) {
                x.add(g.getAs());
            }
            SimpleGroupResultSet rs = new SimpleGroupResultSet(newMeta, newData, x);

            return rs;
            // return create(newMeta, newData);
            /***************************************************/
        } else {
            boolean x = false;
            for (SelectColumn sc : selectColumns) {
                if (sc.function.getFunType() == SqlFunction.FunType.AGG) {
                    // Select语句中带聚合函数
                    x = true;
                }
            }
            if (x) {
                // 处理select中带聚合函数,但并未指定groupBy的情况
                // 这种情况下只返回一行记录

                List<Record<?>> newData = new ArrayList<Record<?>>(1);
                ListResultSetMetaDataSupport<?> newMeta = new ListResultSetMetaDataSupport<Object>(
                        new ArrayList<String>());
                for (SelectColumn sc : selectColumns) {
                    newMeta.addColumnName(sc.as);
                }

                ArrayRecord<Object> rec = (ArrayRecord<Object>) newMeta.newRecord(0);
                int i = 0;
                for (SelectColumn sc : selectColumns) {
                    rec.values[i] = sc.function.apply(new Object[]{new RecordListImpl(data)});
                    i++;
                }
                newData.add(rec);
                return create(newMeta, newData);

            }
        }
        return null;
    }

    private List<Record<?>> doWhere(List<Record<?>> data) {
        if (where != null) {
            return where.filt(data);
        }
        return data;
    }

    @Override
    public Exp eq(SelectColumn sc, Object v) {
        Exp exp = new SqlEqExp(sc, v);
        getWhere(true).and(exp);
        return exp;
    }

    private Where getWhere(boolean createIfNull) {
        if (where == null && createIfNull) {

            where = new Where();
        }
        return where;
    }

    // public void addSelect(String name) {
    //
    // }
    @Override
    public ResultSetQueryImpl groupBy(Object groupByObj) {
        if (groupByObj instanceof String) {
            groupBy.add(baseResultSet.getSelectColumn((String) groupByObj));
        } else if (groupByObj instanceof SelectColumn) {
            groupBy.add((SelectColumn) groupByObj);
        } else if (groupByObj instanceof List) {
            for (Object o : (List) groupByObj) {
                groupBy(o);
            }
        } else if (groupByObj instanceof Object[]) {
            for (Object o : (Object[]) groupByObj) {
                groupBy(o);
            }
            // groupByColumns = (Object[]) groupByObj;
        } else {
            throw new UnsupportedOperationException();
        }

        return this;

    }

    @Override
    public LeftJoin leftJoin(ListResultSet<?> rs) {
        if (join == null) {
            join = new LeftJoin(rs);
            return (LeftJoin) join;
        } else {
            return (LeftJoin) join.add(new LeftJoin(rs));
        }

    }

    @Override
    public Exp like(SelectColumn sc, String v) {
        Exp exp = new SqlLikeExp(sc, v);
        getWhere(true).and(exp);
        return exp;
    }

    /**
     * 目前query查询最简单的算法,未做任何优化,目前的应用场景为小数据量,注意,不支持Right Join及OUTER JOIN 执行顺序为:
     * <p>
     * 创建selectColumns ,如果有join,则加上Joint表的所有列
     * <p>
     * 如果有where条件 , 选择可以在这里过滤的条件进行过滤
     * <p>
     * 如果有Join,则先进行Join操作,多个Record组合成JoinRecord对象 多个连接的算法:
     * <p>
     * 如果有where条件,对Record列表进行过滤(注意,这个可以先对单结果进行过滤:利用索引进行优化)
     * <p>
     * 如果有GroupBy 则对Record列表进行GroupBy操作
     * <p>
     * 根据selectColumns创建ResultSetMeta
     * <p>
     * 根据selectColumns重新生成Record列表
     * <p>
     * 对还未能过滤的条件重新过滤
     * <p>
     * 如果有OrderBy,则进行OrderBy操作
     *
     * @throws SQLException
     */
    @Override
    public ListResultSet<?> query() throws SQLException {

        List<Record<?>> data = null;

        if (join != null) {
            // TODO 在join之前,可使用部分where 条件进行过滤以减少数据集大小(考虑使用索引)
            data = join.join();
        } else {
            data = (List) baseResultSet.getRecords();
            // TODO 可使用部分 where 条件对data进行过滤以减少数据集大小(考虑使用索引)
        }

        List<SelectColumn> allSelectColumn = new ArrayList<SelectColumn>();
        /***********************************************************/
        ListResultSet<?> xx = baseResultSet;
        for (SelectColumn sc : xx.getSelectColumns()) {
            sc.position = 0;
            allSelectColumn.add(sc);
        }
        Join xj = join;
        while (xj != null) {
            for (SelectColumn sc : xj.joinResultSet.getSelectColumns()) {
                sc.position = xj.position;
                allSelectColumn.add(sc);
            }
            xj = xj.next;
        }
        /***********************************************************/
        if (selectColumns.isEmpty()) {
            selectColumns = allSelectColumn;//
        }

        data = doWhere(data);

        ListResultSet<?> result = null;
        try {
            result = doGroupBy(data);
        } catch (IllegalArgumentException e) {
            throw RX.throwB(e);
        }
        if (result == null) {
            // no group by
            ListResultSetMetaDataSupport<?> newMeta = new ListResultSetMetaDataSupport<Object>(new ArrayList<String>());
            for (SelectColumn sc : selectColumns) {
                newMeta.addColumnName(sc.as);
            }

            // copy...
            List<Record<?>> newData = new ArrayList();
            Record tmp = null;
            int i = 0;
            int j = 0;
            for (Record r : data) {
                tmp = newMeta.newRecord(i);
                j = 1;
                for (SelectColumn sc : selectColumns) {
                    tmp.set(j, CommandUtils.execute(sc.function, r));

                    j++;
                }
                i++;
                newData.add(tmp);
            }

            result = create(newMeta, newData);
        }
        // TODO 对上面无法进行过滤的条件进行过滤 , 大部分情况是使用了聚合后的值
        return result;
    }

    @Override
    public ResultSetQueryImpl select(Object selectObj) {
        if (selectObj == null) {
            throw new UnsupportedOperationException("不支持select为空");
        }
        List<Object> selects = null;
        if (selectObj instanceof List) {
            selects = (List<Object>) selectObj;
        } else if (selectObj instanceof Object[]) {
            selects = new ArrayList<>();
            for (Object o : ((Object[]) selectObj)) {
                selects.add(o);
            }
        } else {
            selects = new ArrayList<>(1);
            selects.add(selectObj);
        }

        int i = 0;
        for (Object s : selects) {
            if (s instanceof String) {
                selectColumns.add(new SelectColumn((String) s, i, null));
            } else if (s instanceof SelectColumn) {
                selectColumns.add((SelectColumn) s);
            } else if (s instanceof Map) {
                final Function c = (Function) ((Map) s).get("column");
                if (c instanceof SqlFunction) {
                    selectColumns.add(new SelectColumn((String) ((Map) s).get("as"), i, (SqlFunction) c));
                } else {
                    selectColumns.add(new SelectColumn((String) ((Map) s).get("as"), i, new SqlFunction() {

                        @Override
                        public Object apply(Object[] args)
                                throws IllegalArgumentException {
                            return c.apply(args);
                        }

                        @Override
                        public FunType getFunType() {
                            return FunType.COMMON;
                        }
                    }));
                }

            } else {
                if (s == null) {
                    throw new UnsupportedOperationException("列不得为空！");
                }
                throw new UnsupportedOperationException();
            }
            i++;
        }
        return this;
    }
}

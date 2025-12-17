package com.foggyframework.dataset.resultset.support;

import com.foggyframework.dataset.resultset.ListResultSet;
import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.RecordList;
import com.foggyframework.dataset.resultset.query.SelectColumn;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class RecordListImpl<T> extends ArrayList<Record<T>> implements RecordList<T> {
    public static final RecordListImpl EMPTY_RECORDLIST = new RecordListImpl(0);
    /**
     *
     */
    private static final long serialVersionUID = -8763248330633095937L;

    ListResultSet<T> resultSet;

    public RecordListImpl(Collection<? extends Record<T>> c) {
        super(c);
    }

    public RecordListImpl(int initialCapacity) {
        super(initialCapacity);
    }

    public RecordListImpl(ListResultSet<T> resultSet) {
        super();
        this.resultSet = resultSet;
    }

    @Override
    public void commit() throws SQLException {
        if (!isEmpty() && resultSet != null) {
            resultSet.commit();
        }
    }

    @Override
    public void delete() {
        for (Record<T> rec : this) {
            rec.delete();
        }
    }

    @Override
    public void each(Function command) {
        for (Record<T> rec : this) {
            CommandUtils.execute(command, rec);
        }
    }

    @Override
    public List<T> getValues() {
        List<T> xx = new ArrayList<T>(size());
        for (Record<T> rec : this) {
            xx.add(rec.getValue());
        }
        return xx;
    }

    @Override
    public double sum(SelectColumn sc) throws SQLException {
        double x = 0;
        Object o = null;
        for (Record<T> rec : this) {
            o = rec.getObject(sc);
            if (o != null) {
                x = x + ((Number) o).doubleValue();
            }
        }
        return x;
    }

    @Override
    public double sum(String str) throws SQLException {
        double x = 0;
        Object o = null;
        for (Record<T> rec : this) {
            o = rec.getObject(str);
            if (o != null) {
                x = x + ((Number) o).doubleValue();
            }
        }
        return x;
    }

    public String toIn(String c) throws SQLException {

        StringBuilder sb = new StringBuilder("(");

        boolean first = true;
        for (Record rec : this) {
            if (first) {
                first = false;
            } else {
                sb.append(",");
            }
            Object v = rec.getObject(c);
            if (v == null) {
                sb.append("''");
            } else {
                sb.append("'").append(v.toString()).append("'");
            }
        }
        sb.append(")");

        return sb.toString();

    }
}

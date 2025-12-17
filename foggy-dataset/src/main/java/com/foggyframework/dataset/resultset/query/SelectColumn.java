package com.foggyframework.dataset.resultset.query;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.resultset.Record;
import com.foggyframework.dataset.resultset.RecordList;

import java.sql.SQLException;
import java.util.function.Function;

/**
 * 注意,ListResultSet必须不是线程安全的,所以它也不是
 *
 * @author Foggy
 */
public class SelectColumn {

    // ListResultSet<?> resultSet;

    public String as;

    public SqlFunction function;

    /**
     * start 0 ;
     */
    public int columnIndex;
    /**
     * start 0 ;
     */
    public int position;

    public SelectColumn(/** ListResultSet<?> resultSet, */
                        String as, int i, SqlFunction function) {
        super();
        this.columnIndex = i;
        this.as = as;
        RX.notNull(as, "as不能为空");
        this.function = function;

        if (this.function == null) {
            this.function = new SqlFunction() {

                @Override
                public Object apply(Object... args) {
                    Record<Object> rec = null;
                    if (args[0] instanceof RecordList) {
                        // throw new UnsupportedOperationException(
                        // "column : [" + SelectColumn.this.as
                        // + "]必须在groupBy中,或者指定聚合函数");
                        rec = (Record<Object>) ((RecordList) args[0]).get(0);
                    } else {
                        rec = (Record<Object>) args[0];
                    }
                    try {
                        return rec.getObject(SelectColumn.this);
                    } catch (SQLException e) {
                        throw RX.throwB(e);
                    }
                }

                @Override
                public FunType getFunType() {
                    return FunType.COMMON;
                }
            };
        }
        position = 0;
    }

    public String getAs() {
        return as;
    }

    public Function getFunction() {
        return function;
    }

    public void setAs(String as) {
        this.as = as;
    }

    public void setFunction(SqlFunction function) {
        this.function = function;
    }
}
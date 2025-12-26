package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.core.trans.ObjectTransFormatter;

import java.util.Map;

/**
 * select ${declare} ${alias}
 * <p>
 * eg. declare = 'tx.aaa' , alias='b'
 * <p>
 * select tx.aaa b
 */
public interface DbQueryColumn extends DbObject, DbColumn {
    DbColumn getSelectColumn();

    DbQueryCondition getJdbcQueryCond();


    Map<String, Object> getUi();

    default String getField() {
        return getJdbcQueryCond() == null ? null : getJdbcQueryCond().getField();
    }

    void setHasRef(boolean hasRef);

    boolean isHasRef();

     ObjectTransFormatter<?> getValueFormatter();
}

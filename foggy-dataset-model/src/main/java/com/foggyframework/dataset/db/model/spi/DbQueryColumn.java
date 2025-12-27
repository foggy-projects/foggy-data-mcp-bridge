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

    DbQueryCondition getDbQueryCond();


    Map<String, Object> getUi();

    default String getField() {
        return getDbQueryCond() == null ? null : getDbQueryCond().getField();
    }

    void setHasRef(boolean hasRef);

    boolean isHasRef();

     ObjectTransFormatter<?> getValueFormatter();
}

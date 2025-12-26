package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.fsscript.exp.FsscriptFunction;

public interface DbProperty extends DbObject {

    DbColumn getPropertyDbColumn();

    TableModel getTableModel();

    DbColumnType getType();

    String getFormat();

    <T> T getExtDataValue(String key);

    DbDataProvider getDataProvider();

    boolean isBit();

    void setFormulaBuilder(FsscriptFunction builder);

    default boolean isDict() {
        return false;
    }

    /**
     * 获取字典引用ID
     * @return 字典ID，如果未设置返回null
     */
    default String getDictRef() {
        return null;
    }
}

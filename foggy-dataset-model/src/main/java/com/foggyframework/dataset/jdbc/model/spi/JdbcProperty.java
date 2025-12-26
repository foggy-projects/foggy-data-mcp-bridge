package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.fsscript.exp.FsscriptFunction;

public interface JdbcProperty extends DbObject {

    JdbcColumn getPropertyJdbcColumn();

    JdbcModel getJdbcModel();

    JdbcColumnType getType();

    String getFormat();

    <T> T getExtDataValue(String key);

    JdbcDataProvider getDataProvider();

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

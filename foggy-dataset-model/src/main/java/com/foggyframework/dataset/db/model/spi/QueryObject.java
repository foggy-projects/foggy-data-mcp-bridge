package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.core.Decorate;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.fsscript.exp.FsscriptFunction;

public interface QueryObject extends Decorate, DbObject {
    String getAlias();

    String getPrimaryKey();

    String getForeignKey(QueryObject joinObject);

    String getBody();

    SqlColumn getSqlColumn(String name, boolean errorIfNotFound);

    SqlColumn appendSqlColumn(String name, String typeName, int length);

    String getForceIndex();

    void setForceIndex(String forceIndex);

    void setOnBuilder(FsscriptFunction onBuilder);

    FsscriptFunction getOnBuilder();

    QueryObject getLinkQueryObject();

    void setLinkQueryObject(QueryObject linkQueryObject);

    /**
     * 注册子查询对象的外键，用于嵌套维度场景
     * @param childQueryObject 子查询对象
     * @param foreignKey 外键列名（在本查询对象对应的表上）
     */
    default void registerChildForeignKey(QueryObject childQueryObject, String foreignKey) {
        // 默认空实现，子类可覆盖
    }

    default boolean isRootEqual(QueryObject queryObject) {
        if (queryObject == null) {
            return false;
        }
        return getRoot() == queryObject.getRoot();
    }


//   default String getForeignKey(){
//        return null;
//   }
//    String getSchema();


}

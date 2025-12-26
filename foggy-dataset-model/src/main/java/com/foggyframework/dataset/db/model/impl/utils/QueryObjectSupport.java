package com.foggyframework.dataset.db.model.impl.utils;

import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
public abstract class QueryObjectSupport extends DbObjectSupport implements QueryObject {

    protected SqlTable sqlTable;

    protected String alias;

    protected String primaryKey;

    protected String forceIndex;

    protected FsscriptFunction onBuilder;

    protected QueryObject linkQueryObject;

    /**
     * 子查询对象到外键的映射，用于嵌套维度场景
     */
    protected Map<Object, String> childForeignKeyMap;

    public QueryObjectSupport(SqlTable sqlTable) {
        this.sqlTable = sqlTable;
    }

    @Override
    public String getForeignKey(QueryObject joinObject) {
        // 检查是否有注册的子查询对象外键
        if (childForeignKeyMap != null && joinObject != null) {
            Object key = joinObject.getRoot();
            String fk = childForeignKeyMap.get(key);
            if (fk != null) {
                return fk;
            }
        }
        return null;
    }

    @Override
    public void registerChildForeignKey(QueryObject childQueryObject, String foreignKey) {
        if (childForeignKeyMap == null) {
            childForeignKeyMap = new HashMap<>();
        }
        childForeignKeyMap.put(childQueryObject.getRoot(), foreignKey);
    }

    @Override
    public SqlColumn getSqlColumn(String name, boolean errorIfNotFound) {
        return sqlTable.getSqlColumn(name, errorIfNotFound);
    }
    @Override
    public SqlColumn appendSqlColumn(String name, String typeName, int length) {
        return sqlTable.appendSqlColumn(name, typeName, length);
    }
}

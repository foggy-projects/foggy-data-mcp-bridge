package com.foggyframework.dataset.db.model.impl.utils;

import com.foggyframework.core.AbstractDecorate;
import com.foggyframework.dataset.db.model.impl.AiObject;
import com.foggyframework.dataset.db.model.spi.DbColumn;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SqlQueryObject extends AbstractDecorate implements QueryObject {

    String sql;

    String alias;

    String description;

    List<DbColumn> columns;

    public SqlQueryObject(String sql, String alias) {
        this.sql = sql;
        this.alias = alias;
    }

//    public SqlQueryObject(String sql, String alias, String schema) {
//        this.sql = sql;
//        this.alias = alias;
//        this.schema = schema;
//    }

    @Override
    public String getCaption() {
        return null;
    }

    @Override
    public String getName() {
        return null;
    }

    
    @Override
    public boolean _isDeprecated() {
        return false;
    }

    @Override
    public Object getExtData() {
        return null;
    }

    @Override
    public AiObject getAi() {
        return null;
    }

    @Override
    public String getAlias() {
        return alias;
    }

    @Override
    public String getPrimaryKey() {
        return null;
    }

    @Override
    public String getForeignKey(QueryObject joinObject) {
        return null;
    }

    @Override
    public String getBody() {
        return "(" + sql + ")";
    }

    @Override
    public SqlColumn getSqlColumn(String name, boolean errorIfNotFound) {
        return null;
    }

    @Override
    public SqlColumn appendSqlColumn(String name, String typeName, int length) {
        return null;
    }

    @Override
    public String getForceIndex() {
        return null;
    }

    @Override
    public void setForceIndex(String forceIndex) {

    }

    @Override
    public void setOnBuilder(FsscriptFunction onBuilder) {

    }

    @Override
    public FsscriptFunction getOnBuilder() {
        return null;
    }

    @Override
    public QueryObject getLinkQueryObject() {
        return null;
    }

    @Override
    public void setLinkQueryObject(QueryObject linkQueryObject) {

    }


}

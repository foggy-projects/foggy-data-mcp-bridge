package com.foggyframework.dataset.db.model.spi.support;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.model.impl.DbObjectSupport;
import com.foggyframework.dataset.db.model.spi.QueryObject;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import lombok.Data;

@Data
public class SimpleQueryObject extends DbObjectSupport implements QueryObject {

    String alias;

    protected String schema;

    String forceIndex;

    public static SimpleQueryObject of(String name, String alias, String schema) {
        SimpleQueryObject obj = new SimpleQueryObject();
        obj.setName(name);
        obj.schema = schema;
        obj.alias = alias;

        return obj;
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
        return buildBody();
    }

    private String buildBody() {
        if (StringUtils.isEmpty(schema)) {
            return name;
        }
        return "`" + schema + "`." + name;
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

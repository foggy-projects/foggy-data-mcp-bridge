package com.foggyframework.dataset.jdbc.model.impl.utils;

import com.foggyframework.core.AbstractDelegateDecorate;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.jdbc.model.impl.AiObject;
import com.foggyframework.dataset.jdbc.model.spi.QueryObject;
import com.foggyframework.fsscript.exp.FsscriptFunction;

//interval expression
public class QueryObjectDelegate extends AbstractDelegateDecorate<QueryObject> implements QueryObject {
    public QueryObjectDelegate(QueryObject delegate) {
        super(delegate);
    }

    public String getAlias() {
        return delegate.getAlias();
    }


    public String getPrimaryKey() {
        return delegate.getPrimaryKey();
    }

    public String getForeignKey(QueryObject joinObject) {
        return delegate.getForeignKey(joinObject);
    }

    @Override
    public String getBody() {
        return delegate.getBody();
    }

    @Override
    public SqlColumn getSqlColumn(String name, boolean errorIfNotFound) {
        return delegate.getSqlColumn(name, errorIfNotFound);
    }

    @Override
    public SqlColumn appendSqlColumn(String name, String typeName, int length) {
        return delegate.appendSqlColumn(name, typeName, length);
    }

    @Override
    public String getForceIndex() {
        return delegate.getForceIndex();
    }

    @Override
    public void setForceIndex(String forceIndex) {
        delegate.setForceIndex(forceIndex);
    }

    @Override
    public void setOnBuilder(FsscriptFunction onBuilder) {
        delegate.setOnBuilder(onBuilder);
    }

    @Override
    public FsscriptFunction getOnBuilder() {
        return delegate.getOnBuilder();
    }

    @Override
    public QueryObject getLinkQueryObject() {
        return delegate.getLinkQueryObject();
    }

    @Override
    public void setLinkQueryObject(QueryObject linkQueryObject) {
        delegate.setLinkQueryObject(linkQueryObject);
    }

    @Override
    public String getCaption() {
        return delegate.getCaption();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public boolean _isDeprecated() {
        return delegate._isDeprecated();
    }

    @Override
    public Object getExtData() {
        return delegate.getExtData();
    }

    @Override
    public AiObject getAi() {
        return delegate.getAi();
    }

    @Override
    public String toString() {
        return "QueryObjectDelegate{" +
                "delegate=" + delegate +
                '}';
    }
}

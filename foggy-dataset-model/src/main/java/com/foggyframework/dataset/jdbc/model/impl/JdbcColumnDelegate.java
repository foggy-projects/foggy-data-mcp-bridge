package com.foggyframework.dataset.jdbc.model.impl;

import com.foggyframework.core.AbstractDelegateDecorate;
import com.foggyframework.core.trans.ObjectTransFormatter;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.jdbc.model.spi.*;
import org.springframework.context.ApplicationContext;

public abstract class JdbcColumnDelegate extends AbstractDelegateDecorate<JdbcColumn> implements JdbcColumn, JdbcObject {


    public JdbcColumnDelegate(JdbcColumn delegate) {
        super(delegate);
    }
    @Override
    public String getDeclare() {
        return delegate.getDeclare();
    }

    @Override
    public String getDeclare(ApplicationContext appCtx, String alias) {
        return delegate.getDeclare(appCtx, alias);
    }

    @Override
    public String getDeclareOrder(ApplicationContext appCtx, String alias) {
        return delegate.getDeclareOrder(appCtx, alias);
    }

    @Override
    public String getSqlColumnName() {
        return delegate.getSqlColumnName();
    }

    @Override
    public String getAlias() {
        return delegate.getAlias();
    }

    @Override
    public String getField() {
        return delegate.getField();
    }

    @Override
    public QueryObject getQueryObject() {
        return delegate.getQueryObject();
    }

    @Override
    public SqlColumn getSqlColumn() {
        return delegate.getSqlColumn();
    }

    @Override
    public ObjectTransFormatter<?> getFormatter() {
        return delegate.getFormatter();
    }

    @Override
    public String buildSqlFragment(ApplicationContext appCtx,String alias, String s) {
        return delegate.buildSqlFragment(appCtx,alias, s);
    }

    @Override
    public JdbcAggregation getAggregation() {
        return delegate.getAggregation();
    }

    @Override
    public JdbcColumnType getType() {
        return delegate.getType();
    }

    @Override
    public boolean isMeasure() {
        return delegate.isMeasure();
    }

    @Override
    public boolean isDimension() {
        return delegate.isDimension();
    }

    @Override
    public boolean isProperty() {
        return delegate.isProperty();
    }

    @Override
    public boolean isCountColumn() {
        return delegate.isCountColumn();
    }

    @Override
    public String getAggregationFormula() {
        return delegate.getAggregationFormula();
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



}

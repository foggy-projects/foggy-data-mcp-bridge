package com.foggyframework.dataset.db.model.engine.query;

public interface JdbcQueryVisitor {
    void acceptSelect(JdbcQuery.JdbcSelect select);

    void acceptFrom(JdbcQuery.JdbcFrom from);

    void acceptWhere(JdbcQuery.JdbcWhere where);

    void acceptGroup(JdbcQuery.JdbcGroupBy group);

    void acceptHaving(JdbcQuery.JdbcHaving having);

    void acceptOrder(JdbcQuery.JdbcOrder order);
}

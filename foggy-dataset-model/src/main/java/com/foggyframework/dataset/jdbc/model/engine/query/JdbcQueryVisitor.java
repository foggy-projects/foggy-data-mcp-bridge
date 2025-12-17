package com.foggyframework.dataset.jdbc.model.engine.query;

public interface JdbcQueryVisitor {
    void acceptSelect(JdbcQuery.JdbcSelect select);

    void acceptFrom(JdbcQuery.JdbcFrom from);

    void acceptWhere(JdbcQuery.JdbcWhere where);

    void acceptOrder(JdbcQuery.JdbcOrder order);

    void acceptGroup(JdbcQuery.JdbcGroupBy group);
}

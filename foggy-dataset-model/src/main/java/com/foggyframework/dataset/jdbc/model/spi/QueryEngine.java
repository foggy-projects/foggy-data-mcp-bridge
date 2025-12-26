package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQuery;

public interface QueryEngine {
    JdbcQuery getJdbcQuery();

    QueryModel getJdbcQueryModel();
}

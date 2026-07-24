package com.foggyframework.dataset.model.spi;

import com.foggyframework.dataset.model.engine.query.JdbcQuery;

public interface QueryEngine {
    JdbcQuery getJdbcQuery();

    QueryModel getJdbcQueryModel();
}

package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;

public interface QueryEngine {
    JdbcQuery getJdbcQuery();

    QueryModel getJdbcQueryModel();
}

package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.impl.loader.JdbcModelLoadContext;

public interface DbModelLoadProcessor {
    DbDimension processDimension(JdbcModelLoadContext context, DbDimension dimension);

    DbMeasure processMeasure(JdbcModelLoadContext context, DbMeasure jdbcMeasure);

    DbProperty processProperty(JdbcModelLoadContext context, DbProperty dbProperty);
}

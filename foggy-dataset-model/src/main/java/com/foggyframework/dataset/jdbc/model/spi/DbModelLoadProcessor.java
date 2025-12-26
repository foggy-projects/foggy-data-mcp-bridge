package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.impl.loader.JdbcModelLoadContext;

public interface DbModelLoadProcessor {
    DbDimension processJdbcDimension(JdbcModelLoadContext context, DbDimension dimension);

    DbMeasure processJdbcMeasure(JdbcModelLoadContext context, DbMeasure jdbcMeasure);

    DbProperty processJdbcProperty(JdbcModelLoadContext context, DbProperty dbProperty);
}

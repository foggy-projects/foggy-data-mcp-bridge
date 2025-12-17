package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.impl.loader.JdbcModelLoadContext;

public interface JdbcModelLoadProcessor {
    JdbcDimension processJdbcDimension(JdbcModelLoadContext context, JdbcDimension dimension);

    JdbcMeasure processJdbcMeasure(JdbcModelLoadContext context, JdbcMeasure jdbcMeasure);

    JdbcProperty processJdbcProperty(JdbcModelLoadContext context, JdbcProperty jdbcProperty);
}

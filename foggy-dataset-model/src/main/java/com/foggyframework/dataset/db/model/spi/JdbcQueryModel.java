package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.dialect.FDialect;

public interface JdbcQueryModel extends QueryModel{
    FDialect getDialect();


//    default String getAlias(JdbcColumn jdbcColumn) {
//        return getAlias(jdbcColumn.getQueryObject());
//    }
//    default String getAlias(JdbcDimension dimension) {
//        return getAlias(dimension.getQueryObject());
//    }

}

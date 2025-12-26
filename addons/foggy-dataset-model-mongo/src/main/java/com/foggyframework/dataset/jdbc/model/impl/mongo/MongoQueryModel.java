package com.foggyframework.dataset.jdbc.model.impl.mongo;

import com.foggyframework.dataset.jdbc.model.spi.JdbcObject;
import com.foggyframework.dataset.jdbc.model.spi.QueryModel;

public interface MongoQueryModel extends JdbcObject, QueryModel {


//    default String getAlias(JdbcColumn jdbcColumn) {
//        return getAlias(jdbcColumn.getQueryObject());
//    }
//    default String getAlias(JdbcDimension dimension) {
//        return getAlias(dimension.getQueryObject());
//    }

}

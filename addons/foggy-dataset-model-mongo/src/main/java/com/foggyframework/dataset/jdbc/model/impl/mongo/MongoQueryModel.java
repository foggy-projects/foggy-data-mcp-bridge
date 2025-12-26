package com.foggyframework.dataset.jdbc.model.impl.mongo;

import com.foggyframework.dataset.jdbc.model.spi.DbObject;
import com.foggyframework.dataset.jdbc.model.spi.QueryModel;

public interface MongoQueryModel extends DbObject, QueryModel {


//    default String getAlias(JdbcColumn jdbcColumn) {
//        return getAlias(jdbcColumn.getQueryObject());
//    }
//    default String getAlias(JdbcDimension dimension) {
//        return getAlias(dimension.getQueryObject());
//    }

}

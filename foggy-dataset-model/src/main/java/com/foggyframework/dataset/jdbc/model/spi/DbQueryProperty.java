package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.impl.query.DbQueryAccessImpl;


public interface DbQueryProperty extends DbObject {
    DbProperty getJdbcProperty();

    DbQueryAccessImpl getQueryAccess();

//    JdbcQueryAccessImpl getQueryAccess();

//    List<JdbcDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, JdbcQueryProperty jdbcDimension, String hierarchy);
}

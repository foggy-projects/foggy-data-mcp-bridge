package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryAccessImpl;


public interface JdbcQueryProperty extends JdbcObject{
    JdbcProperty getJdbcProperty();

    JdbcQueryAccessImpl getQueryAccess();

//    JdbcQueryAccessImpl getQueryAccess();

//    List<JdbcDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, JdbcQueryProperty jdbcDimension, String hierarchy);
}

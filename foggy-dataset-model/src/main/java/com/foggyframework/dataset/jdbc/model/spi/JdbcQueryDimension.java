package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.jdbc.model.common.result.JdbcDataItem;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryAccessImpl;

import javax.sql.DataSource;
import java.util.List;


public interface JdbcQueryDimension extends DbObject {
    JdbcDimension getDimension();

    JdbcQueryAccessImpl getQueryAccess();

    List<JdbcDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, JdbcQueryDimension jdbcDimension, String hierarchy);

}

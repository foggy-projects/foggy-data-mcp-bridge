package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.jdbc.model.common.result.JdbcDataItem;
import com.foggyframework.dataset.jdbc.model.impl.query.DbQueryAccessImpl;

import javax.sql.DataSource;
import java.util.List;


public interface DbQueryDimension extends DbObject {
    DbDimension getDimension();

    DbQueryAccessImpl getQueryAccess();

    List<JdbcDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, DbQueryDimension jdbcDimension, String hierarchy);

}

package com.foggyframework.dataset.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.common.result.DbDataItem;
import com.foggyframework.dataset.model.impl.query.DbQueryAccessImpl;

import javax.sql.DataSource;
import java.util.List;


public interface DbQueryDimension extends DbObject {
    DbDimension getDimension();

    DbQueryAccessImpl getQueryAccess();

    List<DbDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, DbQueryDimension jdbcDimension, String hierarchy);

}

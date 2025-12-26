package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.common.result.DbDataItem;
import com.foggyframework.dataset.db.model.impl.query.DbQueryAccessImpl;

import javax.sql.DataSource;
import java.util.List;


public interface DbQueryDimension extends DbObject {
    DbDimension getDimension();

    DbQueryAccessImpl getQueryAccess();

    List<DbDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, DbQueryDimension jdbcDimension, String hierarchy);

}

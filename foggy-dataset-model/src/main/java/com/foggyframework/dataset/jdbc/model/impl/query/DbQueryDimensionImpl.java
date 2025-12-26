package com.foggyframework.dataset.jdbc.model.impl.query;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.jdbc.model.common.result.JdbcDataItem;
import com.foggyframework.dataset.jdbc.model.impl.JdbcObjectSupport;
import com.foggyframework.dataset.jdbc.model.spi.DbDimension;
import com.foggyframework.dataset.jdbc.model.spi.DbQueryDimension;
import com.foggyframework.dataset.jdbc.model.spi.QueryModel;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import com.foggyframework.dataset.utils.RowMapperUtils;
import com.foggyframework.fsscript.DefaultExpEvaluator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.sql.DataSource;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DbQueryDimensionImpl extends JdbcObjectSupport implements DbQueryDimension {

    DbDimension dimension;

    DbQueryAccessImpl queryAccess;

    QueryModel queryModel;

    public DbQueryDimensionImpl(QueryModel queryModel, DbDimension dimension) {
        this.dimension = dimension;
        this.queryModel = queryModel;
    }

    @Override
    public String getCaption() {
        return StringUtils.isEmpty(caption) ? dimension.getCaption() : caption;
    }

    @Override
    public String getName() {
        return StringUtils.isEmpty(name) ? dimension.getName() : name;
    }


    @Override
    public List<JdbcDataItem> queryDimensionDataByHierarchy(SystemBundlesContext systemBundlesContext, DataSource dataSource, DbQueryDimension jdbcDimension, String hierarchy) {
        if (queryAccess != null && queryAccess.getDimensionDataSql() != null) {
            Object sql = queryAccess.getDimensionDataSql().autoApply(DefaultExpEvaluator.newInstance(systemBundlesContext.getApplicationContext()));
            if (sql instanceof String) {
                List<JdbcDataItem> ll = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate().query((String) sql,  RowMapperUtils.getRowMapper(JdbcDataItem.class));
                return ll;
            } else {
                return (List<JdbcDataItem>) sql;
//                throw new UnsupportedOperationException();
            }
        }
        return dimension.queryDimensionDataByHierarchy(systemBundlesContext,dataSource, jdbcDimension.getDimension(), hierarchy);
    }
}

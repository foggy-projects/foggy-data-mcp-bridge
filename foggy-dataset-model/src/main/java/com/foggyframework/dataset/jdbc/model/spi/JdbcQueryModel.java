package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.jdbc.model.common.result.KpiResultImpl;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryOrderColumnImpl;
import com.foggyframework.dataset.jdbc.model.spi.support.JdbcColumnGroup;

import jakarta.annotation.Nullable;
import javax.sql.DataSource;
import java.util.List;

public interface JdbcQueryModel extends JdbcObject,QueryModel{

    /**
     * 获取模型的短简称
     *
     * <p>简称由 JdbcQueryModelLoader 在加载时自动分配，用于减少 AI 元数据的 token 消耗。
     * 简称规则：提取模型名称中驼峰词的首字母，如 FactSalesQueryModel → FS
     *
     * @return 模型短简称，如 "FS"、"FO"、"DP" 等
     */
    String getShortAlias();

    JdbcModel getJdbcModelByQueryObject(QueryObject queryObject);

    List<JdbcQueryOrderColumnImpl> getOrders();

    List<JdbcQueryProperty> getQueryProperties();


    JdbcQueryColumn getIdJdbcQueryColumn();

    JdbcQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<JdbcQueryRequestDef> form);

    QueryObject getQueryObject();

//    List<JdbcColumn> getSelectColumns();
    List<JdbcQueryColumn> getJdbcQueryColumns();

    List<JdbcQueryCondition> getJdbcQueryConds();

    JdbcModel getJdbcModel();


    JdbcColumn findJdbcColumnForCond(String jdbcColumName, boolean errorIfNotFound);

    JdbcQueryColumn findJdbcQueryColumnByName(String jdbcColumName, boolean errorIfNotFound);

    JdbcColumn findJdbcColumn(String name);

    JdbcDimension findDimension(String name);

    JdbcProperty findProperty(String name, boolean b);

    JdbcQueryDimension findQueryDimension(String dimensionName, boolean errorIfNotFound);

    JdbcQueryProperty findQueryProperty(String name, boolean errorIfNotFound);

    DataSource getDataSource();

    /**
     * 获取数据库方言
     * @return 当前数据源对应的方言
     */
    FDialect getDialect();

    JdbcColumn findJdbcColumnForCond(String condColumnName, boolean errorIfNotFound, boolean extSearch);

    JdbcQueryColumn findJdbcColumnForSelectByName(String columnName, boolean errorIfNotFound);

    JdbcQueryCondition findJdbcQueryCondByField(String name);

    List<JdbcQueryDimension> getQueryDimensions();

    List<JdbcColumnGroup> getColumnGroups();

    @Nullable
    JdbcQueryCondition findJdbcQueryCondByName(String name);

    List<JdbcColumn> getSelectColumns(boolean newList);

    List<JdbcModel> getJdbcModelList();

    String getAlias(QueryObject queryObject);

//    default String getAlias(JdbcColumn jdbcColumn) {
//        return getAlias(jdbcColumn.getQueryObject());
//    }
//    default String getAlias(JdbcDimension dimension) {
//        return getAlias(dimension.getQueryObject());
//    }

}

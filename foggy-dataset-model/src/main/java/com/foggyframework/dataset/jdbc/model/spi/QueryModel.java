package com.foggyframework.dataset.jdbc.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.Decorate;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.engine.query.JdbcQueryResult;
import com.foggyframework.dataset.jdbc.model.impl.query.JdbcQueryOrderColumnImpl;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.spi.support.QueryColumnGroup;
import jakarta.annotation.Nullable;

import java.util.List;

public interface QueryModel extends Decorate, DbObject {

    /**
     * 获取计算字段处理器
     * <p>
     * 不同类型的 QueryModel 返回对应的处理器实现：
     * <ul>
     *     <li>JdbcQueryModel: 返回 SqlCalculatedFieldProcessor</li>
     *     <li>MongoQueryModel: 返回 MongoCalculatedFieldProcessor</li>
     * </ul>
     * 默认返回 null 表示不支持计算字段。
     * </p>
     *
     * @return 计算字段处理器，或 null
     */
    default CalculatedFieldProcessor getCalculatedFieldProcessor() {
        return null;
    }

    /**
     * 获取模型的短简称
     *
     * <p>简称由 JdbcQueryModelLoader 在加载时自动分配，用于减少 AI 元数据的 token 消耗。
     * 简称规则：提取模型名称中驼峰词的首字母，如 FactSalesQueryModel → FS
     *
     * @return 模型短简称，如 "FS"、"FO"、"DP" 等
     */
    String getShortAlias();

    TableModel getJdbcModelByQueryObject(QueryObject queryObject);

    List<JdbcQueryOrderColumnImpl> getOrders();

    List<DbQueryProperty> getQueryProperties();


    DbQueryColumn getIdJdbcQueryColumn();

    JdbcQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form);

    /**
     * 执行查询（带预处理上下文）
     * <p>
     * 使用已预处理的 ModelResultContext 执行查询，
     * 适用于已通过 DataSetResultStep 处理的场景。
     * </p>
     *
     * @param systemBundlesContext 系统上下文
     * @param context              已预处理的查询上下文
     * @return 查询结果
     */
    default JdbcQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        // 默认实现：忽略 context，使用原有方法
        return query(systemBundlesContext, context.getRequest());
    }

    QueryObject getQueryObject();

    //    List<JdbcColumn> getSelectColumns();
    List<DbQueryColumn> getJdbcQueryColumns();

    List<DbQueryCondition> getJdbcQueryConds();

    TableModel getJdbcModel();


    DbColumn findJdbcColumnForCond(String jdbcColumName, boolean errorIfNotFound);

    DbQueryColumn findJdbcQueryColumnByName(String jdbcColumName, boolean errorIfNotFound);

    DbColumn findJdbcColumn(String name);

    DbDimension findDimension(String name);

    DbProperty findProperty(String name, boolean b);

    DbQueryDimension findQueryDimension(String dimensionName, boolean errorIfNotFound);

    DbQueryProperty findQueryProperty(String name, boolean errorIfNotFound);

//    DataSource getDataSource();
//
//    /**
//     * 获取数据库方言
//     * @return 当前数据源对应的方言
//     */
//    FDialect getDialect();

    DbColumn findJdbcColumnForCond(String condColumnName, boolean errorIfNotFound, boolean extSearch);

    DbQueryColumn findJdbcColumnForSelectByName(String columnName, boolean errorIfNotFound);

    DbQueryCondition findJdbcQueryCondByField(String name);

    List<DbQueryDimension> getQueryDimensions();

    List<QueryColumnGroup> getColumnGroups();

    @Nullable
    DbQueryCondition findJdbcQueryCondByName(String name);

    List<DbColumn> getSelectColumns(boolean newList);

    List<TableModel> getJdbcModelList();

    String getAlias(QueryObject queryObject);
}

package com.foggyframework.dataset.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.Decorate;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.permission.FieldPermissionsDef;
import com.foggyframework.dataset.model.def.permission.ModelPermissionsDef;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.join.JoinGraph;
import com.foggyframework.dataset.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.support.QueryColumnGroup;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import jakarta.annotation.Nullable;

import java.util.Collections;
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

    List<DbQueryOrderColumnImpl> getOrders();

    List<DbQueryProperty> getQueryProperties();


    DbQueryColumn getIdJdbcQueryColumn();

    DbQueryResult query(SystemBundlesContext systemBundlesContext, PagingRequest<DbQueryRequestDef> form);

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
    default DbQueryResult query(SystemBundlesContext systemBundlesContext, ModelResultContext context) {
        // 默认实现：忽略 context，使用原有方法
        return query(systemBundlesContext, context.getRequest());
    }

    QueryObject getQueryObject();

    //    List<JdbcColumn> getSelectColumns();
    List<DbQueryColumn> getJdbcQueryColumns();

    List<DbQueryCondition> getDbQueryConds();

    TableModel getJdbcModel();

    /**
     * 获取合并后的 JoinGraph
     * <p>
     * 对于单模型查询，返回主模型的 JoinGraph。
     * 对于多模型查询，返回合并后的 JoinGraph（包含所有模型的关联边）。
     * 结果会被缓存，多次调用不会重复构建。
     * </p>
     *
     * @return 合并后的 JoinGraph
     */
    JoinGraph getMergedJoinGraph();


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

    /**
     * 获取 QM 预定义的计算字段
     *
     * @return 预定义计算字段列表，默认空列表
     * @since 8.4.0
     */
    default List<CalculatedFieldDef> getPredefinedCalculatedFields() {
        return Collections.emptyList();
    }

    /**
     * 获取 QM 字段 ↔ 物理列的双向映射缓存
     * <p>
     * 在 QM 加载时自动构建，包含所有字段类型（度量、属性、维度、计算字段）的
     * QM 字段名 → 物理 table.column 映射和反向索引。
     * </p>
     *
     * @return 物理列映射，未构建时返回 null
     * @since 8.2.0
     */
    @Nullable
    default PhysicalColumnMapping getPhysicalColumnMapping() {
        return null;
    }

    /**
     * QM-level dynamic field permission narrowing layer.
     */
    @Nullable
    default FieldPermissionsDef getFieldPermissions() {
        return null;
    }

    /**
     * Query-model action authorization declaration.
     */
    @Nullable
    default ModelPermissionsDef getModelPermissions() {
        return null;
    }

    @Nullable
    DbQueryCondition findJdbcQueryCondByName(String name);

    List<DbColumn> getSelectColumns(boolean newList);

    List<TableModel> getJdbcModelList();

    String getAlias(QueryObject queryObject);

    /**
     * 获取维度表的 SQL 别名（便捷方法）
     *
     * @param dimensionName 维度名称
     * @return SQL 表别名，如 "d1"
     */
    default String getDimensionAlias(String dimensionName) {
        DbDimension dimension = findDimension(dimensionName);
        if (dimension == null) {
            return null;
        }
        return getAlias(dimension.getQueryObject());
    }

    /**
     * 获取权限查询构建器列表
     *
     * @return queryBuilder 函数列表
     */
    List<FsscriptFunction> getAccessBuilders();

    /**
     * 获取模型加载错误列表
     * <p>
     * 在查询模型加载过程中收集的所有错误信息。
     * 即使有错误，模型仍可能成功加载（优雅降级）。
     * </p>
     *
     * @return 错误列表，如果没有错误返回空列表
     * @since 8.3.0
     */
    default List<ModelLoadError> getLoadErrors() {
        return java.util.Collections.emptyList();
    }

    /**
     * 判断模型是否有错误
     *
     * @return 如果有错误返回 true
     * @since 8.3.0
     */
    default boolean hasErrors() {
        return !getLoadErrors().isEmpty();
    }

    /**
     * 判断模型是否有致命错误
     *
     * @return 如果有致命错误返回 true
     * @since 8.3.0
     */
    default boolean hasFatalErrors() {
        return getLoadErrors().stream()
                .anyMatch(e -> e.getErrorLevel() == ModelLoadError.ErrorLevel.FATAL);
    }

    /**
     * 获取模型加载状态
     *
     * @return 加载状态
     * @since 8.3.0
     */
    default ModelLoadStatus getLoadStatus() {
        return ModelLoadStatus.SUCCESS;
    }
}

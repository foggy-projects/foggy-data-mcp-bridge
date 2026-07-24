package com.foggyframework.dataset.model.spi;

import com.foggyframework.dataset.model.def.DbDefSupport;
import com.foggyframework.dataset.model.def.permission.FieldPermissionsDef;
import com.foggyframework.dataset.model.engine.join.JoinGraph;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;

import java.util.List;

public interface TableModel extends DbObject {

    String getIdColumn();

    DbDimension findJdbcDimensionByName(String name);

    DbMeasure findJdbcMeasureByName(String name);

    DbDimension addDimension(DbDimension dimension);

    DbMeasure addMeasure(DbMeasure measure);

    List<DbColumn> getVisibleSelectColumns();

    QueryObject getQueryObject();

    List<DbDimension> getDimensions();

    List<DbMeasure> getMeasures();


    DbColumn findJdbcColumnByName(String jdbcColumName);

    List<DbProperty> getProperties();

    DbProperty findJdbcPropertyByName(String name);

    DbProperty addJdbcProperty(DbProperty dbProperty);

    String getAlias();

    DbModelType getModelType();

    String getTableName();

    void addDeprecated(DbDefSupport def);

    boolean isDeprecated(String jdbcColumName);

    /**
     * 获取 JOIN 依赖图
     * <p>
     * JoinGraph 在模型加载时构建，包含所有维度和主表之间的关联关系。
     * 用于快速查找 JOIN 路径，替代运行时搜索。
     * </p>
     *
     * @return JOIN 依赖图
     */
    JoinGraph getJoinGraph();

    /**
     * 获取预聚合列表
     * <p>
     * 预聚合配置在模型加载时从 TM 文件解析并构建。
     * 用于查询时自动匹配和重写。
     * </p>
     *
     * @return 预聚合列表，如果未配置返回空列表
     * @since 8.2.0
     */
    List<PreAggregation> getPreAggregations();

    /**
     * TM-level dynamic field permission upper bound.
     */
    default FieldPermissionsDef getFieldPermissions() {
        return null;
    }

    /**
     * 获取模型加载错误列表
     * <p>
     * 在模型加载过程中收集的所有错误信息。
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

//    MongoTemplate getMongoTemplate();

//    DataSource getDataSource();

//    default boolean isImportant() {
//        return false;
//    }

}

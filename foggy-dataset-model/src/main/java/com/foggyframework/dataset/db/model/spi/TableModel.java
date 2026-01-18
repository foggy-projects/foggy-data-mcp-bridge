package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.db.model.def.DbDefSupport;
import com.foggyframework.dataset.db.model.engine.join.JoinGraph;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;

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

//    MongoTemplate getMongoTemplate();

//    DataSource getDataSource();

//    default boolean isImportant() {
//        return false;
//    }

}

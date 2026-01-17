package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 预聚合查询需求构建器
 * <p>
 * 从 JdbcQuery 和 DbQueryRequestDef 中提取查询需求，用于预聚合匹配。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggQueryRequirementBuilder {

    /**
     * 从查询请求和 JdbcQuery 构建查询需求
     *
     * @param queryRequest 查询请求
     * @param jdbcQuery    已构建的 JdbcQuery（包含解析后的列信息）
     * @param queryModel   查询模型
     * @return 查询需求
     */
    public PreAggQueryRequirement build(DbQueryRequestDef queryRequest,
                                         JdbcQuery jdbcQuery,
                                         JdbcQueryModel queryModel) {
        PreAggQueryRequirement requirement = new PreAggQueryRequirement();

        // 设置是否有分组
        requirement.setHasGroupBy(queryRequest.hasGroupBy());

        // 从 SELECT 列中提取维度和度量
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();
        if (select != null && select.getColumns() != null) {
            for (DbColumn column : select.getColumns()) {
                processColumn(column, requirement, queryModel);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Built query requirement: {}", requirement);
        }

        return requirement;
    }

    /**
     * 处理单个列，提取维度或度量信息
     */
    private void processColumn(DbColumn column, PreAggQueryRequirement requirement,
                                JdbcQueryModel queryModel) {
        if (column == null) {
            return;
        }

        // 跳过计算字段（暂不支持预聚合）
        if (column.isCalculatedField()) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping calculated field: {}", column.getName());
            }
            return;
        }

        // 判断是维度还是度量
        if (column.isDimension()) {
            processDimensionColumn(column, requirement, queryModel);
        } else if (column.isMeasure()) {
            processMeasureColumn(column, requirement);
        } else if (column.isProperty()) {
            processPropertyColumn(column, requirement, queryModel);
        }
    }

    /**
     * 处理维度列
     */
    private void processDimensionColumn(DbColumn column, PreAggQueryRequirement requirement,
                                         JdbcQueryModel queryModel) {
        DbDimensionColumn dimColumn = column.getDecorate(DbDimensionColumn.class);
        if (dimColumn == null) {
            return;
        }

        DbDimension dimension = dimColumn.getDimension();
        if (dimension == null) {
            return;
        }

        String dimensionName = dimension.getName();

        // 使用维度路径作为名称（如 product.category）
        if (dimension.getDimensionPath() != null) {
            dimensionName = dimension.getDimensionPath().toDotFormat();
        }

        requirement.addDimension(dimensionName);

        // 检测时间粒度
        TimeGranularity granularity = detectTimeGranularity(column, dimension);
        if (granularity != null) {
            requirement.setTimeGranularity(dimensionName, granularity);
        }

        // 如果是属性列，添加属性信息
        String propertyName = extractPropertyName(column);
        if (propertyName != null) {
            requirement.addDimensionProperty(dimensionName, propertyName);
        }
    }

    /**
     * 处理属性列
     * <p>
     * 属性列的处理：从列名中解析维度和属性名。
     * 列名格式通常为：{dimensionName}${propertyName}
     * 例如：customer$memberLevel, product$categoryName
     * </p>
     */
    private void processPropertyColumn(DbColumn column, PreAggQueryRequirement requirement,
                                        JdbcQueryModel queryModel) {
        DbPropertyColumn propColumn = column.getDecorate(DbPropertyColumn.class);
        if (propColumn == null) {
            return;
        }

        DbProperty property = propColumn.getProperty();
        if (property == null) {
            return;
        }

        // 从列名中解析维度和属性名
        // 列名格式：{dimensionName}${propertyName}
        String columnName = column.getName();
        if (columnName == null) {
            return;
        }

        int dollarIndex = columnName.indexOf('$');
        if (dollarIndex > 0 && dollarIndex < columnName.length() - 1) {
            String dimensionName = columnName.substring(0, dollarIndex);
            String propertyName = columnName.substring(dollarIndex + 1);
            requirement.addDimensionProperty(dimensionName, propertyName);
        }
    }

    /**
     * 处理度量列
     */
    private void processMeasureColumn(DbColumn column, PreAggQueryRequirement requirement) {
        String measureName = column.getName();
        DbAggregation aggregation = column.getAggregation();

        if (aggregation == null) {
            aggregation = DbAggregation.SUM; // 默认聚合方式
        }

        requirement.addMeasure(measureName, aggregation);
    }

    /**
     * 从列中检测时间粒度
     * <p>
     * 根据列的类型或名称推断时间粒度。
     * </p>
     */
    private TimeGranularity detectTimeGranularity(DbColumn column, DbDimension dimension) {
        // 检查列类型
        DbColumnType columnType = column.getType();
        if (columnType == null) {
            return null;
        }

        String columnName = column.getName().toLowerCase();

        // 根据列名推断粒度
        if (columnName.contains("year") || columnName.endsWith("_year")) {
            return TimeGranularity.YEAR;
        }
        if (columnName.contains("quarter") || columnName.endsWith("_quarter")) {
            return TimeGranularity.QUARTER;
        }
        if (columnName.contains("month") || columnName.endsWith("_month")) {
            return TimeGranularity.MONTH;
        }
        if (columnName.contains("week") || columnName.endsWith("_week")) {
            return TimeGranularity.WEEK;
        }
        if (columnName.contains("day") || columnName.endsWith("_day") || columnName.endsWith("_date")) {
            return TimeGranularity.DAY;
        }
        if (columnName.contains("hour") || columnName.endsWith("_hour")) {
            return TimeGranularity.HOUR;
        }
        if (columnName.contains("minute") || columnName.endsWith("_minute")) {
            return TimeGranularity.MINUTE;
        }

        // 根据列类型推断
        if (columnType == DbColumnType.DAY) {
            return TimeGranularity.DAY;
        }
        if (columnType == DbColumnType.DATETIME) {
            return TimeGranularity.MINUTE; // 默认分钟级
        }

        return null;
    }

    /**
     * 从列名中提取属性名
     * <p>
     * 例如：从 "product$category_name" 提取 "category_name"
     * </p>
     */
    private String extractPropertyName(DbColumn column) {
        String columnName = column.getName();
        if (columnName == null) {
            return null;
        }

        // 检查是否包含属性分隔符 $
        int lastDollar = columnName.lastIndexOf('$');
        if (lastDollar > 0 && lastDollar < columnName.length() - 1) {
            return columnName.substring(lastDollar + 1);
        }

        return null;
    }
}

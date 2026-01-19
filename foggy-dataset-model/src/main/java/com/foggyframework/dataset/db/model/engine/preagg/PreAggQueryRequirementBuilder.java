package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

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

        // 从 SELECT 列中提取维度和度量
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();
        if (select != null && select.getColumns() != null) {
            if (log.isDebugEnabled()) {
                log.debug("JdbcQuery has {} columns in select", select.getColumns().size());
                for (int i = 0; i < select.getColumns().size(); i++) {
                    DbColumn col = select.getColumns().get(i);
                    log.debug("  Column[{}]: name={}, class={}, isDimension={}, isMeasure={}, isProperty={}",
                            i, col != null ? col.getName() : "null",
                            col != null ? col.getClass().getSimpleName() : "null",
                            col != null && col.isDimension(),
                            col != null && col.isMeasure(),
                            col != null && col.isProperty());
                }
            }
            for (DbColumn column : select.getColumns()) {
                processColumn(column, requirement, queryModel);
            }
        } else {
            log.warn("JdbcQuery select is null or has no columns! select={}", select);
        }

        // 判断是否有 GROUP BY：
        // 1. 显式设置了 groupBy
        // 2. 隐式 GROUP BY：同时有维度（或维度属性）和度量时，SQL 会自动添加 GROUP BY
        boolean hasExplicitGroupBy = queryRequest.hasGroupBy();
        boolean hasImplicitGroupBy = !requirement.getDimensionNames().isEmpty()
                && !requirement.getMeasureAggregations().isEmpty();
        requirement.setHasGroupBy(hasExplicitGroupBy || hasImplicitGroupBy);

        // 判断是否有 WHERE 条件
        boolean hasWhereConditions = false;
        boolean hasCustomSqlConditions = false;

        // 检查 slices（结构化条件，可以透传到预聚合）
        if (queryRequest.getSlice() != null && !queryRequest.getSlice().isEmpty()) {
            hasWhereConditions = true;
            // 提取 slice 涉及的列
            extractSliceColumns(queryRequest.getSlice(), requirement);
        }

        // 检查 JdbcQuery 中的 WHERE 条件（自定义 SQL，无法透传）
        if (jdbcQuery.getWhere() != null && !jdbcQuery.getWhere().isEmpty()) {
            hasWhereConditions = true;
            hasCustomSqlConditions = true; // 自定义 SQL 条件
        }

        requirement.setHasWhereConditions(hasWhereConditions);
        requirement.setHasCustomSqlConditions(hasCustomSqlConditions);

        if (log.isDebugEnabled()) {
            log.debug("Built query requirement: {} (explicitGroupBy={}, implicitGroupBy={}, hasWhereConditions={}, hasCustomSqlConditions={})",
                    requirement, hasExplicitGroupBy, hasImplicitGroupBy, hasWhereConditions, hasCustomSqlConditions);
        }

        return requirement;
    }

    /**
     * 从 Slice 列表中提取涉及的列
     *
     * @param slices      Slice 条件列表
     * @param requirement 查询需求
     */
    private void extractSliceColumns(List<SliceRequestDef> slices, PreAggQueryRequirement requirement) {
        for (SliceRequestDef slice : slices) {
            extractSliceColumn(slice, requirement);
        }
    }

    /**
     * 从单个 Slice 中提取涉及的列（递归处理 $or/$and 组合）
     */
    private void extractSliceColumn(CondRequestDef cond, PreAggQueryRequirement requirement) {
        if (cond == null) {
            return;
        }

        // 处理逻辑组合条件
        if (cond._isLogicalGroup()) {
            List<CondRequestDef> children = cond._getGroupChildren();
            if (children != null) {
                for (CondRequestDef child : children) {
                    extractSliceColumn(child, requirement);
                }
            }
            return;
        }

        // 处理简单条件
        String field = cond.getField();
        if (field != null && !field.isEmpty()) {
            requirement.addSliceColumn(field);
            if (log.isDebugEnabled()) {
                log.debug("Extracted slice column: {}", field);
            }
        }
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

        // 记录列的类型以便调试
        if (log.isDebugEnabled()) {
            log.debug("Processing column: name={}, isDimension={}, isMeasure={}, isProperty={}",
                    column.getName(), column.isDimension(), column.isMeasure(), column.isProperty());
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
     * 时间粒度属性映射：属性名 -> TimeGranularity
     */
    private static final Map<String, TimeGranularity> TIME_PROPERTY_GRANULARITY = Map.of(
            "year", TimeGranularity.YEAR,
            "quarter", TimeGranularity.QUARTER,
            "month", TimeGranularity.MONTH,
            "week", TimeGranularity.WEEK,
            "day", TimeGranularity.DAY,
            "hour", TimeGranularity.HOUR,
            "minute", TimeGranularity.MINUTE
    );

    /**
     * 处理属性列
     * <p>
     * 属性列的处理：从列名中解析维度和属性名。
     * 列名格式通常为：{dimensionName}${propertyName}
     * 例如：customer$memberLevel, product$categoryName
     * </p>
     * <p>
     * 注意：属性列引用了某个维度，因此也需要将维度添加到需求中。
     * </p>
     * <p>
     * 特殊处理：时间粒度属性（如 salesDate$month）会被识别并设置为查询粒度。
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
            // 属性列引用了维度，需要同时添加维度和属性
            requirement.addDimension(dimensionName);
            requirement.addDimensionProperty(dimensionName, propertyName);

            // 检查是否是时间粒度属性
            TimeGranularity granularity = TIME_PROPERTY_GRANULARITY.get(propertyName.toLowerCase());
            if (granularity != null) {
                requirement.setTimeGranularity(dimensionName, granularity);
                if (log.isDebugEnabled()) {
                    log.debug("Detected time granularity from property: dimension={}, property={}, granularity={}",
                            dimensionName, propertyName, granularity);
                }
            }
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

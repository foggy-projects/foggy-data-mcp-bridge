package com.foggyframework.dataset.db.model.preagg.ddl;

import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.*;

/**
 * 预聚合 SQL 构建器
 * <p>
 * 构建预聚合相关的 SQL 语句：DDL、刷新 SQL 等。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggSqlBuilder {

    /**
     * 构建全量刷新的 INSERT ... SELECT 语句
     *
     * @param preAgg      预聚合配置
     * @param sourceModel 源模型
     * @return INSERT SQL
     */
    public String buildFullRefreshInsertSql(PreAggregation preAgg, TableModel sourceModel) {
        StringBuilder sql = new StringBuilder();
        String preAggTableName = getFullTableName(preAgg);

        // 收集列信息
        List<String> targetColumns = new ArrayList<>();
        List<String> selectExprs = new ArrayList<>();
        List<String> groupByExprs = new ArrayList<>();

        // 1. 处理维度列
        for (String dimName : preAgg.getDimensionNames()) {
            DbDimension dimension = sourceModel.findJdbcDimensionByName(dimName);
            if (dimension == null) {
                log.warn("Dimension '{}' not found in source model", dimName);
                continue;
            }

            // 获取维度的主键列
            String dimColumnName = getDimensionColumnName(dimension);
            TimeGranularity granularity = preAgg.getGranularity(dimName);

            if (granularity != null) {
                // 时间维度：应用粒度截断
                String truncatedExpr = buildDateTruncateExpr(dimColumnName, granularity);
                targetColumns.add(dimColumnName);
                selectExprs.add(truncatedExpr + " AS " + dimColumnName);
                groupByExprs.add(truncatedExpr);
            } else {
                // 普通维度
                targetColumns.add(dimColumnName);
                selectExprs.add(dimColumnName);
                groupByExprs.add(dimColumnName);
            }

            // 处理维度属性
            Set<String> props = preAgg.getDimensionProperties(dimName);
            for (String propName : props) {
                String propColumnName = dimName + "_" + propName;
                targetColumns.add(propColumnName);
                // 属性通常取 MAX 或 MIN（同一维度值的属性应该相同）
                selectExprs.add("MAX(" + propName + ") AS " + propColumnName);
            }
        }

        // 2. 处理度量列
        Map<String, DbAggregation> measureAggs = preAgg.getMeasureAggregations();
        Map<String, String> measureColumns = preAgg.getMeasureColumnNames();

        for (Map.Entry<String, DbAggregation> entry : measureAggs.entrySet()) {
            String measureName = entry.getKey();
            DbAggregation agg = entry.getValue();
            String targetColumnName = measureColumns.getOrDefault(measureName, measureName + "_" + agg.name().toLowerCase());

            DbMeasure measure = sourceModel.findJdbcMeasureByName(measureName);
            String sourceColumnName = measure != null ? measure.getJdbcColumn().getSqlColumnName() : measureName;

            targetColumns.add(targetColumnName);
            selectExprs.add(buildAggregationExpr(sourceColumnName, agg) + " AS " + targetColumnName);
        }

        // 3. 添加行数统计列
        targetColumns.add("_preagg_row_count");
        selectExprs.add("COUNT(*) AS _preagg_row_count");

        // 4. 添加时间戳列
        targetColumns.add("_preagg_created_at");
        selectExprs.add("NOW() AS _preagg_created_at");

        // 构建 SQL
        sql.append("INSERT INTO ").append(preAggTableName).append(" (");
        sql.append(String.join(", ", targetColumns));
        sql.append(") SELECT ");
        sql.append(String.join(", ", selectExprs));
        sql.append(" FROM ").append(sourceModel.getTableName());

        // TODO: 添加 JOIN（如果需要维表属性）

        if (!groupByExprs.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByExprs));
        }

        return sql.toString();
    }

    /**
     * 构建增量删除 SQL
     */
    public String buildIncrementalDeleteSql(PreAggregation preAgg, PreAggRefreshDef refreshConfig,
                                             LocalDate startDate, LocalDate endDate) {
        String tableName = getFullTableName(preAgg);
        String watermarkColumn = parseWatermarkColumnName(refreshConfig.getWatermarkColumn());

        return String.format("DELETE FROM %s WHERE %s >= '%s' AND %s <= '%s'",
                tableName, watermarkColumn, startDate, watermarkColumn, endDate);
    }

    /**
     * 构建增量插入 SQL
     */
    public String buildIncrementalInsertSql(PreAggregation preAgg, TableModel sourceModel,
                                             PreAggRefreshDef refreshConfig,
                                             LocalDate startDate, LocalDate endDate) {
        // 基于全量刷新 SQL 添加 WHERE 条件
        String fullSql = buildFullRefreshInsertSql(preAgg, sourceModel);
        String watermarkColumn = parseWatermarkColumnName(refreshConfig.getWatermarkColumn());

        // 在 GROUP BY 之前添加 WHERE 条件
        String whereClause = String.format(" WHERE %s >= '%s' AND %s <= '%s'",
                watermarkColumn, startDate, watermarkColumn, endDate);

        // 找到 GROUP BY 的位置并插入 WHERE
        int groupByIndex = fullSql.toUpperCase().indexOf(" GROUP BY");
        if (groupByIndex > 0) {
            return fullSql.substring(0, groupByIndex) + whereClause + fullSql.substring(groupByIndex);
        } else {
            return fullSql + whereClause;
        }
    }

    /**
     * 构建创建表 DDL
     */
    public String buildCreateTableDdl(PreAggregation preAgg, TableModel sourceModel) {
        StringBuilder ddl = new StringBuilder();
        String tableName = getFullTableName(preAgg);

        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> pkColumns = new ArrayList<>();

        // 维度列
        for (String dimName : preAgg.getDimensionNames()) {
            DbDimension dimension = sourceModel.findJdbcDimensionByName(dimName);
            if (dimension == null) continue;

            String dimColumnName = getDimensionColumnName(dimension);
            TimeGranularity granularity = preAgg.getGranularity(dimName);

            String dataType = granularity != null ? "DATE" : getColumnDataType(dimension);
            columnDefs.add("    " + dimColumnName + " " + dataType + " NOT NULL");
            pkColumns.add(dimColumnName);

            // 维度属性列
            for (String propName : preAgg.getDimensionProperties(dimName)) {
                String propColumnName = dimName + "_" + propName;
                columnDefs.add("    " + propColumnName + " VARCHAR(255)");
            }
        }

        // 度量列
        for (Map.Entry<String, String> entry : preAgg.getMeasureColumnNames().entrySet()) {
            String columnName = entry.getValue();
            columnDefs.add("    " + columnName + " DECIMAL(20,4)");
        }

        // 元数据列
        columnDefs.add("    _preagg_row_count BIGINT DEFAULT 1");
        columnDefs.add("    _preagg_created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        columnDefs.add("    _preagg_updated_at TIMESTAMP");

        // 主键
        if (!pkColumns.isEmpty()) {
            columnDefs.add("    PRIMARY KEY (" + String.join(", ", pkColumns) + ")");
        }

        ddl.append(String.join(",\n", columnDefs));
        ddl.append("\n)");

        return ddl.toString();
    }

    // ==================== 辅助方法 ====================

    private String getFullTableName(PreAggregation preAgg) {
        String schema = preAgg.getSchema();
        String tableName = preAgg.getTableName();
        if (schema != null && !schema.isEmpty()) {
            return schema + "." + tableName;
        }
        return tableName;
    }

    private String getDimensionColumnName(DbDimension dimension) {
        // 获取维度的 ID 列名
        DbColumn idColumn = dimension.getPrimaryKeyDbColumn();
        if (idColumn != null) {
            return idColumn.getSqlColumnName();
        }
        return dimension.getName() + "_id";
    }

    private String buildDateTruncateExpr(String column, TimeGranularity granularity) {
        // MySQL 语法（后续可扩展为方言支持）
        switch (granularity) {
            case YEAR:
                return "DATE_FORMAT(" + column + ", '%Y-01-01')";
            case QUARTER:
                return "MAKEDATE(YEAR(" + column + "), 1) + INTERVAL QUARTER(" + column + ") QUARTER - INTERVAL 1 QUARTER";
            case MONTH:
                return "DATE_FORMAT(" + column + ", '%Y-%m-01')";
            case WEEK:
                return "DATE_SUB(" + column + ", INTERVAL WEEKDAY(" + column + ") DAY)";
            case DAY:
                return "DATE(" + column + ")";
            case HOUR:
                return "DATE_FORMAT(" + column + ", '%Y-%m-%d %H:00:00')";
            case MINUTE:
            default:
                return column;
        }
    }

    private String buildAggregationExpr(String column, DbAggregation agg) {
        switch (agg) {
            case SUM:
                return "SUM(" + column + ")";
            case COUNT:
                return "COUNT(*)";
            case MIN:
                return "MIN(" + column + ")";
            case MAX:
                return "MAX(" + column + ")";
            case AVG:
                return "AVG(" + column + ")";
            default:
                return "SUM(" + column + ")";
        }
    }

    private String getColumnDataType(DbDimension dimension) {
        DbColumn idColumn = dimension.getPrimaryKeyDbColumn();
        if (idColumn != null && idColumn.getType() != null) {
            switch (idColumn.getType()) {
                case INTEGER:
                    return "INT";
                case BIGINT:
                    return "BIGINT";
                case TEXT:
                    return "VARCHAR(255)";
                case DAY:
                    return "DATE";
                case DATETIME:
                    return "DATETIME";
                default:
                    return "BIGINT";
            }
        }
        return "BIGINT";
    }

    /**
     * 解析水位线列名
     * <p>
     * 格式：dimensionName$propertyName 或 dimensionName$id
     * </p>
     */
    private String parseWatermarkColumnName(String watermarkColumn) {
        if (watermarkColumn == null) {
            return "created_at";
        }
        // 简化处理：直接使用 $ 前的部分作为列名
        int dollarIndex = watermarkColumn.indexOf('$');
        if (dollarIndex > 0) {
            return watermarkColumn.substring(0, dollarIndex);
        }
        return watermarkColumn;
    }
}

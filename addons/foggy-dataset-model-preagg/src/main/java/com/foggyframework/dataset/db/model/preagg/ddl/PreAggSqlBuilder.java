package com.foggyframework.dataset.db.model.preagg.ddl;

import com.foggyframework.dataset.db.dialect.FDialect;
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
 * <p>
 * 支持多数据库方言，通过 {@link FDialect} 生成方言特定的日期截断、时间戳等表达式。
 * 增量 SQL 使用参数化查询（{@link ParameterizedSql}），防止 SQL 注入。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggSqlBuilder {

    private final FDialect dialect;

    /**
     * 使用默认方言（MySQL）创建 SQL 构建器
     */
    public PreAggSqlBuilder() {
        this(FDialect.MYSQL_DIALECT);
    }

    /**
     * 使用指定方言创建 SQL 构建器
     *
     * @param dialect 数据库方言
     */
    public PreAggSqlBuilder(FDialect dialect) {
        this.dialect = dialect != null ? dialect : FDialect.MYSQL_DIALECT;
    }

    /**
     * 构建全量刷新的 INSERT ... SELECT 语句
     *
     * @param preAgg      预聚合配置
     * @param sourceModel 源模型
     * @return INSERT SQL
     */
    public String buildFullRefreshInsertSql(PreAggregation preAgg, TableModel sourceModel) {
        RefreshSqlParts parts = buildRefreshSqlParts(preAgg, sourceModel);

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(preAgg.getQualifiedTableName()).append(" (");
        sql.append(String.join(", ", parts.targetColumns));
        sql.append(") SELECT ");
        sql.append(String.join(", ", parts.selectExprs));
        sql.append(" FROM ").append(sourceModel.getTableName());

        // TODO: 添加 JOIN（如果需要维表属性）

        if (!parts.groupByExprs.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", parts.groupByExprs));
        }

        return sql.toString();
    }

    /**
     * 构建增量删除 SQL（参数化）
     * <p>
     * 返回 {@link ParameterizedSql}，日期值使用 {@code ?} 占位符，防止 SQL 注入。
     * </p>
     *
     * @param preAgg        预聚合配置
     * @param refreshConfig 刷新配置
     * @param startDate     起始日期
     * @param endDate       结束日期
     * @return 参数化 SQL（params: [startDate, endDate]）
     */
    public ParameterizedSql buildIncrementalDeleteSql(PreAggregation preAgg, PreAggRefreshDef refreshConfig,
                                                       LocalDate startDate, LocalDate endDate) {
        String tableName = preAgg.getQualifiedTableName();
        String watermarkColumn = parseWatermarkColumnName(refreshConfig.getWatermarkColumn());

        String sql = "DELETE FROM " + tableName + " WHERE " + watermarkColumn + " >= ? AND " + watermarkColumn + " <= ?";
        List<Object> params = Arrays.asList(startDate, endDate);

        return new ParameterizedSql(sql, params);
    }

    /**
     * 构建增量插入 SQL（参数化）
     * <p>
     * 返回 {@link ParameterizedSql}，日期值使用 {@code ?} 占位符，防止 SQL 注入。
     * SQL 结构通过独立构建 SELECT/WHERE/GROUP BY 各部分来组装，不使用字符串搜索。
     * </p>
     *
     * @param preAgg        预聚合配置
     * @param sourceModel   源模型
     * @param refreshConfig 刷新配置
     * @param startDate     起始日期
     * @param endDate       结束日期
     * @return 参数化 SQL（params: [startDate, endDate]）
     */
    public ParameterizedSql buildIncrementalInsertSql(PreAggregation preAgg, TableModel sourceModel,
                                                       PreAggRefreshDef refreshConfig,
                                                       LocalDate startDate, LocalDate endDate) {
        RefreshSqlParts parts = buildRefreshSqlParts(preAgg, sourceModel);
        String watermarkColumn = parseWatermarkColumnName(refreshConfig.getWatermarkColumn());

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(preAgg.getQualifiedTableName()).append(" (");
        sql.append(String.join(", ", parts.targetColumns));
        sql.append(") SELECT ");
        sql.append(String.join(", ", parts.selectExprs));
        sql.append(" FROM ").append(sourceModel.getTableName());

        // WHERE 子句使用参数化（防 SQL 注入）
        sql.append(" WHERE ").append(watermarkColumn).append(" >= ? AND ").append(watermarkColumn).append(" <= ?");

        if (!parts.groupByExprs.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", parts.groupByExprs));
        }

        List<Object> params = Arrays.asList(startDate, endDate);
        return new ParameterizedSql(sql.toString(), params);
    }

    /**
     * 构建创建表 DDL
     */
    public String buildCreateTableDdl(PreAggregation preAgg, TableModel sourceModel) {
        StringBuilder ddl = new StringBuilder();
        String tableName = preAgg.getQualifiedTableName();

        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> pkColumns = new ArrayList<>();

        // 维度列
        for (String dimName : preAgg.getDimensionNames()) {
            DbDimension dimension = sourceModel.findJdbcDimensionByName(dimName);
            if (dimension == null) continue;

            String dimColumnName = getDimensionColumnName(dimension);
            TimeGranularity granularity = preAgg.getGranularity(dimName);

            String dataType = granularity != null
                    ? dialect.mapColumnType("DATE")
                    : dialect.mapColumnType(getColumnDataType(dimension));
            columnDefs.add("    " + dimColumnName + " " + dataType + " NOT NULL");
            pkColumns.add(dimColumnName);

            // 维度属性列
            for (String propName : preAgg.getDimensionProperties(dimName)) {
                String propColumnName = dimName + "_" + propName;
                columnDefs.add("    " + propColumnName + " " + dialect.mapColumnType("VARCHAR(255)"));
            }
        }

        // 度量列
        for (Map.Entry<String, String> entry : preAgg.getMeasureColumnNames().entrySet()) {
            String columnName = entry.getValue();
            columnDefs.add("    " + columnName + " " + dialect.mapColumnType("DECIMAL(20,4)"));
        }

        // 元数据列
        columnDefs.add("    _preagg_row_count " + dialect.mapColumnType("BIGINT") + " DEFAULT 1");
        columnDefs.add("    _preagg_created_at " + dialect.mapColumnType("TIMESTAMP") + " DEFAULT " + dialect.buildCurrentTimestampExpression());
        columnDefs.add("    _preagg_updated_at " + dialect.mapColumnType("TIMESTAMP"));

        // 主键
        if (!pkColumns.isEmpty()) {
            columnDefs.add("    PRIMARY KEY (" + String.join(", ", pkColumns) + ")");
        }

        ddl.append(String.join(",\n", columnDefs));
        ddl.append("\n)");

        return ddl.toString();
    }

    // ==================== 内部结构 ====================

    /**
     * 刷新 SQL 的各组成部分
     * <p>
     * 将 SELECT、GROUP BY 等部分独立构建，避免事后字符串搜索。
     * </p>
     */
    static class RefreshSqlParts {
        final List<String> targetColumns = new ArrayList<>();
        final List<String> selectExprs = new ArrayList<>();
        final List<String> groupByExprs = new ArrayList<>();
    }

    /**
     * 构建刷新 SQL 的公共部分
     * <p>
     * 提取全量和增量刷新共用的列/SELECT/GROUP BY 构建逻辑。
     * 增量版本在此基础上添加 WHERE 子句，无需字符串搜索定位 GROUP BY。
     * </p>
     */
    private RefreshSqlParts buildRefreshSqlParts(PreAggregation preAgg, TableModel sourceModel) {
        RefreshSqlParts parts = new RefreshSqlParts();

        // 1. 处理维度列
        for (String dimName : preAgg.getDimensionNames()) {
            DbDimension dimension = sourceModel.findJdbcDimensionByName(dimName);
            if (dimension == null) {
                log.warn("Dimension '{}' not found in source model", dimName);
                continue;
            }

            String dimColumnName = getDimensionColumnName(dimension);
            TimeGranularity granularity = preAgg.getGranularity(dimName);

            if (granularity != null) {
                // 使用方言的日期截断表达式
                String truncatedExpr = dialect.buildDateTruncateExpression(dimColumnName, granularity.name());
                parts.targetColumns.add(dimColumnName);
                parts.selectExprs.add(truncatedExpr + " AS " + dimColumnName);
                parts.groupByExprs.add(truncatedExpr);
            } else {
                parts.targetColumns.add(dimColumnName);
                parts.selectExprs.add(dimColumnName);
                parts.groupByExprs.add(dimColumnName);
            }

            // 处理维度属性
            Set<String> props = preAgg.getDimensionProperties(dimName);
            for (String propName : props) {
                String propColumnName = dimName + "_" + propName;
                parts.targetColumns.add(propColumnName);
                parts.selectExprs.add("MAX(" + propName + ") AS " + propColumnName);
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

            parts.targetColumns.add(targetColumnName);
            parts.selectExprs.add(buildAggregationExpr(sourceColumnName, agg) + " AS " + targetColumnName);
        }

        // 3. 添加行数统计列
        parts.targetColumns.add("_preagg_row_count");
        parts.selectExprs.add("COUNT(*) AS _preagg_row_count");

        // 4. 添加时间戳列（使用方言的当前时间戳表达式）
        parts.targetColumns.add("_preagg_created_at");
        parts.selectExprs.add(dialect.buildCurrentTimestampExpression() + " AS _preagg_created_at");

        return parts;
    }

    // ==================== 辅助方法 ====================

    private String getDimensionColumnName(DbDimension dimension) {
        DbColumn idColumn = dimension.getPrimaryKeyDbColumn();
        if (idColumn != null) {
            return idColumn.getSqlColumnName();
        }
        return dimension.getName() + "_id";
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
        int dollarIndex = watermarkColumn.indexOf('$');
        if (dollarIndex > 0) {
            return watermarkColumn.substring(0, dollarIndex);
        }
        return watermarkColumn;
    }
}

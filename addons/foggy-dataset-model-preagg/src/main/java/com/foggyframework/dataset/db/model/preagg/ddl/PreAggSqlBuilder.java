package com.foggyframework.dataset.db.model.preagg.ddl;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.preagg.TimeGranularity;

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
public class PreAggSqlBuilder {

    private static final String SOURCE_ALIAS = "src";

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
        appendSourceFromAndJoins(sql, sourceModel, parts);

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
     * @param endDate       结束日期（exclusive）
     * @return 参数化 SQL（params: [startDateInclusive, endDateExclusive]）
     */
    public ParameterizedSql buildIncrementalDeleteSql(PreAggregation preAgg, PreAggRefreshDef refreshConfig,
                                                       LocalDate startDate, LocalDate endDate) {
        String tableName = preAgg.getQualifiedTableName();
        String watermarkColumn = PreAggPhysicalColumnContract.resolveMaterializedWatermark(
                preAgg, refreshConfig);

        String sql = "DELETE FROM " + tableName + " WHERE " + watermarkColumn
                + " >= ? AND " + watermarkColumn + " < ?";
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
     * @param endDate       结束日期（exclusive）
     * @return 参数化 SQL（params: [startDateInclusive, endDateExclusive]）
     */
    public ParameterizedSql buildIncrementalInsertSql(PreAggregation preAgg, TableModel sourceModel,
                                                       PreAggRefreshDef refreshConfig,
                                                       LocalDate startDate, LocalDate endDate) {
        RefreshSqlParts parts = buildRefreshSqlParts(preAgg, sourceModel);
        PreAggPhysicalColumnContract.WatermarkColumns watermark =
                PreAggPhysicalColumnContract.resolveWatermark(
                        preAgg, sourceModel, refreshConfig, dialect);
        ensureSourceJoin(parts, sourceModel, watermark.sourceColumn(), watermark.dimension());
        String sourceWatermarkColumn = sourceColumnExpression(
                sourceModel, watermark.sourceColumn());

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(preAgg.getQualifiedTableName()).append(" (");
        sql.append(String.join(", ", parts.targetColumns));
        sql.append(") SELECT ");
        sql.append(String.join(", ", parts.selectExprs));
        appendSourceFromAndJoins(sql, sourceModel, parts);

        // WHERE 子句使用参数化（防 SQL 注入）
        sql.append(" WHERE ").append(sourceWatermarkColumn).append(" >= ? AND ")
                .append(sourceWatermarkColumn).append(" < ?");

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
        validateConfiguredWatermark(preAgg, sourceModel);
        StringBuilder ddl = new StringBuilder();
        String tableName = preAgg.getQualifiedTableName();

        ddl.append("CREATE TABLE ").append(tableName).append(" (\n");

        List<String> columnDefs = new ArrayList<>();
        List<String> pkColumns = new ArrayList<>();
        Set<String> declaredColumns = new LinkedHashSet<>();

        // 维度列
        for (String dimName : preAgg.getDimensionNames()) {
            TimeGranularity granularity = preAgg.getGranularity(dimName);
            PreAggPhysicalColumnContract.ResolvedColumn dimensionGrain =
                    PreAggPhysicalColumnContract.resolveDimensionGrain(
                            preAgg, sourceModel, dimName, granularity);
            String dimColumnName = dimensionGrain.materializedColumn();

            String dataType = dialect.mapColumnType(
                    getDimensionGrainDataType(granularity, dimensionGrain.sourceColumn().type()));
            requireUniqueColumn(declaredColumns, dimColumnName, dimensionGrain.semanticField());
            columnDefs.add("    " + dimColumnName + " " + dataType + " NOT NULL");
            pkColumns.add(dimColumnName);

            // 维度属性列
            for (String propName : getDeclaredDimensionProperties(preAgg, dimName)) {
                PreAggPhysicalColumnContract.ResolvedColumn property =
                        PreAggPhysicalColumnContract.resolveDimensionProperty(
                                preAgg, sourceModel, dimName, propName);
                if (property.semanticField().equals(dimensionGrain.semanticField())) {
                    continue;
                }
                rejectUnsafeCoarseIdProperty(granularity, property);
                requireUniqueColumn(
                        declaredColumns, property.materializedColumn(), property.semanticField());
                columnDefs.add("    " + property.materializedColumn() + " "
                        + dialect.mapColumnType(getColumnDataType(property.sourceColumn().type())));
            }
        }

        // 度量列
        Map<String, String> measureColumns = preAgg.getMeasureColumnNames();
        for (Map.Entry<String, DbAggregation> entry : preAgg.getMeasureAggregations().entrySet()) {
            String measureName = entry.getKey();
            DbAggregation aggregation = entry.getValue();
            String columnName = requireMeasureColumn(measureColumns, measureName);
            requireUniqueColumn(declaredColumns, columnName, "measure " + measureName);
            columnDefs.add("    " + columnName + " "
                    + dialect.mapColumnType(getMeasureDataType(aggregation)));
        }

        // 元数据列
        requireUniqueColumn(declaredColumns, "_preagg_row_count", "metadata");
        columnDefs.add("    _preagg_row_count " + dialect.mapColumnType("BIGINT") + " DEFAULT 1");
        requireUniqueColumn(declaredColumns, "_preagg_created_at", "metadata");
        columnDefs.add("    _preagg_created_at " + dialect.mapColumnType("DATETIME")
                + " DEFAULT " + buildDdlCurrentTimestampExpression());
        requireUniqueColumn(declaredColumns, "_preagg_updated_at", "metadata");
        columnDefs.add("    _preagg_updated_at " + dialect.mapColumnType("DATETIME"));

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
        final Map<String, String> joins = new LinkedHashMap<>();
        final Set<String> declaredTargetColumns = new LinkedHashSet<>();
    }

    /**
     * 构建刷新 SQL 的公共部分
     * <p>
     * 提取全量和增量刷新共用的列/SELECT/GROUP BY 构建逻辑。
     * 增量版本在此基础上添加 WHERE 子句，无需字符串搜索定位 GROUP BY。
     * </p>
     */
    private RefreshSqlParts buildRefreshSqlParts(PreAggregation preAgg, TableModel sourceModel) {
        validateConfiguredWatermark(preAgg, sourceModel);
        RefreshSqlParts parts = new RefreshSqlParts();

        // 1. 处理维度列
        for (String dimName : preAgg.getDimensionNames()) {
            TimeGranularity granularity = preAgg.getGranularity(dimName);
            PreAggPhysicalColumnContract.ResolvedColumn dimensionGrain =
                    PreAggPhysicalColumnContract.resolveDimensionGrain(
                            preAgg, sourceModel, dimName, granularity);
            String dimColumnName = dimensionGrain.materializedColumn();
            ensureSourceJoin(
                    parts, sourceModel, dimensionGrain.sourceColumn(), dimensionGrain.dimension());
            String sourceDimensionColumn = sourceColumnExpression(
                    sourceModel, dimensionGrain.sourceColumn());
            addTargetColumn(parts, dimColumnName, dimensionGrain.semanticField());

            if (shouldTruncateDimensionGrain(granularity, dimensionGrain.sourceColumn().type())) {
                // 使用方言的日期截断表达式
                String truncatedExpr = dialect.buildDateTruncateExpression(
                        sourceDimensionColumn, granularity.name());
                parts.selectExprs.add(truncatedExpr + " AS " + dimColumnName);
                parts.groupByExprs.add(truncatedExpr);
            } else {
                parts.selectExprs.add(sourceDimensionColumn + " AS " + dimColumnName);
                parts.groupByExprs.add(sourceDimensionColumn);
            }

            // 处理维度属性
            Set<String> props = getDeclaredDimensionProperties(preAgg, dimName);
            for (String propName : props) {
                PreAggPhysicalColumnContract.ResolvedColumn property =
                        PreAggPhysicalColumnContract.resolveDimensionProperty(
                                preAgg, sourceModel, dimName, propName);
                if (property.semanticField().equals(dimensionGrain.semanticField())) {
                    continue;
                }
                rejectUnsafeCoarseIdProperty(granularity, property);
                ensureSourceJoin(parts, sourceModel, property.sourceColumn(), property.dimension());
                String sourcePropertyColumn = sourceColumnExpression(
                        sourceModel, property.sourceColumn());
                addTargetColumn(parts, property.materializedColumn(), property.semanticField());
                parts.selectExprs.add("MAX(" + sourcePropertyColumn + ") AS "
                        + property.materializedColumn());
            }
        }

        // 2. 处理度量列
        Map<String, DbAggregation> measureAggs = preAgg.getMeasureAggregations();
        Map<String, String> measureColumns = preAgg.getMeasureColumnNames();

        for (Map.Entry<String, DbAggregation> entry : measureAggs.entrySet()) {
            String measureName = entry.getKey();
            DbAggregation agg = entry.getValue();
            String targetColumnName = requireMeasureColumn(measureColumns, measureName);

            String sourceExpression = null;
            if (agg != DbAggregation.COUNT) {
                DbMeasure measure = sourceModel.findJdbcMeasureByName(measureName);
                if (measure == null || measure.getJdbcColumn() == null) {
                    throw contractError(
                            "No source measure expression is declared for " + measureName);
                }
                sourceExpression = measure.getJdbcColumn().getDeclare(null, SOURCE_ALIAS, dialect);
                if (isBlank(sourceExpression)) {
                    throw contractError(
                            "No source measure expression is declared for " + measureName);
                }
            }

            addTargetColumn(parts, targetColumnName, "measure " + measureName);
            parts.selectExprs.add(buildAggregationExpr(sourceExpression, agg)
                    + " AS " + targetColumnName);
        }

        // 3. 添加行数统计列
        addTargetColumn(parts, "_preagg_row_count", "metadata");
        parts.selectExprs.add("COUNT(*) AS _preagg_row_count");

        // 4. 添加时间戳列（使用方言的当前时间戳表达式）
        addTargetColumn(parts, "_preagg_created_at", "metadata");
        parts.selectExprs.add(dialect.buildCurrentTimestampExpression() + " AS _preagg_created_at");

        return parts;
    }

    private void validateConfiguredWatermark(PreAggregation preAgg, TableModel sourceModel) {
        PreAggRefreshDef refreshConfig = preAgg != null ? preAgg.getRefreshConfig() : null;
        String watermark = refreshConfig != null ? refreshConfig.getWatermarkColumn() : null;
        if (watermark != null && !watermark.isBlank()) {
            PreAggPhysicalColumnContract.resolveWatermark(
                    preAgg, sourceModel, refreshConfig, dialect);
        }
    }

    // ==================== 辅助方法 ====================

    private void appendSourceFromAndJoins(StringBuilder sql,
                                          TableModel sourceModel,
                                          RefreshSqlParts parts) {
        QueryObject sourceQueryObject = sourceModel.getQueryObject();
        String sourceBody = sourceQueryObject != null ? sourceQueryObject.getBody() : null;
        if (isBlank(sourceBody)) {
            sourceBody = sourceModel.getTableName();
        }
        if (isBlank(sourceBody)) {
            throw contractError("Source model has no physical query object");
        }
        sql.append(" FROM ").append(sourceBody).append(" ").append(SOURCE_ALIAS);
        for (String join : parts.joins.values()) {
            sql.append(" ").append(join);
        }
    }

    private void ensureSourceJoin(RefreshSqlParts parts,
                                  TableModel sourceModel,
                                  PreAggPhysicalColumnContract.SourceColumn sourceColumn,
                                  DbDimension dimension) {
        QueryObject sourceQueryObject = sourceModel.getQueryObject();
        QueryObject columnQueryObject = sourceColumn.queryObject();
        if (sameQueryObject(sourceQueryObject, columnQueryObject)) {
            return;
        }
        if (columnQueryObject == null || dimension == null
                || !sameQueryObject(columnQueryObject, dimension.getQueryObject())) {
            throw contractError(
                    "Cannot prove source table for physical column " + sourceColumn.physicalName());
        }
        if (dimension.getParentDimension() != null) {
            throw contractError(
                    "Nested dimension joins are not supported by Addon refresh: " + dimension.getName());
        }

        String alias = columnQueryObject.getAlias();
        String body = columnQueryObject.getBody();
        String primaryKey = columnQueryObject.getPrimaryKey();
        String foreignKey = dimension.getForeignKey();
        if (isBlank(foreignKey) && dimension.getForeignKeyDbColumn() != null) {
            foreignKey = dimension.getForeignKeyDbColumn().getSqlColumnName();
        }
        if (isBlank(alias) || isBlank(body) || isBlank(primaryKey) || isBlank(foreignKey)) {
            throw contractError(
                    "Incomplete source join contract for dimension " + dimension.getName());
        }
        if (SOURCE_ALIAS.equals(alias)) {
            throw contractError("Dimension alias collides with source alias: " + alias);
        }

        String join = "LEFT JOIN " + body + " " + alias
                + " ON " + SOURCE_ALIAS + "." + foreignKey
                + " = " + alias + "." + primaryKey;
        String existing = parts.joins.putIfAbsent(alias, join);
        if (existing != null && !existing.equals(join)) {
            throw contractError("Conflicting source joins use alias " + alias);
        }
    }

    private String sourceColumnExpression(
            TableModel sourceModel,
            PreAggPhysicalColumnContract.SourceColumn sourceColumn) {
        if (sourceColumn == null || isBlank(sourceColumn.physicalName())) {
            throw contractError("Source physical column must not be blank");
        }
        QueryObject sourceQueryObject = sourceModel.getQueryObject();
        QueryObject columnQueryObject = sourceColumn.queryObject();
        if (sameQueryObject(sourceQueryObject, columnQueryObject)) {
            return SOURCE_ALIAS + "." + sourceColumn.physicalName();
        }
        if (columnQueryObject == null || isBlank(columnQueryObject.getAlias())) {
            throw contractError(
                    "Cannot prove source alias for physical column " + sourceColumn.physicalName());
        }
        return columnQueryObject.getAlias() + "." + sourceColumn.physicalName();
    }

    private boolean sameQueryObject(QueryObject first, QueryObject second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        Object firstRoot = first.getRoot();
        Object secondRoot = second.getRoot();
        return firstRoot != null && firstRoot == secondRoot;
    }

    private void addTargetColumn(RefreshSqlParts parts, String column, String source) {
        requireUniqueColumn(parts.declaredTargetColumns, column, source);
        parts.targetColumns.add(column);
    }

    private void requireUniqueColumn(Set<String> declaredColumns, String column, String source) {
        if (isBlank(column)) {
            throw contractError("Materialized column must not be blank for " + source);
        }
        if (!declaredColumns.add(column)) {
            throw contractError(
                    "Materialized column " + column + " is declared more than once (" + source + ")");
        }
    }

    private String requireMeasureColumn(Map<String, String> measureColumns, String measureName) {
        String column = measureColumns != null ? measureColumns.get(measureName) : null;
        if (isBlank(column)) {
            throw contractError(
                    "No explicit materialized measure column is declared for " + measureName);
        }
        return column;
    }

    private Set<String> getDeclaredDimensionProperties(PreAggregation preAgg,
                                                       String dimensionName) {
        Set<String> properties = new LinkedHashSet<>(
                preAgg.getDimensionProperties(dimensionName));
        Map<String, String> explicitMappings =
                preAgg.getExplicitDimensionPropertyColumnNames();
        if (explicitMappings == null || explicitMappings.isEmpty()) {
            return properties;
        }

        String prefix = dimensionName + "$";
        for (String semanticField : explicitMappings.keySet()) {
            if (semanticField != null && semanticField.startsWith(prefix)
                    && semanticField.length() > prefix.length()) {
                properties.add(semanticField.substring(prefix.length()));
            }
        }
        return properties;
    }

    private String buildAggregationExpr(String column, DbAggregation agg) {
        if (agg == null) {
            throw contractError("Measure aggregation must not be null");
        }
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
                throw contractError("Unsupported materialization aggregation: " + agg);
        }
    }

    private String getMeasureDataType(DbAggregation aggregation) {
        if (aggregation == null) {
            throw contractError("Measure aggregation must not be null");
        }
        switch (aggregation) {
            case COUNT:
                return "BIGINT";
            case SUM:
            case MIN:
            case MAX:
            case AVG:
                return "DECIMAL(20,4)";
            default:
                throw contractError("Unsupported materialization aggregation: " + aggregation);
        }
    }

    private String buildDdlCurrentTimestampExpression() {
        if (dialect == FDialect.SQLITE_DIALECT
                || "SQLite".equalsIgnoreCase(dialect.getProductName())) {
            return "CURRENT_TIMESTAMP";
        }
        return dialect.buildCurrentTimestampExpression();
    }

    private String getDimensionGrainDataType(TimeGranularity granularity,
                                             DbColumnType sourceType) {
        if (!shouldTruncateDimensionGrain(granularity, sourceType)) {
            return getColumnDataType(sourceType);
        }
        if (granularity == TimeGranularity.MINUTE || granularity == TimeGranularity.HOUR) {
            return "DATETIME";
        }
        return "DATE";
    }

    private boolean shouldTruncateDimensionGrain(TimeGranularity granularity,
                                                 DbColumnType sourceType) {
        return granularity != null
                && (granularity != TimeGranularity.DAY || sourceType == DbColumnType.DATETIME);
    }

    private void rejectUnsafeCoarseIdProperty(
            TimeGranularity granularity,
            PreAggPhysicalColumnContract.ResolvedColumn property) {
        if (granularity != null && granularity != TimeGranularity.DAY
                && property.semanticField().endsWith("$id")) {
            throw contractError(
                    "A coarse time bucket cannot materialize a single dimension id: "
                            + property.semanticField());
        }
    }

    private String getColumnDataType(DbColumnType type) {
        if (type == null) {
            throw contractError("Source physical column type must not be null");
        }
        switch (type) {
            case DICT:
            case INTEGER:
                return "INT";
            case BIGINT:
                return "BIGINT";
            case MONEY:
            case NUMBER:
                return "DECIMAL(20,4)";
            case TEXT:
            case STRING:
                return "VARCHAR(255)";
            case DAY:
                return "DATE";
            case DATETIME:
                return "DATETIME";
            default:
                throw contractError("Unsupported materialized column type: " + type);
        }
    }

    private IllegalArgumentException contractError(String message) {
        return new IllegalArgumentException(
                "Pre-aggregation physical column contract violation: " + message);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

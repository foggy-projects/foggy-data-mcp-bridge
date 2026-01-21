package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 预聚合查询重写器
 * <p>
 * 将原始查询重写为使用预聚合表的查询。
 * </p>
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>单表模式：仅使用预聚合表</li>
 *   <li>混合模式：预聚合表 UNION 原始表（Lambda 架构）</li>
 * </ul>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggQueryRewriter {

    private final JdbcQueryModel queryModel;
    private final ApplicationContext applicationContext;

    public PreAggQueryRewriter(JdbcQueryModel queryModel, ApplicationContext applicationContext) {
        this.queryModel = queryModel;
        this.applicationContext = applicationContext;
    }

    /**
     * 重写查询以使用预聚合表
     *
     * @param matchResult  匹配结果
     * @param jdbcQuery    原始 JdbcQuery
     * @param queryRequest 查询请求
     * @param queryEngine  查询引擎（用于获取原始 SQL 参数）
     * @return 重写结果
     */
    public PreAggRewriteResult rewrite(PreAggregationMatchResult matchResult,
                                        JdbcQuery jdbcQuery,
                                        DbQueryRequestDef queryRequest,
                                        JdbcModelQueryEngine queryEngine) {
        if (!matchResult.isMatched()) {
            return PreAggRewriteResult.notApplied();
        }

        PreAggregation preAgg = matchResult.getPreAggregation();
        boolean needsRollup = matchResult.isNeedsRollup();
        boolean isHybrid = matchResult.isHybridQuery();

        try {
            String sql;
            List<Object> params;

            if (isHybrid) {
                // 混合查询模式：UNION SQL
                Object watermark = matchResult.getWatermark();
                sql = buildHybridSql(preAgg, jdbcQuery, queryRequest, queryEngine, watermark);
                params = buildHybridParams(queryEngine.getValues(), watermark);

                if (log.isInfoEnabled()) {
                    log.info("Rewrote query to HYBRID mode using pre-aggregation '{}', watermark={}",
                            preAgg.getName(), watermark);
                }

                if (log.isDebugEnabled()) {
                    log.debug("Hybrid SQL: {}", sql);
                }

                return PreAggRewriteResult.hybrid(preAgg, sql, params, needsRollup, watermark);
            } else {
                // 单表模式：仅预聚合表
                PreAggSqlBuildResult sqlBuildResult = buildPreAggSql(preAgg, jdbcQuery, queryRequest, needsRollup);
                sql = sqlBuildResult.getSql();
                // 使用生成的 WHERE 参数
                params = sqlBuildResult.getWhereParams() != null ? sqlBuildResult.getWhereParams() : new ArrayList<>();

                if (log.isInfoEnabled()) {
                    log.info("Rewrote query to use pre-aggregation '{}', needsRollup={}, whereIncluded={}, params={}",
                            preAgg.getName(), needsRollup, sqlBuildResult.isWhereIncluded(), params.size());
                }

                if (log.isDebugEnabled()) {
                    log.debug("Pre-aggregation SQL: {}", sql);
                    log.debug("Pre-aggregation params: {}", params);
                }

                return PreAggRewriteResult.applied(preAgg, sql, params, needsRollup);
            }
        } catch (Exception e) {
            log.warn("Failed to rewrite query for pre-aggregation '{}': {}",
                    preAgg.getName(), e.getMessage(), e);
            return PreAggRewriteResult.notApplied();
        }
    }

    /**
     * 构建聚合查询 SQL（用于 returnTotal 场景）
     * <p>
     * 当主查询是明细查询（无 GROUP BY）时，聚合查询仍然可以使用预聚合表。
     * 生成的 SQL 只包含 COUNT(*) 和 SUM(度量) 聚合，不包含维度列。
     * </p>
     * <p>
     * 此方法不支持混合查询模式，使用 {@link #buildAggregateSql(PreAggregation, JdbcQuery, DbQueryRequestDef, PreAggregationMatchResult)}
     * 获取完整的混合模式支持。
     * </p>
     *
     * @param preAgg       预聚合
     * @param jdbcQuery    原始 JdbcQuery
     * @param queryRequest 查询请求
     * @return 聚合 SQL 构建结果，如果不适用返回 null
     */
    public PreAggAggregateSqlResult buildAggregateSql(PreAggregation preAgg,
                                                       JdbcQuery jdbcQuery,
                                                       DbQueryRequestDef queryRequest) {
        return buildAggregateSqlInternal(preAgg, jdbcQuery, queryRequest, false, null);
    }

    /**
     * 构建聚合查询 SQL（支持混合查询模式）
     * <p>
     * 当预聚合数据有水位线限制时，使用混合模式：
     * <pre>
     * SELECT COUNT(*), SUM(measure) FROM (
     *   SELECT measure_sum FROM preagg_table WHERE watermark <= ?
     *   UNION ALL
     *   SELECT measure FROM source_table WHERE watermark > ?
     * ) AS combined
     * </pre>
     * </p>
     *
     * @param preAgg       预聚合
     * @param jdbcQuery    原始 JdbcQuery
     * @param queryRequest 查询请求
     * @param matchResult  匹配结果（包含混合模式和水位线信息）
     * @return 聚合 SQL 构建结果，如果不适用返回 null
     */
    public PreAggAggregateSqlResult buildAggregateSql(PreAggregation preAgg,
                                                       JdbcQuery jdbcQuery,
                                                       DbQueryRequestDef queryRequest,
                                                       PreAggregationMatchResult matchResult) {
        if (matchResult == null || !matchResult.isMatched()) {
            return null;
        }
        return buildAggregateSqlInternal(preAgg, jdbcQuery, queryRequest,
                matchResult.isHybridQuery(), matchResult.getWatermark());
    }

    /**
     * 构建聚合查询 SQL 的内部实现
     *
     * @param preAgg       预聚合
     * @param jdbcQuery    原始 JdbcQuery
     * @param queryRequest 查询请求
     * @param isHybrid     是否使用混合查询模式
     * @param watermark    水位线（混合模式时使用）
     * @return 聚合 SQL 构建结果
     */
    private PreAggAggregateSqlResult buildAggregateSqlInternal(PreAggregation preAgg,
                                                                 JdbcQuery jdbcQuery,
                                                                 DbQueryRequestDef queryRequest,
                                                                 boolean isHybrid,
                                                                 Object watermark) {
        if (preAgg == null) {
            return null;
        }

        try {
            if (isHybrid && watermark != null) {
                // 混合模式：UNION 预聚合表和原始表
                return buildHybridAggregateSql(preAgg, jdbcQuery, queryRequest, watermark);
            } else {
                // 单表模式：仅预聚合表
                return buildSingleTableAggregateSql(preAgg, jdbcQuery, queryRequest);
            }
        } catch (Exception e) {
            log.warn("Failed to build aggregate SQL for pre-aggregation '{}': {}",
                    preAgg.getName(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建单表模式的聚合查询 SQL
     */
    private PreAggAggregateSqlResult buildSingleTableAggregateSql(PreAggregation preAgg,
                                                                    JdbcQuery jdbcQuery,
                                                                    DbQueryRequestDef queryRequest) {
        StringBuilder sql = new StringBuilder();
        String preAggTableName = getFullTableName(preAgg);
        String alias = "pa";

        // SELECT 子句：COUNT(*) as total, SUM(measure) as measureName
        sql.append("SELECT COUNT(*) AS total");
        sql.append(buildAggregateMeasureColumns(preAgg, jdbcQuery, alias));

        // FROM 子句
        sql.append(" FROM ").append(preAggTableName).append(" ").append(alias);

        // WHERE 子句（从 slices 中生成）
        WhereClauseResult whereResult = buildWhereClauseFromSlices(preAgg, queryRequest, alias);
        List<Object> params = new ArrayList<>();
        if (whereResult.getClause() != null && !whereResult.getClause().isEmpty()) {
            sql.append(" WHERE ").append(whereResult.getClause());
            params = whereResult.getParams();
        }

        if (log.isInfoEnabled()) {
            log.info("Built aggregate SQL using pre-aggregation '{}': {}", preAgg.getName(), sql);
        }

        return PreAggAggregateSqlResult.single(sql.toString(), params, preAgg.getName());
    }

    /**
     * 构建混合模式的聚合查询 SQL（Lambda 架构）
     * <p>
     * 生成的 SQL 结构：
     * <pre>
     * SELECT COUNT(*) AS total, SUM(combined.measure) AS measure
     * FROM (
     *   SELECT measure_sum AS measure FROM preagg_table WHERE watermark <= ?
     *   UNION ALL
     *   SELECT measure FROM source_table WHERE watermark > ? [AND slice conditions]
     * ) AS combined
     * </pre>
     * </p>
     */
    private PreAggAggregateSqlResult buildHybridAggregateSql(PreAggregation preAgg,
                                                               JdbcQuery jdbcQuery,
                                                               DbQueryRequestDef queryRequest,
                                                               Object watermark) {
        StringBuilder sql = new StringBuilder();
        String preAggTableName = getFullTableName(preAgg);
        String sourceTableName = queryModel.getJdbcModel().getTableName();
        String watermarkColumn = parseWatermarkColumn(preAgg.getWatermarkColumn());

        // 外层 SELECT：对 UNION 结果做聚合
        sql.append("SELECT COUNT(*) AS total");
        sql.append(buildAggregateMeasureColumnsForHybrid(preAgg, jdbcQuery, "combined"));

        sql.append(" FROM (");

        // 内层 UNION 第一部分：预聚合表
        sql.append("SELECT ");
        sql.append(buildInnerMeasureColumnsForHybrid(preAgg, jdbcQuery, "pa", true));
        sql.append(" FROM ").append(preAggTableName).append(" pa");
        sql.append(" WHERE ").append("pa.").append(watermarkColumn).append(" <= ?");

        // 添加预聚合表的 slice 条件
        WhereClauseResult preAggWhereResult = buildWhereClauseFromSlices(preAgg, queryRequest, "pa");
        if (preAggWhereResult.getClause() != null && !preAggWhereResult.getClause().isEmpty()) {
            sql.append(" AND ").append(preAggWhereResult.getClause());
        }

        // UNION ALL
        sql.append(" UNION ALL ");

        // 内层 UNION 第二部分：原始表（新鲜数据）
        sql.append("SELECT ");
        sql.append(buildInnerMeasureColumnsForHybrid(preAgg, jdbcQuery, "src", false));
        sql.append(" FROM ").append(sourceTableName).append(" src");
        sql.append(" WHERE ").append("src.").append(watermarkColumn).append(" > ?");

        // 添加原始表的 slice 条件（需要映射列名）
        WhereClauseResult sourceWhereResult = buildSourceWhereClauseFromSlices(preAgg, queryRequest, "src");
        if (sourceWhereResult.getClause() != null && !sourceWhereResult.getClause().isEmpty()) {
            sql.append(" AND ").append(sourceWhereResult.getClause());
        }

        sql.append(") AS combined");

        // 构建参数列表：watermark * 2 + slice params * 2
        List<Object> params = new ArrayList<>();
        params.add(watermark);  // 预聚合表 WHERE
        if (preAggWhereResult.getParams() != null) {
            params.addAll(preAggWhereResult.getParams());
        }
        params.add(watermark);  // 原始表 WHERE
        if (sourceWhereResult.getParams() != null) {
            params.addAll(sourceWhereResult.getParams());
        }

        if (log.isInfoEnabled()) {
            log.info("Built hybrid aggregate SQL using pre-aggregation '{}', watermark={}: {}",
                    preAgg.getName(), watermark, sql);
        }

        return PreAggAggregateSqlResult.hybrid(sql.toString(), params, preAgg.getName(), watermark);
    }

    /**
     * 构建聚合查询的度量列（单表模式）
     * <p>
     * 生成形如: ", SUM(pa.sales_amount_sum) AS salesAmount"
     * </p>
     */
    private String buildAggregateMeasureColumns(PreAggregation preAgg, JdbcQuery jdbcQuery, String alias) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> measureColumnNames = preAgg.getMeasureColumnNames();
        Map<String, DbAggregation> measureAggregations = preAgg.getMeasureAggregations();

        if (jdbcQuery.getSelect() != null && jdbcQuery.getSelect().getColumns() != null) {
            for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
                if (column.isMeasure()) {
                    String measureName = column.getName();
                    String columnAlias = column.getAlias();

                    String preAggColumnName = measureColumnNames.get(measureName);
                    if (preAggColumnName == null) {
                        preAggColumnName = measureName + "_sum";
                    }

                    DbAggregation agg = measureAggregations.get(measureName);
                    String aggFunc = getAggregationFunction(agg);

                    sb.append(", ").append(aggFunc).append("(")
                      .append(alias).append(".").append(preAggColumnName)
                      .append(") AS ").append(columnAlias);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 构建混合模式外层的度量聚合列
     * <p>
     * 生成形如: ", SUM(combined.measure) AS salesAmount"
     * </p>
     */
    private String buildAggregateMeasureColumnsForHybrid(PreAggregation preAgg, JdbcQuery jdbcQuery, String alias) {
        StringBuilder sb = new StringBuilder();

        if (jdbcQuery.getSelect() != null && jdbcQuery.getSelect().getColumns() != null) {
            for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
                if (column.isMeasure()) {
                    String measureName = column.getName();
                    String columnAlias = column.getAlias();

                    // 外层使用统一的别名（内层 UNION 会用同名）
                    sb.append(", SUM(").append(alias).append(".").append(measureName)
                      .append(") AS ").append(columnAlias);
                }
            }
        }
        return sb.toString();
    }

    /**
     * 构建混合模式内层的度量列
     *
     * @param isPreAggPart true=预聚合表部分，false=原始表部分
     */
    private String buildInnerMeasureColumnsForHybrid(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                                      String alias, boolean isPreAggPart) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> measureColumnNames = preAgg.getMeasureColumnNames();
        boolean first = true;

        if (jdbcQuery.getSelect() != null && jdbcQuery.getSelect().getColumns() != null) {
            for (DbColumn column : jdbcQuery.getSelect().getColumns()) {
                if (column.isMeasure()) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;

                    String measureName = column.getName();

                    if (isPreAggPart) {
                        // 预聚合表：使用预聚合列名
                        String preAggColumnName = measureColumnNames.get(measureName);
                        if (preAggColumnName == null) {
                            preAggColumnName = measureName + "_sum";
                        }
                        sb.append(alias).append(".").append(preAggColumnName)
                          .append(" AS ").append(measureName);
                    } else {
                        // 原始表：使用原始列名
                        String sourceColumnName = getSourceColumnName(column);
                        sb.append(alias).append(".").append(sourceColumnName)
                          .append(" AS ").append(measureName);
                    }
                }
            }
        }
        return sb.toString();
    }

    /**
     * 获取原始表中的列名
     */
    private String getSourceColumnName(DbColumn column) {
        if (column.getSqlColumn() != null) {
            return column.getSqlColumn().getName();
        }
        return column.getName();
    }

    /**
     * 构建原始表的 WHERE 子句（从 slices 中生成，使用原始列名）
     */
    private WhereClauseResult buildSourceWhereClauseFromSlices(PreAggregation preAgg,
                                                                 DbQueryRequestDef queryRequest,
                                                                 String alias) {
        // 目前使用与预聚合表相同的逻辑，列名映射在 buildConditionFromSlice 中处理
        // 如果需要不同的列名映射，可以在这里扩展
        return buildWhereClauseFromSlices(preAgg, queryRequest, alias);
    }

    /**
     * 聚合 SQL 构建结果
     */
    @lombok.Data
    public static class PreAggAggregateSqlResult {
        private final String sql;
        private final List<Object> params;
        private final String preAggName;
        private final boolean hybrid;
        private final Object watermark;

        /**
         * 创建单表模式结果
         */
        public static PreAggAggregateSqlResult single(String sql, List<Object> params, String preAggName) {
            return new PreAggAggregateSqlResult(sql, params, preAggName, false, null);
        }

        /**
         * 创建混合模式结果
         */
        public static PreAggAggregateSqlResult hybrid(String sql, List<Object> params, String preAggName, Object watermark) {
            return new PreAggAggregateSqlResult(sql, params, preAggName, true, watermark);
        }

        private PreAggAggregateSqlResult(String sql, List<Object> params, String preAggName,
                                          boolean hybrid, Object watermark) {
            this.sql = sql;
            this.params = params;
            this.preAggName = preAggName;
            this.hybrid = hybrid;
            this.watermark = watermark;
        }
    }

    /**
     * 构建混合查询 SQL（Lambda 架构）
     * <p>
     * 生成的 SQL 结构：
     * <pre>
     * SELECT [columns with aggregation]
     * FROM (
     *   SELECT [columns] FROM preagg_table WHERE watermark_col <= ?
     *   UNION ALL
     *   SELECT [columns] FROM source_table WHERE watermark_col > ?
     * ) AS combined
     * GROUP BY [dimension columns]
     * ORDER BY ...
     * </pre>
     * </p>
     */
    private String buildHybridSql(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                   DbQueryRequestDef queryRequest, JdbcModelQueryEngine queryEngine,
                                   Object watermark) {
        StringBuilder sql = new StringBuilder();
        String preAggTableName = getFullTableName(preAgg);
        String sourceTableName = queryModel.getJdbcModel().getTableName();
        String watermarkColumn = parseWatermarkColumn(preAgg.getWatermarkColumn());

        // 外层 SELECT（带聚合函数）
        List<String> outerSelectColumns = buildOuterSelectColumns(preAgg, jdbcQuery, "combined");
        List<String> groupByColumns = buildGroupByColumns(preAgg, jdbcQuery, "combined");

        sql.append("SELECT ");
        sql.append(String.join(", ", outerSelectColumns));
        sql.append(" FROM (");

        // 内层 UNION: 预聚合表部分
        sql.append(" SELECT ");
        List<String> preAggInnerColumns = buildInnerSelectColumns(preAgg, jdbcQuery, "pa", true);
        sql.append(String.join(", ", preAggInnerColumns));
        sql.append(" FROM ").append(preAggTableName).append(" pa");
        sql.append(" WHERE ").append("pa.").append(watermarkColumn).append(" <= ?");

        // UNION ALL
        sql.append(" UNION ALL ");

        // 内层 UNION: 原始表部分
        sql.append("SELECT ");
        List<String> sourceInnerColumns = buildInnerSelectColumns(preAgg, jdbcQuery, "src", false);
        sql.append(String.join(", ", sourceInnerColumns));
        sql.append(" FROM ").append(sourceTableName).append(" src");
        sql.append(" WHERE ").append("src.").append(watermarkColumn).append(" > ?");

        // 添加原始查询的 WHERE 条件（如果有）
        String originalWhere = buildOriginalWhereClause(jdbcQuery, "src");
        if (originalWhere != null && !originalWhere.isEmpty()) {
            sql.append(" AND ").append(originalWhere);
        }

        sql.append(") AS combined");

        // GROUP BY（混合查询必须聚合）
        if (!groupByColumns.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByColumns));
        }

        // ORDER BY
        String orderByClause = buildOrderByClause(jdbcQuery, "combined");
        if (orderByClause != null && !orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        return sql.toString();
    }

    /**
     * 构建混合查询参数
     * <p>
     * 参数顺序：watermark（用于预聚合表）、watermark（用于原始表）、原始查询参数
     * </p>
     */
    private List<Object> buildHybridParams(List<Object> originalParams, Object watermark) {
        List<Object> params = new ArrayList<>();
        params.add(watermark);  // 预聚合表 WHERE
        params.add(watermark);  // 原始表 WHERE
        if (originalParams != null) {
            params.addAll(originalParams);
        }
        return params;
    }

    /**
     * 构建外层 SELECT 列（带聚合函数）
     */
    private List<String> buildOuterSelectColumns(PreAggregation preAgg, JdbcQuery jdbcQuery, String alias) {
        List<String> columns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return columns;
        }

        Map<String, DbAggregation> measureAggregations = preAgg.getMeasureAggregations();

        for (DbColumn column : select.getColumns()) {
            String columnAlias = column.getAlias();
            String columnName = column.getName();

            if (column.isMeasure()) {
                // 度量列：聚合
                DbAggregation agg = measureAggregations.get(columnName);
                String aggFunc = getAggregationFunction(agg);
                columns.add(aggFunc + "(" + alias + "." + columnAlias + ") AS " + columnAlias);
            } else {
                // 维度列：直接引用
                columns.add(alias + "." + columnAlias + " AS " + columnAlias);
            }
        }

        return columns;
    }

    /**
     * 构建内层 SELECT 列（不带聚合函数）
     */
    private List<String> buildInnerSelectColumns(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                                  String alias, boolean isPreAggTable) {
        List<String> columns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return columns;
        }

        Map<String, String> measureColumnNames = preAgg.getMeasureColumnNames();

        for (DbColumn column : select.getColumns()) {
            String columnAlias = column.getAlias();
            String columnName = column.getName();

            if (column.isMeasure()) {
                if (isPreAggTable) {
                    // 预聚合表：使用映射的列名
                    String preAggColumnName = measureColumnNames.get(columnName);
                    if (preAggColumnName == null) {
                        preAggColumnName = columnName + "_sum";
                    }
                    columns.add(alias + "." + preAggColumnName + " AS " + columnAlias);
                } else {
                    // 原始表：直接使用度量列
                    String sqlColumnName = getSqlColumnName(column);
                    columns.add(alias + "." + sqlColumnName + " AS " + columnAlias);
                }
            } else {
                // 维度/属性列
                String sqlColumnName = getSqlColumnName(column);
                columns.add(alias + "." + sqlColumnName + " AS " + columnAlias);
            }
        }

        return columns;
    }

    /**
     * 解析水位线列名
     */
    private String parseWatermarkColumn(String watermarkColumn) {
        if (watermarkColumn == null) {
            return "created_at"; // 默认
        }
        // 格式：dimensionName$propertyName 或 dimensionName$id
        int dollarIndex = watermarkColumn.indexOf('$');
        if (dollarIndex > 0) {
            return watermarkColumn.substring(0, dollarIndex);
        }
        return watermarkColumn;
    }

    /**
     * 构建原始 WHERE 条件（用于混合查询的原始表部分）
     */
    private String buildOriginalWhereClause(JdbcQuery jdbcQuery, String alias) {
        // 简化实现：暂时不处理复杂的 WHERE 条件
        // TODO: 实现完整的 WHERE 条件转换
        return "";
    }

    // ==================== 单表模式方法（原有实现） ====================

    /**
     * 预聚合 SQL 构建结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class PreAggSqlBuildResult {
        private String sql;
        private boolean whereIncluded;
        private List<Object> whereParams;
    }

    /**
     * 构建使用预聚合表的 SQL
     */
    private PreAggSqlBuildResult buildPreAggSql(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                   DbQueryRequestDef queryRequest, boolean needsRollup) {
        StringBuilder sql = new StringBuilder();
        String preAggTableName = getFullTableName(preAgg);
        String alias = "pa"; // 预聚合表别名

        // SELECT 子句
        sql.append("SELECT ");
        List<String> selectColumns = buildSelectColumns(preAgg, jdbcQuery, alias, needsRollup);
        sql.append(String.join(", ", selectColumns));

        // FROM 子句
        sql.append(" FROM ").append(preAggTableName).append(" ").append(alias);

        // WHERE 子句（从 slices 中生成）
        WhereClauseResult whereResult = buildWhereClauseFromSlices(preAgg, queryRequest, alias);
        boolean whereIncluded = (whereResult.getClause() != null && !whereResult.getClause().isEmpty());
        if (whereIncluded) {
            sql.append(" WHERE ").append(whereResult.getClause());
        }

        // GROUP BY 子句（如果需要 rollup）
        if (needsRollup) {
            List<String> groupByColumns = buildGroupByColumns(preAgg, jdbcQuery, alias);
            if (!groupByColumns.isEmpty()) {
                sql.append(" GROUP BY ").append(String.join(", ", groupByColumns));
            }
        }

        // ORDER BY 子句
        String orderByClause = buildOrderByClause(jdbcQuery, alias);
        if (orderByClause != null && !orderByClause.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause);
        }

        return new PreAggSqlBuildResult(sql.toString(), whereIncluded, whereResult.getParams());
    }

    /**
     * 获取预聚合表的完整表名（包括 schema）
     */
    private String getFullTableName(PreAggregation preAgg) {
        String schema = preAgg.getSchema();
        String tableName = preAgg.getTableName();

        if (schema != null && !schema.isEmpty()) {
            return schema + "." + tableName;
        }
        return tableName;
    }

    /**
     * 构建 SELECT 列
     */
    private List<String> buildSelectColumns(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                             String alias, boolean needsRollup) {
        List<String> columns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return columns;
        }

        Map<String, String> measureColumnNames = preAgg.getMeasureColumnNames();
        Map<String, DbAggregation> measureAggregations = preAgg.getMeasureAggregations();

        for (DbColumn column : select.getColumns()) {
            String columnAlias = column.getAlias();
            String columnName = column.getName();

            if (column.isMeasure()) {
                // 度量列：从预聚合表中获取对应的列名
                String preAggColumnName = measureColumnNames.get(columnName);
                if (preAggColumnName == null) {
                    preAggColumnName = columnName + "_sum"; // 默认命名
                }

                if (needsRollup) {
                    // 需要 rollup：根据聚合类型包装
                    DbAggregation agg = measureAggregations.get(columnName);
                    String aggFunc = getAggregationFunction(agg);
                    columns.add(aggFunc + "(" + alias + "." + preAggColumnName + ") AS " + columnAlias);
                } else {
                    // 不需要 rollup：直接使用
                    columns.add(alias + "." + preAggColumnName + " AS " + columnAlias);
                }
            } else {
                // 维度/属性列：直接映射
                String sqlColumnName = getSqlColumnName(column);
                if (needsRollup) {
                    // rollup 时维度列可能需要聚合处理
                    columns.add(alias + "." + sqlColumnName + " AS " + columnAlias);
                } else {
                    columns.add(alias + "." + sqlColumnName + " AS " + columnAlias);
                }
            }
        }

        return columns;
    }

    /**
     * 获取聚合函数名
     */
    private String getAggregationFunction(DbAggregation agg) {
        if (agg == null) {
            return "SUM";
        }
        switch (agg) {
            case SUM:
            case COUNT:  // COUNT 在 rollup 时变成 SUM
                return "SUM";
            case MIN:
                return "MIN";
            case MAX:
                return "MAX";
            case AVG:
                return "AVG";
            default:
                return "SUM";
        }
    }

    /**
     * 获取列的 SQL 列名
     */
    private String getSqlColumnName(DbColumn column) {
        if (column.getSqlColumn() != null) {
            return column.getSqlColumn().getName();
        }
        return column.getAlias();
    }

    /**
     * WHERE 子句构建结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class WhereClauseResult {
        private String clause;
        private List<Object> params;

        static WhereClauseResult empty() {
            return new WhereClauseResult("", new ArrayList<>());
        }
    }

    /**
     * 从 Slices 构建 WHERE 子句
     *
     * @param preAgg       预聚合
     * @param queryRequest 查询请求
     * @param alias        表别名
     * @return WHERE 子句结果
     */
    private WhereClauseResult buildWhereClauseFromSlices(PreAggregation preAgg,
                                                          DbQueryRequestDef queryRequest,
                                                          String alias) {
        List<SliceRequestDef> slices = queryRequest.getSlice();
        if (slices == null || slices.isEmpty()) {
            return WhereClauseResult.empty();
        }

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (SliceRequestDef slice : slices) {
            WhereClauseResult sliceResult = buildConditionFromSlice(preAgg, slice, alias);
            if (sliceResult.getClause() != null && !sliceResult.getClause().isEmpty()) {
                conditions.add(sliceResult.getClause());
                params.addAll(sliceResult.getParams());
            }
        }

        if (conditions.isEmpty()) {
            return WhereClauseResult.empty();
        }

        return new WhereClauseResult(String.join(" AND ", conditions), params);
    }

    /**
     * 从单个 Slice 条件构建 WHERE 片段（递归处理 $or/$and）
     */
    private WhereClauseResult buildConditionFromSlice(PreAggregation preAgg,
                                                       CondRequestDef cond,
                                                       String alias) {
        if (cond == null) {
            return WhereClauseResult.empty();
        }

        // 处理逻辑组合条件
        if (cond._isLogicalGroup()) {
            List<CondRequestDef> children = cond._getGroupChildren();
            if (children == null || children.isEmpty()) {
                return WhereClauseResult.empty();
            }

            List<String> subConditions = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            String link = cond._getGroupLink(); // "OR" 或 "AND"

            for (CondRequestDef child : children) {
                WhereClauseResult childResult = buildConditionFromSlice(preAgg, child, alias);
                if (childResult.getClause() != null && !childResult.getClause().isEmpty()) {
                    subConditions.add(childResult.getClause());
                    params.addAll(childResult.getParams());
                }
            }

            if (subConditions.isEmpty()) {
                return WhereClauseResult.empty();
            }

            String joined = String.join(" " + link + " ", subConditions);
            return new WhereClauseResult("(" + joined + ")", params);
        }

        // 处理简单条件
        String field = cond.getField();
        String op = cond.getOp();
        Object value = cond.getValue();

        if (field == null || field.isEmpty()) {
            return WhereClauseResult.empty();
        }

        // 解析字段名，获取预聚合表的列名
        String columnName = mapFieldToPreAggColumn(preAgg, field);
        if (columnName == null) {
            log.warn("Cannot map field '{}' to pre-aggregation column, skipping", field);
            return WhereClauseResult.empty();
        }

        // 构建 SQL 条件
        return buildSqlCondition(alias, columnName, op, value);
    }

    /**
     * 将字段名映射到预聚合表的列名
     *
     * @param preAgg 预聚合
     * @param field  字段名（如 product$categoryName 或 salesDate$caption）
     * @return 预聚合表的列名
     */
    private String mapFieldToPreAggColumn(PreAggregation preAgg, String field) {
        // 首先检查预聚合的列名映射
        Map<String, String> columnNames = preAgg.getDimensionPropertyColumnNames();
        if (columnNames != null && columnNames.containsKey(field)) {
            return columnNames.get(field);
        }

        int dollarIndex = field.indexOf('$');
        if (dollarIndex <= 0) {
            // 没有 $，可能是维度主键或度量
            return field;
        }

        String dimName = field.substring(0, dollarIndex);
        String propName = field.substring(dollarIndex + 1);

        // 检查预聚合是否有此维度
        if (!preAgg.hasDimension(dimName)) {
            return null;
        }

        // 对于 caption 和 id，使用列名映射（如果有）或命名约定
        if ("caption".equals(propName)) {
            // 尝试从映射获取，如果没有则使用命名约定
            String mappedKey = dimName + "$caption";
            if (columnNames != null && columnNames.containsKey(mappedKey)) {
                return columnNames.get(mappedKey);
            }
            // 命名约定：日期维度用 full_date，其他用 <dimName>_name
            String snakeCaseDimName = normalizePropertyName(dimName);
            if (snakeCaseDimName.contains("date") || snakeCaseDimName.contains("time")
                    || snakeCaseDimName.contains("day")) {
                return "full_date";
            }
            return snakeCaseDimName + "_name";
        }

        if ("id".equals(propName)) {
            String mappedKey = dimName + "$id";
            if (columnNames != null && columnNames.containsKey(mappedKey)) {
                return columnNames.get(mappedKey);
            }
            // 命名约定：日期维度用 date_key，其他用 <dimName>_key
            String snakeCaseDimName = normalizePropertyName(dimName);
            if (snakeCaseDimName.contains("date") || snakeCaseDimName.contains("time")
                    || snakeCaseDimName.contains("day")) {
                return "date_key";
            }
            return snakeCaseDimName + "_key";
        }

        // 其他属性：将 camelCase 转换为 snake_case
        return normalizePropertyName(propName);
    }

    /**
     * 将属性名标准化为 snake_case 格式
     */
    private static String normalizePropertyName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.contains("_")) {
            return name.toLowerCase();
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 构建 SQL 条件表达式
     */
    private WhereClauseResult buildSqlCondition(String alias, String columnName, String op, Object value) {
        List<Object> params = new ArrayList<>();
        String condition;

        if (op == null) {
            op = "=";
        }

        switch (op.toLowerCase()) {
            case "=":
            case "eq":
                condition = alias + "." + columnName + " = ?";
                params.add(value);
                break;

            case "!=":
            case "<>":
            case "ne":
                condition = alias + "." + columnName + " != ?";
                params.add(value);
                break;

            case ">":
            case "gt":
                condition = alias + "." + columnName + " > ?";
                params.add(value);
                break;

            case ">=":
            case "gte":
                condition = alias + "." + columnName + " >= ?";
                params.add(value);
                break;

            case "<":
            case "lt":
                condition = alias + "." + columnName + " < ?";
                params.add(value);
                break;

            case "<=":
            case "lte":
                condition = alias + "." + columnName + " <= ?";
                params.add(value);
                break;

            case "in":
                if (value instanceof List) {
                    List<?> values = (List<?>) value;
                    if (values.isEmpty()) {
                        return WhereClauseResult.empty();
                    }
                    String placeholders = String.join(", ", values.stream().map(v -> "?").toList());
                    condition = alias + "." + columnName + " IN (" + placeholders + ")";
                    params.addAll(values);
                } else {
                    condition = alias + "." + columnName + " = ?";
                    params.add(value);
                }
                break;

            case "like":
                condition = alias + "." + columnName + " LIKE ?";
                params.add(value);
                break;

            case "[)":
                // 左闭右开区间: >= start AND < end
                if (value instanceof List && ((List<?>) value).size() >= 2) {
                    List<?> range = (List<?>) value;
                    condition = alias + "." + columnName + " >= ? AND " + alias + "." + columnName + " < ?";
                    params.add(range.get(0));
                    params.add(range.get(1));
                } else {
                    return WhereClauseResult.empty();
                }
                break;

            case "[]":
                // 闭区间: >= start AND <= end
                if (value instanceof List && ((List<?>) value).size() >= 2) {
                    List<?> range = (List<?>) value;
                    condition = alias + "." + columnName + " >= ? AND " + alias + "." + columnName + " <= ?";
                    params.add(range.get(0));
                    params.add(range.get(1));
                } else {
                    return WhereClauseResult.empty();
                }
                break;

            default:
                log.warn("Unsupported slice operator '{}', defaulting to '='", op);
                condition = alias + "." + columnName + " = ?";
                params.add(value);
        }

        return new WhereClauseResult(condition, params);
    }

    /**
     * 构建 GROUP BY 列（用于 rollup）
     */
    private List<String> buildGroupByColumns(PreAggregation preAgg, JdbcQuery jdbcQuery, String alias) {
        List<String> groupByColumns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return groupByColumns;
        }

        for (DbColumn column : select.getColumns()) {
            // 只有维度/属性列需要 GROUP BY
            if (column.isDimension() || column.isProperty()) {
                String columnAlias = column.getAlias();
                groupByColumns.add(alias + "." + columnAlias);
            }
        }

        return groupByColumns;
    }

    /**
     * 构建 ORDER BY 子句
     */
    private String buildOrderByClause(JdbcQuery jdbcQuery, String alias) {
        JdbcQuery.JdbcOrder order = jdbcQuery.getOrder();
        if (order == null || order.getOrders() == null || order.getOrders().isEmpty()) {
            return "";
        }

        List<String> orderParts = new ArrayList<>();
        for (var orderColumn : order.getOrders()) {
            DbColumn column = orderColumn.getSelectColumn();
            String columnAlias = column.getAlias();
            String direction = orderColumn.getOrder();
            orderParts.add(alias + "." + columnAlias + " " + direction);
        }

        return String.join(", ", orderParts);
    }
}

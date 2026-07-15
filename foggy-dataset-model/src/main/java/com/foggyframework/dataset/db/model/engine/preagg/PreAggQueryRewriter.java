package com.foggyframework.dataset.db.model.engine.preagg;

import com.foggyframework.core.utils.StringUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.preagg.internal.PreAggWatermarkResolver;
import com.foggyframework.dataset.db.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.db.model.spi.*;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.db.model.spi.support.AggregationDbColumn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static final String FINAL_STAGE_PREDICATE_NOT_PROVABLE =
            "return-total-equivalent-predicate-not-provable";

    private static final Pattern EXPRESSION_TOKEN_PATTERN = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_$]*)");
    private static final Set<String> EXPRESSION_KEYWORDS = Set.of(
            "AND", "OR", "NOT", "NULL", "TRUE", "FALSE"
    );
    private static final String[] COMPARISON_OPERATORS = {
            "!==", "===", "==", ">=", "<=", "!=", "<>", ">", "<", "="
    };

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

        if (isHybrid) {
            Object watermark = matchResult.getWatermark();
            if (!(watermark instanceof LocalDate boundary)
                    || boundary.isAfter(LocalDate.now())) {
                log.debug("Pre-aggregation hybrid rewrite refused because watermark is not "
                        + "a proven LocalDate exclusive boundary");
                return PreAggRewriteResult.notApplied();
            }
        }

        // Explicit groupBy replaces SELECT fields with AggregationDbColumn
        // wrappers. The legacy hybrid builder cannot prove an equivalent
        // source/pre-aggregation UNION for those wrappers, so fall back to
        // the governed source query instead of emitting guessed SQL.
        if (isHybrid && hasAggregationProjection(jdbcQuery)) {
            log.debug("Pre-aggregation hybrid rewrite refused for aggregate projection wrappers");
            return PreAggRewriteResult.notApplied();
        }

        // The legacy hybrid SQL builder applies the request predicate only
        // to the fresh source branch. The materialized-history branch is
        // constrained by watermark alone, so accepting a slice/WHERE/HAVING
        // would admit rows outside the requested domain. Until predicates
        // are independently rebuilt for both branches, fail closed.
        if (isHybrid && hasQueryPredicates(jdbcQuery, queryRequest)) {
            log.debug("Pre-aggregation hybrid rewrite refused because predicates cannot be "
                    + "proved for both UNION branches");
            return PreAggRewriteResult.notApplied();
        }

        try {
            String sql;
            List<Object> params;

            if (isHybrid) {
                // 混合查询模式：UNION SQL
                Object watermark = matchResult.getWatermark();
                // 先从原始 JdbcQuery 提取 WHERE 条件和参数（#005 修复）
                WhereClauseResult originalWhere = extractWhereClause(jdbcQuery.getWhere());
                sql = buildHybridSql(preAgg, jdbcQuery, queryRequest, queryEngine, watermark, originalWhere);
                params = buildHybridParams(originalWhere.getParams(), watermark);

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
     * 此便捷入口会重新执行候选匹配，并与显式 matchResult 入口使用相同的
     * rollup/hybrid fail-closed 约束，避免调用方绕过等价性证明。
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
        if (preAgg == null) {
            return null;
        }
        PreAggQueryRequirement requirement = new PreAggQueryRequirementBuilder()
                .buildAggregate(queryRequest, jdbcQuery, queryModel);
        PreAggregationMatchResult matchResult = new PreAggregationMatcher()
                .findBestMatch(requirement, List.of(preAgg));
        return buildAggregateSql(preAgg, jdbcQuery, queryRequest, matchResult);
    }

    private boolean hasQueryPredicates(JdbcQuery jdbcQuery, DbQueryRequestDef queryRequest) {
        if (queryRequest != null) {
            if (queryRequest.getSlice() != null && !queryRequest.getSlice().isEmpty()) {
                return true;
            }
            if (queryRequest.getHaving() != null && !queryRequest.getHaving().isEmpty()) {
                return true;
            }
        }
        if (jdbcQuery == null) {
            return false;
        }
        return (jdbcQuery.getWhere() != null && !jdbcQuery.getWhere().isEmpty())
                || (jdbcQuery.getHaving() != null && !jdbcQuery.getHaving().isEmpty());
    }

    /**
     * 构建聚合查询 SQL（支持混合查询模式）
     * <p>
     * 当预聚合数据有水位线限制时，使用混合模式：
     * <pre>
     * SELECT COUNT(*), SUM(measure) FROM (
     *   SELECT measure_sum FROM preagg_table WHERE watermark < ?
     *   UNION ALL
     *   SELECT measure FROM source_table WHERE watermark >= ?
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
        if (matchResult == null || !matchResult.isMatched()
                || matchResult.getPreAggregation() != preAgg) {
            return null;
        }
        // Legacy returnTotal counts physical rows directly. A rollup would
        // require counting a grouped subquery, while hybrid would mix
        // materialized group rows with unaggregated source rows. Neither
        // equivalence is implemented, so both paths must fail closed.
        if (matchResult.isNeedsRollup() || matchResult.isHybridQuery()) {
            log.debug("Legacy pre-aggregation returnTotal refused: needsRollup={}, hybrid={}",
                    matchResult.isNeedsRollup(), matchResult.isHybridQuery());
            return null;
        }
        return buildAggregateSqlInternal(preAgg, jdbcQuery, queryRequest,
                matchResult.isHybridQuery(), matchResult.getWatermark());
    }

    /**
     * 构建基于预聚合主查询结果的 final-stage count SQL。
     * <p>
     * 该路径用于多阶段查询中“最终 stage 未新增过滤，只改变投影/派生列”的场景。
     * 直接对预聚合物理表 COUNT(*) 可能会把预聚合粒度行数误当作最终分组行数；
     * 因此这里只在能够明确重建预聚合 rollup 行集时生成 SQL；否则返回 null 交回原始 final-stage count。
     * </p>
     */
    public PreAggAggregateSqlResult buildFinalStageAggregateSql(PreAggRewriteResult rewriteResult,
                                                                 JdbcQuery jdbcQuery,
                                                                 DbQueryRequestDef queryRequest) {
        if (rewriteResult == null || !rewriteResult.isApplied() || rewriteResult.getSql() == null
                || rewriteResult.getPreAggregation() == null || rewriteResult.isHybridQuery()) {
            return null;
        }
        return buildFinalStageAggregateSql(rewriteResult.getPreAggregation(), jdbcQuery, queryRequest);
    }

    /**
     * 针对单个候选预聚合证明并构建 final-stage count SQL。
     */
    public PreAggAggregateSqlResult buildFinalStageAggregateSql(PreAggregation preAgg,
                                                                 JdbcQuery jdbcQuery,
                                                                 DbQueryRequestDef queryRequest) {
        if (preAgg == null || !preAgg.isEnabled()) {
            return null;
        }
        PreAggQueryRequirement requirement = new PreAggQueryRequirementBuilder()
                .buildFinalStage(queryRequest, jdbcQuery, queryModel);
        PreAggregationMatchResult matchResult = new PreAggregationMatcher()
                .findBestMatch(requirement, List.of(preAgg));
        return buildFinalStageAggregateSql(preAgg, jdbcQuery, queryRequest, matchResult);
    }

    /**
     * Consumes the match already produced by the interceptor so an explicit
     * hybrid-disabled snapshot policy is preserved without exposing a public
     * stale-candidate bypass.
     */
    PreAggAggregateSqlResult buildFinalStageAggregateSql(PreAggregation preAgg,
                                                          JdbcQuery jdbcQuery,
                                                          DbQueryRequestDef queryRequest,
                                                          PreAggregationMatchResult matchResult) {
        if (preAgg == null || !preAgg.isEnabled() || matchResult == null
                || !matchResult.isMatched() || matchResult.isHybridQuery()
                || matchResult.getPreAggregation() != preAgg) {
            return null;
        }
        return new FinalStagePreAggAggregateSqlBuilder(queryModel, this)
                .build(preAgg, jdbcQuery, queryRequest);
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
        if (hasAggregationProjection(jdbcQuery)) {
            log.debug("Pre-aggregation returnTotal rewrite refused for aggregate projection wrappers");
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
     *   SELECT measure_sum AS measure FROM preagg_table WHERE watermark < ?
     *   UNION ALL
     *   SELECT measure FROM source_table WHERE watermark >= ? [AND slice conditions]
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
        PreAggWatermarkResolver.Resolution watermarkResolution = resolveWatermark(preAgg);
        String watermarkColumn = watermarkResolution.materializedColumn();
        String watermarkSourceExpression = resolveSourceWatermarkExpression(
                watermarkResolution, jdbcQuery);
        String factTableAlias = queryModel.getAlias(jdbcQuery.getFrom().getFromObject());

        // 外层 SELECT：对 UNION 结果做聚合
        sql.append("SELECT COUNT(*) AS total");
        sql.append(buildAggregateMeasureColumnsForHybrid(preAgg, jdbcQuery, "combined"));

        sql.append(" FROM (");

        // 内层 UNION 第一部分：预聚合表
        sql.append("SELECT ");
        sql.append(buildInnerMeasureColumnsForHybrid(preAgg, jdbcQuery, "pa", true));
        sql.append(" FROM ").append(preAggTableName).append(" pa");
        sql.append(" WHERE ").append("pa.").append(watermarkColumn).append(" < ?");

        // 添加预聚合表的 slice 条件
        WhereClauseResult preAggWhereResult = buildWhereClauseFromSlices(preAgg, queryRequest, "pa");
        if (preAggWhereResult.getClause() != null && !preAggWhereResult.getClause().isEmpty()) {
            sql.append(" AND ").append(preAggWhereResult.getClause());
        }

        // UNION ALL
        sql.append(" UNION ALL ");

        // 内层 UNION 第二部分：原始表（新鲜数据）
        sql.append("SELECT ");
        sql.append(buildInnerMeasureColumnsForHybrid(
                preAgg, jdbcQuery, factTableAlias, false));
        sql.append(" FROM ").append(buildSourceFromClause(jdbcQuery));
        sql.append(" WHERE ").append(watermarkSourceExpression).append(" >= ?");

        // 添加原始表的 slice 条件（需要映射列名）
        WhereClauseResult sourceWhereResult = buildSourceWhereClauseFromSlices(
                preAgg, queryRequest, factTableAlias);
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
     *   SELECT [columns] FROM preagg_table WHERE watermark_col < ?
     *   UNION ALL
     *   SELECT [columns] FROM source_table WHERE watermark_col >= ?
     * ) AS combined
     * GROUP BY [dimension columns]
     * ORDER BY ...
     * </pre>
     * </p>
     */
    private String buildHybridSql(PreAggregation preAgg, JdbcQuery jdbcQuery,
                                   DbQueryRequestDef queryRequest, JdbcModelQueryEngine queryEngine,
                                   Object watermark, WhereClauseResult originalWhere) {
        StringBuilder sql = new StringBuilder();
        String preAggTableName = getFullTableName(preAgg);
        PreAggWatermarkResolver.Resolution watermarkResolution = resolveWatermark(preAgg);
        String watermarkPreAggColumn = watermarkResolution.materializedColumn();
        String watermarkSourceExpression = resolveSourceWatermarkExpression(
                watermarkResolution, jdbcQuery);

        // 外层 SELECT（带聚合函数）
        List<String> outerSelectColumns = buildOuterSelectColumns(preAgg, jdbcQuery, "combined");
        List<String> groupByColumns = buildHybridGroupByColumns(jdbcQuery, "combined");

        sql.append("SELECT ");
        sql.append(String.join(", ", outerSelectColumns));
        sql.append(" FROM (");

        // 内层 UNION: 预聚合表部分
        sql.append(" SELECT ");
        List<String> preAggInnerColumns = buildInnerSelectColumns(preAgg, jdbcQuery, "pa", true);
        sql.append(String.join(", ", preAggInnerColumns));
        sql.append(" FROM ").append(preAggTableName).append(" pa");
        sql.append(" WHERE ").append("pa.").append(watermarkPreAggColumn).append(" < ?");

        // UNION ALL
        sql.append(" UNION ALL ");

        // 内层 UNION: 原始表部分（#006 修复：使用原始 FROM+JOIN 结构）
        sql.append("SELECT ");
        List<String> sourceInnerColumns = buildSourceSelectColumnsWithJoins(jdbcQuery);
        sql.append(String.join(", ", sourceInnerColumns));

        // FROM + JOINs（使用原始查询的表结构和别名，不再用 "src" 单表）
        sql.append(" FROM ");
        sql.append(buildSourceFromClause(jdbcQuery));

        // WHERE: 水位线条件（使用经证明的物理源列与 JOIN）
        sql.append(" WHERE ").append(watermarkSourceExpression).append(" >= ?");

        // #005 修复：追加原始查询的 WHERE 条件（如 d1.full_date >= ? AND d1.full_date < ?）
        if (originalWhere != null && originalWhere.getClause() != null && !originalWhere.getClause().isEmpty()) {
            sql.append(" AND ").append(originalWhere.getClause());
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
     * #005 修复：参数顺序与 SQL 占位符严格对应：
     * watermark（预聚合表 WHERE）、watermark（源表 WHERE）、提取的原始 WHERE 参数。
     * 不再使用 queryEngine.getValues()，避免 SQL 占位符与参数数量不匹配。
     * </p>
     *
     * @param whereParams 从 JdbcQuery.WHERE 提取的参数（已按 SQL 顺序排列）
     * @param watermark   水位线值
     * @return 参数列表
     */
    private List<Object> buildHybridParams(List<Object> whereParams, Object watermark) {
        List<Object> params = new ArrayList<>();
        params.add(watermark);  // 预聚合表 WHERE: pa.date_key < ?
        params.add(watermark);  // 原始表 WHERE: t1.date_key >= ?
        if (whereParams != null) {
            params.addAll(whereParams);  // 原始 WHERE 条件参数
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
            String quotedAlias = queryModel.getDialect().quoteIdentifier(columnAlias);

            if (column.isMeasure()) {
                // 度量列：聚合
                DbAggregation agg = measureAggregations.get(columnName);
                String aggFunc = getAggregationFunction(agg);
                columns.add(aggFunc + "(" + alias + "." + quotedAlias + ") AS " + quotedAlias);
            } else {
                // 维度列：直接引用
                columns.add(alias + "." + quotedAlias + " AS " + quotedAlias);
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
            String quotedAlias = queryModel.getDialect().quoteIdentifier(columnAlias);

            if (column.isMeasure()) {
                if (isPreAggTable) {
                    // 预聚合表：使用映射的列名
                    String preAggColumnName = measureColumnNames.get(columnName);
                    if (preAggColumnName == null) {
                        throw new IllegalStateException(
                                "Missing configured pre-aggregation measure column: " + columnName);
                    }
                    columns.add(alias + "." + preAggColumnName + " AS " + quotedAlias);
                } else {
                    // 原始表：直接使用度量列
                    String sqlColumnName = getSqlColumnName(column);
                    columns.add(alias + "." + sqlColumnName + " AS " + quotedAlias);
                }
            } else {
                // 维度/属性列
                String sqlColumnName = isPreAggTable
                        ? mapFieldToPreAggColumn(preAgg, columnName)
                        : getSqlColumnName(column);
                if (StringUtils.isEmpty(sqlColumnName)) {
                    throw new IllegalStateException(
                            "Missing configured pre-aggregation dimension column: " + columnName);
                }
                columns.add(alias + "." + sqlColumnName + " AS " + quotedAlias);
            }
        }

        return columns;
    }

    private PreAggWatermarkResolver.Resolution resolveWatermark(PreAggregation preAgg) {
        try {
            PreAggWatermarkResolver.Resolution resolution = PreAggWatermarkResolver.resolve(
                    preAgg, queryModel.getJdbcModel(), preAgg.getRefreshConfig());
            // Hybrid query branches bind the same LocalDate domain used by the
            // Addon refresh boundary. Re-prove the source type here because a
            // runtime watermark may be restored or injected independently of
            // the refresh path.
            PreAggWatermarkResolver.requireLocalDateBounds(
                    resolution, queryModel.getDialect());
            return resolution;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Qualify the physical source watermark only after its table role and
     * required JoinGraph path have both been proved.
     */
    private String resolveSourceWatermarkExpression(
            PreAggWatermarkResolver.Resolution resolution,
            JdbcQuery jdbcQuery) {
        if (resolution == null || resolution.sourceColumn() == null
                || resolution.sourceColumn().queryObject() == null
                || StringUtils.isEmpty(resolution.sourceColumn().physicalName())
                || jdbcQuery == null || jdbcQuery.getFrom() == null
                || jdbcQuery.getFrom().getFromObject() == null) {
            throw new IllegalStateException(
                    "Hybrid watermark has no complete physical source contract");
        }

        QueryObject sourceObject = resolution.sourceColumn().queryObject();
        JdbcQuery.JdbcFrom from = jdbcQuery.getFrom();
        if (!sameQueryObjectRole(from.getFromObject(), sourceObject)) {
            try {
                from.join(sourceObject);
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Hybrid watermark source JOIN cannot be proved: "
                                + resolution.configured(), e);
            }
            boolean joined = from.getJoins() != null && from.getJoins().stream()
                    .anyMatch(join -> sameQueryObjectRole(join.getRight(), sourceObject));
            if (!joined) {
                throw new IllegalStateException(
                        "Hybrid watermark source JOIN cannot be proved: "
                                + resolution.configured());
            }
        }

        String alias = queryModel.getAlias(sourceObject);
        if (StringUtils.isEmpty(alias)) {
            throw new IllegalStateException(
                    "Hybrid watermark source alias cannot be proved: "
                            + resolution.configured());
        }
        return alias + "." + queryModel.getDialect().quoteIdentifierIfNeeded(
                resolution.sourceColumn().physicalName());
    }

    private boolean sameQueryObjectRole(QueryObject left, QueryObject right) {
        if (left == right) {
            return true;
        }
        return left != null && right != null
                && !StringUtils.isEmpty(left.getAlias())
                && left.getAlias().equals(right.getAlias());
    }

    /**
     * 构建源表 FROM + JOIN 子句
     * <p>
     * #006 修复：从原始 JdbcQuery 的 FROM 结构提取表和 JOIN 关系，
     * 使用原始表别名（如 t1, d1, d2），支持维度表列的正确引用。
     * </p>
     *
     * @param jdbcQuery 原始 JdbcQuery
     * @return FROM + JOIN SQL 片段
     */
    private String buildSourceFromClause(JdbcQuery jdbcQuery) {
        JdbcQuery.JdbcFrom from = jdbcQuery.getFrom();
        StringBuilder sb = new StringBuilder();

        // 主表
        QueryObject fromObject = from.getFromObject();
        sb.append(fromObject.getBody()).append(" ").append(queryModel.getAlias(fromObject));

        // JOINs（维度表）
        if (from.getJoins() != null) {
            for (JdbcQuery.JdbcFrom.JdbcJoin join : from.getJoins()) {
                sb.append(join.getJoinTypeString());
                sb.append(join.getRight().getBody()).append(" ");
                sb.append(queryModel.getAlias(join.getRight()));

                if (join.getRight().getForceIndex() != null) {
                    sb.append(" ").append(join.getRight().getForceIndex());
                }

                sb.append(" on ");

                // 优先使用预计算的 ON 条件
                String onCondition = join.getOnCondition();
                if (onCondition != null) {
                    sb.append(onCondition);
                } else {
                    // 从 left/FK/right/PK 构建
                    onCondition = queryModel.getAlias(join.getLeft()) + "." + join.getForeignKey() + "="
                            + queryModel.getAlias(join.getRight()) + "." + join.getRight().getPrimaryKey();
                    sb.append(onCondition);
                }
            }
        }

        return sb.toString();
    }

    /**
     * 构建源表 SELECT 列（使用原始表别名）
     * <p>
     * #006 修复：不再使用单一的 "src" 别名，而是使用各列所属表的原始别名，
     * 正确引用维度表上的列（如 d1.full_date, d2.product_name）。
     * </p>
     *
     * @param jdbcQuery 原始 JdbcQuery
     * @return SELECT 列列表（如 ["d1.full_date AS salesDate$caption", "t1.sales_amount AS salesAmount"]）
     */
    private List<String> buildSourceSelectColumnsWithJoins(JdbcQuery jdbcQuery) {
        List<String> columns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();

        if (select == null || select.getColumns() == null) {
            return columns;
        }

        for (DbColumn column : select.getColumns()) {
            String columnAlias = column.getAlias();
            String quotedAlias = queryModel.getDialect().quoteIdentifier(columnAlias);

            if (column.getQueryObject() != null && column.getSqlColumn() != null) {
                // 普通列：使用 queryModel 解析的原始表别名
                String tableAlias = queryModel.getAlias(column.getQueryObject());
                String declare = column.getDeclare(applicationContext, tableAlias, queryModel.getDialect());
                columns.add(declare + " AS " + quotedAlias);
            } else {
                // AggregationDbColumn 等特殊列（无 SqlColumn）
                // 使用 getDeclare 获取预构建的引用，然后去除可能的聚合函数包装
                String declare = column.getDeclare(applicationContext, null);
                if (column.isMeasure() && column.getAggregation() != null
                        && column.getAggregation() != DbAggregation.NONE) {
                    // 去除聚合函数包装：SUM(t1.amount) → t1.amount
                    declare = stripAggregationWrapper(declare);
                }
                columns.add(declare + " AS " + quotedAlias);
            }
        }

        return columns;
    }

    /**
     * 去除聚合函数包装
     * <p>
     * 例如：SUM(t1.sales_amount) → t1.sales_amount
     * </p>
     */
    private String stripAggregationWrapper(String declare) {
        if (declare == null) {
            return declare;
        }
        int openParen = declare.indexOf('(');
        int closeParen = declare.lastIndexOf(')');
        if (openParen >= 0 && closeParen > openParen) {
            return declare.substring(openParen + 1, closeParen);
        }
        return declare;
    }

    /**
     * 从原始 JdbcQuery 的 WHERE 条件中提取 SQL 片段和参数
     * <p>
     * #005 修复：遍历 JdbcWhere 条件树，收集 SQL 片段和对应的参数值。
     * 模式与 SimpleSqlJdbcQueryVisitor.acceptListCond() 一致。
     * </p>
     *
     * @param where JdbcWhere 条件
     * @return WHERE 子句和参数
     */
    private WhereClauseResult extractWhereClause(JdbcQuery.JdbcWhere where) {
        if (where == null || where.isEmpty()) {
            return WhereClauseResult.empty();
        }

        StringBuilder sb = new StringBuilder();
        List<Object> params = new ArrayList<>();
        FDialect dialect = queryModel.getDialect();

        extractListCond(where, sb, params, dialect);

        String clause = sb.toString().trim();
        if (clause.isEmpty()) {
            return WhereClauseResult.empty();
        }

        return new WhereClauseResult(clause, params);
    }

    /**
     * 递归提取 JdbcListCond 中的条件
     * <p>
     * 逻辑与 SimpleSqlJdbcQueryVisitor.acceptListCond() 保持一致，
     * 确保生成的 SQL 和参数顺序正确。
     * </p>
     */
    private void extractListCond(JdbcQuery.JdbcListCond listCond, StringBuilder sb,
                                  List<Object> params, FDialect dialect) {
        if (listCond.getConds().isEmpty()) {
            return;
        }

        // 与 visitor 一致：OR 开头用 1=0，AND 开头用 1=1
        if (StringUtils.equalsIgnoreCase(listCond.getConds().get(0).getLink(), "OR")) {
            sb.append("1=0");
        } else {
            sb.append("1=1");
        }

        for (JdbcQuery.JdbcCond cond : listCond.getConds()) {
            sb.append(" ").append(cond.getLink()).append(" ");

            if (cond instanceof JdbcQuery.JdbcListCond) {
                if (StringUtils.isEmpty(cond.getLink())) {
                    sb.append("and ");
                }
                sb.append("(");
                extractListCond((JdbcQuery.JdbcListCond) cond, sb, params, dialect);
                sb.append(")");
            } else if (cond instanceof JdbcQuery.ValueCond) {
                sb.append(((JdbcQuery.ValueCond) cond).getSqlFragment());
                Object rawValue = ((JdbcQuery.ValueCond) cond).getValue();
                params.add(dialect != null ? dialect.convertParameterValue(rawValue) : rawValue);
            } else if (cond instanceof JdbcQuery.ListValueCond) {
                sb.append(((JdbcQuery.ListValueCond) cond).getSqlFragment());
                List<Object> rawValues = ((JdbcQuery.ListValueCond) cond).getValue();
                for (Object rawValue : rawValues) {
                    params.add(dialect != null ? dialect.convertParameterValue(rawValue) : rawValue);
                }
            } else if (cond instanceof JdbcQuery.SqlFragmentCond) {
                sb.append(((JdbcQuery.SqlFragmentCond) cond).getSqlFragment());
            }
        }
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
    String getFullTableName(PreAggregation preAgg) {
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
            DbColumn semanticColumn = resolveSemanticColumn(column);
            if (semanticColumn == null || StringUtils.isEmpty(semanticColumn.getName())) {
                throw new IllegalStateException(
                        "Cannot resolve aggregate projection alias to pre-aggregation field: " + column.getAlias());
            }
            if (column instanceof AggregationDbColumn aggregationColumn
                    && aggregationColumn.getAggregation() != null
                    && aggregationColumn.getAggregation() != DbAggregation.NONE
                    && !semanticColumn.isMeasure()) {
                throw new IllegalStateException(
                        "Aggregate projection targets a non-measure pre-aggregation field: " + column.getAlias());
            }
            String columnAlias = column.getAlias();
            String quotedColumnAlias = queryModel.getDialect().quoteIdentifier(columnAlias);
            String columnName = semanticColumn.getName();

            if (semanticColumn.isMeasure()) {
                // 度量列：从预聚合表中获取对应的列名
                String preAggColumnName = measureColumnNames.get(columnName);
                if (preAggColumnName == null) {
                    throw new IllegalStateException(
                            "Missing configured pre-aggregation measure column: " + columnName);
                }

                if (needsRollup) {
                    // 需要 rollup：根据聚合类型包装
                    DbAggregation agg = measureAggregations.get(columnName);
                    String aggFunc = getAggregationFunction(agg);
                    columns.add(aggFunc + "(" + alias + "." + preAggColumnName + ") AS " + quotedColumnAlias);
                } else {
                    // 不需要 rollup：直接使用
                    columns.add(alias + "." + preAggColumnName + " AS " + quotedColumnAlias);
                }
            } else {
                // 维度/属性列必须映射到预聚合物理列，不能回退到结果 alias。
                String sqlColumnName = mapFieldToPreAggColumn(preAgg, columnName);
                if (StringUtils.isEmpty(sqlColumnName)) {
                    throw new IllegalStateException(
                            "Missing configured pre-aggregation dimension column: " + columnName);
                }
                if (needsRollup) {
                    // rollup 时维度列可能需要聚合处理
                    columns.add(alias + "." + sqlColumnName + " AS " + quotedColumnAlias);
                } else {
                    columns.add(alias + "." + sqlColumnName + " AS " + quotedColumnAlias);
                }
            }
        }

        return columns;
    }

    /**
     * 获取聚合函数名
     */
    String getAggregationFunction(DbAggregation agg) {
        if (agg == null) {
            throw new IllegalArgumentException("Missing pre-aggregation measure aggregation");
        }
        switch (agg) {
            case SUM:
            case COUNT:  // COUNT 在 rollup 时变成 SUM
                return "SUM";
            case MIN:
                return "MIN";
            case MAX:
                return "MAX";
            default:
                throw new IllegalArgumentException(
                        "Pre-aggregation measure cannot be rolled up safely: " + agg);
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
    static class WhereClauseResult {
        private String clause;
        private List<Object> params;

        static WhereClauseResult empty() {
            return new WhereClauseResult("", new ArrayList<>());
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    static class ProvableWhereClauseResult {
        private boolean applied;
        private String clause;
        private List<Object> params;
        private String unsupportedReason;

        static ProvableWhereClauseResult proven(String clause, List<Object> params) {
            return new ProvableWhereClauseResult(true, clause, params != null ? params : new ArrayList<>(), null);
        }

        static ProvableWhereClauseResult unsupported(String reason) {
            return new ProvableWhereClauseResult(false, "", new ArrayList<>(), reason);
        }
    }

    public static class PredicateNotProvableException extends RuntimeException {
        private final String reason;
        private final String detail;

        public PredicateNotProvableException(String detail) {
            super(detail);
            this.reason = FINAL_STAGE_PREDICATE_NOT_PROVABLE;
            this.detail = detail;
        }

        public String getReason() {
            return reason;
        }

        public String getDetail() {
            return detail;
        }
    }

    private record ProvableExpressionPart(String sql, String unsupportedReason) {
        static ProvableExpressionPart proven(String sql) {
            return new ProvableExpressionPart(sql, null);
        }

        static ProvableExpressionPart unsupported(String reason) {
            return new ProvableExpressionPart("", reason);
        }

        boolean isApplied() {
            return unsupportedReason == null;
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
    WhereClauseResult buildWhereClauseFromSlices(PreAggregation preAgg,
                                                 DbQueryRequestDef queryRequest,
                                                 String alias) {
        ProvableWhereClauseResult proven =
                buildProvableWhereClauseFromSlices(preAgg, queryRequest, alias);
        if (!proven.isApplied()) {
            throw new PredicateNotProvableException(proven.getUnsupportedReason());
        }
        return new WhereClauseResult(proven.getClause(), proven.getParams());
    }

    ProvableWhereClauseResult buildProvableWhereClauseFromSlices(PreAggregation preAgg,
                                                                 DbQueryRequestDef queryRequest,
                                                                 String alias) {
        if (queryRequest == null || queryRequest.getSlice() == null || queryRequest.getSlice().isEmpty()) {
            return ProvableWhereClauseResult.proven("", new ArrayList<>());
        }

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        for (SliceRequestDef slice : queryRequest.getSlice()) {
            ProvableWhereClauseResult sliceResult = buildProvableConditionFromSlice(preAgg, slice, alias);
            if (!sliceResult.isApplied()) {
                return sliceResult;
            }
            if (!StringUtils.isEmpty(sliceResult.getClause())) {
                conditions.add(sliceResult.getClause());
                params.addAll(sliceResult.getParams());
            }
        }

        return ProvableWhereClauseResult.proven(String.join(" AND ", conditions), params);
    }

    private ProvableWhereClauseResult buildProvableConditionFromSlice(PreAggregation preAgg,
                                                                      CondRequestDef cond,
                                                                      String alias) {
        if (cond == null) {
            return ProvableWhereClauseResult.unsupported("null-slice-condition");
        }

        if (cond._isExpressionCondition()) {
            return buildProvableExpressionConditionForPreAgg(preAgg, cond.getExpr(), alias);
        }

        if (cond._isFieldReference()) {
            return buildProvableFieldReferenceConditionForPreAgg(preAgg, cond, alias);
        }

        if (cond._isLogicalGroup()) {
            List<CondRequestDef> children = cond._getGroupChildren();
            if (children == null || children.isEmpty()) {
                return ProvableWhereClauseResult.unsupported("empty-logical-group");
            }

            List<String> subConditions = new ArrayList<>();
            List<Object> params = new ArrayList<>();
            String link = cond._getGroupLink();

            for (CondRequestDef child : children) {
                ProvableWhereClauseResult childResult = buildProvableConditionFromSlice(preAgg, child, alias);
                if (!childResult.isApplied()) {
                    return childResult;
                }
                if (StringUtils.isEmpty(childResult.getClause())) {
                    return ProvableWhereClauseResult.unsupported("empty-logical-group-child");
                }
                subConditions.add(childResult.getClause());
                params.addAll(childResult.getParams());
            }

            return ProvableWhereClauseResult.proven(
                    "(" + String.join(" " + link + " ", subConditions) + ")",
                    params
            );
        }

        String field = cond.getField();
        if (StringUtils.isEmpty(field)) {
            return ProvableWhereClauseResult.unsupported("empty-slice-field");
        }

        String columnName = resolveProvablePreAggColumn(preAgg, field);
        if (StringUtils.isEmpty(columnName)) {
            return ProvableWhereClauseResult.unsupported("unmapped-slice-field:" + field);
        }

        return buildProvableSqlCondition(alias, columnName, field, cond.getOp(), cond.getValue());
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

        // 处理 $expr 表达式条件
        if (cond._isExpressionCondition()) {
            return buildExpressionConditionForPreAgg(preAgg, cond.getExpr(), alias);
        }

        // 处理 $field 字段引用
        if (cond._isFieldReference()) {
            return buildFieldReferenceConditionForPreAgg(preAgg, cond, alias);
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
     * 构建 $expr 表达式条件（预聚合版本）
     * <p>
     * 由于预聚合场景不使用复杂的表达式引擎，此方法解析简单的字段间比较表达式。
     * </p>
     *
     * @param preAgg     预聚合
     * @param expression 表达式字符串
     * @param alias      表别名
     * @return WHERE 子句结果
     * @since 8.3.0
     */
    private WhereClauseResult buildExpressionConditionForPreAgg(PreAggregation preAgg,
                                                                  String expression,
                                                                  String alias) {
        // 简单解析：支持基本的字段间比较（field1 op field2）
        // 例如："actualAmount > budgetAmount"
        String[] comparisonOps = {" >= ", " <= ", " != ", " <> ", " > ", " < ", " = "};

        for (String compOp : comparisonOps) {
            int opIndex = expression.indexOf(compOp);
            if (opIndex > 0) {
                String leftPart = expression.substring(0, opIndex).trim();
                String rightPart = expression.substring(opIndex + compOp.length()).trim();
                String sqlOp = compOp.trim();

                // 尝试映射左右两侧字段名
                String leftColumn = mapFieldToPreAggColumn(preAgg, leftPart);
                String rightColumn = mapFieldToPreAggColumn(preAgg, rightPart);

                if (leftColumn != null && rightColumn != null) {
                    String sql = alias + "." + leftColumn + " " + sqlOp + " " + alias + "." + rightColumn;
                    if (log.isDebugEnabled()) {
                        log.debug("PreAgg $expr '{}' -> SQL: {}", expression, sql);
                    }
                    return new WhereClauseResult(sql, new ArrayList<>());
                }

                // 如果右侧不是字段名，可能是数值或表达式
                if (leftColumn != null) {
                    // 尝试将右侧作为字面值处理
                    try {
                        // 检查是否包含其他字段引用
                        if (rightPart.matches(".*[a-zA-Z_][a-zA-Z0-9_]*.*")) {
                            // 可能包含字段名，尝试进行表达式替换
                            String processedRight = processExpressionPart(preAgg, rightPart, alias);
                            String sql = alias + "." + leftColumn + " " + sqlOp + " " + processedRight;
                            if (log.isDebugEnabled()) {
                                log.debug("PreAgg $expr '{}' -> SQL: {}", expression, sql);
                            }
                            return new WhereClauseResult(sql, new ArrayList<>());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process $expr right part: {}", rightPart, e);
                    }
                }

                log.warn("Cannot fully resolve $expr '{}' for pre-aggregation", expression);
                break;
            }
        }

        log.warn("Unsupported $expr expression for pre-aggregation: {}", expression);
        return WhereClauseResult.empty();
    }

    /**
     * 处理表达式中的字段部分
     */
    private String processExpressionPart(PreAggregation preAgg, String part, String alias) {
        // 简单的字段名替换：替换标识符为带别名的列名
        // 匹配字段名（字母开头，包含字母数字下划线和$）
        String result = part;
        java.util.regex.Pattern fieldPattern = java.util.regex.Pattern.compile("([a-zA-Z_][a-zA-Z0-9_$]*)");
        java.util.regex.Matcher matcher = fieldPattern.matcher(part);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            // 跳过数字和SQL关键字
            if (token.matches("\\d+") ||
                    java.util.Set.of("AND", "OR", "NOT", "NULL", "TRUE", "FALSE").contains(token.toUpperCase())) {
                matcher.appendReplacement(sb, token);
                continue;
            }
            String mapped = mapFieldToPreAggColumn(preAgg, token);
            if (mapped != null) {
                matcher.appendReplacement(sb, alias + "." + mapped);
            } else {
                // 无法映射，保持原样
                matcher.appendReplacement(sb, token);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 构建 $field 字段引用条件（预聚合版本）
     *
     * @param preAgg 预聚合
     * @param cond   条件定义
     * @param alias  表别名
     * @return WHERE 子句结果
     * @since 8.3.0
     */
    private WhereClauseResult buildFieldReferenceConditionForPreAgg(PreAggregation preAgg,
                                                                      CondRequestDef cond,
                                                                      String alias) {
        String leftField = cond.getField();
        String rightField = cond._getReferencedField();
        String op = cond.getOp();

        // 映射字段名
        String leftColumn = mapFieldToPreAggColumn(preAgg, leftField);
        String rightColumn = mapFieldToPreAggColumn(preAgg, rightField);

        if (leftColumn == null) {
            log.warn("Cannot map left field '{}' to pre-aggregation column", leftField);
            return WhereClauseResult.empty();
        }
        if (rightColumn == null) {
            log.warn("Cannot map right field '{}' to pre-aggregation column", rightField);
            return WhereClauseResult.empty();
        }

        // 规范化操作符
        String normalizedOp = normalizeOperatorForPreAgg(op);

        // 构建 SQL
        String sql = alias + "." + leftColumn + " " + normalizedOp + " " + alias + "." + rightColumn;

        if (log.isDebugEnabled()) {
            log.debug("PreAgg $field: {} {} ${} -> SQL: {}",
                    leftField, op, rightField, sql);
        }

        return new WhereClauseResult(sql, new ArrayList<>());
    }

    private ProvableWhereClauseResult buildProvableExpressionConditionForPreAgg(PreAggregation preAgg,
                                                                                String expression,
                                                                                String alias) {
        if (StringUtils.isEmpty(expression)) {
            return ProvableWhereClauseResult.unsupported("empty-expression");
        }

        for (String sqlOp : COMPARISON_OPERATORS) {
            int opIndex = expression.indexOf(sqlOp);
            if (opIndex <= 0) {
                continue;
            }

            String leftPart = expression.substring(0, opIndex).trim();
            String rightPart = expression.substring(opIndex + sqlOp.length()).trim();
            if (StringUtils.isEmpty(leftPart) || StringUtils.isEmpty(rightPart)) {
                return ProvableWhereClauseResult.unsupported("malformed-expression:" + expression);
            }

            String leftColumn = resolveProvablePreAggColumn(preAgg, leftPart);
            if (StringUtils.isEmpty(leftColumn)) {
                return ProvableWhereClauseResult.unsupported("unmapped-expression-left:" + leftPart);
            }

            ProvableExpressionPart right = processProvableExpressionPart(preAgg, rightPart, alias);
            if (!right.isApplied()) {
                return ProvableWhereClauseResult.unsupported(right.unsupportedReason());
            }

            String normalizedSqlOp = switch (sqlOp) {
                case "==", "===" -> "=";
                case "!==" -> "!=";
                default -> sqlOp;
            };
            return ProvableWhereClauseResult.proven(
                    alias + "." + leftColumn + " " + normalizedSqlOp + " " + right.sql(),
                    new ArrayList<>()
            );
        }

        return ProvableWhereClauseResult.unsupported("unsupported-expression:" + expression);
    }

    private ProvableExpressionPart processProvableExpressionPart(PreAggregation preAgg,
                                                                 String part,
                                                                 String alias) {
        if (StringUtils.isEmpty(part)) {
            return ProvableExpressionPart.unsupported("empty-expression-part");
        }
        if (!part.matches("[A-Za-z0-9_$\\s+\\-*/().]+")) {
            return ProvableExpressionPart.unsupported("unsupported-expression-token:" + part);
        }

        Matcher matcher = EXPRESSION_TOKEN_PATTERN.matcher(part);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String token = matcher.group(1);
            if (EXPRESSION_KEYWORDS.contains(token.toUpperCase())) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(token));
                continue;
            }

            String mapped = resolveProvablePreAggColumn(preAgg, token);
            if (StringUtils.isEmpty(mapped)) {
                return ProvableExpressionPart.unsupported("unmapped-expression-token:" + token);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(alias + "." + mapped));
        }
        matcher.appendTail(sb);
        return ProvableExpressionPart.proven(sb.toString());
    }

    private ProvableWhereClauseResult buildProvableFieldReferenceConditionForPreAgg(PreAggregation preAgg,
                                                                                   CondRequestDef cond,
                                                                                   String alias) {
        String leftField = cond.getField();
        String rightField = cond._getReferencedField();
        String sqlOp = normalizeComparisonOperatorForPreAgg(cond.getOp());

        if (StringUtils.isEmpty(leftField) || StringUtils.isEmpty(rightField)) {
            return ProvableWhereClauseResult.unsupported("empty-field-reference");
        }
        if (StringUtils.isEmpty(sqlOp)) {
            return ProvableWhereClauseResult.unsupported("unsupported-field-reference-op:" + cond.getOp());
        }

        String leftColumn = resolveProvablePreAggColumn(preAgg, leftField);
        if (StringUtils.isEmpty(leftColumn)) {
            return ProvableWhereClauseResult.unsupported("unmapped-field-reference-left:" + leftField);
        }
        String rightColumn = resolveProvablePreAggColumn(preAgg, rightField);
        if (StringUtils.isEmpty(rightColumn)) {
            return ProvableWhereClauseResult.unsupported("unmapped-field-reference-right:" + rightField);
        }

        return ProvableWhereClauseResult.proven(
                alias + "." + leftColumn + " " + sqlOp + " " + alias + "." + rightColumn,
                new ArrayList<>()
        );
    }

    private ProvableWhereClauseResult buildProvableSqlCondition(String alias,
                                                                String columnName,
                                                                String semanticField,
                                                                String op,
                                                                Object value) {
        List<Object> params = new ArrayList<>();
        String normalizedOp = op == null ? "=" : op.toLowerCase();
        String condition;

        try {
            switch (normalizedOp) {
                case "=":
                case "eq":
                    condition = buildProvableComparison(alias, columnName, "=", semanticField, value, params);
                    break;

                case "!=":
                case "<>":
                case "ne":
                    condition = buildProvableComparison(alias, columnName, "!=", semanticField, value, params);
                    break;

                case ">":
                case "gt":
                    condition = buildProvableComparison(alias, columnName, ">", semanticField, value, params);
                    break;

                case ">=":
                case "gte":
                    condition = buildProvableComparison(alias, columnName, ">=", semanticField, value, params);
                    break;

                case "<":
                case "lt":
                    condition = buildProvableComparison(alias, columnName, "<", semanticField, value, params);
                    break;

                case "<=":
                case "lte":
                    condition = buildProvableComparison(alias, columnName, "<=", semanticField, value, params);
                    break;

                case "in":
                    if (!(value instanceof List<?> values) || values.isEmpty()) {
                        return ProvableWhereClauseResult.unsupported("invalid-in-values:" + semanticField);
                    }
                    condition = alias + "." + columnName + " IN ("
                            + String.join(", ", values.stream().map(v -> "?").toList())
                            + ")";
                    for (Object item : values) {
                        params.add(formatProvableSliceValue(semanticField, item));
                    }
                    break;

                case "like":
                case "left_like":
                case "right_like":
                    if (StringUtils.isEmpty(value) || value instanceof List<?>) {
                        return ProvableWhereClauseResult.unsupported("invalid-like-value:" + semanticField);
                    }
                    condition = alias + "." + columnName + " LIKE ?";
                    Object formattedLike = formatProvableSliceValue(semanticField, value);
                    if ("left_like".equals(normalizedOp)) {
                        params.add("%" + formattedLike);
                    } else if ("right_like".equals(normalizedOp)) {
                        params.add(formattedLike + "%");
                    } else {
                        params.add("%" + formattedLike + "%");
                    }
                    break;

                case "[)":
                case "[]":
                case "(]":
                case "()":
                    if (!(value instanceof List<?> range) || range.size() < 2) {
                        return ProvableWhereClauseResult.unsupported("invalid-range:" + semanticField);
                    }
                    Object start = formatProvableSliceValue(semanticField, range.get(0));
                    Object end = formatProvableSliceValue(semanticField, range.get(1));
                    List<String> rangeConditions = new ArrayList<>();
                    if (StringUtils.isNotEmpty(start)) {
                        rangeConditions.add(alias + "." + columnName
                                + (normalizedOp.charAt(0) == '[' ? " >= ?" : " > ?"));
                        params.add(start);
                    }
                    if (StringUtils.isNotEmpty(end)) {
                        rangeConditions.add(alias + "." + columnName
                                + (normalizedOp.charAt(1) == ']' ? " <= ?" : " < ?"));
                        params.add(end);
                    }
                    condition = String.join(" AND ", rangeConditions);
                    break;

                default:
                    return ProvableWhereClauseResult.unsupported("unsupported-slice-op:" + op);
            }
        } catch (RuntimeException ex) {
            log.debug("Cannot format final-stage pre-aggregation predicate field='{}', op='{}': {}",
                    semanticField, op, ex.getMessage());
            return ProvableWhereClauseResult.unsupported("invalid-slice-value:" + semanticField);
        }

        return ProvableWhereClauseResult.proven(condition, params);
    }

    private String buildProvableComparison(String alias,
                                            String columnName,
                                            String sqlOperator,
                                            String semanticField,
                                            Object value,
                                            List<Object> params) {
        if (StringUtils.isEmpty(value) || value instanceof List<?>) {
            throw new IllegalArgumentException("comparison requires one non-empty value");
        }
        params.add(formatProvableSliceValue(semanticField, value));
        return alias + "." + columnName + " " + sqlOperator + " ?";
    }

    private Object formatProvableSliceValue(String semanticField, Object value) {
        DbColumn semanticColumn = queryModel.findJdbcColumnForCond(semanticField, false, true);
        if (semanticColumn == null || semanticColumn.isCalculatedField()) {
            throw new IllegalArgumentException("semantic field is not directly formattable: " + semanticField);
        }
        return semanticColumn.getFormatter(true).format(value);
    }

    private String normalizeComparisonOperatorForPreAgg(String op) {
        String normalized = normalizeOperatorForPreAgg(op);
        if ("=".equals(normalized) || "!=".equals(normalized)
                || ">".equals(normalized) || ">=".equals(normalized)
                || "<".equals(normalized) || "<=".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String resolveProvablePreAggColumn(PreAggregation preAgg, String field) {
        if (preAgg == null || StringUtils.isEmpty(field)) {
            return null;
        }

        int dollarIndex = field.indexOf('$');
        if (dollarIndex <= 0) {
            return null;
        }

        String dimName = field.substring(0, dollarIndex);
        String propName = field.substring(dollarIndex + 1);
        if (StringUtils.isEmpty(dimName) || StringUtils.isEmpty(propName) || !preAgg.hasDimension(dimName)) {
            return null;
        }

        if (!isProvableDimensionProperty(preAgg, dimName, propName)) {
            return null;
        }

        String columnName = mapFieldToPreAggColumn(preAgg, field);
        return StringUtils.isEmpty(columnName) ? null : columnName;
    }

    private boolean isProvableDimensionProperty(PreAggregation preAgg,
                                                String dimName,
                                                String propName) {
        return preAgg.hasMaterializedDimensionProperty(dimName, propName);
    }

    /**
     * 规范化操作符（预聚合版本）
     */
    private String normalizeOperatorForPreAgg(String op) {
        if (op == null) {
            return "=";
        }
        switch (op.toLowerCase()) {
            case "eq":
                return "=";
            case "ne":
            case "<>":
                return "!=";
            case "gt":
                return ">";
            case "gte":
                return ">=";
            case "lt":
                return "<";
            case "lte":
                return "<=";
            default:
                return op;
        }
    }

    /**
     * 将字段名映射到预聚合表的列名
     *
     * @param preAgg 预聚合
     * @param field  字段名（如 product$categoryName 或 salesDate$caption）
     * @return 预聚合表的列名
     */
    String mapFieldToPreAggColumn(PreAggregation preAgg, String field) {
        if (preAgg == null || StringUtils.isEmpty(field)) {
            return null;
        }
        int dollarIndex = field.indexOf('$');
        if (dollarIndex <= 0) {
            // 该方法只解析有语义维度来源的字段。度量由显式 measure
            // mapping 路径处理，裸物理列不能在这里被猜测放行。
            return null;
        }

        String dimName = field.substring(0, dollarIndex);
        String propName = field.substring(dollarIndex + 1);

        // 检查预聚合是否有此维度
        if (!preAgg.hasDimension(dimName)) {
            return null;
        }

        // 命名约定只负责在模型已声明物化属性后生成列名，不能反向证明
        // caption/id/time bucket 或普通属性真实存在于物化表。
        if (!preAgg.hasMaterializedDimensionProperty(dimName, propName)) {
            return null;
        }

        // 完成物化契约证明后，才允许使用显式或约定生成的列名。
        Map<String, String> columnNames = preAgg.getDimensionPropertyColumnNames();
        if (columnNames != null && columnNames.containsKey(field)) {
            return columnNames.get(field);
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
            DbColumn semanticColumn = resolveSemanticColumn(column);
            // 只有维度/属性列需要 GROUP BY
            if (semanticColumn != null && (semanticColumn.isDimension() || semanticColumn.isProperty())) {
                String preAggColumn = mapFieldToPreAggColumn(preAgg, semanticColumn.getName());
                if (StringUtils.isEmpty(preAggColumn)) {
                    throw new IllegalStateException(
                            "Missing configured pre-aggregation group column: " + semanticColumn.getName());
                }
                groupByColumns.add(alias + "." + preAggColumn);
            }
        }

        return groupByColumns;
    }

    private List<String> buildHybridGroupByColumns(JdbcQuery jdbcQuery, String alias) {
        List<String> groupByColumns = new ArrayList<>();
        JdbcQuery.JdbcSelect select = jdbcQuery.getSelect();
        if (select == null || select.getColumns() == null) {
            return groupByColumns;
        }
        for (DbColumn column : select.getColumns()) {
            DbColumn semanticColumn = resolveSemanticColumn(column);
            if (semanticColumn != null && (semanticColumn.isDimension() || semanticColumn.isProperty())) {
                groupByColumns.add(alias + "."
                        + queryModel.getDialect().quoteIdentifier(column.getAlias()));
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
            orderParts.add(queryModel.getDialect().quoteIdentifier(columnAlias) + " " + direction);
        }

        return String.join(", ", orderParts);
    }

    private DbColumn resolveSemanticColumn(DbColumn column) {
        if (!(column instanceof AggregationDbColumn)) {
            return column;
        }
        String alias = column.getAlias();
        if (StringUtils.isEmpty(alias)) {
            return null;
        }
        return queryModel.findJdbcColumnForCond(alias, false, true);
    }

    private boolean hasAggregationProjection(JdbcQuery jdbcQuery) {
        return jdbcQuery != null
                && jdbcQuery.getSelect() != null
                && jdbcQuery.getSelect().getColumns() != null
                && jdbcQuery.getSelect().getColumns().stream()
                .anyMatch(AggregationDbColumn.class::isInstance);
    }
}

package com.foggyframework.dataset.model.engine.preagg;

import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.query.JdbcQuery;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.model.semantic.permission.PermissionPredicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.function.Consumer;

/**
 * 预聚合拦截器
 * <p>
 * 在查询执行前检查是否可以使用预聚合表，并进行查询重写。
 * </p>
 * <p>
 * 支持两种查询模式：
 * <ul>
 *   <li>完全预聚合模式：仅从预聚合表查询</li>
 *   <li>混合查询模式（Lambda 架构）：预聚合表 UNION 原始表</li>
 * </ul>
 * </p>
 * <p>
 * 使用方式：
 * <pre>
 * PreAggregationInterceptor interceptor = new PreAggregationInterceptor(appContext);
 * PreAggRewriteResult result = interceptor.tryRewrite(queryEngine, queryModel, context);
 * if (result.isApplied()) {
 *     // 使用重写后的 SQL
 *     String sql = result.getSql();
 *     if (result.isHybridQuery()) {
 *         // 混合查询模式
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggregationInterceptor {

    private final ApplicationContext applicationContext;
    private final PreAggregationMatcher matcher;
    private final PreAggQueryRequirementBuilder requirementBuilder;

    /**
     * 是否启用混合查询
     */
    private boolean hybridQueryEnabled = true;
    private List<PermissionPredicate> securityPredicates = List.of();
    private boolean securityContextCacheable = true;
    private Consumer<PreAggregationMatcher.CandidateDecision> candidateDecisionObserver;

    public PreAggregationInterceptor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.matcher = new PreAggregationMatcher();
        this.requirementBuilder = new PreAggQueryRequirementBuilder();
    }

    /**
     * 设置是否启用混合查询
     *
     * @param enabled true 启用混合查询
     */
    public void setHybridQueryEnabled(boolean enabled) {
        this.hybridQueryEnabled = enabled;
        this.matcher.setHybridQueryEnabled(enabled);
    }

    public void setPermissionContext(
            List<PermissionPredicate> predicates,
            boolean cacheable
    ) {
        this.securityPredicates = predicates == null ? List.of() : List.copyOf(predicates);
        this.securityContextCacheable = cacheable;
    }

    /** Installs an optional request-scoped observer used only by explain mode. */
    public void setCandidateDecisionObserver(
            Consumer<PreAggregationMatcher.CandidateDecision> observer
    ) {
        this.candidateDecisionObserver = observer;
    }

    /**
     * 尝试使用预聚合重写查询
     *
     * @param queryEngine 查询引擎（已完成 analysisQueryRequest）
     * @param queryModel  查询模型
     * @param queryRequest 查询请求
     * @return 重写结果
     */
    public PreAggRewriteResult tryRewrite(JdbcModelQueryEngine queryEngine,
                                           JdbcQueryModel queryModel,
                                           DbQueryRequestDef queryRequest) {
        // 1. 获取可用的预聚合列表
        List<PreAggregation> preAggregations = getPreAggregations(queryModel);
        if (preAggregations == null || preAggregations.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("No pre-aggregations configured for model: {}", queryModel.getName());
            }
            return PreAggRewriteResult.notApplied(
                    "PREAGG_NOT_CONFIGURED", "No pre-aggregations configured");
        }

        // 2. 从查询中提取需求
        JdbcQuery jdbcQuery = queryEngine.getJdbcQuery();
        PreAggQueryRequirement requirement = requirementBuilder.build(
                queryRequest, jdbcQuery, queryModel,
                securityPredicates, securityContextCacheable);

        if (log.isDebugEnabled()) {
            log.debug("Query requirement: {}", requirement);
        }

        // 3. 匹配最佳预聚合
        PreAggregationMatchResult matchResult = matcher.findBestMatch(
                requirement, preAggregations, candidateDecisionObserver);

        if (!matchResult.isMatched()) {
            if (log.isDebugEnabled()) {
                log.debug("No pre-aggregation matched: {}", matchResult.getReason());
            }
            return PreAggRewriteResult.notApplied(
                    matchResult.getReasonCode(), matchResult.getReason());
        }

        // 4. 记录匹配结果
        if (matchResult.isHybridQuery()) {
            log.info("Using hybrid query mode for pre-aggregation '{}', watermark={}",
                    matchResult.getPreAggName(), matchResult.getWatermark());
        } else {
            log.info("Using full pre-aggregation query for '{}'", matchResult.getPreAggName());
        }

        // 5. 重写查询
        PreAggQueryRewriter rewriter = new PreAggQueryRewriter(queryModel, applicationContext);
        return rewriter.rewrite(matchResult, jdbcQuery, queryRequest, queryEngine);
    }

    /**
     * 获取模型的预聚合列表
     */
    private List<PreAggregation> getPreAggregations(JdbcQueryModel queryModel) {
        // 从 TableModel 获取预聚合配置
        TableModel tableModel = queryModel.getJdbcModel();
        if (tableModel == null) {
            return null;
        }

        return tableModel.getPreAggregations();
    }

    /**
     * 尝试为聚合查询（returnTotal）构建预聚合 SQL
     * <p>
     * 当主查询是明细查询（无 GROUP BY）时，聚合查询仍然可以使用预聚合表。
     * 这对于 returnTotal=true 的场景特别有用，可以大幅提升性能。
     * </p>
     *
     * @param queryEngine  查询引擎
     * @param queryModel   查询模型
     * @param queryRequest 查询请求
     * @return 聚合 SQL 结果，如果不适用返回 null
     */
    public PreAggQueryRewriter.PreAggAggregateSqlResult tryBuildAggregateSql(
            JdbcModelQueryEngine queryEngine,
            JdbcQueryModel queryModel,
            DbQueryRequestDef queryRequest) {

        // 1. 获取可用的预聚合列表
        List<PreAggregation> preAggregations = getPreAggregations(queryModel);
        if (preAggregations == null || preAggregations.isEmpty()) {
            return null;
        }

        // 2. 从查询中提取需求（专门用于聚合查询）
        JdbcQuery jdbcQuery = queryEngine.getJdbcQuery();
        PreAggQueryRequirement requirement = buildAggregateRequirement(queryRequest, jdbcQuery, queryModel);

        if (log.isDebugEnabled()) {
            log.debug("Aggregate query requirement: {}", requirement);
        }

        // 3. 匹配最佳预聚合
        PreAggregationMatchResult matchResult = matcher.findBestMatch(requirement, preAggregations);

        if (!matchResult.isMatched()) {
            if (log.isDebugEnabled()) {
                log.debug("No pre-aggregation matched for aggregate query: {}", matchResult.getReason());
            }
            return null;
        }

        // 4. 构建聚合 SQL（支持混合查询模式）
        PreAggregation preAgg = matchResult.getPreAggregation();
        PreAggQueryRewriter rewriter = new PreAggQueryRewriter(queryModel, applicationContext);
        PreAggQueryRewriter.PreAggAggregateSqlResult result = rewriter.buildAggregateSql(
                preAgg, jdbcQuery, queryRequest, matchResult);

        if (result != null) {
            if (result.isHybrid()) {
                log.info("Using pre-aggregation '{}' for aggregate query (returnTotal) in HYBRID mode, watermark={}",
                        preAgg.getName(), result.getWatermark());
            } else {
                log.info("Using pre-aggregation '{}' for aggregate query (returnTotal)", preAgg.getName());
            }
        }

        return result;
    }

    /**
     * 为多阶段 final-stage count 构建等价的预聚合聚合 SQL。
     * <p>
     * 该路径不依赖主查询重写：final-stage 中的派生聚合投影可能不是主重写器
     * 可安全消费的语义列。候选先通过 final-stage requirement/matcher 证明粒度和聚合兼容，
     * 选中后再由独立 builder 证明分组、度量和谓词都可重建；hybrid 候选一律 fail closed。
     * </p>
     */
    public PreAggQueryRewriter.PreAggAggregateSqlResult tryBuildFinalStageAggregateSql(
            JdbcModelQueryEngine queryEngine,
            JdbcQueryModel queryModel,
            DbQueryRequestDef queryRequest) {

        List<PreAggregation> configured = getPreAggregations(queryModel);
        if (configured == null || configured.isEmpty()) {
            return null;
        }

        JdbcQuery jdbcQuery = queryEngine.getJdbcQuery();
        if (jdbcQuery == null) {
            return null;
        }

        PreAggQueryRequirement requirement =
                requirementBuilder.buildFinalStage(
                        queryRequest, jdbcQuery, queryModel,
                        securityPredicates, securityContextCacheable);
        PreAggregationMatchResult matchResult = matcher.findBestMatch(requirement, configured);
        if (!matchResult.isMatched() || matchResult.isHybridQuery()) {
            return null;
        }

        PreAggQueryRewriter rewriter = new PreAggQueryRewriter(queryModel, applicationContext);
        PreAggQueryRewriter.PreAggAggregateSqlResult result = rewriter.buildFinalStageAggregateSql(
                matchResult.getPreAggregation(), jdbcQuery, queryRequest, matchResult);
        if (result != null) {
            log.info("Using pre-aggregation '{}' for equivalent final-stage aggregate query (returnTotal)",
                    result.getPreAggName());
        }
        return result;
    }

    /**
     * 构建聚合查询的需求
     * <p>
     * 聚合查询只需要度量（不需要维度分组），但仍需要检查：
     * <ul>
     *   <li>度量是否在预聚合中</li>
     *   <li>WHERE 条件涉及的列是否在预聚合中</li>
     * </ul>
     * </p>
     */
    private PreAggQueryRequirement buildAggregateRequirement(DbQueryRequestDef queryRequest,
                                                              JdbcQuery jdbcQuery,
                                                              JdbcQueryModel queryModel) {
        return requirementBuilder.buildAggregate(
                queryRequest, jdbcQuery, queryModel,
                securityPredicates, securityContextCacheable);
    }
}

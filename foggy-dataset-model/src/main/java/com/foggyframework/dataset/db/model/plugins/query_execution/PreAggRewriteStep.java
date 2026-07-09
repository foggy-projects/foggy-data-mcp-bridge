package com.foggyframework.dataset.db.model.plugins.query_execution;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.preagg.PreAggQueryRewriter;
import com.foggyframework.dataset.db.model.engine.preagg.PreAggRewriteResult;
import com.foggyframework.dataset.db.model.engine.preagg.PreAggregationInterceptor;
import com.foggyframework.dataset.db.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 预聚合重写步骤
 * <p>
 * 在 SQL 执行前检查是否可以使用预聚合表，并重写 SQL。
 * </p>
 * <p>
 * 支持两种优化模式：
 * <ul>
 *   <li>主查询预聚合：当查询有 GROUP BY 且匹配预聚合时，重写主查询 SQL</li>
 *   <li>聚合查询预聚合：当 returnTotal=true 时，即使主查询是明细查询，
 *       聚合查询（COUNT/SUM）也可以使用预聚合表加速</li>
 * </ul>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Component
public class PreAggRewriteStep implements QueryExecutionStep {

    private final ApplicationContext applicationContext;

    public PreAggRewriteStep(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public int order() {
        // 预聚合重写应该在 L2 缓存检查之前执行
        // 因为重写后的 SQL 才是最终要缓存的 SQL
        return 1000;
    }

    @Override
    public boolean supports(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        return phase == QueryExecutionPhase.NORMAL_QUERY || phase == QueryExecutionPhase.PREPARE_MANAGED_RELATION;
    }

    @Override
    public int beforeExecute(QueryExecutionContext ctx) {
        // 检查是否禁用预聚合
        if (isPreAggDisabled(ctx)) {
            log.debug("Pre-aggregation disabled for model={}", ctx.getModelName());
            return CONTINUE;
        }

        JdbcModelQueryEngine queryEngine = ctx.getQueryEngine();
        if (queryEngine == null) {
            return CONTINUE;
        }

        // 获取查询模型
        JdbcQueryModel queryModel = queryEngine.getJdbcQueryModel();
        if (queryModel == null) {
            return CONTINUE;
        }

        // 获取查询请求
        DbQueryRequestDef queryRequest = null;
        if (ctx.getModelResultContext() != null
                && ctx.getModelResultContext().getRequest() != null) {
            queryRequest = ctx.getModelResultContext().getRequest().getParam();
        }

        // 尝试预聚合重写（主查询）
        PreAggregationInterceptor interceptor = createInterceptor(ctx);
        StagePlanPreAggPolicy stagePlanPolicy = stagePlanPreAggPolicy(ctx);
        boolean skipMainPreAggForStagePlan = stagePlanPolicy.restrictsMainRewrite();
        PreAggRewriteResult preAggResult = PreAggRewriteResult.notApplied();

        if (skipMainPreAggForStagePlan) {
            markMainPreAggSkippedByStagePlan(ctx, stagePlanPolicy.policy);
        } else {
            preAggResult = tryPreAggregation(interceptor, queryEngine, queryModel, queryRequest);
        }

        if (preAggResult.isApplied()) {
            // 使用重写后的 SQL
            ctx.setSql(preAggResult.getSql());
            ctx.setParams(preAggResult.getParams());

            // 记录预聚合使用信息到 extData
            ctx.setExtData("preAggUsed", preAggResult.getPreAggName());
            ctx.setExtData("preAggNeedsRollup", preAggResult.isNeedsRollup());
            ctx.setExtData("preAggMode", preAggResult.isHybridQuery() ? "hybrid" :
                    (preAggResult.isNeedsRollup() ? "rollup" : "direct"));

            // 同步到 ModelResultContext
            if (ctx.getModelResultContext() != null) {
                ctx.getModelResultContext().getExtData().put("preAggUsed", preAggResult.getPreAggName());
                ctx.getModelResultContext().getExtData().put("preAggNeedsRollup", preAggResult.isNeedsRollup());

                // 更新 cacheConfig 中的预聚合命中信息
                ModelResultContext.QueryCacheConfig cacheConfig = getOrCreateCacheConfig(ctx.getModelResultContext());
                cacheConfig.setPreAggHit(true);
                cacheConfig.setPreAggName(preAggResult.getPreAggName());
            }

            log.info("Query using pre-aggregation '{}' (mode={})",
                    preAggResult.getPreAggName(),
                    preAggResult.isHybridQuery() ? "hybrid" :
                            (preAggResult.isNeedsRollup() ? "rollup" : "direct"));
        }

        // 无论主查询是否使用预聚合，都尝试为聚合查询（returnTotal）设置预聚合 SQL
        // 这样可以确保聚合查询也能受益于预聚合优化
        if (queryRequest != null && queryRequest.isReturnTotal()) {
            if (stagePlanPolicy.allowsEquivalentFinalCount()) {
                tryEquivalentFinalStageAggregatePreAggregation(ctx, interceptor, queryEngine, queryModel, queryRequest);
            } else if (stagePlanPolicy.skipsAllPreAgg()) {
                markAggregatePreAggSkippedByStagePlan(ctx, stagePlanPolicy.policy);
            } else {
                tryAggregatePreAggregation(ctx, interceptor, queryEngine, queryModel, queryRequest);
            }
        }

        return CONTINUE;
    }

    @SuppressWarnings("unchecked")
    private StagePlanPreAggPolicy stagePlanPreAggPolicy(QueryExecutionContext ctx) {
        ModelResultContext modelContext = ctx.getModelResultContext();
        if (modelContext == null || modelContext.getExtData() == null) {
            return StagePlanPreAggPolicy.optimizerAllowed();
        }
        Object rawPlan = modelContext.getExtData().get(QueryStagePlan.EXT_DATA_KEY);
        if (!(rawPlan instanceof Map<?, ?> plan)) {
            return StagePlanPreAggPolicy.optimizerAllowed();
        }
        Object preAggPolicy = plan.get("preAggOptimizationPolicy");
        Object aggSqlPolicy = plan.get("aggSqlOptimizationPolicy");
        if ("return-total-equivalent-only".equals(preAggPolicy)) {
            return new StagePlanPreAggPolicy("return-total-equivalent-only");
        }
        if ("skip-final-stage-required".equals(preAggPolicy)
                || "preserve-final-stage-sql".equals(aggSqlPolicy)) {
            return new StagePlanPreAggPolicy(
                    preAggPolicy != null ? String.valueOf(preAggPolicy) : String.valueOf(aggSqlPolicy)
            );
        }
        return StagePlanPreAggPolicy.optimizerAllowed();
    }

    private void markMainPreAggSkippedByStagePlan(QueryExecutionContext ctx, String reason) {
        ctx.setExtData("preAggOptimizationPolicy", reason);
        ctx.setExtData("preAggSkippedByStagePlan", true);
        ctx.setExtData("preAggMainSkippedByStagePlan", true);
        ctx.setExtData("preAggSkipReason", reason);

        ModelResultContext modelContext = ctx.getModelResultContext();
        if (modelContext != null && modelContext.getExtData() != null) {
            modelContext.getExtData().put("preAggOptimizationPolicy", reason);
            modelContext.getExtData().put("preAggSkippedByStagePlan", true);
            modelContext.getExtData().put("preAggMainSkippedByStagePlan", true);
            modelContext.getExtData().put("preAggSkipReason", reason);
        }
        log.debug("Pre-aggregation skipped for model={} because stage plan policy={}",
                ctx.getModelName(), reason);
    }

    private void markAggregatePreAggSkippedByStagePlan(QueryExecutionContext ctx, String reason) {
        ctx.setExtData("preAggAggregateSkippedByStagePlan", true);
        ctx.setExtData("preAggAggregateSkipReason", reason);

        ModelResultContext modelContext = ctx.getModelResultContext();
        if (modelContext != null && modelContext.getExtData() != null) {
            modelContext.getExtData().put("preAggAggregateSkippedByStagePlan", true);
            modelContext.getExtData().put("preAggAggregateSkipReason", reason);
        }
    }

    /**
     * 创建预聚合拦截器
     */
    private PreAggregationInterceptor createInterceptor(QueryExecutionContext ctx) {
        PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);
        boolean hybridEnabled = isHybridQueryEnabled(ctx);
        interceptor.setHybridQueryEnabled(hybridEnabled);
        return interceptor;
    }

    /**
     * 尝试为聚合查询（returnTotal）使用预聚合
     * <p>
     * 无论主查询是否使用预聚合，聚合查询都可以独立使用预聚合表优化。
     * 支持混合模式（水位线 + 新鲜数据 UNION）。
     * </p>
     */
    private void tryAggregatePreAggregation(QueryExecutionContext ctx,
                                             PreAggregationInterceptor interceptor,
                                             JdbcModelQueryEngine queryEngine,
                                             JdbcQueryModel queryModel,
                                             DbQueryRequestDef queryRequest) {
        try {
            PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                    interceptor.tryBuildAggregateSql(queryEngine, queryModel, queryRequest);

            if (aggResult != null) {
                // 存储聚合查询预聚合 SQL
                ctx.setPreAggAggregateSql(aggResult.getSql());
                ctx.setPreAggAggregateParams(aggResult.getParams());
                ctx.setPreAggAggregatePreAggName(aggResult.getPreAggName());

                // 记录到 extData
                ctx.setExtData("preAggAggregateUsed", aggResult.getPreAggName());
                ctx.setExtData("preAggAggregateHybrid", aggResult.isHybrid());

                if (aggResult.isHybrid()) {
                    log.info("Aggregate query (returnTotal) using pre-aggregation '{}' (HYBRID mode, watermark={})",
                            aggResult.getPreAggName(), aggResult.getWatermark());
                } else {
                    log.info("Aggregate query (returnTotal) using pre-aggregation '{}'", aggResult.getPreAggName());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to build aggregate SQL using pre-aggregation: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Aggregate pre-aggregation error details", e);
            }
        }
    }

    private void tryEquivalentFinalStageAggregatePreAggregation(QueryExecutionContext ctx,
                                                                PreAggregationInterceptor interceptor,
                                                                JdbcModelQueryEngine queryEngine,
                                                                JdbcQueryModel queryModel,
                                                                DbQueryRequestDef queryRequest) {
        try {
            PreAggQueryRewriter.PreAggAggregateSqlResult aggResult =
                    interceptor.tryBuildFinalStageAggregateSql(queryEngine, queryModel, queryRequest);
            if (aggResult == null) {
                markAggregatePreAggSkippedByStagePlan(ctx, "return-total-equivalent-not-matched");
                return;
            }
            ctx.setPreAggAggregateSql(aggResult.getSql());
            ctx.setPreAggAggregateParams(aggResult.getParams());
            ctx.setPreAggAggregatePreAggName(aggResult.getPreAggName());

            ctx.setExtData("preAggAggregateUsed", aggResult.getPreAggName());
            ctx.setExtData("preAggAggregateHybrid", aggResult.isHybrid());
            ctx.setExtData("preAggAggregateMode", "final-stage-equivalent");

            if (ctx.getModelResultContext() != null && ctx.getModelResultContext().getExtData() != null) {
                ctx.getModelResultContext().getExtData().put("preAggAggregateUsed", aggResult.getPreAggName());
                ctx.getModelResultContext().getExtData().put("preAggAggregateHybrid", aggResult.isHybrid());
                ctx.getModelResultContext().getExtData().put("preAggAggregateMode", "final-stage-equivalent");
            }
        } catch (PreAggQueryRewriter.PredicateNotProvableException e) {
            markAggregatePreAggSkippedByStagePlan(ctx, e.getReason());
            ctx.setExtData("preAggAggregateSkipDetail", e.getDetail());
            if (ctx.getModelResultContext() != null && ctx.getModelResultContext().getExtData() != null) {
                ctx.getModelResultContext().getExtData().put("preAggAggregateSkipDetail", e.getDetail());
            }
            log.debug("Equivalent final-stage aggregate pre-aggregation skipped: {}", e.getDetail());
        } catch (Exception e) {
            log.warn("Failed to build equivalent final-stage aggregate SQL using pre-aggregation: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Equivalent final-stage aggregate pre-aggregation error details", e);
            }
            markAggregatePreAggSkippedByStagePlan(ctx, "return-total-equivalent-error");
        }
    }

    private record StagePlanPreAggPolicy(String policy) {
        static StagePlanPreAggPolicy optimizerAllowed() {
            return new StagePlanPreAggPolicy("optimizer-allowed");
        }

        boolean restrictsMainRewrite() {
            return !"optimizer-allowed".equals(policy);
        }

        boolean allowsEquivalentFinalCount() {
            return "return-total-equivalent-only".equals(policy);
        }

        boolean skipsAllPreAgg() {
            return "skip-final-stage-required".equals(policy)
                    || "preserve-final-stage-sql".equals(policy);
        }
    }

    /**
     * 检查预聚合是否被禁用
     */
    private boolean isPreAggDisabled(QueryExecutionContext ctx) {
        if (ctx.getModelResultContext() == null) {
            return false;
        }
        ModelResultContext.QueryCacheConfig cacheConfig = ctx.getModelResultContext().getCacheConfig();
        if (cacheConfig == null) {
            return false;
        }
        return !cacheConfig.isPreAggEnabled();
    }

    /**
     * 获取或创建缓存配置
     */
    private ModelResultContext.QueryCacheConfig getOrCreateCacheConfig(ModelResultContext context) {
        ModelResultContext.QueryCacheConfig cacheConfig = context.getCacheConfig();
        if (cacheConfig == null) {
            cacheConfig = ModelResultContext.QueryCacheConfig.defaultConfig();
            context.setCacheConfig(cacheConfig);
        }
        return cacheConfig;
    }

    /**
     * 尝试使用预聚合重写查询
     */
    private PreAggRewriteResult tryPreAggregation(PreAggregationInterceptor interceptor,
                                                   JdbcModelQueryEngine queryEngine,
                                                   JdbcQueryModel queryModel,
                                                   DbQueryRequestDef queryRequest) {
        try {
            return interceptor.tryRewrite(queryEngine, queryModel, queryRequest);
        } catch (Exception e) {
            log.warn("Pre-aggregation interception failed, falling back to original query: {}",
                    e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("Pre-aggregation error details", e);
            }
            return PreAggRewriteResult.notApplied();
        }
    }

    /**
     * 检查混合查询是否启用
     */
    private boolean isHybridQueryEnabled(QueryExecutionContext ctx) {
        if (ctx.getModelResultContext() == null) {
            return false;  // 默认禁用混合查询
        }
        ModelResultContext.QueryCacheConfig cacheConfig = ctx.getModelResultContext().getCacheConfig();
        if (cacheConfig == null) {
            return false;
        }
        return cacheConfig.isHybridQueryEnabled();
    }
}

package com.foggyframework.dataset.db.model.plugins.query_execution;

import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.db.model.engine.preagg.PreAggRewriteResult;
import com.foggyframework.dataset.db.model.engine.preagg.PreAggregationInterceptor;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 预聚合重写步骤
 * <p>
 * 在 SQL 执行前检查是否可以使用预聚合表，并重写 SQL。
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

        // 尝试预聚合重写
        PreAggRewriteResult preAggResult = tryPreAggregation(ctx, queryEngine, queryModel, queryRequest);

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

        return CONTINUE;
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
    private PreAggRewriteResult tryPreAggregation(QueryExecutionContext ctx,
                                                   JdbcModelQueryEngine queryEngine,
                                                   JdbcQueryModel queryModel,
                                                   DbQueryRequestDef queryRequest) {
        try {
            PreAggregationInterceptor interceptor = new PreAggregationInterceptor(applicationContext);

            // 读取混合查询配置
            boolean hybridEnabled = isHybridQueryEnabled(ctx);
            interceptor.setHybridQueryEnabled(hybridEnabled);

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

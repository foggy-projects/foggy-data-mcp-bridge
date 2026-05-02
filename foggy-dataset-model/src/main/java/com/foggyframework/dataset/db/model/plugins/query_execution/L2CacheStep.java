package com.foggyframework.dataset.db.model.plugins.query_execution;

import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.NoOpQueryCacheProvider;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * L2 缓存步骤
 * <p>
 * 在 SQL 执行前检查 L2 缓存（SQL 级别），命中时跳过执行。
 * 在 SQL 执行后写入 L2 缓存。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Component
public class L2CacheStep implements QueryExecutionStep {

    private QueryCacheProvider queryCacheProvider = NoOpQueryCacheProvider.INSTANCE;

    @Autowired(required = false)
    public void setQueryCacheProvider(QueryCacheProvider queryCacheProvider) {
        if (queryCacheProvider != null) {
            this.queryCacheProvider = queryCacheProvider;
            log.info("L2CacheStep: QueryCacheProvider initialized: {}",
                    queryCacheProvider.getClass().getSimpleName());
        }
    }

    @Override
    public int order() {
        // L2 缓存检查在预聚合重写之后
        // 这样缓存的是重写后的 SQL
        return 900;
    }

    @Override
    public boolean supports(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        // Prepare 阶段也可以读缓存（虽不短路但可记录命中状态），但不建议在此阶段执行 afterExecute（不写缓存）
        return phase == QueryExecutionPhase.NORMAL_QUERY || phase == QueryExecutionPhase.PREPARE_MANAGED_RELATION;
    }

    @Override
    public int beforeExecute(QueryExecutionContext ctx) {
        ModelResultContext modelCtx = ctx.getModelResultContext();

        // 检查是否启用 L2 缓存
        boolean l2Enabled = QueryCacheProvider.isL2Enabled(modelCtx);
        if (!l2Enabled) {
            return CONTINUE;
        }

        // 获取缓存提供者（优先从 context 获取，兼容旧逻辑）
        QueryCacheProvider cacheProvider = getCacheProvider(modelCtx);
        if (cacheProvider == null) {
            return CONTINUE;
        }

        String modelName = ctx.getModelName();
        String pagingSql = ctx.getPagingSql();
        List<?> params = ctx.getParams();

        // 检查 L2 缓存
        PagingResultImpl cached = cacheProvider.checkL2Cache(modelName, pagingSql, params, modelCtx);
        if (cached != null) {
            if (log.isDebugEnabled()) {
                log.debug("L2 cache HIT for model={}", modelName);
            }

            // 标记缓存命中
            QueryCacheProvider.markL2Hit(modelCtx);

            // 检查是否在 prepare 阶段禁用了短路
            if (ctx.getManagedRelationOptions() != null && ctx.getManagedRelationOptions().isDisableInnerCacheShortCircuit()) {
                if (log.isDebugEnabled()) {
                    log.debug("L2 cache short-circuit is disabled by ManagedRelationOptions for model={}", modelName);
                }
            } else {
                // 设置缓存结果并跳过执行
                ctx.setCachedResult(cached);
                ctx.setSkipExecution(true);
            }

            return CONTINUE;
        } else {
            if (log.isDebugEnabled()) {
                log.debug("L2 cache MISS for model={}", modelName);
            }
        }

        return CONTINUE;
    }

    @Override
    public int afterExecute(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        if (phase == QueryExecutionPhase.PREPARE_MANAGED_RELATION) {
            return CONTINUE;
        }

        ModelResultContext modelCtx = ctx.getModelResultContext();

        // 检查是否启用 L2 缓存
        boolean l2Enabled = QueryCacheProvider.isL2Enabled(modelCtx);
        if (!l2Enabled) {
            return CONTINUE;
        }

        // 如果是缓存命中，不需要写入
        if (ctx.isSkipExecution()) {
            return CONTINUE;
        }

        // 获取缓存提供者
        QueryCacheProvider cacheProvider = getCacheProvider(modelCtx);
        if (cacheProvider == null) {
            return CONTINUE;
        }

        // 写入 L2 缓存
        PagingResultImpl result = ctx.getExecutionResult();
        if (result != null) {
            String modelName = ctx.getModelName();
            String pagingSql = ctx.getPagingSql();
            List<?> params = ctx.getParams();

            cacheProvider.writeL2Cache(modelName, pagingSql, params, result, modelCtx);

            if (log.isDebugEnabled()) {
                log.debug("L2 cache WRITE for model={}", modelName);
            }
        }

        return CONTINUE;
    }

    /**
     * 获取缓存提供者
     * <p>
     * 优先从 ModelResultContext.cacheConfig 获取（新逻辑），
     * 兜底使用注入的 queryCacheProvider。
     * </p>
     */
    private QueryCacheProvider getCacheProvider(ModelResultContext modelCtx) {
        if (modelCtx != null && modelCtx.getCacheConfig() != null
                && modelCtx.getCacheConfig().getProvider() != null) {
            return modelCtx.getCacheConfig().getProvider();
        }
        return queryCacheProvider;
    }
}

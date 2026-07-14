package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.dataset.db.model.engine.query.DbQueryResult;
import com.foggyframework.dataset.db.model.spi.NoOpQueryCacheProvider;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.db.model.plugins.cache.CacheResultSnapshot;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * L1 缓存步骤
 * <p>
 * 在 beforeQuery 阶段检查 L1 缓存（Token 级别），命中时跳过后续查询。
 * 在 process 阶段写入 L1 缓存（未命中时）。
 * </p>
 * <p>
 * 查询前 lookup 在所有请求改写和权限步骤之后执行；结果阶段 write 在所有后处理之前执行。
 * 缓存边界使用结构快照，避免本地缓存持有同一可变结果引用而被后续处理污染。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@Component
public class L1CacheStep implements DataSetResultStep {

    /**
     * 查询缓存提供者
     * <p>
     * 默认使用 NoOpQueryCacheProvider，引入 cache 模块后会自动注入具体实现。
     * </p>
     */
    private QueryCacheProvider queryCacheProvider = NoOpQueryCacheProvider.INSTANCE;

    @Autowired(required = false)
    public void setQueryCacheProvider(QueryCacheProvider queryCacheProvider) {
        if (queryCacheProvider != null) {
            this.queryCacheProvider = queryCacheProvider;
            log.info("L1CacheStep: QueryCacheProvider initialized: {}",
                    queryCacheProvider.getClass().getSimpleName());
        }
    }

    @Override
    public int beforeQueryOrder() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int processOrder() {
        return Integer.MIN_VALUE;
    }

    @Override
    public int beforeQuery(ModelResultContext ctx) {
        // 1. 初始化缓存配置
        if (ctx.getCacheConfig() == null) {
            ctx.setCacheConfig(ModelResultContext.QueryCacheConfig.defaultConfig());
        }

        // 2. 注入缓存提供者到配置中（供 QueryModel 使用 L2 缓存）
        ctx.getCacheConfig().setProvider(queryCacheProvider);

        // 3. 检查是否启用 L1 缓存
        boolean l1Enabled = QueryCacheProvider.isL1Enabled(ctx);
        if (!l1Enabled) {
            return CONTINUE;
        }

        // 4. 获取授权令牌
        String authorization = ctx.getAuthorization();
        if (authorization == null) {
            return CONTINUE;
        }

        // 5. 检查 L1 缓存
        PagingResultImpl cached = safeSnapshot(
                queryCacheProvider.checkL1Cache(ctx, authorization), "read");
        if (cached != null) {
            // 缓存命中
            if (log.isDebugEnabled()) {
                String modelName = ctx.getRequest() != null && ctx.getRequest().getParam() != null
                        ? ctx.getRequest().getParam().getQueryModel()
                        : "unknown";
                log.debug("L1 cache HIT for model={}", modelName);
            }

            // 标记缓存命中
            QueryCacheProvider.markL1Hit(ctx);

            // 设置结果并标记跳过查询
            ctx.setPagingResult(cached);
            ctx.setQueryResult(DbQueryResult.of(cached, null));
            ctx.setSkipQuery(true);

            return CONTINUE;
        } else {
            if (log.isDebugEnabled()) {
                String modelName = ctx.getRequest() != null && ctx.getRequest().getParam() != null
                        ? ctx.getRequest().getParam().getQueryModel()
                        : "unknown";
                log.debug("L1 cache MISS for model={}", modelName);
            }
        }

        return CONTINUE;
    }

    @Override
    public int process(ModelResultContext ctx) {
        // 检查是否需要写入 L1 缓存
        // 条件：L1 启用 + 有授权 + 未命中 + 有结果
        boolean l1Enabled = QueryCacheProvider.isL1Enabled(ctx);
        if (!l1Enabled) {
            return CONTINUE;
        }

        String authorization = ctx.getAuthorization();
        if (authorization == null) {
            return CONTINUE;
        }

        // 检查是否已命中（命中则不需要写入）
        ModelResultContext.QueryCacheConfig cacheConfig = ctx.getCacheConfig();
        if (cacheConfig != null && cacheConfig.isL1CacheHit()) {
            return CONTINUE;
        }

        // 写入 L1 缓存
        PagingResultImpl result = ctx.getPagingResult();
        if (result != null) {
            PagingResultImpl snapshot = safeSnapshot(result, "write");
            if (snapshot == null) {
                return CONTINUE;
            }
            queryCacheProvider.writeL1Cache(ctx, authorization, snapshot);

            if (log.isDebugEnabled()) {
                String modelName = ctx.getRequest() != null && ctx.getRequest().getParam() != null
                        ? ctx.getRequest().getParam().getQueryModel()
                        : "unknown";
                log.debug("L1 cache WRITE for model={}", modelName);
            }
        }

        return CONTINUE;
    }

    private PagingResultImpl safeSnapshot(PagingResultImpl source, String operation) {
        try {
            return CacheResultSnapshot.copy(source);
        } catch (CacheResultSnapshot.UnsafeCacheValueException e) {
            log.warn("Skip L1 cache {} because the result cannot be isolated: {}",
                    operation, e.getMessage());
            return null;
        }
    }
}

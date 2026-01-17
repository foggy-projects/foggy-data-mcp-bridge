package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext.QueryCacheConfig;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext.SecurityContext;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryCacheProvider SPI 接口测试
 * <p>
 * 测试双层缓存的辅助方法和 QueryCacheConfig 使用。
 * </p>
 */
@DisplayName("QueryCacheProvider SPI 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryCacheProviderTest {

    @Test
    @Order(1)
    @DisplayName("isL1Enabled - 未设置 cacheConfig 时返回 false")
    void testIsL1Enabled_NoCacheConfig_ReturnsFalse() {
        ModelResultContext context = createContext();

        boolean enabled = QueryCacheProvider.isL1Enabled(context);

        assertFalse(enabled, "L1 缓存默认应该禁用");
    }

    @Test
    @Order(2)
    @DisplayName("isL1Enabled - cacheConfig.l1Enabled=true 时返回 true")
    void testIsL1Enabled_SetTrue_ReturnsTrue() {
        ModelResultContext context = createContext();
        context.setCacheConfig(QueryCacheConfig.builder().l1Enabled(true).build());

        boolean enabled = QueryCacheProvider.isL1Enabled(context);

        assertTrue(enabled, "L1 缓存应该启用");
    }

    @Test
    @Order(3)
    @DisplayName("isL1Enabled - cacheConfig.l1Enabled=false 时返回 false")
    void testIsL1Enabled_SetFalse_ReturnsFalse() {
        ModelResultContext context = createContext();
        context.setCacheConfig(QueryCacheConfig.builder().l1Enabled(false).build());

        boolean enabled = QueryCacheProvider.isL1Enabled(context);

        assertFalse(enabled, "L1 缓存应该禁用");
    }

    @Test
    @Order(4)
    @DisplayName("isL1Enabled - context 为 null 时返回 false")
    void testIsL1Enabled_NullContext_ReturnsFalse() {
        boolean enabled = QueryCacheProvider.isL1Enabled(null);

        assertFalse(enabled, "null context 应该返回 false");
    }

    @Test
    @Order(5)
    @DisplayName("isL2Enabled - 未设置 cacheConfig 时返回 true（默认启用）")
    void testIsL2Enabled_NoCacheConfig_ReturnsTrue() {
        ModelResultContext context = createContext();

        boolean enabled = QueryCacheProvider.isL2Enabled(context);

        assertTrue(enabled, "L2 缓存默认应该启用");
    }

    @Test
    @Order(6)
    @DisplayName("isL2Enabled - cacheConfig.l2Enabled=false 时返回 false")
    void testIsL2Enabled_SetFalse_ReturnsFalse() {
        ModelResultContext context = createContext();
        context.setCacheConfig(QueryCacheConfig.builder().l2Enabled(false).build());

        boolean enabled = QueryCacheProvider.isL2Enabled(context);

        assertFalse(enabled, "L2 缓存应该禁用");
    }

    @Test
    @Order(7)
    @DisplayName("isL2Enabled - cacheConfig.l2Enabled=true 时返回 true")
    void testIsL2Enabled_SetTrue_ReturnsTrue() {
        ModelResultContext context = createContext();
        context.setCacheConfig(QueryCacheConfig.builder().l2Enabled(true).build());

        boolean enabled = QueryCacheProvider.isL2Enabled(context);

        assertTrue(enabled, "L2 缓存应该启用");
    }

    @Test
    @Order(8)
    @DisplayName("isL2Enabled - context 为 null 时返回 true（默认启用）")
    void testIsL2Enabled_NullContext_ReturnsTrue() {
        boolean enabled = QueryCacheProvider.isL2Enabled(null);

        assertTrue(enabled, "null context 应该返回 true（默认启用）");
    }

    @Test
    @Order(9)
    @DisplayName("getAuthorization - 从 SecurityContext 获取")
    void testGetAuthorization_FromSecurityContext() {
        ModelResultContext context = createContext();
        context.setSecurityContext(SecurityContext.builder()
                .authorization("Bearer token123")
                .build());

        String auth = QueryCacheProvider.getAuthorization(context);

        assertEquals("Bearer token123", auth);
    }

    @Test
    @Order(10)
    @DisplayName("getAuthorization - context 为 null 时返回 null")
    void testGetAuthorization_NullContext_ReturnsNull() {
        String auth = QueryCacheProvider.getAuthorization(null);

        assertNull(auth);
    }

    @Test
    @Order(11)
    @DisplayName("getAuthorization - 未设置 SecurityContext 时返回 null")
    void testGetAuthorization_NotSet_ReturnsNull() {
        ModelResultContext context = createContext();

        String auth = QueryCacheProvider.getAuthorization(context);

        assertNull(auth);
    }

    @Test
    @Order(12)
    @DisplayName("NoOpQueryCacheProvider - 所有方法返回空")
    void testNoOpQueryCacheProvider() {
        QueryCacheProvider provider = NoOpQueryCacheProvider.INSTANCE;
        ModelResultContext context = createContext();

        // L1 缓存检查返回 null
        PagingResultImpl l1Result = provider.checkL1Cache(context, "token");
        assertNull(l1Result);

        // L2 缓存检查返回 null
        PagingResultImpl l2Result = provider.checkL2Cache("model", "sql", Collections.emptyList(), context);
        assertNull(l2Result);

        // 写入方法不抛异常
        assertDoesNotThrow(() -> provider.writeL1Cache(context, "token", null));
        assertDoesNotThrow(() -> provider.writeL2Cache("model", "sql", Collections.emptyList(), null, context));

        // evict 方法不抛异常
        assertDoesNotThrow(() -> provider.evict("model"));
        assertDoesNotThrow(() -> provider.evictAll());

        // getStats 返回空 Map
        Map<String, Object> stats = provider.getStats();
        assertTrue(stats.isEmpty());

        // getOrder 返回 Integer.MIN_VALUE（最低优先级）
        assertEquals(Integer.MIN_VALUE, provider.getOrder());
    }

    @Test
    @Order(13)
    @DisplayName("NoOpQueryCacheProvider - 单例模式")
    void testNoOpQueryCacheProvider_Singleton() {
        QueryCacheProvider instance1 = NoOpQueryCacheProvider.INSTANCE;
        QueryCacheProvider instance2 = NoOpQueryCacheProvider.INSTANCE;

        assertSame(instance1, instance2, "应该是同一个实例");
    }

    @Test
    @Order(14)
    @DisplayName("QueryCacheConfig - 静态工厂方法测试")
    void testQueryCacheConfigFactoryMethods() {
        // enableL1() 返回 L1 和 L2 都启用
        QueryCacheConfig l1Config = QueryCacheConfig.enableL1();
        assertTrue(l1Config.isL1Enabled());
        assertTrue(l1Config.isL2Enabled());

        // disabled() 返回都禁用
        QueryCacheConfig disabledConfig = QueryCacheConfig.disabled();
        assertFalse(disabledConfig.isL1Enabled());
        assertFalse(disabledConfig.isL2Enabled());

        // defaultConfig() 返回仅 L2 启用
        QueryCacheConfig defaultConfig = QueryCacheConfig.defaultConfig();
        assertFalse(defaultConfig.isL1Enabled());
        assertTrue(defaultConfig.isL2Enabled());
    }

    @Test
    @Order(15)
    @DisplayName("getOrCreateConfig - 自动创建默认配置")
    void testGetOrCreateConfig_CreatesDefault() {
        ModelResultContext context = createContext();
        assertNull(context.getCacheConfig(), "初始应该为 null");

        QueryCacheConfig config = QueryCacheProvider.getOrCreateConfig(context);

        assertNotNull(config);
        assertFalse(config.isL1Enabled());
        assertTrue(config.isL2Enabled());
        assertSame(config, context.getCacheConfig(), "应该设置到 context 中");
    }

    @Test
    @Order(16)
    @DisplayName("getOrCreateConfig - 返回已存在的配置")
    void testGetOrCreateConfig_ReturnsExisting() {
        ModelResultContext context = createContext();
        QueryCacheConfig existingConfig = QueryCacheConfig.enableL1();
        context.setCacheConfig(existingConfig);

        QueryCacheConfig config = QueryCacheProvider.getOrCreateConfig(context);

        assertSame(existingConfig, config, "应该返回已存在的配置");
    }

    @Test
    @Order(17)
    @DisplayName("markL1Hit - 标记 L1 缓存命中")
    void testMarkL1Hit() {
        ModelResultContext context = createContext();

        QueryCacheProvider.markL1Hit(context);

        assertNotNull(context.getCacheConfig());
        assertTrue(context.getCacheConfig().isL1CacheHit());
    }

    @Test
    @Order(18)
    @DisplayName("markL2Hit - 标记 L2 缓存命中")
    void testMarkL2Hit() {
        ModelResultContext context = createContext();

        QueryCacheProvider.markL2Hit(context);

        assertNotNull(context.getCacheConfig());
        assertTrue(context.getCacheConfig().isL2CacheHit());
    }

    @Test
    @Order(19)
    @DisplayName("自定义 QueryCacheProvider 实现测试")
    void testCustomQueryCacheProvider() {
        // 创建自定义实现
        QueryCacheProvider customProvider = new QueryCacheProvider() {
            private final Map<String, PagingResultImpl> cache = new HashMap<>();

            @Override
            public PagingResultImpl checkL2Cache(String modelName, String sql, List<?> params, ModelResultContext context) {
                String key = modelName + ":" + sql;
                return cache.get(key);
            }

            @Override
            public void writeL2Cache(String modelName, String sql, List<?> params, PagingResultImpl result, ModelResultContext context) {
                String key = modelName + ":" + sql;
                cache.put(key, result);
            }

            @Override
            public int getOrder() {
                return 100;
            }
        };

        ModelResultContext context = createContext();
        PagingResultImpl testResult = PagingResultImpl.of(Collections.singletonList("data"), 0, 10, null, 1);

        // 初始缓存为空
        assertNull(customProvider.checkL2Cache("model1", "SELECT * FROM t", Collections.emptyList(), context));

        // 写入缓存
        customProvider.writeL2Cache("model1", "SELECT * FROM t", Collections.emptyList(), testResult, context);

        // 读取缓存
        PagingResultImpl cached = customProvider.checkL2Cache("model1", "SELECT * FROM t", Collections.emptyList(), context);
        assertNotNull(cached);
        assertEquals(1, cached.getItems().size());

        // 不同 key 返回 null
        assertNull(customProvider.checkL2Cache("model2", "SELECT * FROM t", Collections.emptyList(), context));

        // getOrder 返回自定义值
        assertEquals(100, customProvider.getOrder());
    }

    /**
     * 创建测试用的 ModelResultContext
     */
    private ModelResultContext createContext() {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);

        ModelResultContext ctx = new ModelResultContext(pagingRequest, null);
        return ctx;
    }
}

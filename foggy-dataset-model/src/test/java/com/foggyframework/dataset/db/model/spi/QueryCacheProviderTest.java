package com.foggyframework.dataset.db.model.spi;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
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
 * 测试双层缓存的辅助方法和上下文标记。
 * </p>
 */
@DisplayName("QueryCacheProvider SPI 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QueryCacheProviderTest {

    @Test
    @Order(1)
    @DisplayName("isL1Enabled - 未设置时返回 false")
    void testIsL1Enabled_NotSet_ReturnsFalse() {
        ModelResultContext context = createContext();

        boolean enabled = QueryCacheProvider.isL1Enabled(context);

        assertFalse(enabled, "L1 缓存默认应该禁用");
    }

    @Test
    @Order(2)
    @DisplayName("isL1Enabled - 设置为 true 时返回 true")
    void testIsL1Enabled_SetTrue_ReturnsTrue() {
        ModelResultContext context = createContext();
        context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L1_CACHE, true);

        boolean enabled = QueryCacheProvider.isL1Enabled(context);

        assertTrue(enabled, "L1 缓存应该启用");
    }

    @Test
    @Order(3)
    @DisplayName("isL1Enabled - 设置为 false 时返回 false")
    void testIsL1Enabled_SetFalse_ReturnsFalse() {
        ModelResultContext context = createContext();
        context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L1_CACHE, false);

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
    @DisplayName("isL2Enabled - 未设置时返回 true（默认启用）")
    void testIsL2Enabled_NotSet_ReturnsTrue() {
        ModelResultContext context = createContext();

        boolean enabled = QueryCacheProvider.isL2Enabled(context);

        assertTrue(enabled, "L2 缓存默认应该启用");
    }

    @Test
    @Order(6)
    @DisplayName("isL2Enabled - 设置为 false 时返回 false")
    void testIsL2Enabled_SetFalse_ReturnsFalse() {
        ModelResultContext context = createContext();
        context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L2_CACHE, false);

        boolean enabled = QueryCacheProvider.isL2Enabled(context);

        assertFalse(enabled, "L2 缓存应该禁用");
    }

    @Test
    @Order(7)
    @DisplayName("isL2Enabled - 设置为 true 时返回 true")
    void testIsL2Enabled_SetTrue_ReturnsTrue() {
        ModelResultContext context = createContext();
        context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L2_CACHE, true);

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
    @DisplayName("getAuthorization - 从 extData 获取")
    void testGetAuthorization_FromExtData() {
        ModelResultContext context = createContext();
        context.getExtData().put(QueryCacheProvider.EXT_AUTHORIZATION, "Bearer token123");

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
    @DisplayName("getAuthorization - 未设置时返回 null")
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

        // getOrder 返回 0
        assertEquals(0, provider.getOrder());
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
    @DisplayName("Context Keys 常量值正确")
    void testContextKeysConstants() {
        assertEquals("enableL1Cache", QueryCacheProvider.EXT_ENABLE_L1_CACHE);
        assertEquals("enableL2Cache", QueryCacheProvider.EXT_ENABLE_L2_CACHE);
        assertEquals("authorization", QueryCacheProvider.EXT_AUTHORIZATION);
        assertEquals("l1CacheHit", QueryCacheProvider.EXT_L1_CACHE_HIT);
        assertEquals("l2CacheHit", QueryCacheProvider.EXT_L2_CACHE_HIT);
    }

    @Test
    @Order(15)
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
        assertEquals(1, cached.getList().size());

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

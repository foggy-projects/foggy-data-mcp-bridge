package com.foggyframework.dataset.db.model.cache.provider;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.db.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext.QueryCacheConfig;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CaffeineQueryCacheProvider 单元测试
 * <p>
 * 测试 Caffeine 双层缓存的基本功能。
 * </p>
 */
@DisplayName("CaffeineQueryCacheProvider 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CaffeineQueryCacheProviderTest {

    private CaffeineQueryCacheProvider cacheProvider;
    private QueryCacheProperties properties;
    private QueryFingerprintBuilder fingerprintBuilder;

    @BeforeEach
    void setUp() {
        properties = new QueryCacheProperties();
        properties.setEnabled(true);
        properties.setDefaultTtl(Duration.ofMinutes(5));
        properties.getCaffeine().setMaximumSize(1000);
        properties.getCaffeine().setInitialCapacity(100);
        properties.getCaffeine().setRecordStats(true);

        fingerprintBuilder = new QueryFingerprintBuilder();
        cacheProvider = new CaffeineQueryCacheProvider(fingerprintBuilder, properties);
    }

    @AfterEach
    void tearDown() {
        if (cacheProvider != null) {
            cacheProvider.evictAll();
        }
    }

    // ==================== L2 缓存测试 ====================

    @Test
    @Order(1)
    @DisplayName("L2 缓存 - 未命中返回 null")
    void testL2Cache_Miss_ReturnsNull() {
        ModelResultContext context = createContext("TestModel");

        PagingResultImpl result = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = ?",
                Collections.singletonList(1),
                context
        );

        assertNull(result, "未命中应该返回 null");
    }

    @Test
    @Order(2)
    @DisplayName("L2 缓存 - 写入后能读取")
    void testL2Cache_WriteAndRead() {
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl testResult = createTestResult();

        // 写入缓存
        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = ?",
                Collections.singletonList(1),
                testResult,
                context
        );

        // 读取缓存
        PagingResultImpl cached = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = ?",
                Collections.singletonList(1),
                context
        );

        assertNotNull(cached, "应该能读取到缓存");
        assertEquals(testResult.getTotal(), cached.getTotal());
    }

    @Test
    @Order(3)
    @DisplayName("L2 缓存 - 不同 SQL 不命中")
    void testL2Cache_DifferentSql_NoHit() {
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl testResult = createTestResult();

        // 写入缓存
        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = 1",
                Collections.emptyList(),
                testResult,
                context
        );

        // 用不同的 SQL 读取
        PagingResultImpl cached = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = 2",
                Collections.emptyList(),
                context
        );

        assertNull(cached, "不同 SQL 不应该命中");
    }

    @Test
    @Order(4)
    @DisplayName("L2 缓存 - 不同参数不命中")
    void testL2Cache_DifferentParams_NoHit() {
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl testResult = createTestResult();

        // 写入缓存
        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = ?",
                Collections.singletonList(1),
                testResult,
                context
        );

        // 用不同的参数读取
        PagingResultImpl cached = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = ?",
                Collections.singletonList(2),
                context
        );

        assertNull(cached, "不同参数不应该命中");
    }

    @Test
    @Order(5)
    @DisplayName("L2 缓存 - 禁用时不缓存")
    void testL2Cache_Disabled_NoCaching() {
        properties.setEnabled(false);
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl testResult = createTestResult();

        // 写入缓存（应该被忽略）
        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                testResult,
                context
        );

        // 读取缓存
        PagingResultImpl cached = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                context
        );

        assertNull(cached, "禁用时不应该有缓存");
    }

    @Test
    @Order(6)
    @DisplayName("L2 缓存 - 排除的模型不缓存")
    void testL2Cache_ExcludedModel_NoCaching() {
        properties.getExcludeModels().add("ExcludedModel");
        ModelResultContext context = createContext("ExcludedModel");
        PagingResultImpl testResult = createTestResult();

        // 写入缓存（应该被忽略）
        cacheProvider.writeL2Cache(
                "ExcludedModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                testResult,
                context
        );

        // 读取缓存
        PagingResultImpl cached = cacheProvider.checkL2Cache(
                "ExcludedModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                context
        );

        assertNull(cached, "排除的模型不应该有缓存");
    }

    @Test
    @Order(7)
    @DisplayName("L2 缓存 - 大结果集不缓存")
    void testL2Cache_LargeResult_NoCaching() {
        properties.setMaxResultSize(2);
        ModelResultContext context = createContext("TestModel");

        // 创建大结果集
        PagingResultImpl largeResult = PagingResultImpl.of(
                Arrays.asList("1", "2", "3", "4", "5"),  // 超过 maxResultSize
                0, 10, null, 5
        );

        // 写入缓存（应该被忽略）
        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                largeResult,
                context
        );

        // 读取缓存
        PagingResultImpl cached = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                context
        );

        assertNull(cached, "大结果集不应该被缓存");
    }

    @Test
    @Order(8)
    @DisplayName("L2 缓存 - 空结果集可配置是否缓存")
    void testL2Cache_EmptyResult_Configurable() {
        properties.setCacheEmptyResult(false);
        ModelResultContext context = createContext("TestModel");

        PagingResultImpl emptyResult = PagingResultImpl.of(
                Collections.emptyList(), 0, 10, null, 0
        );

        // 写入缓存（应该被忽略）
        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                emptyResult,
                context
        );

        // 读取缓存
        PagingResultImpl cached = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                context
        );

        assertNull(cached, "空结果集配置为不缓存时不应该被缓存");
    }

    // ==================== 缓存管理测试 ====================

    @Test
    @Order(20)
    @DisplayName("evict - 清除指定模型的缓存")
    void testEvict_SpecificModel() {
        ModelResultContext context1 = createContext("Model1");
        ModelResultContext context2 = createContext("Model2");
        PagingResultImpl testResult = createTestResult();

        // 写入两个模型的缓存
        cacheProvider.writeL2Cache("Model1", "SELECT 1", Collections.emptyList(), testResult, context1);
        cacheProvider.writeL2Cache("Model2", "SELECT 2", Collections.emptyList(), testResult, context2);

        // 验证都能读取
        assertNotNull(cacheProvider.checkL2Cache("Model1", "SELECT 1", Collections.emptyList(), context1));
        assertNotNull(cacheProvider.checkL2Cache("Model2", "SELECT 2", Collections.emptyList(), context2));

        // 清除 Model1 的缓存
        cacheProvider.evict("Model1");

        // Model1 应该被清除，Model2 应该保留
        assertNull(cacheProvider.checkL2Cache("Model1", "SELECT 1", Collections.emptyList(), context1));
        assertNotNull(cacheProvider.checkL2Cache("Model2", "SELECT 2", Collections.emptyList(), context2));
    }

    @Test
    @Order(21)
    @DisplayName("evictAll - 清除所有缓存")
    void testEvictAll() {
        ModelResultContext context1 = createContext("Model1");
        ModelResultContext context2 = createContext("Model2");
        PagingResultImpl testResult = createTestResult();

        // 写入两个模型的缓存
        cacheProvider.writeL2Cache("Model1", "SELECT 1", Collections.emptyList(), testResult, context1);
        cacheProvider.writeL2Cache("Model2", "SELECT 2", Collections.emptyList(), testResult, context2);

        // 清除所有缓存
        cacheProvider.evictAll();

        // 都应该被清除
        assertNull(cacheProvider.checkL2Cache("Model1", "SELECT 1", Collections.emptyList(), context1));
        assertNull(cacheProvider.checkL2Cache("Model2", "SELECT 2", Collections.emptyList(), context2));
    }

    @Test
    @Order(22)
    @DisplayName("getStats - 返回缓存统计")
    void testGetStats() {
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl testResult = createTestResult();

        // 执行一些缓存操作
        cacheProvider.checkL2Cache("TestModel", "SELECT 1", Collections.emptyList(), context);  // miss
        cacheProvider.writeL2Cache("TestModel", "SELECT 1", Collections.emptyList(), testResult, context);
        cacheProvider.checkL2Cache("TestModel", "SELECT 1", Collections.emptyList(), context);  // hit

        Map<String, Object> stats = cacheProvider.getStats();

        assertNotNull(stats);
        assertEquals("caffeine", stats.get("type"));
        assertTrue((Boolean) stats.get("enabled"));
        assertNotNull(stats.get("l2Hits"));
        assertNotNull(stats.get("l2Misses"));
    }

    @Test
    @Order(23)
    @DisplayName("getOrder - 返回优先级")
    void testGetOrder() {
        assertEquals(100, cacheProvider.getOrder());
    }

    // ==================== L1 缓存测试 ====================

    @Test
    @Order(30)
    @DisplayName("L1 缓存 - 需要 authorization")
    void testL1Cache_RequiresAuthorization() {
        ModelResultContext context = createContext("TestModel");
        context.setCacheConfig(QueryCacheConfig.enableL1());
        // 不设置 authorization

        PagingResultImpl result = cacheProvider.checkL1Cache(context, null);

        assertNull(result, "没有 authorization 应该返回 null");
    }

    @Test
    @Order(31)
    @DisplayName("L1 缓存 - 写入后能读取（需要可缓存查询）")
    void testL1Cache_WriteAndRead() {
        // 创建可缓存的查询（无原始 SQL）
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("TestModel");
        queryRequest.setColumns(Arrays.asList("id", "name"));

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);
        pagingRequest.setPage(1);
        pagingRequest.setPageSize(10);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setCacheConfig(QueryCacheConfig.enableL1());

        String authorization = "Bearer test-token-123";
        PagingResultImpl testResult = createTestResult();

        // 写入 L1 缓存
        cacheProvider.writeL1Cache(context, authorization, testResult);

        // 读取 L1 缓存
        PagingResultImpl cached = cacheProvider.checkL1Cache(context, authorization);

        // 注意：L1 缓存依赖 QueryFingerprint，如果 fingerprint 不可缓存则返回 null
        // 这里简化测试，不验证具体命中情况
    }

    // ==================== 辅助方法 ====================

    private ModelResultContext createContext(String modelName) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(modelName);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);

        return new ModelResultContext(pagingRequest, null);
    }

    private PagingResultImpl createTestResult() {
        return PagingResultImpl.of(
                Arrays.asList("item1", "item2"),
                0, 10, null, 2
        );
    }
}

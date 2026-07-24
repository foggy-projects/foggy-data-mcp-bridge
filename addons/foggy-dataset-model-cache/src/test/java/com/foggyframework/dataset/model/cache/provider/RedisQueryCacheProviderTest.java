package com.foggyframework.dataset.model.cache.provider;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.QueryCacheConfig;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisQueryCacheProvider 单元测试（使用 Mock）
 */
@DisplayName("RedisQueryCacheProvider 测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisQueryCacheProviderTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RedisQueryCacheProvider cacheProvider;
    private QueryCacheProperties properties;
    private QueryFingerprintBuilder fingerprintBuilder;

    @BeforeEach
    void setUp() {
        properties = new QueryCacheProperties();
        properties.setEnabled(true);
        properties.setDefaultTtl(Duration.ofMinutes(5));
        properties.setKeyPrefix("qc:");
        properties.getRedis().setTtlJitter(false);  // 禁用随机偏移以便测试

        fingerprintBuilder = new QueryFingerprintBuilder();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheProvider = new RedisQueryCacheProvider(redisTemplate, fingerprintBuilder, properties);
    }

    // ==================== L2 缓存测试 ====================

    @Test
    @Order(1)
    @DisplayName("L2 缓存 - 未命中返回 null")
    void testL2Cache_Miss_ReturnsNull() {
        ModelResultContext context = createContext("TestModel");

        when(valueOperations.get(anyString())).thenReturn(null);

        PagingResultImpl result = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = ?",
                Collections.singletonList(1),
                context
        );

        assertNull(result, "未命中应该返回 null");
        verify(valueOperations).get(anyString());
    }

    @Test
    @Order(2)
    @DisplayName("L2 缓存 - 命中返回缓存结果")
    void testL2Cache_Hit_ReturnsCachedResult() {
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl cachedResult = createTestResult();

        when(valueOperations.get(anyString())).thenReturn(cachedResult);

        PagingResultImpl result = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test WHERE id = ?",
                Collections.singletonList(1),
                context
        );

        assertNotNull(result, "命中应该返回缓存结果");
        assertEquals(cachedResult.getTotal(), result.getTotal());
    }

    @Test
    @Order(3)
    @DisplayName("L2 缓存 - 写入缓存")
    void testL2Cache_Write() {
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl testResult = createTestResult();
        properties.setMaxResultSize(0);

        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                testResult,
                context
        );

        verify(valueOperations).set(anyString(), eq(testResult), any(Duration.class));

        doThrow(new RuntimeException("Redis write failed"))
                .when(valueOperations).set(anyString(), eq(testResult), any(Duration.class));
        assertDoesNotThrow(() -> cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT write-failure",
                Collections.emptyList(),
                testResult,
                context
        ), "Redis 写异常必须降级而不是中断查询");
    }

    @Test
    @Order(4)
    @DisplayName("L2 缓存 - 禁用时不查询")
    void testL2Cache_Disabled_NoQuery() {
        properties.setEnabled(false);
        ModelResultContext context = createContext("TestModel");

        PagingResultImpl result = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                context
        );

        assertNull(result);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @Order(5)
    @DisplayName("L2 缓存 - 禁用时不写入")
    void testL2Cache_Disabled_NoWrite() {
        properties.setEnabled(false);
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl testResult = createTestResult();

        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                testResult,
                context
        );

        properties.setEnabled(true);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT null-result", Collections.emptyList(), null, context);

        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @Order(6)
    @DisplayName("L2 缓存 - 排除的模型不查询")
    void testL2Cache_ExcludedModel_NoQuery() {
        properties.getExcludeModels().add("ExcludedModel");
        ModelResultContext context = createContext("ExcludedModel");

        PagingResultImpl result = cacheProvider.checkL2Cache(
                "ExcludedModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                context
        );

        assertNull(result);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @Order(7)
    @DisplayName("L2 缓存 - 大结果集不写入")
    void testL2Cache_LargeResult_NoWrite() {
        properties.setMaxResultSize(2);
        ModelResultContext context = createContext("TestModel");

        PagingResultImpl largeResult = PagingResultImpl.of(
                Arrays.asList("1", "2", "3", "4", "5"),
                0, 10, null, 5
        );

        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                largeResult,
                context
        );

        properties.setCacheEmptyResult(false);
        PagingResultImpl nullItems = new PagingResultImpl();
        nullItems.setItems(null);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT null-items", Collections.emptyList(), nullItems, context);
        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT empty-items",
                Collections.emptyList(),
                PagingResultImpl.of(Collections.emptyList(), 0, 10, null, 0),
                context);

        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @Order(8)
    @DisplayName("L2 缓存 - Redis 异常时返回 null")
    void testL2Cache_RedisException_ReturnsNull() {
        ModelResultContext context = createContext("TestModel");

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection failed"));

        PagingResultImpl result = cacheProvider.checkL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                context
        );

        assertNull(result, "Redis 异常时应该返回 null");
    }

    @Test
    @Order(9)
    @DisplayName("L2 缓存 - 使用模型特定 TTL")
    void testL2Cache_ModelSpecificTtl() {
        properties.getModelTtl().put("SpecialModel", Duration.ofHours(1));
        ModelResultContext context = createContext("SpecialModel");
        PagingResultImpl testResult = createTestResult();

        cacheProvider.writeL2Cache(
                "SpecialModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                testResult,
                context
        );

        verify(valueOperations).set(anyString(), eq(testResult), eq(Duration.ofHours(1)));

        properties.getRedis().setTtlJitter(true);
        properties.getRedis().setTtlJitterMax(0);
        cacheProvider.writeL2Cache(
                "SpecialModel",
                "SELECT jittered",
                Collections.emptyList(),
                testResult,
                context
        );
        verify(valueOperations, times(2))
                .set(anyString(), eq(testResult), eq(Duration.ofHours(1)));
    }

    @Test
    @Order(10)
    @DisplayName("L2 缓存 - namespace 不同生成不同 Redis key")
    void testL2Cache_DifferentNamespace_UsesDifferentKeys() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext tenantA = createContext("TestModel", queryModel, "tenant-a");
        ModelResultContext tenantB = createContext("TestModel", queryModel, "tenant-b");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), tenantA);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), tenantB);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2)).set(keys.capture(), any(), any(Duration.class));
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
    }

    @Test
    @Order(11)
    @DisplayName("L2 缓存 - 安全策略和 catalog generation 变化生成不同 Redis key")
    void testL2Cache_SecurityAndCatalogGeneration_AreIsolated() {
        JdbcQueryModel firstModel = createResolvedModel("TestModel");
        ModelResultContext base = createContext("TestModel", firstModel, "tenant-a");
        ModelResultContext restricted = createContext("TestModel", firstModel, "tenant-a");
        restricted.setFieldAccess(Collections.singleton("id"));
        ModelResultContext refreshed = createContext(
                "TestModel", createResolvedModel("TestModel"), "tenant-a",
                "catalog:tenant-a:2", "binding:tenant-a:1", true);

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), base);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), restricted);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), refreshed);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(3)).set(keys.capture(), any(), any(Duration.class));
        assertEquals(3, new HashSet<>(keys.getAllValues()).size());
    }

    @Test
    @Order(12)
    @DisplayName("L2 缓存 - 参数类型和分隔符边界生成不同 Redis key")
    void testL2Cache_ParametersUseTypedLengthDelimitedEncoding() {
        ModelResultContext context = createContext("TestModel");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT ?, ?", Arrays.asList("a,b", "c"), createTestResult(), context);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT ?, ?", Arrays.asList("a", "b,c"), createTestResult(), context);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT ?", Collections.singletonList(1), createTestResult(), context);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT ?", Collections.singletonList("1"), createTestResult(), context);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(4)).set(keys.capture(), any(), any(Duration.class));
        assertEquals(4, new HashSet<>(keys.getAllValues()).size());
    }

    @Test
    @Order(13)
    @DisplayName("L2 缓存 - 缺少安全上下文或模型身份时不访问 Redis")
    void testL2Cache_MissingIdentity_FailsClosed() {
        ModelResultContext missingSecurity = createContext("TestModel");
        missingSecurity.setSecurityContext(null);
        ModelResultContext emptySecurity = createContext("TestModel");
        emptySecurity.setSecurityContext(new ModelResultContext.SecurityContext());
        ModelResultContext missingModel = createContext("TestModel");
        missingModel.setQueryModel(null);

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), missingSecurity);
        cacheProvider.checkL2Cache("TestModel", "SELECT 1", Collections.emptyList(), missingSecurity);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 3", Collections.emptyList(), createTestResult(), emptySecurity);
        cacheProvider.checkL2Cache("TestModel", "SELECT 3", Collections.emptyList(), emptySecurity);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 2", Collections.emptyList(), createTestResult(), missingModel);
        cacheProvider.checkL2Cache("TestModel", "SELECT 2", Collections.emptyList(), missingModel);

        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @Order(14)
    @DisplayName("L2 缓存 - 请求 alias 与 resolved model 名称不同时仍使用完整身份")
    void testL2Cache_ModelAlias_RemainsCacheable() {
        JdbcQueryModel resolvedModel = createResolvedModel("ResolvedModel");
        ModelResultContext context = createContext("ModelAlias", resolvedModel, "tenant-a");

        cacheProvider.writeL2Cache(
                "ResolvedModel", "SELECT 1", Collections.emptyList(), createTestResult(), context);

        verify(valueOperations).set(startsWith("qc:l2:ResolvedModel:"), any(), any(Duration.class));
    }

    @Test
    @Order(15)
    @DisplayName("L2 缓存 - 调用方 modelName 不等于 canonical 时不访问 Redis")
    void testL2Cache_ModelNameMismatch_FailsClosed() {
        JdbcQueryModel resolvedModel = createResolvedModel("ResolvedModel");
        ModelResultContext context = createContext("ModelAlias", resolvedModel, "tenant-a");

        cacheProvider.writeL2Cache(
                "ExpectedA", "SELECT 1", Collections.emptyList(), createTestResult(), context);
        PagingResultImpl result = cacheProvider.checkL2Cache(
                "ExpectedA", "SELECT 1", Collections.emptyList(), context);

        assertNull(result);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(valueOperations, never()).get(anyString());
    }

    // ==================== 缓存管理测试 ====================

    @Test
    @Order(20)
    @DisplayName("evict - 清除指定模型的缓存")
    void testEvict_SpecificModel() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "qc:l1:Model1:abc",
                "qc:l1:Model1:def",
                "qc:l2:Model1:xyz"
        ));

        when(redisTemplate.keys("qc:l1:Model1:*")).thenReturn(keys);
        when(redisTemplate.keys("qc:l2:Model1:*")).thenReturn(Collections.emptySet());

        cacheProvider.evict("Model1");

        verify(redisTemplate).delete(keys);

        Set<String> l2Only = Collections.singleton("qc:l2:Model2:only");
        when(redisTemplate.keys("qc:l1:Model2:*")).thenReturn(null);
        when(redisTemplate.keys("qc:l2:Model2:*")).thenReturn(l2Only);
        cacheProvider.evict("Model2");
        verify(redisTemplate).delete(l2Only);

        when(redisTemplate.keys("qc:l1:BrokenModel:*")).thenThrow(new RuntimeException("scan failed"));
        assertDoesNotThrow(() -> cacheProvider.evict("BrokenModel"),
                "清理单模型失败不得向调用方传播 Redis 异常");
    }

    @Test
    @Order(21)
    @DisplayName("evictAll - 清除所有缓存")
    void testEvictAll() {
        Set<String> allKeys = new HashSet<>(Arrays.asList(
                "qc:l1:Model1:abc",
                "qc:l2:Model2:xyz"
        ));

        when(redisTemplate.keys("qc:*")).thenReturn(allKeys);

        cacheProvider.evictAll();

        verify(redisTemplate).delete(allKeys);

        when(redisTemplate.keys("qc:*")).thenReturn(null);
        assertDoesNotThrow(cacheProvider::evictAll);

        when(redisTemplate.keys("qc:*")).thenThrow(new RuntimeException("scan failed"));
        assertDoesNotThrow(cacheProvider::evictAll,
                "全量清理失败不得向调用方传播 Redis 异常");
    }

    @Test
    @Order(22)
    @DisplayName("getStats - 返回统计信息")
    void testGetStats() {
        Map<String, Object> stats = cacheProvider.getStats();

        assertNotNull(stats);
        assertEquals("redis", stats.get("type"));
        assertTrue((Boolean) stats.get("enabled"));
        assertEquals(0L, stats.get("l1Hits"));
        assertEquals(0L, stats.get("l1Misses"));
        assertEquals(0L, stats.get("l2Hits"));
        assertEquals(0L, stats.get("l2Misses"));
    }

    @Test
    @Order(23)
    @DisplayName("getStats - 记录命中/未命中")
    void testGetStats_RecordsHitsMisses() {
        ModelResultContext context = createContext("TestModel");
        PagingResultImpl cachedResult = createTestResult();

        // 模拟未命中
        when(valueOperations.get(anyString())).thenReturn(null);
        cacheProvider.checkL2Cache("TestModel", "SELECT 1", Collections.emptyList(), context);

        // 模拟命中
        when(valueOperations.get(anyString())).thenReturn(cachedResult);
        cacheProvider.checkL2Cache("TestModel", "SELECT 2", Collections.emptyList(), context);

        Map<String, Object> stats = cacheProvider.getStats();

        assertEquals(1L, stats.get("l2Hits"));
        assertEquals(1L, stats.get("l2Misses"));
    }

    @Test
    @Order(24)
    @DisplayName("getOrder - 返回优先级")
    void testGetOrder() {
        assertEquals(100, cacheProvider.getOrder());
    }

    // ==================== L1 缓存测试 ====================

    @Test
    @Order(30)
    @DisplayName("L1 缓存 - 无 authorization 返回 null")
    void testL1Cache_NoAuth_ReturnsNull() {
        ModelResultContext context = createContext("TestModel");
        context.setCacheConfig(QueryCacheConfig.enableL1());

        PagingResultImpl result = cacheProvider.checkL1Cache(context, null);

        assertNull(result);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @Order(31)
    @DisplayName("L1 缓存 - 空 authorization 返回 null")
    void testL1Cache_EmptyAuth_ReturnsNull() {
        ModelResultContext context = createContext("TestModel");
        context.setCacheConfig(QueryCacheConfig.enableL1());

        PagingResultImpl result = cacheProvider.checkL1Cache(context, "");

        assertNull(result);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @Order(32)
    @DisplayName("L1 缓存 - 禁用时不查询")
    void testL1Cache_Disabled_NoQuery() {
        properties.setEnabled(false);
        ModelResultContext context = createContext("TestModel");

        PagingResultImpl result = cacheProvider.checkL1Cache(context, "Bearer token");

        assertNull(result);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @Order(33)
    @DisplayName("L1 缓存 - 写入读取使用同一安全 key 且不泄漏 token")
    void testL1Cache_WriteAndRead_UsesOpaqueKey() {
        ModelResultContext context = createContext("TestModel");
        String authorization = "Bearer top-secret-token";
        PagingResultImpl expected = createTestResult();

        cacheProvider.writeL1Cache(context, authorization, expected);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(key.capture(), eq(expected), any(Duration.class));
        assertTrue(key.getValue().startsWith("qc:l1:TestModel:"));
        assertFalse(key.getValue().contains("top-secret-token"));
        when(valueOperations.get(key.getValue())).thenReturn(expected);

        PagingResultImpl cached = cacheProvider.checkL1Cache(context, authorization);

        assertSame(expected, cached);
        verify(valueOperations).get(key.getValue());

        when(valueOperations.get(key.getValue())).thenReturn("unexpected-payload");
        assertNull(cacheProvider.checkL1Cache(context, authorization),
                "非 PagingResultImpl 值必须按未命中处理");

        when(valueOperations.get(key.getValue())).thenThrow(new RuntimeException("Redis read failed"));
        assertNull(cacheProvider.checkL1Cache(context, authorization),
                "L1 读取异常必须按未命中降级");
        verify(valueOperations, times(3)).get(key.getValue());

        doThrow(new RuntimeException("Redis write failed"))
                .when(valueOperations).set(anyString(), eq(expected), any(Duration.class));
        assertDoesNotThrow(() -> cacheProvider.writeL1Cache(
                context, "Bearer another-token", expected),
                "L1 写异常不得中断查询");
    }

    @Test
    @Order(34)
    @DisplayName("L1 缓存 - 缺少显式安全上下文时不访问 Redis")
    void testL1Cache_MissingSecurityContext_FailsClosed() {
        ModelResultContext context = createContext("TestModel");
        context.setSecurityContext(null);

        cacheProvider.writeL1Cache(context, "Bearer token", createTestResult());
        PagingResultImpl cached = cacheProvider.checkL1Cache(context, "Bearer token");

        assertNull(cached);

        assertNull(cacheProvider.checkL1Cache(null, "Bearer token"));
        cacheProvider.writeL1Cache(null, "Bearer token", createTestResult());

        ModelResultContext excluded = createContext("ExcludedModel");
        properties.getExcludeModels().add("ExcludedModel");
        cacheProvider.writeL1Cache(excluded, "Bearer token", createTestResult());
        assertNull(cacheProvider.checkL1Cache(excluded, "Bearer token"));

        ModelResultContext valid = createContext("TestModel");
        cacheProvider.writeL1Cache(valid, "Bearer token", null);
        properties.setCacheEmptyResult(false);
        PagingResultImpl nullItems = new PagingResultImpl();
        nullItems.setItems(null);
        cacheProvider.writeL1Cache(valid, "Bearer token", nullItems);
        cacheProvider.writeL1Cache(
                valid,
                "Bearer token",
                PagingResultImpl.of(Collections.emptyList(), 0, 10, null, 0));
        properties.setCacheEmptyResult(true);
        properties.setMaxResultSize(1);
        cacheProvider.writeL1Cache(valid, "Bearer token", createTestResult());

        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(valueOperations, never()).get(anyString());
    }

    // ==================== 辅助方法 ====================

    private ModelResultContext createContext(String modelName) {
        return createContext(modelName, createResolvedModel(modelName), "default");
    }

    private ModelResultContext createContext(String modelName, QueryModel queryModel, String namespace) {
        return createContext(
                modelName,
                queryModel,
                namespace,
                "catalog:" + CatalogIdentity.canonicalNamespace(namespace) + ":1",
                "binding:" + CatalogIdentity.canonicalNamespace(namespace) + ":1",
                true);
    }

    private ModelResultContext createContext(String modelName,
                                             QueryModel queryModel,
                                             String namespace,
                                             String catalogGeneration,
                                             String bindingGeneration,
                                             boolean bindingIdentityComplete) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(modelName);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setNamespace(namespace);
        context.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .authorization("Bearer redis-test-token")
                .userId("test-user")
                .tenantId("test-tenant")
                .roles(Arrays.asList("reader", "analyst"))
                .build());
        CatalogIdentity identity = new CatalogIdentity(
                namespace,
                new CatalogGeneration(catalogGeneration),
                new SourceRevision("source:boot:1"));
        DatasourceBindingIdentity bindingIdentity = new DatasourceBindingIdentity(
                "primary",
                "runtime-registry",
                new DatasourceBindingGeneration(bindingGeneration));
        context.pinCatalogResolution(
                new CatalogResolution<>(
                        queryModel.getName(),
                        queryModel,
                        identity,
                        Map.of(bindingIdentity.bindingKey(), bindingIdentity),
                        bindingIdentityComplete),
                namespace);
        return context;
    }

    private JdbcQueryModel createResolvedModel(String modelName) {
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getName()).thenReturn(modelName);
        when(queryModel.getDataSource()).thenReturn(mock(DataSource.class));
        return queryModel;
    }

    private PagingResultImpl createTestResult() {
        return PagingResultImpl.of(
                Arrays.asList("item1", "item2"),
                0, 10, null, 2
        );
    }
}

package com.foggyframework.dataset.db.model.cache.provider;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.cache.config.QueryCacheProperties;
import com.foggyframework.dataset.db.model.cache.fingerprint.QueryFingerprintBuilder;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.QueryCacheProvider;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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

        cacheProvider.writeL2Cache(
                "TestModel",
                "SELECT * FROM test",
                Collections.emptyList(),
                testResult,
                context
        );

        verify(valueOperations).set(anyString(), eq(testResult), any(Duration.class));
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
        context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L1_CACHE, true);

        PagingResultImpl result = cacheProvider.checkL1Cache(context, null);

        assertNull(result);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @Order(31)
    @DisplayName("L1 缓存 - 空 authorization 返回 null")
    void testL1Cache_EmptyAuth_ReturnsNull() {
        ModelResultContext context = createContext("TestModel");
        context.getExtData().put(QueryCacheProvider.EXT_ENABLE_L1_CACHE, true);

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

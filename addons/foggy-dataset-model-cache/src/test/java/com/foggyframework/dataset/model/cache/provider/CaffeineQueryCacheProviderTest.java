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

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        properties.setEnabled(true);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT null-result", Collections.emptyList(), null, context);
        assertEquals(0L, cacheProvider.getStats().get("l2EstimatedSize"),
                "null result 不能进入缓存");
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

        PagingResultImpl nullItems = new PagingResultImpl();
        nullItems.setItems(null);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT null-items", Collections.emptyList(), nullItems, context);
        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT null-items", Collections.emptyList(), context));

        properties.setCacheEmptyResult(true);
        properties.setMaxResultSize(0);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT cache-null-items", Collections.emptyList(), nullItems, context);
        assertSame(nullItems, cacheProvider.checkL2Cache(
                "TestModel", "SELECT cache-null-items", Collections.emptyList(), context),
                "允许空结果且关闭大小限制时，null items 结果仍可稳定缓存");
    }

    @Test
    @Order(9)
    @DisplayName("L2 缓存 - namespace 不同必须隔离")
    void testL2Cache_DifferentNamespace_NoHit() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext tenantA = createContext("TestModel", queryModel, "tenant-a");
        ModelResultContext tenantB = createContext("TestModel", queryModel, "tenant-b");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), createTestResult(), tenantA);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), tenantB));
    }

    @Test
    @Order(10)
    @DisplayName("L2 缓存 - 安全策略不同必须隔离")
    void testL2Cache_DifferentFieldAccess_NoHit() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext fullAccess = createContext("TestModel", queryModel, "tenant-a");
        ModelResultContext limitedAccess = createContext("TestModel", queryModel, "tenant-a");
        fullAccess.setFieldAccess(new LinkedHashSet<>(Arrays.asList("id", "name")));
        limitedAccess.setFieldAccess(Collections.singleton("id"));

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), createTestResult(), fullAccess);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), limitedAccess));
    }

    @Test
    @Order(10)
    @DisplayName("L2 缓存 - authorization 不同必须隔离")
    void testL2Cache_DifferentAuthorization_NoHit() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext firstUser = createContext("TestModel", queryModel, "tenant-a");
        ModelResultContext secondUser = createContext("TestModel", queryModel, "tenant-a");
        secondUser.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .authorization("Bearer another-token")
                .userId("another-user")
                .tenantId("test-tenant")
                .roles(Arrays.asList("reader", "analyst"))
                .build());

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), createTestResult(), firstUser);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), secondUser));
    }

    @Test
    @Order(11)
    @DisplayName("L2 缓存 - 独立上下文和模型对象的同一强身份可命中")
    void testL2Cache_IndependentContextsWithSameIdentity_Hit() {
        ModelResultContext firstContext = createContext("TestModel");
        ModelResultContext secondContext = createContext("TestModel");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), createTestResult(), firstContext);

        assertNotNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), secondContext));
    }

    @Test
    @Order(12)
    @DisplayName("L2 缓存 - 未跟踪 JDBC 模型不得用数据源实例作为身份")
    void testL2Cache_UntrackedJdbcModel_NoCaching() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext context = createUntrackedContext("TestModel", queryModel, "tenant-a");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), createTestResult(), context);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT * FROM test", Collections.emptyList(), context));
        assertEquals(0L, cacheProvider.getStats().get("l2EstimatedSize"));
    }

    @Test
    @Order(13)
    @DisplayName("L2 缓存 - 参数编码区分分隔符边界")
    void testL2Cache_DelimitedParameters_NoCollision() {
        ModelResultContext context = createContext("TestModel");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT * FROM test WHERE a = ? AND b = ?",
                Arrays.asList("a,b", "c"), createTestResult(), context);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT * FROM test WHERE a = ? AND b = ?",
                Arrays.asList("a", "b,c"), context));
    }

    @Test
    @Order(14)
    @DisplayName("L2 缓存 - 参数编码区分值类型")
    void testL2Cache_TypedParameters_NoCollision() {
        ModelResultContext context = createContext("TestModel");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT * FROM test WHERE id = ?",
                Collections.singletonList(1), createTestResult(), context);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT * FROM test WHERE id = ?",
                Collections.singletonList("1"), context));
    }

    @Test
    @Order(15)
    @DisplayName("L2 缓存 - 缺少安全上下文或模型身份时 fail closed")
    void testL2Cache_MissingIdentity_NoCaching() {
        ModelResultContext missingSecurity = createContext("TestModel");
        missingSecurity.setSecurityContext(null);
        ModelResultContext emptySecurity = createContext("TestModel");
        emptySecurity.setSecurityContext(new ModelResultContext.SecurityContext());
        ModelResultContext missingModel = createContext("TestModel");
        missingModel.setQueryModel(null);

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), missingSecurity);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 3", Collections.emptyList(), createTestResult(), emptySecurity);
        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 2", Collections.emptyList(), createTestResult(), missingModel);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), missingSecurity));
        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT 3", Collections.emptyList(), emptySecurity));
        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT 2", Collections.emptyList(), missingModel));
        assertEquals(0L, cacheProvider.getStats().get("l2EstimatedSize"));
    }

    @Test
    @Order(16)
    @DisplayName("L2 缓存 - 未跟踪 routing datasource 不触发实例回退")
    void testL2Cache_UntrackedRoutingDataSource_NoCaching() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext context = createUntrackedContext("TestModel", queryModel, "tenant-a");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), context);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), context));
        assertEquals(0L, cacheProvider.getStats().get("l2EstimatedSize"));
    }

    @Test
    @Order(17)
    @DisplayName("L2 缓存 - 未跟踪模型不解包 delegating datasource")
    void testL2Cache_UntrackedDelegatingDataSource_NoCaching() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext context = createUntrackedContext("TestModel", queryModel, "tenant-a");

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), context);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), context));
        assertEquals(0L, cacheProvider.getStats().get("l2EstimatedSize"));
    }

    @Test
    @Order(18)
    @DisplayName("L2 缓存 - Catalog generation 变化后旧 key 不可达")
    void testL2Cache_CatalogGenerationChange_NoHit() {
        ModelResultContext before = createContext(
                "TestModel", createResolvedModel("TestModel"), "default",
                "catalog:boot:1", "binding:registry:1", true);
        ModelResultContext after = createContext(
                "TestModel", createResolvedModel("TestModel"), "default",
                "catalog:boot:2", "binding:registry:1", true);

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), before);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), after));
    }

    @Test
    @Order(19)
    @DisplayName("L2 缓存 - datasource binding generation 变化后旧 key 不可达")
    void testL2Cache_BindingGenerationChange_NoHit() {
        ModelResultContext before = createContext(
                "TestModel", createResolvedModel("TestModel"), "default",
                "catalog:boot:1", "binding:registry:1", true);
        ModelResultContext after = createContext(
                "TestModel", createResolvedModel("TestModel"), "default",
                "catalog:boot:1", "binding:registry:2", true);

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), before);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), after));
    }

    @Test
    @Order(20)
    @DisplayName("L2 缓存 - 已捕获 catalog 但 binding 身份不完整时 fail closed")
    void testL2Cache_IncompleteBindingIdentity_NoCaching() {
        ModelResultContext context = createContext(
                "TestModel", createResolvedModel("TestModel"), "default",
                "catalog:boot:1", "binding:registry:1", false);

        cacheProvider.writeL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), createTestResult(), context);

        assertNull(cacheProvider.checkL2Cache(
                "TestModel", "SELECT 1", Collections.emptyList(), context));
        assertEquals(0L, cacheProvider.getStats().get("l2EstimatedSize"));
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

        QueryCacheProperties noStatsProperties = new QueryCacheProperties();
        noStatsProperties.getCaffeine().setRecordStats(false);
        CaffeineQueryCacheProvider noStatsProvider = new CaffeineQueryCacheProvider(
                new QueryFingerprintBuilder(), noStatsProperties);
        Map<String, Object> noStats = noStatsProvider.getStats();
        assertFalse(noStats.containsKey("l1Hits"),
                "关闭 recordStats 时不得暴露伪造的命中统计");
        noStatsProvider.evictAll();
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
        assertNull(cacheProvider.checkL1Cache(null, "Bearer token"),
                "缺少上下文时必须 fail closed");

        properties.getExcludeModels().add("ExcludedModel");
        assertNull(cacheProvider.checkL1Cache(createContext("ExcludedModel"), "Bearer token"),
                "排除模型不得读取 L1");
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

        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext context = createContext("TestModel", queryModel, "tenant-a");
        context.setRequest(pagingRequest);
        context.setCacheConfig(QueryCacheConfig.enableL1());

        String authorization = "Bearer test-token-123";
        PagingResultImpl testResult = createTestResult();

        // 写入 L1 缓存
        cacheProvider.writeL1Cache(context, authorization, testResult);

        // 读取 L1 缓存
        PagingResultImpl cached = cacheProvider.checkL1Cache(context, authorization);

        assertNotNull(cached, "可缓存查询写入后必须命中 L1");
        assertEquals(testResult.getTotal(), cached.getTotal());
    }

    @Test
    @Order(32)
    @DisplayName("L1 缓存 - fieldAccess 不同必须隔离")
    void testL1Cache_DifferentFieldAccess_NoHit() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext fullAccess = createContext("TestModel", queryModel, "tenant-a");
        ModelResultContext limitedAccess = createContext("TestModel", queryModel, "tenant-a");
        fullAccess.setFieldAccess(new LinkedHashSet<>(Arrays.asList("id", "name")));
        limitedAccess.setFieldAccess(Collections.singleton("id"));

        cacheProvider.writeL1Cache(fullAccess, "Bearer same-token", createTestResult());

        assertNull(cacheProvider.checkL1Cache(limitedAccess, "Bearer same-token"));
    }

    @Test
    @Order(32)
    @DisplayName("L1 缓存 - authorization 不同必须隔离")
    void testL1Cache_DifferentAuthorization_NoHit() {
        JdbcQueryModel queryModel = createResolvedModel("TestModel");
        ModelResultContext context = createContext("TestModel", queryModel, "tenant-a");

        cacheProvider.writeL1Cache(context, "Bearer first-token", createTestResult());

        assertNull(cacheProvider.checkL1Cache(context, "Bearer second-token"));
    }

    @Test
    @Order(33)
    @DisplayName("L1 缓存 - 缺少显式安全上下文时 fail closed")
    void testL1Cache_MissingSecurityContext_NoCaching() {
        ModelResultContext context = createContext("TestModel");
        context.setSecurityContext(null);

        cacheProvider.writeL1Cache(context, "Bearer test-token", createTestResult());

        assertNull(cacheProvider.checkL1Cache(context, "Bearer test-token"));

        ModelResultContext valid = createContext("TestModel");
        cacheProvider.writeL1Cache(valid, "Bearer test-token", null);

        properties.setCacheEmptyResult(false);
        PagingResultImpl nullItems = new PagingResultImpl();
        nullItems.setItems(null);
        cacheProvider.writeL1Cache(valid, "Bearer test-token", nullItems);
        cacheProvider.writeL1Cache(
                valid,
                "Bearer test-token",
                PagingResultImpl.of(Collections.emptyList(), 0, 10, null, 0));

        properties.setCacheEmptyResult(true);
        properties.setMaxResultSize(1);
        cacheProvider.writeL1Cache(valid, "Bearer test-token", createTestResult());
        assertEquals(0L, cacheProvider.getStats().get("l1EstimatedSize"));
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
        ModelResultContext context = baseContext(modelName, namespace);
        pinStrongIdentity(
                context, queryModel, catalogGeneration, bindingGeneration, bindingIdentityComplete);
        return context;
    }

    private ModelResultContext createUntrackedContext(
            String modelName, QueryModel queryModel, String namespace) {
        ModelResultContext context = baseContext(modelName, namespace);
        context.pinUntrackedQueryModel(queryModel);
        return context;
    }

    private ModelResultContext baseContext(String modelName, String namespace) {
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel(modelName);

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryRequest);

        ModelResultContext context = new ModelResultContext(pagingRequest, null);
        context.setNamespace(namespace);
        context.setSecurityContext(ModelResultContext.SecurityContext.builder()
                .authorization("Bearer test-token")
                .userId("test-user")
                .tenantId("test-tenant")
                .roles(Arrays.asList("reader", "analyst"))
                .build());
        return context;
    }

    private JdbcQueryModel createResolvedModel(String modelName) {
        JdbcQueryModel queryModel = mock(JdbcQueryModel.class);
        when(queryModel.getName()).thenReturn(modelName);
        when(queryModel.getDataSource()).thenReturn(mock(DataSource.class));
        return queryModel;
    }

    private void pinStrongIdentity(ModelResultContext context,
                                   QueryModel queryModel,
                                   String catalogGeneration,
                                   String bindingGeneration,
                                   boolean bindingIdentityComplete) {
        CatalogIdentity identity = new CatalogIdentity(
                context.getNamespace(),
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
                context.getNamespace());
    }

    private PagingResultImpl createTestResult() {
        return PagingResultImpl.of(
                Arrays.asList("item1", "item2"),
                0, 10, null, 2
        );
    }

}

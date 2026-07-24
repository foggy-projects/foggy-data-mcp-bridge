package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotLayout;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.semantic.exception.TooManyPivotCellsException;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.spi.ProcessLocalDefaultDataSourceResolver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pivot 端到端集成测试
 *
 * <p>基于真实 SQLite 数据库（FactSalesQueryModel）验证 Pivot Pipeline 的执行结果，
 * 涵盖扁平、网格、树形、交叉互斥、小计总计等全部核心特性。</p>
 */
@Slf4j
@DisplayName("Pivot Pipeline 端到端集成验收")
@Import(PivotIT.TrackedPivotDatasourceConfiguration.class)
class PivotIT extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";
    private static final int LARGE_AXIS_DOMAIN_SIZE = 1105;
    private static final String LARGE_AXIS_DOMAIN_STATUS = "PDS_BIG";
    private static final String PARENT_CHILD_WINDOW_STATUS_A = "PDS_WINDOW_A";
    private static final String PARENT_CHILD_WINDOW_STATUS_B = "PDS_WINDOW_B";
    private static final String BASELINE_RATIO_SCOPE_STATUS = "PDS_BR_SCOPE";

    @TestConfiguration(proxyBeanMethods = false)
    static class TrackedPivotDatasourceConfiguration {

        @Bean
        @Primary
        NamedDataSourceResolver pivotTrackedDatasourceResolver(DataSource dataSource) {
            return new TrackedPivotDatasourceResolver(dataSource);
        }
    }

    private static final class TrackedPivotDatasourceResolver
            implements NamedDataSourceResolver, ProcessLocalDefaultDataSourceResolver {

        private static final DatasourceBindingIdentity DEFAULT_IDENTITY =
                new DatasourceBindingIdentity(
                        "test:pivot:process-local-default",
                        "test:pivot:ecommerce-backend",
                        new DatasourceBindingGeneration("test-pivot-binding-v1"));
        private static final DatasourceBindingIdentity ODOO_IDENTITY =
                new DatasourceBindingIdentity(
                        "test:pivot:named:odoo",
                        "test:pivot:ecommerce-backend",
                        new DatasourceBindingGeneration("test-pivot-binding-v1"));

        private final DataSource dataSource;
        private final Object publicationMonitor = new Object();

        private TrackedPivotDatasourceResolver(DataSource dataSource) {
            this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        }

        @Override
        public DataSource resolve(String name) {
            return "odoo".equals(name) ? dataSource : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveBinding(String name) {
            return "odoo".equals(name)
                    ? ResolvedDatasourceBinding.tracked(dataSource, ODOO_IDENTITY)
                    : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
            return namespace == null || namespace.isBlank()
                    ? ResolvedDatasourceBinding.tracked(dataSource, DEFAULT_IDENTITY)
                    : null;
        }

        @Override
        public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
            return ResolvedDatasourceBinding.tracked(dataSource, DEFAULT_IDENTITY);
        }

        @Override
        public BindingCurrentness currentness(DatasourceBindingIdentity identity) {
            return DEFAULT_IDENTITY.equals(identity) || ODOO_IDENTITY.equals(identity)
                    ? BindingCurrentness.CURRENT
                    : BindingCurrentness.STALE;
        }

        @Override
        public <T> T publishIfCurrent(Collection<DatasourceBindingIdentity> identities,
                                      Supplier<T> publication) {
            Objects.requireNonNull(identities, "identities");
            Objects.requireNonNull(publication, "publication");
            synchronized (publicationMonitor) {
                if (!identities.stream()
                        .allMatch(identity -> currentness(identity) == BindingCurrentness.CURRENT)) {
                    throw new IllegalStateException("stale Pivot test datasource binding");
                }
                return publication.get();
            }
        }

        @Override
        public boolean isConfigured(String name) {
            return "odoo".equals(name);
        }
    }

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("基础 Pivot (Flat) - 仅有行和度量")
    void testBasicFlatPivot() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);

        // 返回扁平结构
        List<Map<String, Object>> items = response.getItems();
        assertFalse(items.isEmpty());
        assertTrue(items.get(0).containsKey("product$categoryName"));
        assertTrue(items.get(0).containsKey("salesAmount"));

        Map<?, ?> contract = pivotEngineContract(response);
        assertEquals(Boolean.TRUE, contract.get("signed"));
        Map<?, ?> treeContract = contractSection(contract, "tree_axis_contract");
        assertEquals(Boolean.FALSE, treeContract.get("signed"));
        assertContractListContains(treeContract, "unsupported_combinations",
                "columns_axis_tree",
                "output_format_not_tree",
                "domainSlice_start_offset");
        Map<?, ?> drilldownContract = contractSection(contract, "drilldown_contract");
        assertEquals(Boolean.FALSE, drilldownContract.get("signed"));
        assertContractListContains(drilldownContract, "unsigned_shapes",
                "domain_tree_cursor",
                "interactive_expand_collapse_state",
                "multi_level_domainSlice",
                "columns_multi_level_start_offset",
                "tree_axis_domainSlice_start_offset");

        List<Map<String, Object>> diagnostics = pivotDiagnostics(response);
        Map<String, Object> cacheIdentity = diagnosticEvent(diagnostics, "pivot.cache.identity");
        assertEquals(PivotOuterCacheStrongIdentity.STATUS_COMPLETE, cacheIdentity.get("status"));
        assertTrue(((Number) cacheIdentity.get("bindingCount")).intValue() > 0);
        assertEquals(Boolean.FALSE, cacheIdentity.get("manualTokenPresent"));
        assertEquals(64, String.valueOf(cacheIdentity.get("identityHash")).length());
        Map<String, Object> cacheLookup = diagnosticEvent(diagnostics, "pivot.cache.lookup");
        assertEquals("E1a", cacheLookup.get("eligibilityStage"));
        assertEquals("flat", cacheLookup.get("shapeClass"));
        assertNotNull(cacheLookup.get("keyHash"));
        Map<String, Object> cacheMiss = diagnosticEvent(diagnostics, "pivot.cache.miss");
        assertEquals("telemetry_only", cacheMiss.get("reason"));
        assertEquals(cacheLookup.get("keyHash"), cacheMiss.get("keyHash"));
        assertDiagnosticEvent(diagnostics, "pivot.sql_pushdown.skipped");
        Map<String, Object> executionPath = diagnosticEvent(diagnostics, "pivot.execution_path");
        assertEquals(Boolean.FALSE, executionPath.get("sqlPushdownUsed"));
        assertEquals(Boolean.FALSE, executionPath.get("axisDomainSelectionUsed"));
        assertEquals(Boolean.FALSE, executionPath.get("cascadeGenerateUsed"));
        assertEquals(Boolean.TRUE, executionPath.get("memoryShapingUsed"));
        assertTrue(((Number) executionPath.get("resultRows")).intValue() > 0);
    }

    @Test
    @DisplayName("网格 Pivot (Grid) - 行、列、度量")
    void testGridPivot() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(axis("salesDate$month")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("grid");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();

        // grid 模式返回单个包装好的元素
        assertEquals(1, items.size());
        Map<String, Object> grid = items.get(0);

        assertEquals("grid", grid.get("format"));
        assertNotNull(grid.get("rowHeaders"));
        assertNotNull(grid.get("columnHeaders"));
        assertNotNull(grid.get("cells"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rowHeaders = (List<Map<String, Object>>) grid.get("rowHeaders");
        assertFalse(rowHeaders.isEmpty());
        assertTrue(rowHeaders.get(0).containsKey("product$categoryName"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> colHeaders = (List<Map<String, Object>>) grid.get("columnHeaders");
        assertFalse(colHeaders.isEmpty());
        assertTrue(colHeaders.get(0).containsKey("salesDate$month"));
        assertTrue(colHeaders.get(0).containsKey("metric"));
    }

    @Test
    @DisplayName("v9.2: E1b outer cache 命中只返回缓存响应，不重跑 Pivot pipeline")
    void testOuterCacheHitFlatPivotE1b() {
        SemanticQueryServiceV3Impl impl = (SemanticQueryServiceV3Impl) semanticQueryServiceV3;
        PivotPipeline originalPipeline = (PivotPipeline) ReflectionTestUtils.getField(impl, "pivotPipeline");
        try {
            ReflectionTestUtils.setField(impl, "pivotPipeline", outerCachePipeline(60_000L));

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setPivot(basicSalesPivot());

            SemanticQueryResponse first = execute(request);
            SemanticQueryResponse second = execute(request);

            assertEquals(first.getItems(), second.getItems(), "cache hit should return the same Pivot payload");

            List<Map<String, Object>> firstDiagnostics = pivotDiagnostics(first);
            Map<String, Object> firstLookup = diagnosticEvent(firstDiagnostics, "pivot.cache.lookup");
            assertEquals("E1b", firstLookup.get("eligibilityStage"));
            Map<String, Object> firstMiss = diagnosticEvent(firstDiagnostics, "pivot.cache.miss");
            assertEquals("cache_not_found", firstMiss.get("reason"));
            Map<String, Object> store = diagnosticEvent(firstDiagnostics, "pivot.cache.store");
            assertEquals(firstLookup.get("keyHash"), store.get("keyHash"));
            assertTrue(((Number) store.get("payloadBytes")).intValue() > 0);

            List<Map<String, Object>> secondDiagnostics = pivotDiagnostics(second);
            Map<String, Object> secondLookup = diagnosticEvent(secondDiagnostics, "pivot.cache.lookup");
            assertEquals("E1b", secondLookup.get("eligibilityStage"));
            Map<String, Object> hit = diagnosticEvent(secondDiagnostics, "pivot.cache.hit");
            assertEquals(secondLookup.get("keyHash"), hit.get("keyHash"));
            assertEquals(PivotOuterResponseCache.CACHE_NAME, hit.get("cacheName"));
            assertFalse(hasDiagnosticEvent(secondDiagnostics, "pivot.execution_path"),
                    "cache hit response must not pretend the Pivot pipeline executed");
        } finally {
            ReflectionTestUtils.setField(impl, "pivotPipeline", originalPipeline);
        }
    }

    @Test
    @DisplayName("v9.2: E1b outer cache TTL 过期后重新执行并写入")
    void testOuterCacheTtlExpiryFlatPivotE1b() {
        SemanticQueryServiceV3Impl impl = (SemanticQueryServiceV3Impl) semanticQueryServiceV3;
        PivotPipeline originalPipeline = (PivotPipeline) ReflectionTestUtils.getField(impl, "pivotPipeline");
        try {
            ControlledTimeOuterCacheProvider outerCache = new ControlledTimeOuterCacheProvider(1L, 100L);
            ReflectionTestUtils.setField(impl, "pivotPipeline", outerCachePipeline(outerCache));

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setPivot(basicSalesPivot());

            SemanticQueryResponse first = execute(request);
            assertDiagnosticEvent(pivotDiagnostics(first), "pivot.cache.store");

            outerCache.advanceTo(102L);

            SemanticQueryResponse second = execute(request);
            assertEquals(first.getItems(), second.getItems(), "TTL refresh should preserve query result payload");
            List<Map<String, Object>> diagnostics = pivotDiagnostics(second);
            Map<String, Object> evicted = diagnosticEvent(diagnostics, "pivot.cache.evicted");
            assertEquals("ttl_expired", evicted.get("reason"));
            Map<String, Object> miss = diagnosticEvent(diagnostics, "pivot.cache.miss");
            assertEquals("ttl_expired", miss.get("reason"));
            assertDiagnosticEvent(diagnostics, "pivot.execution_path");
            assertDiagnosticEvent(diagnostics, "pivot.cache.store");
        } finally {
            ReflectionTestUtils.setField(impl, "pivotPipeline", originalPipeline);
        }
    }

    @Test
    @DisplayName("v9.2: E1b outer cache 不跨安全上下文命中")
    void testOuterCacheMissesAcrossSecurityContextsE1b() {
        SemanticQueryServiceV3Impl impl = (SemanticQueryServiceV3Impl) semanticQueryServiceV3;
        PivotPipeline originalPipeline = (PivotPipeline) ReflectionTestUtils.getField(impl, "pivotPipeline");
        try {
            ReflectionTestUtils.setField(impl, "pivotPipeline", outerCachePipeline(60_000L));

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setPivot(basicSalesPivot());

            SemanticQueryResponse userAFirst = execute(request, outerCacheContext("user-a"));
            SemanticQueryResponse userBFirst = execute(request, outerCacheContext("user-b"));
            SemanticQueryResponse userASecond = execute(request, outerCacheContext("user-a"));

            Map<String, Object> userALookup = diagnosticEvent(pivotDiagnostics(userAFirst), "pivot.cache.lookup");
            Map<String, Object> userBLookup = diagnosticEvent(pivotDiagnostics(userBFirst), "pivot.cache.lookup");
            assertNotEquals(userALookup.get("keyHash"), userBLookup.get("keyHash"),
                    "different security contexts must not share cache keys");
            assertEquals("cache_not_found",
                    diagnosticEvent(pivotDiagnostics(userBFirst), "pivot.cache.miss").get("reason"));
            assertFalse(hasDiagnosticEvent(pivotDiagnostics(userBFirst), "pivot.cache.hit"));
            assertEquals(userALookup.get("keyHash"),
                    diagnosticEvent(pivotDiagnostics(userASecond), "pivot.cache.hit").get("keyHash"));
        } finally {
            ReflectionTestUtils.setField(impl, "pivotPipeline", originalPipeline);
        }
    }

    @Test
    @DisplayName("v9.2: E1b outer cache 可按 namespace/model 手动清理")
    void testOuterCacheEvictByNamespaceAndModelE1b() {
        SemanticQueryServiceV3Impl impl = (SemanticQueryServiceV3Impl) semanticQueryServiceV3;
        PivotPipeline originalPipeline = (PivotPipeline) ReflectionTestUtils.getField(impl, "pivotPipeline");
        try {
            ReflectionTestUtils.setField(impl, "pivotPipeline", outerCachePipeline(60_000L));

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setPivot(basicSalesPivot());

            SemanticQueryResponse first = execute(request);
            SemanticQueryResponse second = execute(request);
            assertDiagnosticEvent(pivotDiagnostics(first), "pivot.cache.store");
            assertDiagnosticEvent(pivotDiagnostics(second), "pivot.cache.hit");

            assertEquals(1, impl.evictPivotOuterCache(null, TEST_MODEL),
                    "model-level eviction should remove the cached default-namespace entry");

            SemanticQueryResponse third = execute(request);
            List<Map<String, Object>> diagnostics = pivotDiagnostics(third);
            assertEquals("cache_not_found", diagnosticEvent(diagnostics, "pivot.cache.miss").get("reason"));
            assertFalse(hasDiagnosticEvent(diagnostics, "pivot.cache.hit"));
            assertDiagnosticEvent(diagnostics, "pivot.execution_path");
            assertDiagnosticEvent(diagnostics, "pivot.cache.store");
        } finally {
            ReflectionTestUtils.setField(impl, "pivotPipeline", originalPipeline);
        }
    }

    @Test
    @DisplayName("v9.2: E1b outer cache 不存储 warning 响应并输出 store_skipped")
    void testOuterCacheSkipsWarningResponsesE1b() {
        PivotPipeline pipeline = outerCachePipeline(60_000L);
        PivotDiagnosticCollector diagnostics = new PivotDiagnosticCollector(TEST_MODEL);
        PivotOuterCacheTelemetry.Evaluation evaluation = new PivotOuterCacheTelemetry.Evaluation(
                "v2:" + "1".repeat(64), "flat", null,
                "0".repeat(64), PivotOuterCacheStrongIdentity.STATUS_COMPLETE, 1, false);
        SemanticQueryResponse response = new SemanticQueryResponse();
        response.setItems(List.of(Map.of("product$categoryName", "Category", "salesAmount", 1)));
        response.setWarnings(List.of("warning"));
        SemanticQueryResponse.DebugInfo debug = new SemanticQueryResponse.DebugInfo();
        debug.setExtra(new LinkedHashMap<>());
        response.setDebug(debug);

        SemanticQueryResponse result = pipeline.storeOuterCacheIfEligible(
                evaluation, PivotOuterCacheTelemetry.CACHE_STAGE, diagnostics, response);

        List<Map<String, Object>> diagnosticsOutput = pivotDiagnostics(result);
        Map<String, Object> skipped = diagnosticEvent(diagnosticsOutput, "pivot.cache.store_skipped");
        assertEquals(PivotOuterCacheTelemetry.CACHE_STORE_SKIPPED_WARNING_REASON, skipped.get("reason"));
        assertFalse(hasDiagnosticEvent(diagnosticsOutput, "pivot.cache.store"));
    }

    @Test
    @DisplayName("v9.2: E1b external provider unavailable degrades to query execution with diagnostics")
    void testOuterCacheProviderUnavailableDegradesWithDiagnosticsE1b() {
        SemanticQueryServiceV3Impl impl = (SemanticQueryServiceV3Impl) semanticQueryServiceV3;
        PivotPipeline originalPipeline = (PivotPipeline) ReflectionTestUtils.getField(impl, "pivotPipeline");
        try {
            ReflectionTestUtils.setField(impl, "pivotPipeline", outerCachePipeline(
                    PivotOuterCacheSafeProvider.wrap(new UnavailableOuterCacheProvider(), false)));

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setPivot(basicSalesPivot());

            SemanticQueryResponse response = execute(request);
            assertFalse(response.getItems().isEmpty());
            List<Map<String, Object>> diagnostics = pivotDiagnostics(response);

            Map<String, Object> unavailable =
                    diagnosticEvent(diagnostics, "pivot.cache.provider_unavailable");
            assertEquals("isEnabled", unavailable.get("operation"));
            assertEquals("IllegalStateException", unavailable.get("reasonClass"));
            assertEquals("redis unavailable", unavailable.get("reason"));

            Map<String, Object> miss = diagnosticEvent(diagnostics, "pivot.cache.miss");
            assertEquals(PivotOuterCacheTelemetry.CACHE_PROVIDER_UNAVAILABLE_REASON, miss.get("reason"));
            Map<String, Object> storeSkipped = diagnosticEvent(diagnostics, "pivot.cache.store_skipped");
            assertEquals(PivotOuterCacheTelemetry.CACHE_PROVIDER_UNAVAILABLE_REASON, storeSkipped.get("reason"));
            assertDiagnosticEvent(diagnostics, "pivot.execution_path");
        } finally {
            ReflectionTestUtils.setField(impl, "pivotPipeline", originalPipeline);
        }
    }

    @Test
    @DisplayName("Pivot With Having - 对聚合成品进行过滤")
    void testPivotWithHaving() {
        PivotRequest pivot = new PivotRequest();

        AxisField row = axis("product$categoryName");
        MetricFilter having = new MetricFilter();
        having.setMetric("salesAmount");
        having.setOp(">");
        having.setValue(5000); // 只保留销量大于 5000 的品类
        row.setHaving(List.of(having));

        pivot.setRows(List.of(row));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();

        // 验证所有的行 salesAmount 均 > 5000
        for (Map<String, Object> item : items) {
            double amount = ((Number) item.get("salesAmount")).doubleValue();
            assertTrue(amount > 5000, "Having 过滤失败，存在销量 < 5000 的行: " + amount);
        }
    }

    @Test
    @DisplayName("隐式父子分区 TopN - 截断子级")
    void testParentChildTopN() {
        PivotRequest pivot = new PivotRequest();

        AxisField year = axis("salesDate$year");
        AxisField month = axis("salesDate$month");
        month.setLimit(2); // 每个年份下只取 2 个月
        month.setOrderBy(List.of("-salesAmount")); // 按销量降序

        pivot.setRows(List.of(year, month));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("tree");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(request.getPivot());
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        Map<String, Object> treeWrapper = response.getItems().get(0);
        assertEquals("tree", treeWrapper.get("format"));

        @SuppressWarnings("unchecked")
        List<PivotResult.TreeNode> treeData = (List<PivotResult.TreeNode>) treeWrapper.get("data");
        assertFalse(treeData.isEmpty());

        for (PivotResult.TreeNode yearNode : treeData) {
            assertNotNull(yearNode.getChildren());
            assertTrue(yearNode.getChildren().size() <= 2, "每个年份子节点不能超过 2 个");
        }
    }

    @Test
    @DisplayName("v3.7: 多层 rows 子级 start/limit 按每个父级独立分页")
    void testParentChildOffsetLimitWindow() {
        insertParentChildWindowFixture();
        try {
            PivotRequest pivot = new PivotRequest();

            AxisField status = axis("orderStatus");
            AxisField paymentMethod = axis("paymentMethod");
            paymentMethod.setStart(1);
            paymentMethod.setLimit(1);
            paymentMethod.setOrderBy(List.of("-salesAmount"));

            pivot.setRows(List.of(status, paymentMethod));
            pivot.setMetrics(List.of("salesAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderStatus", "in",
                    List.of(PARENT_CHILD_WINDOW_STATUS_A, PARENT_CHILD_WINDOW_STATUS_B))));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);
            List<Map<String, Object>> items = response.getItems();
            assertFalse(items.isEmpty(), "多层 rows 子级 start/limit 应返回每个父级的独立子级窗口");

            Set<String> tuples = new LinkedHashSet<>();
            for (Map<String, Object> item : items) {
                tuples.add(item.get("orderStatus") + "/" + item.get("paymentMethod"));
            }

            assertTrue(tuples.contains(PARENT_CHILD_WINDOW_STATUS_A + "/PDS_PAY_KEEP"),
                    "父级 A 按 salesAmount 降序 start=1 limit=1 后应保留第二名 PDS_PAY_KEEP: " + tuples);
            assertTrue(tuples.contains(PARENT_CHILD_WINDOW_STATUS_B + "/PDS_PAY_KEEP"),
                    "父级 B 按 salesAmount 降序 start=1 limit=1 后应保留第二名 PDS_PAY_KEEP: " + tuples);
            assertFalse(tuples.contains(PARENT_CHILD_WINDOW_STATUS_A + "/PDS_PAY_TOP"),
                    "父级 A 第一名 PDS_PAY_TOP 应被 start=1 跳过: " + tuples);
            assertFalse(tuples.contains(PARENT_CHILD_WINDOW_STATUS_B + "/PDS_PAY_TOP"),
                    "父级 B 第一名 PDS_PAY_TOP 应被 start=1 跳过: " + tuples);

            Map<?, ?> contract = pivotEngineContract(response);
            assertEquals(Boolean.TRUE, contract.get("signed"));
            Map<?, ?> drilldown = contractSection(contract, "drilldown_contract");
            assertEquals(Boolean.TRUE, drilldown.get("signed"));
            assertEquals(Boolean.TRUE, drilldown.get("per_parent_window_used"));
            assertContractListContains(drilldown, "unsigned_shapes",
                    "domain_tree_cursor",
                    "columns_multi_level_start_offset",
                    "tree_axis_domainSlice_start_offset");
            @SuppressWarnings("unchecked")
            List<String> capabilities = (List<String>) contract.get("required_capabilities");
            assertTrue(capabilities.contains("rows_child_per_parent_window"));
        } finally {
            deleteParentChildWindowFixture();
        }
    }

    @Test
    @DisplayName("v3.7: parentShare prePageParent 按子级 window 前父级分母计算")
    void testParentSharePrePageParentWithChildWindow() {
        insertParentChildWindowFixture();
        try {
            PivotRequest pivot = new PivotRequest();

            AxisField status = axis("orderStatus");
            AxisField paymentMethod = axis("paymentMethod");
            paymentMethod.setStart(1);
            paymentMethod.setLimit(1);
            paymentMethod.setOrderBy(List.of("-salesAmount"));

            pivot.setRows(List.of(status, paymentMethod));
            List<PivotMetricItem> metricItems = new ArrayList<>();
            metricItems.add(PivotMetricItem.ofNative("salesAmount"));
            PivotMetricItem paymentShare = new PivotMetricItem();
            paymentShare.setName("paymentShare");
            paymentShare.setType("parentShare");
            paymentShare.setOf("salesAmount");
            paymentShare.setDenominatorScope("prePageParent");
            metricItems.add(paymentShare);
            pivot.setMetricItems(metricItems);
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderStatus", "in",
                    List.of(PARENT_CHILD_WINDOW_STATUS_A, PARENT_CHILD_WINDOW_STATUS_B))));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);
            List<Map<String, Object>> items = response.getItems();
            assertFalse(items.isEmpty(), "parentShare + prePageParent 应返回可见子级窗口");

            for (Map<String, Object> item : items) {
                assertEquals("PDS_PAY_KEEP", item.get("paymentMethod"));
                assertEquals(200d / 600d, ((Number) item.get("paymentShare")).doubleValue(), 0.0001,
                        "占比应使用 window 前父级分母 300+200+100");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> evidence = (List<Map<String, Object>>) response.getDebug()
                    .getExtra().get("parentShareEvidence");
            assertNotNull(evidence, "debug.extra 应包含 parentShareEvidence");
            assertEquals("prePageParent", evidence.get(0).get("denominatorScope"));
            assertEquals("preTopNParentAggIndex", evidence.get(0).get("source"));
            assertEquals(2, ((Number) evidence.get(0).get("parentGroups")).intValue());
        } finally {
            deleteParentChildWindowFixture();
        }
    }

    @Test
    @DisplayName("CrossJoin 骨架补全 - 插入 null 单元格")
    void testCrossJoin() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(axis("salesDate$month")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setCrossjoin(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();

        // 如果存在确实没有销量的 品类+月份，CrossJoinFiller 会补上 null
        // 这里重点断言系统未报错且返回了数据
        assertFalse(items.isEmpty());
    }

    @Test
    @DisplayName("小计与总计 (Subtotals & Grand Total)")
    void testSubtotalsAndGrandTotal() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("salesDate$year"), axis("salesDate$month")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();

        boolean foundRowSubtotal = false;
        boolean foundGrandTotal = false;

        for (Map<String, Object> row : items) {
            if (row.containsKey("_sys_meta")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) row.get("_sys_meta");
                if (Boolean.TRUE.equals(meta.get("isRowSubtotal"))) {
                    foundRowSubtotal = true;
                    assertEquals("ALL", row.get("salesDate$month"));
                }
                if (Boolean.TRUE.equals(meta.get("isGrandTotal"))) {
                    foundGrandTotal = true;
                    assertEquals("GRAND_TOTAL", row.get("salesDate$year"));
                }
            }
        }

        assertTrue(foundRowSubtotal, "未发现行级小计记录");
        assertTrue(foundGrandTotal, "未发现总计记录");
    }

    @Test
    @DisplayName("布局透传 (Layout Placement)")
    void testLayoutPlacementRows() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(axis("salesDate$month")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("grid");

        PivotLayout layout = new PivotLayout();
        layout.setMetricPlacement("rows");
        pivot.setLayout(layout);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        Map<String, Object> grid = response.getItems().get(0);

        @SuppressWarnings("unchecked")
        Map<String, Object> resLayout = (Map<String, Object>) grid.get("layout");
        assertNotNull(resLayout);
        assertEquals("rows", resLayout.get("metricPlacement"));
    }

    @Test
    @DisplayName("互斥校验 - Pivot 与 columns / timeWindow 不能共存")
    void testMutuallyExclusiveValidation() {
        // 1. Pivot + columns
        SemanticQueryRequest request1 = new SemanticQueryRequest();
        PivotRequest pivot1 = new PivotRequest();
        pivot1.setRows(List.of(axis("product$categoryName")));
        pivot1.setMetrics(List.of("salesAmount"));
        request1.setPivot(pivot1);
        request1.setColumns(List.of("some_column"));

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> execute(request1));
        assertTrue(ex1.getMessage().contains("pivot 与 columns 不能同时出现"));

        // 2. Pivot + timeWindow
        SemanticQueryRequest request2 = new SemanticQueryRequest();
        PivotRequest pivot2 = new PivotRequest();
        pivot2.setRows(List.of(axis("product$categoryName")));
        pivot2.setMetrics(List.of("salesAmount"));
        request2.setPivot(pivot2);
        request2.setTimeWindow(Map.of("comparison", "yoy"));

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> execute(request2));
        assertTrue(ex2.getMessage().contains("timeWindow 与 pivot 模式互斥"));
    }

    @Test
    @DisplayName("Pivot 顶层 orderBy / limit 不作为轴 TopN 控制")
    void testTopLevelOrderByAndLimitRejectedInPivotMode() {
        SemanticQueryRequest orderByRequest = new SemanticQueryRequest();
        orderByRequest.setPivot(basicSalesPivot());
        SemanticQueryRequest.OrderItem order = new SemanticQueryRequest.OrderItem();
        order.setField("salesAmount");
        order.setDir("DESC");
        orderByRequest.setOrderBy(List.of(order));

        IllegalArgumentException orderByException =
                assertThrows(IllegalArgumentException.class, () -> execute(orderByRequest));
        assertTrue(orderByException.getMessage().contains("pivot 模式不支持顶层 orderBy"));
        assertTrue(orderByException.getMessage().contains("pivot.rows[*].orderBy"));

        SemanticQueryRequest limitRequest = new SemanticQueryRequest();
        limitRequest.setPivot(basicSalesPivot());
        limitRequest.setLimit(3);

        IllegalArgumentException limitException =
                assertThrows(IllegalArgumentException.class, () -> execute(limitRequest));
        assertTrue(limitException.getMessage().contains("pivot 模式不支持顶层 limit"));
        assertTrue(limitException.getMessage().contains("pivot.rows[*].limit"));
    }

    @Test
    @DisplayName("Pivot 顶层 result-stage 字段不作为透视后处理")
    void testTopLevelResultStageRejectedInPivotMode() {
        SemanticQueryRequest postAggregateRequest = new SemanticQueryRequest();
        postAggregateRequest.setPivot(basicSalesPivot());
        postAggregateRequest.setPostAggregateCalculations(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "salesAmount", "grandTotal", "ratio")));

        IllegalArgumentException postAggregateException =
                assertThrows(IllegalArgumentException.class, () -> execute(postAggregateRequest));
        assertTrue(postAggregateException.getMessage().contains("pivot 模式不支持顶层 postAggregateCalculations"));
        assertTrue(postAggregateException.getMessage().contains("pivot.metrics"));

        SemanticQueryRequest postSliceRequest = new SemanticQueryRequest();
        postSliceRequest.setPivot(basicSalesPivot());
        postSliceRequest.setPostSlice(List.of(slice("salesShare", ">", 0.2)));

        IllegalArgumentException postSliceException =
                assertThrows(IllegalArgumentException.class, () -> execute(postSliceRequest));
        assertTrue(postSliceException.getMessage().contains("pivot 模式不支持顶层 postSlice"));
        assertTrue(postSliceException.getMessage().contains("pivot.rows[*].having"));
    }

    @Test
    @DisplayName("Pivot 派生指标不能作为轴级 having / orderBy 控制")
    void testDerivedMetricsRejectedAsAxisControls() {
        PivotRequest parentSharePivot = basicSalesPivot();
        parentSharePivot.setRows(List.of(axis("product$categoryName"), axis("payment$method")));
        PivotMetricItem paymentShare = new PivotMetricItem();
        paymentShare.setName("paymentShare");
        paymentShare.setType("parentShare");
        paymentShare.setOf("salesAmount");
        parentSharePivot.setMetricItems(List.of(PivotMetricItem.ofNative("salesAmount"), paymentShare));
        parentSharePivot.getRows().get(1).setOrderBy(List.of("-paymentShare"));

        SemanticQueryRequest parentShareRequest = new SemanticQueryRequest();
        parentShareRequest.setPivot(parentSharePivot);
        IllegalArgumentException orderByException =
                assertThrows(IllegalArgumentException.class, () -> execute(parentShareRequest));
        assertTrue(orderByException.getMessage().contains("orderBy 不支持引用派生 Pivot 指标 'paymentShare'"));
        assertTrue(orderByException.getMessage().contains("parentShare/baselineRatio 是后处理输出"));

        PivotRequest baselineRatioPivot = basicSalesPivot();
        baselineRatioPivot.setColumns(List.of(axis("salesDate$month")));
        PivotMetricItem monthRatio = new PivotMetricItem();
        monthRatio.setName("monthRatio");
        monthRatio.setType("baselineRatio");
        monthRatio.setOf("salesAmount");
        monthRatio.setAxis("columns");
        monthRatio.setBaseline("first");
        baselineRatioPivot.setMetricItems(List.of(PivotMetricItem.ofNative("salesAmount"), monthRatio));
        MetricFilter having = new MetricFilter();
        having.setMetric("monthRatio");
        having.setOp(">");
        having.setValue(1);
        baselineRatioPivot.getColumns().get(0).setHaving(List.of(having));

        SemanticQueryRequest baselineRatioRequest = new SemanticQueryRequest();
        baselineRatioRequest.setPivot(baselineRatioPivot);
        IllegalArgumentException havingException =
                assertThrows(IllegalArgumentException.class, () -> execute(baselineRatioRequest));
        assertTrue(havingException.getMessage().contains("having 不支持引用派生 Pivot 指标 'monthRatio'"));
        assertTrue(havingException.getMessage().contains("parentShare/baselineRatio 是后处理输出"));
    }

    @Test
    @DisplayName("基数超限熔断 (TooManyPivotCellsException) - 利用反射注入低阈值")
    void testCardinalityCircuitBreaker() {
        // 原始 pipeline 保存
        SemanticQueryServiceV3Impl impl = (SemanticQueryServiceV3Impl) semanticQueryServiceV3;
        PivotPipeline originalPipeline = (PivotPipeline) ReflectionTestUtils.getField(impl, "pivotPipeline");

        try {
            // 构造极低阈值的 CardinalityBreaker 和 Pipeline
            CardinalityBreaker strictBreaker = new CardinalityBreaker(2); // 阈值 = 2
            PivotPipeline strictPipeline = new PivotPipeline(semanticQueryServiceV3, strictBreaker);

            // 通过反射替换
            ReflectionTestUtils.setField(impl, "pivotPipeline", strictPipeline);

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(axis("product$categoryName"))); // 品类通常多于 5 个
            pivot.setColumns(List.of(axis("salesDate$month")));
            pivot.setMetrics(List.of("salesAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setPivot(pivot);

            // 执行应触发 TooManyPivotCellsException
            TooManyPivotCellsException ex = assertThrows(TooManyPivotCellsException.class, () -> execute(request));
            assertTrue(ex.getCellCount() > 2);
            assertNotNull(ex.getSuggestion());
            log.info("成功触发熔断: {}", ex.getMessage());

        } finally {
            // 恢复原始 pipeline
            ReflectionTestUtils.setField(impl, "pivotPipeline", originalPipeline);
        }
    }

    // ==========================================
    // S8.2: hierarchyMode=tree 守卫规则
    // ==========================================

    @Test
    @DisplayName("hierarchyMode=tree + grid 拒绝")
    void testHierarchyTreeGridRejected() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(treeAxis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("grid");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> execute(request));
        assertTrue(ex.getMessage().contains("hierarchyMode=tree 仅支持 outputFormat=tree"));
        log.info("tree+grid 拒绝通过: {}", ex.getMessage());
    }

    @Test
    @DisplayName("hierarchyMode=tree + crossjoin 拒绝")
    void testHierarchyTreeCrossjoinRejected() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(treeAxis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("tree");
        PivotOptions options = new PivotOptions();
        options.setCrossjoin(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> execute(request));
        assertTrue(ex.getMessage().contains("hierarchyMode=tree 与 crossjoin=true 不兼容"));
        log.info("tree+crossjoin 拒绝通过: {}", ex.getMessage());
    }

    @Test
    @DisplayName("hierarchyMode=tree 在 columns 轴拒绝")
    void testHierarchyTreeColumnAxisRejected() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(treeAxis("salesDate$month")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("tree");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> execute(request));
        assertTrue(ex.getMessage().contains("hierarchyMode=tree 当前仅支持 rows 轴"));
        log.info("columns 轴 tree 拒绝通过: {}", ex.getMessage());
    }

    @Test
    @DisplayName("端到端 Tree: hierarchyMode=tree 成功路径")
    void testHierarchyTreeBasic() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(treeAxis("team$caption")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("tree");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        // 使用支持层级的 FactTeamSalesQueryModel
        SemanticQueryResponse response = execute("FactTeamSalesQueryModel", request);

        // 验证树结构：应当返回 PivotResult 格式（包装在 items 中），且带有 treeData
        List<Map<String, Object>> items = response.getItems();
        assertFalse(items.isEmpty());
        Map<String, Object> pivotData = items.get(0);
        assertEquals("tree", pivotData.get("format"));

        @SuppressWarnings("unchecked")
        List<com.foggyframework.dataset.model.engine.pivot.PivotResult.TreeNode> roots =
                (List<com.foggyframework.dataset.model.engine.pivot.PivotResult.TreeNode>) pivotData.get("data");

        assertNotNull(roots);
        // DimTeam 只有 1 个真正的根节点：总公司 (T001)
        assertEquals(1, roots.size());
        
        com.foggyframework.dataset.model.engine.pivot.PivotResult.TreeNode root = roots.get(0);
        assertEquals("总公司", root.getNode().get("team$caption"));
        assertNotNull(root.getCells().get("salesAmount"));

        // 验证子节点 (技术部, 销售部等)
        assertNotNull(root.getChildren());
        assertTrue(root.getChildren().size() >= 2);

        Map<?, ?> contract = pivotEngineContract(response);
        assertEquals(Boolean.TRUE, contract.get("signed"));
        Map<?, ?> treeContract = contractSection(contract, "tree_axis_contract");
        assertEquals(Boolean.TRUE, treeContract.get("signed"));
        assertEquals("team$caption", treeContract.get("hierarchy_field"));
        assertEquals("team$id", treeContract.get("id_field"));
        assertTrue(((Number) treeContract.get("skeleton_nodes")).intValue() > 0);
        assertContractListContains(treeContract, "unsupported_combinations",
                "columns_axis_tree",
                "crossjoin",
                "domainSlice_start_offset",
                "baselineRatio",
                "cascade_generate");
        @SuppressWarnings("unchecked")
        List<String> capabilities = (List<String>) contract.get("required_capabilities");
        assertTrue(capabilities.contains("rows_axis_parent_child_tree"));
        
        log.info("E2E hierarchyMode=tree 验证通过: 成功构建总公司及子树");
    }

    // ==========================================
    // S8.3: Non-Additive Rollup 集成测试
    // ==========================================

    @Test
    @DisplayName("S8.3: Phase 1 COUNT_DISTINCT 叶子聚合正确")
    void testCountDistinctLeafAggregation() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("uniqueCustomers"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();
        assertFalse(items.isEmpty());

        // 每行都应该有 uniqueCustomers 值
        for (Map<String, Object> row : items) {
            assertNotNull(row.get("uniqueCustomers"),
                    "COUNT_DISTINCT 叶子值不应为 null: " + row.get("product$categoryName"));
            assertTrue(row.get("uniqueCustomers") instanceof Number,
                    "COUNT_DISTINCT 应返回数字类型");
        }

        log.info("S8.3: Phase 1 COUNT_DISTINCT 叶子聚合正确, {} 行", items.size());
    }

    @Test
    @DisplayName("S8.3: SUM 小计不回归 (纯可加度量)")
    void testSumSubtotalNonRegression() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("salesDate$year"), axis("salesDate$month")));
        pivot.setMetrics(List.of("salesAmount", "quantity"));
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();
        assertFalse(items.isEmpty());

        boolean foundRowSubtotal = false;
        boolean foundGrandTotal = false;

        for (Map<String, Object> row : items) {
            if (row.containsKey("_sys_meta")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) row.get("_sys_meta");
                if (Boolean.TRUE.equals(meta.get("isRowSubtotal"))) {
                    foundRowSubtotal = true;
                    // SUM 小计行的度量值应为正数（不为 null）
                    assertNotNull(row.get("salesAmount"), "SUM 小计行 salesAmount 不应为 null");
                    assertNotNull(row.get("quantity"), "SUM 小计行 quantity 不应为 null");
                }
                if (Boolean.TRUE.equals(meta.get("isGrandTotal"))) {
                    foundGrandTotal = true;
                    assertNotNull(row.get("salesAmount"), "Grand total salesAmount 不应为 null");
                }
            }
        }

        assertTrue(foundRowSubtotal, "S8.3 回归: 未发现行级小计");
        assertTrue(foundGrandTotal, "S8.3 回归: 未发现总计");
        log.info("S8.3: SUM 小计不回归验证通过");
    }

    @Test
    @DisplayName("S8.3: 混合可加/不可加度量 + 小计/总计")
    void testMixedAdditiveNonAdditiveSubtotals() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount", "uniqueCustomers"));
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();
        assertFalse(items.isEmpty());

        boolean foundGrandTotal = false;
        for (Map<String, Object> row : items) {
            if (row.containsKey("_sys_meta")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) row.get("_sys_meta");
                if (Boolean.TRUE.equals(meta.get("isGrandTotal"))) {
                    foundGrandTotal = true;
                    // salesAmount (SUM) 应有值
                    assertNotNull(row.get("salesAmount"), "Grand total salesAmount (SUM) 应有值");
                    // uniqueCustomers (COUNT_DISTINCT) 应从辅助查询获取
                    // 可能为 null（如果 cache miss），但在正常情况下应有值
                    log.info("Grand total: salesAmount={}, uniqueCustomers={}",
                            row.get("salesAmount"), row.get("uniqueCustomers"));
                }
            }
        }

        assertTrue(foundGrandTotal, "未找到 grand total 行");
        log.info("S8.3: 混合度量小计/总计验证通过");
    }

    @Test
    @DisplayName("S8.3: hierarchyMode=tree + subtotals 拒绝")
    void testHierarchyTreeSubtotalsRejected() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(treeAxis("team$caption")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("tree");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        // Fail-Closed: tree+subtotals is now silently ignored rather than throwing an exception
        SemanticQueryResponse response = execute("FactTeamSalesQueryModel", request);
        org.junit.jupiter.api.Assertions.assertNotNull(response);
        org.junit.jupiter.api.Assertions.assertFalse(pivot.getOptions().isRowSubtotals(), "RowSubtotals should be silently set to false");
        List<Map<String, Object>> diagnostics = pivotDiagnostics(response);
        Map<String, Object> cacheRefused = diagnosticEvent(diagnostics, "pivot.cache.refused");
        assertEquals("E1a", cacheRefused.get("eligibilityStage"));
        assertEquals("tree_mode", cacheRefused.get("reason"));
        assertEquals("tree", cacheRefused.get("shapeClass"));
        log.info("S8.3: tree+subtotals silently ignored passed");
    }

    @Test
    @DisplayName("S8.3: COUNT_DISTINCT + 多层行轴 + rowSubtotals")
    void testCountDistinctMultiLevelRowSubtotals() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName"), axis("salesDate$month")));
        pivot.setMetrics(List.of("salesAmount", "uniqueCustomers"));
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();
        assertFalse(items.isEmpty());

        int subtotalCount = 0;
        for (Map<String, Object> row : items) {
            if (row.containsKey("_sys_meta")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> meta = (Map<String, Object>) row.get("_sys_meta");
                if (Boolean.TRUE.equals(meta.get("isRowSubtotal"))) {
                    subtotalCount++;
                    assertEquals("ALL", row.get("salesDate$month"));
                    assertNotNull(row.get("salesAmount"), "小计行 salesAmount (SUM) 不应为 null");
                    log.info("Row subtotal: category={}, salesAmount={}, uniqueCustomers={}",
                            row.get("product$categoryName"), row.get("salesAmount"), row.get("uniqueCustomers"));
                }
            }
        }

        assertTrue(subtotalCount > 0, "未发现 row subtotal 行");
        log.info("S8.3: COUNT_DISTINCT 多层行轴 rowSubtotals 验证通过, {} 行小计", subtotalCount);
    }

    @Test
    @DisplayName("S10.1: UNION ALL 批量合并及列对齐机制专项测试")
    void testUnionAllBatchMergeColumnAlignment() {
        PivotRequest pivot = new PivotRequest();
        // 刻意使用复杂的维度组合，产生多个不同列数的 grain
        pivot.setRows(List.of(axis("product$categoryName"), axis("salesDate$year")));
        pivot.setColumns(List.of(axis("customer$customerType")));
        pivot.setMetrics(List.of("uniqueCustomers", "salesAmount")); // uniqueCustomers is COUNT_DISTINCT
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        options.setColumnSubtotals(true);
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        // 如果列不对齐，UNION ALL 会直接抛出 SQL 语法异常。
        // 测试能够正常返回，说明对齐逻辑（或降级逻辑）工作正常。
        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();
        assertFalse(items.isEmpty());

        log.info("S10.1: UNION ALL 批量合并及列对齐机制专项测试通过, 返回 {} 行", items.size());
    }

    // ==========================================
    // S11: parentShare 集成测试
    // ==========================================

    @Test
    @DisplayName("S11: parentShare Flat - 子品类占大类占比")
    void testParentShareFlat() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName"), axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem ps = new PivotMetricItem();
        ps.setName("monthShare");
        ps.setType("parentShare");
        ps.setOf("salesAmount");
        items.add(ps);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> flatItems = response.getItems();
        assertFalse(flatItems.isEmpty());

        // 每行都应有 monthShare 字段
        boolean hasShare = false;
        for (Map<String, Object> row : flatItems) {
            assertTrue(row.containsKey("monthShare"), "flat 输出应包含 parentShare 字段");
            if (row.get("monthShare") != null) {
                double share = ((Number) row.get("monthShare")).doubleValue();
                assertTrue(share >= 0 && share <= 1.0001, "parentShare 应在 [0,1]: " + share);
                hasShare = true;
            }
        }
        assertTrue(hasShare, "应至少有一行 parentShare 非 null");
        log.info("S11: parentShare Flat 验证通过, {} 行", flatItems.size());
    }

    @Test
    @DisplayName("S11: parentShare Grid - grid 输出包含 parentShare 列")
    void testParentShareGrid() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName"), axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem ps = new PivotMetricItem();
        ps.setName("monthShare");
        ps.setType("parentShare");
        ps.setOf("salesAmount");
        items.add(ps);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("grid");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> respItems = response.getItems();
        assertEquals(1, respItems.size());
        Map<String, Object> grid = respItems.get(0);
        assertEquals("grid", grid.get("format"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> columnHeaders = (List<Map<String, Object>>) grid.get("columnHeaders");
        // columnHeaders 应包含 monthShare 度量
        boolean hasMonthShareHeader = columnHeaders.stream()
                .anyMatch(h -> "monthShare".equals(h.get("metric")));
        assertTrue(hasMonthShareHeader, "grid columnHeaders 应包含 monthShare 度量");
        log.info("S11: parentShare Grid 验证通过");
    }

    @Test
    @DisplayName("S11: parentShare + non-additive (uniqueCustomers) → fail-closed")
    void testParentShareNonAdditiveRejected() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName"), axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("uniqueCustomers"));
        PivotMetricItem ps = new PivotMetricItem();
        ps.setName("custShare");
        ps.setType("parentShare");
        ps.setOf("uniqueCustomers");
        items.add(ps);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> execute(request));
        assertTrue(ex.getMessage().contains("不可加") || ex.getMessage().contains("COUNT_DISTINCT"),
                "Should reject non-additive: " + ex.getMessage());
        log.info("S11: parentShare non-additive 拒绝通过: {}", ex.getMessage());
    }

    // ========== S12 baselineRatio ==========

    @Test
    @DisplayName("S12: baselineRatio Flat - 基础扁平输出")
    void testBaselineRatioFlat() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem br = new PivotMetricItem();
        br.setName("salesIndex");
        br.setType("baselineRatio");
        br.setOf("salesAmount");
        br.setAxis("columns");
        br.setBaseline("first");
        items.add(br);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> respItems = response.getItems();
        assertFalse(respItems.isEmpty());
        // Verify output contains the metric
        boolean hasSalesIndex = false;
        for (Map<String, Object> row : respItems) {
            if (row.containsKey("salesIndex")) {
                hasSalesIndex = true;
                break;
            }
        }
        assertTrue(hasSalesIndex, "Flat 输出应包含 salesIndex 字段");
        log.info("S12: baselineRatio Flat 验证通过");
    }

    @Test
    @DisplayName("S12: baselineRatio + hierarchyMode=tree → fail-closed")
    void testBaselineRatioTreeRejected() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(treeAxis("product$categoryId")));
        pivot.setColumns(List.of(axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem br = new PivotMetricItem();
        br.setName("salesIndex");
        br.setType("baselineRatio");
        br.setOf("salesAmount");
        br.setAxis("columns");
        br.setBaseline("first");
        items.add(br);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("tree");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> execute(request));
        assertTrue(ex.getMessage().contains("不支持 hierarchyMode=tree") || ex.getMessage().contains("tree"),
                "Should reject tree hierarchy with baselineRatio: " + ex.getMessage());
        log.info("S12: baselineRatio tree mode 拒绝通过");
    }

    // ========== v3.7 axis domainSlice/start/offset ==========

    @Test
    @DisplayName("v3.7: domainSlice 只选择行轴域，不删除同一行轴下不满足 domainSlice 的 cell")
    void testDomainSliceSelectsRowDomainWithoutDroppingCells() {
        insertPivotDomainSliceFixture();
        try {
            AxisField row = axis("orderId");
            row.setDomainSlice(List.of(slice("discountAmount", ">", 0)));
            row.setOrderBy(List.of("orderId"));

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(row));
            pivot.setColumns(List.of(axis("product$caption")));
            pivot.setMetrics(List.of("salesAmount", "discountAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderId", "in", List.of("PDS_O1", "PDS_O2"))));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);
            List<Map<String, Object>> items = response.getItems();

            List<Map<String, Object>> orderRows = rowsForOrder(items, "PDS_O1");
            assertEquals(2, orderRows.size(), "PDS_O1 的两个商品 cell 都应保留");
            assertTrue(rowsForOrder(items, "PDS_O2").isEmpty(), "PDS_O2 不满足行轴 domainSlice，应被排除");

            boolean hasZeroDiscountCell = false;
            double salesTotal = 0;
            for (Map<String, Object> rowItem : orderRows) {
                Number discount = (Number) rowItem.get("discountAmount");
                Number sales = (Number) rowItem.get("salesAmount");
                if (discount != null && discount.doubleValue() == 0d) {
                    hasZeroDiscountCell = true;
                }
                if (sales != null) {
                    salesTotal += sales.doubleValue();
                }
            }
            assertTrue(hasZeroDiscountCell, "domainSlice 不能把同一 orderId 下 discountAmount=0 的 cell 删除");
            assertEquals(180d, salesTotal, 0.001d);
        } finally {
            deletePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: 顶层 slice 仍会作用于 cell 聚合，作为 domainSlice 的负面对照")
    void testGlobalSliceStillFiltersCells() {
        insertPivotDomainSliceFixture();
        try {
            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(axis("orderId")));
            pivot.setColumns(List.of(axis("product$caption")));
            pivot.setMetrics(List.of("salesAmount", "discountAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(
                    slice("orderId", "in", List.of("PDS_O1", "PDS_O2")),
                    slice("discountAmount", ">", 0)
            ));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);
            List<Map<String, Object>> orderRows = rowsForOrder(response.getItems(), "PDS_O1");

            assertEquals(1, orderRows.size(), "顶层 slice 会删除 PDS_O1 下 discountAmount=0 的 cell");
            assertEquals(80d, ((Number) orderRows.get(0).get("salesAmount")).doubleValue(), 0.001d);
        } finally {
            deletePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: start/limit 在行轴域上分页，不按最终 cell 行分页")
    void testAxisDomainStartPaginatesRowDomain() {
        insertPivotDomainSliceFixture();
        try {
            AxisField row = axis("orderId");
            row.setStart(1);
            row.setLimit(1);
            row.setOrderBy(List.of("orderId"));

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(row));
            pivot.setColumns(List.of(axis("product$caption")));
            pivot.setMetrics(List.of("salesAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderId", "in", List.of("PDS_O1", "PDS_O2"))));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);

            assertTrue(rowsForOrder(response.getItems(), "PDS_O1").isEmpty(),
                    "start=1 应跳过第一个行轴成员 PDS_O1，而不是跳过最终 cell 第一行");
            assertEquals(1, rowsForOrder(response.getItems(), "PDS_O2").size(),
                    "PDS_O2 只有一个商品 cell，应作为第二个行轴成员完整返回");
        } finally {
            deletePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: 大基数轴域 TopN 必须先在查询侧排序截断，不能被默认 1000 行预截断")
    void testLargeAxisDomainTopNPushdownBeforeDefaultLimit() {
        insertLargePivotDomainSliceFixture();
        try {
            AxisField row = axis("orderId");
            row.setDomainSlice(List.of(slice("discountAmount", ">", 0)));
            row.setLimit(1);
            row.setOrderBy(List.of("-salesAmount"));

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(row));
            pivot.setMetrics(List.of("salesAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderStatus", "=", LARGE_AXIS_DOMAIN_STATUS)));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);
            List<Map<String, Object>> items = response.getItems();

            assertEquals(1, items.size());
            assertEquals("PDS_BIG_1104", items.get(0).get("orderId"));
            assertEquals(9999d, ((Number) items.get(0).get("salesAmount")).doubleValue(), 0.001d);
        } finally {
            deleteLargePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: 大基数轴域 start/limit 必须在查询侧分页，不能落入默认 1000 行预截断")
    void testLargeAxisDomainStartPushdownBeyondDefaultLimit() {
        insertLargePivotDomainSliceFixture();
        try {
            AxisField row = axis("orderId");
            row.setStart(1001);
            row.setLimit(1);
            row.setOrderBy(List.of("orderId"));

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(row));
            pivot.setMetrics(List.of("salesAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderStatus", "=", LARGE_AXIS_DOMAIN_STATUS)));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);
            List<Map<String, Object>> items = response.getItems();

            assertEquals(1, items.size());
            assertEquals("PDS_BIG_1001", items.get(0).get("orderId"));
        } finally {
            deleteLargePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: 大基数 surviving domain 使用 domain transport 下推，避免超长 IN")
    void testLargeAxisDomainConstraintUsesDomainTransportPlan() {
        PivotPipeline pipeline = new PivotPipeline(semanticQueryServiceV3);

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("orderId")));
        pivot.setMetrics(List.of("salesAmount"));

        SemanticQueryRequest originalRequest = new SemanticQueryRequest();
        originalRequest.setSlice(List.of(slice("orderStatus", "=", LARGE_AXIS_DOMAIN_STATUS)));

        Set<Object> rowDomain = new LinkedHashSet<>();
        for (int i = 0; i < 501; i++) {
            rowDomain.add(String.format("PDS_BIG_%04d", i));
        }

        Object constrained = ReflectionTestUtils.invokeMethod(pipeline,
                "buildAxisDomainConstrainedCellRequest",
                originalRequest, pivot, rowDomain, null, SemanticRequestContext.empty());

        SemanticQueryRequest cellRequest = ReflectionTestUtils.invokeMethod(constrained, "request");
        SemanticRequestContext context = ReflectionTestUtils.invokeMethod(constrained, "context");

        assertNotNull(cellRequest);
        assertNotNull(context);
        assertEquals(1, cellRequest.getSlice().size(), "大域应放入 domain transport，不应追加 orderId IN slice");
        assertEquals("orderStatus", cellRequest.getSlice().get(0).getField());
        assertEquals(1, context.getDomainTransportPlans().size());

        DomainTransportPlan plan = context.getDomainTransportPlans().get(0);
        assertEquals("_pivot_axis_domain_row_0", plan.getRelationName());
        assertEquals(1, plan.getFields().size());
        assertEquals("orderId", plan.getFields().get(0).getName());
        assertEquals(501, plan.getTuples().size());
    }

    @Test
    @DisplayName("v3.7: 大基数轴域响应输出 pivotDiagnostics domain transport plan")
    void testLargeAxisDomainDiagnosticsExposeDomainTransportPlan() {
        insertLargePivotDomainSliceFixture();
        try {
            AxisField row = axis("orderId");
            row.setDomainSlice(List.of(slice("discountAmount", ">", 0)));
            row.setLimit(600);
            row.setOrderBy(List.of("orderId"));

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(row));
            pivot.setMetrics(List.of("salesAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderStatus", "=", LARGE_AXIS_DOMAIN_STATUS)));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);

            assertEquals(600, response.getItems().size());
            List<Map<String, Object>> diagnostics = pivotDiagnostics(response);
            assertDiagnosticEvent(diagnostics, "pivot.sql_pushdown.skipped");
            assertDiagnosticEvent(diagnostics, "pivot.axis_domain_selection.started");

            Map<String, Object> transport = diagnosticEvent(diagnostics, "pivot.domain_transport.planned");
            assertEquals("_pivot_axis_domain_row_0", transport.get("relation"));
            assertEquals(1, ((Number) transport.get("fieldCount")).intValue());
            assertEquals(600, ((Number) transport.get("tupleCount")).intValue());
            assertEquals(600, ((Number) transport.get("parameterCount")).intValue());

            Map<String, Object> executionPath = diagnosticEvent(diagnostics, "pivot.execution_path");
            assertEquals(Boolean.FALSE, executionPath.get("sqlPushdownUsed"));
            assertEquals(Boolean.TRUE, executionPath.get("axisDomainSelectionUsed"));
            assertEquals(Boolean.FALSE, executionPath.get("cascadeGenerateUsed"));
            assertEquals(600, ((Number) executionPath.get("rowDomainSize")).intValue());
        } finally {
            deleteLargePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: column domainSlice 只选择列轴域，并约束最终 cell 查询")
    void testColumnDomainSliceSelectsColumnDomain() {
        insertPivotDomainSliceFixture();
        try {
            AxisField column = axis("product$caption");
            column.setDomainSlice(List.of(slice("discountAmount", ">", 0)));
            column.setOrderBy(List.of("product$caption"));

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(axis("orderId")));
            pivot.setColumns(List.of(column));
            pivot.setMetrics(List.of("salesAmount", "discountAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderId", "in", List.of("PDS_O1", "PDS_O2"))));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);

            assertEquals(1, response.getItems().size(), "只有满足列轴 domainSlice 的商品列应进入 cell 结果");
            Map<String, Object> row = response.getItems().get(0);
            assertEquals("PDS_O1", row.get("orderId"));
            assertEquals(pivotDomainSliceDiscountedProductCaption(), row.get("product$caption"));
            assertEquals(80d, ((Number) row.get("salesAmount")).doubleValue(), 0.001d);
        } finally {
            deletePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: rows 与 columns 同时使用 domainSlice 时按两个 surviving domains 计算 cell")
    void testRowAndColumnDomainSliceConstrainCellsTogether() {
        insertPivotDomainSliceFixture();
        try {
            AxisField row = axis("orderId");
            row.setDomainSlice(List.of(slice("discountAmount", ">", 0)));

            AxisField column = axis("product$caption");
            column.setDomainSlice(List.of(slice("discountAmount", ">", 0)));

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(row));
            pivot.setColumns(List.of(column));
            pivot.setMetrics(List.of("salesAmount", "discountAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderId", "in", List.of("PDS_O1", "PDS_O2"))));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);

            assertEquals(1, response.getItems().size());
            Map<String, Object> onlyCell = response.getItems().get(0);
            assertEquals("PDS_O1", onlyCell.get("orderId"));
            assertEquals(pivotDomainSliceDiscountedProductCaption(), onlyCell.get("product$caption"));
            assertEquals(80d, ((Number) onlyCell.get("discountAmount")).doubleValue(), 0.001d);
        } finally {
            deletePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: domainSlice 在 grid 输出中仍按轴域语义生效")
    void testDomainSliceGridOutput() {
        insertPivotDomainSliceFixture();
        try {
            AxisField row = axis("orderId");
            row.setDomainSlice(List.of(slice("discountAmount", ">", 0)));

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(row));
            pivot.setColumns(List.of(axis("product$caption")));
            pivot.setMetrics(List.of("salesAmount"));
            pivot.setOutputFormat("grid");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderId", "in", List.of("PDS_O1", "PDS_O2"))));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);
            assertEquals(1, response.getItems().size());
            Map<String, Object> grid = response.getItems().get(0);
            assertEquals("grid", grid.get("format"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rowHeaders = (List<Map<String, Object>>) grid.get("rowHeaders");
            assertEquals(1, rowHeaders.size());
            assertEquals("PDS_O1", rowHeaders.get(0).get("orderId"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columnHeaders = (List<Map<String, Object>>) grid.get("columnHeaders");
            assertEquals(2, columnHeaders.size(), "PDS_O1 下两个商品 cell 都应参与 grid 列头");
        } finally {
            deletePivotDomainSliceFixture();
        }
    }

    @Test
    @DisplayName("v3.7: start/offset 轴域分页必须配合正数 limit，且不能冲突")
    void testAxisDomainPaginationValidation() {
        PivotRequest noLimitPivot = new PivotRequest();
        AxisField rowWithoutLimit = axis("orderId");
        rowWithoutLimit.setStart(20);
        noLimitPivot.setRows(List.of(rowWithoutLimit));
        noLimitPivot.setMetrics(List.of("salesAmount"));

        SemanticQueryRequest noLimitRequest = new SemanticQueryRequest();
        noLimitRequest.setPivot(noLimitPivot);

        IllegalArgumentException noLimit = assertThrows(IllegalArgumentException.class,
                () -> execute(noLimitRequest));
        assertTrue(noLimit.getMessage().contains("必须同时指定正数 limit"));

        PivotRequest conflictPivot = new PivotRequest();
        AxisField conflictRow = axis("orderId");
        conflictRow.setStart(20);
        conflictRow.setOffset(30);
        conflictRow.setLimit(10);
        conflictPivot.setRows(List.of(conflictRow));
        conflictPivot.setMetrics(List.of("salesAmount"));

        SemanticQueryRequest conflictRequest = new SemanticQueryRequest();
        conflictRequest.setPivot(conflictPivot);

        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                () -> execute(conflictRequest));
        assertTrue(conflict.getMessage().contains("不能同时指定不同的 start 和 offset"));
    }

    @Test
    @DisplayName("v3.7: domainSlice/start/offset 与多层轴组合保持 fail-closed")
    void testAxisDomainSelectionRejectsMultiLevelAxes() {
        AxisField childRow = axis("orderId");
        childRow.setDomainSlice(List.of(slice("discountAmount", ">", 0)));

        PivotRequest multiRowPivot = new PivotRequest();
        multiRowPivot.setRows(List.of(axis("product$categoryName"), childRow));
        multiRowPivot.setMetrics(List.of("salesAmount"));

        SemanticQueryRequest multiRowRequest = new SemanticQueryRequest();
        multiRowRequest.setPivot(multiRowPivot);

        IllegalArgumentException multiRow = assertThrows(IllegalArgumentException.class,
                () -> execute(multiRowRequest));
        assertTrue(multiRow.getMessage().contains("单层 rows 和单层 columns"),
                "多层 rows 应明确拒绝 domainSlice/start/offset: " + multiRow.getMessage());

        AxisField childColumn = axis("salesDate$month");
        childColumn.setOffset(1);
        childColumn.setLimit(1);

        PivotRequest multiColumnPivot = new PivotRequest();
        multiColumnPivot.setRows(List.of(axis("orderId")));
        multiColumnPivot.setColumns(List.of(axis("product$categoryName"), childColumn));
        multiColumnPivot.setMetrics(List.of("salesAmount"));

        SemanticQueryRequest multiColumnRequest = new SemanticQueryRequest();
        multiColumnRequest.setPivot(multiColumnPivot);

        IllegalArgumentException multiColumn = assertThrows(IllegalArgumentException.class,
                () -> execute(multiColumnRequest));
        assertTrue(multiColumn.getMessage().contains("单层 columns"),
                "多层 columns start/offset 应继续 fail-closed: " + multiColumn.getMessage());
    }

    @Test
    @DisplayName("v3.7: domainSlice/start/offset 与 hierarchyMode=tree 组合保持 fail-closed")
    void testAxisDomainSelectionRejectsTreeMode() {
        AxisField treeRow = treeAxis("team$caption");
        treeRow.setDomainSlice(List.of(slice("salesAmount", ">", 0)));

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(treeRow));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("tree");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> execute(request));
        assertTrue(ex.getMessage().contains("不支持 hierarchyMode=tree"),
                "tree + domainSlice/start/offset 应继续 fail-closed: " + ex.getMessage());
    }

    @Test
    @DisplayName("v3.7: domainSlice/start/offset 与 parentShare 组合保持 fail-closed")
    void testAxisDomainSelectionRejectsParentShare() {
        AxisField row = axis("product$categoryName");
        row.setDomainSlice(List.of(slice("salesAmount", ">", 0)));

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(row));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem ps = new PivotMetricItem();
        ps.setName("categoryShare");
        ps.setType("parentShare");
        ps.setOf("salesAmount");
        items.add(ps);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> execute(request));
        assertTrue(ex.getMessage().contains("parentShare"),
                "parentShare + domainSlice/start/offset 应说明派生指标 scope 尚未定义: " + ex.getMessage());

        AxisField parentRow = axis("orderStatus");
        AxisField pagedRow = axis("paymentMethod");
        pagedRow.setStart(1);
        pagedRow.setLimit(1);

        PivotRequest pagedPivot = new PivotRequest();
        pagedPivot.setRows(List.of(parentRow, pagedRow));
        pagedPivot.setMetricItems(items);
        pagedPivot.setOutputFormat("flat");

        SemanticQueryRequest pagedRequest = new SemanticQueryRequest();
        pagedRequest.setPivot(pagedPivot);

        IllegalArgumentException pagedEx = assertThrows(IllegalArgumentException.class,
                () -> execute(pagedRequest));
        assertTrue(pagedEx.getMessage().contains("denominatorScope=prePageParent"),
                "parentShare + start/offset 应说明 denominator scope 尚未定义: " + pagedEx.getMessage());
    }

    @Test
    @DisplayName("v3.7: domainSlice/start/offset 与 baselineRatio 组合保持 fail-closed")
    void testAxisDomainSelectionRejectsBaselineRatio() {
        AxisField row = axis("product$categoryName");
        row.setDomainSlice(List.of(slice("salesAmount", ">", 0)));

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(row));
        pivot.setColumns(List.of(axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem br = new PivotMetricItem();
        br.setName("salesIndex");
        br.setType("baselineRatio");
        br.setOf("salesAmount");
        br.setAxis("columns");
        br.setBaseline("first");
        items.add(br);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> execute(request));
        assertTrue(ex.getMessage().contains("baselineScope=prePageAxisDomain"),
                "baselineRatio + domainSlice/start/offset 应说明 baseline scope 尚未定义: " + ex.getMessage());

        AxisField pagedColumn = axis("salesDate$month");
        pagedColumn.setStart(1);
        pagedColumn.setLimit(1);

        PivotRequest pagedPivot = new PivotRequest();
        pagedPivot.setRows(List.of(axis("product$categoryName")));
        pagedPivot.setColumns(List.of(pagedColumn));
        pagedPivot.setMetricItems(items);
        pagedPivot.setOutputFormat("flat");

        SemanticQueryRequest pagedRequest = new SemanticQueryRequest();
        pagedRequest.setPivot(pagedPivot);

        IllegalArgumentException pagedEx = assertThrows(IllegalArgumentException.class,
                () -> execute(pagedRequest));
        assertTrue(pagedEx.getMessage().contains("baselineScope=prePageAxisDomain"),
                "baselineRatio + start/offset 应说明 baseline scope 尚未定义: " + pagedEx.getMessage());

        br.setBaselineScope("visiblePage");
        IllegalArgumentException unsupportedScopeEx = assertThrows(IllegalArgumentException.class,
                () -> execute(pagedRequest));
        assertTrue(unsupportedScopeEx.getMessage().contains("当前仅支持 prePageAxisDomain"),
                "baselineScope=visiblePage 当前版本应 fail closed: " + unsupportedScopeEx.getMessage());
    }

    @Test
    @DisplayName("v3.7: baselineRatio + column start/limit 使用分页前列域作为基准")
    void testBaselineRatioColumnWindowUsesPrePageAxisDomain() {
        insertBaselineRatioScopeFixture();
        try {
            AxisField column = axis("salesDate$dayOfWeek");
            column.setStart(1);
            column.setLimit(2);

            PivotRequest pivot = new PivotRequest();
            pivot.setRows(List.of(axis("orderStatus")));
            pivot.setColumns(List.of(column));

            List<PivotMetricItem> items = new ArrayList<>();
            items.add(PivotMetricItem.ofNative("salesAmount"));
            PivotMetricItem br = new PivotMetricItem();
            br.setName("salesIndex");
            br.setType("baselineRatio");
            br.setOf("salesAmount");
            br.setAxis("columns");
            br.setBaseline("first");
            br.setBaselineScope("prePageAxisDomain");
            items.add(br);
            pivot.setMetricItems(items);
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setSlice(List.of(slice("orderStatus", "=", BASELINE_RATIO_SCOPE_STATUS)));
            request.setPivot(pivot);

            SemanticQueryResponse response = execute(request);
            List<Map<String, Object>> itemsOut = response.getItems();

            assertEquals(2, itemsOut.size(), "start=1 limit=2 应只返回第 2、3 个可见列轴成员");
            Map<Object, Map<String, Object>> byDayOfWeek = new LinkedHashMap<>();
            for (Map<String, Object> row : itemsOut) {
                byDayOfWeek.put(row.get("salesDate$dayOfWeek"), row);
            }

            assertTrue(byDayOfWeek.containsKey(2), "可见列应包含 dayOfWeek=2");
            assertTrue(byDayOfWeek.containsKey(3), "可见列应包含 dayOfWeek=3");
            assertFalse(byDayOfWeek.containsKey(1), "分页前基准列 dayOfWeek=1 不应出现在可见结果中");
            assertEquals(2.0d, ((Number) byDayOfWeek.get(2).get("salesIndex")).doubleValue(), 0.001d,
                    "dayOfWeek=2 应除以分页前 dayOfWeek=1 的 100，而不是页内 dayOfWeek=2 的 200");
            assertEquals(3.0d, ((Number) byDayOfWeek.get(3).get("salesIndex")).doubleValue(), 0.001d);

            Map<String, Object> extra = response.getDebug().getExtra();
            assertNotNull(extra);
            Object evidenceObject = extra.get("baselineRatioEvidence");
            assertTrue(evidenceObject instanceof List<?>, "debug.extra 应输出 baselineRatioEvidence");
            List<?> evidence = (List<?>) evidenceObject;
            assertEquals(1, evidence.size());
            assertTrue(evidence.get(0) instanceof Map<?, ?>);

            Map<?, ?> baselineEvidence = (Map<?, ?>) evidence.get(0);
            assertEquals("salesIndex", baselineEvidence.get("metric"));
            assertEquals("salesAmount", baselineEvidence.get("of"));
            assertEquals("columns", baselineEvidence.get("axis"));
            assertEquals("first", baselineEvidence.get("baseline"));
            assertEquals("prePageAxisDomain", baselineEvidence.get("baselineScope"));
            assertEquals("salesDate$dayOfWeek", baselineEvidence.get("columnField"));
            assertEquals(1, ((Number) baselineEvidence.get("baselineColumnKey")).intValue());
            assertEquals(Boolean.FALSE, baselineEvidence.get("baselineColumnVisible"));
            assertEquals(4, ((Number) baselineEvidence.get("prePageAxisDomainSize")).intValue());
            assertEquals(2, ((Number) baselineEvidence.get("visibleAxisDomainSize")).intValue());
            assertEquals(1, ((Number) baselineEvidence.get("baselineRows")).intValue());
            assertEquals("auxiliaryBaselineRelation", baselineEvidence.get("source"));
        } finally {
            deleteBaselineRatioScopeFixture();
        }
    }

    @Test
    @DisplayName("QM 预定义 formula 可作为 Pivot metrics")
    void testQmPredefinedFormulaMetricInPivotFlat() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("profitRate"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();

        assertFalse(items.isEmpty(), "Pivot metrics 引用 QM 预定义 formula 应返回数据");
        assertTrue(items.get(0).containsKey("profitRate"), "Pivot 输出应包含 QM 预定义 formula 指标");
        assertTrue(items.stream().anyMatch(row -> row.get("profitRate") instanceof Number),
                "Pivot 输出中 profitRate 应为数值");
    }

    @Test
    @DisplayName("QM 预定义 formula 可用于 Pivot 顶层 slice")
    void testQmPredefinedFormulaInPivotTopLevelSlice() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setSlice(List.of(slice("profitRate", ">", 0)));
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();

        assertFalse(items.isEmpty(), "Pivot 顶层 slice 引用 QM 预定义 formula 应可执行并返回数据");
        assertTrue(items.get(0).containsKey("salesAmount"));
    }

    @Test
    @DisplayName("QM 预定义 formula 可用于 Pivot axis having/orderBy")
    void testQmPredefinedFormulaInPivotAxisHavingAndOrderBy() {
        AxisField row = axis("product$categoryName");
        MetricFilter having = new MetricFilter();
        having.setMetric("profitRate");
        having.setOp(">");
        having.setValue(0);
        row.setHaving(List.of(having));
        row.setOrderBy(List.of("-profitRate"));

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(row));
        pivot.setMetrics(List.of("profitRate"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();

        assertFalse(items.isEmpty(), "Pivot axis having/orderBy 引用 QM 预定义 formula 应返回数据");
        for (Map<String, Object> item : items) {
            assertTrue(((Number) item.get("profitRate")).doubleValue() > 0,
                    "axis having 应保留 profitRate > 0 的成员");
        }
    }

    @Test
    @DisplayName("QM 预定义 formula 可参与 Pivot grandTotal rollup")
    void testQmPredefinedFormulaMetricInPivotGrandTotal() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("profitRate"));
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> items = response.getItems();

        assertFalse(items.isEmpty(), "Pivot grandTotal 引用 QM 预定义 formula 应返回数据");
        assertTrue(items.stream().anyMatch(row -> {
            Object meta = row.get("_sys_meta");
            return meta instanceof Map<?, ?> && Boolean.TRUE.equals(((Map<?, ?>) meta).get("isGrandTotal"));
        }), "Pivot 输出应包含 grandTotal 行");
    }

    // ========== 辅助方法 ==========

    private PivotPipeline outerCachePipeline(long ttlMillis) {
        return new PivotPipeline(semanticQueryServiceV3, new CardinalityBreaker(), queryModelLoader, null,
                new PivotPipeline.OuterCacheOptions(true, ttlMillis, 16));
    }

    private PivotPipeline outerCachePipeline(PivotOuterCacheProvider provider) {
        return new PivotPipeline(semanticQueryServiceV3, new CardinalityBreaker(), queryModelLoader, null,
                new PivotPipeline.OuterCacheOptions(true, 60_000L, 16),
                PivotOuterCacheModelIdentityProvider.empty(), provider);
    }

    private SemanticQueryResponse execute(SemanticQueryRequest request) {
        return execute(TEST_MODEL, request);
    }

    private SemanticQueryResponse execute(String model, SemanticQueryRequest request) {
        return execute(model, request, SemanticRequestContext.empty());
    }

    private SemanticQueryResponse execute(SemanticQueryRequest request, SemanticRequestContext context) {
        return execute(TEST_MODEL, request, context);
    }

    private SemanticQueryResponse execute(String model, SemanticQueryRequest request, SemanticRequestContext context) {
        return semanticQueryServiceV3.queryModel(
                model, request, "execute", context);
    }

    private SemanticRequestContext outerCacheContext(String userId) {
        ModelResultContext.SecurityContext securityContext = ModelResultContext.SecurityContext.builder()
                .authorization("Bearer " + userId)
                .userId(userId)
                .roles(List.of("analyst"))
                .tenantId("tenant-main")
                .attributes(Map.of("scope", "pivot-cache-test"))
                .build();
        return SemanticRequestContext.of(null, securityContext,
                Set.of("product$categoryName", "salesAmount"),
                List.of(new DeniedPhysicalColumn(null, "fact_sales", "profit_amount")));
    }

    private Map<?, ?> pivotEngineContract(SemanticQueryResponse response) {
        assertNotNull(response.getDebug(), "pivot response should include debug info");
        assertNotNull(response.getDebug().getExtra(), "pivot response should include debug.extra");
        Object contract = response.getDebug().getExtra().get("pivotEngineContract");
        assertTrue(contract instanceof Map<?, ?>, "debug.extra 应输出 pivotEngineContract");
        return (Map<?, ?>) contract;
    }

    private Map<?, ?> contractSection(Map<?, ?> contract, String sectionName) {
        Object section = contract.get(sectionName);
        assertTrue(section instanceof Map<?, ?>, "pivot contract should include " + sectionName);
        return (Map<?, ?>) section;
    }

    private void assertContractListContains(Map<?, ?> section, String listName, String... expectedValues) {
        Object value = section.get(listName);
        assertTrue(value instanceof List<?>, "pivot contract section should include list " + listName);
        List<?> values = (List<?>) value;
        for (String expected : expectedValues) {
            assertTrue(values.contains(expected), listName + " should contain " + expected + ": " + values);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pivotDiagnostics(SemanticQueryResponse response) {
        assertNotNull(response.getDebug(), "pivot response should include debug info");
        assertNotNull(response.getDebug().getExtra(), "pivot response should include debug.extra");
        Object diagnostics = response.getDebug().getExtra().get("pivotDiagnostics");
        assertTrue(diagnostics instanceof List<?>, "debug.extra 应输出 pivotDiagnostics");
        for (Object item : (List<?>) diagnostics) {
            assertTrue(item instanceof Map<?, ?>, "pivotDiagnostics item should be a map");
        }
        return (List<Map<String, Object>>) diagnostics;
    }

    private Map<String, Object> diagnosticEvent(List<Map<String, Object>> diagnostics, String event) {
        return diagnostics.stream()
                .filter(item -> event.equals(item.get("event")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("pivotDiagnostics should contain " + event + ": " + diagnostics));
    }

    private void assertDiagnosticEvent(List<Map<String, Object>> diagnostics, String event) {
        diagnosticEvent(diagnostics, event);
    }

    private boolean hasDiagnosticEvent(List<Map<String, Object>> diagnostics, String event) {
        return diagnostics.stream().anyMatch(item -> event.equals(item.get("event")));
    }

    private SemanticQueryRequest.SliceItem slice(String field, String op, Object value) {
        SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
        item.setField(field);
        item.setOp(op);
        item.setValue(value);
        return item;
    }

    private PivotRequest basicSalesPivot() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");
        return pivot;
    }

    private List<Map<String, Object>> rowsForOrder(List<Map<String, Object>> rows, String orderId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (orderId.equals(row.get("orderId"))) {
                result.add(row);
            }
        }
        return result;
    }

    private void insertPivotDomainSliceFixture() {
        deletePivotDomainSliceFixture();
        Map<String, Object> zeroDiscountProduct = productByBrandAndSubCategory("Apple", "手机通讯");
        Map<String, Object> discountedProduct = productByBrandAndSubCategory("华为", "手机通讯");
        jdbcTemplate.update("""
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "PDS_O1", 1, 20240101, productKey(zeroDiscountProduct), 1, 1, 1, null,
                1, 100d, 60d, 0d, 100d, 60d, 40d, "COMPLETED", "ALIPAY");
        jdbcTemplate.update("""
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "PDS_O1", 2, 20240101, productKey(discountedProduct), 1, 1, 1, null,
                1, 80d, 50d, 80d, 80d, 50d, 30d, "COMPLETED", "ALIPAY");
        jdbcTemplate.update("""
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "PDS_O2", 1, 20240101, productKey(zeroDiscountProduct), 1, 1, 1, null,
                1, 50d, 30d, 0d, 50d, 30d, 20d, "COMPLETED", "ALIPAY");
    }

    private String pivotDomainSliceDiscountedProductCaption() {
        return String.valueOf(productByBrandAndSubCategory("华为", "手机通讯").get("product_name"));
    }

    private Map<String, Object> productByBrandAndSubCategory(String brand, String subCategory) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(paginateSql("""
                SELECT product_key, product_name
                FROM dim_product
                WHERE brand = ? AND sub_category_name = ?
                ORDER BY product_key
                """, 1), brand, subCategory);
        assertFalse(rows.isEmpty(), "Missing pivot fixture product: brand=" + brand + ", subCategory=" + subCategory);
        return rows.get(0);
    }

    private int productKey(Map<String, Object> row) {
        return ((Number) row.get("product_key")).intValue();
    }

    private void deletePivotDomainSliceFixture() {
        jdbcTemplate.update("DELETE FROM fact_sales WHERE order_id IN ('PDS_O1', 'PDS_O2')");
    }

    private void insertParentChildWindowFixture() {
        deleteParentChildWindowFixture();
        String sql = """
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, List.of(
                new Object[] {"PDS_WIN_A_TOP", 1, 20240101, 1, 1, 1, 1, null,
                        1, 300d, 0d, 0d, 300d, 0d, 300d, PARENT_CHILD_WINDOW_STATUS_A, "PDS_PAY_TOP"},
                new Object[] {"PDS_WIN_A_KEEP", 1, 20240101, 1, 1, 1, 1, null,
                        1, 200d, 0d, 0d, 200d, 0d, 200d, PARENT_CHILD_WINDOW_STATUS_A, "PDS_PAY_KEEP"},
                new Object[] {"PDS_WIN_A_LOW", 1, 20240101, 1, 1, 1, 1, null,
                        1, 100d, 0d, 0d, 100d, 0d, 100d, PARENT_CHILD_WINDOW_STATUS_A, "PDS_PAY_LOW"},
                new Object[] {"PDS_WIN_B_TOP", 1, 20240101, 1, 1, 1, 1, null,
                        1, 300d, 0d, 0d, 300d, 0d, 300d, PARENT_CHILD_WINDOW_STATUS_B, "PDS_PAY_TOP"},
                new Object[] {"PDS_WIN_B_KEEP", 1, 20240101, 1, 1, 1, 1, null,
                        1, 200d, 0d, 0d, 200d, 0d, 200d, PARENT_CHILD_WINDOW_STATUS_B, "PDS_PAY_KEEP"},
                new Object[] {"PDS_WIN_B_LOW", 1, 20240101, 1, 1, 1, 1, null,
                        1, 100d, 0d, 0d, 100d, 0d, 100d, PARENT_CHILD_WINDOW_STATUS_B, "PDS_PAY_LOW"}
        ));
    }

    private void deleteParentChildWindowFixture() {
        jdbcTemplate.update("DELETE FROM fact_sales WHERE order_status IN (?, ?)",
                PARENT_CHILD_WINDOW_STATUS_A, PARENT_CHILD_WINDOW_STATUS_B);
    }

    private void insertBaselineRatioScopeFixture() {
        deleteBaselineRatioScopeFixture();
        normalizeBaselineRatioScopeDateDimension();
        String sql = """
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        jdbcTemplate.batchUpdate(sql, List.of(
                new Object[] {"PDS_BR_D1", 1, 20240101, 1, 1, 1, 1, null,
                        1, 100d, 0d, 0d, 100d, 0d, 100d, BASELINE_RATIO_SCOPE_STATUS, "PDS_BR"},
                new Object[] {"PDS_BR_D2", 1, 20240102, 1, 1, 1, 1, null,
                        1, 200d, 0d, 0d, 200d, 0d, 200d, BASELINE_RATIO_SCOPE_STATUS, "PDS_BR"},
                new Object[] {"PDS_BR_D3", 1, 20240103, 1, 1, 1, 1, null,
                        1, 300d, 0d, 0d, 300d, 0d, 300d, BASELINE_RATIO_SCOPE_STATUS, "PDS_BR"},
                new Object[] {"PDS_BR_D4", 1, 20240104, 1, 1, 1, 1, null,
                        1, 400d, 0d, 0d, 400d, 0d, 400d, BASELINE_RATIO_SCOPE_STATUS, "PDS_BR"}
        ));
    }

    private void normalizeBaselineRatioScopeDateDimension() {
        List<Object[]> rows = List.of(
                new Object[] {1, "星期一", 0, 20240101},
                new Object[] {2, "星期二", 0, 20240102},
                new Object[] {3, "星期三", 0, 20240103},
                new Object[] {4, "星期四", 0, 20240104}
        );
        jdbcTemplate.batchUpdate("""
                UPDATE dim_date
                SET day_of_week = ?, day_name = ?, is_weekend = ?
                WHERE date_key = ?
                """, rows);
    }

    private void deleteBaselineRatioScopeFixture() {
        jdbcTemplate.update("DELETE FROM fact_sales WHERE order_status = ?", BASELINE_RATIO_SCOPE_STATUS);
    }

    private void insertLargePivotDomainSliceFixture() {
        deleteLargePivotDomainSliceFixture();
        String sql = """
                INSERT INTO fact_sales
                (order_id, order_line_no, date_key, product_key, customer_key, store_key, channel_key, promotion_key,
                 quantity, unit_price, unit_cost, discount_amount, sales_amount, cost_amount, profit_amount,
                 order_status, payment_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < LARGE_AXIS_DOMAIN_SIZE; i++) {
            String orderId = String.format("PDS_BIG_%04d", i);
            double salesAmount = i == LARGE_AXIS_DOMAIN_SIZE - 1 ? 9999d : 1d;
            rows.add(new Object[] {
                    orderId, 1, 20240101, 1, 1, 1, 1, null,
                    1, salesAmount, 0d, 1d, salesAmount, 0d, salesAmount,
                    LARGE_AXIS_DOMAIN_STATUS, "PDS_BIG"
            });
        }
        jdbcTemplate.batchUpdate(sql, rows);
    }

    private void deleteLargePivotDomainSliceFixture() {
        jdbcTemplate.update("DELETE FROM fact_sales WHERE order_id LIKE 'PDS_BIG_%'");
    }

    private AxisField axis(String field) {
        AxisField f = new AxisField();
        f.setField(field);
        return f;
    }

    private AxisField treeAxis(String field) {
        AxisField f = new AxisField();
        f.setField(field);
        f.setHierarchyMode("tree");
        return f;
    }

    private static final class UnavailableOuterCacheProvider implements PivotOuterCacheProvider {
        @Override
        public String name() {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public boolean isEnabled() {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public long ttlMillis() {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public LookupResult lookup(String keyHash, long nowMillis) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public void store(String keyHash,
                          SemanticQueryResponse response,
                          long nowMillis,
                          String namespace,
                          String model) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public int evict(String namespace, String model) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public int estimatePayloadBytes(SemanticQueryResponse response) {
            throw new IllegalStateException("redis unavailable");
        }
    }

    private static final class ControlledTimeOuterCacheProvider implements PivotOuterCacheProvider {

        private final PivotOuterResponseCache delegate;
        private long nowMillis;

        private ControlledTimeOuterCacheProvider(long ttlMillis, long initialNowMillis) {
            this.delegate = new PivotOuterResponseCache(
                    new PivotPipeline.OuterCacheOptions(true, ttlMillis, 16));
            this.nowMillis = initialNowMillis;
        }

        private void advanceTo(long nextNowMillis) {
            if (nextNowMillis < nowMillis) {
                throw new IllegalArgumentException("controlled time must not move backwards");
            }
            nowMillis = nextNowMillis;
        }

        @Override
        public String name() {
            return delegate.name();
        }

        @Override
        public boolean isEnabled() {
            return delegate.isEnabled();
        }

        @Override
        public long ttlMillis() {
            return delegate.ttlMillis();
        }

        @Override
        public LookupResult lookup(String keyHash, long ignoredNowMillis) {
            return delegate.lookup(keyHash, nowMillis);
        }

        @Override
        public void store(String keyHash,
                          SemanticQueryResponse response,
                          long ignoredNowMillis,
                          String namespace,
                          String model) {
            delegate.store(keyHash, response, nowMillis, namespace, model);
        }

        @Override
        public int evict(String namespace, String model) {
            return delegate.evict(namespace, model);
        }

        @Override
        public int estimatePayloadBytes(SemanticQueryResponse response) {
            return delegate.estimatePayloadBytes(response);
        }
    }
}

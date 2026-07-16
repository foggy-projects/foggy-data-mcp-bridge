package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.pivot.cascade.PivotCascadeErrorCode;
import com.foggyframework.dataset.db.model.engine.pivot.cascade.PivotCascadeException;
import com.foggyframework.dataset.db.model.engine.pivot.sql.PivotAxisDomainSqlPlanner;
import com.foggyframework.dataset.db.model.engine.pivot.sql.PivotPushdownUnsupportedException;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportTuple;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedRelationOptions;
import com.foggyframework.dataset.db.model.plugins.query_execution.ManagedSqlRelation;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.DeniedPhysicalColumn;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.core.ex.RX;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@DisplayName("Pivot Pipeline SQL Parity 集成测试")
class PivotSqlParityIT extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private QueryFacade queryFacade;

    @Value("${v934.expectedDatabase:}")
    private String v934ExpectedDatabase;

    @Test
    @DisplayName("0. Verify SQL Pushdown is actually used for TopN (Not memory fallback)")
    void testSqlPushdownTriggeredInNormalExecution() {
        ch.qos.logback.classic.Logger pivotLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PivotPipeline.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> listAppender = new ch.qos.logback.core.read.ListAppender<>();
        listAppender.start();
        pivotLogger.addAppender(listAppender);

        try {
            PivotRequest pivot = new PivotRequest();
            AxisField categoryAxis = axis("product$categoryName");
            categoryAxis.setLimit(2);
            categoryAxis.setOrderBy(List.of("-salesAmount"));
            pivot.setRows(List.of(categoryAxis));
            pivot.setMetrics(List.of("salesAmount"));
            pivot.setOutputFormat("flat");

            SemanticQueryRequest request = new SemanticQueryRequest();
            request.setPivot(pivot);

            // Execute without any denied columns or special overrides
            SemanticQueryResponse response = execute(request);
            assertNotNull(response);

            boolean pushdownLogged = listAppender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("Phase 1: SQL pushdown succeeded"));
            if (supportsWindowFunctions()) {
                assertTrue(pushdownLogged,
                        "window-capable dialect must execute the SQL pushdown path");
            } else {
                assertEquals("mysql57", v934ExpectedDatabase,
                        "the only required non-window matrix lane must be mysql57");
                assertFalse(pushdownLogged,
                        "mysql57 must not claim successful CTE/window pushdown");
                assertTrue(listAppender.list.stream().anyMatch(event ->
                                event.getFormattedMessage().contains("event=pivot.sql_pushdown.fallback")
                                        && event.getFormattedMessage().contains("fallback=memory")
                                        && event.getFormattedMessage().contains("reasonClass=UnsupportedOperationException")
                                        && event.getFormattedMessage().contains("does not support CTE")),
                        "mysql57 must emit the explicit unsupported-pushdown fallback reason");
            }
        } finally {
            pivotLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("0.1 PreAgg + systemSlice + TopN keeps final SQL params order")
    void testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder() {
        boolean v934Fixture = v934ExpectedDatabase != null && !v934ExpectedDatabase.isBlank();
        String queryModel = v934Fixture ? "V934PivotPreAggQueryModel" : "FactSalesPreAggQueryModel";
        int sliceStart = v934Fixture ? 20930101 : 20240101;
        int sliceEnd = v934Fixture ? 20930103 : 20240331;
        String factTable = v934Fixture ? "v934_preagg_fact_sales" : "fact_sales";
        String productTable = v934Fixture ? "v934_preagg_dim_product" : "dim_product";
        String expectedPreAggName = v934Fixture ? "v934_daily_product_sales" : "daily_product_sales";
        String expectedPreAggTable = v934Fixture
                ? "v934_preagg_daily_product_sales"
                : "preagg_daily_product_sales";

        DbQueryRequestDef queryDef = new DbQueryRequestDef();
        queryDef.setQueryModel(queryModel);
        queryDef.setReturnTotal(false);
        queryDef.setStrictColumns(true);
        queryDef.setColumns(List.of("product$categoryName", "salesAmount"));
        queryDef.setGroupBy(List.of(
                group("product$categoryName", null),
                group("salesAmount", "SUM")
        ));

        SliceRequestDef systemDateSlice = new SliceRequestDef();
        systemDateSlice.setField("salesDate$id");
        systemDateSlice.setOp("[)");
        systemDateSlice.setValue(List.of(sliceStart, sliceEnd));

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryDef);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(10_000);
        pagingRequest.setPageSize(10_000);

        ModelResultContext resultContext = new ModelResultContext();
        resultContext.setRequest(pagingRequest);
        resultContext.setQueryType(ModelResultContext.QueryType.SEMANTIC);
        resultContext.setSystemSlice(List.of(systemDateSlice));
        if (!v934Fixture) {
            resultContext.setCacheConfig(ModelResultContext.QueryCacheConfig.builder()
                    .preAggEnabled(true)
                    .hybridQueryEnabled(false)
                    .build());
        }

        ManagedSqlRelation baseRelation = queryFacade.prepareManagedRelation(resultContext,
                ManagedRelationOptions.builder()
                        .purpose("pivot-sql-preagg-param-order-regression")
                        .disableInnerCacheShortCircuit(true)
                        .requireStableAliases(true)
                        .build());

        assertTrue(baseRelation.isPreAggApplied(), "preAgg should be applied before outer Pivot CTE wrapping");
        assertEquals(expectedPreAggName, resultContext.getCacheConfig().getPreAggName(),
                "base relation must use the branch-specific preAgg identity");
        assertTrue(baseRelation.getSql().contains("FROM " + expectedPreAggTable + " "),
                "base SQL should query the exact branch-specific preAgg table: " + baseRelation.getSql());
        assertFalse(baseRelation.getSql().contains("FROM " + factTable + " "),
                "raw fact SQL must not masquerade as the preAgg base relation");
        assertIterableEquals(List.of(sliceStart, sliceEnd), baseRelation.getParams(),
                "base relation params should come from the systemSlice after preAgg rewrite");
        assertTrue(baseRelation.getSql().contains("category_name"),
                "preAgg relation must use the physical category column: " + baseRelation.getSql());
        assertTrue(baseRelation.getSql().contains("sales_amount_sum"),
                "preAgg relation must use the configured measure column: " + baseRelation.getSql());
        assertFalse(baseRelation.getSql().contains("pa.product$categoryName"),
                "semantic result alias must never be used as a physical preAgg column");
        assertFalse(baseRelation.getSql().contains("pa.salesAmount"),
                "semantic measure alias must never be used as a physical preAgg column");

        List<Map<String, Object>> baseItems = jdbcTemplate.queryForList(
                baseRelation.getSql(), baseRelation.getParams().toArray(new Object[0]));
        String nativeOracleSql = "SELECT p.category_name AS category_name, "
                + "SUM(f.sales_amount) AS sales_amount "
                + "FROM " + factTable + " f "
                + "LEFT JOIN " + productTable + " p ON f.product_key = p.product_key "
                + "WHERE f.date_key >= ? AND f.date_key < ? "
                + "GROUP BY p.category_name";
        List<Map<String, Object>> nativeItems = jdbcTemplate.queryForList(
                nativeOracleSql, sliceStart, sliceEnd);
        assertFalse(nativeItems.isEmpty(), "native preAgg oracle must contain deterministic rows");
        assertEquals(nativeItems.size(), baseItems.size(),
                "preAgg base relation must preserve the complete grouped native row set");
        assertParity(nativeItems, baseItems,
                "category_name", "product$categoryName", "sales_amount", "salesAmount");

        int topNLimit = 2;
        AxisField categoryAxis = axis("product$categoryName");
        categoryAxis.setLimit(topNLimit);
        categoryAxis.setOrderBy(List.of("-salesAmount"));

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(categoryAxis));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        if (supportsWindowFunctions()) {
            PivotAxisDomainSqlPlanner.PlannedSql planned = PivotAxisDomainSqlPlanner.plan(
                    baseRelation,
                    pivot,
                    List.of("product$categoryName"),
                    List.of(),
                    List.of("salesAmount"));

            List<Object> finalParams = planned.getParams();
            int baseParamCount = baseRelation.getParams().size();
            assertEquals(baseParamCount + 1, finalParams.size(),
                    "outer TopN should append exactly one rn limit param after preAgg/systemSlice params");
            assertEquals(baseRelation.getParams(), finalParams.subList(0, baseParamCount),
                    "preAgg/systemSlice params must remain before outer domain CTE params");
            assertEquals(topNLimit, finalParams.get(baseParamCount),
                    "rn <= ? limit param must be appended after base relation params");

            List<Map<String, Object>> plannedItems = jdbcTemplate.queryForList(
                    planned.getSql(), finalParams.toArray(new Object[0]));
            String topNOracleSql = paginateSql(
                    nativeOracleSql + " ORDER BY sales_amount DESC, category_name ASC", topNLimit);
            List<Map<String, Object>> topNOracleItems = jdbcTemplate.queryForList(
                    topNOracleSql, sliceStart, sliceEnd);
            assertEquals(topNLimit, plannedItems.size(),
                    "planned preAgg TopN must return the exact requested row count");
            assertEquals(topNOracleItems.size(), plannedItems.size());
            assertParity(topNOracleItems, plannedItems,
                    "category_name", "product$categoryName", "sales_amount", "salesAmount");
        } else {
            assertTrue(baseRelation.isPermissionValidated(),
                    "MySQL 5.7 refusal must happen after permission validation");
            assertFalse(baseRelation.isWrappable(),
                    "MySQL 5.7 managed relation must advertise that outer CTE wrapping is unavailable");
            PivotPushdownUnsupportedException refusal = assertThrows(
                    PivotPushdownUnsupportedException.class,
                    () -> PivotAxisDomainSqlPlanner.plan(
                            baseRelation,
                            pivot,
                            List.of("product$categoryName"),
                            List.of(),
                            List.of("salesAmount")));
            assertEquals(
                    "ManagedSqlRelation is not wrappable. Cannot generate outer Pivot SQL. permissionValidated=true",
                    refusal.getMessage(),
                    "MySQL 5.7 must produce the exact fail-closed outer-wrapping refusal");
        }
    }

    // ========== Parity Scenarios ==========

    @Test
    @DisplayName("1. Basic flat pivot: ROWS=[product$categoryName], METRICS=[salesAmount]")
    void testBasicFlatPivotParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "SELECT t2.category_name as category_name, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "GROUP BY t2.category_name";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParity(sqlItems, pivotItems, "category_name", "product$categoryName", "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("2. Grid pivot logical parity: ROWS=[product$categoryName], COLS=[salesDate$month]")
    void testGridPivotParity() {
        // Output format flat but multi-axis, to test logical multi-axis grouping parity.
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(axis("salesDate$month")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat"); // using flat to easily compare row-by-row logical parity

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "SELECT t2.category_name as category_name, t3.month as month_of_year, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "GROUP BY t2.category_name, t3.month";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "month_of_year"),
                List.of("product$categoryName", "salesDate$month"),
                "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("3. Having after aggregation: salesAmount > threshold")
    void testHavingFilterParity() {
        PivotRequest pivot = new PivotRequest();

        AxisField categoryAxis = axis("product$categoryName");
        MetricFilter filter = new MetricFilter();
        filter.setMetric("salesAmount");
        filter.setOp(">");
        filter.setValue(1000);
        categoryAxis.setHaving(List.of(filter));

        pivot.setRows(List.of(categoryAxis));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "SELECT t2.category_name as category_name, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "GROUP BY t2.category_name " +
                "HAVING SUM(t1.sales_amount) > 1000";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParity(sqlItems, pivotItems, "category_name", "product$categoryName", "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("3a. Member Properties (属性带出) Parity")
    void testPropertiesParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$id")));
        pivot.setProperties(List.of("product$brand"));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle - we use MAX to simulate ANY_VALUE for properties which are functionally dependent on grouping key
        String sql = "SELECT t2.product_key as product_id, MAX(t2.brand) as brand, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "GROUP BY t2.product_key";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("product_id", "brand"),
                List.of("product$id", "product$brand"),
                "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("3b. Top N & OrderBy (截断与排序) Parity")
    void testTopNAndOrderByParity() {
        PivotRequest pivot = new PivotRequest();
        AxisField categoryAxis = axis("product$categoryName");
        categoryAxis.setOrderBy(List.of("-salesAmount"));
        categoryAxis.setLimit(2);

        pivot.setRows(List.of(categoryAxis));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle — secondary sort by category_name ASC matches pivot engine's tie-breaking rule
        String sql = "SELECT t2.category_name as category_name, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "GROUP BY t2.category_name " +
                "ORDER BY sales_amount DESC, category_name ASC";
        sql = paginateSql(sql, 2);
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        // Use set-based parity: verify the same TopN members and values exist,
        // regardless of order (collation of Chinese strings differs across DBs).
        assertParity(sqlItems, pivotItems, "category_name", "product$categoryName", "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("3c. Slicer Axis / WHERE (切片轴过滤) Parity")
    void testSlicerAxisParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        // user-level slice mimicking Slicer Axis
        SemanticQueryRequest.SliceItem slice = new SemanticQueryRequest.SliceItem();
        slice.setField("salesDate$year");
        slice.setOp("=");
        slice.setValue(2024);
        request.setSlice(List.of(slice));

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "SELECT t2.category_name as category_name, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "WHERE t3.year = 2024 " +
                "GROUP BY t2.category_name";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParity(sqlItems, pivotItems, "category_name", "product$categoryName", "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("3d. Generate / Per-Group Ranking (分组内 TopN) Parity")
    void testGeneratePerGroupTopNParity() {
        PivotRequest pivot = new PivotRequest();
        AxisField categoryAxis = axis("product$categoryName");

        AxisField subCategoryAxis = axis("product$subCategoryName");
        subCategoryAxis.setOrderBy(List.of("-salesAmount"));
        subCategoryAxis.setLimit(2); // Top 2 subCategories per category

        pivot.setRows(List.of(categoryAxis, subCategoryAxis));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // Portable native oracle: aggregate in SQL, then rank each category in Java so
        // the required MySQL 5.7 lane proves the positive in-memory fallback path too.
        String sql = "SELECT t2.category_name AS category_name, " +
                "t2.sub_category_name AS sub_category_name, " +
                "SUM(t1.sales_amount) AS sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "GROUP BY t2.category_name, t2.sub_category_name";

        List<Map<String, Object>> sqlItems = topNPerGroup(
                jdbcTemplate.queryForList(sql),
                "category_name",
                "sub_category_name",
                "sales_amount",
                2);

        // We use multi-dim parity assertion
        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "sub_category_name"),
                List.of("product$categoryName", "product$subCategoryName"),
                "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("4. Row subtotals / grand total parity")
    void testRowSubtotalsParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName"), axis("salesDate$year")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // Filter out pivot subtotal rows
        List<Map<String, Object>> pivotSubtotals = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isRowSubtotal")))
                .collect(Collectors.toList());
        List<Map<String, Object>> pivotGrandTotal = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isGrandTotal")))
                .collect(Collectors.toList());

        // SQL Oracle Subtotal (Group by category only)
        String sqlSub = "SELECT t2.category_name as category_name, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "GROUP BY t2.category_name";
        List<Map<String, Object>> sqlSubItems = jdbcTemplate.queryForList(sqlSub);

        // SQL Oracle Grand Total (No grouping)
        String sqlGrand = "SELECT SUM(t1.sales_amount) as sales_amount FROM fact_sales t1";
        List<Map<String, Object>> sqlGrandItems = jdbcTemplate.queryForList(sqlGrand);

        assertParity(sqlSubItems, pivotSubtotals, "category_name", "product$categoryName", "sales_amount", "salesAmount");
        assertEquals(1, pivotGrandTotal.size());
        assertEquals(((Number) sqlGrandItems.get(0).get("sales_amount")).doubleValue(),
                     ((Number) pivotGrandTotal.get(0).get("salesAmount")).doubleValue(), 0.01);
    }

    @Test
    @DisplayName("5. Non-additive rollup (COUNT DISTINCT)")
    void testNonAdditiveRollupParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("uniqueCustomers")); // uniqueCustomers is mapped to COUNT_DISTINCT customer_id
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // Filter leaf and grand total
        List<Map<String, Object>> pivotLeaves = pivotItems.stream()
                .filter(r -> !r.containsKey("_sys_meta") || !Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isGrandTotal")))
                .collect(Collectors.toList());
        List<Map<String, Object>> pivotGrandTotal = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isGrandTotal")))
                .collect(Collectors.toList());

        // SQL Oracle (leaf)
        String sql = "SELECT t2.category_name as category_name, COUNT(DISTINCT t1.customer_key) as unique_customers " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "GROUP BY t2.category_name";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParity(sqlItems, pivotLeaves, "category_name", "product$categoryName", "unique_customers", "uniqueCustomers");

        // SQL Oracle (grand total)
        String sqlGrand = "SELECT COUNT(DISTINCT t1.customer_key) as unique_customers FROM fact_sales t1";
        List<Map<String, Object>> sqlGrandItems = jdbcTemplate.queryForList(sqlGrand);

        assertEquals(1, pivotGrandTotal.size());
        assertEquals(((Number) sqlGrandItems.get(0).get("unique_customers")).longValue(),
                     ((Number) pivotGrandTotal.get(0).get("uniqueCustomers")).longValue());
    }

    @Test
    @DisplayName("6. UNION ALL batch/column alignment")
    void testUnionAllBatchMergeParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName"), axis("salesDate$year")));
        pivot.setColumns(List.of(axis("customer$customerType")));
        pivot.setMetrics(List.of("uniqueCustomers")); // triggers NonAdditiveRollupExecutor
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        options.setColumnSubtotals(true);
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        // Executes complex UNION ALL fallback correctly.
        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // Assert Grand Total correctness
        List<Map<String, Object>> pivotGrandTotal = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isGrandTotal")))
                .collect(Collectors.toList());
        log.info("pivotGrandTotal items: {}", pivotGrandTotal);

        String sqlGrand = "SELECT t3.customer_type, COUNT(DISTINCT t1.customer_key) as unique_customers " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_customer t3 ON t1.customer_key = t3.customer_key " +
                "GROUP BY t3.customer_type";
        List<Map<String, Object>> sqlGrandItems = jdbcTemplate.queryForList(sqlGrand);
        assertParity(sqlGrandItems, pivotGrandTotal,
                "customer_type", "customer$customerType",
                "unique_customers", "uniqueCustomers");
    }



    @Test
    @DisplayName("7a. Permissions with Pivot (systemSlice parity) using Category")
    void testSystemSliceCategoryParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        // The versioned ecommerce fixture uses CAT001 for the electronics category.
        List<SliceRequestDef> systemSlice = List.of(new SliceRequestDef("product$categoryId", "=", "CAT001"));

        SemanticRequestContext ctx = SemanticRequestContext.of(null, null, null, null, systemSlice);

        SemanticQueryResponse response = execute(TEST_MODEL, request, ctx);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "SELECT t2.category_name as category_name, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "WHERE t2.category_id = 'CAT001' " +
                "GROUP BY t2.category_name";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParity(sqlItems, pivotItems, "category_name", "product$categoryName", "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("7b. Permissions with Pivot (deniedColumns fail-closed)")
    void testDeniedColumnsFailClosed() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setMetrics(List.of("costAmount")); // Requesting a sensitive metric
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        // Deny access to cost_amount
        List<DeniedPhysicalColumn> deniedColumns = List.of(new DeniedPhysicalColumn(null, "fact_sales", "cost_amount"));

        SemanticRequestContext ctx = SemanticRequestContext.of(null, null, null, deniedColumns);

        Exception ex = assertThrows(Exception.class, () -> execute(TEST_MODEL, request, ctx));
        // Verify fail closed access denied
        log.info("Exception message: {}, cause: {}", ex.getMessage(), ex.getCause() != null ? ex.getCause().getMessage() : "null");
        assertAccessDeniedFailure(ex);
    }

    // ========== S11: parentShare Parity Tests ==========

    @Test
    @DisplayName("8. parentShare parity: 子级占比与 SQL window 比对")
    void testParentShareParity() {
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
        List<Map<String, Object>> pivotItems = response.getItems();

        // Portable native oracle: aggregate first, then calculate the share in test code.
        String sql = "SELECT t2.category_name, d1.month, " +
                "SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "LEFT JOIN dim_date d1 ON t1.date_key = d1.date_key " +
                "GROUP BY t2.category_name, d1.month";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);
        assertParentShareParity(sqlItems, pivotItems);
        log.info("S11: parentShare SQL Parity 验证通过, {} 行", pivotItems.size());
    }

    @Test
    @DisplayName("9. parentShare without systemSlice - basic 2-level hierarchy")
    void testParentShareBasicTwoLevel() {
        PivotRequest pivot = new PivotRequest();
        // Use categoryName + brand (proven QM fields) for 2-level hierarchy
        pivot.setRows(List.of(axis("product$categoryName"), axis("product$brand")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem ps = new PivotMetricItem();
        ps.setName("brandShare");
        ps.setType("parentShare");
        ps.setOf("salesAmount");
        items.add(ps);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();
        assertFalse(pivotItems.isEmpty(), "categoryName + brand parentShare should return data");

        // 所有行都应有 brandShare
        boolean hasShare = false;
        for (Map<String, Object> row : pivotItems) {
            assertTrue(row.containsKey("brandShare"), "flat 输出应包含 parentShare 字段");
            if (row.get("brandShare") != null) {
                double share = ((Number) row.get("brandShare")).doubleValue();
                assertTrue(share >= 0 && share <= 1.0001, "parentShare 应在 [0,1]: " + share);
                hasShare = true;
            }
        }
        assertTrue(hasShare, "应至少有一行 parentShare 非 null");
        log.info("S11: parentShare 2-level 验证通过, {} 行", pivotItems.size());
    }

    @Test
    @DisplayName("10. parentShare + deniedColumns fail-closed")
    void testParentShareDeniedColumnsFailed() {
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

        // Deny access to sales_amount (父级占比依赖的度量)
        List<DeniedPhysicalColumn> deniedColumns = List.of(
                new DeniedPhysicalColumn(null, "fact_sales", "sales_amount"));
        SemanticRequestContext ctx = SemanticRequestContext.of(null, null, null, deniedColumns);

        Exception ex = assertThrows(Exception.class, () -> execute(TEST_MODEL, request, ctx));
        assertAccessDeniedFailure(ex);
        log.info("S11: parentShare deniedColumns fail-closed 验证通过");
    }

    // ========== S12 baselineRatio Parity ==========

    @Test
    @DisplayName("S12: baselineRatio parity with SQL Window functions")
    void testBaselineRatioParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));

        PivotMetricItem brFirst = new PivotMetricItem();
        brFirst.setName("idxFirst");
        brFirst.setType("baselineRatio");
        brFirst.setOf("salesAmount");
        brFirst.setAxis("columns");
        brFirst.setBaseline("first");
        items.add(brFirst);

        PivotMetricItem brLast = new PivotMetricItem();
        brLast.setName("idxLast");
        brLast.setType("baselineRatio");
        brLast.setOf("salesAmount");
        brLast.setAxis("columns");
        brLast.setBaseline("last");
        items.add(brLast);

        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // Portable base oracle. BaselineRatioCalculator uses the global first/last month;
        // ratios are calculated below without relying on CTE/window support.
        String sql = "SELECT t2.category_name as category_name, t3.month as month_name, " +
                "SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "  GROUP BY t2.category_name, t3.month";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertBaselineRatioParity(sqlItems, pivotItems, true, "idxFirst");
        assertBaselineRatioParity(sqlItems, pivotItems, false, "idxLast");

        log.info("S12: baselineRatio (first & last) Parity 验证通过");
    }

    @Test
    @DisplayName("S12: baselineRatio + deniedColumns fail-closed")
    void testBaselineRatioDeniedColumnsFailed() {
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

        // Deny access to sales_amount (基准计算依赖的度量)
        List<DeniedPhysicalColumn> deniedColumns = List.of(
                new DeniedPhysicalColumn(null, "fact_sales", "sales_amount"));
        SemanticRequestContext ctx = SemanticRequestContext.of(null, null, null, deniedColumns);

        Exception ex = assertThrows(Exception.class, () -> execute(TEST_MODEL, request, ctx));
        assertAccessDeniedFailure(ex);
        log.info("S12: baselineRatio deniedColumns fail-closed 验证通过");
    }

    @Test
    @DisplayName("S12: baselineRatio + systemSlice parity")
    void testBaselineRatioSystemSliceParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem br = new PivotMetricItem();
        br.setName("idxFirst");
        br.setType("baselineRatio");
        br.setOf("salesAmount");
        br.setAxis("columns");
        br.setBaseline("first");
        items.add(br);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        // 限定 category = '数码电器' (CAT001)
        List<SliceRequestDef> systemSlice = List.of(new SliceRequestDef("product$categoryId", "=", "CAT001"));
        SemanticRequestContext ctx = SemanticRequestContext.of(null, null, null, null, systemSlice);

        SemanticQueryResponse response = execute(TEST_MODEL, request, ctx);
        List<Map<String, Object>> pivotItems = response.getItems();

        String sql = "SELECT t2.category_name as category_name, t3.month as month_name, " +
                "SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "  WHERE t2.category_id = 'CAT001' " +
                "  GROUP BY t2.category_name, t3.month";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertBaselineRatioParity(sqlItems, pivotItems, true, "idxFirst");

        log.info("S12: baselineRatio + systemSlice Parity 验证通过");
    }

    @Test
    @DisplayName("S12: baselineRatio + user slice parity")
    void testBaselineRatioUserSliceParity() {
        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(axis("product$categoryName")));
        pivot.setColumns(List.of(axis("salesDate$month")));

        List<PivotMetricItem> items = new ArrayList<>();
        items.add(PivotMetricItem.ofNative("salesAmount"));
        PivotMetricItem br = new PivotMetricItem();
        br.setName("idxLast");
        br.setType("baselineRatio");
        br.setOf("salesAmount");
        br.setAxis("columns");
        br.setBaseline("last");
        items.add(br);
        pivot.setMetricItems(items);
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        // 用户级 slice：只看 2024 年
        SemanticQueryRequest.SliceItem yearSlice = new SemanticQueryRequest.SliceItem();
        yearSlice.setField("salesDate$year");
        yearSlice.setOp("=");
        yearSlice.setValue(2024);
        request.setSlice(List.of(yearSlice));

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        String sql = "SELECT t2.category_name as category_name, t3.month as month_name, " +
                "SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "  WHERE t3.year = 2024 " +
                "  GROUP BY t2.category_name, t3.month";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertBaselineRatioParity(sqlItems, pivotItems, false, "idxLast");

        log.info("S12: baselineRatio + user slice Parity 验证通过");
    }

    @Test
    @DisplayName("13. Cascade TopN + COUNT_DISTINCT + totals fails closed")
    void testCascadeTopNNonAdditiveTotalsRejected() {
        PivotRequest pivot = new PivotRequest();
        
        // 维度1：Category (Top 2 by salesAmount to allow SQL pushdown)
        AxisField categoryAxis = axis("product$categoryName");
        categoryAxis.setOrderBy(List.of("-salesAmount"));
        categoryAxis.setLimit(2);
        
        // 维度2：Month
        AxisField monthAxis = axis("salesDate$month");

        pivot.setRows(List.of(categoryAxis, monthAxis));
        pivot.setMetrics(List.of("salesAmount", "uniqueCustomers")); // non-additive
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        PivotCascadeException ex = assertThrows(PivotCascadeException.class, () -> execute(request));
        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_NON_ADDITIVE_REJECTED, ex.getCode());
        assertTrue(ex.getMessage().contains("uniqueCustomers"));
    }

    @Test
    @DisplayName("14. Stage 5A large-domain transport queryModel parity")
    void testLargeDomainTransportQueryModelParity() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setColumns(List.of("product$categoryName", "uniqueCustomers"));
        request.setGroupBy(List.of(
                new SemanticQueryRequest.GroupByItem("product$categoryName", null),
                new SemanticQueryRequest.GroupByItem("uniqueCustomers", null)
        ));
        request.setLimit(1000);
        request.setReturnTotal(false);

        DomainTransportPlan plan = DomainTransportPlan.builder()
                .relationName("_pivot_domain_transport_test")
                .fields(List.of(new DomainTransportField("product$categoryName")))
                .tuples(buildLargeSingleFieldDomain("数码电器", 501))
                .build();
        SemanticRequestContext ctx = SemanticRequestContext.empty()
                .withDomainTransportPlans(List.of(plan));

        SqlGenerationResult generatedSql = semanticQueryServiceV3.generateSql(TEST_MODEL, request, ctx);
        assertNotNull(generatedSql);
        String sqlText = generatedSql.getSql();
        assertTrue(sqlText.contains("_pivot_domain_transport_test")
                        || (sqlText.contains("exists (select 1 from (")
                        && sqlText.contains("product$categoryName")),
                "Generated SQL should contain an inline large-domain transport relation");
        assertEquals(501, generatedSql.getParams().size(),
                "Large-domain transport params should be preserved and ordered");

        SemanticQueryResponse response = execute(TEST_MODEL, request, ctx);
        List<Map<String, Object>> actual = response.getItems();

        String sql = "SELECT t2.category_name as category_name, COUNT(DISTINCT t1.customer_key) as unique_customers " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "WHERE t2.category_name = '数码电器' " +
                "GROUP BY t2.category_name";
        List<Map<String, Object>> expected = jdbcTemplate.queryForList(sql);

        assertParity(expected, actual,
                "category_name", "product$categoryName",
                "unique_customers", "uniqueCustomers");
    }

    // ========== Helpers ==========

    private List<DomainTransportTuple> buildLargeSingleFieldDomain(String matchingValue, int size) {
        List<DomainTransportTuple> tuples = new ArrayList<>();
        tuples.add(new DomainTransportTuple(List.of(matchingValue)));
        for (int i = 1; i < size; i++) {
            tuples.add(new DomainTransportTuple(List.of("__missing_domain_" + i)));
        }
        return tuples;
    }

    private void assertParity(List<Map<String, Object>> sqlItems, List<Map<String, Object>> pivotItems,
                              String sqlDimKey, String pivotDimKey, String sqlMetricKey, String pivotMetricKey) {

        assertFalse(sqlItems.isEmpty(), "SQL oracle must return deterministic rows");
        assertEquals(sqlItems.size(), pivotItems.size(),
                "SQL oracle and Pivot must expose the same row cardinality");
        Map<List<Object>, Double> sqlMap = metricIndex(
                sqlItems, List.of(sqlDimKey), sqlMetricKey, "SQL");
        Map<List<Object>, Double> pivotMap = metricIndex(
                pivotItems, List.of(pivotDimKey), pivotMetricKey, "Pivot");

        assertEquals(sqlMap.keySet(), pivotMap.keySet(), "Dimension members differ between SQL and Pivot");
        for (Map.Entry<List<Object>, Double> entry : sqlMap.entrySet()) {
            assertEquals(entry.getValue(), pivotMap.get(entry.getKey()), 0.01, "Value mismatch for " + entry.getKey());
        }
    }

    private void assertParityMultiDim(List<Map<String, Object>> sqlItems, List<Map<String, Object>> pivotItems,
                              List<String> sqlDimKeys, List<String> pivotDimKeys, String sqlMetricKey, String pivotMetricKey) {

        assertFalse(sqlItems.isEmpty(), "SQL oracle must return deterministic rows");
        assertEquals(sqlItems.size(), pivotItems.size(),
                "SQL oracle and Pivot must expose the same row cardinality");
        Map<List<Object>, Double> sqlMap = metricIndex(sqlItems, sqlDimKeys, sqlMetricKey, "SQL");
        Map<List<Object>, Double> pivotMap = metricIndex(pivotItems, pivotDimKeys, pivotMetricKey, "Pivot");

        assertEquals(sqlMap.keySet(), pivotMap.keySet(), "Dimension tuples differ between SQL and Pivot");
        for (Map.Entry<List<Object>, Double> entry : sqlMap.entrySet()) {
            assertEquals(entry.getValue(), pivotMap.get(entry.getKey()), 0.01,
                    "Value mismatch for " + entry.getKey());
        }
    }

    private void assertParentShareParity(List<Map<String, Object>> sqlItems,
                                         List<Map<String, Object>> pivotItems) {
        assertFalse(sqlItems.isEmpty(), "parentShare SQL oracle must return deterministic rows");
        assertEquals(sqlItems.size(), pivotItems.size(),
                "parentShare must preserve the complete grouped row set");

        Map<Object, Double> totalsByCategory = new LinkedHashMap<>();
        Map<List<Object>, Double> salesByTuple = metricIndex(
                sqlItems, List.of("category_name", "month"), "sales_amount", "SQL");
        for (Map.Entry<List<Object>, Double> entry : salesByTuple.entrySet()) {
            totalsByCategory.merge(entry.getKey().get(0), entry.getValue(), Double::sum);
        }

        Map<List<Object>, Double> actual = metricIndex(
                pivotItems,
                List.of("product$categoryName", "salesDate$month"),
                "monthShare",
                "Pivot");
        assertEquals(salesByTuple.keySet(), actual.keySet(),
                "parentShare dimension tuples differ between SQL and Pivot");
        for (Map.Entry<List<Object>, Double> entry : salesByTuple.entrySet()) {
            double expected = entry.getValue() / totalsByCategory.get(entry.getKey().get(0));
            assertEquals(expected, actual.get(entry.getKey()), 0.001,
                    "parentShare mismatch for " + entry.getKey());
        }
    }

    private void assertBaselineRatioParity(List<Map<String, Object>> sqlItems,
                                           List<Map<String, Object>> pivotItems,
                                           boolean first,
                                           String pivotMetricKey) {
        assertFalse(sqlItems.isEmpty(), "baselineRatio SQL oracle must return deterministic rows");
        assertEquals(sqlItems.size(), pivotItems.size(),
                "baselineRatio must preserve the complete grouped row set");

        BigDecimal baselineMonth = null;
        for (Map<String, Object> row : sqlItems) {
            Object rawMonth = requiredColumn(row, "month_name", "SQL baseline dimension");
            // SQL MIN/MAX ignore NULL axis members. Keep the NULL tuple in the
            // parity set, but do not let it become the global baseline.
            if (rawMonth == null) {
                continue;
            }
            assertTrue(rawMonth instanceof Number,
                    "SQL baseline month must be numeric: " + rawMonth);
            BigDecimal month = normalizeNumber((Number) rawMonth);
            if (baselineMonth == null
                    || (first && month.compareTo(baselineMonth) < 0)
                    || (!first && month.compareTo(baselineMonth) > 0)) {
                baselineMonth = month;
            }
        }
        assertNotNull(baselineMonth, "global baseline month must be resolved");

        Map<List<Object>, Double> salesByTuple = metricIndex(
                sqlItems, List.of("category_name", "month_name"), "sales_amount", "SQL");
        Map<Object, Double> baselineByCategory = new LinkedHashMap<>();
        for (Map.Entry<List<Object>, Double> entry : salesByTuple.entrySet()) {
            if (baselineMonth.equals(entry.getKey().get(1))) {
                baselineByCategory.put(entry.getKey().get(0), entry.getValue());
            }
        }

        Map<List<Object>, Double> actual = nullableMetricIndex(
                pivotItems,
                List.of("product$categoryName", "salesDate$month"),
                pivotMetricKey,
                "Pivot");
        assertEquals(salesByTuple.keySet(), actual.keySet(),
                "baselineRatio dimension tuples differ between SQL and Pivot");
        for (Map.Entry<List<Object>, Double> entry : salesByTuple.entrySet()) {
            Double baseline = baselineByCategory.get(entry.getKey().get(0));
            Double expected = baseline == null || baseline == 0d
                    ? null
                    : entry.getValue() / baseline;
            Double actualValue = actual.get(entry.getKey());
            if (expected == null) {
                assertNull(actualValue, "baselineRatio must remain null without a valid baseline: " + entry.getKey());
            } else {
                assertNotNull(actualValue, "baselineRatio is missing for " + entry.getKey());
                assertEquals(expected, actualValue, 0.001,
                        "baselineRatio mismatch for " + entry.getKey());
            }
        }
    }

    private Map<List<Object>, Double> metricIndex(List<Map<String, Object>> rows,
                                                   List<String> dimensionKeys,
                                                   String metricKey,
                                                   String label) {
        Map<List<Object>, Double> index = nullableMetricIndex(rows, dimensionKeys, metricKey, label);
        for (Map.Entry<List<Object>, Double> entry : index.entrySet()) {
            assertNotNull(entry.getValue(), label + " metric must be numeric and non-null for " + entry.getKey());
        }
        return index;
    }

    private Map<List<Object>, Double> nullableMetricIndex(List<Map<String, Object>> rows,
                                                           List<String> dimensionKeys,
                                                           String metricKey,
                                                           String label) {
        Map<List<Object>, Double> index = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> tuple = dimensionTuple(row, dimensionKeys, label + " dimension");
            assertFalse(index.containsKey(tuple), label + " contains a duplicate dimension tuple: " + tuple);
            Object metric = requiredColumn(row, metricKey, label + " metric");
            assertTrue(metric == null || metric instanceof Number,
                    label + " metric must be numeric: " + metricKey + "=" + metric);
            index.put(tuple, metric == null ? null : ((Number) metric).doubleValue());
        }
        return index;
    }

    private List<Object> dimensionTuple(Map<String, Object> row, List<String> keys, String label) {
        List<Object> tuple = new ArrayList<>(keys.size());
        for (String key : keys) {
            Object value = requiredColumn(row, key, label);
            tuple.add(value instanceof Number ? normalizeNumber((Number) value) : value);
        }
        return tuple;
    }

    private Object requiredColumn(Map<String, Object> row, String key, String label) {
        assertTrue(row.containsKey(key), label + " column is missing: " + key + " in " + row.keySet());
        return row.get(key);
    }

    private BigDecimal normalizeNumber(Number value) {
        return new BigDecimal(value.toString()).stripTrailingZeros();
    }

    private List<Map<String, Object>> topNPerGroup(List<Map<String, Object>> rows,
                                                    String groupKey,
                                                    String memberKey,
                                                    String metricKey,
                                                    int limit) {
        assertFalse(rows.isEmpty(), "per-group TopN SQL oracle must return deterministic rows");
        Map<Object, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object group = requiredColumn(row, groupKey, "SQL group dimension");
            groups.computeIfAbsent(group, ignored -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (List<Map<String, Object>> groupRows : groups.values()) {
            groupRows.sort((left, right) -> {
                double leftMetric = requiredNumericColumn(left, metricKey, "SQL ranking metric");
                double rightMetric = requiredNumericColumn(right, metricKey, "SQL ranking metric");
                int metricOrder = Double.compare(rightMetric, leftMetric);
                if (metricOrder != 0) {
                    return metricOrder;
                }
                Object leftMember = requiredColumn(left, memberKey, "SQL ranking member");
                Object rightMember = requiredColumn(right, memberKey, "SQL ranking member");
                if (leftMember == null || rightMember == null) {
                    return leftMember == rightMember ? 0 : (leftMember == null ? 1 : -1);
                }
                return String.valueOf(leftMember).compareTo(String.valueOf(rightMember));
            });
            result.addAll(groupRows.subList(0, Math.min(limit, groupRows.size())));
        }
        return result;
    }

    private double requiredNumericColumn(Map<String, Object> row, String key, String label) {
        Object value = requiredColumn(row, key, label);
        assertTrue(value instanceof Number, label + " must be numeric: " + key + "=" + value);
        return ((Number) value).doubleValue();
    }

    private void assertAccessDeniedFailure(Exception ex) {
        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        String lowerMsg = msg != null ? msg.toLowerCase() : "";
        assertTrue(msg != null && (lowerMsg.contains("denied") ||
                   lowerMsg.contains("permission") ||
                   msg.contains("安全") || msg.contains("权限") ||
                   msg.contains("Access") || msg.contains("受限") ||
                   msg.contains("拒绝")),
                "Should fail-closed for denied column: " + msg);
    }

    private SemanticQueryResponse execute(SemanticQueryRequest request) {
        return execute(TEST_MODEL, request, SemanticRequestContext.empty());
    }

    private SemanticQueryResponse execute(String model, SemanticQueryRequest request, SemanticRequestContext ctx) {
        return semanticQueryServiceV3.queryModel(model, request, "execute", ctx);
    }

    private AxisField axis(String field) {
        AxisField f = new AxisField();
        f.setField(field);
        return f;
    }

    private GroupRequestDef group(String field, String agg) {
        GroupRequestDef g = new GroupRequestDef();
        g.setField(field);
        g.setAgg(agg);
        return g;
    }
}

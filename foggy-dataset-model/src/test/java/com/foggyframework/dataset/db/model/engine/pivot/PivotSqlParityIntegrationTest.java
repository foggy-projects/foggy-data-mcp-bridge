package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.pivot.sql.PivotAxisDomainSqlPlanner;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Slf4j
@DisplayName("Pivot Pipeline SQL Parity 集成测试")
class PivotSqlParityIntegrationTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private QueryFacade queryFacade;

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

            // MySQL 5.7 does not support CTEs/window functions -> SQL pushdown is not attempted.
            // Only assert pushdown on dialects that support window functions.
            if (!supportsWindowFunctions()) {
                assumeTrue(false, "Skipping pushdown assertion on dialect without CTE/window function support (e.g. MySQL 5.7)");
            }

            boolean pushdownLogged = listAppender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("Phase 1: SQL pushdown succeeded"));
            
            assertTrue(pushdownLogged, "Expected SQL pushdown to be used, but it wasn't logged. Did it fall back to memory?");
        } finally {
            pivotLogger.detachAppender(listAppender);
        }
    }

    @Test
    @DisplayName("0.1 PreAgg + systemSlice + TopN keeps final SQL params order")
    void testPreAggHitWithSystemSliceAndLimitKeepsFinalParamOrder() {
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL pushdown requires CTE + ROW_NUMBER() OVER()");

        DbQueryRequestDef queryDef = new DbQueryRequestDef();
        queryDef.setQueryModel("FactSalesPreAggQueryModel");
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
        systemDateSlice.setValue(List.of(20240101, 20240331));

        PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
        pagingRequest.setParam(queryDef);
        pagingRequest.setStart(0);
        pagingRequest.setLimit(10_000);
        pagingRequest.setPageSize(10_000);

        ModelResultContext resultContext = new ModelResultContext();
        resultContext.setRequest(pagingRequest);
        resultContext.setQueryType(ModelResultContext.QueryType.SEMANTIC);
        resultContext.setSystemSlice(List.of(systemDateSlice));

        ManagedSqlRelation baseRelation = queryFacade.prepareManagedRelation(resultContext,
                ManagedRelationOptions.builder()
                        .purpose("pivot-sql-preagg-param-order-regression")
                        .wrappableRequired(true)
                        .disableInnerCacheShortCircuit(true)
                        .requireStableAliases(true)
                        .requireDialectCapability(ManagedRelationOptions.DialectCapability.CTE)
                        .requireDialectCapability(ManagedRelationOptions.DialectCapability.WINDOW_FUNCTION)
                        .build());

        assertTrue(baseRelation.isPreAggApplied(), "preAgg should be applied before outer Pivot CTE wrapping");
        assertTrue(baseRelation.getSql().contains("preagg_"), "base SQL should query a preAgg table: " + baseRelation.getSql());
        assertIterableEquals(List.of(20240101, 20240331), baseRelation.getParams(),
                "base relation params should come from the systemSlice after preAgg rewrite");

        int topNLimit = 2;
        AxisField categoryAxis = axis("product$categoryName");
        categoryAxis.setLimit(topNLimit);
        categoryAxis.setOrderBy(List.of("-salesAmount"));

        PivotRequest pivot = new PivotRequest();
        pivot.setRows(List.of(categoryAxis));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

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
                "ORDER BY sales_amount DESC, category_name ASC " +
                "LIMIT 2";
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
        slice.setValue(2023);
        request.setSlice(List.of(slice));

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "SELECT t2.category_name as category_name, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "WHERE t3.year = 2023 " +
                "GROUP BY t2.category_name";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParity(sqlItems, pivotItems, "category_name", "product$categoryName", "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("3d. Generate / Per-Group Ranking (分组内 TopN) Parity")
    void testGeneratePerGroupTopNParity() {
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTE + ROW_NUMBER() OVER(), requires MySQL 8+ or SQLite");
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

        // SQL Oracle with Window Function (CTE or derived table)
        String sql = "WITH base AS ( " +
                "  SELECT " +
                "    t2.category_name AS category_name, " +
                "    t2.sub_category_name AS sub_category_name, " +
                "    SUM(t1.sales_amount) AS sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  GROUP BY t2.category_name, t2.sub_category_name " +
                "), " +
                "ranked AS ( " +
                "  SELECT " +
                "    *, " +
                "    ROW_NUMBER() OVER ( " +
                "      PARTITION BY category_name " +
                "      ORDER BY sales_amount DESC " +
                "    ) AS rn " +
                "  FROM base " +
                ") " +
                "SELECT category_name, sub_category_name, sales_amount " +
                "FROM ranked " +
                "WHERE rn <= 2";

        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

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

        Map<String, Long> sqlMap = sqlGrandItems.stream()
                .filter(r -> r.get("customer_type") != null)
                .collect(Collectors.toMap(
                        r -> String.valueOf(r.get("customer_type")),
                        r -> ((Number) r.get("unique_customers")).longValue()
                ));

        for (Map<String, Object> pRow : pivotGrandTotal) {
            String cType = String.valueOf(pRow.get("customer$customerType"));
            if (sqlMap.containsKey(cType)) {
                assertEquals(sqlMap.get(cType).longValue(), ((Number) pRow.get("uniqueCustomers")).longValue(), "Mismatch for " + cType);
            }
        }
        assertTrue(pivotGrandTotal.size() > 0);
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

        // Setup SemanticRequestContext representing category='Electronics' (C001)
        List<SliceRequestDef> systemSlice = List.of(new SliceRequestDef("product$categoryId", "=", "C001"));

        SemanticRequestContext ctx = SemanticRequestContext.of(null, null, null, null, systemSlice);

        SemanticQueryResponse response = execute(TEST_MODEL, request, ctx);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "SELECT t2.category_name as category_name, SUM(t1.sales_amount) as sales_amount " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "WHERE t2.category_id = 'C001' " +
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
        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        assertTrue(msg != null && (msg.contains("denied") ||
                   msg.toLowerCase().contains("permission") ||
                   msg.contains("安全") || msg.contains("权限") ||
                   msg.contains("Access") || msg.contains("受限")));
    }

    // ========== S11: parentShare Parity Tests ==========

    @Test
    @DisplayName("8. parentShare parity: 子级占比与 SQL window 比对")
    void testParentShareParity() {
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses SUM() OVER (PARTITION BY), requires MySQL 8+ or SQLite");
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

        // SQL Oracle: 子级值 / SUM(OVER 父级)
        String sql = "SELECT t2.category_name, d1.month, " +
                "SUM(t1.sales_amount) as sales_amount, " +
                "CAST(SUM(t1.sales_amount) AS REAL) / " +
                "SUM(SUM(t1.sales_amount)) OVER (PARTITION BY t2.category_name) as expected_share " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "LEFT JOIN dim_date d1 ON t1.date_key = d1.date_key " +
                "GROUP BY t2.category_name, d1.month";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        // Build lookup: category-month -> expected_share
        Map<String, Double> expectedShares = sqlItems.stream()
                .filter(r -> r.get("expected_share") != null)
                .collect(Collectors.toMap(
                        r -> r.get("category_name") + "-" + r.get("month"),
                        r -> ((Number) r.get("expected_share")).doubleValue()
                ));

        // Compare
        for (Map<String, Object> row : pivotItems) {
            String key = row.get("product$categoryName") + "-" + row.get("salesDate$month");
            if (row.get("monthShare") != null && expectedShares.containsKey(key)) {
                double actual = ((Number) row.get("monthShare")).doubleValue();
                double expected = expectedShares.get(key);
                assertEquals(expected, actual, 0.001,
                        "parentShare mismatch for " + key);
            }
        }
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
        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        assertTrue(msg != null && (msg.contains("denied") ||
                   msg.toLowerCase().contains("permission") ||
                   msg.contains("安全") || msg.contains("权限") ||
                   msg.contains("Access") || msg.contains("受限")),
                "Should fail-closed for denied column: " + msg);
        log.info("S11: parentShare deniedColumns fail-closed 验证通过");
    }

    // ========== S12 baselineRatio Parity ==========

    @Test
    @DisplayName("S12: baselineRatio parity with SQL Window functions")
    void testBaselineRatioParity() {
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");
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

        // SQL Oracle: global first/last baseline
        // BaselineRatioCalculator uses global column domain sorting, not per-row partition.
        // So "first" = globally smallest month, "last" = globally largest month.
        String sql = "WITH base AS ( " +
                "  SELECT t2.category_name as category_name, t3.month as month_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "  GROUP BY t2.category_name, t3.month " +
                "), global_bounds AS ( " +
                "  SELECT MIN(month_name) as first_month, MAX(month_name) as last_month FROM base " +
                "), first_baseline AS ( " +
                "  SELECT b.category_name, b.sales_amount as first_sales " +
                "  FROM base b, global_bounds g " +
                "  WHERE b.month_name = g.first_month " +
                "), last_baseline AS ( " +
                "  SELECT b.category_name, b.sales_amount as last_sales " +
                "  FROM base b, global_bounds g " +
                "  WHERE b.month_name = g.last_month " +
                ") " +
                "SELECT b.category_name, b.month_name, b.sales_amount, " +
                "  b.sales_amount / NULLIF(fb.first_sales, 0) as idx_first, " +
                "  b.sales_amount / NULLIF(lb.last_sales, 0) as idx_last " +
                "FROM base b " +
                "LEFT JOIN first_baseline fb ON b.category_name = fb.category_name " +
                "LEFT JOIN last_baseline lb ON b.category_name = lb.category_name";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "month_name"),
                List.of("product$categoryName", "salesDate$month"),
                "idx_first", "idxFirst");

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "month_name"),
                List.of("product$categoryName", "salesDate$month"),
                "idx_last", "idxLast");

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
        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        assertTrue(msg != null && (msg.contains("denied") || msg.toLowerCase().contains("permission") ||
                   msg.contains("安全") || msg.contains("权限") || msg.contains("Access") || msg.contains("受限")),
                "Should fail-closed for denied column: " + msg);
        log.info("S12: baselineRatio deniedColumns fail-closed 验证通过");
    }

    @Test
    @DisplayName("S12: baselineRatio + systemSlice parity")
    void testBaselineRatioSystemSliceParity() {
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");
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

        // SQL Oracle：限定 C001 后的 baselineRatio (global first)
        String sql = "WITH base AS ( " +
                "  SELECT t2.category_name as category_name, t3.month as month_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "  WHERE t2.category_id = 'CAT001' " +
                "  GROUP BY t2.category_name, t3.month " +
                "), global_bounds AS ( " +
                "  SELECT MIN(month_name) as first_month FROM base " +
                "), first_baseline AS ( " +
                "  SELECT b.category_name, b.sales_amount as first_sales " +
                "  FROM base b, global_bounds g " +
                "  WHERE b.month_name = g.first_month " +
                ") " +
                "SELECT b.category_name, b.month_name, b.sales_amount, " +
                "  b.sales_amount / NULLIF(fb.first_sales, 0) as idx_first " +
                "FROM base b " +
                "LEFT JOIN first_baseline fb ON b.category_name = fb.category_name";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "month_name"),
                List.of("product$categoryName", "salesDate$month"),
                "idx_first", "idxFirst");

        log.info("S12: baselineRatio + systemSlice Parity 验证通过");
    }

    @Test
    @DisplayName("S12: baselineRatio + user slice parity")
    void testBaselineRatioUserSliceParity() {
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");
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

        // SQL Oracle：限定 year=2024 后的 baselineRatio (global last)
        String sql = "WITH base AS ( " +
                "  SELECT t2.category_name as category_name, t3.month as month_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "  WHERE t3.year = 2024 " +
                "  GROUP BY t2.category_name, t3.month " +
                "), global_bounds AS ( " +
                "  SELECT MAX(month_name) as last_month FROM base " +
                "), last_baseline AS ( " +
                "  SELECT b.category_name, b.sales_amount as last_sales " +
                "  FROM base b, global_bounds g " +
                "  WHERE b.month_name = g.last_month " +
                ") " +
                "SELECT b.category_name, b.month_name, b.sales_amount, " +
                "  b.sales_amount / NULLIF(lb.last_sales, 0) as idx_last " +
                "FROM base b " +
                "LEFT JOIN last_baseline lb ON b.category_name = lb.category_name";
        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "month_name"),
                List.of("product$categoryName", "salesDate$month"),
                "idx_last", "idxLast");

        log.info("S12: baselineRatio + user slice Parity 验证通过");
    }

    @Test
    @DisplayName("13. SQL pushdown active + TopN + COUNT_DISTINCT + rowSubtotals/grandTotal")
    void testSqlPushdownNonAdditiveRollupWithTopNAndSubtotalsParity() {
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

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        List<Map<String, Object>> pivotLeaves = pivotItems.stream()
                .filter(r -> !r.containsKey("_sys_meta") || (!Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isRowSubtotal")) && !Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isGrandTotal"))))
                .collect(Collectors.toList());
        List<Map<String, Object>> pivotSubtotals = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isRowSubtotal")))
                .collect(Collectors.toList());
        List<Map<String, Object>> pivotGrandTotal = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isGrandTotal")))
                .collect(Collectors.toList());

        // SQL Oracle
        String top2CategorySql = "SELECT t2.category_name " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  GROUP BY t2.category_name " +
                "  ORDER BY SUM(t1.sales_amount) DESC, t2.category_name ASC " +
                "  LIMIT 2";
                
        String topNCondition = "EXISTS (SELECT 1 FROM (" + top2CategorySql + ") as top_cats " +
                "WHERE top_cats.category_name = t2.category_name OR (top_cats.category_name IS NULL AND t2.category_name IS NULL))";

        // Leaf Oracle
        String sqlLeaf = "SELECT t2.category_name as category_name, t3.month as month_name, COUNT(DISTINCT t1.customer_key) as unique_customers " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "LEFT JOIN dim_date t3 ON t1.date_key = t3.date_key " +
                "WHERE " + topNCondition + " " +
                "GROUP BY t2.category_name, t3.month";
        List<Map<String, Object>> sqlLeafItems = jdbcTemplate.queryForList(sqlLeaf);
        assertParityMultiDim(sqlLeafItems, pivotLeaves,
                List.of("category_name", "month_name"),
                List.of("product$categoryName", "salesDate$month"),
                "unique_customers", "uniqueCustomers");

        // Subtotal Oracle (Group by Category)
        String sqlSub = "SELECT t2.category_name as category_name, COUNT(DISTINCT t1.customer_key) as unique_customers " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "WHERE " + topNCondition + " " +
                "GROUP BY t2.category_name";
        List<Map<String, Object>> sqlSubItems = jdbcTemplate.queryForList(sqlSub);
        assertParity(sqlSubItems, pivotSubtotals, "category_name", "product$categoryName", "unique_customers", "uniqueCustomers");

        // Grand Total Oracle (All Surviving Domain)
        String sqlGrand = "SELECT COUNT(DISTINCT t1.customer_key) as unique_customers " +
                "FROM fact_sales t1 " +
                "LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "WHERE " + topNCondition;
        List<Map<String, Object>> sqlGrandItems = jdbcTemplate.queryForList(sqlGrand);
        assertEquals(1, pivotGrandTotal.size(), "Grand total should have 1 row");
        assertEquals(((Number) sqlGrandItems.get(0).get("unique_customers")).longValue(),
                     ((Number) pivotGrandTotal.get(0).get("uniqueCustomers")).longValue(),
                     "Grand Total COUNT_DISTINCT mismatch");

        log.info("Test 13: SQL pushdown + TopN + Non-additive + Subtotals Parity 验证通过");
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
        assertTrue(generatedSql.getSql().contains("_pivot_domain_transport_test"),
                "Generated SQL should contain the large-domain transport relation");
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

        Map<String, Double> sqlMap = sqlItems.stream()
                .filter(r -> r.get(sqlDimKey) != null && r.get(sqlMetricKey) != null)
                .collect(Collectors.toMap(
                        r -> String.valueOf(r.get(sqlDimKey)),
                        r -> ((Number) r.get(sqlMetricKey)).doubleValue()
                ));

        Map<String, Double> pivotMap = pivotItems.stream()
                .filter(r -> r.get(pivotDimKey) != null && r.get(pivotMetricKey) != null)
                .collect(Collectors.toMap(
                        r -> String.valueOf(r.get(pivotDimKey)),
                        r -> ((Number) r.get(pivotMetricKey)).doubleValue()
                ));

        assertEquals(sqlMap.size(), pivotMap.size(), "Result size mismatch between SQL and Pivot");
        for (Map.Entry<String, Double> entry : sqlMap.entrySet()) {
            assertTrue(pivotMap.containsKey(entry.getKey()), "Pivot missing key: " + entry.getKey());
            assertEquals(entry.getValue(), pivotMap.get(entry.getKey()), 0.01, "Value mismatch for " + entry.getKey());
        }
    }

    private void assertParityMultiDim(List<Map<String, Object>> sqlItems, List<Map<String, Object>> pivotItems,
                              List<String> sqlDimKeys, List<String> pivotDimKeys, String sqlMetricKey, String pivotMetricKey) {

        Map<String, Double> sqlMap = sqlItems.stream()
                .filter(r -> r.get(sqlMetricKey) != null)
                .collect(Collectors.toMap(
                        r -> sqlDimKeys.stream().map(k -> String.valueOf(r.get(k))).collect(Collectors.joining("-")),
                        r -> ((Number) r.get(sqlMetricKey)).doubleValue(),
                        (a, b) -> a  // 忽略重复 key（NULL category 等 edge case）
                ));

        Map<String, Double> pivotMap = pivotItems.stream()
                .filter(r -> r.get(pivotMetricKey) != null)
                .collect(Collectors.toMap(
                        r -> pivotDimKeys.stream().map(k -> String.valueOf(r.get(k))).collect(Collectors.joining("-")),
                        r -> ((Number) r.get(pivotMetricKey)).doubleValue(),
                        (a, b) -> a
                ));

        // 校验 Pivot 结果的每一行都与 SQL Oracle 一致（交集比较）
        assertTrue(pivotMap.size() > 0, "Pivot should have non-null results");
        for (Map.Entry<String, Double> entry : pivotMap.entrySet()) {
            assertTrue(sqlMap.containsKey(entry.getKey()), "SQL oracle missing key present in Pivot: " + entry.getKey());
            assertEquals(sqlMap.get(entry.getKey()), entry.getValue(), 0.01, "Value mismatch for " + entry.getKey());
        }
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

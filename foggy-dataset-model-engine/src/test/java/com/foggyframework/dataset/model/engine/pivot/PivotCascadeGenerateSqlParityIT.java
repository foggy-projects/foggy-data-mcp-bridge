package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.pivot.cascade.PivotCascadeErrorCode;
import com.foggyframework.dataset.model.engine.pivot.cascade.PivotCascadeException;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@DisplayName("PIVOT-91-C2 Cascade Generate SQL Parity Integration Test")
class PivotCascadeGenerateSqlParityIT extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Value("${v934.expectedDatabase:}")
    private String expectedDatabase;

    @Test
    @DisplayName("0. MySQL 5.7 rows cascade fails closed without memory fallback")
    void testMysql57RowsCascadeFailsClosedWithoutMemoryFallback() {
        PivotRequest pivot = new PivotRequest();
        AxisField category = axis("product$categoryName");
        category.setLimit(2);
        category.setOrderBy(List.of("-salesAmount"));

        AxisField subCategory = axis("product$subCategoryName");
        subCategory.setLimit(2);
        subCategory.setOrderBy(List.of("-salesAmount"));

        pivot.setRows(List.of(category, subCategory));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);

        if (supportsWindowFunctions()) {
            assertFalse("mysql57".equals(expectedDatabase),
                    "mysql57 must never advertise window-function support");
            SemanticQueryResponse response = execute(request);
            assertFalse(response.getItems().isEmpty(),
                    "window-capable databases must execute the same cascade request positively");
            return;
        }
        assertMysql57CascadeRefusal(request);
    }

    @Test
    @DisplayName("1. Parent TopN + child TopN (child domain is subset of parent)")
    void testRowsTwoLevelCascadeSubset() {
        PivotRequest pivot = new PivotRequest();
        AxisField category = axis("product$categoryName");
        category.setLimit(3);
        category.setOrderBy(List.of("-salesAmount"));

        AxisField subCategory = axis("product$subCategoryName");
        subCategory.setLimit(2);
        subCategory.setOrderBy(List.of("-salesAmount"));

        pivot.setRows(List.of(category, subCategory));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        if (assertMysql57CascadeRefusalWhenUnsupported(request)) {
            return;
        }

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();
        List<Map<String, Object>> diagnostics = pivotDiagnostics(response);
        Map<String, Object> cacheIdentity = diagnosticEvent(diagnostics, "pivot.cache.identity");
        Map<String, Object> cacheRefused = diagnosticEvent(diagnostics, "pivot.cache.refused");
        assertEquals(PivotOuterCacheStrongIdentity.STATUS_INCOMPLETE, cacheIdentity.get("status"));
        assertEquals("E1a", cacheRefused.get("eligibilityStage"));
        assertEquals("cascade_shape", cacheRefused.get("reason"));
        assertEquals("cascade", cacheRefused.get("shapeClass"));
        assertTrue(diagnostics.stream().noneMatch(item -> "pivot.cache.lookup".equals(item.get("event"))),
                "incomplete lifecycle identity must not allow cache lookup");
        assertTrue(diagnostics.stream().noneMatch(item -> "pivot.cache.store".equals(item.get("event"))),
                "incomplete lifecycle identity must not allow cache store");

        // SQL Oracle
        String sql = "WITH _base_relation AS (" +
                "  SELECT t2.category_name, t2.sub_category_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  GROUP BY t2.category_name, t2.sub_category_name" +
                "), " +
                "_level1_domain AS (" +
                "  SELECT category_name, SUM(sales_amount) as agg_sales " +
                "  FROM _base_relation GROUP BY category_name" +
                "), " +
                "_level1_ranked AS (" +
                "  SELECT category_name, ROW_NUMBER() OVER(ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC) as rn " +
                "  FROM _level1_domain" +
                "), " +
                "_level1_filtered AS (" +
                "  SELECT category_name FROM _level1_ranked WHERE rn <= 3" +
                "), " +
                "_level2_domain AS (" +
                "  SELECT b.category_name, b.sub_category_name, SUM(b.sales_amount) as agg_sales " +
                "  FROM _base_relation b " +
                "  INNER JOIN _level1_filtered f1 ON b.category_name = f1.category_name " +
                "  GROUP BY b.category_name, b.sub_category_name" +
                "), " +
                "_level2_ranked AS (" +
                "  SELECT *, ROW_NUMBER() OVER(PARTITION BY category_name ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC, CASE WHEN sub_category_name IS NULL THEN 1 ELSE 0 END ASC, sub_category_name ASC) as rn " +
                "  FROM _level2_domain" +
                "), " +
                "_level2_filtered AS (" +
                "  SELECT category_name, sub_category_name FROM _level2_ranked WHERE rn <= 2" +
                ") " +
                "SELECT b.category_name, b.sub_category_name, b.sales_amount " +
                "FROM _base_relation b " +
                "INNER JOIN _level2_filtered f2 ON b.category_name = f2.category_name AND b.sub_category_name = f2.sub_category_name";

        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "sub_category_name"),
                List.of("product$categoryName", "product$subCategoryName"),
                "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("2. Parent ranking ignores child limit (parent rank unchanged by child limit)")
    void testParentRankingIgnoresChildLimit() {
        PivotRequest pivot = new PivotRequest();
        AxisField category = axis("product$categoryName");
        category.setLimit(2);
        category.setOrderBy(List.of("-salesAmount"));

        // Only take the top 1 sub-category
        AxisField subCategory = axis("product$subCategoryName");
        subCategory.setLimit(1);
        subCategory.setOrderBy(List.of("-salesAmount"));

        pivot.setRows(List.of(category, subCategory));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        if (assertMysql57CascadeRefusalWhenUnsupported(request)) {
            return;
        }

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "WITH _base_relation AS (" +
                "  SELECT t2.category_name, t2.sub_category_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  GROUP BY t2.category_name, t2.sub_category_name" +
                "), " +
                "_level1_domain AS (" +
                "  SELECT category_name, SUM(sales_amount) as agg_sales " +
                "  FROM _base_relation GROUP BY category_name" +
                "), " +
                "_level1_ranked AS (" +
                "  SELECT category_name, ROW_NUMBER() OVER(ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC) as rn " +
                "  FROM _level1_domain" +
                "), " +
                "_level1_filtered AS (" +
                "  SELECT category_name FROM _level1_ranked WHERE rn <= 2" +
                "), " +
                "_level2_domain AS (" +
                "  SELECT b.category_name, b.sub_category_name, SUM(b.sales_amount) as agg_sales " +
                "  FROM _base_relation b " +
                "  INNER JOIN _level1_filtered f1 ON b.category_name = f1.category_name " +
                "  GROUP BY b.category_name, b.sub_category_name" +
                "), " +
                "_level2_ranked AS (" +
                "  SELECT *, ROW_NUMBER() OVER(PARTITION BY category_name ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC, CASE WHEN sub_category_name IS NULL THEN 1 ELSE 0 END ASC, sub_category_name ASC) as rn " +
                "  FROM _level2_domain" +
                "), " +
                "_level2_filtered AS (" +
                "  SELECT category_name, sub_category_name FROM _level2_ranked WHERE rn <= 1" +
                ") " +
                "SELECT b.category_name, b.sub_category_name, b.sales_amount " +
                "FROM _base_relation b " +
                "INNER JOIN _level2_filtered f2 ON b.category_name = f2.category_name AND b.sub_category_name = f2.sub_category_name";

        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "sub_category_name"),
                List.of("product$categoryName", "product$subCategoryName"),
                "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("3. Parent having filters before child rank")
    void testParentHavingFiltersBeforeChildRank() {
        PivotRequest pivot = new PivotRequest();
        AxisField category = axis("product$categoryName");
        // Only keep categories with > 5000 sales
        category.setHaving(List.of(filter("salesAmount", ">", 5000)));

        AxisField subCategory = axis("product$subCategoryName");
        subCategory.setLimit(2);
        subCategory.setOrderBy(List.of("-salesAmount"));

        pivot.setRows(List.of(category, subCategory));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        if (assertMysql57CascadeRefusalWhenUnsupported(request)) {
            return;
        }

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "WITH _base_relation AS (" +
                "  SELECT t2.category_name, t2.sub_category_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  GROUP BY t2.category_name, t2.sub_category_name" +
                "), " +
                "_level1_domain AS (" +
                "  SELECT category_name, SUM(sales_amount) as agg_sales " +
                "  FROM _base_relation GROUP BY category_name" +
                "), " +
                "_level1_filtered AS (" +
                "  SELECT category_name FROM _level1_domain WHERE agg_sales > 5000" +
                "), " +
                "_level2_domain AS (" +
                "  SELECT b.category_name, b.sub_category_name, SUM(b.sales_amount) as agg_sales " +
                "  FROM _base_relation b " +
                "  INNER JOIN _level1_filtered f1 ON b.category_name = f1.category_name " +
                "  GROUP BY b.category_name, b.sub_category_name" +
                "), " +
                "_level2_ranked AS (" +
                "  SELECT *, ROW_NUMBER() OVER(PARTITION BY category_name ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC, CASE WHEN sub_category_name IS NULL THEN 1 ELSE 0 END ASC, sub_category_name ASC) as rn " +
                "  FROM _level2_domain" +
                "), " +
                "_level2_filtered AS (" +
                "  SELECT category_name, sub_category_name FROM _level2_ranked WHERE rn <= 2" +
                ") " +
                "SELECT b.category_name, b.sub_category_name, b.sales_amount " +
                "FROM _base_relation b " +
                "INNER JOIN _level2_filtered f2 ON b.category_name = f2.category_name AND b.sub_category_name = f2.sub_category_name";

        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "sub_category_name"),
                List.of("product$categoryName", "product$subCategoryName"),
                "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("4. Child having does not affect parent")
    void testChildHavingDoesNotAffectParent() {
        PivotRequest pivot = new PivotRequest();
        AxisField category = axis("product$categoryName");
        // Parent limit 3
        category.setLimit(3);
        category.setOrderBy(List.of("-salesAmount"));

        AxisField subCategory = axis("product$subCategoryName");
        // Child limit 2 and having > 2000
        subCategory.setLimit(2);
        subCategory.setOrderBy(List.of("-salesAmount"));
        subCategory.setHaving(List.of(filter("salesAmount", ">", 2000)));

        pivot.setRows(List.of(category, subCategory));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        // Subtotals are needed to prove parent survives even if child having removes all children
        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        if (assertMysql57CascadeRefusalWhenUnsupported(request)) {
            return;
        }

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle for subtotals ONLY
        String sqlSub = "WITH _base_relation AS (" +
                "  SELECT t2.category_name, t2.sub_category_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  GROUP BY t2.category_name, t2.sub_category_name" +
                "), " +
                "_level1_domain AS (" +
                "  SELECT category_name, SUM(sales_amount) as agg_sales " +
                "  FROM _base_relation GROUP BY category_name" +
                "), " +
                "_level1_ranked AS (" +
                "  SELECT category_name, ROW_NUMBER() OVER(ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC) as rn " +
                "  FROM _level1_domain" +
                "), " +
                "_level1_filtered AS (" +
                "  SELECT category_name FROM _level1_ranked WHERE rn <= 3" +
                "), " +
                "_level2_domain AS (" +
                "  SELECT b.category_name, b.sub_category_name, SUM(b.sales_amount) as agg_sales " +
                "  FROM _base_relation b " +
                "  INNER JOIN _level1_filtered f1 ON b.category_name = f1.category_name " +
                "  GROUP BY b.category_name, b.sub_category_name" +
                "), " +
                "_level2_domain_filtered AS (" +
                "  SELECT * FROM _level2_domain WHERE agg_sales > 2000" +
                "), " +
                "_level2_ranked AS (" +
                "  SELECT *, ROW_NUMBER() OVER(PARTITION BY category_name ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC, CASE WHEN sub_category_name IS NULL THEN 1 ELSE 0 END ASC, sub_category_name ASC) as rn " +
                "  FROM _level2_domain_filtered" +
                "), " +
                "_level2_filtered AS (" +
                "  SELECT category_name, sub_category_name FROM _level2_ranked WHERE rn <= 2" +
                "), " +
                "_final_cells AS (" +
                "  SELECT b.category_name, b.sub_category_name, b.sales_amount " +
                "  FROM _base_relation b " +
                "  INNER JOIN _level2_filtered f2 ON b.category_name = f2.category_name AND b.sub_category_name = f2.sub_category_name" +
                ") " +
                "SELECT category_name, SUM(sales_amount) as sales_amount FROM _final_cells GROUP BY category_name";

        List<Map<String, Object>> sqlSubItems = jdbcTemplate.queryForList(sqlSub);

        List<Map<String, Object>> pivotSubtotals = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isRowSubtotal")))
                .collect(Collectors.toList());

        assertParity(sqlSubItems, pivotSubtotals, "category_name", "product$categoryName", "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("6. Deterministic tie with NULL buckets")
    void testDeterministicTieWithNullBuckets() {
        PivotRequest pivot = new PivotRequest();
        // Since we want to test tie breaking, we order by a metric that has duplicates or nulls.
        // We will order by orderCount, which might have many 1s.
        AxisField category = axis("product$categoryName");
        category.setLimit(5);
        category.setOrderBy(List.of("-salesAmount"));

        AxisField subCategory = axis("product$subCategoryName");
        subCategory.setLimit(2);
        subCategory.setOrderBy(List.of("-salesAmount"));

        pivot.setRows(List.of(category, subCategory));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        if (assertMysql57CascadeRefusalWhenUnsupported(request)) {
            return;
        }

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle
        String sql = "WITH _base_relation AS (" +
                "  SELECT t2.category_name, t2.sub_category_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  GROUP BY t2.category_name, t2.sub_category_name" +
                "), " +
                "_level1_domain AS (" +
                "  SELECT category_name, SUM(sales_amount) as agg_order " +
                "  FROM _base_relation GROUP BY category_name" +
                "), " +
                "_level1_ranked AS (" +
                "  SELECT category_name, ROW_NUMBER() OVER(ORDER BY CASE WHEN agg_order IS NULL THEN 1 ELSE 0 END ASC, agg_order DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC) as rn " +
                "  FROM _level1_domain" +
                "), " +
                "_level1_filtered AS (" +
                "  SELECT category_name FROM _level1_ranked WHERE rn <= 5" +
                "), " +
                "_level2_domain AS (" +
                "  SELECT b.category_name, b.sub_category_name, SUM(b.sales_amount) as agg_order " +
                "  FROM _base_relation b " +
                "  INNER JOIN _level1_filtered f1 ON (b.category_name = f1.category_name OR (b.category_name IS NULL AND f1.category_name IS NULL)) " +
                "  GROUP BY b.category_name, b.sub_category_name" +
                "), " +
                "_level2_ranked AS (" +
                "  SELECT *, ROW_NUMBER() OVER(PARTITION BY category_name ORDER BY CASE WHEN agg_order IS NULL THEN 1 ELSE 0 END ASC, agg_order DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC, CASE WHEN sub_category_name IS NULL THEN 1 ELSE 0 END ASC, sub_category_name ASC) as rn " +
                "  FROM _level2_domain" +
                "), " +
                "_level2_filtered AS (" +
                "  SELECT category_name, sub_category_name FROM _level2_ranked WHERE rn <= 2" +
                ") " +
                "SELECT b.category_name, b.sub_category_name, b.sales_amount " +
                "FROM _base_relation b " +
                "INNER JOIN _level2_filtered f2 ON (b.category_name = f2.category_name OR (b.category_name IS NULL AND f2.category_name IS NULL)) AND (b.sub_category_name = f2.sub_category_name OR (b.sub_category_name IS NULL AND f2.sub_category_name IS NULL))";

        List<Map<String, Object>> sqlItems = jdbcTemplate.queryForList(sql);

        assertParityMultiDim(sqlItems, pivotItems,
                List.of("category_name", "sub_category_name"),
                List.of("product$categoryName", "product$subCategoryName"),
                "sales_amount", "salesAmount");
    }

    @Test
    @DisplayName("7. Additive row subtotal surviving domain")
    void testAdditiveRowSubtotalSurvivingDomain() {
        PivotRequest pivot = new PivotRequest();
        AxisField category = axis("product$categoryName");
        category.setLimit(2);
        category.setOrderBy(List.of("-salesAmount"));

        AxisField subCategory = axis("product$subCategoryName");
        subCategory.setLimit(2);
        subCategory.setOrderBy(List.of("-salesAmount"));

        pivot.setRows(List.of(category, subCategory));
        pivot.setMetrics(List.of("salesAmount"));
        pivot.setOutputFormat("flat");

        PivotOptions options = new PivotOptions();
        options.setRowSubtotals(true);
        options.setGrandTotal(true);
        pivot.setOptions(options);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setPivot(pivot);
        if (assertMysql57CascadeRefusalWhenUnsupported(request)) {
            return;
        }

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();

        // SQL Oracle (Simulating surviving cells total)
        String sqlBase = "WITH _base_relation AS (" +
                "  SELECT t2.category_name, t2.sub_category_name, SUM(t1.sales_amount) as sales_amount " +
                "  FROM fact_sales t1 " +
                "  LEFT JOIN dim_product t2 ON t1.product_key = t2.product_key " +
                "  GROUP BY t2.category_name, t2.sub_category_name" +
                "), " +
                "_level1_domain AS (" +
                "  SELECT category_name, SUM(sales_amount) as agg_sales " +
                "  FROM _base_relation GROUP BY category_name" +
                "), " +
                "_level1_ranked AS (" +
                "  SELECT category_name, ROW_NUMBER() OVER(ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC) as rn " +
                "  FROM _level1_domain" +
                "), " +
                "_level1_filtered AS (" +
                "  SELECT category_name FROM _level1_ranked WHERE rn <= 2" +
                "), " +
                "_level2_domain AS (" +
                "  SELECT b.category_name, b.sub_category_name, SUM(b.sales_amount) as agg_sales " +
                "  FROM _base_relation b " +
                "  INNER JOIN _level1_filtered f1 ON b.category_name = f1.category_name " +
                "  GROUP BY b.category_name, b.sub_category_name" +
                "), " +
                "_level2_ranked AS (" +
                "  SELECT *, ROW_NUMBER() OVER(PARTITION BY category_name ORDER BY CASE WHEN agg_sales IS NULL THEN 1 ELSE 0 END ASC, agg_sales DESC, CASE WHEN category_name IS NULL THEN 1 ELSE 0 END ASC, category_name ASC, CASE WHEN sub_category_name IS NULL THEN 1 ELSE 0 END ASC, sub_category_name ASC) as rn " +
                "  FROM _level2_domain" +
                "), " +
                "_level2_filtered AS (" +
                "  SELECT category_name, sub_category_name FROM _level2_ranked WHERE rn <= 2" +
                "), " +
                "_final_cells AS (" +
                "  SELECT b.category_name, b.sub_category_name, b.sales_amount " +
                "  FROM _base_relation b " +
                "  INNER JOIN _level2_filtered f2 ON b.category_name = f2.category_name AND b.sub_category_name = f2.sub_category_name" +
                ") ";

        // Grand Total Oracle
        String sqlGrand = sqlBase + "SELECT SUM(sales_amount) as sales_amount FROM _final_cells";
        List<Map<String, Object>> sqlGrandItems = jdbcTemplate.queryForList(sqlGrand);

        List<Map<String, Object>> pivotGrandTotal = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isGrandTotal")))
                .collect(Collectors.toList());

        assertEquals(1, pivotGrandTotal.size());
        assertEquals(((Number) sqlGrandItems.get(0).get("sales_amount")).doubleValue(),
                     ((Number) pivotGrandTotal.get(0).get("salesAmount")).doubleValue(), 0.01);

        // Subtotal Oracle
        String sqlSub = sqlBase + "SELECT category_name, SUM(sales_amount) as sales_amount FROM _final_cells GROUP BY category_name";
        List<Map<String, Object>> sqlSubItems = jdbcTemplate.queryForList(sqlSub);

        List<Map<String, Object>> pivotSubtotals = pivotItems.stream()
                .filter(r -> r.containsKey("_sys_meta") && Boolean.TRUE.equals(((Map)r.get("_sys_meta")).get("isRowSubtotal"))
                        && "ALL".equals(r.get("product$subCategoryName"))) // Exclude other subtotals if any
                .collect(Collectors.toList());

        assertParity(sqlSubItems, pivotSubtotals, "category_name", "product$categoryName", "sales_amount", "salesAmount");
    }

    // ========== Helpers ==========

    private boolean assertMysql57CascadeRefusalWhenUnsupported(SemanticQueryRequest request) {
        if (supportsWindowFunctions()) {
            return false;
        }
        assertMysql57CascadeRefusal(request);
        return true;
    }

    private void assertMysql57CascadeRefusal(SemanticQueryRequest request) {
        assertEquals("mysql57", expectedDatabase,
                "the only required non-window matrix lane must be mysql57");
        assertEquals("mysql", getDialectKey(),
                "the mysql57 refusal must come from the MySQL dialect");
        PivotCascadeException ex = assertThrows(PivotCascadeException.class, () -> execute(request));
        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_SQL_REQUIRED, ex.getCode());
        assertTrue(ex.getMessage().contains("Multi-level TopN requires staged SQL execution"));
        assertTrue(ex.getMessage().contains("Planner failure"));
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

        List<Map<String, Object>> pivotLeaves = pivotItems.stream()
                .filter(r -> !r.containsKey("_sys_meta"))
                .collect(Collectors.toList());
        assertFalse(sqlItems.isEmpty(), "SQL oracle must return deterministic rows");
        assertEquals(sqlItems.size(), pivotLeaves.size(),
                "SQL oracle and Pivot leaves must expose the same row cardinality");
        Map<List<Object>, Double> sqlMap = metricIndex(sqlItems, sqlDimKeys, sqlMetricKey, "SQL");
        Map<List<Object>, Double> pivotMap = metricIndex(pivotLeaves, pivotDimKeys, pivotMetricKey, "Pivot");

        assertEquals(sqlMap.keySet(), pivotMap.keySet(), "Dimension tuples differ between SQL and Pivot");
        for (Map.Entry<List<Object>, Double> entry : sqlMap.entrySet()) {
            assertEquals(entry.getValue(), pivotMap.get(entry.getKey()), 0.01,
                    "Value mismatch for " + entry.getKey());
        }
    }

    private Map<List<Object>, Double> metricIndex(List<Map<String, Object>> rows,
                                                   List<String> dimensionKeys,
                                                   String metricKey,
                                                   String label) {
        Map<List<Object>, Double> index = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> tuple = dimensionTuple(row, dimensionKeys, label + " dimension");
            assertFalse(index.containsKey(tuple), label + " contains a duplicate dimension tuple: " + tuple);
            index.put(tuple, requiredMetric(row, metricKey, label + " metric"));
        }
        return index;
    }

    private List<Object> dimensionTuple(Map<String, Object> row, List<String> keys, String label) {
        List<Object> tuple = new ArrayList<>(keys.size());
        for (String key : keys) {
            assertTrue(row.containsKey(key), label + " column is missing: " + key + " in " + row.keySet());
            Object value = row.get(key);
            tuple.add(value instanceof Number
                    ? new BigDecimal(value.toString()).stripTrailingZeros()
                    : value);
        }
        return tuple;
    }

    private double requiredMetric(Map<String, Object> row, String key, String label) {
        assertTrue(row.containsKey(key), label + " column is missing: " + key + " in " + row.keySet());
        assertTrue(row.get(key) instanceof Number, label + " must be numeric: " + key + "=" + row.get(key));
        return ((Number) row.get(key)).doubleValue();
    }

    private SemanticQueryResponse execute(SemanticQueryRequest request) {
        return execute(TEST_MODEL, request, SemanticRequestContext.empty());
    }

    private SemanticQueryResponse execute(String model, SemanticQueryRequest request, SemanticRequestContext ctx) {
        return semanticQueryServiceV3.queryModel(model, request, "execute", ctx);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pivotDiagnostics(SemanticQueryResponse response) {
        assertTrue(response.getDebug() != null, "pivot response should include debug info");
        assertTrue(response.getDebug().getExtra() != null, "pivot response should include debug.extra");
        Object diagnostics = response.getDebug().getExtra().get("pivotDiagnostics");
        assertTrue(diagnostics instanceof List<?>, "debug.extra should include pivotDiagnostics");
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

    private AxisField axis(String field) {
        AxisField f = new AxisField();
        f.setField(field);
        return f;
    }

    private MetricFilter filter(String metric, String op, Number value) {
        MetricFilter filter = new MetricFilter();
        filter.setMetric(metric);
        filter.setOp(op);
        filter.setValue(value);
        return filter;
    }
}

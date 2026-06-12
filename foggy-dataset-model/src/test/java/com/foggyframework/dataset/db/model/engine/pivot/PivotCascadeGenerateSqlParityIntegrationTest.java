package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.pivot.cascade.PivotCascadeErrorCode;
import com.foggyframework.dataset.db.model.engine.pivot.cascade.PivotCascadeException;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.MetricFilter;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotOptions;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Slf4j
@DisplayName("PIVOT-91-C2 Cascade Generate SQL Parity Integration Test")
class PivotCascadeGenerateSqlParityIntegrationTest extends EcommerceTestSupport {

    private static final String TEST_MODEL = "FactSalesQueryModel";

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Test
    @DisplayName("0. MySQL 5.7 rows cascade fails closed without memory fallback")
    void testMysql57RowsCascadeFailsClosedWithoutMemoryFallback() {
        assumeTrue(activeProfiles.contains("docker"),
                "Skipping: MySQL 5.7 live refusal evidence only runs on docker profile");
        assumeTrue("mysql".equals(getDialectKey()) && !supportsWindowFunctions(),
                "Skipping: requires MySQL 5.7/non-window MySQL profile");

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

        PivotCascadeException ex = assertThrows(PivotCascadeException.class, () -> execute(request));
        assertEquals(PivotCascadeErrorCode.PIVOT_CASCADE_SQL_REQUIRED, ex.getCode());
        assertTrue(ex.getMessage().contains("Multi-level TopN requires staged SQL execution"));
        assertTrue(ex.getMessage().contains("Planner failure"));
    }

    @Test
    @DisplayName("1. Parent TopN + child TopN (child domain is subset of parent)")
    void testRowsTwoLevelCascadeSubset() {
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");

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

        SemanticQueryResponse response = execute(request);
        List<Map<String, Object>> pivotItems = response.getItems();
        Map<String, Object> cacheRefused = diagnosticEvent(pivotDiagnostics(response), "pivot.cache.refused");
        assertEquals("E1a", cacheRefused.get("eligibilityStage"));
        assertEquals("cascade_shape", cacheRefused.get("reason"));
        assertEquals("cascade", cacheRefused.get("shapeClass"));

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
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");

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
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");

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
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");

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
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");

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
        assumeTrue(supportsWindowFunctions(), "Skipping: SQL Oracle uses CTEs, requires MySQL 8+ or SQLite");

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
                .filter(r -> r.get(pivotMetricKey) != null && !r.containsKey("_sys_meta")) // Ignore subtotals
                .collect(Collectors.toMap(
                        r -> pivotDimKeys.stream().map(k -> String.valueOf(r.get(k))).collect(Collectors.joining("-")),
                        r -> ((Number) r.get(pivotMetricKey)).doubleValue(),
                        (a, b) -> a
                ));

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

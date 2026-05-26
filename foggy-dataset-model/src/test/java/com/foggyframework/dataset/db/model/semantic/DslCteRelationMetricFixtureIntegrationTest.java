package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslCteRelationMetricFixtureIntegrationTest extends EcommerceTestSupport {

    @Resource
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @Test
    @DisplayName("DSL_CTE relation metric ratio orderBy bridge executes and matches SQLite baseline")
    void relationMetricRatioOrderByBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = categoryProfitRateLeaderboardManualRows();
        SemanticQueryRequest request = dslCtePlan(categoryProfitRateLeaderboardPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "FactSalesQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio"), generated.getSql());
        assertTrue(generated.getSql().contains("ORDER BY \"profitRate\" DESC"), generated.getSql());
        assertTrue(generated.getSql().contains("LIMIT ?"), generated.getSql());
        assertEquals(List.of(2), generated.getParams());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCategoryProfitRateRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE relation CASE label bridge executes split derive stages and matches SQLite baseline")
    void relationCaseLabelBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = categoryProfitBandLeaderboardManualRows();
        SemanticQueryRequest request = dslCtePlan(categoryProfitBandLeaderboardPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "FactSalesQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio_1"), generated.getSql());
        assertTrue(generated.getSql().contains(
                "CASE WHEN \"profitRate\" < 0.2 THEN 'low' ELSE 'normal' END AS \"profitBand\""),
                generated.getSql());
        assertTrue(generated.getSql().contains("ORDER BY \"profitRate\" DESC"), generated.getSql());
        assertEquals(List.of(2), generated.getParams());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCategoryProfitBandRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE relation ordered numeric bucket bridge executes and matches SQLite baseline")
    void relationOrderedNumericBucketBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = categoryProfitBucketLeaderboardManualRows();
        SemanticQueryRequest request = dslCtePlan(categoryProfitBucketLeaderboardPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "FactSalesQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio_1"), generated.getSql());
        assertTrue(generated.getSql().contains(
                        "CASE WHEN \"profitRate\" < 0.1 THEN 'very_low' "
                                + "WHEN \"profitRate\" < 0.2 THEN 'low' ELSE 'normal' END AS \"profitBand\""),
                generated.getSql());
        assertTrue(generated.getSql().contains("ORDER BY \"profitRate\" DESC"), generated.getSql());
        assertEquals(List.of(2), generated.getParams());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCategoryProfitBandRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE relation ordered numeric bucket label postSlice executes and matches SQLite baseline")
    void relationOrderedNumericBucketLabelPostSliceSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = categoryProfitBucketNormalRows();
        SemanticQueryRequest request = dslCtePlan(categoryProfitBucketLabelPostSlicePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "FactSalesQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio_1"), generated.getSql());
        assertTrue(generated.getSql().contains(
                        "CASE WHEN \"profitRate\" < 0.1 THEN 'very_low' "
                                + "WHEN \"profitRate\" < 0.2 THEN 'low' ELSE 'normal' END AS \"profitBand\""),
                generated.getSql());
        assertTrue(generated.getSql().contains("FROM dsl_cte_metric_ratio\nWHERE \"profitBand\" = ?"),
                generated.getSql());
        assertTrue(generated.getSql().contains("ORDER BY \"profitRate\" DESC, \"product$categoryName\" ASC"),
                generated.getSql());
        assertEquals(List.of("normal", 5), generated.getParams());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));

        assertTrue(!rows.isEmpty(), "fixture should contain at least one normal profit bucket row");
        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCategoryProfitBandRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE relation metric difference orderBy bridge executes and matches SQLite baseline")
    void relationMetricDifferenceOrderByBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = categoryNonProfitAmountLeaderboardManualRows();
        SemanticQueryRequest request = dslCtePlan(categoryNonProfitAmountLeaderboardPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "FactSalesQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("dsl_cte_metric_ratio"), generated.getSql());
        assertTrue(generated.getSql().contains("\"salesAmount\" - \"profitAmount\" AS \"nonProfitAmount\""),
                generated.getSql());
        assertTrue(generated.getSql().contains("ORDER BY \"nonProfitAmount\" DESC"), generated.getSql());
        assertTrue(generated.getSql().contains("LIMIT ?"), generated.getSql());
        assertEquals(List.of(2), generated.getParams());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCategoryNonProfitAmountRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    @Test
    @DisplayName("DSL_CTE conditional value aggregate difference bridge executes and matches SQLite baseline")
    void conditionalValueAggregateDifferenceBridgeSqlMatchesManualBaseline() {
        assumeCommonTableExpressionsSupported();

        List<Map<String, Object>> manualRows = categoryNonCompletedSalesLeaderboardManualRows();
        SemanticQueryRequest request = dslCtePlan(categoryNonCompletedSalesLeaderboardPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SemanticQueryResponse response = semanticQueryServiceV3.validateQuery(
                "FactSalesQueryModel", request, SemanticRequestContext.empty());
        Map<String, Object> aggregateContract = stageContract(response, "category_completed_sales",
                "aggregate_contract");
        assertEquals("conditional_value_aggregation", aggregateContract.get("kind"));
        assertEquals(true, aggregateContract.get("bridge_signed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultStageBridge = (Map<String, Object>) response.getExecution()
                .getDslCteValidation().get("dsl_result_stage_metric_ratio");
        assertEquals("relation_metric_arithmetic", resultStageBridge.get("kind"));

        SqlGenerationResult generated = semanticQueryServiceV3.generateSql(
                "FactSalesQueryModel", request, SemanticRequestContext.empty());

        assertNotNull(generated);
        assertNotNull(generated.getSql());
        assertTrue(generated.getSql().contains("completedSalesAmount"), generated.getSql());
        assertTrue(generated.getSql().contains(
                "\"totalSalesAmount\" - \"completedSalesAmount\" AS \"nonCompletedSalesAmount\""),
                generated.getSql());
        assertTrue(generated.getSql().contains(
                "ORDER BY \"nonCompletedSalesAmount\" DESC, \"product$categoryName\" ASC"), generated.getSql());

        List<Map<String, Object>> rows = new ArrayList<>(jdbcTemplate.queryForList(
                generated.getSql(), generated.getParams().toArray(new Object[0])));

        assertEquals(manualRows.size(), rows.size());
        for (int i = 0; i < manualRows.size(); i++) {
            assertGeneratedCategoryNonCompletedSalesRowMatchesManual(rows.get(i), manualRows.get(i));
        }
    }

    private List<Map<String, Object>> categoryProfitRateLeaderboardManualRows() {
        return jdbcTemplate.queryForList("""
                SELECT dp.category_name AS categoryName,
                       SUM(fs.sales_amount) AS salesAmount,
                       SUM(fs.profit_amount) AS profitAmount,
                       1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) AS profitRate
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY profitRate DESC
                LIMIT 2
                """);
    }

    private List<Map<String, Object>> categoryNonProfitAmountLeaderboardManualRows() {
        return jdbcTemplate.queryForList("""
                SELECT dp.category_name AS categoryName,
                       SUM(fs.sales_amount) AS salesAmount,
                       SUM(fs.profit_amount) AS profitAmount,
                       SUM(fs.sales_amount) - SUM(fs.profit_amount) AS nonProfitAmount
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY nonProfitAmount DESC
                LIMIT 2
                """);
    }

    private List<Map<String, Object>> categoryProfitBandLeaderboardManualRows() {
        return jdbcTemplate.queryForList("""
                SELECT dp.category_name AS categoryName,
                       SUM(fs.sales_amount) AS salesAmount,
                       SUM(fs.profit_amount) AS profitAmount,
                       1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) AS profitRate,
                       CASE WHEN 1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) < 0.2
                            THEN 'low' ELSE 'normal' END AS profitBand
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY profitRate DESC
                LIMIT 2
                """);
    }

    private List<Map<String, Object>> categoryProfitBucketLeaderboardManualRows() {
        return jdbcTemplate.queryForList("""
                SELECT dp.category_name AS categoryName,
                       SUM(fs.sales_amount) AS salesAmount,
                       SUM(fs.profit_amount) AS profitAmount,
                       1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) AS profitRate,
                       CASE WHEN 1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) < 0.1
                            THEN 'very_low'
                            WHEN 1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) < 0.2
                            THEN 'low'
                            ELSE 'normal' END AS profitBand
                FROM fact_sales fs
                LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                GROUP BY dp.category_name
                ORDER BY profitRate DESC
                LIMIT 2
                """);
    }

    private List<Map<String, Object>> categoryProfitBucketNormalRows() {
        return jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dp.category_name AS categoryName,
                           SUM(fs.sales_amount) AS salesAmount,
                           SUM(fs.profit_amount) AS profitAmount,
                           1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) AS profitRate,
                           CASE WHEN 1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) < 0.1
                                THEN 'very_low'
                                WHEN 1.0 * SUM(fs.profit_amount) / NULLIF(SUM(fs.sales_amount), 0) < 0.2
                                THEN 'low'
                                ELSE 'normal' END AS profitBand
                    FROM fact_sales fs
                    LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                    GROUP BY dp.category_name
                )
                WHERE profitBand = 'normal'
                ORDER BY profitRate DESC, categoryName ASC
                LIMIT 5
                """);
    }

    private List<Map<String, Object>> categoryNonCompletedSalesLeaderboardManualRows() {
        return jdbcTemplate.queryForList("""
                SELECT *
                FROM (
                    SELECT dp.category_name AS categoryName,
                           SUM(fs.sales_amount) AS totalSalesAmount,
                           SUM(CASE WHEN fs.order_status = 'COMPLETED' THEN fs.sales_amount ELSE 0 END)
                               AS completedSalesAmount,
                           SUM(fs.sales_amount)
                               - SUM(CASE WHEN fs.order_status = 'COMPLETED' THEN fs.sales_amount ELSE 0 END)
                               AS nonCompletedSalesAmount
                    FROM fact_sales fs
                    LEFT JOIN dim_product dp ON fs.product_key = dp.product_key
                    GROUP BY dp.category_name
                )
                ORDER BY nonCompletedSalesAmount DESC, categoryName ASC
                LIMIT 3
                """);
    }

    private static void assertGeneratedCategoryProfitRateRowMatchesManual(Map<String, Object> generated,
                                                                          Map<String, Object> manual) {
        assertEquals(manual.get("categoryName"), value(generated, "product$categoryName"));
        assertDecimalClose(manual.get("salesAmount"), value(generated, "salesAmount"));
        assertDecimalClose(manual.get("profitAmount"), value(generated, "profitAmount"));
        assertDecimalClose(manual.get("profitRate"), value(generated, "profitRate"));
    }

    private static void assertGeneratedCategoryNonProfitAmountRowMatchesManual(Map<String, Object> generated,
                                                                               Map<String, Object> manual) {
        assertEquals(manual.get("categoryName"), value(generated, "product$categoryName"));
        assertDecimalClose(manual.get("salesAmount"), value(generated, "salesAmount"));
        assertDecimalClose(manual.get("profitAmount"), value(generated, "profitAmount"));
        assertDecimalClose(manual.get("nonProfitAmount"), value(generated, "nonProfitAmount"));
    }

    private static void assertGeneratedCategoryProfitBandRowMatchesManual(Map<String, Object> generated,
                                                                          Map<String, Object> manual) {
        assertEquals(manual.get("categoryName"), value(generated, "product$categoryName"));
        assertDecimalClose(manual.get("salesAmount"), value(generated, "salesAmount"));
        assertDecimalClose(manual.get("profitAmount"), value(generated, "profitAmount"));
        assertDecimalClose(manual.get("profitRate"), value(generated, "profitRate"));
        assertEquals(manual.get("profitBand"), value(generated, "profitBand"));
    }

    private static void assertGeneratedCategoryNonCompletedSalesRowMatchesManual(Map<String, Object> generated,
                                                                                 Map<String, Object> manual) {
        assertEquals(manual.get("categoryName"), value(generated, "product$categoryName"));
        assertDecimalClose(manual.get("totalSalesAmount"), value(generated, "totalSalesAmount"));
        assertDecimalClose(manual.get("completedSalesAmount"), value(generated, "completedSalesAmount"));
        assertDecimalClose(manual.get("nonCompletedSalesAmount"), value(generated, "nonCompletedSalesAmount"));
    }

    private static Map<String, Object> stageContract(SemanticQueryResponse response,
                                                     String stageName,
                                                     String contractKey) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) response.getExecution()
                .getDslCteValidation().get("stages");
        return stages.stream()
                .filter(stage -> stageName.equals(stage.get("name")))
                .findFirst()
                .map(stage -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> contract = (Map<String, Object>) stage.get(contractKey);
                    return contract;
                })
                .orElseThrow(() -> new AssertionError("Missing contract " + contractKey + " for stage " + stageName));
    }

    private static SemanticQueryRequest dslCtePlan(Map<String, Object> ctePlan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL_CTE");
        request.setExecutablePlan(m("cte_plan", ctePlan));
        return request;
    }

    private static Map<String, Object> categoryProfitRateLeaderboardPlan() {
        return m(
                "stages", List.of(
                        stage("category_profit", "aggregate",
                                "input", m("model", "FactSalesQueryModel"),
                                "groupBy", List.of("product$categoryName"),
                                "metrics", List.of(
                                        m("name", "salesAmount", "expr", "sum(salesAmount)"),
                                        m("name", "profitAmount", "expr", "sum(profitAmount)"))),
                        stage("category_profit_rate", "derive",
                                "inputs", List.of("category_profit"),
                                "derived", List.of(
                                        m("name", "profitRate", "expr", "profitAmount / nullif(salesAmount, 0)"))),
                        stage("top_categories", "orderBy",
                                "inputs", List.of("category_profit_rate"),
                                "orderBy", List.of(
                                        m("field", "profitRate", "dir", "DESC")),
                                "limit", 2)
                ),
                "output", List.of("product$categoryName", "salesAmount", "profitAmount", "profitRate")
        );
    }

    private static Map<String, Object> categoryNonProfitAmountLeaderboardPlan() {
        return m(
                "stages", List.of(
                        stage("category_profit", "aggregate",
                                "input", m("model", "FactSalesQueryModel"),
                                "groupBy", List.of("product$categoryName"),
                                "metrics", List.of(
                                        m("name", "salesAmount", "expr", "sum(salesAmount)"),
                                        m("name", "profitAmount", "expr", "sum(profitAmount)"))),
                        stage("category_non_profit_amount", "derive",
                                "inputs", List.of("category_profit"),
                                "derived", List.of(
                                        m("name", "nonProfitAmount", "expr", "salesAmount - profitAmount"))),
                        stage("top_categories", "orderBy",
                                "inputs", List.of("category_non_profit_amount"),
                                "orderBy", List.of(
                                        m("field", "nonProfitAmount", "dir", "DESC")),
                                "limit", 2)
                ),
                "output", List.of("product$categoryName", "salesAmount", "profitAmount", "nonProfitAmount")
        );
    }

    private static Map<String, Object> categoryProfitBandLeaderboardPlan() {
        return m(
                "stages", List.of(
                        stage("category_profit", "aggregate",
                                "input", m("model", "FactSalesQueryModel"),
                                "groupBy", List.of("product$categoryName"),
                                "metrics", List.of(
                                        m("name", "salesAmount", "expr", "sum(salesAmount)"),
                                        m("name", "profitAmount", "expr", "sum(profitAmount)"))),
                        stage("category_profit_rate", "derive",
                                "inputs", List.of("category_profit"),
                                "derived", List.of(
                                        m("name", "profitRate", "expr", "profitAmount / nullif(salesAmount, 0)"))),
                        stage("category_profit_band", "derive",
                                "inputs", List.of("category_profit_rate"),
                                "derived", List.of(
                                        m("name", "profitBand",
                                                "expr", "case when profitRate < 0.2 then 'low' else 'normal' end"))),
                        stage("top_categories", "orderBy",
                                "inputs", List.of("category_profit_band"),
                                "orderBy", List.of(
                                        m("field", "profitRate", "dir", "DESC")),
                                "limit", 2)
                ),
                "output", List.of("product$categoryName", "salesAmount", "profitAmount", "profitRate",
                        "profitBand")
        );
    }

    private static Map<String, Object> categoryProfitBucketLeaderboardPlan() {
        return m(
                "stages", List.of(
                        stage("category_profit", "aggregate",
                                "input", m("model", "FactSalesQueryModel"),
                                "groupBy", List.of("product$categoryName"),
                                "metrics", List.of(
                                        m("name", "salesAmount", "expr", "sum(salesAmount)"),
                                        m("name", "profitAmount", "expr", "sum(profitAmount)"))),
                        stage("category_profit_rate", "derive",
                                "inputs", List.of("category_profit"),
                                "derived", List.of(
                                        m("name", "profitRate", "expr", "profitAmount / nullif(salesAmount, 0)"))),
                        stage("category_profit_band", "derive",
                                "inputs", List.of("category_profit_rate"),
                                "derived", List.of(
                                        m("name", "profitBand",
                                                "expr", "case when profitRate < 0.1 then 'very_low' "
                                                        + "when profitRate < 0.2 then 'low' else 'normal' end"))),
                        stage("top_categories", "orderBy",
                                "inputs", List.of("category_profit_band"),
                                "orderBy", List.of(
                                        m("field", "profitRate", "dir", "DESC")),
                                "limit", 2)
                ),
                "output", List.of("product$categoryName", "salesAmount", "profitAmount", "profitRate",
                        "profitBand")
        );
    }

    private static Map<String, Object> categoryProfitBucketLabelPostSlicePlan() {
        return m(
                "stages", List.of(
                        stage("category_profit", "aggregate",
                                "input", m("model", "FactSalesQueryModel"),
                                "groupBy", List.of("product$categoryName"),
                                "metrics", List.of(
                                        m("name", "salesAmount", "expr", "sum(salesAmount)"),
                                        m("name", "profitAmount", "expr", "sum(profitAmount)"))),
                        stage("category_profit_rate", "derive",
                                "inputs", List.of("category_profit"),
                                "derived", List.of(
                                        m("name", "profitRate", "expr", "profitAmount / nullif(salesAmount, 0)"))),
                        stage("category_profit_band", "derive",
                                "inputs", List.of("category_profit_rate"),
                                "derived", List.of(
                                        m("name", "profitBand",
                                                "expr", "case when profitRate < 0.1 then 'very_low' "
                                                        + "when profitRate < 0.2 then 'low' else 'normal' end"))),
                        stage("normal_profit_categories", "postSlice",
                                "inputs", List.of("category_profit_band"),
                                "filters", List.of(
                                        m("field", "profitBand", "op", "=", "value", "normal"))),
                        stage("top_normal_profit_categories", "orderBy",
                                "inputs", List.of("normal_profit_categories"),
                                "orderBy", List.of(
                                        m("field", "profitRate", "dir", "DESC"),
                                        m("field", "product$categoryName", "dir", "ASC")),
                                "limit", 5)
                ),
                "output", List.of("product$categoryName", "salesAmount", "profitAmount", "profitRate",
                        "profitBand")
        );
    }

    private static Map<String, Object> categoryNonCompletedSalesLeaderboardPlan() {
        return m(
                "stages", List.of(
                        stage("category_completed_sales", "aggregate",
                                "input", m("model", "FactSalesQueryModel"),
                                "groupBy", List.of("product$categoryName"),
                                "metrics", List.of(
                                        m("name", "totalSalesAmount", "expr", "sum(salesAmount)"),
                                        m("name", "completedSalesAmount", "expr",
                                                "sum(case when orderStatus = 'COMPLETED' "
                                                        + "then salesAmount else 0 end)"))),
                        stage("category_non_completed_sales", "derive",
                                "inputs", List.of("category_completed_sales"),
                                "derived", List.of(
                                        m("name", "nonCompletedSalesAmount",
                                                "expr", "totalSalesAmount - completedSalesAmount"))),
                        stage("top_non_completed_categories", "orderBy",
                                "inputs", List.of("category_non_completed_sales"),
                                "orderBy", List.of(
                                        m("field", "nonCompletedSalesAmount", "dir", "DESC"),
                                        m("field", "product$categoryName", "dir", "ASC")),
                                "limit", 3)
                ),
                "output", List.of("product$categoryName", "totalSalesAmount", "completedSalesAmount",
                        "nonCompletedSalesAmount")
        );
    }

    private static Map<String, Object> stage(String name, String type, Object... rest) {
        Map<String, Object> result = m(rest);
        result.put("name", name);
        result.put("type", type);
        return result;
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            result.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return result;
    }

    private static Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) {
            return row.get(key);
        }
        throw new AssertionError("Missing key " + key + " in " + row);
    }

    private static void assertDecimalClose(Object expected, Object actual) {
        BigDecimal delta = BigDecimal.valueOf(((Number) actual).doubleValue())
                .subtract(BigDecimal.valueOf(((Number) expected).doubleValue()))
                .abs();
        assertTrue(delta.compareTo(BigDecimal.valueOf(0.000001)) <= 0,
                "expected=" + expected + ", actual=" + actual);
    }
}

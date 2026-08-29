package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import com.foggyframework.dataset.model.def.query.request.*;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.formula.SqlFormulaService;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePreparation;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * CTE Wrapping 集成测试
 * <p>
 * Validates the two-stage SQL generation architecture for Window Calculated Fields.
 * When a CF uses explicit partitionBy/windowOrderBy, the engine should:
 * <ul>
 *   <li>Stage 1 (CTE): Generate base SQL without window CFs</li>
 *   <li>Stage 2 (outer): Wrap Stage 1 in a CTE, add window CFs with alias-based references</li>
 *   <li>Elevate ORDER BY to Stage 2</li>
 *   <li>Preserve backward compatibility for non-windowed queries (single-pass)</li>
 * </ul>
 * </p>
 *
 * @author Foggy
 * @since 9.2.0
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("CTE Wrapping 两阶段 SQL 生成测试")
class JdbcModelQueryEngineCteWrapTest extends EcommerceTestSupport {

    @Resource
    private SqlFormulaService sqlFormulaService;

    @Resource
    private SystemBundlesContext systemBundlesContext;

    // ==========================================
    // CTE Structure Validation
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("Window CF with partitionBy/windowOrderBy generates CTE-wrapped SQL")
    void testCteStructureForRankWindow() {
        if (!supportsWindowFunctions()) {
            log.info("当前数据库不支持窗口函数，跳过");
            return;
        }
        JdbcModelQueryEngine engine = buildRankWindowEngine();
        String sql = engine.getSql();
        assertNotNull(sql, "SQL 生成失败");
        printSql(sql, "CTE Wrapping: RANK()");

        // Verify CTE structure
        assertTrue(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "Should generate CTE wrapper: " + sql);
        assertTrue(sql.toUpperCase().contains("FROM STAGE1"),
                "Outer query should reference stage1 CTE: " + sql);
        assertTrue(sql.toUpperCase().contains("RANK()"),
                "Should contain RANK() window function: " + sql);
        assertTrue(sql.toUpperCase().contains("OVER"),
                "Should contain OVER clause: " + sql);
    }

    @Test
    @Order(2)
    @DisplayName("CTE inner SQL does NOT contain window CF column")
    void testCteInnerSqlExcludesWindowColumn() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildRankWindowEngine();
        String sql = engine.getSql();

        // The CTE inner SQL (between "AS (" and ")") should NOT contain RANK()
        int cteStart = sql.toUpperCase().indexOf("AS (");
        int cteEnd = sql.toUpperCase().indexOf(")\nSELECT");
        if (cteEnd < 0) {
            cteEnd = sql.toUpperCase().indexOf(")\r\nSELECT");
        }
        assertTrue(cteStart > 0 && cteEnd > cteStart, "CTE block should be present");

        String cteSql = sql.substring(cteStart + 4, cteEnd);
        assertFalse(cteSql.toUpperCase().contains("RANK()"),
                "CTE inner SQL should NOT contain RANK(): " + cteSql);
        assertFalse(cteSql.toUpperCase().contains("OVER"),
                "CTE inner SQL should NOT contain OVER clause: " + cteSql);
    }

    @Test
    @Order(3)
    @DisplayName("Window CF base expression uses alias-based column references (not physical)")
    void testCteWindowExprUsesAliases() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildRankWindowEngine();
        String sql = engine.getSql();

        // The outer SELECT's window expression should reference aliases, not table.column
        // e.g., ORDER BY "salesAmount" not ORDER BY t1.sales_amount
        String outerSql = extractOuterSelect(sql);
        assertFalse(outerSql.contains("t1.sales_amount"),
                "Window OVER clause should NOT reference physical column t1.sales_amount: " + outerSql);
        assertTrue(outerSql.contains("\"salesAmount\""),
                "Window OVER clause should reference alias \"salesAmount\": " + outerSql);
    }

    @Test
    @Order(4)
    @DisplayName("ORDER BY is elevated to Stage 2 outer query")
    void testOrderByElevatedToStage2() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildRankWindowRequest();
        request.setOrderBy(new ArrayList<>(List.of(
                orderAsc("product$categoryName"),
                orderDesc("salesAmount")
        )));

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();

        // ORDER BY should appear AFTER the CTE wrapper, not inside it
        int cteEnd = sql.toUpperCase().indexOf("FROM STAGE1");
        // Use lastIndexOf because ORDER BY also appears inside OVER() clause
        int orderByIdx = sql.toUpperCase().lastIndexOf("ORDER BY");
        assertTrue(cteEnd > 0 && orderByIdx > cteEnd,
                "ORDER BY should be elevated to outer query (after FROM stage1): " + sql);
    }

    @Test
    @Order(5)
    @DisplayName("postSlice over window alias creates explicit result filter stage")
    void testPostSliceOverWindowAliasCreatesResultStage() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildRankWindowRequest();
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("salesRank", "=", 1))));
        request.setOrderBy(new ArrayList<>(List.of(orderDesc("salesRank"))));

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();
        String normalizedSql = sql.replace('`', '"');

        assertTrue(normalizedSql.contains("WITH stage1 AS"), sql);
        assertTrue(normalizedSql.contains("__POST_RESULT_STAGE__ AS"), sql);
        assertTrue(normalizedSql.contains("FROM __POST_RESULT_STAGE__"), sql);
        assertTrue(normalizedSql.contains("WHERE \"salesRank\" = ?"), sql);
        assertTrue(normalizedSql.contains("ORDER BY \"salesRank\" DESC"), sql);
        assertEquals(1, engine.getValues().get(engine.getValues().size() - 1));
        assertEquals(2, engine.getCteStages().size());
        assertEquals(List.of(1), engine.getCteOuterSelectParams());
    }

    @Test
    @Order(6)
    @DisplayName("postSlice without result stage is rejected")
    void testPostSliceWithoutResultStageRejected() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of("product$caption", "salesAmount")));
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("salesAmount", ">", 100))));

        AnalysisFailure failure = analyzeFailureWithContext(request, null);
        Map<String, Object> plan = queryStagePlan(failure.context());

        assertTrue(failure.exception().getMessage().contains("POST_SLICE_REQUIRES_RESULT_STAGE"),
                failure.exception().getMessage());
        assertTrue(listValue(plan, "unsupported").contains("post-slice-result-stage-required"),
                plan.toString());
    }

    @Test
    @Order(7)
    @DisplayName("Regex edge case: Substring collision prevention in CTE rewriting")
    void testRegexSubstringCollision() {
        String sql = "SUM(t1.sales) / MAX(t1.sales_tax) + t1.sales";
        String physicalSql = "t1.sales";
        String aliasSql = "\"sales\"";

        String regex = "(?<![\\\\p{L}0-9_$])" + java.util.regex.Pattern.quote(physicalSql) + "(?![\\\\p{L}0-9_$])";
        String replaced = sql.replaceAll(regex, java.util.regex.Matcher.quoteReplacement(aliasSql));

        assertEquals("SUM(\"sales\") / MAX(t1.sales_tax) + \"sales\"", replaced, 
                "Should only replace exact matches on word boundaries");

        // Edge case: end of string
        assertEquals("\"sales\"", "t1.sales".replaceAll(regex, aliasSql));

        // Edge case: start of string
        assertEquals("\"sales\" / 2", "t1.sales / 2".replaceAll(regex, aliasSql));
        
        // Edge case: unicode letters
        String unicodeSql = "t1.销售额 + t1.销售额_tax";
        String unicodePhysical = "t1.销售额";
        String unicodeAlias = "\"sales\"";
        String unicodeRegex = "(?<![\\\\p{L}0-9_$])" + java.util.regex.Pattern.quote(unicodePhysical) + "(?![\\\\p{L}0-9_$])";
        assertEquals("\"sales\" + t1.销售额_tax", unicodeSql.replaceAll(unicodeRegex, unicodeAlias));
    }

    // ==========================================
    // Execution Correctness
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("CTE-wrapped RANK() query executes correctly and returns results")
    void testCteRankExecutes() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildRankWindowEngine();
        String sql = engine.getSql();
        List<Map<String, Object>> results = executeQuery(sql);
        assertNotNull(results);
        assertFalse(results.isEmpty(), "CTE-wrapped RANK query should return results");
        printResults(results);

        // Verify each row has a salesRank
        for (Map<String, Object> row : results) {
            assertNotNull(row.get("salesRank"), "Each row should have a salesRank value");
        }
    }

    @Test
    @Order(11)
    @DisplayName("CTE-wrapped LAG() query executes correctly")
    void testCteLagExecutes() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildLagWindowEngine();
        String sql = engine.getSql();
        printSql(sql, "CTE Wrapping: LAG()");

        assertTrue(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "LAG should trigger CTE wrapping: " + sql);
        assertTrue(sql.toUpperCase().contains("LAG("),
                "Should contain LAG function: " + sql);

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty(), "CTE-wrapped LAG query should return results");
    }

    @Test
    @Order(12)
    @DisplayName("CTE-wrapped moving average with window frame executes correctly")
    void testCteMovingAverageExecutes() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcModelQueryEngine engine = buildMovingAverageEngine();
        String sql = engine.getSql();
        printSql(sql, "CTE Wrapping: Moving Average");

        assertTrue(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "Moving average should trigger CTE wrapping: " + sql);
        assertTrue(sql.toUpperCase().contains("ROWS BETWEEN"),
                "Should contain window frame: " + sql);

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
    }

    @Test
    @Order(13)
    @DisplayName("Running SUM with ordinary slice and postSlice executes and matches hand-written SQL")
    void testRunningSumPostSliceExecutesAndMatchesHandWrittenSql() {
        assumeCommonTableExpressionsSupported();

        if (!supportsWindowFunctions()) {
            log.info("running SUM postSlice parity not executed on {}", getDialectKey());
            return;
        }

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of("orderId", "salesAmount", "runningSalesAmount")));
        request.setGroupBy(buildGroupBy("orderId"));
        request.setSlice(new ArrayList<>(List.of(new SliceRequestDef("orderStatus", "=", "COMPLETED"))));
        int threshold = runningSumPostSliceThreshold();

        CalculatedFieldDef runningSalesAmount = new CalculatedFieldDef();
        runningSalesAmount.setName("runningSalesAmount");
        runningSalesAmount.setCaption("累计销售额");
        runningSalesAmount.setExpression("SUM(salesAmount)");
        runningSalesAmount.setWindowOrderBy(new ArrayList<>(List.of(
                new WindowOrderDef("salesAmount", "desc"),
                new WindowOrderDef("orderId", "asc")
        )));
        runningSalesAmount.setWindowFrame("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW");
        request.setCalculatedFields(new ArrayList<>(List.of(runningSalesAmount)));
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("runningSalesAmount", "<=", threshold))));
        request.setOrderBy(new ArrayList<>(List.of(
                orderDesc("salesAmount"),
                orderAsc("orderId")
        )));

        JdbcModelQueryEngine engine = analyze(request);
        String sql = engine.getSql();
        String normalizedSql = sql.replace('`', '"');
        printSql(sql, "CTE Wrapping: Running SUM postSlice parity");

        assertTrue(normalizedSql.contains("__POST_RESULT_STAGE__ AS"), sql);
        assertTrue(normalizedSql.toUpperCase().contains("SUM("), sql);
        assertTrue(normalizedSql.toUpperCase().contains("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"), sql);
        assertTrue(engine.getValues().contains("COMPLETED"), "ordinary slice value should be bound");
        assertTrue(engine.getValues().contains(threshold), "postSlice threshold should be bound");

        List<Map<String, Object>> actual = jdbcTemplate.queryForList(
                sql, engine.getValues().toArray(new Object[0]));
        List<Map<String, Object>> expected = executeQuery(handWrittenRunningSumSql(threshold));

        assertFalse(expected.isEmpty(), "hand-written running SUM baseline should not be empty");
        assertRowsEqualInOrder(expected, actual, sql);
    }

    // ==========================================
    // Backward Compatibility: Single-Pass
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("Non-windowed query remains single-pass (no CTE)")
    void testSinglePassForNonWindowQuery() {
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList("product$categoryName", "salesAmount"));
        request.setGroupBy(buildGroupBy("product$categoryName"));

        engine.analysisQueryRequest(systemBundlesContext, request);
        String sql = engine.getSql();

        assertFalse(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "Non-windowed query should NOT use CTE wrapping: " + sql);
    }

    @Test
    @Order(21)
    @DisplayName("CALCULATE-generated window (SUM(SUM(x)) OVER()) remains single-pass")
    void testSinglePassForCalculateWindow() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of(
                "customer$customerType", "salesAmount", "totalShare"
        )));
        request.setCalculatedFields(new ArrayList<>(List.of(
                new CalculatedFieldDef(
                        "totalShare", "总占比",
                        "SUM(salesAmount) / NULLIF(CALCULATE(SUM(salesAmount), REMOVE(customer$customerType)), 0)"
                )
        )));
        request.setGroupBy(buildGroupBy("customer$customerType"));
        request.setOrderBy(new ArrayList<>(List.of(orderAsc("customer$customerType"))));

        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        engine.analysisQueryRequest(systemBundlesContext, request);

        String sql = engine.getSql();
        assertFalse(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "CALCULATE-generated window should NOT use CTE wrapping: " + sql);
        assertTrue(sql.toUpperCase().contains("OVER ()"),
                "CALCULATE should use inline window: " + sql);

        // Verify execution
        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
    }

    @Test
    @Order(22)
    @DisplayName("Stage planner diagnostics keep non-window aggregate query single-stage")
    void testStagePlannerDiagnosticsForSinglePassAggregate() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of("product$categoryName", "salesAmount")));
        request.setGroupBy(buildGroupBy("product$categoryName"));
        request.setReturnTotal(true);

        AnalysisResult result = analyzeWithContext(request);
        Map<String, Object> plan = queryStagePlan(result.context());

        assertEquals(QueryStagePlan.VERSION, plan.get("version"));
        assertEquals(true, plan.get("enabled"));
        assertEquals("single", plan.get("renderStrategy"));
        assertEquals("final-stage-count", plan.get("returnTotalStrategy"));
        assertEquals("final", plan.get("finalCountStageId"));
        assertEquals("final-stage-sql-without-order", plan.get("countSqlInput"));
        assertEquals("optimizer-allowed", plan.get("aggSqlOptimizationPolicy"));
        assertEquals("optimizer-allowed", plan.get("preAggOptimizationPolicy"));
        assertEquals(List.of(), plan.get("fallbacks"));
        assertEquals(List.of(), plan.get("unsupported"));

        assertEquals(List.of("row", "agg", "final"), stageIds(plan));
        assertEquals("ROW_STAGE", stage(plan, "row").get("type"));
        assertEquals("AGGREGATE_STAGE", stage(plan, "agg").get("type"));
        assertEquals("FINAL_STAGE", stage(plan, "final").get("type"));
        assertEquals(false, stage(plan, "row").get("requiresSqlBoundary"));
        assertEquals(false, stage(plan, "agg").get("requiresSqlBoundary"));
        assertEquals(false, stage(plan, "final").get("requiresSqlBoundary"));
        assertFalse(result.engine().getSql().toUpperCase().contains("WITH STAGE1 AS"),
                "Planner diagnostics must not change SQL rendering: " + result.engine().getSql());
    }

    @Test
    @Order(23)
    @DisplayName("Stage planner diagnostics expose window result and postSlice aliases")
    void testStagePlannerDiagnosticsForWindowPostSlice() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildRankWindowRequest();
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("salesRank", "=", 1))));
        request.setOrderBy(new ArrayList<>(List.of(orderDesc("salesRank"))));
        request.setReturnTotal(true);

        AnalysisResult result = analyzeWithContext(request);
        Map<String, Object> plan = queryStagePlan(result.context());

        assertEquals(expectedMultiStageRenderStrategy(), plan.get("renderStrategy"));
        assertEquals("final-stage-count", plan.get("returnTotalStrategy"));
        assertEquals("final-stage-sql-without-order", plan.get("countSqlInput"));
        assertEquals("preserve-final-stage-sql", plan.get("aggSqlOptimizationPolicy"));
        assertEquals("skip-final-stage-required", plan.get("preAggOptimizationPolicy"));
        assertEquals(List.of("row", "window_result", "final"), stageIds(plan));

        Map<String, Object> windowStage = stage(plan, "window_result");
        assertEquals("WINDOW_RESULT_STAGE", windowStage.get("type"));
        assertTrue(listValue(windowStage, "outputAliases").contains("salesRank"));
        assertEquals(List.of("salesRank"), windowStage.get("filterAliases"));
        assertEquals(true, windowStage.get("requiresSqlBoundary"));
        assertEquals(1, windowStage.get("parameterCount"));
        assertEquals(List.of("salesRank"), stage(plan, "final").get("orderAliases"));
        assertEquals(false, stage(plan, "final").get("requiresSqlBoundary"));
        assertTrue(result.engine().getSql().contains("__POST_RESULT_STAGE__"),
                "Existing window SQL wrapping should remain active: " + result.engine().getSql());
        assertNull(result.engine().getAggSqlOptimizationResult(),
                "Result-stage filters require returnTotal to preserve the final SQL stage");
        assertFinalTotalMatchesRows(result.engine());
    }

    @Test
    @Order(24)
    @DisplayName("Stage planner diagnostics expose post-aggregate stage aliases")
    void testStagePlannerDiagnosticsForPostAggregate() {
        DbQueryRequestDef request = buildPostAggregateSalesShareRequest();

        AnalysisResult result = analyzeWithContext(request);
        Map<String, Object> plan = queryStagePlan(result.context());

        assertEquals(expectedMultiStageRenderStrategy(), plan.get("renderStrategy"));
        assertEquals("disabled", plan.get("returnTotalStrategy"));
        assertEquals("disabled", plan.get("countSqlInput"));
        assertEquals("preserve-final-stage-sql", plan.get("aggSqlOptimizationPolicy"));
        assertEquals("skip-final-stage-required", plan.get("preAggOptimizationPolicy"));
        assertEquals(List.of("row", "agg", "post_agg", "window_result", "final"), stageIds(plan));

        Map<String, Object> postAggStage = stage(plan, "post_agg");
        assertEquals("POST_AGGREGATE_STAGE", postAggStage.get("type"));
        assertTrue(listValue(postAggStage, "outputAliases").contains("salesShare"));
        assertTrue(listValue(postAggStage, "inputAliases").contains("teamSales"));
        assertEquals(true, postAggStage.get("requiresSqlBoundary"));

        Map<String, Object> resultStage = stage(plan, "window_result");
        assertEquals("WINDOW_RESULT_STAGE", resultStage.get("type"));
        assertTrue(listValue(resultStage, "inputAliases").contains("salesShare"));
        assertEquals(List.of("salesShare"), resultStage.get("filterAliases"));
        assertEquals(1, resultStage.get("parameterCount"));
        assertPostAggregateRenderingMatchesPlan(result.engine(), plan);
    }

    @Test
    @Order(25)
    @DisplayName("Post-aggregate renderer uses derived table fallback when CTE is unsupported")
    void testPostAggregateDerivedFallbackWhenCteUnsupported() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildPostAggregateSalesShareRequest();
        request.setReturnTotal(true);
        request.setOrderBy(new ArrayList<>(List.of(orderDesc("salesShare"))));

        AnalysisResult result = analyzeWithContext(request, new NoCteSqliteDialect());
        Map<String, Object> plan = queryStagePlan(result.context());
        String sql = result.engine().getSql();

        assertEquals("derived", plan.get("renderStrategy"));
        assertEquals("final-stage-count", plan.get("returnTotalStrategy"));
        assertEquals("final-stage-sql-without-order", plan.get("countSqlInput"));
        assertEquals("preserve-final-stage-sql", plan.get("aggSqlOptimizationPolicy"));
        assertEquals("skip-final-stage-required", plan.get("preAggOptimizationPolicy"));
        assertEquals(List.of("sqlite-derived-table"), plan.get("fallbacks"));
        assertFalse(result.engine().isCteWrapped(), "Derived fallback should not expose structured CTE stages");
        assertEquals(List.of(), result.engine().getCteStages());
        assertFalse(sql.toUpperCase().contains("WITH "), "Derived fallback must not emit WITH: " + sql);
        assertTrue(sql.contains("post_stage"), "Derived fallback should still expose the post stage alias: " + sql);
        assertTrue(sql.contains("FROM (\nSELECT"), "Derived fallback should nest the planned stage SQL: " + sql);

        assertNull(result.engine().getAggSqlOptimizationResult(),
                "Derived stage fallback should preserve final-stage SQL for returnTotal");
        assertFinalTotalMatchesRows(result.engine());
    }

    @Test
    @Order(99)
    @DisplayName("Post-aggregate MAIN/TOTAL bind one request-scoped graph + base projection preparation")
    void testPostAggregateMainBindsSharedResultStagePreparation() throws Exception {
        DbQueryRequestDef request = buildPostAggregateSalesShareRequest();

        AnalysisResult result = analyzeWithContext(request);
        Map<String, Object> diagnostics = queryStagePlan(result.context());
        java.lang.reflect.Field field = JdbcModelQueryEngine.class.getDeclaredField(
                "resultStagePreparation");
        field.setAccessible(true);
        ResultStagePreparation preparation = (ResultStagePreparation) field.get(result.engine());
        ResultStagePlan.Graph graph = preparation.graph();

        assertNotNull(preparation,
                "postAggregate MAIN must prepare graph and base projections before visitor");
        assertEquals(diagnostics, graph.diagnostics().toDiagnosticsMap());
        assertEquals(stageIds(diagnostics),
                graph.stages().stream().map(ResultStagePlan.Stage::stageId).toList());
        assertFalse(preparation.baseProjectionPlan().main().columns().isEmpty());
        assertFalse(preparation.baseProjectionPlan().total().columns().isEmpty());
        ResultStagePlan.Stage postAggregate = graph.stage("post_agg");
        assertNotNull(postAggregate);
        assertEquals(List.of("salesShare"),
                postAggregate.computedColumns().stream().map(ResultStagePlan.Column::alias).toList());
        assertEquals(1, graph.stage("window_result").filters().size());
        assertTrue(graph.stage("window_result").filters().get(0).sql().contains("salesShare"));
    }

    @Test
    @Order(26)
    @DisplayName("Post-aggregate returnTotal counts the filtered final result stage")
    void testPostAggregateReturnTotalCountsFilteredFinalStage() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildPostAggregateSalesShareRequest();
        request.setReturnTotal(true);

        AnalysisResult result = analyzeWithContext(request);
        Map<String, Object> plan = queryStagePlan(result.context());

        assertEquals("final-stage-count", plan.get("returnTotalStrategy"));
        assertEquals("final-stage-sql-without-order", plan.get("countSqlInput"));
        assertEquals("preserve-final-stage-sql", plan.get("aggSqlOptimizationPolicy"));
        assertEquals("skip-final-stage-required", plan.get("preAggOptimizationPolicy"));
        assertNull(result.engine().getAggSqlOptimizationResult(),
                "Post-aggregate returnTotal should not optimize away final-stage filters");
        assertFinalTotalMatchesRows(result.engine());
    }

    @Test
    @Order(27)
    @DisplayName("Window result stage fails closed when dialect does not support window functions")
    void testWindowStageFailsClosedWhenWindowFunctionsUnsupported() {
        DbQueryRequestDef request = buildRankWindowRequest();

        AnalysisFailure failure = analyzeFailureWithContext(request, new NoWindowSqliteDialect());
        Map<String, Object> plan = queryStagePlan(failure.context());

        assertTrue(failure.exception().getMessage().contains("WINDOW_RESULT_STAGE_WINDOW_FUNCTION_UNSUPPORTED"),
                failure.exception().getMessage());
        assertTrue(listValue(plan, "unsupported").contains("window-functions-unsupported"), plan.toString());
        assertEquals("cte", plan.get("renderStrategy"));
        assertEquals(List.of("row", "window_result", "final"), stageIds(plan));
    }

    @Test
    @Order(28)
    @DisplayName("Window result stage fails closed when CTE is unavailable")
    void testWindowStageFailsClosedWhenCteUnsupported() {
        DbQueryRequestDef request = buildRankWindowRequest();

        AnalysisFailure failure = analyzeFailureWithContext(request, new NoCteWindowSqliteDialect());
        Map<String, Object> plan = queryStagePlan(failure.context());

        assertTrue(failure.exception().getMessage().contains("WINDOW_RESULT_STAGE_DERIVED_RENDERING_UNSUPPORTED"),
                failure.exception().getMessage());
        assertEquals("derived", plan.get("renderStrategy"));
        assertEquals(List.of("sqlite-derived-table"), plan.get("fallbacks"));
        assertTrue(listValue(plan, "unsupported").contains("window-derived-rendering-unsupported"), plan.toString());
        assertEquals(List.of("row", "window_result", "final"), stageIds(plan));
    }

    @Test
    @Order(29)
    @DisplayName("SqlGenerationResult carries stage diagnostics for compose")
    void testSqlGenerationResultCarriesStageDiagnosticsForCompose() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildRankWindowRequest();
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertTrue(queryModel instanceof JdbcQueryModelImpl, "test fixture should use JdbcQueryModelImpl");
        ModelResultContext context = new ModelResultContext(PagingRequest.buildPagingRequest(request, 100), null);

        SqlGenerationResult result = ((JdbcQueryModelImpl) queryModel).generateSql(systemBundlesContext, context);

        assertTrue(result.hasCteStages());
        assertTrue(result.getDiagnostics().containsKey(QueryStagePlan.EXT_DATA_KEY));
        Object raw = result.getDiagnostics().get(QueryStagePlan.EXT_DATA_KEY);
        assertTrue(raw instanceof Map<?, ?>);
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) raw;
        assertEquals("cte", plan.get("renderStrategy"));
        assertEquals("preserve-final-stage-sql", plan.get("aggSqlOptimizationPolicy"));
        assertEquals("skip-final-stage-required", plan.get("preAggOptimizationPolicy"));
        assertEquals(plan, context.getExtData().get(QueryStagePlan.EXT_DATA_KEY));
    }

    @Test
    @Order(30)
    @DisplayName("Post-aggregate and window result stage mix fails closed through planner diagnostics")
    void testPostAggregateWindowMixFailsClosedThroughPlannerDiagnostics() {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef request = buildPostAggregateSalesShareRequest();
        CalculatedFieldDef movingAvg = new CalculatedFieldDef();
        movingAvg.setName("stage5MovingAvg");
        movingAvg.setExpression("AVG(teamSales)");
        movingAvg.setPartitionBy(Arrays.asList("product$categoryName"));
        movingAvg.setWindowOrderBy(Arrays.asList(new WindowOrderDef("teamSales", "desc")));
        movingAvg.setWindowFrame("ROWS BETWEEN 1 PRECEDING AND CURRENT ROW");
        request.setCalculatedFields(new ArrayList<>(List.of(movingAvg)));
        request.getColumns().add("stage5MovingAvg");

        AnalysisFailure failure = analyzeFailureWithContext(request, null);
        Map<String, Object> plan = queryStagePlan(failure.context());

        assertTrue(failure.exception().getMessage().contains("POST_AGGREGATE_WINDOW_MIX_UNSUPPORTED"),
                failure.exception().getMessage());
        assertTrue(listValue(plan, "unsupported").contains("post-aggregate-window-mix-unsupported"),
                plan.toString());
        assertEquals(List.of("row", "agg", "post_agg", "window_result", "final"), stageIds(plan));
    }

    // ==========================================
    // QM Predefined Window CFs
    // ==========================================

    @Test
    @Order(31)
    @DisplayName("QM predefined window CF uses CTE wrapping and executes correctly")
    void testQmPredefinedWindowWithCte() {
        if (!supportsWindowFunctions()) {
            return;
        }
        JdbcQueryModel queryModel = getQueryModel("FactSalesQueryModel");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);

        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "salesRank"
        ));

        engine.analysisQueryRequest(systemBundlesContext, request);
        String sql = engine.getSql();
        printSql(sql, "QM Predefined salesRank via CTE");

        assertTrue(sql.toUpperCase().contains("WITH STAGE1 AS"),
                "QM predefined window CF should use CTE wrapping: " + sql);
        assertTrue(sql.toUpperCase().contains("RANK()"),
                "Should contain RANK(): " + sql);

        List<Map<String, Object>> results = executeQuery(sql);
        assertFalse(results.isEmpty());
    }

    // ==========================================
    // Helpers
    // ==========================================

    private JdbcModelQueryEngine buildRankWindowEngine() {
        return analyze(buildRankWindowRequest());
    }

    private DbQueryRequestDef buildRankWindowRequest() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef rank = new CalculatedFieldDef();
        rank.setName("salesRank");
        rank.setCaption("销售排名");
        rank.setExpression("RANK()");
        rank.setPartitionBy(Arrays.asList("product$categoryName"));
        rank.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesAmount", "desc")
        ));
        request.setCalculatedFields(new ArrayList<>(List.of(rank)));
        request.setColumns(Arrays.asList(
                "product$categoryName", "product$caption", "salesAmount", "salesRank"
        ));
        return request;
    }

    private JdbcModelQueryEngine buildLagWindowEngine() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef lag = new CalculatedFieldDef();
        lag.setName("prevAmount");
        lag.setCaption("上期销售额");
        lag.setExpression("LAG(salesAmount, 1)");
        lag.setPartitionBy(Arrays.asList("product$caption"));
        lag.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesDate$caption", "asc")
        ));
        request.setCalculatedFields(new ArrayList<>(List.of(lag)));
        request.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "prevAmount"
        ));
        return analyze(request);
    }

    private JdbcModelQueryEngine buildMovingAverageEngine() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");

        CalculatedFieldDef ma = new CalculatedFieldDef();
        ma.setName("ma3");
        ma.setCaption("3行移动平均");
        ma.setExpression("AVG(salesAmount)");
        ma.setPartitionBy(Arrays.asList("product$caption"));
        ma.setWindowOrderBy(Arrays.asList(
                new WindowOrderDef("salesDate$caption", "asc")
        ));
        ma.setWindowFrame("ROWS BETWEEN 2 PRECEDING AND CURRENT ROW");
        request.setCalculatedFields(new ArrayList<>(List.of(ma)));
        request.setColumns(Arrays.asList(
                "product$caption", "salesDate$caption", "salesAmount", "ma3"
        ));
        return analyze(request);
    }

    private DbQueryRequestDef buildPostAggregateSalesShareRequest() {
        DbQueryRequestDef request = new DbQueryRequestDef();
        request.setQueryModel("FactSalesQueryModel");
        request.setColumns(new ArrayList<>(List.of(
                "product$categoryName",
                "sum(salesAmount) as teamSales",
                "salesShare"
        )));
        request.setGroupBy(buildGroupBy("product$categoryName"));
        request.setPostAggregateCalculations(new ArrayList<>(List.of(new PostAggregateCalculationDef(
                "salesShare", "ratioToTotal", "teamSales", "grandTotal", "ratio"
        ))));
        request.setPostSlice(new ArrayList<>(List.of(new SliceRequestDef("salesShare", ">", 0.2))));
        return request;
    }

    private JdbcModelQueryEngine analyze(DbQueryRequestDef request) {
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertNotNull(queryModel, "查询模型加载失败");
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        engine.analysisQueryRequest(systemBundlesContext, request);
        return engine;
    }

    private AnalysisResult analyzeWithContext(DbQueryRequestDef request) {
        return analyzeWithContext(request, null);
    }

    private AnalysisResult analyzeWithContext(DbQueryRequestDef request, FDialect dialect) {
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertNotNull(queryModel, "查询模型加载失败");
        if (dialect != null) {
            queryModel = spy(queryModel);
            doReturn(dialect).when(queryModel).getDialect();
        }
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        ModelResultContext context = new ModelResultContext(PagingRequest.buildPagingRequest(request, 100), null);
        engine.analysisQueryRequest(systemBundlesContext, context);
        return new AnalysisResult(engine, context);
    }

    private AnalysisFailure analyzeFailureWithContext(DbQueryRequestDef request, FDialect dialect) {
        JdbcQueryModel queryModel = getQueryModel(request.getQueryModel());
        assertNotNull(queryModel, "查询模型加载失败");
        if (dialect != null) {
            queryModel = spy(queryModel);
            doReturn(dialect).when(queryModel).getDialect();
        }
        JdbcModelQueryEngine engine = new JdbcModelQueryEngine(queryModel, sqlFormulaService);
        ModelResultContext context = new ModelResultContext(PagingRequest.buildPagingRequest(request, 100), null);
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> engine.analysisQueryRequest(systemBundlesContext, context));
        return new AnalysisFailure(exception, context);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> queryStagePlan(ModelResultContext context) {
        Object plan = context.getExtData().get(QueryStagePlan.EXT_DATA_KEY);
        assertNotNull(plan, "queryStagePlan diagnostics should be attached to context.extData");
        assertTrue(plan instanceof Map<?, ?>, "queryStagePlan diagnostics should be a map");
        return (Map<String, Object>) plan;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> stages(Map<String, Object> plan) {
        Object stages = plan.get("stages");
        assertTrue(stages instanceof List<?>, "queryStagePlan.stages should be a list");
        return (List<Map<String, Object>>) stages;
    }

    private List<String> stageIds(Map<String, Object> plan) {
        return stages(plan).stream()
                .map(stage -> (String) stage.get("id"))
                .toList();
    }

    private Map<String, Object> stage(Map<String, Object> plan, String id) {
        return stages(plan).stream()
                .filter(stage -> id.equals(stage.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing stage '" + id + "' in " + plan));
    }

    @SuppressWarnings("unchecked")
    private List<String> listValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        assertTrue(value instanceof List<?>, key + " should be a list");
        return (List<String>) value;
    }

    private String expectedMultiStageRenderStrategy() {
        return supportsCommonTableExpressions() ? "cte" : "derived";
    }

    private void assertPostAggregateRenderingMatchesPlan(JdbcModelQueryEngine engine, Map<String, Object> plan) {
        String strategy = (String) plan.get("renderStrategy");
        String sql = engine.getSql();
        if ("cte".equals(strategy)) {
            assertTrue(engine.isCteWrapped(), "CTE rendering should expose structured CTE stages");
            assertTrue(sql.contains("post_stage AS"),
                    "CTE post-aggregate SQL wrapping should remain active: " + sql);
            return;
        }
        assertEquals("derived", strategy, "Post-aggregate strategy should be CTE or derived");
        assertFalse(engine.isCteWrapped(), "Derived fallback should not expose structured CTE stages");
        assertFalse(sql.toUpperCase().contains("WITH "), "Derived fallback must not emit WITH: " + sql);
        assertTrue(sql.contains("post_stage"), "Derived fallback should still expose the post stage alias: " + sql);
        assertTrue(sql.contains("FROM (\nSELECT"), "Derived fallback should nest the planned stage SQL: " + sql);
    }

    private void assertFinalTotalMatchesRows(JdbcModelQueryEngine engine) {
        Object[] params = engine.getValues().toArray(new Object[0]);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(engine.getSql(), params);
        assertFalse(rows.isEmpty(), "Main query should execute against the fixture");

        Map<String, Object> totalData = jdbcTemplate.queryForMap(engine.getAggSql(), params);
        Object total = totalData.get("total");
        if (total == null) {
            total = totalData.get("TOTAL");
        }
        assertNotNull(total, "Agg SQL should expose a total column: " + engine.getAggSql());
        assertEquals(rows.size(), ((Number) total).intValue(),
                "returnTotal should count the final semantic result set");
    }

    private static class NoCteSqliteDialect extends SqliteDialect {
        @Override
        public boolean supportsCte() {
            return false;
        }
    }

    private static class NoWindowSqliteDialect extends SqliteDialect {
        @Override
        public boolean supportsWindowFunctions() {
            return false;
        }
    }

    private static class NoCteWindowSqliteDialect extends SqliteDialect {
        @Override
        public boolean supportsCte() {
            return false;
        }

        @Override
        public boolean supportsWindowFunctions() {
            return true;
        }
    }

    private List<GroupRequestDef> buildGroupBy(String... fields) {
        List<GroupRequestDef> groups = new ArrayList<>();
        for (String field : fields) {
            GroupRequestDef g = new GroupRequestDef();
            g.setField(field);
            groups.add(g);
        }
        return groups;
    }

    private OrderRequestDef orderAsc(String field) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir("ASC");
        return order;
    }

    private OrderRequestDef orderDesc(String field) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir("DESC");
        return order;
    }

    private String extractOuterSelect(String sql) {
        int fromStage1 = sql.toUpperCase().indexOf("FROM STAGE1");
        if (fromStage1 < 0) return sql;
        // Find the SELECT before FROM stage1
        int outerSelect = sql.toUpperCase().lastIndexOf("SELECT ", fromStage1);
        return outerSelect >= 0 ? sql.substring(outerSelect, fromStage1) : sql;
    }

    private String handWrittenRunningSumSql(int threshold) {
        String orderId = quoteIdentifier("orderId");
        String salesAmount = quoteIdentifier("salesAmount");
        String runningSalesAmount = quoteIdentifier("runningSalesAmount");
        return """
                WITH order_sales AS (
                    SELECT fs.order_id AS %s,
                           SUM(fs.sales_amount) AS %s
                    FROM fact_sales fs
                    WHERE fs.order_status = 'COMPLETED'
                    GROUP BY fs.order_id
                ),
                order_sales_window AS (
                    SELECT %s,
                           %s,
                           SUM(%s) OVER (
                               ORDER BY %s DESC, %s ASC
                               ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
                           ) AS %s
                    FROM order_sales
                )
                SELECT %s, %s, %s
                FROM order_sales_window
                WHERE %s <= %d
                ORDER BY %s DESC, %s ASC
                """.formatted(
                orderId, salesAmount,
                orderId, salesAmount, salesAmount, salesAmount, orderId, runningSalesAmount,
                orderId, salesAmount, runningSalesAmount,
                runningSalesAmount, threshold,
                salesAmount, orderId);
    }

    private int runningSumPostSliceThreshold() {
        // Keep parity stable when Maven reuses the shared SQLite fixture across repeated executions.
        Number maxOrderSales = jdbcTemplate.queryForObject("""
                SELECT MAX(order_sales.salesAmount)
                FROM (
                    SELECT SUM(fs.sales_amount) AS salesAmount
                    FROM fact_sales fs
                    WHERE fs.order_status = 'COMPLETED'
                    GROUP BY fs.order_id
                ) order_sales
                """, Number.class);
        assertNotNull(maxOrderSales, "COMPLETED order sales baseline should exist");
        return BigDecimal.valueOf(maxOrderSales.doubleValue())
                .setScale(0, RoundingMode.CEILING)
                .intValue();
    }

    private String quoteIdentifier(String identifier) {
        String dialect = getDialectKey();
        if (dialect.contains("mysql")) {
            return "`" + identifier + "`";
        }
        if (dialect.contains("sqlserver")) {
            return "[" + identifier + "]";
        }
        return "\"" + identifier + "\"";
    }

    private static void assertRowsEqualInOrder(
            List<Map<String, Object>> expected,
            List<Map<String, Object>> actual,
            String sql) {
        assertFalse(actual.isEmpty(), "actual result should not be empty");
        List<Map<String, String>> expectedRows = canonicalRowsInOrder(expected);
        List<Map<String, String>> actualRows = canonicalRowsInOrder(actual);
        assertTrue(expectedRows.equals(actualRows), () -> {
            int firstDiff = firstDiff(expectedRows, actualRows);
            String diff = firstDiff < 0
                    ? "no row diff"
                    : "firstDiff=" + firstDiff
                    + ", expected=" + expectedRows.get(firstDiff)
                    + ", actual=" + actualRows.get(firstDiff);
            return "expectedSize=" + expectedRows.size()
                    + ", actualSize=" + actualRows.size()
                    + ", " + diff
                    + "\nSQL:\n" + sql;
        });
    }

    private static int firstDiff(List<Map<String, String>> expectedRows, List<Map<String, String>> actualRows) {
        int size = Math.min(expectedRows.size(), actualRows.size());
        for (int i = 0; i < size; i++) {
            if (!expectedRows.get(i).equals(actualRows.get(i))) {
                return i;
            }
        }
        return expectedRows.size() == actualRows.size() ? -1 : size;
    }

    private static List<Map<String, String>> canonicalRowsInOrder(List<Map<String, Object>> rows) {
        List<Map<String, String>> canonical = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, String> normalized = new LinkedHashMap<>();
            row.entrySet().stream()
                    .filter(entry -> !entry.getKey().startsWith("_"))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> normalized.put(entry.getKey(), canonicalValue(entry.getValue())));
            canonical.add(normalized);
        }
        return canonical;
    }

    private static String canonicalValue(Object value) {
        if (value == null) {
            return "<null>";
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString())
                    .setScale(6, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
        }
        return value.toString();
    }

    private JdbcModelQueryEngine analyze(JdbcModelQueryEngine engine) {
        return engine;
    }

    private record AnalysisResult(JdbcModelQueryEngine engine, ModelResultContext context) {
    }

    private record AnalysisFailure(RuntimeException exception, ModelResultContext context) {
    }

    @Test
    @Order(100)
    @DisplayName("测试隐式依赖注入：Window CF 引用了未在 columns 选中的字段")
    void testHiddenDependencyInWindowFunction() throws Exception {
        if (!supportsWindowFunctions()) {
            return;
        }
        DbQueryRequestDef queryRequest = new DbQueryRequestDef();
        queryRequest.setQueryModel("FactSalesQueryModel");
        // Request DOES NOT explicitly ask for product$categoryName, but explicitly asks for dimension product$caption and metric salesAmount
        queryRequest.setColumns(new ArrayList<>(Arrays.asList("product$caption", "salesAmount", "salesRank")));

        CalculatedFieldDef cf = new CalculatedFieldDef();
        cf.setName("salesRank");
        cf.setExpression("RANK()");
        // Hidden dependency: product$categoryName is NOT in columns
        cf.setPartitionBy(Arrays.asList("product$categoryName"));
        cf.setWindowOrderBy(Arrays.asList(new WindowOrderDef("salesAmount", "desc")));
        queryRequest.setCalculatedFields(new ArrayList<>(Arrays.asList(cf)));

        JdbcModelQueryEngine engine = analyze(queryRequest);
        String sql = engine.getSql();
        log.debug("执行SQL: {}", sql);

        assertTrue(sql.contains("PARTITION BY") && sql.contains("product$categoryName"),
                "Outer stage must use correct projected alias for hidden dependency. Actual SQL:\n" + sql);
        assertTrue(sql.contains("category_name") &&
                        (sql.contains("\"product$categoryName\"") || sql.contains("`product$categoryName`")),
                "Stage 1 must project the hidden dependency. Actual SQL:\n" + sql);
        assertFalse(sql.contains("stage1.\"product$categoryName\"") || sql.contains("stage1.`product$categoryName`"),
                "Hidden dependency must not be exposed as a final output column. Actual SQL:\n" + sql);
    }
}

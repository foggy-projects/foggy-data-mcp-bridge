package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.*;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePlan;
import com.foggyframework.dataset.model.engine.stage.result.ResultStagePreparation;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.JdbcQueryModel;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("结果阶段规划与回退测试")
class JdbcModelQueryEngineStagePlanTest extends CteWrapTestSupport {
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
}

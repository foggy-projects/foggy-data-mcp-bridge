package com.foggyframework.dataset.model.ecommerce;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.def.query.request.*;
import com.foggyframework.dataset.model.engine.JdbcModelQueryEngine;
import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.engine.stage.QueryStagePlan;
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
@DisplayName("结果阶段能力门禁与 final total 测试")
class JdbcModelQueryEngineStageCapabilityTest extends CteWrapTestSupport {
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
}

package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.impl.SemanticQueryServiceV3Impl;
import com.foggyframework.dataset.db.model.semantic.support.DslCteDslRequestMapper;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.db.model.spi.DbQueryColumn;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DslCteAcceptanceSampleTest {

    private SemanticQueryServiceV3Impl service;

    @BeforeEach
    void setUp() {
        service = new SemanticQueryServiceV3Impl();
        QueryModelLoader loader = mock(QueryModelLoader.class);
        QueryModel saleOrder = queryModel(
                "SaleOrder", "product.categoryName", "amount"
        );
        when(loader.getJdbcQueryModel("SaleOrder", null)).thenReturn(saleOrder);
        ReflectionTestUtils.setField(service, "queryModelLoader", loader);
    }

    @Test
    @DisplayName("DSL_CTE acceptance samples pass normalized stage contract")
    void acceptsDslCteAcceptanceSamples() {
        assertDslCteReady("biz-004", biz004(), Set.of("aggregate", "window_derive"));
        assertDslCteReady("biz-005", biz005(), Set.of("aggregate", "derive", "postSlice"));
        assertDslCteReady("biz-006", biz006(), Set.of("aggregate", "window_derive", "postSlice"));
        assertDslCteReady("biz-024", biz024(), Set.of("derive", "aggregate"));
        assertDslCteReady("holdout-005", holdout005(), Set.of("derive", "aggregate"));
        assertDslCteReady("third-004", third004(), Set.of("derive", "aggregate", "postSlice"));
        assertDslCteReady("third-013", third013(), Set.of("derive", "aggregate"));
        assertDslCteReady("third-022", third022(), Set.of("aggregate", "join_align", "postSlice"));
        assertDslCteReady("third-025", third025(), Set.of("aggregate", "derive", "postSlice"));
        assertDslCteReady("third-031", third031(), Set.of("aggregate", "window_derive"));
        assertDslCteReady("third-039", third039(), Set.of("derive", "aggregate", "postSlice"));
    }

    @Test
    @DisplayName("DSL_CTE contract rejects missing stage plan")
    void rejectsMissingDslCtePlan() {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL_CTE");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("AnyModel", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("DSL_CTE_PLAN_NOT_DECLARED"));
    }

    @Test
    @DisplayName("DSL_CTE does not enter SQL generation in P0")
    void generateSqlRejectsExecution() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.generateSql("SaleOrder", dslCtePlan(biz005()), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("DSL_CTE_EXECUTION_NOT_IMPLEMENTED"));
    }

    @Test
    @DisplayName("DSL_CTE validation marks ratio postSlice plan as bridge-ready")
    void validationShowsBridgeReadyForRatioPostSlice() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(biz005()), SemanticRequestContext.empty());

        assertEquals("BRIDGE_READY", response.getExecution().getDslCteValidation().get("dsl_bridge_status"));
        assertEquals("SaleOrder", response.getExecution().getDslCteValidation().get("dsl_bridge_model"));
        assertNotNull(response.getExecution().getDslCteValidation().get("dsl_request"));
    }

    @Test
    @DisplayName("DSL_CTE validation marks rolling window plan as bridge-ready")
    void validationShowsBridgeReadyForRollingWindow() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(biz004()), SemanticRequestContext.empty());

        assertEquals("BRIDGE_READY", response.getExecution().getDslCteValidation().get("dsl_bridge_status"));
        SemanticQueryRequest dslRequest = (SemanticQueryRequest) response.getExecution()
                .getDslCteValidation().get("dsl_request");
        assertNotNull(dslRequest);
        assertEquals("rolling_7d", dslRequest.getTimeWindow().get("comparison"));
        assertEquals(List.of("salesAmount"), dslRequest.getTimeWindow().get("targetMetrics"));
        assertTrue(dslRequest.getColumns().contains("salesAmount__rolling_7d"));
    }

    @Test
    @DisplayName("DSL_CTE validation keeps bridge deferred when aggregate input model is missing")
    void validationDefersBridgeWhenInputModelMissing() {
        Map<String, Object> plan = biz005();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(0).put("input", m());

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(plan), SemanticRequestContext.empty());

        Map<String, Object> validation = response.getExecution().getDslCteValidation();
        assertEquals("BRIDGE_DEFERRED", validation.get("dsl_bridge_status"));
        @SuppressWarnings("unchecked")
        List<String> unsupported = (List<String>) validation.get("dsl_bridge_unsupported");
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("aggregate input model")));
    }

    @Test
    @DisplayName("DSL_CTE DSL bridge defers valueField postSlice filters")
    void bridgeDefersValueFieldPostSliceFilters() {
        DslCteDslRequestMapper.BridgeResult bridge =
                DslCteDslRequestMapper.toDslRequest(null, m("cte_plan", aggregateValueFieldPostSlice()));

        assertFalse(bridge.ready());
        assertTrue(bridge.unsupported().stream().anyMatch(msg -> msg.contains("valueField")));
    }

    @Test
    @DisplayName("DSL_CTE generateSql can opt in to DSL bridge for aggregate derive postSlice")
    void generateSqlOptInUsesDslBridgeForRatioPostSlice() {
        QueryFacade queryFacade = mock(QueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class)))
                .thenReturn(new SqlGenerationResult(
                        "WITH post_stage AS (...) SELECT * FROM post_stage WHERE salesShare > ?",
                        List.of(0.05),
                        null));
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);

        SemanticQueryRequest request = dslCtePlan(biz005());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult result = service.generateSql("SaleOrder", request, SemanticRequestContext.empty());

        assertTrue(result.getSql().contains("post_stage"));
        org.mockito.ArgumentCaptor<ModelResultContext> captor = org.mockito.ArgumentCaptor.forClass(ModelResultContext.class);
        verify(queryFacade).buildSqlOnly(captor.capture());
        assertEquals(List.of("product.categoryName", "sum(amount) AS salesAmount", "salesShare"),
                captor.getValue().getRequest().getParam().getColumns());
        assertEquals(1, captor.getValue().getRequest().getParam().getPostAggregateCalculations().size());
        assertEquals("salesShare", captor.getValue().getRequest().getParam().getPostSlice().get(0).getField());
    }

    @Test
    @DisplayName("DSL_CTE generateSql can opt in to rolling window timeWindow bridge")
    void generateSqlOptInUsesDslBridgeForRollingWindow() {
        QueryFacade queryFacade = mock(QueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class)))
                .thenReturn(new SqlGenerationResult(
                        "WITH daily AS (...) SELECT salesAmount__rolling_7d FROM daily",
                        List.of(),
                        null));
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);

        SemanticQueryRequest request = dslCtePlan(biz004());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult result = service.generateSql("SaleOrder", request, SemanticRequestContext.empty());

        assertTrue(result.getSql().contains("salesAmount__rolling_7d"));
        org.mockito.ArgumentCaptor<ModelResultContext> captor = org.mockito.ArgumentCaptor.forClass(ModelResultContext.class);
        verify(queryFacade).buildSqlOnly(captor.capture());
        assertEquals(List.of("orderDate.day", "sum(amount) AS salesAmount", "salesAmount__rolling_7d"),
                captor.getValue().getRequest().getParam().getColumns());
        assertEquals(Map.of(
                        "field", "orderDate.day",
                        "grain", "day",
                        "comparison", "rolling_7d",
                        "targetMetrics", List.of("salesAmount"),
                        "rollingAggregator", "sum"),
                captor.getValue().getExtData().get("timeWindow"));
    }

    @Test
    @DisplayName("DSL_CTE generateSql opt-in still fails closed for unsupported window stages")
    void generateSqlOptInDefersUnsupportedWindowStages() {
        SemanticQueryRequest request = dslCtePlan(biz006());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.generateSql("SaleOrder", request, SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("DSL_CTE_DSL_BRIDGE_NOT_SUPPORTED"));
    }

    private void assertDslCteReady(String sampleId, Map<String, Object> ctePlan, Set<String> expectedTypes) {
        SemanticQueryRequest request = dslCtePlan(ctePlan);

        SemanticQueryResponse response = service.validateQuery(
                "AnyModel", request, SemanticRequestContext.empty());

        assertEquals("DSL_CTE", response.getExecution().getRoute(), sampleId);
        assertEquals("PLAN_READY", response.getExecution().getStatus(), sampleId);
        assertEquals(request.getExecutablePlan(), response.getExecution().getExecutablePlan(), sampleId);
        assertNotNull(response.getExecution().getDslCteValidation(), sampleId);

        @SuppressWarnings("unchecked")
        List<String> stageTypes = (List<String>) response.getExecution().getDslCteValidation().get("stage_types");
        assertTrue(stageTypes.containsAll(expectedTypes), sampleId + " stage types " + stageTypes);
    }

    private SemanticQueryRequest dslCtePlan(Map<String, Object> ctePlan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL_CTE");
        request.setExecutablePlan(m("cte_plan", ctePlan));
        return request;
    }

    private Map<String, Object> biz004() {
        return plan(
                List.of(
                        stage("daily_sales", "aggregate",
                                "input", model("SaleOrder"),
                                "filters", List.of(filter("orderDate", "last_n_days", 30)),
                                "groupBy", List.of("orderDate.day"),
                                "metrics", List.of(metric("salesAmount", "sum(amount)"))),
                        stage("rolling_sales", "window_derive",
                                "inputs", List.of("daily_sales"),
                                "window", window(List.of(), List.of(order("orderDate.day", "ASC")),
                                        m("type", "rows", "start", -6, "end", 0)),
                                "derived", List.of(derived("rolling7dSalesAmount", "sum(salesAmount) over last 7 rows")))
                ),
                List.of("orderDate.day", "salesAmount", "rolling7dSalesAmount")
        );
    }

    private Map<String, Object> biz005() {
        return planWithSliceLowering(
                List.of(
                        stage("category_sales", "aggregate",
                                "input", model("SaleOrder"),
                                "groupBy", List.of("product.categoryName"),
                                "metrics", List.of(metric("salesAmount", "sum(amount)"))),
                        stage("category_ratio", "derive",
                                "inputs", List.of("category_sales"),
                                "derived", List.of(derived("salesShare", "salesAmount / sum(salesAmount) over ()"))),
                        stage("filtered", "postSlice",
                                "inputs", List.of("category_ratio"),
                                "filters", List.of(filter("salesShare", ">", 0.05)))
                ),
                List.of("product.categoryName", "salesAmount", "salesShare"),
                List.of(m("field", "salesShare", "from", "slice", "to", "postSlice", "reason", "derived_alias"))
        );
    }

    private Map<String, Object> biz006() {
        return plan(
                List.of(
                        stage("customer_sales", "aggregate",
                                "input", model("SaleOrder"),
                                "groupBy", List.of("customer.name"),
                                "metrics", List.of(metric("salesAmount", "sum(amount)"))),
                        stage("customer_rank_contribution", "window_derive",
                                "inputs", List.of("customer_sales"),
                                "window", window(List.of(), List.of(order("salesAmount", "DESC")), null),
                                "derived", List.of(
                                        derived("salesRank", "rank()"),
                                        derived("cumulativeShare", "sum(salesAmount) over order / sum(salesAmount) over all"))),
                        stage("top_contribution_customers", "postSlice",
                                "inputs", List.of("customer_rank_contribution"),
                                "filters", List.of(filter("cumulativeShare", "<=", 0.8)))
                ),
                List.of("customer.name", "salesAmount", "salesRank", "cumulativeShare")
        );
    }

    private Map<String, Object> biz024() {
        return slaPlan("ticket_scope", "this_month", null, 48, false);
    }

    private Map<String, Object> holdout005() {
        return slaPlan("ticket_scope", "month", "2026-05", 48, false);
    }

    private Map<String, Object> third004() {
        Map<String, Object> plan = slaPlan("recent_ticket_scope", "last_n_days", 30, 24, true);
        @SuppressWarnings("unchecked")
        List<Object> stages = (List<Object>) plan.get("stages");
        stages.add(stage("low_sla_teams", "postSlice",
                "inputs", List.of("team_sla_rate"),
                "filters", List.of(filter("slaAchievementRate", "<", 0.85))));
        return plan;
    }

    private Map<String, Object> third013() {
        return plan(
                List.of(
                        stage("lead_scope", "derive",
                                "input", model("CrmLead"),
                                "filters", List.of(filter("createdAt", "this_quarter", null)),
                                "derived", List.of(
                                        derived("convertedOpportunity", "convertedOpportunityId is not null"),
                                        derived("convertedOrder", "convertedOrderId is not null"))),
                        stage("source_funnel", "aggregate",
                                "inputs", List.of("lead_scope"),
                                "groupBy", List.of("leadSource"),
                                "metrics", List.of(
                                        metric("leadCount", "count(*)"),
                                        metric("convertedOpportunityCount", "sum(case when convertedOpportunity then 1 else 0 end)"),
                                        metric("convertedOrderCount", "sum(case when convertedOrder then 1 else 0 end)"))),
                        stage("source_conversion_rate", "derive",
                                "inputs", List.of("source_funnel"),
                                "derived", List.of(derived("leadToOrderConversionRate", "convertedOrderCount / leadCount")))
                ),
                List.of("leadSource", "leadCount", "convertedOpportunityCount",
                        "convertedOrderCount", "leadToOrderConversionRate")
        );
    }

    private Map<String, Object> third022() {
        return plan(
                List.of(
                        stage("department_avg", "aggregate",
                                "input", model("Student"),
                                "groupBy", List.of("department.name"),
                                "metrics", List.of(metric("departmentAvgGpa", "avg(gpa)"))),
                        stage("student_with_department_avg", "join_align",
                                "inputs", List.of("Student", "department_avg"),
                                "keys", List.of("department.name"),
                                "joinType", "declared_dimension_align"),
                        stage("above_department_avg", "postSlice",
                                "inputs", List.of("student_with_department_avg"),
                                "filters", List.of(m("field", "gpa", "op", ">", "valueField", "departmentAvgGpa")))
                ),
                List.of("studentName", "department.name", "gpa", "departmentAvgGpa")
        );
    }

    private Map<String, Object> third025() {
        return planWithSliceLowering(
                List.of(
                        stage("category_sales", "aggregate",
                                "input", model("StoreSale"),
                                "filters", List.of(filter("saleDate", "this_year", null)),
                                "groupBy", List.of("product.categoryName"),
                                "metrics", List.of(metric("salesAmount", "sum(salesAmount)"))),
                        stage("category_ratio", "derive",
                                "inputs", List.of("category_sales"),
                                "derived", List.of(derived("salesShare", "salesAmount / sum(salesAmount) over ()"))),
                        stage("filtered", "postSlice",
                                "inputs", List.of("category_ratio"),
                                "filters", List.of(filter("salesShare", ">", 0.15)))
                ),
                List.of("product.categoryName", "salesAmount", "salesShare"),
                List.of(m("field", "salesShare", "from", "slice", "to", "postSlice", "reason", "derived_alias"))
        );
    }

    private Map<String, Object> aggregateValueFieldPostSlice() {
        return plan(
                List.of(
                        stage("category_sales", "aggregate",
                                "input", model("SaleOrder"),
                                "groupBy", List.of("product.categoryName"),
                                "metrics", List.of(metric("salesAmount", "sum(amount)"))),
                        stage("filtered", "postSlice",
                                "inputs", List.of("category_sales"),
                                "filters", List.of(m("field", "salesAmount", "op", ">", "valueField", "targetSalesAmount")))
                ),
                List.of("product.categoryName", "salesAmount")
        );
    }

    private Map<String, Object> third031() {
        return plan(
                List.of(
                        stage("monthly_branch_balance", "aggregate",
                                "input", model("AccountBalance"),
                                "filters", List.of(filter("balanceDate", "year", "2026")),
                                "groupBy", List.of("branch.name", "balanceDate.month"),
                                "metrics", List.of(metric("balanceAmount", "sum(balanceAmount)"))),
                        stage("monthly_growth", "window_derive",
                                "inputs", List.of("monthly_branch_balance"),
                                "window", window(List.of("branch.name"), List.of(order("balanceDate.month", "ASC")), null),
                                "derived", List.of(
                                        derived("previousMonthBalance", "lag(balanceAmount)"),
                                        derived("monthOverMonthGrowthRate", "(balanceAmount - previousMonthBalance) / previousMonthBalance")))
                ),
                List.of("branch.name", "balanceDate.month", "balanceAmount", "monthOverMonthGrowthRate")
        );
    }

    private Map<String, Object> third039() {
        return plan(
                List.of(
                        stage("home_games", "derive",
                                "input", model("SportsMatch"),
                                "filters", List.of(filter("season", "=", "2026")),
                                "derived", List.of(derived("homeWin", "homeScore > awayScore"))),
                        stage("team_home_record", "aggregate",
                                "inputs", List.of("home_games"),
                                "groupBy", List.of("homeTeam.name"),
                                "metrics", List.of(
                                        metric("homeGameCount", "count(*)"),
                                        metric("homeWinCount", "sum(case when homeWin then 1 else 0 end)"))),
                        stage("all_home_wins", "postSlice",
                                "inputs", List.of("team_home_record"),
                                "filters", List.of(m("field", "homeWinCount", "op", "=", "valueField", "homeGameCount")))
                ),
                List.of("homeTeam.name", "homeGameCount", "homeWinCount")
        );
    }

    private Map<String, Object> slaPlan(String scopeStage, String timeOp, Object timeValue,
                                        int hours, boolean includePostFilter) {
        List<Map<String, Object>> stages = new java.util.ArrayList<>(List.of(
                stage(scopeStage, "derive",
                        "input", model("ServiceTicket"),
                        "filters", List.of(filter("createdAt", timeOp, timeValue)),
                        "derived", List.of(
                                derived("firstResponseHours", "hours_between(createdAt, firstResponseAt)"),
                                derived("slaHit", "firstResponseAt is not null and firstResponseHours <= " + hours),
                                derived("unrespondedOver48h", "firstResponseAt is null or firstResponseHours > " + hours))),
                stage("team_sla", "aggregate",
                        "inputs", List.of(scopeStage),
                        "groupBy", List.of("team.name"),
                        "metrics", List.of(
                                metric("ticketCount", "count(*)"),
                                metric("slaHitCount", "sum(case when slaHit then 1 else 0 end)"),
                                metric("over48hUnrespondedCount", "sum(case when unrespondedOver48h then 1 else 0 end)"))),
                stage("team_sla_rate", "derive",
                        "inputs", List.of("team_sla"),
                        "derived", List.of(derived("slaAchievementRate", "slaHitCount / ticketCount")))
        ));
        return plan(stages, includePostFilter
                ? List.of("team.name", "ticketCount", "slaAchievementRate")
                : List.of("team.name", "ticketCount", "over48hUnrespondedCount", "slaAchievementRate"));
    }

    private Map<String, Object> plan(List<?> stages, List<String> output) {
        return m("stages", stages, "output", output);
    }

    private Map<String, Object> planWithSliceLowering(List<?> stages, List<String> output, List<?> sliceLowering) {
        return m("stages", stages, "sliceLowering", sliceLowering, "output", output);
    }

    private Map<String, Object> stage(String name, String type, Object... rest) {
        Map<String, Object> result = m(rest);
        result.put("name", name);
        result.put("type", type);
        return result;
    }

    private Map<String, Object> model(String name) {
        return m("model", name);
    }

    private Map<String, Object> filter(String field, String op, Object value) {
        Map<String, Object> result = m("field", field, "op", op);
        if (value != null) {
            result.put("value", value);
        }
        return result;
    }

    private Map<String, Object> metric(String name, String expr) {
        return m("name", name, "expr", expr);
    }

    private Map<String, Object> derived(String name, String expr) {
        return m("name", name, "expr", expr);
    }

    private Map<String, Object> order(String field, String dir) {
        return m("field", field, "dir", dir);
    }

    private Map<String, Object> window(List<String> partitionBy, List<?> orderBy, Map<String, Object> frame) {
        Map<String, Object> result = m("partitionBy", partitionBy, "orderBy", orderBy);
        if (frame != null) {
            result.put("frame", frame);
        }
        return result;
    }

    private Map<String, Object> m(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }

    private QueryModel queryModel(String name, String... fields) {
        QueryModel qm = mock(QueryModel.class);
        List<DbQueryColumn> columns = List.of(fields).stream()
                .map(this::column)
                .toList();
        when(qm.getName()).thenReturn(name);
        when(qm.getShortAlias()).thenReturn(name);
        when(qm.getJdbcQueryColumns()).thenReturn(columns);
        when(qm.getPredefinedCalculatedFields()).thenReturn(List.of());
        return qm;
    }

    private DbQueryColumn column(String name) {
        DbQueryColumn col = mock(DbQueryColumn.class);
        when(col.getName()).thenReturn(name);
        return col;
    }
}

package com.foggyframework.dataset.db.model.semantic;

import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
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
        QueryModel serviceTicket = queryModel(
                "ServiceTicketQueryModel", "team$caption", "ticketId", "createdAt", "firstResponseAt", "priority"
        );
        when(loader.getJdbcQueryModel("SaleOrder", null)).thenReturn(saleOrder);
        when(loader.getJdbcQueryModel("ServiceTicketQueryModel", null)).thenReturn(serviceTicket);
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
    @DisplayName("DSL_CTE bridge keeps SLA templates deferred with pre-aggregate derive reason")
    void validationDefersSlaTemplateBridge() {
        List<String> unsupported = bridgeUnsupported(biz024());

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("pre-aggregate derive")));
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("ticket_scope")));
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("row-level calculatedFields bridge")));
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("hours_between")));
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("conditional numerator aggregation")));
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("metric-to-metric post-aggregate ratio")));
    }

    @Test
    @DisplayName("DSL_CTE bridge explains SLA post-filter templates as deferred")
    void validationDefersSlaPostFilterTemplateBridge() {
        List<String> unsupported = bridgeUnsupported(third004());

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("pre-aggregate derive")));
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("row-level calculatedFields bridge")));
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("postSlice after a pre-aggregate business metric")));
    }

    @Test
    @DisplayName("DSL_CTE validation exposes unsigned SLA row-level derived contracts")
    void validationExposesSlaDerivedContracts() {
        SemanticQueryResponse response = service.validateQuery(
                "ServiceTicket", dslCtePlan(biz024()), SemanticRequestContext.empty());

        Map<String, Object> rowLevelContract = stageContract(response, "ticket_scope", "derive_contract");
        assertEquals("sla_row_level_derived", rowLevelContract.get("kind"));
        assertEquals("row_level_calculatedFields", rowLevelContract.get("bridge_scope"));
        assertEquals(false, rowLevelContract.get("bridge_signed"));
        @SuppressWarnings("unchecked")
        List<String> rowCapabilities = (List<String>) rowLevelContract.get("required_capabilities");
        assertTrue(rowCapabilities.contains("governed_duration_function_mapping"));
        assertTrue(rowCapabilities.contains("null_handling_predicate"));

        Map<String, Object> aggregateContract = stageContract(response, "team_sla", "aggregate_contract");
        assertEquals("conditional_numerator_aggregation", aggregateContract.get("kind"));
        assertEquals(false, aggregateContract.get("bridge_signed"));

        Map<String, Object> ratioContract = stageContract(response, "team_sla_rate", "derive_contract");
        assertEquals("metric_to_metric_ratio", ratioContract.get("kind"));
        assertEquals("post_aggregate_calculation", ratioContract.get("bridge_scope"));
        assertEquals(false, ratioContract.get("bridge_signed"));
    }

    @Test
    @DisplayName("DSL_CTE validation marks minimal row-level SLA duration bridge as ready")
    void validationShowsBridgeReadyForMinimalRowLevelSla() {
        SemanticQueryResponse response = service.validateQuery(
                "ServiceTicketQueryModel", dslCtePlan(minimalRowLevelSlaPlan()), SemanticRequestContext.empty());

        Map<String, Object> validation = response.getExecution().getDslCteValidation();
        assertEquals("BRIDGE_READY", validation.get("dsl_bridge_status"));
        assertEquals("ServiceTicketQueryModel", validation.get("dsl_bridge_model"));
        SemanticQueryRequest dslRequest = (SemanticQueryRequest) validation.get("dsl_request");
        assertNotNull(dslRequest);
        assertEquals(List.of("team$caption", "count(ticketId) AS ticketCount", "sum(slaHit) AS slaHitCount"),
                dslRequest.getColumns());
        assertEquals(2, dslRequest.getCalculatedFields().size());
        assertEquals("hours_between(createdAt, firstResponseAt)",
                dslRequest.getCalculatedFields().get(0).getExpression());
        assertEquals("iif(is_not_null(firstResponseAt) && firstResponseHours <= 48, 1, 0)",
                dslRequest.getCalculatedFields().get(1).getExpression());
    }

    @Test
    @DisplayName("DSL_CTE validation marks minimal SLA rate postSlice as result-stage bridge-ready")
    void validationShowsBridgeReadyForMinimalSlaRatePostSlice() {
        SemanticQueryResponse response = service.validateQuery(
                "ServiceTicketQueryModel", dslCtePlan(minimalSlaRatePostSlicePlan()),
                SemanticRequestContext.empty());

        Map<String, Object> validation = response.getExecution().getDslCteValidation();
        assertEquals("BRIDGE_READY", validation.get("dsl_bridge_status"));
        assertEquals("ServiceTicketQueryModel", validation.get("dsl_bridge_model"));
        assertNotNull(validation.get("dsl_request"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ratioBridge = (Map<String, Object>) validation.get("dsl_result_stage_metric_ratio");
        assertEquals("sla_metric_ratio", ratioBridge.get("kind"));
        assertEquals("result_stage_metric_ratio", ratioBridge.get("bridge_scope"));
        assertEquals(true, ratioBridge.get("bridge_signed"));
        assertEquals("slaHitCount", ratioBridge.get("numerator"));
        assertEquals("ticketCount", ratioBridge.get("denominator"));
        assertEquals("slaAchievementRate", ratioBridge.get("ratio_alias"));
        assertEquals(1, ratioBridge.get("postSlice_filters"));
    }

    @Test
    @DisplayName("DSL_CTE validation marks priority-aware SLA rate postSlice as result-stage bridge-ready")
    void validationShowsBridgeReadyForPriorityAwareSlaRatePostSlice() {
        SemanticQueryResponse response = service.validateQuery(
                "ServiceTicketQueryModel", dslCtePlan(priorityAwareSlaRatePostSlicePlan()),
                SemanticRequestContext.empty());

        Map<String, Object> validation = response.getExecution().getDslCteValidation();
        assertEquals("BRIDGE_READY", validation.get("dsl_bridge_status"));
        SemanticQueryRequest dslRequest = (SemanticQueryRequest) validation.get("dsl_request");
        assertNotNull(dslRequest);
        assertEquals(3, dslRequest.getCalculatedFields().size());
        assertEquals("iif(priority == 'P1', 4, iif(priority == 'P2', 24, iif(priority == 'P3', 48, null)))",
                dslRequest.getCalculatedFields().get(1).getExpression());
        assertEquals("iif(is_not_null(firstResponseAt) && firstResponseHours <= slaThresholdHours, 1, 0)",
                dslRequest.getCalculatedFields().get(2).getExpression());

        Map<String, Object> rowLevelContract = stageContract(response, "ticket_scope", "derive_contract");
        @SuppressWarnings("unchecked")
        List<String> rowCapabilities = (List<String>) rowLevelContract.get("required_capabilities");
        assertTrue(rowCapabilities.contains("priority_threshold_mapping"));
    }

    @Test
    @DisplayName("DSL_CTE priority-aware SLA bridge defers missing priority thresholds")
    void validationDefersPriorityAwareSlaMissingThreshold() {
        Map<String, Object> plan = priorityAwareSlaRatePostSlicePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(0).put("derived", List.of(
                derived("firstResponseHours", "hours_between(createdAt, firstResponseAt)"),
                derived("slaThresholdHours", "priority_threshold(priority, P1=4, P2=24)"),
                derived("slaHit", "firstResponseAt is not null and firstResponseHours <= slaThresholdHours")));

        List<String> unsupported = bridgeUnsupported(plan);

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("thresholds for P1/P2/P3")));
    }

    @Test
    @DisplayName("DSL_CTE priority-aware SLA bridge defers unknown priority thresholds")
    void validationDefersPriorityAwareSlaUnknownThresholdCode() {
        Map<String, Object> plan = priorityAwareSlaRatePostSlicePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(0).put("derived", List.of(
                derived("firstResponseHours", "hours_between(createdAt, firstResponseAt)"),
                derived("slaThresholdHours", "priority_threshold(priority, P1=4, P2=24, P4=72)"),
                derived("slaHit", "firstResponseAt is not null and firstResponseHours <= slaThresholdHours")));

        List<String> unsupported = bridgeUnsupported(plan);

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("priority code: P4")));
    }

    @Test
    @DisplayName("DSL_CTE priority-aware SLA bridge defers non-numeric priority thresholds")
    void validationDefersPriorityAwareSlaNonNumericThreshold() {
        Map<String, Object> plan = priorityAwareSlaRatePostSlicePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(0).put("derived", List.of(
                derived("firstResponseHours", "hours_between(createdAt, firstResponseAt)"),
                derived("slaThresholdHours", "priority_threshold(priority, P1=4, P2=24, P3=fast)"),
                derived("slaHit", "firstResponseAt is not null and firstResponseHours <= slaThresholdHours")));

        List<String> unsupported = bridgeUnsupported(plan);

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("numeric hours: P3=fast")));
    }

    @Test
    @DisplayName("DSL_CTE SLA rate bridge defers postSlice with non-linear input")
    void validationDefersMinimalSlaRatePostSliceInputMismatch() {
        Map<String, Object> plan = minimalSlaRatePostSlicePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(3).put("inputs", List.of("team_sla"));

        DslCteDslRequestMapper.ResultStageMetricRatioBridgeResult bridge =
                DslCteDslRequestMapper.toResultStageMetricRatioBridge(null, m("cte_plan", plan));

        assertFalse(bridge.ready());
        assertTrue(bridge.unsupported().stream()
                .anyMatch(msg -> msg.contains("postSlice must reference the ratio derive stage")));
    }

    @Test
    @DisplayName("DSL_CTE SLA rate bridge defers postSlice on non-ratio alias")
    void validationDefersMinimalSlaRatePostSliceOnMetricAlias() {
        Map<String, Object> plan = minimalSlaRatePostSlicePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(3).put("filters", List.of(filter("slaHitCount", "<", 3)));

        List<String> unsupported = bridgeUnsupported(plan);

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("postSlice only on signed derived alias")));
    }

    @Test
    @DisplayName("DSL_CTE SLA rate bridge defers unsupported ratio formula")
    void validationDefersMinimalSlaRateUnsupportedRatioFormula() {
        Map<String, Object> plan = minimalSlaRatePostSlicePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(2).put("derived", List.of(derived("slaAchievementRate", "ticketCount / slaHitCount")));

        List<String> unsupported = bridgeUnsupported(plan);

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("slaHitCount / ticketCount")));
    }

    @Test
    @DisplayName("DSL_CTE validation marks lag period-over-period plan as bridge-ready")
    void validationShowsBridgeReadyForLagPeriodOverPeriod() {
        SemanticQueryResponse response = service.validateQuery(
                "AccountBalance", dslCtePlan(third031()), SemanticRequestContext.empty());

        assertEquals("BRIDGE_READY", response.getExecution().getDslCteValidation().get("dsl_bridge_status"));
        SemanticQueryRequest dslRequest = (SemanticQueryRequest) response.getExecution()
                .getDslCteValidation().get("dsl_request");
        assertNotNull(dslRequest);
        assertEquals("mom", dslRequest.getTimeWindow().get("comparison"));
        assertEquals(List.of("balanceAmount"), dslRequest.getTimeWindow().get("targetMetrics"));
        assertTrue(dslRequest.getColumns().contains("balanceAmount__ratio"));
    }

    @Test
    @DisplayName("DSL_CTE bridge defers unsupported lag period-over-period formulas")
    void validationDefersUnsupportedLagPeriodOverPeriodBridge() {
        Map<String, Object> plan = third031();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(1).put("derived", List.of(
                derived("previousMonthBalance", "lag(balanceAmount)"),
                derived("monthOverMonthGrowthRate", "balanceAmount / previousMonthBalance")));

        List<String> unsupported = bridgeUnsupported(plan);

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("period-over-period")));
    }

    @Test
    @DisplayName("DSL_CTE bridge keeps join-align valueField templates deferred")
    void validationDefersJoinAlignValueFieldBridge() {
        List<String> unsupported = bridgeUnsupported(third022());

        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("join_align")));
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("valueField")));
    }

    @Test
    @DisplayName("DSL_CTE validation marks cumulative contribution as result-stage bridge-ready")
    void validationShowsBridgeReadyForCumulativeContribution() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(biz006()), SemanticRequestContext.empty());

        Map<String, Object> validation = response.getExecution().getDslCteValidation();
        assertEquals("BRIDGE_READY", validation.get("dsl_bridge_status"));
        assertNotNull(validation.get("dsl_request"));
        @SuppressWarnings("unchecked")
        Map<String, Object> resultStageWindow = (Map<String, Object>) validation.get("dsl_result_stage_window");
        assertEquals("cumulative_contribution", resultStageWindow.get("kind"));
        assertEquals("result_stage_window", resultStageWindow.get("bridge_scope"));
        assertEquals(true, resultStageWindow.get("bridge_signed"));
    }

    @Test
    @DisplayName("DSL_CTE validation classifies cumulative contribution as signed result-stage window")
    void validationClassifiesCumulativeContributionWindowContract() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(biz006()), SemanticRequestContext.empty());

        Map<String, Object> windowContract = windowContract(response, "customer_rank_contribution");

        assertEquals("cumulative_contribution", windowContract.get("kind"));
        assertEquals("result_stage_window", windowContract.get("bridge_scope"));
        assertEquals(true, windowContract.get("bridge_signed"));
        @SuppressWarnings("unchecked")
        List<String> capabilities = (List<String>) windowContract.get("required_capabilities");
        assertTrue(capabilities.contains("rank_over_aggregate_metric_order"));
        assertTrue(capabilities.contains("running_total_ratio"));
        assertTrue(capabilities.contains("postSlice_on_window_alias"));
    }

    @Test
    @DisplayName("DSL_CTE validation keeps signed window bridge contracts distinct")
    void validationClassifiesSignedWindowBridgeContracts() {
        assertEquals("rolling_sum", windowContract(
                service.validateQuery("SaleOrder", dslCtePlan(biz004()), SemanticRequestContext.empty()),
                "rolling_sales").get("kind"));
        assertEquals("period_over_period_lag", windowContract(
                service.validateQuery("AccountBalance", dslCtePlan(third031()), SemanticRequestContext.empty()),
                "monthly_growth").get("kind"));
    }

    @Test
    @DisplayName("DSL_CTE validation exposes inferred stage output fields")
    void validationExposesStageOutputFields() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(biz005()), SemanticRequestContext.empty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) response.getExecution()
                .getDslCteValidation().get("stages");
        Map<String, Object> ratioStage = stages.stream()
                .filter(stage -> "category_ratio".equals(stage.get("name")))
                .findFirst()
                .orElseThrow();

        assertEquals(true, ratioStage.get("output_complete"));
        @SuppressWarnings("unchecked")
        List<String> outputFields = (List<String>) ratioStage.get("output_fields");
        assertTrue(outputFields.contains("product.categoryName"));
        assertTrue(outputFields.contains("salesAmount"));
        assertTrue(outputFields.contains("salesShare"));
    }

    @Test
    @DisplayName("DSL_CTE validation infers aliases from string metrics")
    void validationInfersStringMetricAliases() {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(stringMetricAggregatePlan()), SemanticRequestContext.empty());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) response.getExecution()
                .getDslCteValidation().get("stages");
        @SuppressWarnings("unchecked")
        List<String> outputFields = (List<String>) stages.get(0).get("output_fields");

        assertTrue(outputFields.contains("product.categoryName"));
        assertTrue(outputFields.contains("salesAmount"));
    }

    @Test
    @DisplayName("DSL_CTE validation rejects unavailable final output fields for complete lineage")
    void validationRejectsUnknownFinalOutputField() {
        Map<String, Object> plan = biz005();
        plan.put("output", List.of("product.categoryName", "salesAmount", "missingAlias"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", dslCtePlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("cte_plan.output references unavailable field 'missingAlias'"));
    }

    @Test
    @DisplayName("DSL_CTE validation rejects unavailable postSlice fields for complete lineage")
    void validationRejectsUnknownPostSliceField() {
        Map<String, Object> plan = biz005();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(2).put("filters", List.of(filter("missingAlias", ">", 0.05)));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", dslCtePlan(plan), SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("postSlice field references unavailable field 'missingAlias'"));
    }

    @Test
    @DisplayName("DSL_CTE validation rejects unavailable postSlice valueField for complete lineage")
    void validationRejectsUnknownPostSliceValueField() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                service.validateQuery("SaleOrder", dslCtePlan(aggregateValueFieldPostSlice()),
                        SemanticRequestContext.empty()));

        assertTrue(ex.getMessage().contains("postSlice valueField references unavailable field 'targetSalesAmount'"));
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
    @DisplayName("DSL_CTE generateSql can opt in to MoM period-over-period timeWindow bridge")
    void generateSqlOptInUsesDslBridgeForLagPeriodOverPeriod() {
        QueryFacade queryFacade = mock(QueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class)))
                .thenReturn(new SqlGenerationResult(
                        "WITH monthly AS (...) SELECT balanceAmount__ratio FROM monthly",
                        List.of(),
                        null));
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);

        SemanticQueryRequest request = dslCtePlan(third031());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult result = service.generateSql("AccountBalance", request, SemanticRequestContext.empty());

        assertTrue(result.getSql().contains("balanceAmount__ratio"));
        org.mockito.ArgumentCaptor<ModelResultContext> captor = org.mockito.ArgumentCaptor.forClass(ModelResultContext.class);
        verify(queryFacade).buildSqlOnly(captor.capture());
        assertEquals(List.of("branch.name", "balanceDate.month", "sum(balanceAmount) AS balanceAmount",
                        "balanceAmount__ratio"),
                captor.getValue().getRequest().getParam().getColumns());
        assertEquals(Map.of(
                        "field", "balanceDate.month",
                        "grain", "month",
                        "comparison", "mom",
                        "targetMetrics", List.of("balanceAmount")),
                captor.getValue().getExtData().get("timeWindow"));
    }

    @Test
    @DisplayName("DSL_CTE generateSql can opt in to minimal row-level SLA calculatedFields bridge")
    void generateSqlOptInUsesMinimalRowLevelSlaBridge() {
        QueryFacade queryFacade = mock(QueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class)))
                .thenReturn(new SqlGenerationResult(
                        "SELECT team_name, COUNT(*) AS ticketCount, SUM(slaHit) AS slaHitCount FROM service_ticket",
                        List.of(),
                        null));
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);

        SemanticQueryRequest request = dslCtePlan(minimalRowLevelSlaPlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult result = service.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertTrue(result.getSql().contains("slaHitCount"));
        org.mockito.ArgumentCaptor<ModelResultContext> captor = org.mockito.ArgumentCaptor.forClass(ModelResultContext.class);
        verify(queryFacade).buildSqlOnly(captor.capture());
        assertEquals("ServiceTicketQueryModel", captor.getValue().getRequest().getParam().getQueryModel());
        assertEquals(List.of("team$caption", "count(ticketId) AS ticketCount", "sum(slaHit) AS slaHitCount"),
                captor.getValue().getRequest().getParam().getColumns());
        List<CalculatedFieldDef> calculatedFields = captor.getValue().getRequest().getParam().getCalculatedFields();
        assertEquals(2, calculatedFields.size());
        assertEquals("firstResponseHours", calculatedFields.get(0).getName());
        assertEquals("slaHit", calculatedFields.get(1).getName());
    }

    @Test
    @DisplayName("DSL_CTE generateSql can opt in to minimal SLA rate result-stage bridge")
    void generateSqlOptInUsesMinimalSlaRatePostSliceBridge() {
        QueryFacade queryFacade = mock(QueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class)))
                .thenReturn(new SqlGenerationResult(
                        "SELECT \"team$caption\", COUNT(ticket_id) AS \"ticketCount\", SUM(slaHit) AS \"slaHitCount\" FROM service_ticket GROUP BY \"team$caption\"",
                        List.of(),
                        null));
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);

        SemanticQueryRequest request = dslCtePlan(minimalSlaRatePostSlicePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult result = service.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertTrue(result.getSql().contains("dsl_cte_metric_ratio"));
        assertTrue(result.getSql().contains("slaAchievementRate"));
        assertTrue(result.getSql().contains("WHERE \"slaAchievementRate\" < ?"));
        assertEquals(List.of(0.85), result.getParams());
        org.mockito.ArgumentCaptor<ModelResultContext> captor = org.mockito.ArgumentCaptor.forClass(ModelResultContext.class);
        verify(queryFacade).buildSqlOnly(captor.capture());
        assertEquals(List.of("team$caption", "count(ticketId) AS ticketCount", "sum(slaHit) AS slaHitCount"),
                captor.getValue().getRequest().getParam().getColumns());
    }

    @Test
    @DisplayName("DSL_CTE generateSql can opt in to priority-aware SLA rate result-stage bridge")
    void generateSqlOptInUsesPriorityAwareSlaRatePostSliceBridge() {
        QueryFacade queryFacade = mock(QueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class)))
                .thenReturn(new SqlGenerationResult(
                        "SELECT \"team$caption\", COUNT(ticket_id) AS \"ticketCount\", SUM(slaHit) AS \"slaHitCount\" FROM service_ticket GROUP BY \"team$caption\"",
                        List.of(),
                        null));
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);

        SemanticQueryRequest request = dslCtePlan(priorityAwareSlaRatePostSlicePlan());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult result = service.generateSql(
                "ServiceTicketQueryModel", request, SemanticRequestContext.empty());

        assertTrue(result.getSql().contains("dsl_cte_metric_ratio"));
        org.mockito.ArgumentCaptor<ModelResultContext> captor = org.mockito.ArgumentCaptor.forClass(ModelResultContext.class);
        verify(queryFacade).buildSqlOnly(captor.capture());
        List<CalculatedFieldDef> calculatedFields = captor.getValue().getRequest().getParam().getCalculatedFields();
        assertEquals(3, calculatedFields.size());
        assertEquals("slaThresholdHours", calculatedFields.get(1).getName());
        assertEquals("iif(priority == 'P1', 4, iif(priority == 'P2', 24, iif(priority == 'P3', 48, null)))",
                calculatedFields.get(1).getExpression());
    }

    @Test
    @DisplayName("DSL_CTE generateSql can opt in to cumulative contribution result-stage window")
    void generateSqlOptInUsesResultStageWindowForCumulativeContribution() {
        QueryFacade queryFacade = mock(QueryFacade.class);
        when(queryFacade.buildSqlOnly(any(ModelResultContext.class)))
                .thenReturn(new SqlGenerationResult(
                        "SELECT \"customer.name\", SUM(amount) AS \"salesAmount\" FROM sale_order GROUP BY \"customer.name\"",
                        List.of(),
                        null));
        ReflectionTestUtils.setField(service, "queryFacade", queryFacade);

        SemanticQueryRequest request = dslCtePlan(biz006());
        request.setHints(Map.of("dslCteCompileToDsl", true));

        SqlGenerationResult result = service.generateSql("SaleOrder", request, SemanticRequestContext.empty());

        assertTrue(result.getSql().contains("dsl_cte_base"));
        assertTrue(result.getSql().contains("RANK() OVER"));
        assertTrue(result.getSql().contains("cumulativeShare"));
        assertTrue(result.getSql().contains("WHERE \"cumulativeShare\" <= ?"));
        assertEquals(List.of(0.8), result.getParams());
    }

    @Test
    @DisplayName("DSL_CTE result-stage window bridge still fails closed for unsupported cumulative postSlice")
    void resultStageWindowDefersUnsupportedPostSlice() {
        Map<String, Object> plan = biz006();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(2).put("filters", List.of(filter("salesRank", "<=", 10)));

        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(plan), SemanticRequestContext.empty());

        Map<String, Object> validation = response.getExecution().getDslCteValidation();
        assertEquals("BRIDGE_DEFERRED", validation.get("dsl_bridge_status"));
        @SuppressWarnings("unchecked")
        List<String> unsupported = (List<String>) validation.get("dsl_bridge_unsupported");
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("postSlice only on cumulative share alias")));
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

    @SuppressWarnings("unchecked")
    private List<String> bridgeUnsupported(Map<String, Object> ctePlan) {
        SemanticQueryResponse response = service.validateQuery(
                "SaleOrder", dslCtePlan(ctePlan), SemanticRequestContext.empty());

        Map<String, Object> validation = response.getExecution().getDslCteValidation();
        assertEquals("BRIDGE_DEFERRED", validation.get("dsl_bridge_status"));
        return (List<String>) validation.get("dsl_bridge_unsupported");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> windowContract(SemanticQueryResponse response, String stageName) {
        return stageContract(response, stageName, "window_contract");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stageContract(SemanticQueryResponse response, String stageName, String contractKey) {
        List<Map<String, Object>> stages = (List<Map<String, Object>>) response.getExecution()
                .getDslCteValidation().get("stages");
        return (Map<String, Object>) stages.stream()
                .filter(stage -> stageName.equals(stage.get("name")))
                .findFirst()
                .orElseThrow()
                .get(contractKey);
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

    private Map<String, Object> stringMetricAggregatePlan() {
        return plan(
                List.of(
                        stage("category_sales", "aggregate",
                                "input", model("SaleOrder"),
                                "groupBy", List.of("product.categoryName"),
                                "metrics", List.of("sum(amount) AS salesAmount"))
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

    private Map<String, Object> minimalRowLevelSlaPlan() {
        return plan(
                List.of(
                        stage("ticket_scope", "derive",
                                "input", model("ServiceTicketQueryModel"),
                                "filters", List.of(
                                        filter("createdAt", ">=", "2026-05-01 00:00:00"),
                                        filter("createdAt", "<", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        derived("firstResponseHours", "hours_between(createdAt, firstResponseAt)"),
                                        derived("slaHit", "firstResponseAt is not null and firstResponseHours <= 48"))),
                        stage("team_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        metric("ticketCount", "count(*)"),
                                        metric("slaHitCount", "sum(slaHit)")))
                ),
                List.of("team$caption", "ticketCount", "slaHitCount")
        );
    }

    private Map<String, Object> minimalSlaRatePostSlicePlan() {
        return plan(
                List.of(
                        stage("ticket_scope", "derive",
                                "input", model("ServiceTicketQueryModel"),
                                "filters", List.of(
                                        filter("createdAt", ">=", "2026-05-01 00:00:00"),
                                        filter("createdAt", "<", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        derived("firstResponseHours", "hours_between(createdAt, firstResponseAt)"),
                                        derived("slaHit", "firstResponseAt is not null and firstResponseHours <= 48"))),
                        stage("team_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        metric("ticketCount", "count(*)"),
                                        metric("slaHitCount", "sum(slaHit)"))),
                        stage("team_sla_rate", "derive",
                                "inputs", List.of("team_sla"),
                                "derived", List.of(derived("slaAchievementRate", "slaHitCount / ticketCount"))),
                        stage("low_sla_teams", "postSlice",
                                "inputs", List.of("team_sla_rate"),
                                "filters", List.of(filter("slaAchievementRate", "<", 0.85)))
                ),
                List.of("team$caption", "ticketCount", "slaAchievementRate")
        );
    }

    private Map<String, Object> priorityAwareSlaRatePostSlicePlan() {
        return plan(
                List.of(
                        stage("ticket_scope", "derive",
                                "input", model("ServiceTicketQueryModel"),
                                "filters", List.of(
                                        filter("createdAt", ">=", "2026-05-01 00:00:00"),
                                        filter("createdAt", "<", "2026-06-01 00:00:00")),
                                "derived", List.of(
                                        derived("firstResponseHours", "hours_between(createdAt, firstResponseAt)"),
                                        derived("slaThresholdHours", "priority_threshold(priority, P1=4, P2=24, P3=48)"),
                                        derived("slaHit", "firstResponseAt is not null and firstResponseHours <= slaThresholdHours"))),
                        stage("team_sla", "aggregate",
                                "inputs", List.of("ticket_scope"),
                                "groupBy", List.of("team$caption"),
                                "metrics", List.of(
                                        metric("ticketCount", "count(*)"),
                                        metric("slaHitCount", "sum(slaHit)"))),
                        stage("team_sla_rate", "derive",
                                "inputs", List.of("team_sla"),
                                "derived", List.of(derived("slaAchievementRate", "slaHitCount / ticketCount"))),
                        stage("low_sla_teams", "postSlice",
                                "inputs", List.of("team_sla_rate"),
                                "filters", List.of(filter("slaAchievementRate", "<", 0.85)))
                ),
                List.of("team$caption", "ticketCount", "slaHitCount", "slaAchievementRate")
        );
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

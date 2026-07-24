package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DslCtePlanningServiceTest {

    @Test
    @DisplayName("Planner selects simple DSL bridge and exposes compatible evidence")
    void selectsSimpleDslBridge() {
        DslCtePlanningService.DslCtePlan plan =
                DslCtePlanningService.plan(null, dslCtePlan(simpleAggregatePlan()));

        assertEquals(DslCtePlanningService.Kind.SIMPLE_DSL, plan.kind());
        assertTrue(plan.ready());
        assertNotNull(plan.simpleBridge().request());

        Map<String, Object> evidence = plan.validationEvidence();
        assertEquals("BRIDGE_READY", evidence.get("dsl_bridge_status"));
        assertEquals("SaleOrder", evidence.get("dsl_bridge_model"));
        assertNotNull(evidence.get("dsl_request"));
    }

    @Test
    @DisplayName("Planner selects result-stage metric ratio bridge after simple bridge defers")
    void selectsResultStageMetricRatioBridge() {
        DslCtePlanningService.DslCtePlan plan =
                DslCtePlanningService.plan(null, dslCtePlan(minimalSlaRatePostSlicePlan()));

        assertEquals(DslCtePlanningService.Kind.RESULT_STAGE_METRIC_RATIO, plan.kind());
        assertTrue(plan.ready());
        assertNotNull(plan.metricRatioBridge().baseRequest());

        Map<String, Object> evidence = plan.validationEvidence();
        assertEquals("BRIDGE_READY", evidence.get("dsl_bridge_status"));
        assertEquals("ServiceTicketQueryModel", evidence.get("dsl_bridge_model"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ratioEvidence =
                (Map<String, Object>) evidence.get("dsl_result_stage_metric_ratio");
        assertEquals("sla_metric_ratio", ratioEvidence.get("kind"));
        assertEquals("result_stage_metric_ratio", ratioEvidence.get("bridge_scope"));
    }

    @Test
    @DisplayName("Planner selects signed cross-model join-align bridge")
    void selectsCrossModelJoinAlignBridge() {
        DslCtePlanningService.DslCtePlan plan =
                DslCtePlanningService.plan(null, dslCtePlan(signedCrossModelCrmOrderJoinAlignBridge()));

        assertEquals(DslCtePlanningService.Kind.CROSS_MODEL_JOIN_ALIGN, plan.kind());
        assertTrue(plan.ready());
        assertEquals("CrmLead", plan.joinAlignBridge().leftModel());
        assertEquals("FactOrderQueryModel", plan.joinAlignBridge().rightModel());

        Map<String, Object> evidence = plan.validationEvidence();
        @SuppressWarnings("unchecked")
        Map<String, Object> models = (Map<String, Object>) evidence.get("dsl_bridge_models");
        assertEquals("CrmLead", models.get("left"));
        assertEquals("FactOrderQueryModel", models.get("right"));
        assertNotNull(evidence.get("dsl_cross_model_join_align"));
    }

    @Test
    @DisplayName("Planner combines unsupported reasons when no bridge is selected")
    void combinesUnsupportedReasons() {
        DslCtePlanningService.DslCtePlan plan =
                DslCtePlanningService.plan(null, dslCtePlan(aggregateValueFieldPostSlice()));

        assertEquals(DslCtePlanningService.Kind.UNSUPPORTED, plan.kind());
        assertEquals("BRIDGE_DEFERRED", plan.status());
        assertTrue(plan.unsupported().stream().anyMatch(msg -> msg.contains("valueField")));

        Map<String, Object> evidence = plan.validationEvidence();
        assertEquals("BRIDGE_DEFERRED", evidence.get("dsl_bridge_status"));
        @SuppressWarnings("unchecked")
        List<String> unsupported = (List<String>) evidence.get("dsl_bridge_unsupported");
        assertTrue(unsupported.stream().anyMatch(msg -> msg.contains("valueField")));
    }

    private SemanticQueryRequest dslCtePlan(Map<String, Object> ctePlan) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL_CTE");
        request.setExecutablePlan(m("cte_plan", ctePlan));
        return request;
    }

    private Map<String, Object> simpleAggregatePlan() {
        return plan(
                List.of(stage("category_sales", "aggregate",
                        "input", model("SaleOrder"),
                        "groupBy", List.of("product.categoryName"),
                        "metrics", List.of(metric("salesAmount", "sum(amount)")))),
                List.of("product.categoryName", "salesAmount"));
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

    private Map<String, Object> signedCrossModelCrmOrderJoinAlignBridge() {
        Map<String, Object> plan = plan(
                List.of(
                        stage("lead_orders", "aggregate",
                                "input", model("CrmLead"),
                                "filters", List.of(
                                        filter("createdAt", ">=", "2026-05-01 00:00:00"),
                                        filter("createdAt", "<", "2026-06-01 00:00:00")),
                                "groupBy", List.of("leadSource", "convertedOrderId", "createdAt"),
                                "metrics", List.of(metric("leadCount", "count(*)"))),
                        stage("completed_orders", "aggregate",
                                "input", model("FactOrderQueryModel"),
                                "filters", List.of(filter("orderStatus", "=", "COMPLETED")),
                                "groupBy", List.of("orderId"),
                                "metrics", List.of(metric("matchedOrderCount", "count(*)"))),
                        stage("verified_order_align", "join_align",
                                "inputs", List.of("lead_orders", "completed_orders"),
                                "keys", List.of("convertedOrderId=orderId"),
                                "joinType", "declared_key_align")
                ),
                List.of("leadSource", "convertedOrderId", "leadCount", "orderId", "matchedOrderCount")
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) plan.get("stages");
        stages.get(2).put("relationRef", "CrmLead.convertedOrderId -> FactOrderQueryModel.orderId");
        stages.get(2).put("cardinality", "many_to_one");
        stages.get(2).put("timeAttribution", m(
                "basis", "lead_created",
                "field", "createdAt",
                "sourceStage", "lead_orders"));
        stages.get(2).put("relation", m(
                "left", m(
                        "stage", "lead_orders",
                        "model", "CrmLead",
                        "field", "convertedOrderId"),
                "right", m(
                        "stage", "completed_orders",
                        "model", "FactOrderQueryModel",
                        "field", "orderId")));
        stages.get(2).put("runtimeGuard", m(
                "cardinality", m(
                        "enforce", true,
                        "policy", "fail_closed",
                        "leftMultiplicity", "many",
                        "rightMultiplicity", "one",
                        "nullKeyPolicy", "exclude_unmatched"),
                "timeAttribution", m(
                        "enforce", true,
                        "policy", "fail_closed",
                        "sourceStage", "lead_orders",
                        "sourceField", "createdAt",
                        "nullPolicy", "reject_null")));
        stages.get(2).put("output", List.of(
                "leadSource", "convertedOrderId", "leadCount",
                "orderId", "matchedOrderCount"));
        return plan;
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
                                "filters", List.of(m("field", "salesAmount", "op", ">", "valueField",
                                        "salesAmount")))
                ),
                List.of("product.categoryName", "salesAmount")
        );
    }

    private Map<String, Object> plan(List<Map<String, Object>> stages, List<String> output) {
        return m("stages", stages, "output", output);
    }

    private Map<String, Object> stage(String name, String type, Object... entries) {
        Map<String, Object> result = m("name", name, "type", type);
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }

    private Map<String, Object> model(String model) {
        return m("model", model);
    }

    private Map<String, Object> metric(String name, String expr) {
        return m("name", name, "expr", expr);
    }

    private Map<String, Object> derived(String name, String expr) {
        return m("name", name, "expr", expr);
    }

    private Map<String, Object> filter(String field, String op, Object value) {
        return m("field", field, "op", op, "value", value);
    }

    private Map<String, Object> m(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }
}

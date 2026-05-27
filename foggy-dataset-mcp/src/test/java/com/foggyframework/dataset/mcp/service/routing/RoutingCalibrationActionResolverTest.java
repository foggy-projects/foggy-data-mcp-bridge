package com.foggyframework.dataset.mcp.service.routing;

import com.foggyframework.dataset.mcp.schema.DatasetNLQueryRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RoutingCalibrationActionResolver")
class RoutingCalibrationActionResolverTest {

    private final RoutingCalibrationActionResolver resolver = new RoutingCalibrationActionResolver();

    @Test
    @DisplayName("无 guard 时执行原始计划")
    void noGuard_shouldExecuteRaw() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("查询销售额")
                .build();

        RoutingCalibrationAction action = resolver.resolve(request);

        assertEquals(RoutingCalibrationActionType.EXECUTE_RAW, action.type());
        assertFalse(action.requiresReplan());
        assertTrue(action.executionAllowed());
    }

    @Test
    @DisplayName("route 变化时要求按 calibrated_route 重新规划")
    void routeChanged_shouldRequireReplan() {
        DatasetNLQueryRequest request = requestWithGuard(Map.of(
                "raw_route", "SEMANTIC_SQL",
                "calibrated_route", "DSL",
                "requires_replan", true,
                "execution_allowed", false,
                "routing_calibration_applied_rules", List.of("dsl_audit_boundary")
        ));

        RoutingCalibrationAction action = resolver.resolve(request);

        assertEquals(RoutingCalibrationActionType.REPLAN_REQUIRED, action.type());
        assertEquals("SEMANTIC_SQL", action.rawRoute());
        assertEquals("DSL", action.calibratedRoute());
        assertEquals(List.of("dsl_audit_boundary"), action.appliedRules());
        assertTrue(action.requiresReplan());
        assertFalse(action.executionAllowed());
    }

    @Test
    @DisplayName("risk-only 校准只做审计")
    void riskOnlyChange_shouldAuditOnly() {
        DatasetNLQueryRequest request = requestWithGuard(Map.of(
                "raw_route", "CLARIFY",
                "calibrated_route", "CLARIFY",
                "raw_risks", List.of("needs_time_range"),
                "calibrated_risks", List.of("needs_time_range", "needs_metric_definition"),
                "applied_rules", List.of("vague_lead_quality")
        ));

        RoutingCalibrationAction action = resolver.resolve(request);

        assertEquals(RoutingCalibrationActionType.AUDIT_ONLY, action.type());
        assertFalse(action.requiresReplan());
        assertTrue(action.executionAllowed());
    }

    @Test
    @DisplayName("direct risk_changed guard 应被识别为审计")
    void directRiskChangedGuard_shouldAuditOnly() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("查询销售额")
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "risk_changed", true,
                                "calibrated_risks", List.of("needs_metric_definition")
                        ))
                        .build())
                .build();

        RoutingCalibrationAction action = resolver.resolve(request);

        assertEquals(RoutingCalibrationActionType.AUDIT_ONLY, action.type());
        assertFalse(action.requiresReplan());
        assertTrue(action.executionAllowed());
    }

    @Test
    @DisplayName("缺少 calibrated_route 的 replan guard 应阻断执行")
    void missingCalibratedRoute_shouldBlock() {
        DatasetNLQueryRequest request = requestWithGuard(Map.of(
                "raw_route", "SEMANTIC_SQL",
                "requires_replan", true,
                "execution_allowed", false
        ));

        RoutingCalibrationAction action = resolver.resolve(request);

        assertEquals(RoutingCalibrationActionType.BLOCKED, action.type());
        assertTrue(action.requiresReplan());
        assertFalse(action.executionAllowed());
    }

    @Test
    @DisplayName("兼容 camelCase guard 字段")
    void camelCaseGuard_shouldResolve() {
        DatasetNLQueryRequest request = DatasetNLQueryRequest.builder()
                .query("查询销售额")
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of(
                                "routingCalibrationGuard", Map.of(
                                        "rawRoute", "CLARIFY",
                                        "calibratedRoute", "DSL_CTE",
                                        "requiresReplan", true,
                                        "executionAllowed", false,
                                        "appliedRules", List.of("funnel_denominator_boundary")
                                )
                        ))
                        .build())
                .build();

        RoutingCalibrationAction action = resolver.resolve(request);

        assertEquals(RoutingCalibrationActionType.REPLAN_REQUIRED, action.type());
        assertEquals("DSL_CTE", action.calibratedRoute());
        assertEquals(List.of("funnel_denominator_boundary"), action.appliedRules());
    }

    private DatasetNLQueryRequest requestWithGuard(Map<String, Object> guard) {
        return DatasetNLQueryRequest.builder()
                .query("查询销售额")
                .hints(DatasetNLQueryRequest.QueryHints.builder()
                        .extra(Map.of("routing_calibration_guard", guard))
                        .build())
                .build();
    }
}

package com.foggyframework.dataset.mcp.service.routing;

import java.util.List;
import java.util.Map;

public record RoutingCalibrationAction(
        RoutingCalibrationActionType type,
        String rawRoute,
        String calibratedRoute,
        List<String> rawRisks,
        List<String> calibratedRisks,
        List<String> appliedRules,
        boolean requiresReplan,
        boolean executionAllowed,
        String reason
) {
    public static RoutingCalibrationAction executeRaw() {
        return new RoutingCalibrationAction(
                RoutingCalibrationActionType.EXECUTE_RAW,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                false,
                true,
                "no routing calibration guard"
        );
    }

    public Map<String, Object> toAuditMap() {
        return Map.of(
                "action", type.name(),
                "raw_route", rawRoute != null ? rawRoute : "",
                "calibrated_route", calibratedRoute != null ? calibratedRoute : "",
                "raw_risks", rawRisks,
                "calibrated_risks", calibratedRisks,
                "applied_rules", appliedRules,
                "requires_replan", requiresReplan,
                "execution_allowed", executionAllowed,
                "reason", reason
        );
    }
}

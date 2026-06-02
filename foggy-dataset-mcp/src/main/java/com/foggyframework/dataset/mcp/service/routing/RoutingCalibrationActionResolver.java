package com.foggyframework.dataset.mcp.service.routing;

import com.foggyframework.dataset.mcp.schema.DatasetNLQueryRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RoutingCalibrationActionResolver {

    private static final String SNAKE_GUARD_KEY = "routing_calibration_guard";
    private static final String CAMEL_GUARD_KEY = "routingCalibrationGuard";

    public RoutingCalibrationAction resolve(DatasetNLQueryRequest request) {
        Map<String, Object> guard = extractGuard(request);
        if (guard.isEmpty()) {
            return RoutingCalibrationAction.executeRaw();
        }

        String rawRoute = stringValue(firstPresent(guard, "raw_route", "rawRoute"));
        String calibratedRoute = stringValue(firstPresent(guard, "calibrated_route", "calibratedRoute"));
        List<String> rawRisks = stringList(firstPresent(guard, "raw_risks", "rawRisks"));
        List<String> calibratedRisks = stringList(firstPresent(guard, "calibrated_risks", "calibratedRisks"));
        List<String> appliedRules = stringList(firstPresent(
                guard,
                "routing_calibration_applied_rules",
                "applied_rules",
                "appliedRules"
        ));

        boolean requiresReplan = booleanValue(firstPresent(guard, "requires_replan", "requiresReplan"));
        boolean executionAllowed = !guard.containsKey("execution_allowed") && !guard.containsKey("executionAllowed")
                || booleanValue(firstPresent(guard, "execution_allowed", "executionAllowed"));
        boolean routeChanged = booleanValue(firstPresent(guard, "route_changed", "routeChanged"))
                || routesDiffer(rawRoute, calibratedRoute);
        boolean riskChanged = booleanValue(firstPresent(guard, "risk_changed", "riskChanged"))
                || risksDiffer(rawRisks, calibratedRisks);

        if ((requiresReplan || routeChanged || !executionAllowed) && isBlank(calibratedRoute)) {
            return new RoutingCalibrationAction(
                    RoutingCalibrationActionType.BLOCKED,
                    rawRoute,
                    calibratedRoute,
                    rawRisks,
                    calibratedRisks,
                    appliedRules,
                    true,
                    false,
                    "routing calibration requires replan but calibrated route is missing"
            );
        }

        if (isTerminalRoute(calibratedRoute)) {
            return new RoutingCalibrationAction(
                    RoutingCalibrationActionType.TERMINAL_ROUTE,
                    rawRoute,
                    calibratedRoute,
                    rawRisks,
                    calibratedRisks,
                    appliedRules,
                    false,
                    false,
                    "calibrated terminal route must stop before query tools"
            );
        }

        if (requiresReplan || routeChanged || !executionAllowed) {
            return new RoutingCalibrationAction(
                    RoutingCalibrationActionType.REPLAN_REQUIRED,
                    rawRoute,
                    calibratedRoute,
                    rawRisks,
                    calibratedRisks,
                    appliedRules,
                    true,
                    false,
                    "route-changing calibration requires fresh planning by calibrated route"
            );
        }

        if (riskChanged || !appliedRules.isEmpty()) {
            return new RoutingCalibrationAction(
                    RoutingCalibrationActionType.AUDIT_ONLY,
                    rawRoute,
                    calibratedRoute,
                    rawRisks,
                    calibratedRisks,
                    appliedRules,
                    false,
                    true,
                    "risk-only calibration is audit-only"
            );
        }

        return new RoutingCalibrationAction(
                RoutingCalibrationActionType.EXECUTE_RAW,
                rawRoute,
                calibratedRoute,
                rawRisks,
                calibratedRisks,
                appliedRules,
                false,
                true,
                "routing calibration guard does not change execution"
        );
    }

    private Map<String, Object> extractGuard(DatasetNLQueryRequest request) {
        if (request == null || request.getHints() == null || request.getHints().getExtra() == null) {
            return Map.of();
        }

        Map<String, Object> extra = request.getHints().getExtra();
        Object nested = extra.containsKey(SNAKE_GUARD_KEY) ? extra.get(SNAKE_GUARD_KEY) : extra.get(CAMEL_GUARD_KEY);
        if (nested instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    copied.put(String.valueOf(key), value);
                }
            });
            return copied;
        }

        if (extra.containsKey("requires_replan")
                || extra.containsKey("requiresReplan")
                || extra.containsKey("calibrated_route")
                || extra.containsKey("calibratedRoute")
                || extra.containsKey("route_changed")
                || extra.containsKey("routeChanged")
                || extra.containsKey("risk_changed")
                || extra.containsKey("riskChanged")
                || extra.containsKey("execution_allowed")
                || extra.containsKey("executionAllowed")) {
            return extra;
        }

        return Map.of();
    }

    private Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value).trim().toLowerCase();
        return "true".equals(text) || "1".equals(text) || "yes".equals(text);
    }

    private List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            List<String> result = new ArrayList<>();
            for (Object item : collection) {
                String text = stringValue(item);
                if (text != null) {
                    result.add(text);
                }
            }
            return List.copyOf(result);
        }
        String text = stringValue(value);
        return text == null ? List.of() : List.of(text);
    }

    private boolean routesDiffer(String rawRoute, String calibratedRoute) {
        return !isBlank(rawRoute)
                && !isBlank(calibratedRoute)
                && !rawRoute.equalsIgnoreCase(calibratedRoute);
    }

    private boolean risksDiffer(List<String> rawRisks, List<String> calibratedRisks) {
        return (!rawRisks.isEmpty() || !calibratedRisks.isEmpty())
                && !List.copyOf(rawRisks).equals(List.copyOf(calibratedRisks));
    }

    private boolean isTerminalRoute(String route) {
        return "CLARIFY".equalsIgnoreCase(route) || "REJECT".equalsIgnoreCase(route);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

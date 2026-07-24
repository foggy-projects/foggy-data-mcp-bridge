package com.foggyframework.dataset.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapts the narrow L1 DSL_CTE planner output contract into Java preflight input.
 */
public final class DslCtePlannerContractAdapter {

    public static final String CONTRACT_INVALID = "DSL_CTE_PLANNER_CONTRACT_INVALID";
    public static final String CONTRACT_UNSUPPORTED = "DSL_CTE_PLANNER_CONTRACT_UNSUPPORTED";

    private static final String PLANNER = "dsl_cte_planner";
    private static final String STATUS_PLAN_READY = "PLAN_READY";
    private static final String EXECUTION_SURFACE_DSL_CTE = "DSL_CTE";
    private static final String TEMPLATE_RELATION_RESULT_DERIVE = "relation_result_derive@v1";
    private static final String L3_ATTEMPT = "ATTEMPT";

    private DslCtePlannerContractAdapter() {
    }

    public static SemanticQueryRequest toQueryRequest(Object plannerOutput) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute(EXECUTION_SURFACE_DSL_CTE);
        request.setStatus(STATUS_PLAN_READY);
        request.setExecutablePlan(toExecutablePlan(plannerOutput));
        return request;
    }

    public static Map<String, Object> toExecutablePlan(Object plannerOutput) {
        Map<String, Object> root = requiredObject(plannerOutput, "planner output");
        requireExact(root, "planner", PLANNER, CONTRACT_INVALID);
        requireEnum(root, "status", STATUS_PLAN_READY, CONTRACT_UNSUPPORTED);
        requireEnum(root, "execution_surface", EXECUTION_SURFACE_DSL_CTE, CONTRACT_UNSUPPORTED);
        requireExact(root, "selected_template", TEMPLATE_RELATION_RESULT_DERIVE, CONTRACT_UNSUPPORTED);
        requireEnum(root, "l3_validation", L3_ATTEMPT, CONTRACT_UNSUPPORTED);

        List<Map<String, Object>> stages = requiredStageList(root.get("stages"));
        List<String> output = requiredStringList(root.get("output"), "output");

        Map<String, Object> ctePlan = new LinkedHashMap<>();
        ctePlan.put("stages", stages);
        ctePlan.put("output", output);

        Map<String, Object> executablePlan = new LinkedHashMap<>();
        executablePlan.put("cte_plan", ctePlan);
        return executablePlan;
    }

    private static void requireExact(Map<String, Object> root, String key, String expected, String errorCode) {
        String value = stringValue(root.get(key));
        if (!expected.equals(value)) {
            throw RX.throwB(errorCode + ": " + key + " must be " + expected + ".");
        }
    }

    private static void requireEnum(Map<String, Object> root, String key, String expected, String errorCode) {
        String value = stringValue(root.get(key));
        if (value == null || !expected.equalsIgnoreCase(value)) {
            throw RX.throwB(errorCode + ": " + key + " must be " + expected + ".");
        }
    }

    private static List<Map<String, Object>> requiredStageList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw RX.throwB(CONTRACT_INVALID + ": stages must be a non-empty object list.");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> map)) {
                throw RX.throwB(CONTRACT_INVALID + ": stages[" + i + "] must be an object.");
            }
            result.add(copyMap(map));
        }
        return result;
    }

    private static List<String> requiredStringList(Object value, String key) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw RX.throwB(CONTRACT_INVALID + ": " + key + " must be a non-empty string list.");
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            String item = stringValue(list.get(i));
            if (item == null || item.isBlank()) {
                throw RX.throwB(CONTRACT_INVALID + ": " + key + "[" + i + "] must be a non-empty string.");
            }
            result.add(item);
        }
        return result;
    }

    private static Map<String, Object> requiredObject(Object value, String label) {
        if (!(value instanceof Map<?, ?> map)) {
            throw RX.throwB(CONTRACT_INVALID + ": " + label + " must be an object.");
        }
        return copyMap(map);
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            result.put(String.valueOf(entry.getKey()), copyJsonValue(entry.getValue()));
        }
        return result;
    }

    private static List<Object> copyList(List<?> source) {
        List<Object> result = new ArrayList<>();
        for (Object item : source) {
            result.add(copyJsonValue(item));
        }
        return result;
    }

    private static Object copyJsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        if (value instanceof List<?> list) {
            return copyList(list);
        }
        return value;
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text.trim() : null;
    }
}

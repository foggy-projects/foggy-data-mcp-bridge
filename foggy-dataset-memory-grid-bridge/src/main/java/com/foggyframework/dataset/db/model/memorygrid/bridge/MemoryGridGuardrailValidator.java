package com.foggyframework.dataset.db.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Phase-1 guardrail for Memory Grid plans.
 *
 * <p>P0 only validates bounded governed inputs and does not execute an in-memory database.</p>
 */
public final class MemoryGridGuardrailValidator {

    public static final String UNBOUNDED_INPUT = "MEMORY_GRID_UNBOUNDED_INPUT";
    public static final String UNGOVERNED_SOURCE = "MEMORY_GRID_UNGOVERNED_SOURCE";
    public static final String GRAIN_MISMATCH = "MEMORY_GRID_GRAIN_MISMATCH";
    public static final String LIMIT_EXCEEDED = "MEMORY_GRID_LIMIT_EXCEEDED";

    private static final int MAX_INPUT_ROW_LIMIT = 500;
    private static final int MAX_INPUT_COUNT = 3;
    private static final int MAX_OUTPUT_LIMIT = 1000;
    private static final int MAX_CELL_COUNT = 50_000;
    private static final Set<String> GOVERNED_SOURCE_ROUTES = Set.of("DSL", "DSL_CTE", "SEMANTIC_SQL");

    private MemoryGridGuardrailValidator() {
    }

    public static Map<String, Object> validate(Map<String, Object> plan, SemanticRequestContext context) {
        if (plan == null || plan.isEmpty()) {
            throw RX.throwB(UNBOUNDED_INPUT + ": memory_grid_plan must be provided.");
        }

        List<Map<String, Object>> inputs = inputPlans(plan.get("inputs"));
        if (inputs.isEmpty()) {
            throw RX.throwB(UNBOUNDED_INPUT + ": memory_grid_plan.inputs must be non-empty.");
        }
        if (inputs.size() > MAX_INPUT_COUNT) {
            throw RX.throwB(LIMIT_EXCEEDED + ": memory grid input count exceeds phase-1 limit.");
        }

        int estimatedCells = 0;
        List<Map<String, Object>> inputEvidence = new ArrayList<>();
        Set<String> governedRoutes = new LinkedHashSet<>();
        Set<String> grains = new LinkedHashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            Map<String, Object> input = inputs.get(i);
            String name = stringValue(input.get("name"));
            String sourceRoute = normalizeRoute(input.get("source_route"));
            if (!GOVERNED_SOURCE_ROUTES.contains(sourceRoute) || !Boolean.TRUE.equals(input.get("governed"))) {
                throw RX.throwB(UNGOVERNED_SOURCE + ": input " + (i + 1) + " must come from governed DSL/DSL_CTE/Semantic SQL result.");
            }
            String resultHandle = stringValue(input.get("result_handle"));
            if (resultHandle == null || resultHandle.isBlank()) {
                throw RX.throwB(UNGOVERNED_SOURCE + ": input " + (i + 1) + " must declare governed result_handle.");
            }

            Integer rowLimit = intValue(input.get("row_limit"));
            if (rowLimit == null || rowLimit <= 0) {
                throw RX.throwB(UNBOUNDED_INPUT + ": input " + (i + 1) + " must declare positive row_limit.");
            }
            if (rowLimit > MAX_INPUT_ROW_LIMIT) {
                throw RX.throwB(LIMIT_EXCEEDED + ": input " + (i + 1) + " row_limit exceeds phase-1 limit.");
            }

            List<String> grain = stringList(input.get("grain"));
            if (grain.isEmpty()) {
                throw RX.throwB(GRAIN_MISMATCH + ": input " + (i + 1) + " grain must be declared.");
            }
            ensureFieldsAllowed(grain, context);

            List<?> metrics = listValue(input.get("metrics"));
            estimatedCells += rowLimit * (grain.size() + metrics.size());
            inputEvidence.add(Map.of(
                    "name", name == null ? "input_" + (i + 1) : name,
                    "source_route", sourceRoute,
                    "row_limit", rowLimit,
                    "grain", grain,
                    "governed", true
            ));
            governedRoutes.add(sourceRoute);
            grains.addAll(grain);
        }

        if (estimatedCells > MAX_CELL_COUNT) {
            throw RX.throwB(LIMIT_EXCEEDED + ": estimated input cells exceed phase-1 limit.");
        }

        Integer outputLimit = intValue(plan.get("output_limit"));
        if (outputLimit == null || outputLimit <= 0) {
            throw RX.throwB(UNBOUNDED_INPUT + ": output_limit must be declared.");
        }
        if (outputLimit > MAX_OUTPUT_LIMIT) {
            throw RX.throwB(LIMIT_EXCEEDED + ": output_limit exceeds phase-1 limit.");
        }

        Map<String, Object> join = mapValue(plan.get("join"));
        List<String> joinKeys = join == null ? List.of() : stringList(join.get("keys"));
        if (joinKeys.isEmpty()) {
            throw RX.throwB(GRAIN_MISMATCH + ": memory grid join keys must be declared.");
        }
        ensureFieldsAllowed(joinKeys, context);
        ensureJoinKeysInInputGrain(inputs, joinKeys);

        List<?> derived = listValue(plan.get("derived"));
        if (derived.isEmpty()) {
            throw RX.throwB(GRAIN_MISMATCH + ": memory grid derived formula must be declared.");
        }

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("inputs", inputEvidence);
        validation.put("governed_source_routes", new ArrayList<>(governedRoutes));
        validation.put("grain", new ArrayList<>(grains));
        validation.put("join_keys", joinKeys);
        validation.put("output_limit", outputLimit);
        validation.put("estimated_input_cells", estimatedCells);
        validation.put("limits", Map.of(
                "max_input_row_limit", MAX_INPUT_ROW_LIMIT,
                "max_input_count", MAX_INPUT_COUNT,
                "max_output_limit", MAX_OUTPUT_LIMIT,
                "max_cell_count", MAX_CELL_COUNT
        ));
        validation.put("denied", List.of());
        return validation;
    }

    private static void ensureFieldsAllowed(List<String> fields, SemanticRequestContext context) {
        if (context == null || context.getFieldAccess() == null) {
            return;
        }
        for (String field : fields) {
            if (!context.getFieldAccess().contains(field)) {
                throw RX.throwB(UNGOVERNED_SOURCE + ": field '" + field + "' is denied by semantic field access policy.");
            }
        }
    }

    private static void ensureJoinKeysInInputGrain(List<Map<String, Object>> inputs, List<String> joinKeys) {
        for (int i = 0; i < inputs.size(); i++) {
            List<String> grain = stringList(inputs.get(i).get("grain"));
            if (!grain.containsAll(joinKeys)) {
                throw RX.throwB(GRAIN_MISMATCH + ": input " + (i + 1) + " grain must contain every memory grid join key.");
            }
        }
    }

    private static List<Map<String, Object>> inputPlans(Object value) {
        List<?> list = listValue(value);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = mapValue(item);
            if (map == null) {
                throw RX.throwB(UNBOUNDED_INPUT + ": memory grid inputs must be objects.");
            }
            result.add(map);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private static List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static List<String> stringList(Object value) {
        List<?> list = listValue(value);
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = stringValue(item);
            if (text != null && !text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private static String normalizeRoute(Object value) {
        String route = stringValue(value);
        return route == null ? null : route.trim().toUpperCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

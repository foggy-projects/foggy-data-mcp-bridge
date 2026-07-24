package com.foggyframework.dataset.model.memorygrid.bridge;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;

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
    public static final String ALIGNMENT_CONTRACT_MISSING = "MEMORY_GRID_ALIGNMENT_CONTRACT_MISSING";
    public static final String ALIGNMENT_CONTRACT_MISMATCH = "MEMORY_GRID_ALIGNMENT_CONTRACT_MISMATCH";

    public static final int MAX_INPUT_ROW_LIMIT = 500;
    public static final int MAX_INPUT_COUNT = 3;
    public static final int MAX_OUTPUT_LIMIT = 1000;
    public static final int MAX_CELL_COUNT = 50_000;
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
        Set<String> models = new LinkedHashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
            Map<String, Object> input = inputs.get(i);
            String name = stringValue(input.get("name"));
            String model = stringValue(input.get("model"));
            if (model != null && !model.isBlank()) {
                models.add(model);
            }
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
        if (models.size() > 1) {
            validation.put("alignment_contract", validateCrossModelAlignmentContract(
                    plan,
                    inputs,
                    joinKeys,
                    derived));
        }
        validation.put("limits", Map.of(
                "max_input_row_limit", MAX_INPUT_ROW_LIMIT,
                "max_input_count", MAX_INPUT_COUNT,
                "max_output_limit", MAX_OUTPUT_LIMIT,
                "max_cell_count", MAX_CELL_COUNT
        ));
        validation.put("memory_grid_guard", productionGuardDescriptor());
        validation.put("denied", List.of());
        return validation;
    }

    public static Map<String, Object> productionGuardDescriptor() {
        Map<String, Object> guard = new LinkedHashMap<>();
        guard.put("guard_profile", "bounded-result-handle-v1");
        guard.put("handle_backend", "result_handle_store");
        guard.put("handle_replay_mode", "strict_owner_field_schema_replay");
        guard.put("bounded_inputs_required", true);
        guard.put("request_rows_allowed", false);
        guard.put("grid_sql_supported", false);
        guard.put("limits", limits());
        guard.put("handle_lifecycle", Map.of(
                "ttl_enforced", true,
                "invalidation_supported", true,
                "read_count_enforced", true,
                "cleanup_supported", true,
                "admin_inspect_supported", true,
                "storage_delete_on_cleanup", true,
                "audit_exposure", "external_safe_redacted"
        ));
        guard.put("cross_model_alignment_contract", Map.of(
                "required_for_distinct_input_models", true,
                "required_fields", List.of("template", "input_roles", "match_keys", "grain", "formula"),
                "target_or_forecast_requires_version_or_scenario", true,
                "supported_templates", List.of(
                        "bounded_cross_model_metric_merge@v1",
                        "bounded_target_achievement_merge@v1",
                        "bounded_forecast_deviation_merge@v1")
        ));
        guard.put("fail_closed_codes", List.of(
                UNBOUNDED_INPUT,
                UNGOVERNED_SOURCE,
                GRAIN_MISMATCH,
                LIMIT_EXCEEDED,
                ALIGNMENT_CONTRACT_MISSING,
                ALIGNMENT_CONTRACT_MISMATCH,
                MemoryGridExecutor.RESULT_HANDLE_NOT_FOUND,
                MemoryGridExecutor.RESULT_HANDLE_EXPIRED,
                MemoryGridExecutor.NAMESPACE_MISMATCH,
                MemoryGridExecutor.SOURCE_ROUTE_MISMATCH,
                MemoryGridExecutor.SCHEMA_MISMATCH,
                MemoryGridExecutor.SCHEMA_DRIFT,
                MemoryGridExecutor.AUTH_REPLAY_MISMATCH,
                MemoryGridExecutor.GOVERNANCE_MISMATCH,
                MemoryGridExecutor.STORAGE_UNAVAILABLE
        ));
        return guard;
    }

    public static Map<String, Object> limits() {
        return Map.of(
                "max_input_row_limit", MAX_INPUT_ROW_LIMIT,
                "max_input_count", MAX_INPUT_COUNT,
                "max_output_limit", MAX_OUTPUT_LIMIT,
                "max_cell_count", MAX_CELL_COUNT
        );
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

    private static Map<String, Object> validateCrossModelAlignmentContract(
            Map<String, Object> plan,
            List<Map<String, Object>> inputs,
            List<String> joinKeys,
            List<?> derived) {
        Map<String, Object> contract = mapValue(plan.get("alignment_contract"));
        if (contract == null || contract.isEmpty()) {
            throw RX.throwB(ALIGNMENT_CONTRACT_MISSING
                    + ": cross-model memory grid plan must declare alignment_contract.");
        }
        String template = stringValue(contract.get("template"));
        if (!Set.of(
                "bounded_cross_model_metric_merge@v1",
                "bounded_target_achievement_merge@v1",
                "bounded_forecast_deviation_merge@v1").contains(template)) {
            throw RX.throwB(ALIGNMENT_CONTRACT_MISMATCH
                    + ": alignment_contract.template is not supported.");
        }
        List<String> matchKeys = stringList(firstPresent(contract, "match_keys", "matchKeys"));
        if (!matchKeys.equals(joinKeys)) {
            throw RX.throwB(ALIGNMENT_CONTRACT_MISMATCH
                    + ": alignment_contract.match_keys must equal memory grid join.keys.");
        }
        List<String> contractGrain = stringList(contract.get("grain"));
        if (!new LinkedHashSet<>(contractGrain).equals(new LinkedHashSet<>(joinKeys))) {
            throw RX.throwB(ALIGNMENT_CONTRACT_MISMATCH
                    + ": alignment_contract.grain must match join key grain for v1.");
        }
        Map<String, String> inputRoles = inputRoles(inputs);
        if (inputRoles.size() < 2) {
            throw RX.throwB(ALIGNMENT_CONTRACT_MISMATCH
                    + ": cross-model alignment requires explicit input roles.");
        }
        Map<String, String> contractRoles = contractInputRoles(contract.get("input_roles"));
        if (!contractRoles.equals(inputRoles)) {
            throw RX.throwB(ALIGNMENT_CONTRACT_MISMATCH
                    + ": alignment_contract.input_roles must match explicit memory grid input roles.");
        }
        boolean versionOrScenarioDeclared =
                hasText(stringValue(contract.get("version"))) || hasText(stringValue(contract.get("scenario")));
        if ("bounded_target_achievement_merge@v1".equals(template)) {
            if (!inputRoles.containsKey("actual") || !inputRoles.containsKey("target")) {
                throw RX.throwB(ALIGNMENT_CONTRACT_MISMATCH
                        + ": target achievement alignment requires actual and target input roles.");
            }
            if (!versionOrScenarioDeclared) {
                throw RX.throwB(ALIGNMENT_CONTRACT_MISSING
                        + ": target achievement alignment must declare target version.");
            }
        }
        if ("bounded_forecast_deviation_merge@v1".equals(template)) {
            if (!inputRoles.containsKey("actual") || !inputRoles.containsKey("forecast")) {
                throw RX.throwB(ALIGNMENT_CONTRACT_MISMATCH
                        + ": forecast deviation alignment requires actual and forecast input roles.");
            }
            if (!versionOrScenarioDeclared) {
                throw RX.throwB(ALIGNMENT_CONTRACT_MISSING
                        + ": forecast deviation alignment must declare forecast scenario.");
            }
        }
        String formula = stringValue(contract.get("formula"));
        if (!hasText(formula) || !derivedExpressions(derived).contains(formula)) {
            throw RX.throwB(ALIGNMENT_CONTRACT_MISMATCH
                    + ": alignment_contract.formula must match a declared Memory Grid derived expression.");
        }

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("template", template);
        evidence.put("input_roles", inputRoles);
        evidence.put("match_keys", matchKeys);
        evidence.put("grain", contractGrain);
        evidence.put("version_or_scenario_declared", versionOrScenarioDeclared);
        evidence.put("formula", formula);
        return evidence;
    }

    private static Map<String, String> inputRoles(List<Map<String, Object>> inputs) {
        Map<String, String> roles = new LinkedHashMap<>();
        for (Map<String, Object> input : inputs) {
            String role = normalizeValue(input.get("role"));
            if (role != null) {
                roles.put(role, stringValue(input.get("name")));
            }
        }
        return roles;
    }

    private static Map<String, String> contractInputRoles(Object value) {
        Map<String, Object> rawRoles = mapValue(value);
        if (rawRoles == null || rawRoles.isEmpty()) {
            return Map.of();
        }
        Map<String, String> roles = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawRoles.entrySet()) {
            String role = normalizeValue(entry.getKey());
            String inputName = stringValue(entry.getValue());
            if (role != null && hasText(inputName)) {
                roles.put(role, inputName);
            }
        }
        return roles;
    }

    private static Set<String> derivedExpressions(List<?> derived) {
        Set<String> expressions = new LinkedHashSet<>();
        for (Object item : derived) {
            Map<String, Object> map = mapValue(item);
            String expr = map == null ? null : stringValue(map.get("expr"));
            if (hasText(expr)) {
                expressions.add(expr);
            }
        }
        return expressions;
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

    private static String normalizeValue(Object value) {
        String text = stringValue(value);
        return text == null || text.isBlank() ? null : text.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Object firstPresent(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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

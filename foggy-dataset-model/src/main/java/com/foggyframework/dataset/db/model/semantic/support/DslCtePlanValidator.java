package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase-1 contract validator for normalized DSL_CTE stage plans.
 *
 * <p>P0 validates governed stage shape only. It does not compile arbitrary CTE
 * plans or execute generated SQL.</p>
 */
public final class DslCtePlanValidator {

    public static final String PLAN_NOT_DECLARED = "DSL_CTE_PLAN_NOT_DECLARED";
    public static final String STAGE_INVALID = "DSL_CTE_STAGE_INVALID";
    public static final String STAGE_REFERENCE_INVALID = "DSL_CTE_STAGE_REFERENCE_INVALID";
    public static final String POST_SLICE_INVALID = "DSL_CTE_POST_SLICE_INVALID";

    private static final Set<String> ALLOWED_STAGE_TYPES = Set.of(
            "aggregate", "derive", "window_derive", "postSlice", "join_align"
    );

    private DslCtePlanValidator() {
    }

    public static Map<String, Object> validate(Object executablePlan) {
        Map<String, Object> ctePlan = extractCtePlan(executablePlan);
        List<Map<String, Object>> stages = stagePlans(ctePlan.get("stages"));
        if (stages.isEmpty()) {
            throw RX.throwB(PLAN_NOT_DECLARED + ": cte_plan.stages must be non-empty.");
        }

        List<String> outputs = stringList(ctePlan.get("output"));
        if (outputs.isEmpty()) {
            throw RX.throwB(PLAN_NOT_DECLARED + ": cte_plan.output must be non-empty.");
        }

        Set<String> names = new LinkedHashSet<>();
        Set<String> stageTypes = new LinkedHashSet<>();
        List<Map<String, Object>> stageEvidence = new ArrayList<>();
        boolean hasPostSlice = false;

        for (int i = 0; i < stages.size(); i++) {
            Map<String, Object> stage = stages.get(i);
            String name = requiredString(stage, "name", i);
            if (names.contains(name)) {
                throw RX.throwB(STAGE_INVALID + ": duplicate stage name '" + name + "'.");
            }

            String type = requiredString(stage, "type", i);
            if (!ALLOWED_STAGE_TYPES.contains(type)) {
                throw RX.throwB(STAGE_INVALID + ": unsupported stage type '" + type + "'.");
            }

            validateStageShape(stage, type, names, i);
            names.add(name);
            stageTypes.add(type);
            hasPostSlice = hasPostSlice || "postSlice".equals(type);
            stageEvidence.add(Map.of(
                    "name", name,
                    "type", type,
                    "source", stageSource(stage)
            ));
        }

        List<Map<String, Object>> sliceLowering = mapList(ctePlan.get("sliceLowering"));
        for (Map<String, Object> lowering : sliceLowering) {
            String to = stringValue(lowering.get("to"));
            if (!"postSlice".equals(to)) {
                throw RX.throwB(POST_SLICE_INVALID + ": sliceLowering.to must be postSlice for derived aliases.");
            }
        }

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("stage_count", stages.size());
        validation.put("stage_types", new ArrayList<>(stageTypes));
        validation.put("stages", stageEvidence);
        validation.put("output", outputs);
        validation.put("post_filter_required", hasPostSlice || !sliceLowering.isEmpty());
        validation.put("slice_lowering_count", sliceLowering.size());
        validation.put("execution_supported", false);
        validation.put("execution_note", "P0 validates normalized DSL_CTE stage contracts; SQL execution is not enabled here.");
        return validation;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractCtePlan(Object executablePlan) {
        Map<String, Object> root = mapValue(executablePlan);
        if (root == null || root.isEmpty()) {
            throw RX.throwB(PLAN_NOT_DECLARED + ": executable_plan must contain cte_plan.");
        }
        Object nested = root.get("cte_plan");
        Map<String, Object> ctePlan = mapValue(nested);
        if (ctePlan != null) {
            return ctePlan;
        }
        if (root.containsKey("stages")) {
            return root;
        }
        throw RX.throwB(PLAN_NOT_DECLARED + ": executable_plan.cte_plan must be provided.");
    }

    private static void validateStageShape(Map<String, Object> stage, String type,
                                           Set<String> knownStageNames, int index) {
        switch (type) {
            case "aggregate" -> {
                requireSource(stage, knownStageNames, index);
                if (stringList(stage.get("metrics")).isEmpty() && mapList(stage.get("metrics")).isEmpty()) {
                    throw RX.throwB(STAGE_INVALID + ": aggregate stage must declare metrics.");
                }
            }
            case "derive" -> {
                requireSource(stage, knownStageNames, index);
                if (mapList(stage.get("derived")).isEmpty()) {
                    throw RX.throwB(STAGE_INVALID + ": derive stage must declare derived formulas.");
                }
            }
            case "window_derive" -> {
                requirePriorInputs(stage, knownStageNames, index);
                if (mapValue(stage.get("window")) == null) {
                    throw RX.throwB(STAGE_INVALID + ": window_derive stage must declare window.");
                }
                if (mapList(stage.get("derived")).isEmpty()) {
                    throw RX.throwB(STAGE_INVALID + ": window_derive stage must declare derived formulas.");
                }
            }
            case "postSlice" -> {
                requirePriorInputs(stage, knownStageNames, index);
                if (mapList(stage.get("filters")).isEmpty()) {
                    throw RX.throwB(POST_SLICE_INVALID + ": postSlice stage must declare filters.");
                }
            }
            case "join_align" -> {
                List<String> inputs = stringList(stage.get("inputs"));
                if (inputs.size() < 2) {
                    throw RX.throwB(STAGE_REFERENCE_INVALID + ": join_align stage must declare at least two inputs.");
                }
                if (stringList(stage.get("keys")).isEmpty()) {
                    throw RX.throwB(STAGE_INVALID + ": join_align stage must declare alignment keys.");
                }
                String joinType = stringValue(stage.get("joinType"));
                if (joinType == null || joinType.isBlank()) {
                    throw RX.throwB(STAGE_INVALID + ": join_align stage must declare joinType.");
                }
            }
            default -> throw RX.throwB(STAGE_INVALID + ": unsupported stage type '" + type + "'.");
        }
    }

    private static void requireSource(Map<String, Object> stage, Set<String> knownStageNames, int index) {
        if (mapValue(stage.get("input")) != null) {
            return;
        }
        requirePriorInputs(stage, knownStageNames, index);
    }

    private static void requirePriorInputs(Map<String, Object> stage, Set<String> knownStageNames, int index) {
        List<String> inputs = stringList(stage.get("inputs"));
        if (inputs.isEmpty()) {
            throw RX.throwB(STAGE_REFERENCE_INVALID + ": stage " + (index + 1) + " must declare inputs.");
        }
        for (String input : inputs) {
            if (!knownStageNames.contains(input)) {
                throw RX.throwB(STAGE_REFERENCE_INVALID + ": stage input '" + input + "' must reference a prior stage.");
            }
        }
    }

    private static String stageSource(Map<String, Object> stage) {
        Map<String, Object> input = mapValue(stage.get("input"));
        if (input != null) {
            String model = stringValue(input.get("model"));
            return model == null ? "model" : model;
        }
        List<String> inputs = stringList(stage.get("inputs"));
        return inputs.isEmpty() ? "unknown" : String.join(",", inputs);
    }

    private static String requiredString(Map<String, Object> stage, String key, int index) {
        String value = stringValue(stage.get(key));
        if (value == null || value.isBlank()) {
            throw RX.throwB(STAGE_INVALID + ": stage " + (index + 1) + " must declare " + key + ".");
        }
        return value;
    }

    private static List<Map<String, Object>> stagePlans(Object value) {
        return mapList(value);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = mapValue(item);
            if (map == null) {
                throw RX.throwB(STAGE_INVALID + ": stage entries must be objects.");
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

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = stringValue(item);
            if (text != null && !text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}

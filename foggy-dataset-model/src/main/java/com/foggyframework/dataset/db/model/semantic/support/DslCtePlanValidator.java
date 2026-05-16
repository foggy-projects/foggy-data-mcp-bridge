package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        Map<String, StageOutput> stageOutputs = new LinkedHashMap<>();
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
            StageOutput output = inferStageOutput(stage, type, stageOutputs);
            names.add(name);
            stageOutputs.put(name, output);
            stageTypes.add(type);
            hasPostSlice = hasPostSlice || "postSlice".equals(type);
            stageEvidence.add(stageEvidence(name, type, stage, output));
        }

        validatePlanOutput(outputs, stages, stageOutputs);

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

    private static StageOutput inferStageOutput(Map<String, Object> stage, String type,
                                                Map<String, StageOutput> stageOutputs) {
        return switch (type) {
            case "aggregate" -> aggregateOutput(stage);
            case "derive", "window_derive" -> derivedOutput(stage, stageOutputs);
            case "postSlice" -> postSliceOutput(stage, stageOutputs);
            case "join_align" -> joinAlignOutput(stage, stageOutputs);
            default -> StageOutput.incomplete(Set.of());
        };
    }

    private static StageOutput aggregateOutput(Map<String, Object> stage) {
        Set<String> fields = new LinkedHashSet<>(stringList(stage.get("groupBy")));
        Object rawMetrics = stage.get("metrics");
        if (rawMetrics instanceof List<?> metrics) {
            for (Object item : metrics) {
                Map<String, Object> metric = mapValue(item);
                if (metric != null) {
                    String name = stringValue(metric.get("name"));
                    if (name != null && !name.isBlank()) {
                        fields.add(name);
                    }
                    continue;
                }
                String alias = metricAlias(stringValue(item));
                if (alias != null && !alias.isBlank()) {
                    fields.add(alias);
                }
            }
        }
        return StageOutput.complete(fields);
    }

    private static StageOutput derivedOutput(Map<String, Object> stage, Map<String, StageOutput> stageOutputs) {
        StageOutput base = mergedInputOutput(stage, stageOutputs);
        Set<String> fields = new LinkedHashSet<>(base.fields());
        for (Map<String, Object> derived : mapList(stage.get("derived"))) {
            String name = stringValue(derived.get("name"));
            if (name != null && !name.isBlank()) {
                fields.add(name);
            }
        }
        return new StageOutput(fields, base.complete());
    }

    private static StageOutput postSliceOutput(Map<String, Object> stage, Map<String, StageOutput> stageOutputs) {
        StageOutput base = mergedInputOutput(stage, stageOutputs);
        if (base.complete()) {
            for (Map<String, Object> filter : mapList(stage.get("filters"))) {
                validateAvailableField(base, filter.get("field"), "postSlice field");
                validateAvailableField(base, filter.get("valueField"), "postSlice valueField");
            }
        }
        return base;
    }

    private static StageOutput joinAlignOutput(Map<String, Object> stage, Map<String, StageOutput> stageOutputs) {
        Set<String> fields = new LinkedHashSet<>();
        for (String input : stringList(stage.get("inputs"))) {
            StageOutput output = stageOutputs.get(input);
            if (output != null) {
                fields.addAll(output.fields());
            }
        }
        return StageOutput.incomplete(fields);
    }

    private static StageOutput mergedInputOutput(Map<String, Object> stage, Map<String, StageOutput> stageOutputs) {
        List<String> inputs = stringList(stage.get("inputs"));
        if (inputs.isEmpty()) {
            return StageOutput.incomplete(Set.of());
        }
        Set<String> fields = new LinkedHashSet<>();
        boolean complete = true;
        for (String input : inputs) {
            StageOutput output = stageOutputs.get(input);
            if (output == null) {
                complete = false;
            } else {
                fields.addAll(output.fields());
                complete = complete && output.complete();
            }
        }
        return new StageOutput(fields, complete);
    }

    private static void validatePlanOutput(List<String> outputs, List<Map<String, Object>> stages,
                                           Map<String, StageOutput> stageOutputs) {
        String finalStageName = stringValue(stages.get(stages.size() - 1).get("name"));
        StageOutput finalOutput = finalStageName == null ? null : stageOutputs.get(finalStageName);
        if (finalOutput == null || !finalOutput.complete()) {
            return;
        }
        for (String output : outputs) {
            if (!finalOutput.fields().contains(output)) {
                throw RX.throwB(STAGE_INVALID + ": cte_plan.output references unavailable field '" + output + "'.");
            }
        }
    }

    private static void validateAvailableField(StageOutput output, Object rawField, String usage) {
        String field = stringValue(rawField);
        if (field != null && !field.isBlank() && !output.fields().contains(field)) {
            throw RX.throwB(POST_SLICE_INVALID + ": " + usage + " references unavailable field '" + field + "'.");
        }
    }

    private static Map<String, Object> stageEvidence(String name, String type, Map<String, Object> stage,
                                                     StageOutput output) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("name", name);
        evidence.put("type", type);
        evidence.put("source", stageSource(stage));
        evidence.put("output_fields", new ArrayList<>(output.fields()));
        evidence.put("output_complete", output.complete());
        if ("window_derive".equals(type)) {
            evidence.put("window_contract", windowContractEvidence(stage));
        } else if ("derive".equals(type)) {
            Map<String, Object> deriveContract = deriveContractEvidence(stage);
            if (!deriveContract.isEmpty()) {
                evidence.put("derive_contract", deriveContract);
            }
        } else if ("aggregate".equals(type)) {
            Map<String, Object> aggregateContract = aggregateContractEvidence(stage);
            if (!aggregateContract.isEmpty()) {
                evidence.put("aggregate_contract", aggregateContract);
            }
        }
        return evidence;
    }

    private static Map<String, Object> deriveContractEvidence(Map<String, Object> stage) {
        List<Map<String, Object>> derived = mapList(stage.get("derived"));
        Map<String, Object> evidence = new LinkedHashMap<>();
        boolean preAggregate = mapValue(stage.get("input")) != null;
        boolean hasDuration = false;
        boolean hasBooleanPredicate = false;
        boolean hasMetricRatio = false;
        boolean hasPriorityThreshold = false;
        for (Map<String, Object> item : derived) {
            String expr = stringValue(item.get("expr"));
            if (expr == null) {
                continue;
            }
            String normalized = expr.toLowerCase(Locale.ROOT);
            hasDuration = hasDuration || normalized.contains("hours_between(");
            hasBooleanPredicate = hasBooleanPredicate
                    || normalized.contains(" is null")
                    || normalized.contains(" is not null")
                    || normalized.contains(" and ")
                    || normalized.contains(" or ");
            hasMetricRatio = hasMetricRatio || isMetricToMetricRatio(expr);
            hasPriorityThreshold = hasPriorityThreshold || normalized.contains("priority_threshold(");
        }
        if (preAggregate && (hasDuration || hasBooleanPredicate || hasPriorityThreshold)) {
            evidence.put("kind", "sla_row_level_derived");
            evidence.put("bridge_scope", "row_level_calculatedFields");
            evidence.put("bridge_signed", false);
            List<String> capabilities = new ArrayList<>(List.of(
                    "governed_duration_function_mapping",
                    "row_level_boolean_predicate",
                    "null_handling_predicate",
                    "conditional_numerator_source"));
            if (hasPriorityThreshold) {
                capabilities.add("priority_threshold_mapping");
            }
            evidence.put("required_capabilities", capabilities);
            return evidence;
        }
        if (!preAggregate && hasMetricRatio) {
            evidence.put("kind", "metric_to_metric_ratio");
            evidence.put("bridge_scope", "post_aggregate_calculation");
            evidence.put("bridge_signed", false);
            evidence.put("required_capabilities", List.of(
                    "post_aggregate_metric_reference",
                    "division_by_zero_policy",
                    "postSlice_on_derived_rate"));
            return evidence;
        }
        return evidence;
    }

    private static Map<String, Object> aggregateContractEvidence(Map<String, Object> stage) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        boolean hasConditionalMetric = false;
        Object rawMetrics = stage.get("metrics");
        if (rawMetrics instanceof List<?> metrics) {
            for (Object item : metrics) {
                Map<String, Object> metric = mapValue(item);
                if (metric == null) {
                    continue;
                }
                String expr = stringValue(metric.get("expr"));
                hasConditionalMetric = hasConditionalMetric
                        || (expr != null && expr.toLowerCase(Locale.ROOT).contains("case when"));
            }
        }
        if (hasConditionalMetric) {
            evidence.put("kind", "conditional_numerator_aggregation");
            evidence.put("bridge_scope", "aggregate_metrics");
            evidence.put("bridge_signed", false);
            evidence.put("required_capabilities", List.of(
                    "case_when_over_row_level_derived",
                    "boolean_metric_sum",
                    "governed_denominator_alignment"));
        }
        return evidence;
    }

    private static Map<String, Object> windowContractEvidence(Map<String, Object> stage) {
        List<Map<String, Object>> derived = mapList(stage.get("derived"));
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (containsLagOrLead(derived)) {
            evidence.put("kind", "period_over_period_lag");
            evidence.put("bridge_scope", "timeWindow");
            evidence.put("bridge_signed", true);
            evidence.put("required_capabilities", List.of("aggregate_metric_lag", "period_ratio", "time_grain_alignment"));
            return evidence;
        }
        if (containsRankOrCumulativeContribution(derived)) {
            evidence.put("kind", "cumulative_contribution");
            evidence.put("bridge_scope", "result_stage_window");
            evidence.put("bridge_signed", true);
            evidence.put("required_capabilities", List.of(
                    "rank_over_aggregate_metric_order",
                    "running_total_ratio",
                    "deterministic_result_ordering",
                    "postSlice_on_window_alias"));
            return evidence;
        }
        if (containsRollingSum(derived)) {
            evidence.put("kind", "rolling_sum");
            evidence.put("bridge_scope", "timeWindow");
            evidence.put("bridge_signed", true);
            evidence.put("required_capabilities", List.of("aggregate_time_series", "bounded_rows_frame"));
            return evidence;
        }
        evidence.put("kind", "generic_window");
        evidence.put("bridge_scope", "result_stage_window");
        evidence.put("bridge_signed", false);
        evidence.put("required_capabilities", List.of("window_function_contract"));
        return evidence;
    }

    private static boolean containsLagOrLead(List<Map<String, Object>> derived) {
        for (Map<String, Object> item : derived) {
            String expr = stringValue(item.get("expr"));
            if (expr != null && expr.toLowerCase(Locale.ROOT).matches(".*\\b(lag|lead)\\s*\\(.*")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRankOrCumulativeContribution(List<Map<String, Object>> derived) {
        for (Map<String, Object> item : derived) {
            String expr = stringValue(item.get("expr"));
            if (expr == null) {
                continue;
            }
            String normalized = expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if ("rank()".equals(normalized) || normalized.contains("overorder")
                    || normalized.contains("overall")) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsRollingSum(List<Map<String, Object>> derived) {
        for (Map<String, Object> item : derived) {
            String expr = stringValue(item.get("expr"));
            if (expr == null) {
                continue;
            }
            String normalized = expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (normalized.matches("sum\\([a-z_][a-z0-9_$]*\\)overlast\\d+rows")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMetricToMetricRatio(String expr) {
        if (expr == null) {
            return false;
        }
        String normalized = expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return normalized.matches("[a-z_][a-z0-9_$]*(?:\\.[a-z_][a-z0-9_$]*)?/[a-z_][a-z0-9_$]*(?:\\.[a-z_][a-z0-9_$]*)?")
                && !normalized.contains("sum(")
                && !normalized.contains("over(");
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

    private static String metricAlias(String metric) {
        if (metric == null) {
            return null;
        }
        String[] parts = metric.split("(?i)\\s+as\\s+");
        return parts.length == 2 ? parts[1].trim() : null;
    }

    private record StageOutput(Set<String> fields, boolean complete) {
        private static StageOutput complete(Set<String> fields) {
            return new StageOutput(new LinkedHashSet<>(fields), true);
        }

        private static StageOutput incomplete(Set<String> fields) {
            return new StageOutput(new LinkedHashSet<>(fields), false);
        }
    }
}

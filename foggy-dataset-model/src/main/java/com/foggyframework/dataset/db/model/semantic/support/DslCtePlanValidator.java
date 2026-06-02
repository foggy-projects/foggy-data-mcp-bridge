package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            "aggregate", "derive", "window_derive", "postSlice", "orderBy", "join_align"
    );
    private static final Set<String> SIGNED_JOIN_ALIGN_CARDINALITIES = Set.of(
            "one_to_one", "one_to_many", "many_to_one"
    );
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*(?:\\.[A-Za-z_][A-Za-z0-9_$]*)?");
    private static final Pattern SIGNED_CONDITIONAL_COUNT_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*case\\s+when\\s+[A-Za-z_][A-Za-z0-9_$.]*\\s+"
                    + "(?:is\\s+not\\s+null|(?:=|==|!=|<>)\\s*'(?:[^']|'')*')"
                    + "\\s+then\\s+1\\s+else\\s+0\\s+end\\s*\\)\\s*$");
    private static final Pattern SIGNED_CONDITIONAL_VALUE_SUM_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*case\\s+when\\s+[A-Za-z_][A-Za-z0-9_$.]*\\s+"
                    + "(?:is\\s+not\\s+null|(?:=|==|!=|<>)\\s*'(?:[^']|'')*')"
                    + "\\s+then\\s+[A-Za-z_][A-Za-z0-9_$.]*\\s+else\\s+0(?:\\.0+)?\\s+end\\s*\\)\\s*$");
    private static final Pattern ORDERED_BUCKET_PATTERN = Pattern.compile(
            "(?i)^\\s*case\\s+(.+)\\s+else\\s*'((?:[^']|'')*)'\\s+end\\s*$");
    private static final Pattern ORDERED_BUCKET_WHEN_PATTERN = Pattern.compile(
            "(?i)\\G\\s*when\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*(<=|>=|<>|!=|==|=|<|>)\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s+then\\s*'((?:[^']|'')*)'\\s*");
    private static final Pattern RESULT_STAGE_FORMULA_CALL_PATTERN = Pattern.compile(
            "(?i)^\\s*(ratio_to_total|ratioToTotal|cumulative_sum|cumulativeSum|"
                    + "cumulative_ratio_to_total|cumulativeRatioToTotal|rank_by|rankBy|rank_desc|rankDesc)"
                    + "\\s*\\((.*)\\)\\s*$");
    private static final Set<String> EXPRESSION_KEYWORDS = Set.of(
            "and", "or", "is", "not", "null", "true", "false",
            "case", "when", "then", "else", "end", "as", "over", "rows",
            "current", "row", "preceding", "following", "order", "all", "last", "asc", "desc",
            "count", "sum", "avg", "min", "max", "iif", "if", "coalesce", "nullif", "round", "abs",
            "hours_between",
            "priority_threshold", "lag", "lead", "rank",
            "ratio_to_total", "ratiototal",
            "cumulative_sum", "cumulativesum",
            "cumulative_ratio_to_total", "cumulativeratiototal",
            "rank_by", "rankby", "rank_desc", "rankdesc");

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
        boolean hasPostResultStage = false;

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
            validateStageContractConsistency(stage, type, stageOutputs);
            names.add(name);
            stageOutputs.put(name, output);
            stageTypes.add(type);
            hasPostResultStage = hasPostResultStage || "postSlice".equals(type) || "orderBy".equals(type);
            stageEvidence.add(stageEvidence(name, type, stage, output));
        }

        validatePlanOutput(outputs, stages, stageOutputs, ctePlan);

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
        validation.put("post_filter_required", hasPostResultStage || !sliceLowering.isEmpty());
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
            case "orderBy" -> {
                requirePriorInputs(stage, knownStageNames, index);
                if (mapList(stage.get("orderBy")).isEmpty()) {
                    throw RX.throwB(STAGE_INVALID + ": orderBy stage must declare orderBy.");
                }
                validateLimit(stage);
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
                validateSignedJoinAlignShape(stage);
            }
            default -> throw RX.throwB(STAGE_INVALID + ": unsupported stage type '" + type + "'.");
        }
    }

    private static StageOutput inferStageOutput(Map<String, Object> stage, String type,
                                                Map<String, StageOutput> stageOutputs) {
        return switch (type) {
            case "aggregate" -> aggregateOutput(stage);
            case "derive" -> derivedOutput(stage, stageOutputs, true);
            case "window_derive" -> derivedOutput(stage, stageOutputs, false);
            case "postSlice" -> postSliceOutput(stage, stageOutputs);
            case "orderBy" -> orderByOutput(stage, stageOutputs);
            case "join_align" -> joinAlignOutput(stage, stageOutputs);
            default -> StageOutput.incomplete(Set.of(), sourceModels(stage), sourceFields(stage));
        };
    }

    private static StageOutput aggregateOutput(Map<String, Object> stage) {
        Set<String> fields = new LinkedHashSet<>(stringList(stage.get("groupBy")));
        Set<String> sourceFields = new LinkedHashSet<>(fields);
        collectFilterFields(stage.get("filters"), sourceFields);
        Object rawMetrics = stage.get("metrics");
        if (rawMetrics instanceof List<?> metrics) {
            for (Object item : metrics) {
                Map<String, Object> metric = mapValue(item);
                if (metric != null) {
                    String name = stringValue(metric.get("name"));
                    if (name != null && !name.isBlank()) {
                        fields.add(name);
                    }
                    collectExpressionFields(stringValue(metric.get("expr")), sourceFields);
                    continue;
                }
                String metricText = stringValue(item);
                collectExpressionFields(metricText, sourceFields);
                String alias = metricAlias(metricText);
                if (alias != null && !alias.isBlank()) {
                    fields.add(alias);
                }
            }
        }
        return StageOutput.complete(fields, sourceModels(stage), sourceFields);
    }

    private static StageOutput derivedOutput(Map<String, Object> stage, Map<String, StageOutput> stageOutputs,
                                             boolean validateVisibleInputs) {
        StageOutput base = mergedInputOutput(stage, stageOutputs);
        Set<String> fields = new LinkedHashSet<>(base.fields());
        Set<String> sourceFields = new LinkedHashSet<>(base.sourceFields());
        collectFilterFields(stage.get("filters"), sourceFields);
        List<Map<String, Object>> derivedItems = mapList(stage.get("derived"));
        Set<String> sameStageAliases = derivedAliases(derivedItems);
        if (validateVisibleInputs && base.complete()) {
            validateSameStageDerivedAliasDag(derivedItems, base, sameStageAliases);
        }
        for (Map<String, Object> derived : derivedItems) {
            String name = stringValue(derived.get("name"));
            String expr = stringValue(derived.get("expr"));
            if (validateVisibleInputs && base.complete()) {
                validateDerivedExpressionReferences(name, expr, base, sameStageAliases);
            }
            if (name != null && !name.isBlank()) {
                fields.add(name);
            }
            collectExpressionFields(expr, sourceFields);
        }
        Set<String> sourceModels = base.sourceModels().isEmpty() ? sourceModels(stage) : base.sourceModels();
        return new StageOutput(fields, base.complete(), sourceModels, sourceFields);
    }

    private static void validateDerivedExpressionReferences(String name, String expr, StageOutput base,
                                                            Set<String> sameStageAliases) {
        validateResultStageFormulaOptions(name, expr);
        Set<String> dependencies = new LinkedHashSet<>();
        collectExpressionFields(expr, dependencies);
        for (String dependency : dependencies) {
            if (!base.fields().contains(dependency) && !sameStageAliases.contains(dependency)) {
                String alias = name == null || name.isBlank() ? "<unnamed>" : name;
                throw RX.throwB(STAGE_INVALID + ": derive expression '" + alias
                        + "' references unavailable input field '" + dependency
                        + "'; use prior output fields or same-stage aliases that can be auto-layered.");
            }
        }
    }

    private static void validateResultStageFormulaOptions(String name, String expr) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        Matcher matcher = RESULT_STAGE_FORMULA_CALL_PATTERN.matcher(expr);
        if (!matcher.matches()) {
            return;
        }
        String function = matcher.group(1).toLowerCase(Locale.ROOT);
        List<String> args = splitTopLevelArgs(matcher.group(2));
        boolean allowed = switch (function) {
            case "ratio_to_total", "ratiototal" -> args.size() == 1;
            case "cumulative_sum", "cumulativesum", "cumulative_ratio_to_total", "cumulativeratiototal",
                    "rank_by", "rankby", "rank_desc", "rankdesc" ->
                    args.size() == 1 || (args.size() == 2 && "desc".equalsIgnoreCase(args.get(1).trim()));
            default -> false;
        };
        if (!allowed) {
            String alias = name == null || name.isBlank() ? "<unnamed>" : name;
            throw RX.throwB(STAGE_INVALID + ": derive expression '" + alias
                    + "' uses unsupported result-stage formula options. Signed result-stage formulas only support "
                    + "grandTotal scope, DESC order, and implicit frame; partition, ASC order, explicit frame, "
                    + "and extra arguments are unsigned.");
        }
    }

    private static List<String> splitTopLevelArgs(String argsText) {
        List<String> args = new ArrayList<>();
        if (argsText == null || argsText.isBlank()) {
            return args;
        }
        int depth = 0;
        int start = 0;
        for (int i = 0; i < argsText.length(); i++) {
            char ch = argsText.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth = Math.max(0, depth - 1);
            } else if (ch == ',' && depth == 0) {
                args.add(argsText.substring(start, i).trim());
                start = i + 1;
            }
        }
        args.add(argsText.substring(start).trim());
        return args;
    }

    private static Set<String> derivedAliases(List<Map<String, Object>> derivedItems) {
        Set<String> aliases = new LinkedHashSet<>();
        for (Map<String, Object> derived : derivedItems) {
            String name = stringValue(derived.get("name"));
            if (name != null && !name.isBlank()) {
                aliases.add(name);
            }
        }
        return aliases;
    }

    private static void validateSameStageDerivedAliasDag(List<Map<String, Object>> derivedItems,
                                                         StageOutput base,
                                                         Set<String> sameStageAliases) {
        Set<String> visible = new LinkedHashSet<>(base.fields());
        Set<String> pending = new LinkedHashSet<>(sameStageAliases);
        while (!pending.isEmpty()) {
            boolean progressed = false;
            for (Map<String, Object> derived : derivedItems) {
                String name = stringValue(derived.get("name"));
                if (name == null || !pending.contains(name)) {
                    continue;
                }
                Set<String> dependencies = new LinkedHashSet<>();
                collectExpressionFields(stringValue(derived.get("expr")), dependencies);
                dependencies.retainAll(sameStageAliases);
                if (visible.containsAll(dependencies)) {
                    visible.add(name);
                    pending.remove(name);
                    progressed = true;
                }
            }
            if (!progressed) {
                throw RX.throwB(STAGE_INVALID
                        + ": same-stage derive alias references must form an acyclic auto-layerable DAG.");
            }
        }
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

    private static StageOutput orderByOutput(Map<String, Object> stage, Map<String, StageOutput> stageOutputs) {
        StageOutput base = mergedInputOutput(stage, stageOutputs);
        for (Map<String, Object> order : mapList(stage.get("orderBy"))) {
            if (order.containsKey("expr")) {
                throw RX.throwB(STAGE_INVALID + ": orderBy stage does not support expression orderBy.");
            }
            if (base.complete()) {
                validateAvailableField(base, order.get("field"), "orderBy field", STAGE_INVALID);
            }
            String dir = stringValue(order.get("dir"));
            if (dir != null && !dir.isBlank()
                    && !"asc".equalsIgnoreCase(dir)
                    && !"desc".equalsIgnoreCase(dir)) {
                throw RX.throwB(STAGE_INVALID + ": orderBy dir must be ASC or DESC.");
            }
        }
        return base;
    }

    private static StageOutput joinAlignOutput(Map<String, Object> stage, Map<String, StageOutput> stageOutputs) {
        Set<String> fields = new LinkedHashSet<>();
        Set<String> sourceModels = new LinkedHashSet<>();
        Set<String> sourceFields = new LinkedHashSet<>();
        boolean complete = true;
        for (String input : stringList(stage.get("inputs"))) {
            StageOutput output = stageOutputs.get(input);
            if (output != null) {
                fields.addAll(output.fields());
                sourceModels.addAll(output.sourceModels());
                sourceFields.addAll(output.sourceFields());
                complete = complete && output.complete();
            } else {
                complete = false;
            }
        }
        List<String> declaredOutput = stringList(stage.get("output"));
        if (declaredOutput.isEmpty()) {
            return StageOutput.incomplete(fields, sourceModels, sourceFields);
        }
        if (complete) {
            for (String field : declaredOutput) {
                if (!fields.contains(field)) {
                    throw RX.throwB(STAGE_INVALID
                            + ": join_align output references unavailable field '" + field + "'.");
                }
            }
            return StageOutput.complete(new LinkedHashSet<>(declaredOutput), sourceModels, sourceFields);
        }
        return StageOutput.incomplete(new LinkedHashSet<>(declaredOutput), sourceModels, sourceFields);
    }

    private static StageOutput mergedInputOutput(Map<String, Object> stage, Map<String, StageOutput> stageOutputs) {
        List<String> inputs = stringList(stage.get("inputs"));
        if (inputs.isEmpty()) {
            return StageOutput.incomplete(Set.of(), sourceModels(stage), sourceFields(stage));
        }
        Set<String> fields = new LinkedHashSet<>();
        Set<String> sourceModels = new LinkedHashSet<>();
        Set<String> sourceFields = new LinkedHashSet<>();
        boolean complete = true;
        for (String input : inputs) {
            StageOutput output = stageOutputs.get(input);
            if (output == null) {
                complete = false;
            } else {
                fields.addAll(output.fields());
                sourceModels.addAll(output.sourceModels());
                sourceFields.addAll(output.sourceFields());
                complete = complete && output.complete();
            }
        }
        return new StageOutput(fields, complete, sourceModels, sourceFields);
    }

    private static void validateStageContractConsistency(Map<String, Object> stage, String type,
                                                         Map<String, StageOutput> stageOutputs) {
        if (!"join_align".equals(type) || !signedJoinAlign(stage)) {
            return;
        }
        validateSignedJoinAlignTimeAttribution(stage, stageOutputs);
        validateTypedRelationContract(stage, stageOutputs);
        validateRuntimeGuardContract(stage, stageOutputs);
    }

    private static void validateSignedJoinAlignTimeAttribution(Map<String, Object> stage,
                                                               Map<String, StageOutput> stageOutputs) {
        Map<String, Object> timeAttribution = mapValue(stage.get("timeAttribution"));
        if (timeAttribution == null || isBlank(timeAttribution.get("sourceStage"))) {
            return;
        }
        String sourceStage = stringValue(timeAttribution.get("sourceStage"));
        if (!stringList(stage.get("inputs")).contains(sourceStage)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.timeAttribution.sourceStage must reference a join input stage.");
        }
        StageOutput source = stageOutputs.get(sourceStage);
        if (source == null) {
            throw RX.throwB(STAGE_REFERENCE_INVALID
                    + ": signed join_align.timeAttribution.sourceStage must reference a prior stage.");
        }
        String field = stringValue(timeAttribution.get("field"));
        if (!fieldAvailableInStageSource(source, field)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.timeAttribution.field references unavailable source field '"
                    + field + "' in stage '" + sourceStage + "'.");
        }
    }

    private static void validateTypedRelationContract(Map<String, Object> stage,
                                                      Map<String, StageOutput> stageOutputs) {
        Map<String, Object> relation = mapValue(stage.get("relation"));
        if (relation == null) {
            return;
        }
        RelationEndpoint left = relationEndpoint(relation, "left");
        RelationEndpoint right = relationEndpoint(relation, "right");
        List<String> inputs = stringList(stage.get("inputs"));
        if (!inputs.contains(left.stage()) || !inputs.contains(right.stage())) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.relation endpoints must reference join input stages.");
        }
        if (left.stage().equals(right.stage())) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.relation endpoints must reference two different stages.");
        }
        validateRelationEndpoint("left", left, stageOutputs);
        validateRelationEndpoint("right", right, stageOutputs);
        if (!alignmentKeyMatches(stringList(stage.get("keys")), left.field(), right.field())) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.relation fields must match an explicit alignment key '"
                    + left.field() + "=" + right.field() + "'.");
        }
        validateRelationRefMatchesTypedEndpoints(stage, left, right);
    }

    private static void validateRelationEndpoint(String side, RelationEndpoint endpoint,
                                                 Map<String, StageOutput> stageOutputs) {
        StageOutput output = stageOutputs.get(endpoint.stage());
        if (output == null) {
            throw RX.throwB(STAGE_REFERENCE_INVALID
                    + ": signed join_align.relation." + side + ".stage must reference a prior stage.");
        }
        if (!output.sourceModels().isEmpty() && !output.sourceModels().contains(endpoint.model())) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.relation." + side + ".model '"
                    + endpoint.model() + "' does not match stage '" + endpoint.stage() + "'.");
        }
        if (output.complete() && !output.fields().contains(endpoint.field())) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.relation." + side
                    + ".field references unavailable output field '" + endpoint.field()
                    + "' in stage '" + endpoint.stage() + "'.");
        }
    }

    private static void validateRelationRefMatchesTypedEndpoints(Map<String, Object> stage,
                                                                 RelationEndpoint left,
                                                                 RelationEndpoint right) {
        String relationRef = stringValue(stage.get("relationRef"));
        if (relationRef == null || relationRef.isBlank()) {
            return;
        }
        String forward = left.model() + "." + left.field() + " -> " + right.model() + "." + right.field();
        String reverse = right.model() + "." + right.field() + " -> " + left.model() + "." + left.field();
        if (!relationRef.equals(forward) && !relationRef.equals(reverse)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.relationRef must match typed relation endpoints.");
        }
    }

    private static void validateRuntimeGuardContract(Map<String, Object> stage,
                                                     Map<String, StageOutput> stageOutputs) {
        DslCteJoinAlignRuntimeGuardContract guard =
                DslCteJoinAlignRuntimeGuardContract.parseNullable(stage.get("runtimeGuard"));
        if (guard == null) {
            return;
        }
        if (guard.cardinality() != null) {
            validateRuntimeCardinalityGuard(stage, guard.cardinality());
        }
        if (guard.timeAttribution() != null) {
            validateRuntimeTimeAttributionGuard(stage, guard.timeAttribution(), stageOutputs);
        }
    }

    private static void validateRuntimeCardinalityGuard(Map<String, Object> stage,
                                                        DslCteJoinAlignRuntimeGuardContract.Cardinality cardinality) {
        String declared = stringValue(stage.get("cardinality"));
        boolean matches = switch (declared) {
            case "one_to_one" -> "one".equals(cardinality.leftMultiplicity())
                    && "one".equals(cardinality.rightMultiplicity());
            case "one_to_many" -> "one".equals(cardinality.leftMultiplicity())
                    && "many".equals(cardinality.rightMultiplicity());
            case "many_to_one" -> "many".equals(cardinality.leftMultiplicity())
                    && "one".equals(cardinality.rightMultiplicity());
            default -> false;
        };
        if (!matches) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.runtimeGuard.cardinality must match signed cardinality '"
                    + declared + "'.");
        }
    }

    private static void validateRuntimeTimeAttributionGuard(Map<String, Object> stage,
                                                            DslCteJoinAlignRuntimeGuardContract.TimeAttribution timeGuard,
                                                            Map<String, StageOutput> stageOutputs) {
        Map<String, Object> signedTimeAttribution = mapValue(stage.get("timeAttribution"));
        if (signedTimeAttribution != null) {
            String signedSourceStage = stringValue(signedTimeAttribution.get("sourceStage"));
            String signedField = stringValue(signedTimeAttribution.get("field"));
            if (signedSourceStage != null && !signedSourceStage.equals(timeGuard.sourceStage())) {
                throw RX.throwB(STAGE_INVALID
                        + ": signed join_align.runtimeGuard.timeAttribution.sourceStage must match timeAttribution.sourceStage.");
            }
            if (signedField != null && !signedField.equals(timeGuard.sourceField())) {
                throw RX.throwB(STAGE_INVALID
                        + ": signed join_align.runtimeGuard.timeAttribution.sourceField must match timeAttribution.field.");
            }
        }

        validateRuntimeTimeField(stage, stageOutputs, timeGuard.sourceStage(), timeGuard.sourceField(),
                "runtimeGuard.timeAttribution.sourceField");
        if (timeGuard.targetStage() != null || timeGuard.targetField() != null) {
            validateRuntimeTimeField(stage, stageOutputs, timeGuard.targetStage(), timeGuard.targetField(),
                    "runtimeGuard.timeAttribution.targetField");
        }
    }

    private static void validateRuntimeTimeField(Map<String, Object> stage,
                                                 Map<String, StageOutput> stageOutputs,
                                                 String stageName,
                                                 String field,
                                                 String usage) {
        if (!stringList(stage.get("inputs")).contains(stageName)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align." + usage + " stage must reference a join input stage.");
        }
        StageOutput output = stageOutputs.get(stageName);
        if (output == null) {
            throw RX.throwB(STAGE_REFERENCE_INVALID
                    + ": signed join_align." + usage + " stage must reference a prior stage.");
        }
        if (!fieldAvailableInStageSource(output, field)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align." + usage + " references unavailable source field '"
                    + field + "' in stage '" + stageName + "'.");
        }
    }

    private static void validatePlanOutput(List<String> outputs, List<Map<String, Object>> stages,
                                           Map<String, StageOutput> stageOutputs,
                                           Map<String, Object> ctePlan) {
        String finalStageName = stringValue(stages.get(stages.size() - 1).get("name"));
        StageOutput finalOutput = finalStageName == null ? null : stageOutputs.get(finalStageName);
        if (finalOutput == null || !finalOutput.complete()) {
            return;
        }
        Set<String> planLevelOutputs = planLevelDerivedOutputFields(ctePlan);
        for (String output : outputs) {
            if (!finalOutput.fields().contains(output) && !planLevelOutputs.contains(output)) {
                throw RX.throwB(STAGE_INVALID + ": cte_plan.output references unavailable field '" + output + "'.");
            }
        }
    }

    private static Set<String> planLevelDerivedOutputFields(Map<String, Object> ctePlan) {
        Set<String> fields = new LinkedHashSet<>();
        Map<String, Object> moneyDerivedMetric = mapValue(ctePlan.get("moneyDerivedMetricContract"));
        if (moneyDerivedMetric == null) {
            return fields;
        }
        String kind = stringValue(moneyDerivedMetric.get("kind"));
        if (!"source_cohort_target_year_month_amount_share".equals(kind)
                && !"source_cohort_target_year_month_amount_per_lead".equals(kind)) {
            return fields;
        }
        String denominatorMetric = stringValue(moneyDerivedMetric.get("denominatorMetric"));
        if (safeIdentifier(denominatorMetric)) {
            fields.add(denominatorMetric);
        }
        String metric = stringValue(moneyDerivedMetric.get("metric"));
        if (metric == null) {
            metric = stringValue(moneyDerivedMetric.get("ratioAlias"));
        }
        if (metric == null) {
            metric = stringValue(moneyDerivedMetric.get("name"));
        }
        if (safeIdentifier(metric)) {
            fields.add(metric);
        }
        return fields;
    }

    private static boolean safeIdentifier(String value) {
        return value != null && IDENTIFIER_PATTERN.matcher(value).matches();
    }

    private static void validateAvailableField(StageOutput output, Object rawField, String usage) {
        validateAvailableField(output, rawField, usage, POST_SLICE_INVALID);
    }

    private static void validateAvailableField(StageOutput output, Object rawField, String usage, String errorCode) {
        String field = stringValue(rawField);
        if (field != null && !field.isBlank() && !output.fields().contains(field)) {
            throw RX.throwB(errorCode + ": " + usage + " references unavailable field '" + field + "'.");
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
        } else if ("orderBy".equals(type)) {
            evidence.put("orderBy_contract", orderByContractEvidence(stage));
        } else if ("aggregate".equals(type)) {
            Map<String, Object> aggregateContract = aggregateContractEvidence(stage);
            if (!aggregateContract.isEmpty()) {
                evidence.put("aggregate_contract", aggregateContract);
            }
        } else if ("join_align".equals(type)) {
            evidence.put("join_align_contract", joinAlignContractEvidence(stage));
        }
        return evidence;
    }

    private static Map<String, Object> orderByContractEvidence(Map<String, Object> stage) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("kind", "result_stage_ordering");
        evidence.put("bridge_scope", "preflight_only");
        evidence.put("bridge_signed", false);
        evidence.put("orderBy", mapList(stage.get("orderBy")));
        Integer limit = intValue(stage.get("limit"));
        if (limit != null) {
            evidence.put("limit", limit);
        }
        evidence.put("required_capabilities", List.of(
                "post_result_ordering",
                "bounded_result_limit",
                "order_field_output_schema_validation"));
        return evidence;
    }

    private static Map<String, Object> joinAlignContractEvidence(Map<String, Object> stage) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        boolean signed = signedJoinAlign(stage);
        evidence.put("kind", "cross_stage_alignment");
        evidence.put("bridge_scope", signed ? "signed_relation_contract" : "stage_contract_only");
        evidence.put("bridge_signed", signed);
        evidence.put("inputs", stringList(stage.get("inputs")));
        evidence.put("keys", stringList(stage.get("keys")));
        evidence.put("joinType", stringValue(stage.get("joinType")));
        if (signed) {
            evidence.put("relationRef", stringValue(stage.get("relationRef")));
            evidence.put("cardinality", stringValue(stage.get("cardinality")));
            evidence.put("timeAttribution", mapValue(stage.get("timeAttribution")));
            Map<String, Object> relation = mapValue(stage.get("relation"));
            if (relation != null) {
                evidence.put("typed_relation", relation);
            }
            DslCteJoinAlignRuntimeGuardContract runtimeGuard =
                    DslCteJoinAlignRuntimeGuardContract.parseNullable(stage.get("runtimeGuard"));
            if (runtimeGuard != null) {
                evidence.put("runtime_guard", runtimeGuard.toEvidenceMap());
                evidence.put("runtime_guard_signed", true);
                evidence.put("runtime_guard_contract", "typed_fail_closed");
                evidence.put("runtime_guard_normalized", true);
            }
            evidence.put("output_schema", stringList(stage.get("output")));
        }
        evidence.put("required_capabilities", List.of(
                "declared_alignment_key_mapping",
                "join_cardinality_guard",
                "fail_closed_runtime_guard_declaration",
                "cross_model_governance_replay",
                "complete_output_schema_derivation"));
        return evidence;
    }

    private static void validateSignedJoinAlignShape(Map<String, Object> stage) {
        if (!signedJoinAlignCandidate(stage)) {
            return;
        }
        if (isBlank(stage.get("relationRef"))) {
            throw RX.throwB(STAGE_INVALID + ": signed join_align must declare relationRef.");
        }
        String cardinality = stringValue(stage.get("cardinality"));
        if (cardinality == null || !SIGNED_JOIN_ALIGN_CARDINALITIES.contains(cardinality)) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.cardinality must be one_to_one, one_to_many, or many_to_one.");
        }
        Map<String, Object> timeAttribution = mapValue(stage.get("timeAttribution"));
        if (timeAttribution == null
                || isBlank(timeAttribution.get("basis"))
                || isBlank(timeAttribution.get("field"))) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.timeAttribution must declare basis and field.");
        }
        if (stringList(stage.get("output")).isEmpty()) {
            throw RX.throwB(STAGE_INVALID + ": signed join_align must declare output schema.");
        }
    }

    private static boolean signedJoinAlign(Map<String, Object> stage) {
        return signedJoinAlignCandidate(stage)
                && !isBlank(stage.get("relationRef"))
                && !isBlank(stage.get("cardinality"))
                && mapValue(stage.get("timeAttribution")) != null
                && !stringList(stage.get("output")).isEmpty();
    }

    private static boolean signedJoinAlignCandidate(Map<String, Object> stage) {
        return stage.containsKey("relationRef")
                || stage.containsKey("cardinality")
                || stage.containsKey("timeAttribution")
                || stage.containsKey("relation")
                || stage.containsKey("runtimeGuard")
                || stage.containsKey("output");
    }

    private static RelationEndpoint relationEndpoint(Map<String, Object> relation, String side) {
        Map<String, Object> endpoint = mapValue(relation.get(side));
        if (endpoint == null) {
            throw RX.throwB(STAGE_INVALID + ": signed join_align.relation must declare " + side + " endpoint.");
        }
        String stage = stringValue(endpoint.get("stage"));
        String model = stringValue(endpoint.get("model"));
        String field = stringValue(endpoint.get("field"));
        if (stage == null || stage.isBlank()
                || model == null || model.isBlank()
                || field == null || field.isBlank()) {
            throw RX.throwB(STAGE_INVALID
                    + ": signed join_align.relation." + side + " must declare stage, model, and field.");
        }
        return new RelationEndpoint(stage, model, field);
    }

    private static boolean alignmentKeyMatches(List<String> keys, String leftField, String rightField) {
        for (String key : keys) {
            String[] parts = key.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            String left = parts[0].trim();
            String right = parts[1].trim();
            if ((leftField.equals(left) && rightField.equals(right))
                    || (leftField.equals(right) && rightField.equals(left))) {
                return true;
            }
        }
        return false;
    }

    private static boolean fieldAvailableInStageSource(StageOutput source, String field) {
        return field != null
                && !field.isBlank()
                && (source.sourceFields().contains(field) || source.fields().contains(field));
    }

    private static Map<String, Object> deriveContractEvidence(Map<String, Object> stage) {
        List<Map<String, Object>> derived = mapList(stage.get("derived"));
        Map<String, Object> evidence = new LinkedHashMap<>();
        boolean preAggregate = mapValue(stage.get("input")) != null;
        boolean hasDuration = false;
        boolean hasBooleanPredicate = false;
        boolean hasMetricRatio = false;
        boolean hasPriorityThreshold = false;
        List<Map<String, Object>> orderedBucketContracts = new ArrayList<>();
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
            Map<String, Object> orderedBucketContract = orderedBucketContractEvidence(item, expr);
            if (orderedBucketContract != null) {
                orderedBucketContracts.add(orderedBucketContract);
            }
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
        if (!preAggregate && !orderedBucketContracts.isEmpty()) {
            evidence.put("kind", "relation_metric_ordered_bucket");
            evidence.put("bridge_scope", "result_stage_metric_ratio");
            evidence.put("bridge_signed", true);
            evidence.put("bucket_contracts", orderedBucketContracts);
            evidence.put("required_capabilities", List.of(
                    "ordered_numeric_bucket_case",
                    "single_visible_numeric_source_alias",
                    "short_literal_bucket_labels",
                    "equality_only_label_postSlice",
                    "output_schema_availability_guard"));
            return evidence;
        }
        return evidence;
    }

    private static Map<String, Object> orderedBucketContractEvidence(Map<String, Object> item, String expr) {
        Matcher matcher = ORDERED_BUCKET_PATTERN.matcher(expr == null ? "" : expr);
        if (!matcher.matches()) {
            return null;
        }
        String body = matcher.group(1);
        String elseLabel = unescapeSqlStringLiteral(matcher.group(2));
        if (!safeCaseLabelLiteral(elseLabel)) {
            return null;
        }
        Matcher whenMatcher = ORDERED_BUCKET_WHEN_PATTERN.matcher(body);
        List<Map<String, Object>> conditions = new ArrayList<>();
        String sourceAlias = null;
        int end = 0;
        while (whenMatcher.find()) {
            if (whenMatcher.start() != end) {
                return null;
            }
            String field = whenMatcher.group(1);
            if (sourceAlias == null) {
                sourceAlias = field;
            } else if (!sourceAlias.equals(field)) {
                return null;
            }
            String label = unescapeSqlStringLiteral(whenMatcher.group(4));
            if (!safeCaseLabelLiteral(label)) {
                return null;
            }
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("op", sqlOperator(whenMatcher.group(2)));
            condition.put("threshold", whenMatcher.group(3));
            condition.put("label", label);
            conditions.add(condition);
            end = whenMatcher.end();
        }
        if (end != body.length() || conditions.size() < 2 || sourceAlias == null) {
            return null;
        }
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("alias", stringValue(item.get("name")));
        contract.put("source_alias", sourceAlias);
        contract.put("conditions", conditions);
        contract.put("else_label", elseLabel);
        contract.put("source_policy", "single_visible_numeric_alias");
        contract.put("label_policy", "short_single_line_literal");
        contract.put("postSlice_policy", "label_alias_equality_only");
        contract.put("postSlice_allowed_ops", List.of("=", "!=", "<>"));
        contract.put("unsupported_bucket_shapes", List.of(
                "multi_field_case",
                "nested_case",
                "non_numeric_threshold",
                "unsafe_label_literal"));
        return contract;
    }

    private static Map<String, Object> aggregateContractEvidence(Map<String, Object> stage) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        boolean hasConditionalMetric = false;
        boolean hasConditionalValueSumMetric = false;
        boolean allConditionalMetricsSigned = true;
        Object rawMetrics = stage.get("metrics");
        if (rawMetrics instanceof List<?> metrics) {
            for (Object item : metrics) {
                Map<String, Object> metric = mapValue(item);
                if (metric == null) {
                    continue;
                }
                String expr = stringValue(metric.get("expr"));
                if (expr != null && expr.toLowerCase(Locale.ROOT).contains("case when")) {
                    hasConditionalMetric = true;
                    boolean signedCount = SIGNED_CONDITIONAL_COUNT_PATTERN.matcher(expr).matches();
                    boolean signedValueSum = SIGNED_CONDITIONAL_VALUE_SUM_PATTERN.matcher(expr).matches();
                    hasConditionalValueSumMetric = hasConditionalValueSumMetric || signedValueSum;
                    allConditionalMetricsSigned = allConditionalMetricsSigned
                            && (signedCount || signedValueSum);
                }
            }
        }
        if (hasConditionalMetric) {
            evidence.put("kind", hasConditionalValueSumMetric
                    ? "conditional_value_aggregation"
                    : "conditional_numerator_aggregation");
            evidence.put("bridge_scope", "aggregate_metrics");
            evidence.put("bridge_signed", allConditionalMetricsSigned);
            evidence.put("required_capabilities", allConditionalMetricsSigned
                    ? conditionalAggregateCapabilities(hasConditionalValueSumMetric)
                    : List.of(
                            "case_when_over_row_level_derived",
                            "boolean_metric_sum",
                            "governed_denominator_alignment"));
        }
        return evidence;
    }

    private static List<String> conditionalAggregateCapabilities(boolean hasConditionalValueSumMetric) {
        if (hasConditionalValueSumMetric) {
            return List.of(
                    "conditional_value_sum_normalization",
                    "governed_field_predicate",
                    "governed_value_field",
                    "semantic_unit_guard");
        }
        return List.of("conditional_count_normalization", "governed_field_predicate");
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
            boolean signed = containsExactRank(derived)
                    && containsCumulativeContributionRatio(derived)
                    && unsupportedRankingFunction(derived) == null;
            evidence.put("kind", "cumulative_contribution");
            evidence.put("bridge_scope", signed ? "result_stage_window" : "stage_contract_only");
            evidence.put("bridge_signed", signed);
            evidence.put("required_capabilities", List.of(
                    "rank_over_aggregate_metric_order",
                    "running_total_ratio",
                    "deterministic_result_ordering",
                    "postSlice_on_window_alias"));
            evidence.put("ranking_contract", rankingContractEvidence(stage, derived));
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

    private static boolean containsExactRank(List<Map<String, Object>> derived) {
        for (Map<String, Object> item : derived) {
            String expr = stringValue(item.get("expr"));
            String normalized = expr == null ? "" : expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if ("rank()".equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCumulativeContributionRatio(List<Map<String, Object>> derived) {
        for (Map<String, Object> item : derived) {
            String expr = stringValue(item.get("expr"));
            String normalized = expr == null ? "" : expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if (normalized.contains("sum(") && normalized.contains("overorder")
                    && normalized.contains("overall")) {
                return true;
            }
        }
        return false;
    }

    private static String unsupportedRankingFunction(List<Map<String, Object>> derived) {
        for (Map<String, Object> item : derived) {
            String expr = stringValue(item.get("expr"));
            String normalized = expr == null ? "" : expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            for (String function : List.of("dense_rank", "row_number", "percent_rank", "cume_dist", "ntile")) {
                if (normalized.startsWith(function + "(")) {
                    return function;
                }
            }
        }
        return null;
    }

    private static Map<String, Object> rankingContractEvidence(Map<String, Object> stage,
                                                               List<Map<String, Object>> derived) {
        Map<String, Object> contract = new LinkedHashMap<>();
        Map<String, Object> window = mapValue(stage.get("window"));
        contract.put("rank_function", "rank");
        contract.put("allowed_rank_functions", List.of("rank"));
        contract.put("partitionBy", window == null ? List.of() : stringList(window.get("partitionBy")));
        contract.put("orderBy", window == null ? List.of() : mapList(window.get("orderBy")));
        contract.put("explicit_frame_allowed", false);
        contract.put("running_total_frame", "rows_unbounded_preceding_to_current_row");
        contract.put("postSlice_policy", "cumulative_alias_only_lt_lte");
        contract.put("unsupported_rank_functions", List.of(
                "dense_rank", "row_number", "percent_rank", "cume_dist", "ntile"));
        String rejected = unsupportedRankingFunction(derived);
        if (rejected != null) {
            contract.put("rejected_rank_function", rejected);
        }
        return contract;
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

    private static Set<String> sourceModels(Map<String, Object> stage) {
        Map<String, Object> input = mapValue(stage.get("input"));
        if (input == null) {
            return Set.of();
        }
        String model = stringValue(input.get("model"));
        if (model == null || model.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(List.of(model));
    }

    private static Set<String> sourceFields(Map<String, Object> stage) {
        Set<String> fields = new LinkedHashSet<>(stringList(stage.get("groupBy")));
        collectFilterFields(stage.get("filters"), fields);
        collectMetricSourceFields(stage.get("metrics"), fields);
        for (Map<String, Object> derived : mapList(stage.get("derived"))) {
            collectExpressionFields(stringValue(derived.get("expr")), fields);
        }
        return fields;
    }

    private static void collectMetricSourceFields(Object rawMetrics, Set<String> fields) {
        if (!(rawMetrics instanceof List<?> metrics)) {
            return;
        }
        for (Object item : metrics) {
            Map<String, Object> metric = mapValue(item);
            if (metric != null) {
                collectExpressionFields(stringValue(metric.get("expr")), fields);
                continue;
            }
            String metricExpr = stringValue(item);
            if (metricExpr != null) {
                collectExpressionFields(metricExpr, fields);
            }
        }
    }

    private static void collectFilterFields(Object rawFilters, Set<String> fields) {
        for (Map<String, Object> filter : mapList(rawFilters)) {
            addFieldName(fields, filter.get("field"));
            addFieldName(fields, filter.get("valueField"));
        }
    }

    private static void collectExpressionFields(String expr, Set<String> fields) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        String withoutQuotedStrings = expr.replaceAll("'[^']*'", " ");
        Matcher matcher = IDENTIFIER_PATTERN.matcher(withoutQuotedStrings);
        while (matcher.find()) {
            String token = matcher.group();
            if (!EXPRESSION_KEYWORDS.contains(token.toLowerCase(Locale.ROOT))) {
                fields.add(token);
            }
        }
    }

    private static void addFieldName(Set<String> fields, Object raw) {
        String field = stringValue(raw);
        if (field != null && !field.isBlank()) {
            fields.add(field);
        }
    }

    private static void validateLimit(Map<String, Object> stage) {
        if (!stage.containsKey("limit") || stage.get("limit") == null) {
            return;
        }
        Integer limit = intValue(stage.get("limit"));
        if (limit == null || limit <= 0 || limit > 1000) {
            throw RX.throwB(STAGE_INVALID + ": orderBy limit must be a positive integer <= 1000.");
        }
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

    private static String sqlOperator(String op) {
        if (op == null) {
            return null;
        }
        return switch (op.trim()) {
            case "==" -> "=";
            case "=", "!=", "<>", "<", "<=", ">", ">=" -> op.trim();
            default -> null;
        };
    }

    private static String unescapeSqlStringLiteral(String value) {
        return value == null ? null : value.replace("''", "'");
    }

    private static boolean safeCaseLabelLiteral(String value) {
        return value != null
                && value.length() <= 64
                && !value.contains("\n")
                && !value.contains("\r")
                && !value.contains("\u0000");
    }

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = stringValue(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isBlank(Object value) {
        String text = stringValue(value);
        return text == null || text.isBlank();
    }

    private static String metricAlias(String metric) {
        if (metric == null) {
            return null;
        }
        String[] parts = metric.split("(?i)\\s+as\\s+");
        return parts.length == 2 ? parts[1].trim() : null;
    }

    private record StageOutput(Set<String> fields, boolean complete,
                               Set<String> sourceModels, Set<String> sourceFields) {
        private static StageOutput complete(Set<String> fields, Set<String> sourceModels, Set<String> sourceFields) {
            return new StageOutput(new LinkedHashSet<>(fields), true,
                    new LinkedHashSet<>(sourceModels), new LinkedHashSet<>(sourceFields));
        }

        private static StageOutput incomplete(Set<String> fields, Set<String> sourceModels, Set<String> sourceFields) {
            return new StageOutput(new LinkedHashSet<>(fields), false,
                    new LinkedHashSet<>(sourceModels), new LinkedHashSet<>(sourceFields));
        }
    }

    private record RelationEndpoint(String stage, String model, String field) {
    }
}

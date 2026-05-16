package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a narrow DSL_CTE stage contract into the existing DSL request shape.
 *
 * <p>This bridge is intentionally smaller than {@link DslCtePlanValidator}.
 * It only maps signed stage templates to existing DSL execution paths without
 * opening arbitrary CTE compilation.</p>
 */
public final class DslCteDslRequestMapper {

    public static final String STATUS_READY = "BRIDGE_READY";
    public static final String STATUS_DEFERRED = "BRIDGE_DEFERRED";
    private static final Pattern LAG_PATTERN = Pattern.compile(
            "(?i)^\\s*lag\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*(?:,\\s*1\\s*)?\\)\\s*$");
    private static final Pattern ROLLING_SUM_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s+over\\s+last\\s+(\\d+)\\s+rows\\s*$");
    private static final Pattern CUMULATIVE_SHARE_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s+over\\s+order\\s*/\\s*sum\\s*\\(\\s*\\1\\s*\\)\\s+over\\s+all\\s*$");
    private static final Pattern SAFE_ALIAS_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_$.]*");
    private static final Pattern HOURS_BETWEEN_PATTERN = Pattern.compile(
            "(?i)^\\s*hours_between\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*,\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s*$");
    private static final Pattern SLA_HIT_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+not\\s+null\\s+and\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*<=\\s*(\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SLA_HIT_THRESHOLD_ALIAS_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+not\\s+null\\s+and\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*<=\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*$");
    private static final Pattern SLA_OVERDUE_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+null\\s+or\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*>\\s*(\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SLA_OVERDUE_THRESHOLD_ALIAS_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+null\\s+or\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*>\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*$");
    private static final Pattern COMBINED_SLA_HIT_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*(?:=|==)\\s*1\\s+and\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s*(?:=|==)\\s*1\\s*$");
    private static final Pattern NOT_NULL_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+not\\s+null\\s*$");
    private static final Pattern PRIORITY_THRESHOLD_PATTERN = Pattern.compile(
            "(?i)^\\s*priority_threshold\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*,\\s*(.+)\\s*\\)\\s*$");
    private static final Pattern SUM_ALIAS_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)\\s*$");
    private static final Pattern CASE_WHEN_ALIAS_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*case\\s+when\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s+then\\s+1\\s+else\\s+0\\s+end\\s*\\)\\s*$");
    private static final Pattern METRIC_RATIO_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*/\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*$");
    private static final List<String> SIGNED_PRIORITY_CODES = List.of("P1", "P2", "P3");

    private DslCteDslRequestMapper() {
    }

    public static BridgeResult toDslRequest(String fallbackModel, Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return BridgeResult.deferred(unsupported);
        }
        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        if (stages.isEmpty()) {
            unsupported.add("cte_plan.stages must be non-empty");
            return BridgeResult.deferred(unsupported);
        }

        Map<String, Object> aggregate = stages.get(0);
        if (!"aggregate".equals(stringValue(aggregate.get("type")))) {
            String firstType = stringValue(aggregate.get("type"));
            String firstName = stringValue(aggregate.get("name"));
            if ("derive".equals(firstType) && mapValue(aggregate.get("input")) != null) {
                BridgeResult rowLevelBridge = rowLevelDerivedAggregateBridge(fallbackModel, ctePlan, stages);
                if (rowLevelBridge.ready()) {
                    return rowLevelBridge;
                }
                unsupported.addAll(rowLevelBridge.unsupported());
            }
            unsupported.add("DSL_CTE bridge v1 requires the first stage to be aggregate; pre-aggregate "
                    + firstType + " stage is not executable in this cut: " + firstName);
            if ("derive".equals(firstType) && mapValue(aggregate.get("input")) != null) {
                addPreAggregateDeriveDiagnostics(stages, aggregate, unsupported);
            }
            return BridgeResult.deferred(unsupported);
        }

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL");
        request.setStatus("PLAN_READY");
        request.setGroupBy(groupByItems(aggregate.get("groupBy")));
        request.setSlice(sliceItems(aggregate.get("filters"), unsupported));

        MetricMapping metrics = metrics(aggregate.get("metrics"), unsupported);
        List<PostAggregateCalculationDef> postAgg = new ArrayList<>();
        List<SemanticQueryRequest.SliceItem> postSlice = null;
        Map<String, String> outputAliasOverride = new LinkedHashMap<>();
        boolean hasWindowBridge = false;

        for (int i = 1; i < stages.size(); i++) {
            Map<String, Object> stage = stages.get(i);
            String type = stringValue(stage.get("type"));
            if ("derive".equals(type)) {
                postAgg.addAll(postAggregateCalculations(stage.get("derived"), metrics.aliases(), unsupported));
            } else if ("postSlice".equals(type)) {
                postSlice = sliceItems(stage.get("filters"), unsupported);
            } else if ("window_derive".equals(type)) {
                WindowBridge windowBridge = rollingWindowBridge(stage, aggregate, metrics.aliases(), unsupported);
                if (windowBridge != null) {
                    request.setTimeWindow(windowBridge.timeWindow());
                    outputAliasOverride.putAll(windowBridge.outputAliasOverride());
                    hasWindowBridge = true;
                }
            } else {
                unsupported.add("DSL_CTE bridge v1 does not execute stage type: " + type);
            }
        }
        if (hasWindowBridge && postSlice != null) {
            unsupported.add("DSL_CTE rolling window bridge does not support postSlice in this cut");
        }
        if (hasWindowBridge && !postAgg.isEmpty()) {
            unsupported.add("DSL_CTE rolling window bridge does not support postAggregate calculations in this cut");
        }

        request.setColumns(outputColumns(ctePlan.get("output"), aggregate.get("groupBy"), metrics, postAgg,
                outputAliasOverride, unsupported));
        request.setPostAggregateCalculations(postAgg.isEmpty() ? null : postAgg);
        request.setPostSlice(postSlice);
        request.setReturnTotal(false);

        String model = sourceModel(fallbackModel, aggregate);
        if (model == null || model.isBlank()) {
            unsupported.add("aggregate input model must be declared for DSL_CTE bridge v1");
        }
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            unsupported.add("DSL_CTE bridge request must have output columns");
        }
        if (!unsupported.isEmpty()) {
            return BridgeResult.deferred(unsupported);
        }
        return BridgeResult.ready(model, request);
    }

    public static ResultStageWindowBridgeResult toResultStageWindowBridge(String fallbackModel, Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }
        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        if (stages.size() < 2 || stages.size() > 3) {
            unsupported.add("result-stage window bridge requires aggregate -> window_derive -> optional postSlice");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }

        Map<String, Object> aggregate = stages.get(0);
        Map<String, Object> windowStage = stages.get(1);
        if (!"aggregate".equals(stringValue(aggregate.get("type")))
                || !"window_derive".equals(stringValue(windowStage.get("type")))) {
            unsupported.add("result-stage window bridge requires aggregate followed by window_derive");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }
        if (stages.size() == 3 && !"postSlice".equals(stringValue(stages.get(2).get("type")))) {
            unsupported.add("result-stage window bridge supports only postSlice after window_derive");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }

        List<String> inputs = stringList(windowStage.get("inputs"));
        String aggregateName = stringValue(aggregate.get("name"));
        if (inputs.size() != 1 || aggregateName == null || !aggregateName.equals(inputs.get(0))) {
            unsupported.add("result-stage window_derive must reference the first aggregate stage");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }

        MetricMapping metrics = metrics(aggregate.get("metrics"), unsupported);
        List<String> metricAliases = metrics.aliases();
        if (metricAliases.size() != 1) {
            unsupported.add("result-stage cumulative contribution bridge requires exactly one aggregate metric");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }
        String metricAlias = metricAliases.get(0);
        List<String> groupBy = stringList(aggregate.get("groupBy"));
        if (groupBy.isEmpty()) {
            unsupported.add("result-stage cumulative contribution bridge requires at least one grouping field for deterministic tie-breaking");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }
        if (!allSafeAliases(groupBy, metricAliases)) {
            unsupported.add("result-stage window bridge supports only governed field aliases");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }

        Map<String, Object> window = mapValue(windowStage.get("window"));
        if (window == null || !stringList(window.get("partitionBy")).isEmpty()) {
            unsupported.add("result-stage cumulative contribution bridge does not support partitionBy in this cut");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }
        if (mapValue(window.get("frame")) != null) {
            unsupported.add("result-stage cumulative contribution bridge does not support explicit frames");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }
        List<Map<String, Object>> orderBy = mapList(window.get("orderBy"));
        if (orderBy.size() != 1) {
            unsupported.add("result-stage cumulative contribution bridge requires exactly one orderBy metric");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }
        String orderField = stringValue(orderBy.get(0).get("field"));
        String orderDir = stringValue(orderBy.get(0).get("dir"));
        if (!metricAlias.equals(orderField) || !"DESC".equalsIgnoreCase(orderDir)) {
            unsupported.add("result-stage cumulative contribution bridge requires DESC orderBy on the aggregate metric");
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }

        CumulativeDerived cumulative = cumulativeDerived(windowStage.get("derived"), metricAlias, unsupported);
        if (cumulative == null) {
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }

        List<ResultStageFilter> filters = resultStageFilters(
                stages.size() == 3 ? stages.get(2).get("filters") : null,
                cumulative.cumulativeAlias(), unsupported);
        if (!unsupported.isEmpty()) {
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }

        SemanticQueryRequest baseRequest = new SemanticQueryRequest();
        baseRequest.setRoute("DSL");
        baseRequest.setStatus("PLAN_READY");
        baseRequest.setGroupBy(groupByItems(aggregate.get("groupBy")));
        baseRequest.setSlice(sliceItems(aggregate.get("filters"), unsupported));
        baseRequest.setColumns(outputColumns(null, aggregate.get("groupBy"), metrics, List.of(), Map.of(), unsupported));
        baseRequest.setReturnTotal(false);

        String model = sourceModel(fallbackModel, aggregate);
        if (model == null || model.isBlank()) {
            unsupported.add("aggregate input model must be declared for result-stage window bridge");
        }
        List<String> output = stringList(ctePlan.get("output"));
        if (output.isEmpty()) {
            output.addAll(groupBy);
            output.add(metricAlias);
            output.add(cumulative.rankAlias());
            output.add(cumulative.cumulativeAlias());
        }
        if (!allSafeAliases(output, List.of(cumulative.rankAlias(), cumulative.cumulativeAlias()))) {
            unsupported.add("result-stage window output supports only governed field aliases");
        }
        if (!unsupported.isEmpty()) {
            return ResultStageWindowBridgeResult.deferred(unsupported);
        }

        ResultStageWindowPlan plan = new ResultStageWindowPlan(
                output, groupBy, metricAlias, cumulative.rankAlias(), cumulative.cumulativeAlias(), filters);
        return ResultStageWindowBridgeResult.ready(model, baseRequest, plan);
    }

    public static ResultStageMetricRatioBridgeResult toResultStageMetricRatioBridge(String fallbackModel,
                                                                                   Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }
        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        if (stages.size() < 3 || stages.size() > 4) {
            unsupported.add("result-stage SLA metric ratio bridge requires derive(input) -> aggregate -> derive -> optional postSlice");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        Map<String, Object> derive = stages.get(0);
        Map<String, Object> aggregate = stages.get(1);
        Map<String, Object> ratioStage = stages.get(2);
        if (!"derive".equals(stringValue(derive.get("type")))
                || mapValue(derive.get("input")) == null
                || !"aggregate".equals(stringValue(aggregate.get("type")))
                || !"derive".equals(stringValue(ratioStage.get("type")))) {
            unsupported.add("result-stage SLA metric ratio bridge requires derive(input) -> aggregate -> derive");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }
        if (stages.size() == 4 && !"postSlice".equals(stringValue(stages.get(3).get("type")))) {
            unsupported.add("result-stage SLA metric ratio bridge supports only postSlice after the ratio derive");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        String deriveName = stringValue(derive.get("name"));
        String aggregateName = stringValue(aggregate.get("name"));
        String ratioName = stringValue(ratioStage.get("name"));
        if (deriveName == null || aggregateName == null || ratioName == null) {
            unsupported.add("result-stage SLA metric ratio bridge requires named stages");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }
        if (!List.of(deriveName).equals(stringList(aggregate.get("inputs")))
                || !List.of(aggregateName).equals(stringList(ratioStage.get("inputs")))) {
            unsupported.add("result-stage SLA metric ratio bridge requires linear stage inputs");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }
        if (stages.size() == 4 && !List.of(ratioName).equals(stringList(stages.get(3).get("inputs")))) {
            unsupported.add("result-stage SLA metric ratio postSlice must reference the ratio derive stage");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        String model = sourceModel(fallbackModel, derive);
        List<CalculatedFieldDef> calculatedFields = rowLevelSlaCalculatedFields(derive.get("derived"), model, unsupported);
        MetricMapping metrics = rowLevelAggregateMetrics(aggregate.get("metrics"), model, calculatedFields, unsupported);
        List<MetricRatioDerived> ratios = slaMetricRatioDerived(ratioStage.get("derived"), model,
                metrics.aliases(), unsupported);
        if (ratios.isEmpty()) {
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }
        List<String> ratioAliases = ratios.stream().map(MetricRatioDerived::ratioAlias).toList();
        List<ResultStageFilter> filters = resultStageAliasFilters(
                stages.size() == 4 ? stages.get(3).get("filters") : null,
                ratioAliases, "result-stage SLA metric ratio bridge", unsupported);
        if (!unsupported.isEmpty()) {
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        SemanticQueryRequest baseRequest = new SemanticQueryRequest();
        baseRequest.setRoute("DSL");
        baseRequest.setStatus("PLAN_READY");
        baseRequest.setCalculatedFields(calculatedFields.isEmpty() ? null : calculatedFields);
        baseRequest.setGroupBy(groupByItems(aggregate.get("groupBy")));
        baseRequest.setSlice(sliceItems(derive.get("filters"), unsupported));
        baseRequest.setColumns(outputColumns(null, aggregate.get("groupBy"), metrics, List.of(), Map.of(), unsupported));
        baseRequest.setReturnTotal(false);

        if (model == null || model.isBlank()) {
            unsupported.add("derive input model must be declared for result-stage SLA metric ratio bridge");
        }
        List<String> groupBy = stringList(aggregate.get("groupBy"));
        List<String> output = stringList(ctePlan.get("output"));
        if (output.isEmpty()) {
            output.addAll(groupBy);
            output.addAll(metrics.aliases());
            output.addAll(ratioAliases);
        }
        if (!allSafeAliases(output, groupBy, metrics.aliases(), ratioAliases)) {
            unsupported.add("result-stage SLA metric ratio output supports only governed field aliases");
        }
        List<String> availableOutput = new ArrayList<>();
        availableOutput.addAll(groupBy);
        availableOutput.addAll(metrics.aliases());
        availableOutput.addAll(ratioAliases);
        for (String field : output) {
            if (!availableOutput.contains(field)) {
                unsupported.add("result-stage SLA metric ratio output references unavailable field: " + field);
            }
        }
        if (!unsupported.isEmpty()) {
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        ResultStageMetricRatioPlan plan = new ResultStageMetricRatioPlan(
                output, groupBy, metrics.aliases(), ratios, filters);
        return ResultStageMetricRatioBridgeResult.ready(model, baseRequest, plan);
    }

    private static Map<String, Object> ctePlan(Object executablePlan, List<String> unsupported) {
        Map<String, Object> root = mapValue(executablePlan);
        if (root == null || root.isEmpty()) {
            unsupported.add("executable_plan must be an object");
            return null;
        }
        Map<String, Object> nested = mapValue(root.get("cte_plan"));
        if (nested != null) {
            return nested;
        }
        if (root.containsKey("stages")) {
            return root;
        }
        unsupported.add("executable_plan.cte_plan must be provided");
        return null;
    }

    private static String sourceModel(String fallbackModel, Map<String, Object> aggregate) {
        Map<String, Object> input = mapValue(aggregate.get("input"));
        String model = input == null ? null : stringValue(input.get("model"));
        return model == null || model.isBlank() ? fallbackModel : model;
    }

    private static BridgeResult rowLevelDerivedAggregateBridge(String fallbackModel, Map<String, Object> ctePlan,
                                                               List<Map<String, Object>> stages) {
        List<String> unsupported = new ArrayList<>();
        if (stages.size() != 2) {
            unsupported.add("row-level SLA bridge requires exactly derive(input) -> aggregate in this cut");
            return BridgeResult.deferred(unsupported);
        }

        Map<String, Object> derive = stages.get(0);
        Map<String, Object> aggregate = stages.get(1);
        if (!"aggregate".equals(stringValue(aggregate.get("type")))) {
            unsupported.add("row-level SLA bridge requires aggregate after pre-aggregate derive");
            return BridgeResult.deferred(unsupported);
        }
        String deriveName = stringValue(derive.get("name"));
        List<String> aggregateInputs = stringList(aggregate.get("inputs"));
        if (deriveName == null || aggregateInputs.size() != 1 || !deriveName.equals(aggregateInputs.get(0))) {
            unsupported.add("row-level SLA aggregate must reference the pre-aggregate derive stage");
            return BridgeResult.deferred(unsupported);
        }

        String model = sourceModel(fallbackModel, derive);
        List<CalculatedFieldDef> calculatedFields = rowLevelSlaCalculatedFields(derive.get("derived"), model, unsupported);
        MetricMapping metrics = rowLevelAggregateMetrics(aggregate.get("metrics"), model, calculatedFields, unsupported);

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL");
        request.setStatus("PLAN_READY");
        request.setCalculatedFields(calculatedFields.isEmpty() ? null : calculatedFields);
        request.setGroupBy(groupByItems(aggregate.get("groupBy")));
        request.setSlice(sliceItems(derive.get("filters"), unsupported));
        request.setColumns(outputColumns(ctePlan.get("output"), aggregate.get("groupBy"), metrics,
                List.of(), Map.of(), unsupported));
        request.setOrderBy(orderItems(ctePlan.get("orderBy"), availableFields(
                ctePlan.get("output"), aggregate.get("groupBy"), metrics.aliases()), unsupported));
        request.setLimit(limit(ctePlan.get("limit"), unsupported));
        request.setReturnTotal(false);

        if (model == null || model.isBlank()) {
            unsupported.add("derive input model must be declared for row-level SLA bridge");
        }
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            unsupported.add("row-level SLA bridge request must have output columns");
        }
        if (!unsupported.isEmpty()) {
            return BridgeResult.deferred(unsupported);
        }
        return BridgeResult.ready(model, request);
    }

    private static List<CalculatedFieldDef> rowLevelSlaCalculatedFields(Object rawDerived, String model,
                                                                        List<String> unsupported) {
        List<CalculatedFieldDef> result = new ArrayList<>();
        Map<String, String> durationEndByAlias = new LinkedHashMap<>();
        List<String> thresholdAliases = new ArrayList<>();
        List<String> hitAliases = new ArrayList<>();
        for (Map<String, Object> derived : mapList(rawDerived)) {
            String name = stringValue(derived.get("name"));
            String expr = stringValue(derived.get("expr"));
            if (name == null || !SAFE_ALIAS_PATTERN.matcher(name).matches() || expr == null) {
                unsupported.add("row-level SLA derived fields must declare governed name and expr: " + derived);
                continue;
            }

            Matcher duration = HOURS_BETWEEN_PATTERN.matcher(expr);
            if (duration.matches()) {
                String start = duration.group(1);
                String end = duration.group(2);
                if (!"createdAt".equals(start)
                        || (!"firstResponseAt".equals(end) && !"resolvedAt".equals(end))) {
                    unsupported.add("row-level SLA bridge supports hours_between(createdAt, firstResponseAt) "
                            + "or hours_between(createdAt, resolvedAt) only");
                    continue;
                }
                String caption = "resolvedAt".equals(end) ? "解决小时数" : "首响小时数";
                result.add(new CalculatedFieldDef(name, caption, "hours_between(createdAt, " + end + ")"));
                durationEndByAlias.put(name, end);
                continue;
            }

            Matcher priorityThreshold = PRIORITY_THRESHOLD_PATTERN.matcher(expr);
            if (priorityThreshold.matches()) {
                String field = priorityThreshold.group(1);
                if (!"priority".equals(field)) {
                    unsupported.add("priority-aware SLA bridge supports priority_threshold(priority, ...) only");
                    continue;
                }
                Map<String, String> thresholds = priorityThresholds(priorityThreshold.group(2), unsupported);
                if (thresholds.isEmpty()) {
                    continue;
                }
                result.add(new CalculatedFieldDef(name, "SLA优先级阈值小时数",
                        priorityThresholdExpression(thresholds)));
                thresholdAliases.add(name);
                continue;
            }

            Matcher notNull = NOT_NULL_PATTERN.matcher(expr);
            if (notNull.matches() && signedFunnelConvertedAlias(name, notNull.group(1), model)) {
                result.add(new CalculatedFieldDef(name, "漏斗转化标记",
                        "iif(is_not_null(" + notNull.group(1) + "), 1, 0)"));
                hitAliases.add(name);
                continue;
            }

            Matcher slaHit = SLA_HIT_PATTERN.matcher(expr);
            if (slaHit.matches()) {
                String nullableField = slaHit.group(1);
                String durationAlias = slaHit.group(2);
                String thresholdHours = slaHit.group(3);
                String durationEnd = durationEndByAlias.get(durationAlias);
                if (durationEnd == null || !durationEnd.equals(nullableField)) {
                    unsupported.add("row-level SLA hit predicate must use the signed duration end field "
                            + "and signed duration alias");
                    continue;
                }
                result.add(new CalculatedFieldDef(name, "SLA命中标记",
                        "iif(is_not_null(" + durationEnd + ") && " + durationAlias + " <= "
                                + thresholdHours + ", 1, 0)"));
                hitAliases.add(name);
                continue;
            }

            Matcher slaHitThresholdAlias = SLA_HIT_THRESHOLD_ALIAS_PATTERN.matcher(expr);
            if (slaHitThresholdAlias.matches()) {
                String nullableField = slaHitThresholdAlias.group(1);
                String durationAlias = slaHitThresholdAlias.group(2);
                String thresholdAlias = slaHitThresholdAlias.group(3);
                String durationEnd = durationEndByAlias.get(durationAlias);
                if (durationEnd == null
                        || !durationEnd.equals(nullableField)
                        || !thresholdAliases.contains(thresholdAlias)) {
                    unsupported.add("priority-aware SLA hit predicate must use the signed duration end field, "
                            + "signed duration alias, and signed threshold alias");
                    continue;
                }
                result.add(new CalculatedFieldDef(name, "SLA命中标记",
                        "iif(is_not_null(" + durationEnd + ") && " + durationAlias + " <= "
                                + thresholdAlias + ", 1, 0)"));
                hitAliases.add(name);
                continue;
            }

            Matcher combinedSlaHit = COMBINED_SLA_HIT_PATTERN.matcher(expr);
            if (combinedSlaHit.matches()) {
                String left = combinedSlaHit.group(1);
                String right = combinedSlaHit.group(2);
                if (!"combinedSlaHit".equals(name)
                        || !hitAliases.contains("firstResponseSlaHit")
                        || !hitAliases.contains("resolutionSlaHit")
                        || !signedCombinedSlaHitInputs(left, right)) {
                    unsupported.add("combined SLA hit predicate must use signed firstResponseSlaHit "
                            + "and resolutionSlaHit aliases");
                    continue;
                }
                result.add(new CalculatedFieldDef(name, "SLA组合命中标记",
                        "iif(firstResponseSlaHit == 1 && resolutionSlaHit == 1, 1, 0)"));
                hitAliases.add(name);
                continue;
            }

            Matcher slaOverdue = SLA_OVERDUE_PATTERN.matcher(expr);
            if (slaOverdue.matches()) {
                String nullableField = slaOverdue.group(1);
                String durationAlias = slaOverdue.group(2);
                String thresholdHours = slaOverdue.group(3);
                String durationEnd = durationEndByAlias.get(durationAlias);
                if (durationEnd == null || !durationEnd.equals(nullableField)) {
                    unsupported.add("row-level SLA overdue predicate must use the signed duration end field "
                            + "and signed duration alias");
                    continue;
                }
                result.add(new CalculatedFieldDef(name, "SLA超时标记",
                        "iif(is_null(" + durationEnd + ") || " + durationAlias + " > "
                                + thresholdHours + ", 1, 0)"));
                continue;
            }

            Matcher slaOverdueThresholdAlias = SLA_OVERDUE_THRESHOLD_ALIAS_PATTERN.matcher(expr);
            if (slaOverdueThresholdAlias.matches()) {
                String nullableField = slaOverdueThresholdAlias.group(1);
                String durationAlias = slaOverdueThresholdAlias.group(2);
                String thresholdAlias = slaOverdueThresholdAlias.group(3);
                String durationEnd = durationEndByAlias.get(durationAlias);
                if (durationEnd == null
                        || !durationEnd.equals(nullableField)
                        || !thresholdAliases.contains(thresholdAlias)) {
                    unsupported.add("priority-aware SLA overdue predicate must use the signed duration end field, "
                            + "signed duration alias, and signed threshold alias");
                    continue;
                }
                result.add(new CalculatedFieldDef(name, "SLA超时标记",
                        "iif(is_null(" + durationEnd + ") || " + durationAlias + " > "
                                + thresholdAlias + ", 1, 0)"));
                continue;
            }

            unsupported.add("row-level SLA bridge supports only hours_between duration, priority_threshold mapping, "
                    + "SLA hit threshold predicate, combined SLA hit predicate, signed CRM funnel non-null predicate, "
                    + "and SLA overdue threshold predicate: " + name);
        }
        if (result.isEmpty()) {
            unsupported.add("row-level SLA bridge requires signed calculatedFields");
        }
        return result;
    }

    private static boolean signedFunnelConvertedAlias(String name, String nullableField, String model) {
        return isCrmLeadModel(model)
                && (("convertedOpportunity".equals(name) && "convertedOpportunityId".equals(nullableField))
                || ("convertedOrder".equals(name) && "convertedOrderId".equals(nullableField)));
    }

    private static boolean isCrmLeadModel(String model) {
        return "CrmLead".equals(model);
    }

    private static Map<String, String> priorityThresholds(String raw, List<String> unsupported) {
        Map<String, String> thresholds = new LinkedHashMap<>();
        for (String item : raw.split(",")) {
            String entry = item.trim();
            if (entry.isEmpty() || !entry.contains("=")) {
                unsupported.add("priority-aware SLA bridge requires priority_threshold(priority, P1=..., P2=..., P3=...)");
                return Map.of();
            }
            String[] parts = entry.split("=", 2);
            String code = parts[0].trim().toUpperCase(Locale.ROOT);
            String value = parts[1].trim();
            if (!SIGNED_PRIORITY_CODES.contains(code)) {
                unsupported.add("priority-aware SLA bridge does not support priority code: " + code);
                return Map.of();
            }
            if (!value.matches("\\d+(?:\\.\\d+)?")) {
                unsupported.add("priority-aware SLA threshold must be numeric hours: " + entry);
                return Map.of();
            }
            if (thresholds.put(code, value) != null) {
                unsupported.add("priority-aware SLA bridge contains duplicate priority code: " + code);
                return Map.of();
            }
        }
        if (!thresholds.keySet().containsAll(SIGNED_PRIORITY_CODES) || thresholds.size() != SIGNED_PRIORITY_CODES.size()) {
            unsupported.add("priority-aware SLA bridge requires thresholds for P1/P2/P3");
            return Map.of();
        }
        return thresholds;
    }

    private static String priorityThresholdExpression(Map<String, String> thresholds) {
        String expr = "null";
        for (int i = SIGNED_PRIORITY_CODES.size() - 1; i >= 0; i--) {
            String code = SIGNED_PRIORITY_CODES.get(i);
            expr = "iif(priority == '" + code + "', " + thresholds.get(code) + ", " + expr + ")";
        }
        return expr;
    }

    private static boolean signedCombinedSlaHitInputs(String left, String right) {
        return ("firstResponseSlaHit".equals(left) && "resolutionSlaHit".equals(right))
                || ("resolutionSlaHit".equals(left) && "firstResponseSlaHit".equals(right));
    }

    private static MetricMapping rowLevelAggregateMetrics(Object raw, String model,
                                                          List<CalculatedFieldDef> calculatedFields,
                                                          List<String> unsupported) {
        List<String> calculatedNames = calculatedFields.stream()
                .map(CalculatedFieldDef::getName)
                .toList();
        List<Map<String, Object>> metricMaps = mapList(raw);
        Map<String, String> columnByAlias = new LinkedHashMap<>();
        for (Map<String, Object> metric : metricMaps) {
            String name = stringValue(metric.get("name"));
            String expr = stringValue(metric.get("expr"));
            if (name == null || expr == null || !SAFE_ALIAS_PATTERN.matcher(name).matches()) {
                unsupported.add("row-level SLA aggregate metric must declare governed name and expr: " + metric);
                continue;
            }
            String normalized = expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if ("count(*)".equals(normalized)) {
                if ("ticketCount".equals(name)) {
                    columnByAlias.put(name, "count(ticketId) AS " + name);
                } else if (isCrmLeadModel(model) && "leadCount".equals(name)) {
                    columnByAlias.put(name, "count(leadId) AS " + name);
                } else {
                    unsupported.add("row-level SLA/funnel count(*) metric must use a signed count alias");
                }
                continue;
            }
            Matcher sumAlias = SUM_ALIAS_PATTERN.matcher(expr);
            if (sumAlias.matches() && calculatedNames.contains(sumAlias.group(1))) {
                columnByAlias.put(name, expr + " AS " + name);
                continue;
            }
            Matcher caseWhenAlias = CASE_WHEN_ALIAS_PATTERN.matcher(expr);
            if (caseWhenAlias.matches() && signedFunnelCountAlias(name, caseWhenAlias.group(1), model)
                    && calculatedNames.contains(caseWhenAlias.group(1))) {
                columnByAlias.put(name, "sum(" + caseWhenAlias.group(1) + ") AS " + name);
                continue;
            }
            unsupported.add("row-level SLA bridge supports only count(*) or sum(signedCalculatedField) metrics: "
                    + name);
        }
        if (columnByAlias.isEmpty()) {
            unsupported.add("row-level SLA aggregate must declare object metrics");
        }
        return new MetricMapping(columnByAlias);
    }

    private static boolean signedFunnelCountAlias(String name, String calculatedAlias, String model) {
        return isCrmLeadModel(model)
                && (("convertedOpportunityCount".equals(name) && "convertedOpportunity".equals(calculatedAlias))
                || ("convertedOrderCount".equals(name) && "convertedOrder".equals(calculatedAlias)));
    }

    private static List<String> outputColumns(Object rawOutput, Object rawGroupBy, MetricMapping metrics,
                                              List<PostAggregateCalculationDef> postAgg,
                                              Map<String, String> outputAliasOverride,
                                              List<String> unsupported) {
        List<String> outputs = stringList(rawOutput);
        if (outputs.isEmpty()) {
            outputs.addAll(stringList(rawGroupBy));
            outputs.addAll(metrics.aliases());
            for (PostAggregateCalculationDef calc : postAgg) {
                outputs.add(calc.getName());
            }
        }
        List<String> columns = new ArrayList<>();
        for (String output : outputs) {
            String override = outputAliasOverride.get(output);
            String metricExpr = metrics.columnByAlias().get(output);
            if (override != null) {
                columns.add(override);
            } else {
                columns.add(metricExpr == null ? output : metricExpr);
            }
        }
        if (columns.stream().anyMatch(col -> col == null || col.isBlank())) {
            unsupported.add("output columns must be non-blank");
        }
        return columns;
    }

    private static WindowBridge rollingWindowBridge(Map<String, Object> stage, Map<String, Object> aggregate,
                                                    List<String> metricAliases, List<String> unsupported) {
        List<String> inputs = stringList(stage.get("inputs"));
        String aggregateName = stringValue(aggregate.get("name"));
        if (inputs.size() != 1 || aggregateName == null || !aggregateName.equals(inputs.get(0))) {
            unsupported.add("window_derive must reference the first aggregate stage for DSL_CTE rolling bridge");
            return null;
        }

        Map<String, Object> window = mapValue(stage.get("window"));
        if (window == null) {
            unsupported.add("window_derive must declare window for DSL_CTE rolling bridge");
            return null;
        }
        List<Map<String, Object>> derived = mapList(stage.get("derived"));
        if (containsLagOrLead(derived)) {
            return periodOverPeriodBridge(stage, aggregate, metricAliases, derived, window, unsupported);
        }
        if (containsRankOrCumulativeContribution(derived)) {
            addCumulativeContributionDiagnostics(derived, unsupported);
            return null;
        }
        if (!stringList(window.get("partitionBy")).isEmpty()) {
            unsupported.add("DSL_CTE rolling bridge does not support partitionBy in this cut");
            return null;
        }
        List<Map<String, Object>> orderBy = mapList(window.get("orderBy"));
        if (orderBy.size() != 1) {
            unsupported.add("DSL_CTE rolling bridge requires exactly one window orderBy field");
            return null;
        }
        String orderField = stringValue(orderBy.get(0).get("field"));
        String orderDir = stringValue(orderBy.get(0).get("dir"));
        if (orderField == null || (orderDir != null && !"ASC".equalsIgnoreCase(orderDir))) {
            unsupported.add("DSL_CTE rolling bridge requires ASC orderBy field");
            return null;
        }
        if (!stringList(aggregate.get("groupBy")).contains(orderField)) {
            unsupported.add("DSL_CTE rolling bridge orderBy field must be part of aggregate groupBy");
            return null;
        }

        Map<String, Object> frame = mapValue(window.get("frame"));
        Integer frameStart = intValue(frame == null ? null : frame.get("start"));
        Integer frameEnd = intValue(frame == null ? null : frame.get("end"));
        if (frameStart == null || frameEnd == null || frameEnd != 0 || frameStart >= 0) {
            unsupported.add("DSL_CTE rolling bridge requires rows frame ending at current row");
            return null;
        }
        int windowSize = Math.abs(frameStart) + 1;
        if (windowSize != 7 && windowSize != 30 && windowSize != 90) {
            unsupported.add("DSL_CTE rolling bridge supports only 7/30/90 row windows");
            return null;
        }

        if (derived.size() != 1) {
            unsupported.add("DSL_CTE rolling bridge requires exactly one derived rolling metric");
            return null;
        }
        String derivedName = stringValue(derived.get(0).get("name"));
        String expr = stringValue(derived.get(0).get("expr"));
        Matcher matcher = ROLLING_SUM_PATTERN.matcher(expr == null ? "" : expr);
        if (derivedName == null || !matcher.matches()) {
            unsupported.add("window_derive formula is not executable through DSL_CTE rolling bridge: " + derived);
            return null;
        }
        String measure = matcher.group(1);
        int exprWindowSize = Integer.parseInt(matcher.group(2));
        if (!metricAliases.contains(measure) || exprWindowSize != windowSize) {
            unsupported.add("window_derive rolling formula must reference an aggregate metric and matching frame");
            return null;
        }

        String grain = grain(orderField);
        if (!"day".equals(grain)) {
            unsupported.add("DSL_CTE rolling bridge supports only day-grain rolling windows in this cut");
            return null;
        }
        String comparison = "rolling_" + windowSize + "d";
        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("field", orderField);
        timeWindow.put("grain", grain);
        timeWindow.put("comparison", comparison);
        timeWindow.put("targetMetrics", List.of(measure));
        timeWindow.put("rollingAggregator", "sum");

        Map<String, String> aliasOverride = Map.of(derivedName, measure + "__" + comparison);
        return new WindowBridge(timeWindow, aliasOverride);
    }

    private static WindowBridge periodOverPeriodBridge(Map<String, Object> stage, Map<String, Object> aggregate,
                                                       List<String> metricAliases,
                                                       List<Map<String, Object>> derived,
                                                       Map<String, Object> window,
                                                       List<String> unsupported) {
        List<String> inputs = stringList(stage.get("inputs"));
        String aggregateName = stringValue(aggregate.get("name"));
        if (inputs.size() != 1 || aggregateName == null || !aggregateName.equals(inputs.get(0))) {
            unsupported.add("window_derive must reference the first aggregate stage for DSL_CTE period-over-period bridge");
            return null;
        }
        if (derived.size() != 2) {
            unsupported.add("DSL_CTE period-over-period bridge requires lag metric and growth ratio derived fields");
            return null;
        }

        List<String> partitionBy = stringList(window.get("partitionBy"));
        if (partitionBy.isEmpty()) {
            unsupported.add("DSL_CTE period-over-period bridge requires partitionBy fields");
            return null;
        }
        List<String> groupBy = stringList(aggregate.get("groupBy"));
        if (!groupBy.containsAll(partitionBy)) {
            unsupported.add("DSL_CTE period-over-period partitionBy fields must be part of aggregate groupBy");
            return null;
        }
        List<Map<String, Object>> orderBy = mapList(window.get("orderBy"));
        if (orderBy.size() != 1) {
            unsupported.add("DSL_CTE period-over-period bridge requires exactly one window orderBy field");
            return null;
        }
        String orderField = stringValue(orderBy.get(0).get("field"));
        String orderDir = stringValue(orderBy.get(0).get("dir"));
        if (orderField == null || (orderDir != null && !"ASC".equalsIgnoreCase(orderDir))) {
            unsupported.add("DSL_CTE period-over-period bridge requires ASC orderBy field");
            return null;
        }
        if (!groupBy.contains(orderField)) {
            unsupported.add("DSL_CTE period-over-period orderBy field must be part of aggregate groupBy");
            return null;
        }
        if (mapValue(window.get("frame")) != null) {
            unsupported.add("DSL_CTE period-over-period bridge does not support explicit frames");
            return null;
        }

        Map<String, Object> lagDerived = derived.get(0);
        String priorAlias = stringValue(lagDerived.get("name"));
        String lagExpr = stringValue(lagDerived.get("expr"));
        Matcher lagMatcher = LAG_PATTERN.matcher(lagExpr == null ? "" : lagExpr);
        if (priorAlias == null || !lagMatcher.matches()) {
            unsupported.add("DSL_CTE period-over-period bridge requires first derived field to be lag(metric)");
            return null;
        }
        String measure = lagMatcher.group(1);
        if (!metricAliases.contains(measure)) {
            unsupported.add("DSL_CTE period-over-period lag metric must reference an aggregate metric");
            return null;
        }

        Map<String, Object> ratioDerived = derived.get(1);
        String ratioAlias = stringValue(ratioDerived.get("name"));
        String ratioExpr = stringValue(ratioDerived.get("expr"));
        if (ratioAlias == null || !matchesPeriodGrowthExpr(ratioExpr, measure, priorAlias)) {
            unsupported.add("DSL_CTE period-over-period bridge requires growth formula (metric - lagAlias) / lagAlias");
            return null;
        }

        String grain = grain(orderField);
        if (!"month".equals(grain)) {
            unsupported.add("DSL_CTE period-over-period bridge supports only month-grain MoM in this cut");
            return null;
        }
        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("field", orderField);
        timeWindow.put("grain", grain);
        timeWindow.put("comparison", "mom");
        timeWindow.put("targetMetrics", List.of(measure));

        Map<String, String> aliasOverride = new LinkedHashMap<>();
        aliasOverride.put(priorAlias, measure + "__prior");
        aliasOverride.put(ratioAlias, measure + "__ratio");
        return new WindowBridge(timeWindow, aliasOverride);
    }

    private static boolean matchesPeriodGrowthExpr(String expr, String measure, String priorAlias) {
        if (expr == null || measure == null || priorAlias == null) {
            return false;
        }
        String normalized = expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String current = measure.toLowerCase(Locale.ROOT);
        String prior = priorAlias.toLowerCase(Locale.ROOT);
        String canonical = "(" + current + "-" + prior + ")/" + prior;
        String canonicalWithEnginePrior = "(" + current + "-" + current + "__prior)/" + current + "__prior";
        return canonical.equals(normalized) || canonicalWithEnginePrior.equals(normalized);
    }

    private static void addPreAggregateDeriveDiagnostics(List<Map<String, Object>> stages,
                                                         Map<String, Object> firstStage,
                                                         List<String> unsupported) {
        addUnsupported(unsupported,
                "pre-aggregate derive requires a signed row-level calculatedFields bridge; current cut keeps it deferred: "
                        + stringValue(firstStage.get("name")));
        for (Map<String, Object> derived : mapList(firstStage.get("derived"))) {
            String expr = stringValue(derived.get("expr"));
            if (expr == null) {
                continue;
            }
            String normalized = expr.toLowerCase(Locale.ROOT);
            if (normalized.contains("hours_between(")) {
                addUnsupported(unsupported,
                        "pre-aggregate SLA duration function hours_between(...) is not signed; needs a governed DATE_DIFF-style mapping");
            }
            if (normalized.contains(" is null")
                    || normalized.contains(" is not null")
                    || normalized.contains(" and ")
                    || normalized.contains(" or ")) {
                addUnsupported(unsupported,
                        "pre-aggregate boolean/null-handling predicate is not signed as a row-level derived field");
            }
        }
        for (int i = 1; i < stages.size(); i++) {
            Map<String, Object> stage = stages.get(i);
            String type = stringValue(stage.get("type"));
            if ("aggregate".equals(type)) {
                for (Map<String, Object> metric : mapList(stage.get("metrics"))) {
                    String expr = stringValue(metric.get("expr"));
                    if (expr != null && expr.toLowerCase(Locale.ROOT).contains("case when")) {
                        addUnsupported(unsupported,
                                "conditional numerator aggregation with CASE WHEN over row-level derived fields is not signed");
                    }
                }
            } else if ("derive".equals(type)) {
                for (Map<String, Object> derived : mapList(stage.get("derived"))) {
                    String expr = stringValue(derived.get("expr"));
                    if (isMetricToMetricRatio(expr)) {
                        addUnsupported(unsupported,
                                "metric-to-metric post-aggregate ratio is not executable through DSL_CTE bridge v1: "
                                        + stringValue(derived.get("name")));
                    }
                }
            } else if ("postSlice".equals(type)) {
                addUnsupported(unsupported,
                        "postSlice after a pre-aggregate business metric remains deferred until the derived metric bridge is signed");
            }
        }
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

    private static void addUnsupported(List<String> unsupported, String message) {
        if (!unsupported.contains(message)) {
            unsupported.add(message);
        }
    }

    private static MetricMapping metrics(Object raw, List<String> unsupported) {
        List<Map<String, Object>> metricMaps = mapList(raw);
        Map<String, String> columnByAlias = new LinkedHashMap<>();
        for (Map<String, Object> metric : metricMaps) {
            String name = stringValue(metric.get("name"));
            String expr = stringValue(metric.get("expr"));
            if (name == null || expr == null) {
                unsupported.add("aggregate metric must declare name and expr: " + metric);
                continue;
            }
            columnByAlias.put(name, expr + " AS " + name);
        }
        if (columnByAlias.isEmpty()) {
            unsupported.add("aggregate stage must declare object metrics for DSL_CTE bridge v1");
        }
        return new MetricMapping(columnByAlias);
    }

    private static List<PostAggregateCalculationDef> postAggregateCalculations(Object rawDerived,
                                                                               List<String> metricAliases,
                                                                               List<String> unsupported) {
        List<PostAggregateCalculationDef> result = new ArrayList<>();
        for (Map<String, Object> derived : mapList(rawDerived)) {
            String name = stringValue(derived.get("name"));
            String expr = stringValue(derived.get("expr"));
            String measure = ratioToTotalMeasure(expr, metricAliases);
            if (name == null || measure == null) {
                unsupported.add("derive formula is not executable through DSL_CTE bridge v1: " + derived);
                continue;
            }
            result.add(new PostAggregateCalculationDef(name, "ratioToTotal", measure, "grandTotal", "ratio"));
        }
        return result;
    }

    private static String ratioToTotalMeasure(String expr, List<String> metricAliases) {
        if (expr == null) {
            return null;
        }
        String normalized = expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        for (String alias : metricAliases) {
            String lowerAlias = alias.toLowerCase(Locale.ROOT);
            if ((lowerAlias + "/sum(" + lowerAlias + ")over()").equals(normalized)) {
                return alias;
            }
        }
        return null;
    }

    private static CumulativeDerived cumulativeDerived(Object rawDerived, String metricAlias,
                                                       List<String> unsupported) {
        String rankAlias = null;
        String cumulativeAlias = null;
        for (Map<String, Object> item : mapList(rawDerived)) {
            String name = stringValue(item.get("name"));
            String expr = stringValue(item.get("expr"));
            if (name == null || !SAFE_ALIAS_PATTERN.matcher(name).matches()) {
                unsupported.add("result-stage cumulative contribution derived fields must declare governed aliases");
                continue;
            }
            String normalized = expr == null ? "" : expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if ("rank()".equals(normalized)) {
                rankAlias = name;
                continue;
            }
            Matcher matcher = CUMULATIVE_SHARE_PATTERN.matcher(expr == null ? "" : expr);
            if (matcher.matches() && metricAlias.equals(matcher.group(1))) {
                cumulativeAlias = name;
                continue;
            }
            unsupported.add("result-stage cumulative contribution bridge supports only rank() and cumulative share formulas");
        }
        if (rankAlias == null || cumulativeAlias == null) {
            unsupported.add("result-stage cumulative contribution bridge requires rank() and cumulative share derived fields");
            return null;
        }
        return new CumulativeDerived(rankAlias, cumulativeAlias);
    }

    private static List<MetricRatioDerived> slaMetricRatioDerived(Object rawDerived, String model,
                                                                  List<String> metricAliases,
                                                                  List<String> unsupported) {
        List<Map<String, Object>> derived = mapList(rawDerived);
        if (derived.isEmpty() || derived.size() > 2) {
            unsupported.add("result-stage SLA metric ratio bridge requires one or two ratio derived fields");
            return List.of();
        }
        List<MetricRatioDerived> result = new ArrayList<>();
        for (Map<String, Object> item : derived) {
            String name = stringValue(item.get("name"));
            String expr = stringValue(item.get("expr"));
            if (name == null || !SAFE_ALIAS_PATTERN.matcher(name).matches()) {
                unsupported.add("result-stage SLA metric ratio derived field must declare a governed alias");
                continue;
            }
            Matcher matcher = METRIC_RATIO_PATTERN.matcher(expr == null ? "" : expr);
            if (!matcher.matches()) {
                unsupported.add("result-stage SLA metric ratio bridge supports only numerator / denominator formulas");
                continue;
            }
            String numerator = matcher.group(1);
            String denominator = matcher.group(2);
            if (!signedMetricRatioAlias(numerator, denominator, name, model)) {
                unsupported.add("result-stage SLA metric ratio bridge supports only signed SLA numerator / ticketCount ratios "
                        + "or signed CRM funnel conversion ratios");
                continue;
            }
            if (!metricAliases.contains(numerator) || !metricAliases.contains(denominator)) {
                unsupported.add("result-stage SLA metric ratio must reference signed aggregate metric aliases");
                continue;
            }
            if (result.stream().anyMatch(existing -> existing.ratioAlias().equals(name))) {
                unsupported.add("result-stage SLA metric ratio aliases must be unique");
                continue;
            }
            result.add(new MetricRatioDerived(name, numerator, denominator));
        }
        return unsupported.isEmpty() ? result : List.of();
    }

    private static boolean signedSlaRatioAlias(String numerator, String denominator, String ratioAlias) {
        if (!"ticketCount".equals(denominator)) {
            return false;
        }
        return switch (numerator) {
            case "slaHitCount" -> "slaAchievementRate".equals(ratioAlias);
            case "firstResponseSlaHitCount" -> "firstResponseSlaRate".equals(ratioAlias);
            case "resolutionSlaHitCount" -> "resolutionSlaRate".equals(ratioAlias);
            case "combinedSlaHitCount" -> "combinedSlaRate".equals(ratioAlias);
            default -> false;
        };
    }

    private static boolean signedMetricRatioAlias(String numerator, String denominator, String ratioAlias,
                                                  String model) {
        return signedSlaRatioAlias(numerator, denominator, ratioAlias)
                || (isCrmLeadModel(model) && signedFunnelRatioAlias(numerator, denominator, ratioAlias));
    }

    private static boolean signedFunnelRatioAlias(String numerator, String denominator, String ratioAlias) {
        if ("convertedOpportunityCount".equals(numerator) && "leadCount".equals(denominator)) {
            return "leadToOpportunityRate".equals(ratioAlias);
        }
        if ("convertedOrderCount".equals(numerator) && "convertedOpportunityCount".equals(denominator)) {
            return "opportunityToOrderRate".equals(ratioAlias);
        }
        if ("convertedOrderCount".equals(numerator) && "leadCount".equals(denominator)) {
            return "leadToOrderRate".equals(ratioAlias)
                    || "leadToOrderConversionRate".equals(ratioAlias);
        }
        return false;
    }

    private static List<ResultStageFilter> resultStageFilters(Object rawFilters, String cumulativeAlias,
                                                              List<String> unsupported) {
        return resultStageAliasFilters(rawFilters, cumulativeAlias,
                "result-stage cumulative contribution bridge",
                "postSlice only on cumulative share alias",
                "supports only < or <= cumulative share filters",
                List.of("<", "<="), unsupported);
    }

    private static List<ResultStageFilter> resultStageAliasFilters(Object rawFilters, String signedAlias,
                                                                  String bridgeName,
                                                                  List<String> unsupported) {
        return resultStageAliasFilters(rawFilters, List.of(signedAlias), bridgeName, unsupported);
    }

    private static List<ResultStageFilter> resultStageAliasFilters(Object rawFilters, List<String> signedAliases,
                                                                  String bridgeName,
                                                                  List<String> unsupported) {
        return resultStageAliasFilters(rawFilters, signedAliases, bridgeName,
                "postSlice only on signed derived alias",
                "supports only simple comparison postSlice filters",
                List.of("=", "!=", "<>", "<", "<=", ">", ">="), unsupported);
    }

    private static List<ResultStageFilter> resultStageAliasFilters(Object rawFilters, String signedAlias,
                                                                  String bridgeName,
                                                                  String aliasMessage,
                                                                  String operatorMessage,
                                                                  List<String> allowedOps,
                                                                  List<String> unsupported) {
        return resultStageAliasFilters(rawFilters, List.of(signedAlias), bridgeName, aliasMessage, operatorMessage,
                allowedOps, unsupported);
    }

    private static List<ResultStageFilter> resultStageAliasFilters(Object rawFilters, List<String> signedAliases,
                                                                  String bridgeName,
                                                                  String aliasMessage,
                                                                  String operatorMessage,
                                                                  List<String> allowedOps,
                                                                  List<String> unsupported) {
        List<ResultStageFilter> result = new ArrayList<>();
        for (Map<String, Object> filter : mapList(rawFilters)) {
            String field = stringValue(filter.get("field"));
            String op = stringValue(filter.get("op"));
            if (!signedAliases.contains(field)) {
                unsupported.add(bridgeName + " " + aliasMessage);
                continue;
            }
            if (filter.containsKey("valueField")) {
                unsupported.add(bridgeName + " does not support valueField postSlice");
                continue;
            }
            String sqlOp = sqlOperator(op);
            if (sqlOp == null || !allowedOps.contains(sqlOp)) {
                unsupported.add(bridgeName + " " + operatorMessage);
                continue;
            }
            Object value = filter.get("value");
            if (!(value instanceof Number)) {
                unsupported.add(bridgeName + " postSlice value must be numeric");
                continue;
            }
            result.add(new ResultStageFilter(field, sqlOp, value));
        }
        return result;
    }

    @SafeVarargs
    private static boolean allSafeAliases(List<String>... aliases) {
        for (List<String> aliasList : aliases) {
            for (String alias : aliasList) {
                if (alias == null || !SAFE_ALIAS_PATTERN.matcher(alias).matches()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String sqlOperator(String op) {
        if (op == null) {
            return null;
        }
        return switch (op.trim()) {
            case "=", "!=", "<>", "<", "<=", ">", ">=" -> op.trim();
            default -> null;
        };
    }

    private static String quoteAlias(String alias) {
        if (alias == null || !SAFE_ALIAS_PATTERN.matcher(alias).matches()) {
            throw RX.throwB("DSL_CTE_RESULT_STAGE_UNSAFE_ALIAS: " + alias);
        }
        return "\"" + alias.replace("\"", "\"\"") + "\"";
    }

    private static boolean containsLagOrLead(List<Map<String, Object>> derived) {
        for (Map<String, Object> item : derived) {
            String expr = stringValue(item.get("expr"));
            if (expr == null) {
                continue;
            }
            String normalized = expr.toLowerCase(Locale.ROOT);
            if (normalized.matches(".*\\b(lag|lead)\\s*\\(.*")) {
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

    private static void addCumulativeContributionDiagnostics(List<Map<String, Object>> derived,
                                                            List<String> unsupported) {
        for (Map<String, Object> item : derived) {
            String name = stringValue(item.get("name"));
            String expr = stringValue(item.get("expr"));
            String normalized = expr == null ? "" : expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            if ("rank()".equals(normalized)) {
                addUnsupported(unsupported,
                        "rank() over aggregate metric order is not signed through DSL_CTE bridge v1: " + name);
            } else if (normalized.contains("overorder") || normalized.contains("overall")) {
                addUnsupported(unsupported,
                        "cumulative contribution window ratio is not signed through DSL_CTE bridge v1: " + name);
            }
        }
        addUnsupported(unsupported,
                "cumulative contribution requires result-stage ranking, running-total ratio, deterministic ordering, and postSlice semantics before execution can be signed");
    }

    private static List<SemanticQueryRequest.GroupByItem> groupByItems(Object raw) {
        List<String> fields = stringList(raw);
        List<SemanticQueryRequest.GroupByItem> groupBy = new ArrayList<>();
        for (String field : fields) {
            groupBy.add(new SemanticQueryRequest.GroupByItem(field, null));
        }
        return groupBy.isEmpty() ? null : groupBy;
    }

    private static List<SemanticQueryRequest.SliceItem> sliceItems(Object raw, List<String> unsupported) {
        List<Map<String, Object>> filters = mapList(raw);
        if (filters.isEmpty()) {
            return null;
        }
        List<SemanticQueryRequest.SliceItem> result = new ArrayList<>();
        for (Map<String, Object> filter : filters) {
            String field = stringValue(filter.get("field"));
            String op = stringValue(filter.get("op"));
            if (field == null || op == null) {
                unsupported.add("filter must declare field and op: " + filter);
                continue;
            }
            if (filter.containsKey("valueField")) {
                unsupported.add("filter valueField is not executable through DSL_CTE bridge v1: " + filter);
                continue;
            }
            SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
            item.setField(field);
            item.setOp(op);
            item.setValue(filter.get("value"));
            result.add(item);
        }
        return result;
    }

    private static List<SemanticQueryRequest.OrderItem> orderItems(Object raw, List<String> availableFields,
                                                                   List<String> unsupported) {
        if (raw == null) {
            return null;
        }
        List<Map<String, Object>> orderMaps = mapList(raw);
        if (orderMaps.isEmpty()) {
            unsupported.add("DSL_CTE bridge orderBy must be a non-empty object list");
            return null;
        }
        List<SemanticQueryRequest.OrderItem> orderBy = new ArrayList<>();
        for (Map<String, Object> order : orderMaps) {
            if (order.containsKey("expr")) {
                unsupported.add("DSL_CTE bridge does not support expression orderBy: " + order.get("expr"));
                continue;
            }
            String field = stringValue(order.get("field"));
            String dir = stringValue(order.getOrDefault("dir", "asc"));
            if (field == null || !availableFields.contains(field)) {
                unsupported.add("DSL_CTE bridge orderBy field must reference output/group/metric alias: " + field);
                continue;
            }
            if (dir == null || (!"asc".equalsIgnoreCase(dir) && !"desc".equalsIgnoreCase(dir))) {
                unsupported.add("DSL_CTE bridge orderBy dir must be ASC or DESC: " + order);
                continue;
            }
            SemanticQueryRequest.OrderItem item = new SemanticQueryRequest.OrderItem();
            item.setField(field);
            item.setDir(dir.toLowerCase(Locale.ROOT));
            orderBy.add(item);
        }
        return orderBy.isEmpty() ? null : orderBy;
    }

    private static Integer limit(Object raw, List<String> unsupported) {
        if (raw == null) {
            return null;
        }
        Integer value = intValue(raw);
        if (value == null || value <= 0 || value > 1000) {
            unsupported.add("DSL_CTE bridge limit must be a positive integer <= 1000: " + raw);
            return null;
        }
        return value;
    }

    private static List<String> availableFields(Object rawOutput, Object rawGroupBy, List<String> metricAliases) {
        List<String> fields = stringList(rawOutput);
        for (String field : stringList(rawGroupBy)) {
            if (!fields.contains(field)) {
                fields.add(field);
            }
        }
        for (String alias : metricAliases) {
            if (!fields.contains(alias)) {
                fields.add(alias);
            }
        }
        return fields;
    }

    public static SqlGenerationResult applyTopLevelLimitIfDeclared(SqlGenerationResult base, Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null || !unsupported.isEmpty()) {
            return base;
        }
        Integer value = intValue(ctePlan.get("limit"));
        if (value == null) {
            return base;
        }
        if (value <= 0 || value > 1000) {
            throw RX.throwB("DSL_CTE bridge limit must be a positive integer <= 1000: " + ctePlan.get("limit"));
        }
        String sql = base.getSql() == null ? "" : base.getSql().stripTrailing();
        if (containsTrailingLimit(sql)) {
            return base;
        }
        List<Object> params = new ArrayList<>();
        if (base.getParams() != null) {
            params.addAll(base.getParams());
        }
        params.add(value);
        return new SqlGenerationResult(sql + "\nLIMIT ?", params, base.getQueryEngine(), base.getCteStages());
    }

    private static boolean containsTrailingLimit(String sql) {
        return Pattern.compile("(?is).*\\blimit\\s+(?:\\?|\\d+)\\s*$").matcher(sql).matches();
    }

    private static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = mapValue(item);
            if (map != null) {
                result.add(map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
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

    private static Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String grain(String field) {
        if (field == null) {
            return null;
        }
        String lower = field.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".day") || lower.endsWith("$id")) {
            return "day";
        }
        if (lower.endsWith(".month") || lower.endsWith("$month")) {
            return "month";
        }
        if (lower.endsWith(".year") || lower.endsWith("$year")) {
            return "year";
        }
        return null;
    }

    private record MetricMapping(Map<String, String> columnByAlias) {
        List<String> aliases() {
            return new ArrayList<>(columnByAlias.keySet());
        }
    }

    private record WindowBridge(Map<String, Object> timeWindow, Map<String, String> outputAliasOverride) {
    }

    private record CumulativeDerived(String rankAlias, String cumulativeAlias) {
    }

    public record MetricRatioDerived(String ratioAlias, String numeratorAlias, String denominatorAlias) {
    }

    public record ResultStageFilter(String field, String op, Object value) {
    }

    public record ResultStageWindowPlan(List<String> output, List<String> groupBy, String metricAlias,
                                        String rankAlias, String cumulativeAlias,
                                        List<ResultStageFilter> filters) {

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "cumulative_contribution");
            result.put("bridge_scope", "result_stage_window");
            result.put("bridge_signed", true);
            result.put("metric", metricAlias);
            result.put("rank_alias", rankAlias);
            result.put("cumulative_alias", cumulativeAlias);
            result.put("deterministic_tie_breakers", groupBy);
            result.put("postSlice_filters", filters.size());
            return result;
        }

        SqlGenerationResult wrap(SqlGenerationResult base) {
            if (base == null || base.getSql() == null || base.getSql().isBlank()) {
                throw RX.throwB("DSL_CTE_RESULT_STAGE_BASE_SQL_MISSING");
            }
            String baseSql = base.getSql().trim();
            if (!base.hasCteStages() && baseSql.regionMatches(true, 0, "WITH ", 0, 5)) {
                throw RX.throwB("DSL_CTE_RESULT_STAGE_BASE_WITH_UNSUPPORTED: base SQL must expose structured CTE stages");
            }

            List<Object> params = new ArrayList<>();
            StringBuilder sql = new StringBuilder("WITH ");
            if (base.hasCteStages()) {
                for (int i = 0; i < base.getCteStages().size(); i++) {
                    if (i > 0) {
                        sql.append(",\n");
                    }
                    SqlGenerationResult.CteStage stage = base.getCteStages().get(i);
                    sql.append(stage.alias()).append(" AS (\n").append(stage.sql()).append("\n)");
                    params.addAll(stage.params());
                }
                sql.append(",\n");
            }
            params.addAll(base.getParams());

            String baseAlias = "dsl_cte_base";
            String windowAlias = "dsl_cte_window";
            sql.append(baseAlias).append(" AS (\n").append(baseSql).append("\n),\n");
            sql.append(windowAlias).append(" AS (\n");
            sql.append("SELECT ");
            List<String> selectItems = new ArrayList<>();
            for (String field : groupBy) {
                selectItems.add(quoteAlias(field));
            }
            selectItems.add(quoteAlias(metricAlias));
            selectItems.add("RANK() OVER (ORDER BY " + quoteAlias(metricAlias) + " DESC) AS " + quoteAlias(rankAlias));
            selectItems.add("(1.0 * SUM(" + quoteAlias(metricAlias) + ") OVER (ORDER BY "
                    + deterministicOrderBy()
                    + " ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) / NULLIF(SUM("
                    + quoteAlias(metricAlias) + ") OVER (), 0)) AS " + quoteAlias(cumulativeAlias));
            sql.append(String.join(", ", selectItems));
            sql.append("\nFROM ").append(baseAlias).append("\n)\n");

            sql.append("SELECT ");
            sql.append(String.join(", ", output.stream().map(DslCteDslRequestMapper::quoteAlias).toList()));
            sql.append("\nFROM ").append(windowAlias);
            if (!filters.isEmpty()) {
                sql.append("\nWHERE ");
                List<String> whereParts = new ArrayList<>();
                for (ResultStageFilter filter : filters) {
                    whereParts.add(quoteAlias(filter.field()) + " " + filter.op() + " ?");
                    params.add(filter.value());
                }
                sql.append(String.join(" AND ", whereParts));
            }
            sql.append("\nORDER BY ").append(deterministicOrderBy());
            return new SqlGenerationResult(sql.toString(), params, null);
        }

        private String deterministicOrderBy() {
            List<String> order = new ArrayList<>();
            order.add(quoteAlias(metricAlias) + " DESC");
            for (String field : groupBy) {
                order.add(quoteAlias(field) + " ASC");
            }
            return String.join(", ", order);
        }
    }

    public record ResultStageMetricRatioPlan(List<String> output, List<String> groupBy, List<String> metricAliases,
                                             List<MetricRatioDerived> ratios,
                                             List<ResultStageFilter> filters) {

        Map<String, Object> summary() {
            MetricRatioDerived primaryRatio = ratios.get(0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", ratios.stream().anyMatch(ratio -> signedFunnelRatioAlias(
                    ratio.numeratorAlias(), ratio.denominatorAlias(), ratio.ratioAlias()))
                    ? "funnel_conversion_rate"
                    : "sla_metric_ratio");
            result.put("bridge_scope", "result_stage_metric_ratio");
            result.put("bridge_signed", true);
            result.put("numerator", primaryRatio.numeratorAlias());
            result.put("denominator", primaryRatio.denominatorAlias());
            result.put("ratio_alias", primaryRatio.ratioAlias());
            result.put("ratios", ratios.stream()
                    .map(ratio -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("numerator", ratio.numeratorAlias());
                        item.put("denominator", ratio.denominatorAlias());
                        item.put("ratio_alias", ratio.ratioAlias());
                        return item;
                    })
                    .toList());
            result.put("postSlice_filters", filters.size());
            return result;
        }

        SqlGenerationResult wrap(SqlGenerationResult base) {
            if (base == null || base.getSql() == null || base.getSql().isBlank()) {
                throw RX.throwB("DSL_CTE_RESULT_STAGE_BASE_SQL_MISSING");
            }
            String baseSql = base.getSql().trim();
            if (!base.hasCteStages() && baseSql.regionMatches(true, 0, "WITH ", 0, 5)) {
                throw RX.throwB("DSL_CTE_RESULT_STAGE_BASE_WITH_UNSUPPORTED: base SQL must expose structured CTE stages");
            }

            List<Object> params = new ArrayList<>();
            StringBuilder sql = new StringBuilder("WITH ");
            if (base.hasCteStages()) {
                for (int i = 0; i < base.getCteStages().size(); i++) {
                    if (i > 0) {
                        sql.append(",\n");
                    }
                    SqlGenerationResult.CteStage stage = base.getCteStages().get(i);
                    sql.append(stage.alias()).append(" AS (\n").append(stage.sql()).append("\n)");
                    params.addAll(stage.params());
                }
                sql.append(",\n");
            }
            params.addAll(base.getParams());

            String baseAlias = "dsl_cte_base";
            String ratioAliasName = "dsl_cte_metric_ratio";
            sql.append(baseAlias).append(" AS (\n").append(baseSql).append("\n),\n");
            sql.append(ratioAliasName).append(" AS (\n");
            sql.append("SELECT ");
            List<String> selectItems = new ArrayList<>();
            for (String field : groupBy) {
                selectItems.add(quoteAlias(field));
            }
            for (String metric : metricAliases) {
                selectItems.add(quoteAlias(metric));
            }
            for (MetricRatioDerived ratio : ratios) {
                selectItems.add("(1.0 * " + quoteAlias(ratio.numeratorAlias()) + " / NULLIF("
                        + quoteAlias(ratio.denominatorAlias()) + ", 0)) AS " + quoteAlias(ratio.ratioAlias()));
            }
            sql.append(String.join(", ", selectItems));
            sql.append("\nFROM ").append(baseAlias).append("\n)\n");

            sql.append("SELECT ");
            sql.append(String.join(", ", output.stream().map(DslCteDslRequestMapper::quoteAlias).toList()));
            sql.append("\nFROM ").append(ratioAliasName);
            if (!filters.isEmpty()) {
                sql.append("\nWHERE ");
                List<String> whereParts = new ArrayList<>();
                for (ResultStageFilter filter : filters) {
                    whereParts.add(quoteAlias(filter.field()) + " " + filter.op() + " ?");
                    params.add(filter.value());
                }
                sql.append(String.join(" AND ", whereParts));
            }
            if (!groupBy.isEmpty()) {
                sql.append("\nORDER BY ");
                sql.append(groupBy.stream()
                        .map(field -> quoteAlias(field) + " ASC")
                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            return new SqlGenerationResult(sql.toString(), params, null);
        }
    }

    public record ResultStageWindowBridgeResult(String status, String model, SemanticQueryRequest baseRequest,
                                                ResultStageWindowPlan plan, List<String> unsupported) {
        static ResultStageWindowBridgeResult ready(String model, SemanticQueryRequest baseRequest,
                                                   ResultStageWindowPlan plan) {
            return new ResultStageWindowBridgeResult(STATUS_READY, model, baseRequest, plan, List.of());
        }

        static ResultStageWindowBridgeResult deferred(List<String> unsupported) {
            return new ResultStageWindowBridgeResult(STATUS_DEFERRED, null, null, null, List.copyOf(unsupported));
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }

        public Map<String, Object> summary() {
            return plan == null ? Map.of() : plan.summary();
        }

        public SqlGenerationResult wrap(SqlGenerationResult base) {
            if (!ready()) {
                throw RX.throwB("DSL_CTE_RESULT_STAGE_WINDOW_NOT_SUPPORTED: " + unsupported);
            }
            return plan.wrap(base);
        }
    }

    public record ResultStageMetricRatioBridgeResult(String status, String model, SemanticQueryRequest baseRequest,
                                                     ResultStageMetricRatioPlan plan,
                                                     List<String> unsupported) {
        static ResultStageMetricRatioBridgeResult ready(String model, SemanticQueryRequest baseRequest,
                                                        ResultStageMetricRatioPlan plan) {
            return new ResultStageMetricRatioBridgeResult(STATUS_READY, model, baseRequest, plan, List.of());
        }

        static ResultStageMetricRatioBridgeResult deferred(List<String> unsupported) {
            return new ResultStageMetricRatioBridgeResult(STATUS_DEFERRED, null, null, null,
                    List.copyOf(unsupported));
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }

        public Map<String, Object> summary() {
            return plan == null ? Map.of() : plan.summary();
        }

        public SqlGenerationResult wrap(SqlGenerationResult base) {
            if (!ready()) {
                throw RX.throwB("DSL_CTE_RESULT_STAGE_METRIC_RATIO_NOT_SUPPORTED: " + unsupported);
            }
            return plan.wrap(base);
        }
    }

    public record BridgeResult(String status, String model, SemanticQueryRequest request, List<String> unsupported) {
        static BridgeResult ready(String model, SemanticQueryRequest request) {
            return new BridgeResult(STATUS_READY, model, request, List.of());
        }

        static BridgeResult deferred(List<String> unsupported) {
            return new BridgeResult(STATUS_DEFERRED, null, null, List.copyOf(unsupported));
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }

        public void requireReady() {
            if (!ready()) {
                throw RX.throwB("DSL_CTE_DSL_BRIDGE_NOT_SUPPORTED: " + unsupported);
            }
        }
    }
}

package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
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
    public static final String STATUS_CONTRACT_READY = "CONTRACT_READY";
    private static final String FACT_ORDER_EVENT_DATE_FIELD = "orderDate$caption";
    private static final String FACT_ORDER_STAGE_YEAR_FIELD = "orderDate$year";
    private static final String FACT_ORDER_STAGE_MONTH_FIELD = "orderDate$month";
    private static final String FACT_ORDER_TARGET_YEAR_FIELD = "FactOrderQueryModel.orderDate$year";
    private static final String FACT_ORDER_TARGET_MONTH_FIELD = "FactOrderQueryModel.orderDate$month";
    private static final String FACT_ORDER_AMOUNT_FIELD = "amount";
    private static final String FACT_ORDER_STATUS_FIELD = "orderStatus";
    private static final String FACT_ORDER_PAYMENT_STATUS_FIELD = "paymentStatus";
    private static final String ZERO_FILL_TARGET_PERIOD_UNSUPPORTED_MESSAGE =
            "cross-model funnel zero-filled target-month calendar is not signed; "
                    + "use CLARIFY until calendar scaffold and zero policy are explicit";
    private static final int ZERO_FILL_CALENDAR_MAX_PERIODS = 36;
    private static final Pattern YEAR_MONTH_LITERAL_PATTERN = Pattern.compile("^(?:\\d{4}[-/]\\d{1,2}|\\d{6})$");
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
    private static final Pattern SLA_UNRESPONDED_CUTOFF_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+null\\s+and\\s+createdAt\\s*(<|<=)\\s*('(?:\\d{4}-\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?)'|now\\s*\\(\\s*\\))\\s*$");
    private static final Pattern SLA_UNRESPONDED_CUTOFF_REVERSED_PATTERN = Pattern.compile(
            "(?i)^\\s*createdAt\\s*(<|<=)\\s*('(?:\\d{4}-\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?)'|now\\s*\\(\\s*\\))\\s+and\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+null\\s*$");
    private static final Pattern SLA_UNRESPONDED_REFERENCE_HOURS_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+null\\s+and\\s+hours_between\\s*\\(\\s*createdAt\\s*,\\s*'((?:\\d{4}-\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?))'\\s*\\)\\s*(>|>=)\\s*(\\d+(?:\\.\\d+)?)\\s*$");
    private static final Pattern SLA_UNRESPONDED_REFERENCE_HOURS_REVERSED_PATTERN = Pattern.compile(
            "(?i)^\\s*hours_between\\s*\\(\\s*createdAt\\s*,\\s*'((?:\\d{4}-\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(?::\\d{2})?))'\\s*\\)\\s*(>|>=)\\s*(\\d+(?:\\.\\d+)?)\\s+and\\s+([A-Za-z_][A-Za-z0-9_$]*)\\s+is\\s+null\\s*$");
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
    private static final Pattern CASE_WHEN_NOT_NULL_COUNT_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*case\\s+when\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s+is\\s+not\\s+null\\s+then\\s+1\\s+else\\s+0\\s+end\\s*\\)\\s*$");
    private static final Pattern CASE_WHEN_STRING_COMPARE_COUNT_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*case\\s+when\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s*(=|==|!=|<>)\\s*'((?:[^']|'')*)'\\s+then\\s+1\\s+else\\s+0\\s+end\\s*\\)\\s*$");
    private static final Pattern CASE_WHEN_NOT_NULL_VALUE_SUM_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*case\\s+when\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s+is\\s+not\\s+null\\s+then\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s+else\\s+0(?:\\.0+)?\\s+end\\s*\\)\\s*$");
    private static final Pattern CASE_WHEN_STRING_COMPARE_VALUE_SUM_PATTERN = Pattern.compile(
            "(?i)^\\s*sum\\s*\\(\\s*case\\s+when\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s*(=|==|!=|<>)\\s*'((?:[^']|'')*)'\\s+then\\s+([A-Za-z_][A-Za-z0-9_$.]*)\\s+else\\s+0(?:\\.0+)?\\s+end\\s*\\)\\s*$");
    private static final Pattern METRIC_RATIO_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*/\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*$");
    private static final Pattern METRIC_DIFFERENCE_PATTERN = Pattern.compile(
            "(?i)^\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*-\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*$");
    private static final Pattern METRIC_DIFFERENCE_RATIO_PATTERN = Pattern.compile(
            "(?i)^\\s*\\(?\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*-\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*\\)?\\s*/\\s*([A-Za-z_][A-Za-z0-9_$]*)\\s*$");
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
        List<SemanticQueryRequest.OrderItem> resultOrderBy = null;
        Integer resultLimit = null;
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
            } else if ("orderBy".equals(type)) {
                if (i != stages.size() - 1) {
                    unsupported.add("DSL_CTE bridge orderBy stage must be the final result stage in this cut");
                    continue;
                }
                if (resultOrderBy != null) {
                    unsupported.add("DSL_CTE bridge supports only one orderBy stage in this cut");
                    continue;
                }
                List<String> outputFields = stringList(ctePlan.get("output"));
                if (outputFields.isEmpty()) {
                    unsupported.add("DSL_CTE bridge orderBy stage requires top-level output fields");
                    continue;
                }
                resultOrderBy = orderItems(stage.get("orderBy"), outputFields, unsupported);
                resultLimit = limit(stage.get("limit"), unsupported);
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
        request.setCalculatedFields(metrics.calculatedFields().isEmpty() ? null : metrics.calculatedFields());
        request.setPostAggregateCalculations(postAgg.isEmpty() ? null : postAgg);
        request.setPostSlice(postSlice);
        request.setOrderBy(resultOrderBy);
        request.setLimit(resultLimit);
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
        baseRequest.setCalculatedFields(metrics.calculatedFields().isEmpty() ? null : metrics.calculatedFields());
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
        List<String> availableOutput = new ArrayList<>();
        availableOutput.addAll(groupBy);
        availableOutput.add(metricAlias);
        availableOutput.add(cumulative.rankAlias());
        availableOutput.add(cumulative.cumulativeAlias());
        for (String field : output) {
            if (!availableOutput.contains(field)) {
                unsupported.add("result-stage window output references unavailable field: " + field);
            }
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
        if (stages.size() >= 2
                && "aggregate".equals(stringValue(stages.get(0).get("type")))
                && "derive".equals(stringValue(stages.get(1).get("type")))) {
            return toRelationResultStageMetricRatioBridge(fallbackModel, ctePlan, stages);
        }
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
        ResultStageDerivedMetrics derivedMetrics = resultStageMetricDerived(ratioStage.get("derived"), model,
                metrics.aliases(), unsupported);
        if (derivedMetrics.empty()) {
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }
        List<String> derivedAliases = derivedMetrics.aliases();
        List<ResultStageFilter> filters = resultStageAliasFilters(
                stages.size() == 4 ? stages.get(3).get("filters") : null,
                derivedAliases, "result-stage SLA metric ratio bridge", unsupported);
        if (!unsupported.isEmpty()) {
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        SemanticQueryRequest baseRequest = new SemanticQueryRequest();
        baseRequest.setRoute("DSL");
        baseRequest.setStatus("PLAN_READY");
        baseRequest.setGroupBy(groupByItems(aggregate.get("groupBy")));
        baseRequest.setSlice(sliceItems(derive.get("filters"), unsupported));
        baseRequest.setColumns(outputColumns(null, aggregate.get("groupBy"), metrics, List.of(), Map.of(), unsupported));
        baseRequest.setCalculatedFields(mergeCalculatedFields(calculatedFields, metrics.calculatedFields()));
        baseRequest.setReturnTotal(false);

        if (model == null || model.isBlank()) {
            unsupported.add("derive input model must be declared for result-stage SLA metric ratio bridge");
        }
        List<String> groupBy = stringList(aggregate.get("groupBy"));
        List<String> output = stringList(ctePlan.get("output"));
        if (output.isEmpty()) {
            output.addAll(groupBy);
            output.addAll(metrics.aliases());
            output.addAll(derivedAliases);
        }
        if (!allSafeAliases(output, groupBy, metrics.aliases(), derivedAliases)) {
            unsupported.add("result-stage SLA metric ratio output supports only governed field aliases");
        }
        List<String> availableOutput = new ArrayList<>();
        availableOutput.addAll(groupBy);
        availableOutput.addAll(metrics.aliases());
        availableOutput.addAll(derivedAliases);
        for (String field : output) {
            if (!availableOutput.contains(field)) {
                unsupported.add("result-stage SLA metric ratio output references unavailable field: " + field);
            }
        }
        List<SemanticQueryRequest.OrderItem> orderBy = orderItems(ctePlan.get("orderBy"), availableOutput, unsupported);
        Integer resultLimit = limit(ctePlan.get("limit"), unsupported);
        if (!unsupported.isEmpty()) {
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        ResultStageMetricRatioPlan plan = new ResultStageMetricRatioPlan(
                output, groupBy, metrics.aliases(), derivedMetrics.ratios(), derivedMetrics.arithmetic(), filters,
                orderBy == null ? List.of() : orderBy, resultLimit);
        return ResultStageMetricRatioBridgeResult.ready(model, baseRequest, plan);
    }

    private static ResultStageMetricRatioBridgeResult toRelationResultStageMetricRatioBridge(
            String fallbackModel, Map<String, Object> ctePlan, List<Map<String, Object>> stages) {
        List<String> unsupported = new ArrayList<>();
        if (stages.size() < 2 || stages.size() > 8) {
            unsupported.add("relation result-stage metric ratio bridge requires aggregate -> derive+ -> optional postSlice -> optional orderBy");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        Map<String, Object> aggregate = stages.get(0);
        if (!"aggregate".equals(stringValue(aggregate.get("type")))) {
            unsupported.add("relation result-stage metric ratio bridge requires aggregate followed by derive stages");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        int cursor = 1;
        List<Map<String, Object>> deriveStages = new ArrayList<>();
        while (cursor < stages.size() && "derive".equals(stringValue(stages.get(cursor).get("type")))) {
            deriveStages.add(stages.get(cursor++));
        }
        if (deriveStages.isEmpty()) {
            unsupported.add("relation result-stage metric ratio bridge requires at least one derive stage");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }
        Map<String, Object> postSliceStage = null;
        Map<String, Object> orderByStage = null;
        if (cursor < stages.size() && "postSlice".equals(stringValue(stages.get(cursor).get("type")))) {
            postSliceStage = stages.get(cursor++);
        }
        if (cursor < stages.size() && "orderBy".equals(stringValue(stages.get(cursor).get("type")))) {
            orderByStage = stages.get(cursor++);
        }
        if (cursor != stages.size()) {
            unsupported.add("relation result-stage metric ratio bridge supports only aggregate -> derive+ -> optional postSlice -> optional orderBy");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        String aggregateName = stringValue(aggregate.get("name"));
        if (aggregateName == null) {
            unsupported.add("relation result-stage metric ratio bridge requires named aggregate and derive stages");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }
        String previousName = aggregateName;
        for (Map<String, Object> deriveStage : deriveStages) {
            String deriveName = stringValue(deriveStage.get("name"));
            if (deriveName == null || !List.of(previousName).equals(stringList(deriveStage.get("inputs")))) {
                unsupported.add("relation result-stage metric ratio derive stages must form a linear chain");
                return ResultStageMetricRatioBridgeResult.deferred(unsupported);
            }
            previousName = deriveName;
        }
        if (postSliceStage != null) {
            String postSliceName = stringValue(postSliceStage.get("name"));
            if (postSliceName == null || !List.of(previousName).equals(stringList(postSliceStage.get("inputs")))) {
                unsupported.add("relation result-stage metric ratio postSlice must reference the previous result stage");
                return ResultStageMetricRatioBridgeResult.deferred(unsupported);
            }
            previousName = postSliceName;
        }
        if (orderByStage != null && !List.of(previousName).equals(stringList(orderByStage.get("inputs")))) {
            unsupported.add("relation result-stage metric ratio orderBy must reference the previous result stage");
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        String model = sourceModel(fallbackModel, aggregate);
        MetricMapping metrics = metrics(aggregate.get("metrics"), unsupported);
        List<ResultStageDerivedMetrics> derivedMetricStages = new ArrayList<>();
        List<String> derivedAliases = new ArrayList<>();
        List<String> labelAliases = new ArrayList<>();
        List<String> formulaAliases = new ArrayList<>(metrics.aliases());
        for (Map<String, Object> deriveStage : deriveStages) {
            List<ResultStageDerivedMetrics> derivedLayers = RelationResultExpressionCompiler.compileLayered(
                    deriveStage.get("derived"), metrics.aliases(), formulaAliases, derivedAliases, unsupported);
            if (derivedLayers.isEmpty() || derivedLayers.stream().anyMatch(ResultStageDerivedMetrics::empty)) {
                return ResultStageMetricRatioBridgeResult.deferred(unsupported);
            }
            for (ResultStageDerivedMetrics derivedMetrics : derivedLayers) {
                derivedMetricStages.add(derivedMetrics);
                derivedAliases.addAll(derivedMetrics.aliases());
                labelAliases.addAll(derivedMetrics.labelAliases());
                formulaAliases.addAll(derivedMetrics.aliases());
            }
        }
        List<ResultStageFilter> filters = resultStageAliasFilters(
                postSliceStage == null ? null : postSliceStage.get("filters"),
                derivedAliases, labelAliases, "relation result-stage metric ratio bridge", unsupported);

        List<String> groupBy = stringList(aggregate.get("groupBy"));
        List<String> output = stringList(ctePlan.get("output"));
        if (output.isEmpty()) {
            output.addAll(groupBy);
            output.addAll(metrics.aliases());
            output.addAll(derivedAliases);
        }
        List<String> availableOutput = new ArrayList<>();
        availableOutput.addAll(groupBy);
        availableOutput.addAll(metrics.aliases());
        availableOutput.addAll(derivedAliases);
        for (String field : output) {
            if (!availableOutput.contains(field)) {
                unsupported.add("relation result-stage metric ratio output references unavailable field: " + field);
            }
        }

        List<SemanticQueryRequest.OrderItem> orderBy = orderByStage == null
                ? null
                : orderItems(orderByStage.get("orderBy"), output, unsupported);
        Integer resultLimit = orderByStage == null ? null : limit(orderByStage.get("limit"), unsupported);
        if (!allSafeAliases(output, groupBy, metrics.aliases(), derivedAliases)) {
            unsupported.add("relation result-stage metric ratio output supports only governed field aliases");
        }

        SemanticQueryRequest baseRequest = new SemanticQueryRequest();
        baseRequest.setRoute("DSL");
        baseRequest.setStatus("PLAN_READY");
        baseRequest.setGroupBy(groupByItems(aggregate.get("groupBy")));
        baseRequest.setSlice(sliceItems(aggregate.get("filters"), unsupported));
        baseRequest.setColumns(outputColumns(null, aggregate.get("groupBy"), metrics, List.of(), Map.of(), unsupported));
        baseRequest.setCalculatedFields(metrics.calculatedFields().isEmpty() ? null : metrics.calculatedFields());
        baseRequest.setReturnTotal(false);

        if (model == null || model.isBlank()) {
            unsupported.add("aggregate input model must be declared for relation result-stage metric ratio bridge");
        }
        if (!unsupported.isEmpty()) {
            return ResultStageMetricRatioBridgeResult.deferred(unsupported);
        }

        ResultStageMetricRatioPlan plan = new ResultStageMetricRatioPlan(
                output, groupBy, metrics.aliases(),
                derivedMetricStages.stream().flatMap(stage -> stage.ratios().stream()).toList(),
                derivedMetricStages.stream().flatMap(stage -> stage.arithmetic().stream()).toList(),
                derivedMetricStages, filters, orderBy == null ? List.of() : orderBy, resultLimit);
        return ResultStageMetricRatioBridgeResult.ready(model, baseRequest, plan);
    }

    public static CrossModelJoinAlignBridgeResult toCrossModelJoinAlignBridge(String fallbackModel,
                                                                              Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return CrossModelJoinAlignBridgeResult.deferred(unsupported);
        }
        if (declaresCrossModelFunnelTimeAttribution(ctePlan)) {
            unsupported.add("cross-model funnel time-attribution contract is validation-only; join_align compile bridge is not signed");
            return CrossModelJoinAlignBridgeResult.deferred(unsupported);
        }
        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        if (stages.size() != 3) {
            unsupported.add("cross-model join_align bridge requires exactly left aggregate -> right aggregate -> signed join_align; post-join stages remain deferred");
            return CrossModelJoinAlignBridgeResult.deferred(unsupported);
        }

        Map<String, Object> leftAggregate = stages.get(0);
        Map<String, Object> rightAggregate = stages.get(1);
        Map<String, Object> joinAlign = stages.get(2);
        if (!"aggregate".equals(stringValue(leftAggregate.get("type")))
                || !"aggregate".equals(stringValue(rightAggregate.get("type")))
                || !"join_align".equals(stringValue(joinAlign.get("type")))) {
            unsupported.add("cross-model join_align bridge requires aggregate -> aggregate -> join_align");
            return CrossModelJoinAlignBridgeResult.deferred(unsupported);
        }

        String leftStageName = stringValue(leftAggregate.get("name"));
        String rightStageName = stringValue(rightAggregate.get("name"));
        List<String> inputs = stringList(joinAlign.get("inputs"));
        if (leftStageName == null || rightStageName == null
                || !List.of(leftStageName, rightStageName).equals(inputs)) {
            unsupported.add("cross-model join_align bridge requires join inputs to match the two aggregate stages in order");
            return CrossModelJoinAlignBridgeResult.deferred(unsupported);
        }

        String relationRef = stringValue(joinAlign.get("relationRef"));
        String cardinality = stringValue(joinAlign.get("cardinality"));
        if (!"many_to_one".equals(cardinality)) {
            unsupported.add("cross-model join_align bridge supports only signed many_to_one cardinality in this cut");
        }
        if (!"declared_key_align".equals(stringValue(joinAlign.get("joinType")))) {
            unsupported.add("cross-model join_align bridge supports only declared_key_align joinType");
        }

        Map<String, Object> relation = mapValue(joinAlign.get("relation"));
        Map<String, Object> leftEndpoint = relation == null ? null : mapValue(relation.get("left"));
        Map<String, Object> rightEndpoint = relation == null ? null : mapValue(relation.get("right"));
        String leftModel = sourceModel(fallbackModel, leftAggregate);
        String rightModel = sourceModel(null, rightAggregate);
        String leftKey = stringValue(leftEndpoint == null ? null : leftEndpoint.get("field"));
        String rightKey = stringValue(rightEndpoint == null ? null : rightEndpoint.get("field"));
        if (!signedCrmOrderJoinEndpoint(leftEndpoint, leftStageName, "CrmLead", "convertedOrderId")
                || !"CrmLead".equals(leftModel)
                || !signedCrmOrderJoinEndpoint(rightEndpoint, rightStageName, "FactOrderQueryModel", "orderId")
                || !"FactOrderQueryModel".equals(rightModel)
                || !"CrmLead.convertedOrderId -> FactOrderQueryModel.orderId".equals(relationRef)) {
            unsupported.add("cross-model join_align bridge supports only signed CrmLead.convertedOrderId -> FactOrderQueryModel.orderId");
        }
        if (!singleAlignmentKeyMatches(stringList(joinAlign.get("keys")), leftKey, rightKey)) {
            unsupported.add("cross-model join_align bridge requires a single convertedOrderId=orderId alignment key");
        }

        DslCteJoinAlignRuntimeGuardContract runtimeGuard =
                DslCteJoinAlignRuntimeGuardContract.parseNullable(joinAlign.get("runtimeGuard"));
        if (runtimeGuard == null || runtimeGuard.cardinality() == null || runtimeGuard.timeAttribution() == null) {
            unsupported.add("cross-model join_align bridge requires signed cardinality and timeAttribution runtimeGuard");
            return CrossModelJoinAlignBridgeResult.deferred(unsupported);
        }
        DslCteJoinAlignRuntimeGuardContract.Cardinality guardCardinality = runtimeGuard.cardinality();
        if (!"many".equals(guardCardinality.leftMultiplicity())
                || !"one".equals(guardCardinality.rightMultiplicity())) {
            unsupported.add("cross-model join_align bridge runtime cardinality must be many-to-one");
        }
        String nullKeyPolicy = guardCardinality.nullKeyPolicy();
        if (!"exclude_unmatched".equals(nullKeyPolicy) && !"reject_null".equals(nullKeyPolicy)) {
            unsupported.add("cross-model join_align bridge supports only exclude_unmatched or reject_null null key policy");
        }
        DslCteJoinAlignRuntimeGuardContract.TimeAttribution timeGuard = runtimeGuard.timeAttribution();
        if (!leftStageName.equals(timeGuard.sourceStage())) {
            unsupported.add("cross-model join_align bridge requires time attribution sourceStage on the left aggregate");
        }
        if (!stringList(leftAggregate.get("groupBy")).contains(timeGuard.sourceField())) {
            unsupported.add("cross-model join_align bridge runtime time attribution SQL guard requires sourceField in left aggregate groupBy");
        }

        MetricMapping leftMetrics = crossModelJoinAggregateMetrics(leftAggregate, leftModel, true, unsupported);
        MetricMapping rightMetrics = crossModelJoinAggregateMetrics(rightAggregate, rightModel, false, unsupported);
        SemanticQueryRequest leftRequest = aggregateBridgeRequest(leftAggregate, leftMetrics, unsupported);
        SemanticQueryRequest rightRequest = aggregateBridgeRequest(rightAggregate, rightMetrics, unsupported);

        List<String> leftFields = availableFields(null, leftAggregate.get("groupBy"), leftMetrics.aliases());
        List<String> rightFields = availableFields(null, rightAggregate.get("groupBy"), rightMetrics.aliases());
        List<String> joinOutput = stringList(joinAlign.get("output"));
        if (joinOutput.isEmpty()) {
            unsupported.add("cross-model join_align bridge requires signed join output schema");
        }
        for (String field : joinOutput) {
            if (!leftFields.contains(field) && !rightFields.contains(field)) {
                unsupported.add("cross-model join_align output references unavailable field: " + field);
            }
        }
        List<String> output = stringList(ctePlan.get("output"));
        if (output.isEmpty()) {
            output.addAll(joinOutput);
        }
        for (String field : output) {
            if (!joinOutput.contains(field)) {
                unsupported.add("cross-model join_align bridge output must be a subset of signed join output: " + field);
            }
        }
        if (!allSafeAliases(leftFields, rightFields, joinOutput, output)) {
            unsupported.add("cross-model join_align bridge supports only governed field aliases");
        }
        if (!unsupported.isEmpty()) {
            return CrossModelJoinAlignBridgeResult.deferred(unsupported);
        }

        CrossModelJoinAlignPlan plan = new CrossModelJoinAlignPlan(
                output,
                joinOutput,
                leftFields,
                rightFields,
                leftKey,
                rightKey,
                leftMetrics.aliases().get(0),
                rightMetrics.aliases().get(0),
                timeGuard.sourceField(),
                "reject_null".equals(nullKeyPolicy),
                relationRef,
                cardinality,
                nullKeyPolicy);
        return CrossModelJoinAlignBridgeResult.ready(leftModel, leftRequest, rightModel, rightRequest, plan);
    }

    public static CrossModelFunnelTimeAttributionContractResult toCrossModelFunnelTimeAttributionContract(
            String fallbackModel, Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return CrossModelFunnelTimeAttributionContractResult.deferred(false, unsupported);
        }
        Map<String, Object> contract = mapValue(ctePlan.get("timeAttributionContract"));
        if (contract == null) {
            return CrossModelFunnelTimeAttributionContractResult.deferred(false, List.of());
        }
        if (!"source_cohort_target_event_window".equals(stringValue(contract.get("kind")))) {
            unsupported.add("cross-model funnel time-attribution contract kind must be source_cohort_target_event_window");
        }
        if (contract.containsKey("qualityMetric")
                || contract.containsKey("amountAttribution")
                || contract.containsKey("orderSelection")) {
            unsupported.add("cross-model funnel time-attribution contract does not sign quality, amount, or order-selection attribution");
        }

        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        if (stages.size() != 6) {
            unsupported.add("cross-model funnel time-attribution contract requires signed join_align plus matched numerator, denominator, and final rate stages");
            return CrossModelFunnelTimeAttributionContractResult.deferred(
                    true, unsupported, declaresTargetPeriodFunnelAttribution(ctePlan));
        }

        Map<String, Object> leftAggregate = stages.get(0);
        Map<String, Object> rightAggregate = stages.get(1);
        Map<String, Object> joinAlign = stages.get(2);
        Map<String, Object> matchedAggregate = stages.get(3);
        Map<String, Object> denominatorAggregate = stages.get(4);
        Map<String, Object> finalDerive = stages.get(5);
        if (!"aggregate".equals(stringValue(leftAggregate.get("type")))
                || !"aggregate".equals(stringValue(rightAggregate.get("type")))
                || !"join_align".equals(stringValue(joinAlign.get("type")))
                || !"aggregate".equals(stringValue(matchedAggregate.get("type")))
                || !"aggregate".equals(stringValue(denominatorAggregate.get("type")))
                || !"derive".equals(stringValue(finalDerive.get("type")))) {
            unsupported.add("cross-model funnel time-attribution contract requires aggregate -> aggregate -> join_align -> aggregate -> aggregate -> derive");
            return CrossModelFunnelTimeAttributionContractResult.deferred(
                    true, unsupported, declaresTargetPeriodFunnelAttribution(ctePlan));
        }

        String leftStageName = stringValue(leftAggregate.get("name"));
        String rightStageName = stringValue(rightAggregate.get("name"));
        String joinStageName = stringValue(joinAlign.get("name"));
        String matchedStageName = stringValue(matchedAggregate.get("name"));
        String denominatorStageName = stringValue(denominatorAggregate.get("name"));
        String leftModel = sourceModel(fallbackModel, leftAggregate);
        String rightModel = sourceModel(null, rightAggregate);
        String denominatorModel = sourceModel(fallbackModel, denominatorAggregate);

        Map<String, Object> source = mapValue(contract.get("source"));
        Map<String, Object> target = mapValue(contract.get("target"));
        String sourceStage = stringValue(source == null ? null : source.get("stage"));
        String sourceModel = stringValue(source == null ? null : source.get("model"));
        String sourceField = stringValue(source == null ? null : source.get("field"));
        String targetStage = stringValue(target == null ? null : target.get("stage"));
        String targetModel = stringValue(target == null ? null : target.get("model"));
        String targetField = stringValue(target == null ? null : target.get("field"));
        if (leftStageName == null
                || !leftStageName.equals(sourceStage)
                || !"CrmLead".equals(sourceModel)
                || !"createdAt".equals(sourceField)
                || !"CrmLead".equals(leftModel)) {
            unsupported.add("cross-model funnel time-attribution contract source cohort must be CrmLead.createdAt on the left aggregate");
        }
        if (rightStageName == null
                || !rightStageName.equals(targetStage)
                || !"FactOrderQueryModel".equals(targetModel)
                || !FACT_ORDER_EVENT_DATE_FIELD.equals(targetField)
                || !"FactOrderQueryModel".equals(rightModel)) {
            unsupported.add("cross-model funnel time-attribution contract target event field must be FactOrderQueryModel.orderDate$caption on the right aggregate");
        }
        if (!stringList(leftAggregate.get("groupBy")).contains("createdAt")) {
            unsupported.add("cross-model funnel time-attribution contract requires source field createdAt in the left aggregate groupBy");
        }
        if (!stringList(rightAggregate.get("groupBy")).contains(FACT_ORDER_EVENT_DATE_FIELD)) {
            unsupported.add("cross-model funnel time-attribution contract requires target event field orderDate$caption in the right aggregate groupBy");
        }
        TargetPeriodContract targetPeriod = targetPeriodContract(ctePlan, contract, matchedAggregate, unsupported);
        boolean targetPeriodAttribution = targetPeriod.declared();
        ZeroFillCalendarScaffoldContract zeroFillCalendarScaffold =
                zeroFillCalendarScaffoldContract(ctePlan, targetPeriod, unsupported);
        if (targetPeriodAttribution
                && !stringList(rightAggregate.get("groupBy")).containsAll(targetPeriod.stageFields())) {
            unsupported.add("cross-model funnel target-period attribution requires target period fields "
                    + targetPeriod.stageFields() + " in the right aggregate groupBy");
        }

        Map<String, Object> window = mapValue(contract.get("window"));
        Integer windowDays = timeAttributionWindowDays(window);
        String order = stringValue(window == null ? null : window.get("order"));
        if (windowDays == null || windowDays <= 0 || !"source_at_or_before_target".equals(order)) {
            unsupported.add("cross-model funnel time-attribution contract requires a positive day conversion window with source_at_or_before_target order");
        }

        String relationRef = stringValue(joinAlign.get("relationRef"));
        String cardinality = stringValue(joinAlign.get("cardinality"));
        if (leftStageName == null || rightStageName == null
                || !List.of(leftStageName, rightStageName).equals(stringList(joinAlign.get("inputs")))) {
            unsupported.add("cross-model funnel time-attribution contract requires join inputs to match source then target aggregate stages");
        }
        if (!"many_to_one".equals(cardinality)) {
            unsupported.add("cross-model funnel time-attribution contract supports only signed many_to_one cardinality in this cut");
        }
        if (!"declared_key_align".equals(stringValue(joinAlign.get("joinType")))) {
            unsupported.add("cross-model funnel time-attribution contract supports only declared_key_align joinType");
        }
        Map<String, Object> relation = mapValue(joinAlign.get("relation"));
        Map<String, Object> leftEndpoint = relation == null ? null : mapValue(relation.get("left"));
        Map<String, Object> rightEndpoint = relation == null ? null : mapValue(relation.get("right"));
        String leftKey = stringValue(leftEndpoint == null ? null : leftEndpoint.get("field"));
        String rightKey = stringValue(rightEndpoint == null ? null : rightEndpoint.get("field"));
        if (!signedCrmOrderJoinEndpoint(leftEndpoint, leftStageName, "CrmLead", "convertedOrderId")
                || !signedCrmOrderJoinEndpoint(rightEndpoint, rightStageName, "FactOrderQueryModel", "orderId")
                || !"CrmLead.convertedOrderId -> FactOrderQueryModel.orderId".equals(relationRef)) {
            unsupported.add("cross-model funnel time-attribution contract supports only signed CrmLead.convertedOrderId -> FactOrderQueryModel.orderId");
        }
        if (!singleAlignmentKeyMatches(stringList(joinAlign.get("keys")), leftKey, rightKey)) {
            unsupported.add("cross-model funnel time-attribution contract requires a single convertedOrderId=orderId alignment key");
        }

        DslCteJoinAlignRuntimeGuardContract runtimeGuard =
                DslCteJoinAlignRuntimeGuardContract.parseNullable(joinAlign.get("runtimeGuard"));
        if (runtimeGuard == null || runtimeGuard.cardinality() == null || runtimeGuard.timeAttribution() == null) {
            unsupported.add("cross-model funnel time-attribution contract requires signed cardinality and timeAttribution runtimeGuard");
        } else {
            DslCteJoinAlignRuntimeGuardContract.TimeAttribution timeGuard = runtimeGuard.timeAttribution();
            if (leftStageName == null || !leftStageName.equals(timeGuard.sourceStage())
                    || !"createdAt".equals(timeGuard.sourceField())) {
                unsupported.add("cross-model funnel time-attribution contract runtime guard must bind source event CrmLead.createdAt");
            }
            if (rightStageName == null || !rightStageName.equals(timeGuard.targetStage())
                    || !FACT_ORDER_EVENT_DATE_FIELD.equals(timeGuard.targetField())) {
                unsupported.add("cross-model funnel time-attribution contract runtime guard target event field must be FactOrderQueryModel.orderDate$caption");
            }
            if (!"source_at_or_before_target".equals(timeGuard.order())) {
                unsupported.add("cross-model funnel time-attribution contract runtime guard must require source_at_or_before_target order");
            }
        }

        if (joinStageName == null || !List.of(joinStageName).equals(stringList(matchedAggregate.get("inputs")))) {
            unsupported.add("cross-model funnel time-attribution numerator must aggregate the signed join_align stage");
        }
        List<String> matchedGroupBy = stringList(matchedAggregate.get("groupBy"));
        List<String> denominatorGroupBy = stringList(denominatorAggregate.get("groupBy"));
        if (targetPeriodAttribution) {
            if (!expectedTargetPeriodGroupBy("leadSource", targetPeriod.stageFields()).equals(matchedGroupBy)) {
                unsupported.add("cross-model funnel target-month attribution numerator must group by leadSource and targetPeriod only");
            }
            if (!List.of("leadSource").equals(denominatorGroupBy)) {
                unsupported.add("cross-model funnel target-month attribution denominator must stay fixed at leadSource only");
            }
        } else if (!List.of("leadSource").equals(matchedGroupBy)
                || !List.of("leadSource").equals(denominatorGroupBy)) {
            unsupported.add("cross-model funnel time-attribution numerator and denominator must group by leadSource only");
        }
        if (denominatorStageName == null || matchedStageName == null
                || !List.of(denominatorStageName, matchedStageName).equals(stringList(finalDerive.get("inputs")))) {
            unsupported.add("cross-model funnel time-attribution final derive must combine denominator then matched numerator");
        }
        if (!"CrmLead".equals(denominatorModel)) {
            unsupported.add("cross-model funnel time-attribution denominator must use CrmLead");
        }
        if (!sameFilterSet(leftAggregate.get("filters"), denominatorAggregate.get("filters"))) {
            unsupported.add("cross-model funnel time-attribution numerator and denominator must use the same lead source filters");
        }

        MetricMapping denominatorMetrics = crossModelFunnelDenominatorMetrics(denominatorAggregate, denominatorModel,
                unsupported);
        String matchedMetric = crossModelFunnelMatchedMetric(matchedAggregate, unsupported);
        String rateAlias = crossModelFunnelRateAlias(finalDerive, unsupported);
        List<String> output = stringList(ctePlan.get("output"));
        if (output.isEmpty()) {
            output.add("leadSource");
            if (targetPeriodAttribution) {
                output.addAll(targetPeriod.stageFields());
            }
            output.add("totalLeadCount");
            output.add(matchedMetric);
            output.add(rateAlias);
        }
        List<String> available = new ArrayList<>(List.of("leadSource", "totalLeadCount", matchedMetric, rateAlias));
        if (targetPeriodAttribution) {
            available.addAll(1, targetPeriod.stageFields());
        }
        for (String field : output) {
            if (!available.contains(field)) {
                unsupported.add("cross-model funnel time-attribution output references unavailable field: " + field);
            }
        }
        if (!allSafeAliases(output, available)) {
            unsupported.add("cross-model funnel time-attribution contract supports only governed field aliases");
        }
        if (!unsupported.isEmpty()) {
            return CrossModelFunnelTimeAttributionContractResult.deferred(
                    true, unsupported, targetPeriodAttribution);
        }

        CrossModelFunnelTimeAttributionContractPlan plan = new CrossModelFunnelTimeAttributionContractPlan(
                output,
                relationRef,
                cardinality,
                leftStageName,
                leftModel,
                "createdAt",
                rightStageName,
                rightModel,
                FACT_ORDER_EVENT_DATE_FIELD,
                windowDays,
                order,
                "leadSource",
                denominatorMetrics.aliases().get(0),
                matchedMetric,
                rateAlias,
                targetPeriodAttribution ? targetPeriod.semanticField() : null,
                targetPeriodAttribution ? targetPeriod.grain() : null,
                targetPeriodAttribution ? targetPeriod.stageFields() : List.of(),
                targetPeriodAttribution ? targetPeriod.outputFields() : List.of(),
                zeroFillCalendarScaffold);
        return CrossModelFunnelTimeAttributionContractResult.ready(plan);
    }

    public static CrossModelFunnelTimeAttributionBridgeResult toCrossModelFunnelTimeAttributionBridge(
            String fallbackModel, Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return CrossModelFunnelTimeAttributionBridgeResult.deferred(false, unsupported);
        }
        if (!declaresCrossModelFunnelTimeAttribution(ctePlan)) {
            return CrossModelFunnelTimeAttributionBridgeResult.deferred(false, List.of());
        }

        CrossModelFunnelTimeAttributionContractResult contract =
                toCrossModelFunnelTimeAttributionContract(fallbackModel, executablePlan);
        if (!contract.ready()) {
            return CrossModelFunnelTimeAttributionBridgeResult.deferred(true, contract.unsupported());
        }

        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        Map<String, Object> leftAggregate = stages.get(0);
        Map<String, Object> rightAggregate = stages.get(1);
        Map<String, Object> joinAlign = stages.get(2);
        Map<String, Object> denominatorAggregate = stages.get(4);

        String leftStageName = stringValue(leftAggregate.get("name"));
        String rightStageName = stringValue(rightAggregate.get("name"));
        String leftModel = sourceModel(fallbackModel, leftAggregate);
        String rightModel = sourceModel(null, rightAggregate);
        String denominatorModel = sourceModel(fallbackModel, denominatorAggregate);

        Map<String, Object> relation = mapValue(joinAlign.get("relation"));
        Map<String, Object> leftEndpoint = relation == null ? null : mapValue(relation.get("left"));
        Map<String, Object> rightEndpoint = relation == null ? null : mapValue(relation.get("right"));
        String leftKey = stringValue(leftEndpoint == null ? null : leftEndpoint.get("field"));
        String rightKey = stringValue(rightEndpoint == null ? null : rightEndpoint.get("field"));
        DslCteJoinAlignRuntimeGuardContract runtimeGuard =
                DslCteJoinAlignRuntimeGuardContract.parseNullable(joinAlign.get("runtimeGuard"));
        DslCteJoinAlignRuntimeGuardContract.Cardinality cardinality =
                runtimeGuard == null ? null : runtimeGuard.cardinality();
        String nullKeyPolicy = cardinality == null ? null : cardinality.nullKeyPolicy();
        boolean rejectNullLeftKeys = "reject_null".equals(nullKeyPolicy);

        if (cardinality == null
                || !"many".equals(cardinality.leftMultiplicity())
                || !"one".equals(cardinality.rightMultiplicity())) {
            unsupported.add("cross-model funnel time-attribution bridge runtime cardinality must be many-to-one");
        }
        if (!"exclude_unmatched".equals(nullKeyPolicy) && !"reject_null".equals(nullKeyPolicy)) {
            unsupported.add("cross-model funnel time-attribution bridge supports only exclude_unmatched or reject_null null key policy");
        }

        MetricMapping leftMetrics = crossModelJoinAggregateMetrics(leftAggregate, leftModel, true, unsupported);
        MetricMapping rightMetrics = crossModelJoinAggregateMetrics(rightAggregate, rightModel, false, unsupported);
        MetricMapping denominatorMetrics =
                crossModelFunnelDenominatorMetrics(denominatorAggregate, denominatorModel, unsupported);
        SemanticQueryRequest leftRequest = aggregateBridgeRequest(leftAggregate, leftMetrics, unsupported);
        SemanticQueryRequest rightRequest = aggregateBridgeRequest(rightAggregate, rightMetrics, unsupported);
        SemanticQueryRequest denominatorRequest =
                aggregateBridgeRequest(denominatorAggregate, denominatorMetrics, unsupported);

        List<String> leftFields = availableFields(null, leftAggregate.get("groupBy"), leftMetrics.aliases());
        List<String> rightFields = availableFields(null, rightAggregate.get("groupBy"), rightMetrics.aliases());
        List<String> joinOutput = stringList(joinAlign.get("output"));
        if (joinOutput.isEmpty()) {
            unsupported.add("cross-model funnel time-attribution bridge requires signed join output schema");
        }
        for (String field : joinOutput) {
            if (!leftFields.contains(field) && !rightFields.contains(field)) {
                unsupported.add("cross-model funnel time-attribution join output references unavailable field: " + field);
            }
        }
        CrossModelFunnelTimeAttributionContractPlan contractPlan = contract.plan();
        if (!leftFields.contains(contractPlan.sourceField())) {
            unsupported.add("cross-model funnel time-attribution bridge requires source event field in left SQL output");
        }
        if (!rightFields.contains(contractPlan.targetField())) {
            unsupported.add("cross-model funnel time-attribution bridge requires target event field in right SQL output");
        }
        if (!joinOutput.contains(contractPlan.sourceField()) || !joinOutput.contains(contractPlan.targetField())) {
            unsupported.add("cross-model funnel time-attribution bridge requires source and target event fields in join output");
        }
        if (contractPlan.targetPeriodAttribution()) {
            if (!rightFields.containsAll(contractPlan.targetPeriodStageFields())) {
                unsupported.add("cross-model funnel target attribution bridge requires target period fields "
                        + contractPlan.targetPeriodStageFields() + " in right SQL output");
            }
            if (!joinOutput.containsAll(contractPlan.targetPeriodStageFields())) {
                unsupported.add("cross-model funnel target attribution bridge requires target period fields "
                        + contractPlan.targetPeriodStageFields() + " in join output");
            }
        }
        if (!allSafeAliases(leftFields, rightFields, joinOutput, contractPlan.output())) {
            unsupported.add("cross-model funnel time-attribution bridge supports only governed field aliases");
        }
        if (!unsupported.isEmpty()) {
            return CrossModelFunnelTimeAttributionBridgeResult.deferred(true, unsupported);
        }

        CrossModelJoinAlignPlan joinPlan = new CrossModelJoinAlignPlan(
                joinOutput,
                joinOutput,
                leftFields,
                rightFields,
                leftKey,
                rightKey,
                leftMetrics.aliases().get(0),
                rightMetrics.aliases().get(0),
                contractPlan.sourceField(),
                rejectNullLeftKeys,
                contractPlan.relationRef(),
                contractPlan.cardinality(),
                nullKeyPolicy);
        CrossModelFunnelTimeAttributionBridgePlan plan = new CrossModelFunnelTimeAttributionBridgePlan(
                contractPlan.output(),
                joinPlan,
                contractPlan.targetField(),
                contractPlan.windowDays(),
                contractPlan.windowOrder(),
                contractPlan.groupKey(),
                contractPlan.denominatorMetric(),
                contractPlan.matchedMetric(),
                contractPlan.rateAlias(),
                contractPlan.targetPeriodStageFields(),
                contractPlan.targetPeriodGrain(),
                contractPlan.targetPeriodOutputFields(),
                contractPlan.zeroFillCalendarScaffold());
        return CrossModelFunnelTimeAttributionBridgeResult.ready(denominatorModel, denominatorRequest,
                leftStageName, leftModel, leftRequest, rightStageName, rightModel, rightRequest, plan);
    }

    public static CrossModelFunnelMoneyAttributionContractResult toCrossModelFunnelMoneyAttributionContract(
            String fallbackModel, Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return CrossModelFunnelMoneyAttributionContractResult.deferred(false, unsupported);
        }
        Map<String, Object> contract = mapValue(ctePlan.get("moneyAttributionContract"));
        if (contract == null) {
            return CrossModelFunnelMoneyAttributionContractResult.deferred(false, List.of());
        }
        if (!"source_cohort_target_year_month_converted_amount".equals(stringValue(contract.get("kind")))) {
            unsupported.add("cross-model funnel money-attribution contract kind must be source_cohort_target_year_month_converted_amount");
        }

        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        if (stages.size() != 4) {
            unsupported.add("cross-model funnel money-attribution contract requires signed source aggregate, completed-paid order aggregate, join_align, and final amount aggregate");
            return CrossModelFunnelMoneyAttributionContractResult.deferred(true, unsupported);
        }

        Map<String, Object> leftAggregate = stages.get(0);
        Map<String, Object> rightAggregate = stages.get(1);
        Map<String, Object> joinAlign = stages.get(2);
        Map<String, Object> amountAggregate = stages.get(3);
        if (!"aggregate".equals(stringValue(leftAggregate.get("type")))
                || !"aggregate".equals(stringValue(rightAggregate.get("type")))
                || !"join_align".equals(stringValue(joinAlign.get("type")))
                || !"aggregate".equals(stringValue(amountAggregate.get("type")))) {
            unsupported.add("cross-model funnel money-attribution contract requires aggregate -> aggregate -> join_align -> aggregate");
            return CrossModelFunnelMoneyAttributionContractResult.deferred(true, unsupported);
        }

        String leftStageName = stringValue(leftAggregate.get("name"));
        String rightStageName = stringValue(rightAggregate.get("name"));
        String joinStageName = stringValue(joinAlign.get("name"));
        String leftModel = sourceModel(fallbackModel, leftAggregate);
        String rightModel = sourceModel(null, rightAggregate);

        Map<String, Object> source = mapValue(contract.get("source"));
        Map<String, Object> target = mapValue(contract.get("target"));
        String sourceStage = stringValue(source == null ? null : source.get("stage"));
        String sourceModel = stringValue(source == null ? null : source.get("model"));
        String sourceField = stringValue(source == null ? null : source.get("field"));
        String targetStage = stringValue(target == null ? null : target.get("stage"));
        String targetModel = stringValue(target == null ? null : target.get("model"));
        String targetField = stringValue(target == null ? null : target.get("field"));
        if (leftStageName == null
                || !leftStageName.equals(sourceStage)
                || !"CrmLead".equals(sourceModel)
                || !"createdAt".equals(sourceField)
                || !"CrmLead".equals(leftModel)) {
            unsupported.add("cross-model funnel money-attribution contract source cohort must be CrmLead.createdAt on the left aggregate");
        }
        if (rightStageName == null
                || !rightStageName.equals(targetStage)
                || !"FactOrderQueryModel".equals(targetModel)
                || !FACT_ORDER_EVENT_DATE_FIELD.equals(targetField)
                || !"FactOrderQueryModel".equals(rightModel)) {
            unsupported.add("cross-model funnel money-attribution contract target event field must be FactOrderQueryModel.orderDate$caption on the right aggregate");
        }
        if (!stringList(leftAggregate.get("groupBy")).contains("createdAt")) {
            unsupported.add("cross-model funnel money-attribution contract requires source field createdAt in the left aggregate groupBy");
        }
        if (!stringList(rightAggregate.get("groupBy")).contains(FACT_ORDER_EVENT_DATE_FIELD)) {
            unsupported.add("cross-model funnel money-attribution contract requires target event field orderDate$caption in the right aggregate groupBy");
        }
        TargetPeriodContract targetPeriod = targetPeriodContract(ctePlan, contract, amountAggregate, unsupported);
        if (!targetPeriod.declared() || !"year_month".equals(targetPeriod.grain())) {
            unsupported.add("cross-model funnel money-attribution contract requires explicit year_month targetPeriod on FactOrderQueryModel.orderDate");
        }
        if (targetPeriod.declared()
                && !stringList(rightAggregate.get("groupBy")).containsAll(targetPeriod.stageFields())) {
            unsupported.add("cross-model funnel money-attribution requires target period fields "
                    + targetPeriod.stageFields() + " in the right aggregate groupBy");
        }
        if (mapValue(ctePlan.get("calendarScaffold")) != null || usesZeroFilledTargetPeriodCalendar(ctePlan)) {
            unsupported.add("cross-model funnel money-attribution does not sign zero-filled target-period calendars in this cut");
        }

        Map<String, Object> window = mapValue(contract.get("window"));
        Integer windowDays = timeAttributionWindowDays(window);
        String order = stringValue(window == null ? null : window.get("order"));
        if (windowDays == null || windowDays <= 0 || !"source_at_or_before_target".equals(order)) {
            unsupported.add("cross-model funnel money-attribution contract requires a positive day conversion window with source_at_or_before_target order");
        }

        String relationRef = stringValue(joinAlign.get("relationRef"));
        String cardinality = stringValue(joinAlign.get("cardinality"));
        if (leftStageName == null || rightStageName == null
                || !List.of(leftStageName, rightStageName).equals(stringList(joinAlign.get("inputs")))) {
            unsupported.add("cross-model funnel money-attribution contract requires join inputs to match source then target aggregate stages");
        }
        if (!"many_to_one".equals(cardinality)) {
            unsupported.add("cross-model funnel money-attribution contract supports only signed many_to_one cardinality in this cut");
        }
        if (!"declared_key_align".equals(stringValue(joinAlign.get("joinType")))) {
            unsupported.add("cross-model funnel money-attribution contract supports only declared_key_align joinType");
        }
        Map<String, Object> relation = mapValue(joinAlign.get("relation"));
        Map<String, Object> leftEndpoint = relation == null ? null : mapValue(relation.get("left"));
        Map<String, Object> rightEndpoint = relation == null ? null : mapValue(relation.get("right"));
        String leftKey = stringValue(leftEndpoint == null ? null : leftEndpoint.get("field"));
        String rightKey = stringValue(rightEndpoint == null ? null : rightEndpoint.get("field"));
        if (!signedCrmOrderJoinEndpoint(leftEndpoint, leftStageName, "CrmLead", "convertedOrderId")
                || !signedCrmOrderJoinEndpoint(rightEndpoint, rightStageName, "FactOrderQueryModel", "orderId")
                || !"CrmLead.convertedOrderId -> FactOrderQueryModel.orderId".equals(relationRef)) {
            unsupported.add("cross-model funnel money-attribution contract supports only signed CrmLead.convertedOrderId -> FactOrderQueryModel.orderId");
        }
        if (!singleAlignmentKeyMatches(stringList(joinAlign.get("keys")), leftKey, rightKey)) {
            unsupported.add("cross-model funnel money-attribution contract requires a single convertedOrderId=orderId alignment key");
        }

        DslCteJoinAlignRuntimeGuardContract runtimeGuard =
                DslCteJoinAlignRuntimeGuardContract.parseNullable(joinAlign.get("runtimeGuard"));
        if (runtimeGuard == null || runtimeGuard.cardinality() == null || runtimeGuard.timeAttribution() == null) {
            unsupported.add("cross-model funnel money-attribution contract requires signed cardinality and timeAttribution runtimeGuard");
        } else {
            DslCteJoinAlignRuntimeGuardContract.TimeAttribution timeGuard = runtimeGuard.timeAttribution();
            if (leftStageName == null || !leftStageName.equals(timeGuard.sourceStage())
                    || !"createdAt".equals(timeGuard.sourceField())) {
                unsupported.add("cross-model funnel money-attribution contract runtime guard must bind source event CrmLead.createdAt");
            }
            if (rightStageName == null || !rightStageName.equals(timeGuard.targetStage())
                    || !FACT_ORDER_EVENT_DATE_FIELD.equals(timeGuard.targetField())) {
                unsupported.add("cross-model funnel money-attribution contract runtime guard target event field must be FactOrderQueryModel.orderDate$caption");
            }
            if (!"source_at_or_before_target".equals(timeGuard.order())) {
                unsupported.add("cross-model funnel money-attribution contract runtime guard must require source_at_or_before_target order");
            }
        }

        if (!completedPaidOrderScope(rightAggregate.get("filters"))) {
            unsupported.add("cross-model funnel money-attribution requires completed_paid_orders scope: orderStatus=COMPLETED and paymentStatus=PAID");
        }
        if (!"converted_order_id_only".equals(stringValue(contract.get("orderSelection")))) {
            unsupported.add("cross-model funnel money-attribution orderSelection must be converted_order_id_only");
        }
        if (!"dedupe_order_id_after_signed_relation".equals(stringValue(contract.get("deduplication")))) {
            unsupported.add("cross-model funnel money-attribution deduplication must be dedupe_order_id_after_signed_relation");
        }
        if (!"completed_paid_orders".equals(stringValue(contract.get("orderStatusScope")))) {
            unsupported.add("cross-model funnel money-attribution orderStatusScope must be completed_paid_orders");
        }
        if (!"single_currency_no_conversion".equals(stringValue(contract.get("currencyScope")))) {
            unsupported.add("cross-model funnel money-attribution currencyScope must be single_currency_no_conversion");
        }
        validateMoneyAttributionAmountContract(contract, unsupported);

        if (joinStageName == null || !List.of(joinStageName).equals(stringList(amountAggregate.get("inputs")))) {
            unsupported.add("cross-model funnel money-attribution final amount aggregate must aggregate the signed join_align stage");
        }
        if (!expectedTargetPeriodGroupBy("leadSource", targetPeriod.stageFields())
                .equals(stringList(amountAggregate.get("groupBy")))) {
            unsupported.add("cross-model funnel money-attribution final amount aggregate must group by leadSource and target year-month only");
        }

        MetricMapping leftMetrics = crossModelJoinAggregateMetrics(leftAggregate, leftModel, true, unsupported);
        MetricMapping rightMetrics = crossModelMoneyAttributionRightMetrics(rightAggregate, rightModel, unsupported);
        String amountMetric = crossModelMoneyAttributionMetric(amountAggregate, unsupported);
        MoneyAmountShareContract amountShareContract = moneyAmountShareContract(ctePlan, amountMetric, unsupported);
        MoneyAmountPerLeadContract amountPerLeadContract =
                moneyAmountPerLeadContract(ctePlan, amountMetric, unsupported);
        if (mapValue(ctePlan.get("moneyDerivedMetricContract")) != null
                && !amountShareContract.declared()
                && !amountPerLeadContract.declared()) {
            unsupported.add("cross-model funnel money-derived contract kind must be "
                    + "source_cohort_target_year_month_amount_share or "
                    + "source_cohort_target_year_month_amount_per_lead");
        }
        List<String> output = stringList(ctePlan.get("output"));
        if (output.isEmpty()) {
            output.add("leadSource");
            output.addAll(targetPeriod.stageFields());
            if (amountShareContract.declared()
                    && amountShareContract.denominatorMetric() != null
                    && amountShareContract.ratioAlias() != null) {
                output.add(amountMetric);
                output.add(amountShareContract.denominatorMetric());
                output.add(amountShareContract.ratioAlias());
            } else if (amountPerLeadContract.declared()
                    && amountPerLeadContract.denominatorMetric() != null
                    && amountPerLeadContract.ratioAlias() != null) {
                output.add(amountMetric);
                output.add(amountPerLeadContract.denominatorMetric());
                output.add(amountPerLeadContract.ratioAlias());
            } else {
                output.add(amountMetric);
            }
        }
        List<String> available = new ArrayList<>(expectedTargetPeriodGroupBy("leadSource", targetPeriod.stageFields()));
        available.add(amountMetric);
        if (amountShareContract.declared()) {
            if (amountShareContract.denominatorMetric() != null) {
                available.add(amountShareContract.denominatorMetric());
            }
            if (amountShareContract.ratioAlias() != null) {
                available.add(amountShareContract.ratioAlias());
            }
        }
        if (amountPerLeadContract.declared()) {
            if (amountPerLeadContract.denominatorMetric() != null) {
                available.add(amountPerLeadContract.denominatorMetric());
            }
            if (amountPerLeadContract.ratioAlias() != null) {
                available.add(amountPerLeadContract.ratioAlias());
            }
        }
        for (String field : output) {
            if (!available.contains(field)) {
                unsupported.add("cross-model funnel money-attribution output references unavailable field: " + field);
            }
        }
        if (!allSafeAliases(output, available)) {
            unsupported.add("cross-model funnel money-attribution contract supports only governed field aliases");
        }
        if (!unsupported.isEmpty()) {
            return CrossModelFunnelMoneyAttributionContractResult.deferred(true, unsupported);
        }

        CrossModelFunnelMoneyAttributionContractPlan plan = new CrossModelFunnelMoneyAttributionContractPlan(
                output,
                relationRef,
                cardinality,
                leftStageName,
                leftModel,
                "createdAt",
                rightStageName,
                rightModel,
                FACT_ORDER_EVENT_DATE_FIELD,
                windowDays,
                order,
                "leadSource",
                rightMetrics.aliases().get(1),
                amountMetric,
                targetPeriod.semanticField(),
                targetPeriod.grain(),
                targetPeriod.stageFields(),
                targetPeriod.outputFields(),
                amountShareContract,
                amountPerLeadContract);
        return CrossModelFunnelMoneyAttributionContractResult.ready(plan);
    }

    public static CrossModelFunnelMoneyAttributionBridgeResult toCrossModelFunnelMoneyAttributionBridge(
            String fallbackModel, Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return CrossModelFunnelMoneyAttributionBridgeResult.deferred(false, unsupported);
        }
        if (!declaresCrossModelFunnelMoneyAttribution(ctePlan)) {
            return CrossModelFunnelMoneyAttributionBridgeResult.deferred(false, List.of());
        }

        CrossModelFunnelMoneyAttributionContractResult contract =
                toCrossModelFunnelMoneyAttributionContract(fallbackModel, executablePlan);
        if (!contract.ready()) {
            return CrossModelFunnelMoneyAttributionBridgeResult.deferred(true, contract.unsupported());
        }

        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        Map<String, Object> leftAggregate = stages.get(0);
        Map<String, Object> rightAggregate = stages.get(1);
        Map<String, Object> joinAlign = stages.get(2);

        String leftStageName = stringValue(leftAggregate.get("name"));
        String rightStageName = stringValue(rightAggregate.get("name"));
        String leftModel = sourceModel(fallbackModel, leftAggregate);
        String rightModel = sourceModel(null, rightAggregate);

        Map<String, Object> relation = mapValue(joinAlign.get("relation"));
        Map<String, Object> leftEndpoint = relation == null ? null : mapValue(relation.get("left"));
        Map<String, Object> rightEndpoint = relation == null ? null : mapValue(relation.get("right"));
        String leftKey = stringValue(leftEndpoint == null ? null : leftEndpoint.get("field"));
        String rightKey = stringValue(rightEndpoint == null ? null : rightEndpoint.get("field"));
        DslCteJoinAlignRuntimeGuardContract runtimeGuard =
                DslCteJoinAlignRuntimeGuardContract.parseNullable(joinAlign.get("runtimeGuard"));
        DslCteJoinAlignRuntimeGuardContract.Cardinality cardinality =
                runtimeGuard == null ? null : runtimeGuard.cardinality();
        String nullKeyPolicy = cardinality == null ? null : cardinality.nullKeyPolicy();
        boolean rejectNullLeftKeys = "reject_null".equals(nullKeyPolicy);

        if (cardinality == null
                || !"many".equals(cardinality.leftMultiplicity())
                || !"one".equals(cardinality.rightMultiplicity())) {
            unsupported.add("cross-model funnel money-attribution bridge runtime cardinality must be many-to-one");
        }
        if (!"exclude_unmatched".equals(nullKeyPolicy) && !"reject_null".equals(nullKeyPolicy)) {
            unsupported.add("cross-model funnel money-attribution bridge supports only exclude_unmatched or reject_null null key policy");
        }

        MetricMapping leftMetrics = crossModelJoinAggregateMetrics(leftAggregate, leftModel, true, unsupported);
        MetricMapping rightMetrics = crossModelMoneyAttributionRightMetrics(rightAggregate, rightModel, unsupported);
        SemanticQueryRequest leftRequest = aggregateBridgeRequest(leftAggregate, leftMetrics, unsupported);
        SemanticQueryRequest rightRequest = aggregateBridgeRequest(rightAggregate, rightMetrics, unsupported);
        SemanticQueryRequest denominatorRequest = null;
        String denominatorModel = null;

        List<String> leftFields = availableFields(null, leftAggregate.get("groupBy"), leftMetrics.aliases());
        List<String> rightFields = availableFields(null, rightAggregate.get("groupBy"), rightMetrics.aliases());
        List<String> joinOutput = stringList(joinAlign.get("output"));
        if (joinOutput.isEmpty()) {
            unsupported.add("cross-model funnel money-attribution bridge requires signed join output schema");
        }
        for (String field : joinOutput) {
            if (!leftFields.contains(field) && !rightFields.contains(field)) {
                unsupported.add("cross-model funnel money-attribution join output references unavailable field: " + field);
            }
        }
        CrossModelFunnelMoneyAttributionContractPlan contractPlan = contract.plan();
        if (!leftFields.contains(contractPlan.sourceField())) {
            unsupported.add("cross-model funnel money-attribution bridge requires source event field in left SQL output");
        }
        if (!rightFields.contains(contractPlan.targetField())) {
            unsupported.add("cross-model funnel money-attribution bridge requires target event field in right SQL output");
        }
        if (!rightFields.contains(contractPlan.orderAmountMetric())) {
            unsupported.add("cross-model funnel money-attribution bridge requires orderAmount in right SQL output");
        }
        if (!joinOutput.contains(contractPlan.sourceField())
                || !joinOutput.contains(contractPlan.targetField())
                || !joinOutput.contains(contractPlan.orderAmountMetric())) {
            unsupported.add("cross-model funnel money-attribution bridge requires source event, target event, and orderAmount in join output");
        }
        if (!rightFields.containsAll(contractPlan.targetPeriodStageFields())) {
            unsupported.add("cross-model funnel money-attribution bridge requires target period fields "
                    + contractPlan.targetPeriodStageFields() + " in right SQL output");
        }
        if (!joinOutput.containsAll(contractPlan.targetPeriodStageFields())) {
            unsupported.add("cross-model funnel money-attribution bridge requires target period fields "
                    + contractPlan.targetPeriodStageFields() + " in join output");
        }
        if (!allSafeAliases(leftFields, rightFields, joinOutput, contractPlan.output())) {
            unsupported.add("cross-model funnel money-attribution bridge supports only governed field aliases");
        }
        if (contractPlan.amountPerLeadContract().declared()) {
            denominatorModel = "CrmLead";
            MetricMapping denominatorMetrics = moneyAmountPerLeadDenominatorMetrics(
                    contractPlan.amountPerLeadContract(), unsupported);
            Map<String, Object> denominatorAggregate = new LinkedHashMap<>();
            denominatorAggregate.put("type", "aggregate");
            denominatorAggregate.put("input", Map.of("model", denominatorModel));
            denominatorAggregate.put("filters", leftAggregate.get("filters"));
            denominatorAggregate.put("groupBy", List.of(contractPlan.groupKey()));
            denominatorAggregate.put("metrics", List.of(Map.of(
                    "name", contractPlan.amountPerLeadContract().denominatorMetric(),
                    "expr", "count(*)")));
            denominatorRequest = aggregateBridgeRequest(denominatorAggregate, denominatorMetrics, unsupported);
        }
        if (!unsupported.isEmpty()) {
            return CrossModelFunnelMoneyAttributionBridgeResult.deferred(true, unsupported);
        }

        CrossModelJoinAlignPlan joinPlan = new CrossModelJoinAlignPlan(
                joinOutput,
                joinOutput,
                leftFields,
                rightFields,
                leftKey,
                rightKey,
                leftMetrics.aliases().get(0),
                "matchedOrderCount",
                contractPlan.sourceField(),
                rejectNullLeftKeys,
                contractPlan.relationRef(),
                contractPlan.cardinality(),
                nullKeyPolicy);
        CrossModelFunnelMoneyAttributionBridgePlan plan = new CrossModelFunnelMoneyAttributionBridgePlan(
                contractPlan.output(),
                joinPlan,
                contractPlan.targetField(),
                contractPlan.windowDays(),
                contractPlan.windowOrder(),
                contractPlan.groupKey(),
                contractPlan.orderAmountMetric(),
                contractPlan.convertedAmountMetric(),
                contractPlan.targetPeriodStageFields(),
                contractPlan.targetPeriodGrain(),
                contractPlan.targetPeriodOutputFields(),
                contractPlan.amountShareContract(),
                contractPlan.amountPerLeadContract());
        return CrossModelFunnelMoneyAttributionBridgeResult.ready(
                denominatorModel, denominatorRequest,
                leftStageName, leftModel, leftRequest, rightStageName, rightModel, rightRequest, plan);
    }

    public static CrossModelFunnelSourceRateBridgeResult toCrossModelFunnelSourceRateBridge(String fallbackModel,
                                                                                            Object executablePlan) {
        List<String> unsupported = new ArrayList<>();
        Map<String, Object> ctePlan = ctePlan(executablePlan, unsupported);
        if (ctePlan == null) {
            return CrossModelFunnelSourceRateBridgeResult.deferred(unsupported);
        }
        if (declaresCrossModelFunnelTimeAttribution(ctePlan)) {
            unsupported.add("cross-model funnel time-attribution contract is validation-only; source-rate compile bridge is not signed");
            return CrossModelFunnelSourceRateBridgeResult.deferred(unsupported);
        }
        List<Map<String, Object>> stages = mapList(ctePlan.get("stages"));
        if (stages.size() != 6) {
            unsupported.add("cross-model funnel source-rate bridge requires signed join_align plus matched numerator, denominator, and final rate stages");
            return CrossModelFunnelSourceRateBridgeResult.deferred(unsupported);
        }

        Map<String, Object> leftAggregate = stages.get(0);
        Map<String, Object> rightAggregate = stages.get(1);
        Map<String, Object> joinAlign = stages.get(2);
        Map<String, Object> matchedAggregate = stages.get(3);
        Map<String, Object> denominatorAggregate = stages.get(4);
        Map<String, Object> finalDerive = stages.get(5);
        if (!"aggregate".equals(stringValue(leftAggregate.get("type")))
                || !"aggregate".equals(stringValue(rightAggregate.get("type")))
                || !"join_align".equals(stringValue(joinAlign.get("type")))
                || !"aggregate".equals(stringValue(matchedAggregate.get("type")))
                || !"aggregate".equals(stringValue(denominatorAggregate.get("type")))
                || !"derive".equals(stringValue(finalDerive.get("type")))) {
            unsupported.add("cross-model funnel source-rate bridge requires aggregate -> aggregate -> join_align -> aggregate -> aggregate -> derive");
            return CrossModelFunnelSourceRateBridgeResult.deferred(unsupported);
        }

        Map<String, Object> joinOnlyPlan = new LinkedHashMap<>();
        joinOnlyPlan.put("stages", new ArrayList<>(stages.subList(0, 3)));
        joinOnlyPlan.put("output", stringList(joinAlign.get("output")));
        CrossModelJoinAlignBridgeResult joinBridge =
                toCrossModelJoinAlignBridge(fallbackModel, Map.of("cte_plan", joinOnlyPlan));
        if (!joinBridge.ready()) {
            unsupported.addAll(joinBridge.unsupported());
            return CrossModelFunnelSourceRateBridgeResult.deferred(unsupported);
        }

        String joinStageName = stringValue(joinAlign.get("name"));
        String matchedStageName = stringValue(matchedAggregate.get("name"));
        String denominatorStageName = stringValue(denominatorAggregate.get("name"));
        if (joinStageName == null || matchedStageName == null || denominatorStageName == null) {
            unsupported.add("cross-model funnel source-rate bridge requires named join, numerator, and denominator stages");
        }
        if (!List.of(joinStageName).equals(stringList(matchedAggregate.get("inputs")))) {
            unsupported.add("cross-model funnel source-rate numerator must aggregate the signed join_align stage");
        }
        if (!List.of("leadSource").equals(stringList(matchedAggregate.get("groupBy")))) {
            unsupported.add("cross-model funnel source-rate numerator must group by leadSource only");
        }
        if (!List.of("leadSource").equals(stringList(denominatorAggregate.get("groupBy")))) {
            unsupported.add("cross-model funnel source-rate denominator must group by leadSource only");
        }
        if (!List.of("leadSource").equals(stringList(finalDerive.get("groupBy")))) {
            if (finalDerive.containsKey("groupBy")) {
                unsupported.add("cross-model funnel source-rate final derive must not declare a different groupBy");
            }
        }
        if (!List.of(denominatorStageName, matchedStageName).equals(stringList(finalDerive.get("inputs")))) {
            unsupported.add("cross-model funnel source-rate final derive must combine denominator then matched numerator");
        }

        String denominatorModel = sourceModel(fallbackModel, denominatorAggregate);
        if (!"CrmLead".equals(denominatorModel)) {
            unsupported.add("cross-model funnel source-rate denominator must use CrmLead");
        }
        if (!sameFilterSet(leftAggregate.get("filters"), denominatorAggregate.get("filters"))) {
            unsupported.add("cross-model funnel source-rate numerator and denominator must use the same lead source filters");
        }

        MetricMapping denominatorMetrics = crossModelFunnelDenominatorMetrics(denominatorAggregate, denominatorModel,
                unsupported);
        String matchedMetric = crossModelFunnelMatchedMetric(matchedAggregate, unsupported);
        String rateAlias = crossModelFunnelRateAlias(finalDerive, unsupported);
        SemanticQueryRequest denominatorRequest =
                aggregateBridgeRequest(denominatorAggregate, denominatorMetrics, unsupported);

        List<String> output = stringList(ctePlan.get("output"));
        if (output.isEmpty()) {
            output.add("leadSource");
            output.add("totalLeadCount");
            output.add(matchedMetric);
            output.add(rateAlias);
        }
        List<String> available = List.of("leadSource", "totalLeadCount", matchedMetric, rateAlias);
        for (String field : output) {
            if (!available.contains(field)) {
                unsupported.add("cross-model funnel source-rate output references unavailable field: " + field);
            }
        }
        if (!allSafeAliases(output, available)) {
            unsupported.add("cross-model funnel source-rate bridge supports only governed field aliases");
        }
        if (!unsupported.isEmpty()) {
            return CrossModelFunnelSourceRateBridgeResult.deferred(unsupported);
        }

        CrossModelFunnelSourceRatePlan plan = new CrossModelFunnelSourceRatePlan(
                output,
                joinBridge.plan(),
                "leadSource",
                "totalLeadCount",
                matchedMetric,
                rateAlias);
        return CrossModelFunnelSourceRateBridgeResult.ready(denominatorModel, denominatorRequest,
                joinBridge.leftModel(), joinBridge.leftRequest(), joinBridge.rightModel(), joinBridge.rightRequest(),
                plan);
    }

    private static SemanticQueryRequest aggregateBridgeRequest(Map<String, Object> aggregate,
                                                               MetricMapping metrics,
                                                               List<String> unsupported) {
        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL");
        request.setStatus("PLAN_READY");
        request.setGroupBy(groupByItems(aggregate.get("groupBy")));
        request.setSlice(sliceItems(aggregate.get("filters"), unsupported));
        request.setColumns(outputColumns(null, aggregate.get("groupBy"), metrics, List.of(), Map.of(), unsupported));
        request.setCalculatedFields(metrics.calculatedFields().isEmpty() ? null : metrics.calculatedFields());
        request.setReturnTotal(false);
        return request;
    }

    private static MetricMapping crossModelJoinAggregateMetrics(Map<String, Object> aggregate,
                                                                String model,
                                                                boolean left,
                                                                List<String> unsupported) {
        List<Map<String, Object>> metricMaps = mapList(aggregate.get("metrics"));
        Map<String, String> columnByAlias = new LinkedHashMap<>();
        if (metricMaps.size() != 1) {
            unsupported.add("cross-model join_align bridge requires exactly one aggregate metric per side");
            return new MetricMapping(columnByAlias);
        }
        Map<String, Object> metric = metricMaps.get(0);
        String name = stringValue(metric.get("name"));
        String expr = stringValue(metric.get("expr"));
        if (!isCountAll(expr)) {
            unsupported.add("cross-model join_align bridge supports only count(*) aggregate metrics");
            return new MetricMapping(columnByAlias);
        }
        if (left) {
            if (!"CrmLead".equals(model) || !"leadCount".equals(name)) {
                unsupported.add("cross-model join_align left aggregate must expose leadCount=count(*) on CrmLead");
                return new MetricMapping(columnByAlias);
            }
            columnByAlias.put(name, "count(leadId) AS " + name);
        } else {
            if (!"FactOrderQueryModel".equals(model) || !"matchedOrderCount".equals(name)) {
                unsupported.add("cross-model join_align right aggregate must expose matchedOrderCount=count(*) on FactOrderQueryModel");
                return new MetricMapping(columnByAlias);
            }
            columnByAlias.put(name, "count(orderId) AS " + name);
        }
        return new MetricMapping(columnByAlias);
    }

    private static MetricMapping crossModelFunnelDenominatorMetrics(Map<String, Object> aggregate,
                                                                    String model,
                                                                    List<String> unsupported) {
        List<Map<String, Object>> metricMaps = mapList(aggregate.get("metrics"));
        Map<String, String> columnByAlias = new LinkedHashMap<>();
        if (metricMaps.size() != 1) {
            unsupported.add("cross-model funnel denominator requires exactly one metric");
            return new MetricMapping(columnByAlias);
        }
        Map<String, Object> metric = metricMaps.get(0);
        String name = stringValue(metric.get("name"));
        String expr = stringValue(metric.get("expr"));
        if (!"CrmLead".equals(model) || !"totalLeadCount".equals(name) || !isCountAll(expr)) {
            unsupported.add("cross-model funnel denominator must expose totalLeadCount=count(*) on CrmLead");
            return new MetricMapping(columnByAlias);
        }
        columnByAlias.put(name, "count(leadId) AS " + name);
        return new MetricMapping(columnByAlias);
    }

    private static MetricMapping moneyAmountPerLeadDenominatorMetrics(MoneyAmountPerLeadContract contract,
                                                                       List<String> unsupported) {
        Map<String, String> columnByAlias = new LinkedHashMap<>();
        if (!contract.declared() || contract.denominatorMetric() == null) {
            unsupported.add("cross-model funnel amount-per-lead denominator requires distinctLeadCount");
            return new MetricMapping(columnByAlias);
        }
        columnByAlias.put(contract.denominatorMetric(), "count(leadId) AS " + contract.denominatorMetric());
        return new MetricMapping(columnByAlias);
    }

    private static String crossModelFunnelMatchedMetric(Map<String, Object> aggregate, List<String> unsupported) {
        List<Map<String, Object>> metricMaps = mapList(aggregate.get("metrics"));
        if (metricMaps.size() != 1) {
            unsupported.add("cross-model funnel matched numerator requires exactly one metric");
            return "matchedLeadCount";
        }
        Map<String, Object> metric = metricMaps.get(0);
        String name = stringValue(metric.get("name"));
        String expr = stringValue(metric.get("expr"));
        Matcher sumAlias = SUM_ALIAS_PATTERN.matcher(expr == null ? "" : expr);
        if (!"matchedLeadCount".equals(name) || !sumAlias.matches() || !"leadCount".equals(sumAlias.group(1))) {
            unsupported.add("cross-model funnel numerator must expose matchedLeadCount=sum(leadCount)");
        }
        return name == null ? "matchedLeadCount" : name;
    }

    private static MetricMapping crossModelMoneyAttributionRightMetrics(Map<String, Object> aggregate,
                                                                        String model,
                                                                        List<String> unsupported) {
        List<Map<String, Object>> metricMaps = mapList(aggregate.get("metrics"));
        Map<String, String> columnByAlias = new LinkedHashMap<>();
        if (!"FactOrderQueryModel".equals(model)) {
            unsupported.add("cross-model funnel money-attribution right aggregate must use FactOrderQueryModel");
            return new MetricMapping(columnByAlias);
        }
        if (metricMaps.size() != 2) {
            unsupported.add("cross-model funnel money-attribution right aggregate must expose matchedOrderCount=count(*) and orderAmount=sum(amount)");
            return new MetricMapping(columnByAlias);
        }
        boolean matchedCount = false;
        boolean orderAmount = false;
        for (Map<String, Object> metric : metricMaps) {
            String name = stringValue(metric.get("name"));
            String expr = stringValue(metric.get("expr"));
            Matcher sumAlias = SUM_ALIAS_PATTERN.matcher(expr == null ? "" : expr);
            if ("matchedOrderCount".equals(name) && isCountAll(expr)) {
                columnByAlias.put(name, "count(orderId) AS " + name);
                matchedCount = true;
            } else if ("orderAmount".equals(name)
                    && sumAlias.matches()
                    && FACT_ORDER_AMOUNT_FIELD.equals(sumAlias.group(1))) {
                columnByAlias.put(name, "sum(" + FACT_ORDER_AMOUNT_FIELD + ") AS " + name);
                orderAmount = true;
            }
        }
        if (!matchedCount || !orderAmount) {
            unsupported.add("cross-model funnel money-attribution right aggregate must expose matchedOrderCount=count(*) and orderAmount=sum(amount)");
        }
        return new MetricMapping(columnByAlias);
    }

    private static String crossModelMoneyAttributionMetric(Map<String, Object> aggregate, List<String> unsupported) {
        List<Map<String, Object>> metricMaps = mapList(aggregate.get("metrics"));
        if (metricMaps.size() != 1) {
            unsupported.add("cross-model funnel money-attribution final amount aggregate requires exactly one metric");
            return "convertedAmount";
        }
        Map<String, Object> metric = metricMaps.get(0);
        String name = stringValue(metric.get("name"));
        String expr = stringValue(metric.get("expr"));
        Matcher sumAlias = SUM_ALIAS_PATTERN.matcher(expr == null ? "" : expr);
        if (!"convertedAmount".equals(name)
                || !sumAlias.matches()
                || !"orderAmount".equals(sumAlias.group(1))) {
            unsupported.add("cross-model funnel money-attribution final amount aggregate must expose convertedAmount=sum(orderAmount)");
        }
        return name == null ? "convertedAmount" : name;
    }

    private static String crossModelFunnelRateAlias(Map<String, Object> derive, List<String> unsupported) {
        List<Map<String, Object>> derived = mapList(derive.get("derived"));
        if (derived.size() != 1) {
            unsupported.add("cross-model funnel final derive requires exactly one rate formula");
            return "leadToOrderConversionRate";
        }
        Map<String, Object> item = derived.get(0);
        String name = stringValue(item.get("name"));
        String expr = stringValue(item.get("expr"));
        Matcher ratio = METRIC_RATIO_PATTERN.matcher(expr == null ? "" : expr);
        if (!"leadToOrderConversionRate".equals(name)
                || !ratio.matches()
                || !"matchedLeadCount".equals(ratio.group(1))
                || !"totalLeadCount".equals(ratio.group(2))) {
            unsupported.add("cross-model funnel final formula must be leadToOrderConversionRate=matchedLeadCount / totalLeadCount");
        }
        return name == null ? "leadToOrderConversionRate" : name;
    }

    private static boolean sameFilterSet(Object leftRaw, Object rightRaw) {
        return filterSignatures(leftRaw).equals(filterSignatures(rightRaw));
    }

    private static boolean declaresCrossModelFunnelTimeAttribution(Map<String, Object> ctePlan) {
        return ctePlan != null && mapValue(ctePlan.get("timeAttributionContract")) != null;
    }

    private static boolean declaresCrossModelFunnelMoneyAttribution(Map<String, Object> ctePlan) {
        return ctePlan != null && mapValue(ctePlan.get("moneyAttributionContract")) != null;
    }

    private static boolean declaresTargetPeriodFunnelAttribution(Map<String, Object> ctePlan) {
        if (ctePlan == null) {
            return false;
        }
        if (ctePlan.containsKey("targetPeriod") || ctePlan.containsKey("outputGrain")) {
            return true;
        }
        Map<String, Object> contract = mapValue(ctePlan.get("timeAttributionContract"));
        Map<String, Object> target = contract == null ? null : mapValue(contract.get("target"));
        return target != null && target.containsKey("targetPeriod");
    }

    private enum TargetPeriodKind {
        TARGET_MONTH,
        YEAR_MONTH,
        INVALID
    }

    private static TargetPeriodContract targetPeriodContract(Map<String, Object> ctePlan,
                                                             Map<String, Object> contract,
                                                             Map<String, Object> matchedAggregate,
                                                             List<String> unsupported) {
        if (!declaresTargetPeriodFunnelAttribution(ctePlan)) {
            return TargetPeriodContract.notDeclared();
        }

        Map<String, Object> target = contract == null ? null : mapValue(contract.get("target"));
        Object rawTargetPeriod = ctePlan.get("targetPeriod");
        if (rawTargetPeriod == null && target != null) {
            rawTargetPeriod = target.get("targetPeriod");
        }
        Object rawOutputGrain = ctePlan.get("outputGrain");
        TargetPeriodKind kind = targetPeriodKind(rawTargetPeriod, unsupported);
        addTargetPeriodGuardDiagnostics(kind, rawOutputGrain, matchedAggregate, ctePlan, unsupported);
        if (kind == TargetPeriodKind.INVALID) {
            unsupported.add("cross-model funnel target attribution requires targetPeriod on FactOrderQueryModel.orderDate with grain=month or grain=year_month");
        }

        OutputGrainContract outputGrain = outputGrainContract(rawOutputGrain, kind, unsupported);
        if (!outputGrain.valid()) {
            unsupported.add(kind == TargetPeriodKind.YEAR_MONTH
                    ? "cross-model funnel target-year-month attribution requires outputGrain sourceFields=[CrmLead.leadSource] and targetPeriodFields=[FactOrderQueryModel.orderDate$year, FactOrderQueryModel.orderDate$month]"
                    : "cross-model funnel target-month attribution requires outputGrain sourceFields=[CrmLead.leadSource] and targetPeriodFields=[FactOrderQueryModel.orderDate$month]");
        }

        List<String> stageFields = targetPeriodStageFields(stringList(matchedAggregate.get("groupBy")), kind);
        if (stageFields.isEmpty()) {
            stageFields = kind == TargetPeriodKind.YEAR_MONTH
                    ? List.of(FACT_ORDER_STAGE_YEAR_FIELD, FACT_ORDER_STAGE_MONTH_FIELD)
                    : List.of(FACT_ORDER_STAGE_MONTH_FIELD);
        }
        List<String> outputFields = outputGrain.targetOutputFields();
        if (outputFields.isEmpty()) {
            outputFields = kind == TargetPeriodKind.YEAR_MONTH
                    ? List.of(FACT_ORDER_TARGET_YEAR_FIELD, FACT_ORDER_TARGET_MONTH_FIELD)
                    : List.of(FACT_ORDER_TARGET_MONTH_FIELD);
        }
        return new TargetPeriodContract(true, stageFields,
                "FactOrderQueryModel.orderDate",
                kind == TargetPeriodKind.YEAR_MONTH ? "year_month" : "month",
                outputFields);
    }

    private static TargetPeriodKind targetPeriodKind(Object rawTargetPeriod, List<String> unsupported) {
        Map<String, Object> targetPeriod = mapValue(rawTargetPeriod);
        if (targetPeriod != null) {
            String field = stringValue(targetPeriod.get("field"));
            String grain = stringValue(targetPeriod.get("grain"));
            if (!targetPeriodSemanticFieldMatches(field)) {
                return TargetPeriodKind.INVALID;
            }
            if ("month".equals(grain)) {
                return TargetPeriodKind.TARGET_MONTH;
            }
            if (isYearMonthGrain(grain)) {
                return TargetPeriodKind.YEAR_MONTH;
            }
            if (hasYearMonthHint(targetPeriod)) {
                addUnsupported(unsupported, "cross-model funnel year-month targetPeriod is not signed unless grain=year_month and outputGrain uses explicit year plus month fields");
            }
            return TargetPeriodKind.INVALID;
        }
        String text = stringValue(rawTargetPeriod);
        if (isYearMonthToken(text)) {
            addUnsupported(unsupported, "cross-model funnel year-month targetPeriod is not signed; use CLARIFY until an explicit year-month contract exists");
            return TargetPeriodKind.INVALID;
        }
        if (ambiguousTargetPeriodOutputFieldMatches(text)) {
            addUnsupported(unsupported, "cross-model funnel target-month attribution requires explicit FactOrderQueryModel.orderDate$month; generic targetPeriod aliases are not signed");
            return TargetPeriodKind.INVALID;
        }
        return targetPeriodOutputFieldMatches(text) ? TargetPeriodKind.TARGET_MONTH : TargetPeriodKind.INVALID;
    }

    private static OutputGrainContract outputGrainContract(Object rawOutputGrain,
                                                           TargetPeriodKind kind,
                                                           List<String> unsupported) {
        List<String> sourceFields = new ArrayList<>();
        List<String> targetPeriodFields = new ArrayList<>();
        Map<String, Object> outputGrain = mapValue(rawOutputGrain);
        if (outputGrain != null) {
            sourceFields.addAll(stringList(outputGrain.get("sourceFields")));
            targetPeriodFields.addAll(stringList(outputGrain.get("targetPeriodFields")));
        } else {
            for (String field : stringList(rawOutputGrain)) {
                if (sourceLeadFieldMatches(field)) {
                    sourceFields.add(field);
                }
                if (targetPeriodOutputFieldMatches(field) || targetPeriodYearFieldMatches(field)) {
                    targetPeriodFields.add(field);
                }
            }
        }
        if (usesSyntheticYearMonthToken(targetPeriodFields)
                || (usesYearAndMonthFields(targetPeriodFields) && kind != TargetPeriodKind.YEAR_MONTH)) {
            addUnsupported(unsupported, "cross-model funnel year-month targetPeriod is not signed; use CLARIFY until an explicit year-month contract exists");
        }
        if (targetPeriodFields.stream().anyMatch(DslCteDslRequestMapper::ambiguousTargetPeriodOutputFieldMatches)) {
            addUnsupported(unsupported, "cross-model funnel target-month attribution requires explicit FactOrderQueryModel.orderDate$month; generic targetPeriod aliases are not signed");
        }
        boolean sourceOk = sourceFields.stream().anyMatch(DslCteDslRequestMapper::sourceLeadFieldMatches);
        List<String> targetOutputFields = normalizedTargetPeriodOutputFields(targetPeriodFields, kind);
        return new OutputGrainContract(sourceOk && !targetOutputFields.isEmpty(),
                targetOutputFields);
    }

    private static List<String> targetPeriodStageFields(List<String> groupBy, TargetPeriodKind kind) {
        if (kind == TargetPeriodKind.YEAR_MONTH) {
            boolean hasYear = groupBy.stream().anyMatch(DslCteDslRequestMapper::targetPeriodYearFieldMatches);
            boolean hasMonth = groupBy.stream().anyMatch(DslCteDslRequestMapper::targetPeriodOutputFieldMatches);
            return hasYear && hasMonth
                    ? List.of(FACT_ORDER_STAGE_YEAR_FIELD, FACT_ORDER_STAGE_MONTH_FIELD)
                    : List.of();
        }
        return groupBy.stream()
                .filter(DslCteDslRequestMapper::targetPeriodOutputFieldMatches)
                .findFirst()
                .map(field -> List.of(FACT_ORDER_STAGE_MONTH_FIELD))
                .orElse(List.of());
    }

    private static void addTargetPeriodGuardDiagnostics(TargetPeriodKind kind,
                                                        Object rawOutputGrain,
                                                        Map<String, Object> matchedAggregate,
                                                        Map<String, Object> ctePlan,
                                                        List<String> unsupported) {
        List<String> targetPeriodFields = targetPeriodFields(rawOutputGrain);
        List<String> matchedGroupBy = stringList(matchedAggregate.get("groupBy"));
        if (usesSyntheticYearMonthToken(targetPeriodFields)
                || usesSyntheticYearMonthToken(matchedGroupBy)
                || (kind != TargetPeriodKind.YEAR_MONTH
                        && (usesYearAndMonthFields(targetPeriodFields) || usesYearAndMonthFields(matchedGroupBy)))
                || usesYearMonthTargetPeriodFilters(ctePlan)) {
            addUnsupported(unsupported, "cross-model funnel year-month targetPeriod is not signed; use CLARIFY until an explicit year-month contract exists");
        }
        if (usesAmbiguousTargetPeriodAlias(targetPeriodFields)
                || usesAmbiguousTargetPeriodAlias(matchedGroupBy)
                || usesAmbiguousTargetPeriodFilterAlias(ctePlan)) {
            addUnsupported(unsupported, "cross-model funnel target-month attribution requires explicit FactOrderQueryModel.orderDate$month; generic targetPeriod aliases are not signed");
        }
    }

    private static ZeroFillCalendarScaffoldContract zeroFillCalendarScaffoldContract(
            Map<String, Object> ctePlan,
            TargetPeriodContract targetPeriod,
            List<String> unsupported) {
        Map<String, Object> scaffold = mapValue(ctePlan == null ? null : ctePlan.get("calendarScaffold"));
        if (scaffold == null) {
            if (usesZeroFilledTargetPeriodCalendar(ctePlan)) {
                addUnsupported(unsupported, ZERO_FILL_TARGET_PERIOD_UNSUPPORTED_MESSAGE);
            }
            return ZeroFillCalendarScaffoldContract.notDeclared();
        }

        boolean valid = true;
        if (targetPeriod == null || !targetPeriod.declared()
                || !"year_month".equals(targetPeriod.grain())) {
            addUnsupported(unsupported, ZERO_FILL_TARGET_PERIOD_UNSUPPORTED_MESSAGE);
            valid = false;
        }
        String field = stringValue(scaffold.get("field"));
        if (!targetPeriodSemanticFieldMatches(field)) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.field must be FactOrderQueryModel.orderDate");
            valid = false;
        }
        String grain = firstNonBlank(stringValue(scaffold.get("grain")),
                stringValue(scaffold.get("bucketGrain")),
                targetPeriod == null ? null : targetPeriod.grain());
        if (!isYearMonthGrain(grain)) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold requires grain=year_month");
            valid = false;
        }
        String source = firstNonBlank(stringValue(scaffold.get("source")), stringValue(scaffold.get("calendar")));
        if (!naturalYearMonthCalendarSource(source)) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.source must be natural_gregorian_year_month");
            valid = false;
        }
        String rangePolicy = firstNonBlank(stringValue(scaffold.get("rangePolicy")), "explicit");
        if (!"explicit".equals(normalizedSemanticToken(rangePolicy))) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.rangePolicy supports explicit only in this cut");
            valid = false;
        }
        Map<String, Object> range = mapValue(scaffold.get("range"));
        YearMonthPeriod from = parseYearMonthPeriod(range == null ? null : stringValue(range.get("from")));
        YearMonthPeriod to = parseYearMonthPeriod(range == null ? null : stringValue(range.get("to")));
        if (from == null || to == null) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.range requires from/to year-month literals");
            valid = false;
        } else if (from.after(to)) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.range from must be <= to");
            valid = false;
        }
        String fillPolicy = stringValue(scaffold.get("fillPolicy"));
        if (!zeroFillPolicyValue(fillPolicy)) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.fillPolicy must be zero");
            valid = false;
        }
        String denominatorScope = stringValue(scaffold.get("denominatorScope"));
        if (denominatorScope != null && !denominatorScope.isBlank()
                && !"fixedpersourcegroup".equals(normalizedSemanticToken(denominatorScope))) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.denominatorScope must be fixed_per_source_group");
            valid = false;
        }
        String scaffoldScope = firstNonBlank(stringValue(scaffold.get("scaffoldScope")),
                stringValue(scaffold.get("sourceGroupScope")),
                "source_groups_from_source_cohort");
        String normalizedScope = normalizedSemanticToken(scaffoldScope);
        if (!List.of("sourcegroupsfromsourcecohort", "sourcecohortgroups").contains(normalizedScope)) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.scaffoldScope must use source cohort groups");
            valid = false;
        }
        if (truthySemanticBoolean(scaffold.get("fullDictionary"))) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold must not expand from full target dictionary");
            valid = false;
        }
        List<YearMonthPeriod> periods = from == null || to == null ? List.of() : yearMonthPeriods(from, to);
        if (periods.size() > ZERO_FILL_CALENDAR_MAX_PERIODS) {
            addUnsupported(unsupported,
                    "cross-model funnel zero-fill calendarScaffold.range exceeds "
                            + ZERO_FILL_CALENDAR_MAX_PERIODS + " months");
            valid = false;
        }
        if (!valid) {
            return ZeroFillCalendarScaffoldContract.notDeclared();
        }
        return new ZeroFillCalendarScaffoldContract(
                true,
                "natural_gregorian_year_month",
                "explicit",
                periods,
                "zero",
                "source_groups_from_source_cohort");
    }

    private static boolean naturalYearMonthCalendarSource(String source) {
        String normalized = normalizedSemanticToken(source);
        return "natural".equals(normalized)
                || "naturalgregorian".equals(normalized)
                || "naturalgregorianyearmonth".equals(normalized)
                || "gregorianyearmonth".equals(normalized);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static YearMonthPeriod parseYearMonthPeriod(String value) {
        if (!isYearMonthLiteral(value)) {
            return null;
        }
        String normalized = value.trim().replace("/", "-");
        try {
            int year;
            int month;
            if (normalized.contains("-")) {
                String[] parts = normalized.split("-", 2);
                year = Integer.parseInt(parts[0]);
                month = Integer.parseInt(parts[1]);
            } else {
                year = Integer.parseInt(normalized.substring(0, 4));
                month = Integer.parseInt(normalized.substring(4, 6));
            }
            if (month < 1 || month > 12) {
                return null;
            }
            return new YearMonthPeriod(year, month);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static List<YearMonthPeriod> yearMonthPeriods(YearMonthPeriod from, YearMonthPeriod to) {
        List<YearMonthPeriod> periods = new ArrayList<>();
        YearMonthPeriod cursor = from;
        while (!cursor.after(to) && periods.size() <= ZERO_FILL_CALENDAR_MAX_PERIODS) {
            periods.add(cursor);
            cursor = cursor.next();
        }
        return periods;
    }

    private static List<String> targetPeriodFields(Object rawOutputGrain) {
        Map<String, Object> outputGrain = mapValue(rawOutputGrain);
        if (outputGrain != null) {
            return stringList(outputGrain.get("targetPeriodFields"));
        }
        return stringList(rawOutputGrain);
    }

    private static boolean sourceLeadFieldMatches(String field) {
        return "leadSource".equals(field) || "CrmLead.leadSource".equals(field);
    }

    private static List<String> expectedTargetPeriodGroupBy(String sourceField, List<String> targetPeriodFields) {
        List<String> result = new ArrayList<>();
        result.add(sourceField);
        result.addAll(targetPeriodFields);
        return result;
    }

    private static String firstOrNull(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static boolean targetPeriodSemanticFieldMatches(String field) {
        return "FactOrderQueryModel.orderDate".equals(field) || "orderDate".equals(field);
    }

    private static boolean targetPeriodOutputFieldMatches(String field) {
        if (field == null) {
            return false;
        }
        String normalized = field.trim();
        return "orderDate$month".equals(normalized)
                || "FactOrderQueryModel.orderDate$month".equals(normalized);
    }

    private static String normalizeTargetPeriodOutputField(String field) {
        if (targetPeriodYearFieldMatches(field)) {
            return FACT_ORDER_TARGET_YEAR_FIELD;
        }
        return targetPeriodOutputFieldMatches(field) ? FACT_ORDER_TARGET_MONTH_FIELD : field;
    }

    private static boolean ambiguousTargetPeriodOutputFieldMatches(String field) {
        if (field == null) {
            return false;
        }
        String normalized = field.trim();
        return "targetPeriod".equals(normalized)
                || "orderDate.month".equals(normalized)
                || "FactOrderQueryModel.orderDate.month".equals(normalized);
    }

    private static boolean usesAmbiguousTargetPeriodAlias(List<String> fields) {
        return fields.stream().anyMatch(DslCteDslRequestMapper::ambiguousTargetPeriodOutputFieldMatches);
    }

    private static boolean usesSyntheticYearMonthToken(List<String> fields) {
        return fields.stream().anyMatch(DslCteDslRequestMapper::isYearMonthToken);
    }

    private static boolean usesYearAndMonthFields(List<String> fields) {
        boolean hasYear = false;
        boolean hasMonth = false;
        for (String field : fields) {
            hasYear = hasYear || targetPeriodYearFieldMatches(field);
            hasMonth = hasMonth || targetPeriodOutputFieldMatches(field);
        }
        return hasYear && hasMonth;
    }

    private static List<String> normalizedTargetPeriodOutputFields(List<String> fields, TargetPeriodKind kind) {
        if (kind == TargetPeriodKind.YEAR_MONTH && usesYearAndMonthFields(fields)
                && !usesSyntheticYearMonthToken(fields)) {
            return List.of(FACT_ORDER_TARGET_YEAR_FIELD, FACT_ORDER_TARGET_MONTH_FIELD);
        }
        return fields.stream()
                .filter(DslCteDslRequestMapper::targetPeriodOutputFieldMatches)
                .findFirst()
                .map(field -> List.of(normalizeTargetPeriodOutputField(field)))
                .orElse(List.of());
    }

    private static boolean usesYearMonthTargetPeriodFilters(Map<String, Object> ctePlan) {
        return targetPeriodFilters(ctePlan).stream()
                .anyMatch(filter -> {
                    String field = stringValue(filter.get("field"));
                    String valueField = stringValue(filter.get("valueField"));
                    return isYearMonthToken(field)
                            || isYearMonthToken(valueField)
                            || ((targetPeriodFilterFieldMatches(field) || targetPeriodFilterFieldMatches(valueField))
                                    && filterValueUsesYearMonthLiteral(filter));
                });
    }

    private static boolean usesAmbiguousTargetPeriodFilterAlias(Map<String, Object> ctePlan) {
        return targetPeriodFilters(ctePlan).stream()
                .anyMatch(filter -> ambiguousTargetPeriodOutputFieldMatches(stringValue(filter.get("field")))
                        || ambiguousTargetPeriodOutputFieldMatches(stringValue(filter.get("valueField"))));
    }

    private static List<Map<String, Object>> targetPeriodFilters(Map<String, Object> ctePlan) {
        if (ctePlan == null) {
            return List.of();
        }
        List<Map<String, Object>> filters = new ArrayList<>(mapList(ctePlan.get("filters")));
        filters.addAll(mapList(ctePlan.get("postSlice")));
        for (Map<String, Object> stage : mapList(ctePlan.get("stages"))) {
            filters.addAll(mapList(stage.get("filters")));
        }
        return filters;
    }

    private static boolean targetPeriodFilterFieldMatches(String field) {
        return targetPeriodOutputFieldMatches(field)
                || targetPeriodYearFieldMatches(field)
                || ambiguousTargetPeriodOutputFieldMatches(field);
    }

    private static boolean usesZeroFilledTargetPeriodCalendar(Map<String, Object> ctePlan) {
        return containsZeroFilledTargetPeriodHint(ctePlan);
    }

    private static boolean containsZeroFilledTargetPeriodHint(Object raw) {
        Map<String, Object> map = mapValue(raw);
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (zeroFilledTargetPeriodHint(entry.getKey(), entry.getValue())
                        || containsZeroFilledTargetPeriodHint(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (containsZeroFilledTargetPeriodHint(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean zeroFilledTargetPeriodHint(String key, Object value) {
        String normalizedKey = normalizedSemanticToken(key);
        if (normalizedKey.contains("zerodenominator")) {
            return false;
        }
        boolean fillKey = normalizedKey.contains("zerofill")
                || normalizedKey.contains("fillmissing")
                || normalizedKey.contains("includemissing")
                || normalizedKey.contains("calendarscaffold")
                || normalizedKey.contains("calendarspine")
                || normalizedKey.contains("missingtargetperiod")
                || normalizedKey.contains("missingbucket")
                || normalizedKey.contains("emptybucket")
                || normalizedKey.contains("alltargetmonths")
                || normalizedKey.contains("allmonths");
        if (fillKey && hintEnabled(value)) {
            return true;
        }
        boolean policyKey = normalizedKey.contains("fillpolicy")
                || normalizedKey.contains("missingpolicy")
                || normalizedKey.contains("bucketpolicy")
                || normalizedKey.contains("calendarpolicy");
        return policyKey && zeroFillPolicyValue(value);
    }

    private static boolean hintEnabled(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        String normalized = normalizedSemanticToken(stringValue(value));
        return !List.of("", "false", "no", "none", "null", "off", "matchedonly", "disabled")
                .contains(normalized);
    }

    private static boolean truthySemanticBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0;
        }
        String normalized = normalizedSemanticToken(stringValue(value));
        return List.of("true", "yes", "on", "enabled", "1").contains(normalized);
    }

    private static boolean zeroFillPolicyValue(Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (zeroFillPolicyValue(item)) {
                    return true;
                }
            }
            return false;
        }
        Map<String, Object> map = mapValue(value);
        if (map != null) {
            return containsZeroFilledTargetPeriodHint(map);
        }
        String normalized = normalizedSemanticToken(stringValue(value));
        return "zero".equals(normalized)
                || normalized.contains("zerofill")
                || normalized.contains("fillmissing")
                || normalized.contains("includemissing")
                || normalized.contains("allmonths")
                || normalized.contains("calendarscaffold")
                || normalized.contains("calendarspine");
    }

    private static boolean filterValueUsesYearMonthLiteral(Map<String, Object> filter) {
        return valueUsesYearMonthLiteral(filter.get("value"))
                || valueUsesYearMonthLiteral(filter.get("values"));
    }

    private static boolean valueUsesYearMonthLiteral(Object value) {
        String text = stringValue(value);
        if (isYearMonthLiteral(text)) {
            return true;
        }
        return stringList(value).stream().anyMatch(DslCteDslRequestMapper::isYearMonthLiteral);
    }

    private static boolean targetPeriodYearFieldMatches(String field) {
        if (field == null) {
            return false;
        }
        String normalized = field.trim();
        return "orderDate$year".equals(normalized)
                || "FactOrderQueryModel.orderDate$year".equals(normalized)
                || "orderDate.year".equals(normalized)
                || "FactOrderQueryModel.orderDate.year".equals(normalized);
    }

    private static boolean hasYearMonthHint(Map<String, Object> targetPeriod) {
        return isYearMonthToken(stringValue(targetPeriod.get("format")))
                || isYearMonthToken(stringValue(targetPeriod.get("valueFormat")))
                || isYearMonthToken(stringValue(targetPeriod.get("keyFormat")))
                || isYearMonthToken(stringValue(targetPeriod.get("bucketFormat")));
    }

    private static boolean isYearMonthGrain(String value) {
        if (value == null) {
            return false;
        }
        String normalized = normalizedSemanticToken(value);
        return "yearmonth".equals(normalized);
    }

    private static boolean isYearMonthToken(String value) {
        if (value == null) {
            return false;
        }
        String normalized = normalizedSemanticToken(value);
        return normalized.contains("yearmonth")
                || normalized.contains("yyyymm");
    }

    private static boolean isYearMonthLiteral(String value) {
        return value != null && YEAR_MONTH_LITERAL_PATTERN.matcher(value.trim()).matches();
    }

    private static String normalizedSemanticToken(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(".", "")
                .replace("$", "")
                .replace(" ", "");
    }

    private static Integer timeAttributionWindowDays(Map<String, Object> window) {
        if (window == null) {
            return null;
        }
        Integer durationDays = intValue(window.get("durationDays"));
        if (durationDays != null) {
            return durationDays;
        }
        Integer size = intValue(window.get("size"));
        String unit = stringValue(window.get("unit"));
        if (size != null && ("day".equals(unit) || "days".equals(unit))) {
            return size;
        }
        return null;
    }

    private static List<String> filterSignatures(Object raw) {
        return mapList(raw).stream()
                .map(filter -> String.valueOf(stringValue(filter.get("field"))) + "|"
                        + String.valueOf(stringValue(filter.get("op"))) + "|"
                        + String.valueOf(filter.get("value")) + "|"
                        + String.valueOf(filter.get("valueField")))
                .sorted()
                .toList();
    }

    private static boolean completedPaidOrderScope(Object rawFilters) {
        boolean completed = false;
        boolean paid = false;
        for (Map<String, Object> filter : mapList(rawFilters)) {
            completed = completed || strictEqualityFilter(filter, FACT_ORDER_STATUS_FIELD, "COMPLETED");
            paid = paid || strictEqualityFilter(filter, FACT_ORDER_PAYMENT_STATUS_FIELD, "PAID");
        }
        return completed && paid;
    }

    private static boolean strictEqualityFilter(Map<String, Object> filter, String field, String value) {
        if (!field.equals(stringValue(filter.get("field")))) {
            return false;
        }
        String op = stringValue(filter.get("op"));
        Object rawValue = filter.get("value");
        Object rawValues = filter.get("values");
        if ("=".equals(op) || "==".equals(op) || "eq".equalsIgnoreCase(op)) {
            return value.equalsIgnoreCase(String.valueOf(rawValue));
        }
        if ("in".equalsIgnoreCase(op) && rawValues instanceof List<?> values) {
            return values.size() == 1 && value.equalsIgnoreCase(String.valueOf(values.get(0)));
        }
        return false;
    }

    private static void validateMoneyAttributionAmountContract(Map<String, Object> contract,
                                                               List<String> unsupported) {
        Map<String, Object> amount = mapValue(contract.get("amount"));
        if (amount == null) {
            unsupported.add("cross-model funnel money-attribution contract requires amount field mapping");
            return;
        }
        String metric = stringValue(amount.get("metric"));
        String field = stringValue(amount.get("field"));
        String stageField = stringValue(amount.get("stageField"));
        String aggregation = stringValue(amount.get("aggregation"));
        String sourceMetric = stringValue(amount.get("sourceMetric"));
        if (!"convertedAmount".equals(metric)
                || !("FactOrderQueryModel.amount".equals(field) || FACT_ORDER_AMOUNT_FIELD.equals(field))
                || (stageField != null && !FACT_ORDER_AMOUNT_FIELD.equals(stageField))
                || !"sum".equals(aggregation)
                || (sourceMetric != null && !"orderAmount".equals(sourceMetric))) {
            unsupported.add("cross-model funnel money-attribution amount mapping must be convertedAmount=sum(FactOrderQueryModel.amount)");
        }
    }

    private static MoneyAmountShareContract moneyAmountShareContract(Map<String, Object> ctePlan,
                                                                     String convertedAmountMetric,
                                                                     List<String> unsupported) {
        Map<String, Object> contract = mapValue(ctePlan.get("moneyDerivedMetricContract"));
        if (contract == null) {
            return MoneyAmountShareContract.notDeclared();
        }

        String kind = stringValue(contract.get("kind"));
        if (!"source_cohort_target_year_month_amount_share".equals(kind)) {
            return MoneyAmountShareContract.notDeclared();
        }
        String baseMetric = stringValue(contract.get("baseMetric"));
        String numeratorMetric = stringValue(contract.get("numeratorMetric"));
        String denominatorMetric = stringValue(contract.get("denominatorMetric"));
        String denominatorScope = stringValue(contract.get("denominatorScope"));
        String ratioAlias = stringValue(contract.get("metric"));
        if (ratioAlias == null) {
            ratioAlias = stringValue(contract.get("ratioAlias"));
        }
        if (ratioAlias == null) {
            ratioAlias = stringValue(contract.get("name"));
        }
        String formula = stringValue(contract.get("formula"));

        if (baseMetric != null && !convertedAmountMetric.equals(baseMetric)) {
            unsupported.add("cross-model funnel amount-share contract baseMetric must match convertedAmount");
        }
        if (!convertedAmountMetric.equals(numeratorMetric)) {
            unsupported.add("cross-model funnel amount-share contract numeratorMetric must be convertedAmount");
        }
        if (!"denominatorConvertedAmount".equals(denominatorMetric)) {
            unsupported.add("cross-model funnel amount-share contract denominatorMetric must be denominatorConvertedAmount");
        }
        if (!"same_target_period_all_source_groups".equals(denominatorScope)) {
            unsupported.add("cross-model funnel amount-share contract requires denominatorScope=same_target_period_all_source_groups");
        }
        if (!"amountShare".equals(ratioAlias)) {
            unsupported.add("cross-model funnel amount-share contract metric must be amountShare");
        }
        String expectedFormula = "amountShare=" + convertedAmountMetric + "/denominatorConvertedAmount";
        String normalizedFormula = formula == null ? null : formula.replaceAll("\\s+", "");
        if (!expectedFormula.equals(normalizedFormula)) {
            unsupported.add("cross-model funnel amount-share contract formula must be "
                    + expectedFormula);
        }

        return new MoneyAmountShareContract(true, numeratorMetric, denominatorMetric,
                denominatorScope, ratioAlias, formula);
    }

    private static MoneyAmountPerLeadContract moneyAmountPerLeadContract(Map<String, Object> ctePlan,
                                                                         String convertedAmountMetric,
                                                                         List<String> unsupported) {
        Map<String, Object> contract = mapValue(ctePlan.get("moneyDerivedMetricContract"));
        if (contract == null) {
            return MoneyAmountPerLeadContract.notDeclared();
        }

        String kind = stringValue(contract.get("kind"));
        if (!"source_cohort_target_year_month_amount_per_lead".equals(kind)) {
            return MoneyAmountPerLeadContract.notDeclared();
        }
        String baseMetric = stringValue(contract.get("baseMetric"));
        String numeratorMetric = stringValue(contract.get("numeratorMetric"));
        String denominatorMetric = stringValue(contract.get("denominatorMetric"));
        String denominatorScope = stringValue(contract.get("denominatorScope"));
        String ratioAlias = stringValue(contract.get("metric"));
        if (ratioAlias == null) {
            ratioAlias = stringValue(contract.get("ratioAlias"));
        }
        if (ratioAlias == null) {
            ratioAlias = stringValue(contract.get("name"));
        }
        String formula = stringValue(contract.get("formula"));

        if (baseMetric != null && !convertedAmountMetric.equals(baseMetric)) {
            unsupported.add("cross-model funnel amount-per-lead contract baseMetric must match convertedAmount");
        }
        if (!convertedAmountMetric.equals(numeratorMetric)) {
            unsupported.add("cross-model funnel amount-per-lead contract numeratorMetric must be convertedAmount");
        }
        if (!"distinctLeadCount".equals(denominatorMetric)) {
            unsupported.add("cross-model funnel amount-per-lead contract denominatorMetric must be distinctLeadCount");
        }
        if (!"fixed_per_source_group".equals(denominatorScope)) {
            unsupported.add("cross-model funnel amount-per-lead contract requires denominatorScope=fixed_per_source_group");
        }
        if (!"amountPerLead".equals(ratioAlias)) {
            unsupported.add("cross-model funnel amount-per-lead contract metric must be amountPerLead");
        }
        String expectedFormula = "amountPerLead=" + convertedAmountMetric + "/distinctLeadCount";
        String normalizedFormula = formula == null ? null : formula.replaceAll("\\s+", "");
        if (!expectedFormula.equals(normalizedFormula)) {
            unsupported.add("cross-model funnel amount-per-lead contract formula must be "
                    + expectedFormula);
        }

        return new MoneyAmountPerLeadContract(true, numeratorMetric, denominatorMetric,
                denominatorScope, ratioAlias, formula);
    }

    private static boolean isCountAll(String expr) {
        return expr != null && "count(*)".equals(expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", ""));
    }

    private static boolean signedCrmOrderJoinEndpoint(Map<String, Object> endpoint,
                                                      String stage,
                                                      String model,
                                                      String field) {
        return endpoint != null
                && stage != null
                && stage.equals(stringValue(endpoint.get("stage")))
                && model.equals(stringValue(endpoint.get("model")))
                && field.equals(stringValue(endpoint.get("field")));
    }

    private static boolean singleAlignmentKeyMatches(List<String> keys, String leftKey, String rightKey) {
        if (keys.size() != 1 || leftKey == null || rightKey == null) {
            return false;
        }
        String[] parts = keys.get(0).split("=", 2);
        if (parts.length != 2) {
            return false;
        }
        String left = parts[0].trim();
        String right = parts[1].trim();
        return leftKey.equals(left) && rightKey.equals(right);
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
        if (stages.size() < 2) {
            unsupported.add("row-level SLA bridge requires derive(input) -> aggregate in this cut");
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
        List<PostAggregateCalculationDef> postAgg = new ArrayList<>();
        List<SemanticQueryRequest.SliceItem> postSlice = null;
        String previousStageName = stringValue(aggregate.get("name"));
        for (int i = 2; i < stages.size(); i++) {
            Map<String, Object> resultStage = stages.get(i);
            String type = stringValue(resultStage.get("type"));
            String stageName = stringValue(resultStage.get("name"));
            List<String> inputs = stringList(resultStage.get("inputs"));
            if (inputs.size() != 1 || previousStageName == null || !previousStageName.equals(inputs.get(0))) {
                unsupported.add("row-level SLA result stage must reference previous signed stage: " + stageName);
                continue;
            }
            if ("derive".equals(type)) {
                postAgg.addAll(postAggregateCalculations(resultStage.get("derived"), metrics.aliases(), unsupported));
                previousStageName = stageName;
            } else if ("postSlice".equals(type)) {
                if (postSlice != null) {
                    unsupported.add("row-level SLA bridge supports only one postSlice stage in this cut");
                }
                if (i != stages.size() - 1) {
                    unsupported.add("row-level SLA postSlice stage must be final in this cut");
                }
                postSlice = sliceItems(resultStage.get("filters"), unsupported);
                previousStageName = stageName;
            } else {
                unsupported.add("row-level SLA bridge does not execute result stage type: " + type);
            }
        }

        SemanticQueryRequest request = new SemanticQueryRequest();
        request.setRoute("DSL");
        request.setStatus("PLAN_READY");
        request.setGroupBy(groupByItems(aggregate.get("groupBy")));
        request.setSlice(sliceItems(derive.get("filters"), unsupported));
        request.setColumns(outputColumns(ctePlan.get("output"), aggregate.get("groupBy"), metrics,
                postAgg, Map.of(), unsupported));
        request.setCalculatedFields(mergeCalculatedFields(calculatedFields, metrics.calculatedFields()));
        request.setPostAggregateCalculations(postAgg.isEmpty() ? null : postAgg);
        request.setPostSlice(postSlice);
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

            Matcher unrespondedCutoff = SLA_UNRESPONDED_CUTOFF_PATTERN.matcher(expr);
            if (unrespondedCutoff.matches()) {
                addUnrespondedCutoffCalculatedField(result, name, unrespondedCutoff.group(1),
                        unrespondedCutoff.group(2), unrespondedCutoff.group(3), unsupported);
                continue;
            }

            Matcher unrespondedCutoffReversed = SLA_UNRESPONDED_CUTOFF_REVERSED_PATTERN.matcher(expr);
            if (unrespondedCutoffReversed.matches()) {
                addUnrespondedCutoffCalculatedField(result, name, unrespondedCutoffReversed.group(3),
                        unrespondedCutoffReversed.group(1), unrespondedCutoffReversed.group(2), unsupported);
                continue;
            }

            Matcher unrespondedReferenceHours = SLA_UNRESPONDED_REFERENCE_HOURS_PATTERN.matcher(expr);
            if (unrespondedReferenceHours.matches()) {
                addUnrespondedReferenceHoursCalculatedField(result, name, unrespondedReferenceHours.group(1),
                        unrespondedReferenceHours.group(2), unrespondedReferenceHours.group(3),
                        unrespondedReferenceHours.group(4), unsupported);
                continue;
            }

            Matcher unrespondedReferenceHoursReversed = SLA_UNRESPONDED_REFERENCE_HOURS_REVERSED_PATTERN.matcher(expr);
            if (unrespondedReferenceHoursReversed.matches()) {
                addUnrespondedReferenceHoursCalculatedField(result, name, unrespondedReferenceHoursReversed.group(4),
                        unrespondedReferenceHoursReversed.group(1), unrespondedReferenceHoursReversed.group(2),
                        unrespondedReferenceHoursReversed.group(3), unsupported);
                continue;
            }

            unsupported.add("row-level SLA bridge supports only hours_between duration, priority_threshold mapping, "
                    + "SLA hit threshold predicate, combined SLA hit predicate, signed CRM funnel non-null predicate, "
                    + "SLA overdue threshold predicate, unresponded cutoff predicate, and unresponded reference-time "
                    + "threshold predicate: " + name);
        }
        if (result.isEmpty()) {
            unsupported.add("row-level SLA bridge requires signed calculatedFields");
        }
        return result;
    }

    private static void addUnrespondedCutoffCalculatedField(List<CalculatedFieldDef> result, String name,
                                                           String nullableField, String op, String cutoff,
                                                           List<String> unsupported) {
        if (!"firstResponseAt".equals(nullableField) && !"resolvedAt".equals(nullableField)) {
            unsupported.add("row-level SLA unresponded cutoff predicate supports firstResponseAt/resolvedAt only: "
                    + name);
            return;
        }
        String normalizedCutoff = cutoff.replaceAll("\\s+", "");
        if ("now()".equalsIgnoreCase(normalizedCutoff)) {
            cutoff = "now()";
        }
        result.add(new CalculatedFieldDef(name, "SLA未响应超时标记",
                "iif(is_null(" + nullableField + ") && createdAt " + op + " " + cutoff + ", 1, 0)"));
    }

    private static void addUnrespondedReferenceHoursCalculatedField(List<CalculatedFieldDef> result, String name,
                                                                    String nullableField, String referenceTime,
                                                                    String thresholdOp, String thresholdHours,
                                                                    List<String> unsupported) {
        if (!"firstResponseAt".equals(nullableField) && !"resolvedAt".equals(nullableField)) {
            unsupported.add("row-level SLA unresponded reference-time predicate supports firstResponseAt/resolvedAt only: "
                    + name);
            return;
        }
        String cutoff = referenceTimeMinusHours(referenceTime, thresholdHours, unsupported);
        if (cutoff == null) {
            return;
        }
        String createdAtOp = ">=".equals(thresholdOp) ? "<=" : "<";
        result.add(new CalculatedFieldDef(name, "SLA未响应超时标记",
                "iif(is_null(" + nullableField + ") && createdAt " + createdAtOp + " '" + cutoff + "', 1, 0)"));
    }

    private static String referenceTimeMinusHours(String referenceTime, String thresholdHours,
                                                  List<String> unsupported) {
        try {
            long minutes = Math.round(Double.parseDouble(thresholdHours) * 60.0d);
            LocalDateTime value = parseReferenceDateTime(referenceTime);
            return value.minusMinutes(minutes).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (NumberFormatException ex) {
            unsupported.add("row-level SLA unresponded reference-time threshold must be numeric hours");
            return null;
        } catch (DateTimeParseException ex) {
            unsupported.add("row-level SLA unresponded reference-time literal must be yyyy-MM-dd or yyyy-MM-dd HH:mm[:ss]");
            return null;
        }
    }

    private static LocalDateTime parseReferenceDateTime(String referenceTime) {
        String normalized = referenceTime.replace('T', ' ');
        if (normalized.length() == 10) {
            return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        }
        DateTimeFormatter formatter = normalized.length() == 16
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.parse(normalized, formatter);
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
            if (caseWhenAlias.matches() && calculatedNames.contains(caseWhenAlias.group(1))) {
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
        if (derived.size() < 2 || derived.size() > 3) {
            unsupported.add("DSL_CTE period-over-period bridge requires lag metric and difference or growth derived fields");
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

        PeriodComparison periodComparison = periodComparison(orderField, partitionBy);
        if (periodComparison == null) {
            unsupported.add("DSL_CTE period-over-period bridge supports only signed month-grain MoM or YoY templates in this cut");
            return null;
        }
        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("field", periodComparison.field());
        timeWindow.put("grain", periodComparison.grain());
        timeWindow.put("comparison", periodComparison.comparison());
        timeWindow.put("targetMetrics", List.of(measure));

        Map<String, String> aliasOverride = new LinkedHashMap<>();
        aliasOverride.put(priorAlias, measure + "__prior");
        boolean hasDiff = false;
        boolean hasRatio = false;
        for (int i = 1; i < derived.size(); i++) {
            Map<String, Object> currentDerived = derived.get(i);
            String alias = stringValue(currentDerived.get("name"));
            String expr = stringValue(currentDerived.get("expr"));
            if (alias == null) {
                unsupported.add("DSL_CTE period-over-period bridge requires named derived fields");
                return null;
            }
            if (matchesPeriodDiffExpr(expr, measure, priorAlias)) {
                if (hasDiff) {
                    unsupported.add("DSL_CTE period-over-period bridge supports only one difference derived field");
                    return null;
                }
                aliasOverride.put(alias, measure + "__diff");
                hasDiff = true;
            } else if (matchesPeriodGrowthExpr(expr, measure, priorAlias)) {
                if (hasRatio) {
                    unsupported.add("DSL_CTE period-over-period bridge supports only one growth ratio derived field");
                    return null;
                }
                aliasOverride.put(alias, measure + "__ratio");
                hasRatio = true;
            } else {
                unsupported.add("DSL_CTE period-over-period bridge requires difference formula metric - lagAlias "
                        + "or growth formula (metric - lagAlias) / lagAlias");
                return null;
            }
        }
        if (!hasDiff && !hasRatio) {
            unsupported.add("DSL_CTE period-over-period bridge requires difference or growth derived field");
            return null;
        }
        return new WindowBridge(timeWindow, aliasOverride);
    }

    private static boolean matchesPeriodDiffExpr(String expr, String measure, String priorAlias) {
        if (expr == null || measure == null || priorAlias == null) {
            return false;
        }
        String normalized = expr.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String current = measure.toLowerCase(Locale.ROOT);
        String prior = priorAlias.toLowerCase(Locale.ROOT);
        String canonical = current + "-" + prior;
        String parenthesized = "(" + canonical + ")";
        String canonicalWithEnginePrior = current + "-" + current + "__prior";
        String parenthesizedWithEnginePrior = "(" + canonicalWithEnginePrior + ")";
        return canonical.equals(normalized)
                || parenthesized.equals(normalized)
                || canonicalWithEnginePrior.equals(normalized)
                || parenthesizedWithEnginePrior.equals(normalized);
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

    private static PeriodComparison periodComparison(String orderField, List<String> partitionBy) {
        String grain = grain(orderField);
        if ("month".equals(grain)) {
            return new PeriodComparison(orderField, "month", "mom");
        }
        if (!"year".equals(grain)) {
            return null;
        }
        String root = temporalRoot(orderField, "year");
        if (root == null || partitionBy == null) {
            return null;
        }
        for (String partitionField : partitionBy) {
            if ("month".equals(grain(partitionField))
                    && root.equals(temporalRoot(partitionField, "month"))) {
                return new PeriodComparison(root + temporalSeparator(orderField) + "id", "month", "yoy");
            }
            if ("quarter".equals(grain(partitionField))
                    && root.equals(temporalRoot(partitionField, "quarter"))) {
                return new PeriodComparison(root + temporalSeparator(orderField) + "id", "quarter", "yoy");
            }
        }
        return null;
    }

    private static String temporalRoot(String field, String suffix) {
        if (field == null || suffix == null) {
            return null;
        }
        String lower = field.toLowerCase(Locale.ROOT);
        String dotSuffix = "." + suffix;
        String dollarSuffix = "$" + suffix;
        if (lower.endsWith(dotSuffix)) {
            return field.substring(0, field.length() - dotSuffix.length());
        }
        if (lower.endsWith(dollarSuffix)) {
            return field.substring(0, field.length() - dollarSuffix.length());
        }
        return null;
    }

    private static String temporalSeparator(String field) {
        return field != null && field.contains("$") ? "$" : ".";
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

    private static List<CalculatedFieldDef> mergeCalculatedFields(List<CalculatedFieldDef> first,
                                                                  List<CalculatedFieldDef> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return null;
        }
        List<CalculatedFieldDef> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        return result;
    }

    private static CalculatedFieldDef aggregateCalculatedField(String name, String caption,
                                                              String expression, String agg) {
        CalculatedFieldDef def = new CalculatedFieldDef(name, caption, expression);
        def.setAgg(agg);
        return def;
    }

    private static MetricMapping metrics(Object raw, List<String> unsupported) {
        List<Map<String, Object>> metricMaps = mapList(raw);
        Map<String, String> columnByAlias = new LinkedHashMap<>();
        List<CalculatedFieldDef> calculatedFields = new ArrayList<>();
        for (Map<String, Object> metric : metricMaps) {
            String name = stringValue(metric.get("name"));
            String expr = stringValue(metric.get("expr"));
            if (name == null || expr == null) {
                unsupported.add("aggregate metric must declare name and expr: " + metric);
                continue;
            }
            String normalizedConditionalCount = normalizeConditionalCountMetric(expr, unsupported);
            if (normalizedConditionalCount != null) {
                columnByAlias.put(name, normalizedConditionalCount + " AS " + name);
                continue;
            }
            ConditionalValueSum conditionalValueSum = conditionalValueSumMetric(expr, unsupported);
            if (conditionalValueSum != null) {
                calculatedFields.add(aggregateCalculatedField(
                        name,
                        name + "条件金额行值",
                        conditionalValueSum.formula(),
                        "SUM"));
                columnByAlias.put(name, name);
                continue;
            }
            if (expr.toLowerCase(Locale.ROOT).contains("case when")) {
                unsupported.add("aggregate CASE metric is not signed for DSL_CTE bridge v1: " + name);
                continue;
            }
            columnByAlias.put(name, expr + " AS " + name);
        }
        if (columnByAlias.isEmpty()) {
            unsupported.add("aggregate stage must declare object metrics for DSL_CTE bridge v1");
        }
        return new MetricMapping(columnByAlias, calculatedFields);
    }

    private static String normalizeConditionalCountMetric(String expr, List<String> unsupported) {
        Matcher notNull = CASE_WHEN_NOT_NULL_COUNT_PATTERN.matcher(expr == null ? "" : expr);
        if (notNull.matches()) {
            return "sum(if(is_not_null(" + notNull.group(1) + "), 1, 0))";
        }
        Matcher stringCompare = CASE_WHEN_STRING_COMPARE_COUNT_PATTERN.matcher(expr == null ? "" : expr);
        if (stringCompare.matches()) {
            String value = unescapeSqlStringLiteral(stringCompare.group(3));
            if (!safeCaseLabelLiteral(value)) {
                unsupported.add("conditional count CASE literal must be a short single-line string");
                return null;
            }
            return "sum(if(" + stringCompare.group(1) + " " + formulaOperator(stringCompare.group(2))
                    + " " + quoteStringLiteral(value) + ", 1, 0))";
        }
        return null;
    }

    private static ConditionalValueSum conditionalValueSumMetric(String expr, List<String> unsupported) {
        Matcher notNull = CASE_WHEN_NOT_NULL_VALUE_SUM_PATTERN.matcher(expr == null ? "" : expr);
        if (notNull.matches()) {
            return new ConditionalValueSum(
                    "if(is_not_null(" + notNull.group(1) + "), " + notNull.group(2) + ", 0)");
        }
        Matcher stringCompare = CASE_WHEN_STRING_COMPARE_VALUE_SUM_PATTERN.matcher(expr == null ? "" : expr);
        if (stringCompare.matches()) {
            String value = unescapeSqlStringLiteral(stringCompare.group(3));
            if (!safeCaseLabelLiteral(value)) {
                unsupported.add("conditional value SUM CASE literal must be a short single-line string");
                return null;
            }
            return new ConditionalValueSum(
                    "if(" + stringCompare.group(1) + " " + formulaOperator(stringCompare.group(2))
                            + " " + quoteStringLiteral(value) + ", " + stringCompare.group(4) + ", 0)");
        }
        return null;
    }

    private static String formulaOperator(String op) {
        if ("=".equals(op)) {
            return "==";
        }
        if ("<>".equals(op)) {
            return "!=";
        }
        return op;
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

    private static ResultStageDerivedMetrics resultStageMetricDerived(Object rawDerived, String model,
                                                                      List<String> metricAliases,
                                                                      List<String> unsupported) {
        List<Map<String, Object>> derived = mapList(rawDerived);
        if (derived.isEmpty() || derived.size() > 6) {
            unsupported.add("result-stage SLA metric ratio bridge requires one to six signed derived fields");
            return ResultStageDerivedMetrics.emptyMetrics();
        }
        List<MetricRatioDerived> ratios = new ArrayList<>();
        List<MetricArithmeticDerived> arithmetic = new ArrayList<>();
        for (Map<String, Object> item : derived) {
            String name = stringValue(item.get("name"));
            String expr = stringValue(item.get("expr"));
            if (name == null || !SAFE_ALIAS_PATTERN.matcher(name).matches()) {
                unsupported.add("result-stage SLA metric ratio derived field must declare a governed alias");
                continue;
            }
            Matcher matcher = METRIC_RATIO_PATTERN.matcher(expr == null ? "" : expr);
            if (matcher.matches()) {
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
                if (aliasAlreadyUsed(name, ratios, arithmetic)) {
                    unsupported.add("result-stage SLA metric ratio aliases must be unique");
                    continue;
                }
                ratios.add(new MetricRatioDerived(name, numerator, denominator));
                continue;
            }

            MetricArithmeticDerived arithmeticDerived = signedSlaArithmeticDerived(name, expr,
                    metricAliases, unsupported);
            if (arithmeticDerived != null) {
                if (aliasAlreadyUsed(name, ratios, arithmetic)) {
                    unsupported.add("result-stage SLA metric ratio aliases must be unique");
                    continue;
                }
                arithmetic.add(arithmeticDerived);
                continue;
            }

            arithmeticDerived = signedFunnelArithmeticDerived(name, expr, model,
                    metricAliases, unsupported);
            if (arithmeticDerived != null) {
                if (aliasAlreadyUsed(name, ratios, arithmetic)) {
                    unsupported.add("result-stage SLA metric ratio aliases must be unique");
                    continue;
                }
                arithmetic.add(arithmeticDerived);
                continue;
            }
            unsupported.add("result-stage SLA metric ratio bridge supports only numerator / denominator formulas, "
                    + "signed SLA miss-count formulas, or signed CRM funnel drop-off formulas");
        }
        return unsupported.isEmpty() ? new ResultStageDerivedMetrics(ratios, arithmetic)
                : ResultStageDerivedMetrics.emptyMetrics();
    }

    private static MetricArithmeticDerived signedSlaArithmeticDerived(String name, String expr,
                                                                      List<String> metricAliases,
                                                                      List<String> unsupported) {
        Matcher differenceMatcher = METRIC_DIFFERENCE_PATTERN.matcher(expr == null ? "" : expr);
        if (!differenceMatcher.matches()) {
            return null;
        }
        String left = differenceMatcher.group(1);
        String right = differenceMatcher.group(2);
        if (!signedSlaMissCountAlias(left, right, name)) {
            return null;
        }
        if (!metricAliases.contains(left) || !metricAliases.contains(right)) {
            unsupported.add("result-stage SLA miss count must reference signed aggregate metric aliases");
            return null;
        }
        return new MetricArithmeticDerived(name, quoteAlias(left) + " - " + quoteAlias(right),
                "sla_miss_count");
    }

    private static MetricArithmeticDerived signedFunnelArithmeticDerived(String name, String expr, String model,
                                                                         List<String> metricAliases,
                                                                         List<String> unsupported) {
        if (!isCrmLeadModel(model)) {
            return null;
        }
        Matcher differenceMatcher = METRIC_DIFFERENCE_PATTERN.matcher(expr == null ? "" : expr);
        if (differenceMatcher.matches()) {
            String left = differenceMatcher.group(1);
            String right = differenceMatcher.group(2);
            if (!signedFunnelDropOffCountAlias(left, right, name)) {
                unsupported.add("result-stage SLA metric ratio bridge supports only signed CRM funnel drop-off count formulas");
                return null;
            }
            if (!metricAliases.contains(left) || !metricAliases.contains(right)) {
                unsupported.add("result-stage CRM funnel drop-off must reference signed aggregate metric aliases");
                return null;
            }
            return new MetricArithmeticDerived(name, quoteAlias(left) + " - " + quoteAlias(right),
                    "funnel_drop_off_count");
        }

        Matcher ratioMatcher = METRIC_DIFFERENCE_RATIO_PATTERN.matcher(expr == null ? "" : expr);
        if (ratioMatcher.matches()) {
            String left = ratioMatcher.group(1);
            String right = ratioMatcher.group(2);
            String denominator = ratioMatcher.group(3);
            if (!signedFunnelDropOffRateAlias(left, right, denominator, name)) {
                unsupported.add("result-stage SLA metric ratio bridge supports only signed CRM funnel drop-off rate formulas");
                return null;
            }
            if (!metricAliases.contains(left) || !metricAliases.contains(right) || !metricAliases.contains(denominator)) {
                unsupported.add("result-stage CRM funnel drop-off must reference signed aggregate metric aliases");
                return null;
            }
            return new MetricArithmeticDerived(name, "(1.0 * (" + quoteAlias(left) + " - " + quoteAlias(right)
                    + ") / NULLIF(" + quoteAlias(denominator) + ", 0))", "funnel_drop_off_rate");
        }
        return null;
    }

    private static boolean aliasAlreadyUsed(String alias, List<MetricRatioDerived> ratios,
                                            List<MetricArithmeticDerived> arithmetic) {
        return ratios.stream().anyMatch(existing -> existing.ratioAlias().equals(alias))
                || arithmetic.stream().anyMatch(existing -> existing.alias().equals(alias));
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

    private static boolean signedSlaMissCountAlias(String left, String right, String alias) {
        if (!"ticketCount".equals(left)) {
            return false;
        }
        if (!List.of("notHitCount", "slaMissCount", "slaMissedCount", "slaViolationCount").contains(alias)) {
            return false;
        }
        return switch (right) {
            case "slaHitCount", "firstResponseSlaHitCount", "resolutionSlaHitCount", "combinedSlaHitCount" -> true;
            default -> false;
        };
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

    private static boolean signedFunnelDropOffCountAlias(String left, String right, String alias) {
        if ("leadCount".equals(left) && "convertedOpportunityCount".equals(right)) {
            return "leadToOpportunityDropOffCount".equals(alias);
        }
        if ("convertedOpportunityCount".equals(left) && "convertedOrderCount".equals(right)) {
            return "opportunityDropOffCount".equals(alias);
        }
        if ("leadCount".equals(left) && "convertedOrderCount".equals(right)) {
            return "leadToOrderDropOffCount".equals(alias);
        }
        return false;
    }

    private static boolean signedFunnelDropOffRateAlias(String left, String right, String denominator, String alias) {
        if ("leadCount".equals(left) && "convertedOpportunityCount".equals(right)
                && "leadCount".equals(denominator)) {
            return "leadToOpportunityDropOffRate".equals(alias);
        }
        if ("convertedOpportunityCount".equals(left) && "convertedOrderCount".equals(right)
                && "convertedOpportunityCount".equals(denominator)) {
            return "opportunityToOrderDropOffRate".equals(alias);
        }
        if ("leadCount".equals(left) && "convertedOrderCount".equals(right)
                && "leadCount".equals(denominator)) {
            return "leadToOrderDropOffRate".equals(alias);
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

    private static List<ResultStageFilter> resultStageAliasFilters(Object rawFilters, List<String> signedAliases,
                                                                  List<String> stringAliases, String bridgeName,
                                                                  List<String> unsupported) {
        return resultStageAliasFilters(rawFilters, signedAliases, stringAliases, bridgeName,
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
        return resultStageAliasFilters(rawFilters, signedAliases, List.of(), bridgeName, aliasMessage,
                operatorMessage, allowedOps, unsupported);
    }

    private static List<ResultStageFilter> resultStageAliasFilters(Object rawFilters, List<String> signedAliases,
                                                                  List<String> stringAliases,
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
            if (stringAliases.contains(field)) {
                if (!List.of("=", "!=", "<>").contains(sqlOp)) {
                    unsupported.add(bridgeName + " supports only equality postSlice filters on signed label aliases");
                    continue;
                }
                String label = stringValue(value);
                if (!safeCaseLabelLiteral(label)) {
                    unsupported.add(bridgeName + " label postSlice value must be a short single-line string");
                    continue;
                }
                result.add(new ResultStageFilter(field, sqlOp, label));
                continue;
            }
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
            case "==" -> "=";
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

    private static BridgeSqlDialect detectBridgeSqlDialect(SqlGenerationResult... results) {
        StringBuilder combined = new StringBuilder();
        if (results != null) {
            for (SqlGenerationResult result : results) {
                if (result != null && result.getSql() != null) {
                    combined.append(result.getSql()).append('\n');
                }
            }
        }
        String sql = combined.toString().toUpperCase(Locale.ROOT);
        if (sql.contains("TO_CHAR(") || sql.contains("INTERVAL '")) {
            return BridgeSqlDialect.POSTGRESQL;
        }
        if (sql.contains("DATE_FORMAT(") || sql.contains("TIMESTAMPDIFF(")) {
            return BridgeSqlDialect.MYSQL;
        }
        if (sql.contains("DATEADD(") || sql.contains("DATEDIFF(")) {
            return BridgeSqlDialect.SQLSERVER;
        }
        return BridgeSqlDialect.SQLITE;
    }

    private static String bridgeDateOnly(String expression, BridgeSqlDialect dialect) {
        return switch (dialect) {
            case POSTGRESQL, SQLSERVER -> "CAST(" + expression + " AS date)";
            case MYSQL, SQLITE -> "date(" + expression + ")";
        };
    }

    private static String bridgeDatePlusDays(String expression, BridgeSqlDialect dialect) {
        return switch (dialect) {
            case POSTGRESQL -> "(" + bridgeDateOnly(expression, dialect) + " + (? * INTERVAL '1 day'))";
            case MYSQL -> "DATE_ADD(" + bridgeDateOnly(expression, dialect) + ", INTERVAL ? DAY)";
            case SQLSERVER -> "DATEADD(day, ?, " + bridgeDateOnly(expression, dialect) + ")";
            case SQLITE -> "date(" + expression + ", '+' || ? || ' days')";
        };
    }

    private enum BridgeSqlDialect {
        MYSQL,
        POSTGRESQL,
        SQLSERVER,
        SQLITE
    }

    private static String quoteStringLiteral(String value) {
        if (!safeCaseLabelLiteral(value)) {
            throw RX.throwB("DSL_CTE_RESULT_STAGE_UNSAFE_LABEL: " + value);
        }
        return "'" + value.replace("'", "''") + "'";
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
        if (lower.endsWith(".quarter") || lower.endsWith("$quarter")) {
            return "quarter";
        }
        if (lower.endsWith(".year") || lower.endsWith("$year")) {
            return "year";
        }
        return null;
    }

    private record MetricMapping(Map<String, String> columnByAlias, List<CalculatedFieldDef> calculatedFields) {
        MetricMapping(Map<String, String> columnByAlias) {
            this(columnByAlias, List.of());
        }

        List<String> aliases() {
            return new ArrayList<>(columnByAlias.keySet());
        }
    }

    private record ConditionalValueSum(String formula) {
    }

    private record WindowBridge(Map<String, Object> timeWindow, Map<String, String> outputAliasOverride) {
    }

    private record PeriodComparison(String field, String grain, String comparison) {
    }

    private record CumulativeDerived(String rankAlias, String cumulativeAlias) {
    }

    public record MetricRatioDerived(String ratioAlias, String numeratorAlias, String denominatorAlias) {
    }

    public record MetricArithmeticDerived(String alias, String sqlExpression, String kind,
                                          Map<String, Object> descriptor) {
        public MetricArithmeticDerived(String alias, String sqlExpression, String kind) {
            this(alias, sqlExpression, kind, Map.of());
        }

        public MetricArithmeticDerived {
            descriptor = descriptor == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(descriptor));
        }
    }

    public record ResultStageDerivedMetrics(List<MetricRatioDerived> ratios,
                                            List<MetricArithmeticDerived> arithmetic) {
        static ResultStageDerivedMetrics emptyMetrics() {
            return new ResultStageDerivedMetrics(List.of(), List.of());
        }

        boolean empty() {
            return ratios.isEmpty() && arithmetic.isEmpty();
        }

        List<String> aliases() {
            List<String> result = new ArrayList<>();
            result.addAll(ratios.stream().map(MetricRatioDerived::ratioAlias).toList());
            result.addAll(arithmetic.stream().map(MetricArithmeticDerived::alias).toList());
            return result;
        }

        List<String> labelAliases() {
            return arithmetic.stream()
                    .filter(item -> "relation_metric_case_label".equals(item.kind())
                            || "relation_metric_ordered_bucket".equals(item.kind()))
                    .map(MetricArithmeticDerived::alias)
                    .toList();
        }
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
            result.put("required_capabilities", List.of(
                    "rank_over_aggregate_metric_order",
                    "running_total_ratio",
                    "deterministic_result_ordering",
                    "postSlice_on_window_alias"));
            result.put("ranking_contract", rankingContract());
            return result;
        }

        private Map<String, Object> rankingContract() {
            Map<String, Object> contract = new LinkedHashMap<>();
            contract.put("rank_function", "rank");
            contract.put("allowed_rank_functions", List.of("rank"));
            contract.put("order_metric", metricAlias);
            contract.put("order_direction", "DESC");
            contract.put("deterministic_tie_breakers", groupBy);
            contract.put("running_total_frame", "rows_unbounded_preceding_to_current_row");
            contract.put("postSlice_allowed_aliases", List.of(cumulativeAlias));
            contract.put("postSlice_allowed_ops", List.of("<", "<="));
            contract.put("unsupported_rank_functions", List.of(
                    "dense_rank", "row_number", "percent_rank", "cume_dist", "ntile"));
            return contract;
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
                                             List<MetricArithmeticDerived> arithmetic,
                                             List<ResultStageDerivedMetrics> derivedStages,
                                             List<ResultStageFilter> filters,
                                             List<SemanticQueryRequest.OrderItem> orderBy,
                                             Integer limit) {

        public ResultStageMetricRatioPlan(List<String> output, List<String> groupBy, List<String> metricAliases,
                                          List<MetricRatioDerived> ratios,
                                          List<MetricArithmeticDerived> arithmetic,
                                          List<ResultStageFilter> filters,
                                          List<SemanticQueryRequest.OrderItem> orderBy,
                                          Integer limit) {
            this(output, groupBy, metricAliases, ratios, arithmetic,
                    List.of(new ResultStageDerivedMetrics(ratios == null ? List.of() : ratios,
                            arithmetic == null ? List.of() : arithmetic)),
                    filters, orderBy, limit);
        }

        public ResultStageMetricRatioPlan {
            output = output == null ? List.of() : List.copyOf(output);
            groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
            metricAliases = metricAliases == null ? List.of() : List.copyOf(metricAliases);
            ratios = ratios == null ? List.of() : List.copyOf(ratios);
            arithmetic = arithmetic == null ? List.of() : List.copyOf(arithmetic);
            List<ResultStageDerivedMetrics> normalizedStages = derivedStages == null ? List.of()
                    : derivedStages.stream().filter(stage -> stage != null && !stage.empty()).toList();
            if (normalizedStages.isEmpty() && (!ratios.isEmpty() || !arithmetic.isEmpty())) {
                normalizedStages = List.of(new ResultStageDerivedMetrics(ratios, arithmetic));
            }
            derivedStages = List.copyOf(normalizedStages);
            filters = filters == null ? List.of() : List.copyOf(filters);
            orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
        }

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            boolean hasCaseLabel = arithmetic.stream()
                    .anyMatch(item -> "relation_metric_case_label".equals(item.kind()));
            boolean hasOrderedBucket = arithmetic.stream()
                    .anyMatch(item -> "relation_metric_ordered_bucket".equals(item.kind()));
            result.put("kind", arithmetic.stream().anyMatch(item -> item.kind().startsWith("funnel_drop_off"))
                    ? "funnel_drop_off"
                    : ratios.stream().anyMatch(ratio -> signedFunnelRatioAlias(
                    ratio.numeratorAlias(), ratio.denominatorAlias(), ratio.ratioAlias()))
                    ? "funnel_conversion_rate"
                    : ratios.stream().anyMatch(ratio -> signedSlaRatioAlias(
                    ratio.numeratorAlias(), ratio.denominatorAlias(), ratio.ratioAlias()))
                    ? "sla_metric_ratio"
                    : hasOrderedBucket
                    ? "relation_metric_ordered_bucket"
                    : hasCaseLabel
                    ? "relation_metric_case_label"
                    : !arithmetic.isEmpty()
                    ? "relation_metric_arithmetic"
                    : "relation_metric_ratio");
            result.put("bridge_scope", "result_stage_metric_ratio");
            result.put("bridge_signed", true);
            if (!ratios.isEmpty()) {
                MetricRatioDerived primaryRatio = ratios.get(0);
                result.put("numerator", primaryRatio.numeratorAlias());
                result.put("denominator", primaryRatio.denominatorAlias());
                result.put("ratio_alias", primaryRatio.ratioAlias());
            }
            result.put("ratios", ratios.stream()
                    .map(ratio -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("numerator", ratio.numeratorAlias());
                        item.put("denominator", ratio.denominatorAlias());
                        item.put("ratio_alias", ratio.ratioAlias());
                        return item;
                    })
                    .toList());
            result.put("arithmetic", arithmetic.stream()
                    .map(derived -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("alias", derived.alias());
                        item.put("kind", derived.kind());
                        if (!derived.descriptor().isEmpty()) {
                            item.put("descriptor", derived.descriptor());
                        }
                        return item;
                    })
                    .toList());
            if (hasOrderedBucket) {
                result.put("bucket_contracts", orderedBucketContracts());
                result.put("required_capabilities", List.of(
                        "ordered_numeric_bucket_case",
                        "single_visible_numeric_source_alias",
                        "short_literal_bucket_labels",
                        "equality_only_label_postSlice",
                        "output_schema_availability_guard"));
            }
            result.put("derived_stages", derivedStages.stream()
                    .map(stage -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("ratios", stage.ratios().stream().map(MetricRatioDerived::ratioAlias).toList());
                        item.put("arithmetic", stage.arithmetic().stream()
                                .map(MetricArithmeticDerived::alias).toList());
                        return item;
                    })
                    .toList());
            result.put("postSlice_filters", filters.size());
            result.put("orderBy", orderBy.stream()
                    .map(item -> {
                        Map<String, Object> order = new LinkedHashMap<>();
                        order.put("field", item.getField());
                        order.put("dir", item.getDir());
                        return order;
                    })
                    .toList());
            result.put("limit", limit);
            return result;
        }

        private List<Map<String, Object>> orderedBucketContracts() {
            return arithmetic.stream()
                    .filter(item -> "relation_metric_ordered_bucket".equals(item.kind()))
                    .map(item -> {
                        Map<String, Object> contract = new LinkedHashMap<>();
                        contract.put("alias", item.alias());
                        contract.put("kind", item.kind());
                        contract.putAll(item.descriptor());
                        return contract;
                    })
                    .toList();
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
            sql.append(baseAlias).append(" AS (\n").append(baseSql).append("\n),\n");
            String currentAlias = baseAlias;
            List<String> visibleFields = new ArrayList<>();
            visibleFields.addAll(groupBy);
            visibleFields.addAll(metricAliases);
            List<ResultStageDerivedMetrics> stagesToRender = derivedStages.isEmpty()
                    ? List.of(new ResultStageDerivedMetrics(ratios, arithmetic))
                    : derivedStages;
            for (int i = 0; i < stagesToRender.size(); i++) {
                if (i > 0) {
                    sql.append(",\n");
                }
                ResultStageDerivedMetrics stage = stagesToRender.get(i);
                String stageAlias = i == stagesToRender.size() - 1
                        ? "dsl_cte_metric_ratio"
                        : "dsl_cte_metric_ratio_" + (i + 1);
                sql.append(stageAlias).append(" AS (\n");
                sql.append("SELECT ");
                List<String> selectItems = new ArrayList<>();
                for (String field : visibleFields) {
                    selectItems.add(quoteAlias(field));
                }
                for (MetricRatioDerived ratio : stage.ratios()) {
                    selectItems.add("(1.0 * " + quoteAlias(ratio.numeratorAlias()) + " / NULLIF("
                            + quoteAlias(ratio.denominatorAlias()) + ", 0)) AS " + quoteAlias(ratio.ratioAlias()));
                }
                for (MetricArithmeticDerived derived : stage.arithmetic()) {
                    selectItems.add(derived.sqlExpression() + " AS " + quoteAlias(derived.alias()));
                }
                sql.append(String.join(", ", selectItems));
                sql.append("\nFROM ").append(currentAlias).append("\n)");
                visibleFields.addAll(stage.aliases());
                currentAlias = stageAlias;
            }
            sql.append("\n");

            sql.append("SELECT ");
            sql.append(String.join(", ", output.stream().map(DslCteDslRequestMapper::quoteAlias).toList()));
            sql.append("\nFROM ").append(currentAlias);
            if (!filters.isEmpty()) {
                sql.append("\nWHERE ");
                List<String> whereParts = new ArrayList<>();
                for (ResultStageFilter filter : filters) {
                    whereParts.add(quoteAlias(filter.field()) + " " + filter.op() + " ?");
                    params.add(filter.value());
                }
                sql.append(String.join(" AND ", whereParts));
            }
            if (!orderBy.isEmpty()) {
                sql.append("\nORDER BY ");
                sql.append(orderBy.stream()
                        .map(item -> quoteAlias(item.getField()) + " " + item.getDir().toUpperCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.joining(", ")));
            } else if (!groupBy.isEmpty()) {
                sql.append("\nORDER BY ");
                sql.append(groupBy.stream()
                        .map(field -> quoteAlias(field) + " ASC")
                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            if (limit != null) {
                sql.append("\nLIMIT ?");
                params.add(limit);
            }
            return new SqlGenerationResult(sql.toString(), params, null);
        }
    }

    public record CrossModelJoinAlignPlan(List<String> output,
                                          List<String> joinOutput,
                                          List<String> leftFields,
                                          List<String> rightFields,
                                          String leftKey,
                                          String rightKey,
                                          String leftMetric,
                                          String rightMetric,
                                          String sourceField,
                                          boolean rejectNullLeftKeys,
                                          String relationRef,
                                          String cardinality,
                                          String nullKeyPolicy) {

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "cross_model_join_align");
            result.put("bridge_scope", "runtime_guarded_join_align");
            result.put("bridge_signed", true);
            result.put("relationRef", relationRef);
            result.put("cardinality", cardinality);
            result.put("leftKey", leftKey);
            result.put("rightKey", rightKey);
            result.put("runtime_guard_sql", true);
            result.put("null_key_policy", nullKeyPolicy);
            result.put("time_attribution_source_field", sourceField);
            result.put("output", output);
            return result;
        }

        SqlGenerationResult wrap(SqlGenerationResult leftBase, SqlGenerationResult rightBase) {
            validateBaseSql(leftBase, "LEFT");
            validateBaseSql(rightBase, "RIGHT");

            String leftSql = leftBase.getSql().trim();
            String rightSql = rightBase.getSql().trim();
            List<Object> params = new ArrayList<>();
            params.addAll(leftBase.getParams());
            params.addAll(rightBase.getParams());

            String leftAlias = "dsl_cte_join_left";
            String rightAlias = "dsl_cte_join_right";
            String guardAlias = "dsl_cte_join_guard";
            String joinAlias = "dsl_cte_join_align";

            StringBuilder sql = new StringBuilder("WITH ");
            sql.append(leftAlias).append(" AS (\n").append(leftSql).append("\n),\n");
            sql.append(rightAlias).append(" AS (\n").append(rightSql).append("\n),\n");
            sql.append(guardAlias).append(" AS (\n");
            sql.append("SELECT ");
            sql.append("(SELECT COUNT(*) FROM ").append(rightAlias)
                    .append(" WHERE ").append(quoteAlias(rightMetric)).append(" > 1) AS ")
                    .append(quoteAlias("duplicateRightKeys")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l LEFT JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(leftKey)).append(" = r.").append(quoteAlias(rightKey))
                    .append(" WHERE l.").append(quoteAlias(leftKey)).append(" IS NOT NULL AND r.")
                    .append(quoteAlias(rightKey)).append(" IS NULL) AS ")
                    .append(quoteAlias("unmatchedLeftKeys")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias)
                    .append(" WHERE ").append(quoteAlias(leftKey)).append(" IS NULL) AS ")
                    .append(quoteAlias("nullLeftKeys")).append(", ");
            if (rejectNullLeftKeys) {
                sql.append("(SELECT COUNT(*) FROM ").append(leftAlias)
                        .append(" WHERE ").append(quoteAlias(leftKey)).append(" IS NULL) AS ")
                        .append(quoteAlias("rejectedNullLeftKeys")).append(", ");
            } else {
                sql.append("0 AS ").append(quoteAlias("rejectedNullLeftKeys")).append(", ");
            }
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(leftKey)).append(" = r.").append(quoteAlias(rightKey))
                    .append(" WHERE l.").append(quoteAlias(sourceField)).append(" IS NULL) AS ")
                    .append(quoteAlias("missingAttributionRows")).append("\n");
            sql.append("),\n");

            sql.append(joinAlias).append(" AS (\n");
            sql.append("SELECT ");
            List<String> selectItems = new ArrayList<>();
            for (String field : joinOutput) {
                selectItems.add(qualifiedField(field) + " AS " + quoteAlias(field));
            }
            sql.append(String.join(", ", selectItems));
            sql.append("\nFROM ").append(leftAlias).append(" l\n");
            sql.append("JOIN ").append(rightAlias).append(" r ON l.")
                    .append(quoteAlias(leftKey)).append(" = r.").append(quoteAlias(rightKey)).append("\n");
            sql.append("CROSS JOIN ").append(guardAlias).append(" g\n");
            sql.append("WHERE l.").append(quoteAlias(leftKey)).append(" IS NOT NULL\n");
            sql.append("  AND g.").append(quoteAlias("duplicateRightKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("unmatchedLeftKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("rejectedNullLeftKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("missingAttributionRows")).append(" = 0\n");
            sql.append(")\n");

            sql.append("SELECT ");
            sql.append(String.join(", ", output.stream().map(DslCteDslRequestMapper::quoteAlias).toList()));
            sql.append("\nFROM ").append(joinAlias);
            List<String> orderBy = deterministicOrderBy();
            if (!orderBy.isEmpty()) {
                sql.append("\nORDER BY ").append(String.join(", ", orderBy));
            }
            return new SqlGenerationResult(sql.toString(), params, null);
        }

        private static void validateBaseSql(SqlGenerationResult base, String side) {
            if (base == null || base.getSql() == null || base.getSql().isBlank()) {
                throw RX.throwB("DSL_CTE_JOIN_ALIGN_" + side + "_BASE_SQL_MISSING");
            }
            String sql = base.getSql().trim();
            if (base.hasCteStages() || sql.regionMatches(true, 0, "WITH ", 0, 5)) {
                throw RX.throwB("DSL_CTE_JOIN_ALIGN_" + side + "_BASE_WITH_UNSUPPORTED");
            }
        }

        private String qualifiedField(String field) {
            if (leftFields.contains(field)) {
                return "l." + quoteAlias(field);
            }
            if (rightFields.contains(field)) {
                return "r." + quoteAlias(field);
            }
            throw RX.throwB("DSL_CTE_JOIN_ALIGN_UNAVAILABLE_FIELD: " + field);
        }

        private List<String> deterministicOrderBy() {
            List<String> order = new ArrayList<>();
            if (joinOutput.contains("leadSource")) {
                order.add(quoteAlias("leadSource") + " ASC");
            }
            if (joinOutput.contains(leftKey)) {
                order.add(quoteAlias(leftKey) + " ASC");
            }
            if (!leftKey.equals(rightKey) && joinOutput.contains(rightKey)) {
                order.add(quoteAlias(rightKey) + " ASC");
            }
            return order;
        }
    }

    public record CrossModelFunnelSourceRatePlan(List<String> output,
                                                 CrossModelJoinAlignPlan joinPlan,
                                                 String groupKey,
                                                 String denominatorMetric,
                                                 String matchedMetric,
                                                 String rateAlias) {

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "cross_model_funnel_source_rate");
            result.put("bridge_scope", "runtime_guarded_source_rate");
            result.put("bridge_signed", true);
            result.put("relationRef", joinPlan.relationRef());
            result.put("cardinality", joinPlan.cardinality());
            result.put("groupBy", List.of(groupKey));
            result.put("denominator", denominatorMetric);
            result.put("numerator", matchedMetric);
            result.put("ratio_alias", rateAlias);
            result.put("runtime_guard_sql", true);
            result.put("null_key_policy", joinPlan.nullKeyPolicy());
            result.put("time_attribution_source_field", joinPlan.sourceField());
            result.put("output", output);
            return result;
        }

        SqlGenerationResult wrap(SqlGenerationResult denominatorBase,
                                 SqlGenerationResult leftBase,
                                 SqlGenerationResult rightBase) {
            validateBaseSql(denominatorBase, "DENOMINATOR");
            validateBaseSql(leftBase, "LEFT");
            validateBaseSql(rightBase, "RIGHT");

            String denominatorSql = denominatorBase.getSql().trim();
            String leftSql = leftBase.getSql().trim();
            String rightSql = rightBase.getSql().trim();
            List<Object> params = new ArrayList<>();
            params.addAll(denominatorBase.getParams());
            params.addAll(leftBase.getParams());
            params.addAll(rightBase.getParams());

            String denominatorAlias = "dsl_cte_funnel_denominator";
            String leftAlias = "dsl_cte_join_left";
            String rightAlias = "dsl_cte_join_right";
            String guardAlias = "dsl_cte_join_guard";
            String joinAlias = "dsl_cte_join_align";
            String matchedAlias = "dsl_cte_funnel_matched";
            String rateAliasName = "dsl_cte_funnel_rate";

            StringBuilder sql = new StringBuilder("WITH ");
            sql.append(denominatorAlias).append(" AS (\n").append(denominatorSql).append("\n),\n");
            sql.append(leftAlias).append(" AS (\n").append(leftSql).append("\n),\n");
            sql.append(rightAlias).append(" AS (\n").append(rightSql).append("\n),\n");
            appendGuardCte(sql, leftAlias, rightAlias, guardAlias);
            sql.append(",\n");
            appendJoinAlignCte(sql, leftAlias, rightAlias, guardAlias, joinAlias);
            sql.append(",\n");
            sql.append(matchedAlias).append(" AS (\n");
            sql.append("SELECT ").append(quoteAlias(groupKey)).append(", SUM(")
                    .append(quoteAlias(joinPlan.leftMetric())).append(") AS ").append(quoteAlias(matchedMetric))
                    .append("\nFROM ").append(joinAlias).append("\nGROUP BY ").append(quoteAlias(groupKey))
                    .append("\n),\n");
            sql.append(rateAliasName).append(" AS (\n");
            sql.append("SELECT d.").append(quoteAlias(groupKey)).append(" AS ").append(quoteAlias(groupKey))
                    .append(", d.").append(quoteAlias(denominatorMetric)).append(" AS ")
                    .append(quoteAlias(denominatorMetric))
                    .append(", COALESCE(m.").append(quoteAlias(matchedMetric)).append(", 0) AS ")
                    .append(quoteAlias(matchedMetric))
                    .append(", (1.0 * COALESCE(m.").append(quoteAlias(matchedMetric)).append(", 0) / NULLIF(d.")
                    .append(quoteAlias(denominatorMetric)).append(", 0)) AS ").append(quoteAlias(rateAlias))
                    .append("\nFROM ").append(denominatorAlias).append(" d\n")
                    .append("LEFT JOIN ").append(matchedAlias).append(" m ON d.").append(quoteAlias(groupKey))
                    .append(" = m.").append(quoteAlias(groupKey)).append("\n")
                    .append("CROSS JOIN ").append(guardAlias).append(" g\n")
                    .append("WHERE g.").append(quoteAlias("duplicateRightKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("unmatchedLeftKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("rejectedNullLeftKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("missingAttributionRows")).append(" = 0\n")
                    .append(")\n");

            sql.append("SELECT ");
            sql.append(String.join(", ", output.stream().map(DslCteDslRequestMapper::quoteAlias).toList()));
            sql.append("\nFROM ").append(rateAliasName);
            sql.append("\nORDER BY ").append(quoteAlias(groupKey)).append(" ASC");
            return new SqlGenerationResult(sql.toString(), params, null);
        }

        private void appendGuardCte(StringBuilder sql, String leftAlias, String rightAlias, String guardAlias) {
            sql.append(guardAlias).append(" AS (\n");
            sql.append("SELECT ");
            sql.append("(SELECT COUNT(*) FROM ").append(rightAlias)
                    .append(" WHERE ").append(quoteAlias(joinPlan.rightMetric())).append(" > 1) AS ")
                    .append(quoteAlias("duplicateRightKeys")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l LEFT JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE l.").append(quoteAlias(joinPlan.leftKey())).append(" IS NOT NULL AND r.")
                    .append(quoteAlias(joinPlan.rightKey())).append(" IS NULL) AS ")
                    .append(quoteAlias("unmatchedLeftKeys")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias)
                    .append(" WHERE ").append(quoteAlias(joinPlan.leftKey())).append(" IS NULL) AS ")
                    .append(quoteAlias("nullLeftKeys")).append(", ");
            if (joinPlan.rejectNullLeftKeys()) {
                sql.append("(SELECT COUNT(*) FROM ").append(leftAlias)
                        .append(" WHERE ").append(quoteAlias(joinPlan.leftKey())).append(" IS NULL) AS ")
                        .append(quoteAlias("rejectedNullLeftKeys")).append(", ");
            } else {
                sql.append("0 AS ").append(quoteAlias("rejectedNullLeftKeys")).append(", ");
            }
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE l.").append(quoteAlias(joinPlan.sourceField())).append(" IS NULL) AS ")
                    .append(quoteAlias("missingAttributionRows")).append("\n");
            sql.append(")");
        }

        private void appendJoinAlignCte(StringBuilder sql, String leftAlias, String rightAlias,
                                        String guardAlias, String joinAlias) {
            sql.append(joinAlias).append(" AS (\n");
            sql.append("SELECT ");
            List<String> selectItems = new ArrayList<>();
            for (String field : joinPlan.joinOutput()) {
                selectItems.add(qualifiedJoinField(field) + " AS " + quoteAlias(field));
            }
            sql.append(String.join(", ", selectItems));
            sql.append("\nFROM ").append(leftAlias).append(" l\n");
            sql.append("JOIN ").append(rightAlias).append(" r ON l.")
                    .append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey())).append("\n");
            sql.append("CROSS JOIN ").append(guardAlias).append(" g\n");
            sql.append("WHERE l.").append(quoteAlias(joinPlan.leftKey())).append(" IS NOT NULL\n");
            sql.append("  AND g.").append(quoteAlias("duplicateRightKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("unmatchedLeftKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("rejectedNullLeftKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("missingAttributionRows")).append(" = 0\n");
            sql.append(")");
        }

        private String qualifiedJoinField(String field) {
            if (joinPlan.leftFields().contains(field)) {
                return "l." + quoteAlias(field);
            }
            if (joinPlan.rightFields().contains(field)) {
                return "r." + quoteAlias(field);
            }
            throw RX.throwB("DSL_CTE_FUNNEL_SOURCE_RATE_UNAVAILABLE_FIELD: " + field);
        }

        private static void validateBaseSql(SqlGenerationResult base, String side) {
            if (base == null || base.getSql() == null || base.getSql().isBlank()) {
                throw RX.throwB("DSL_CTE_FUNNEL_SOURCE_RATE_" + side + "_BASE_SQL_MISSING");
            }
            String sql = base.getSql().trim();
            if (base.hasCteStages() || sql.regionMatches(true, 0, "WITH ", 0, 5)) {
                throw RX.throwB("DSL_CTE_FUNNEL_SOURCE_RATE_" + side + "_BASE_WITH_UNSUPPORTED");
            }
        }
    }

    public record CrossModelFunnelMoneyAttributionBridgePlan(List<String> output,
                                                             CrossModelJoinAlignPlan joinPlan,
                                                             String targetField,
                                                             int windowDays,
                                                             String windowOrder,
                                                             String groupKey,
                                                             String orderAmountMetric,
                                                             String convertedAmountMetric,
                                                             List<String> targetPeriodStageFields,
                                                             String targetPeriodGrain,
                                                             List<String> targetPeriodOutputFields,
                                                             MoneyAmountShareContract amountShareContract,
                                                             MoneyAmountPerLeadContract amountPerLeadContract) {

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", moneyAttributionKind(
                    "cross_model_funnel_target_year_month_money_attribution",
                    amountShareContract,
                    amountPerLeadContract));
            result.put("bridge_scope", moneyAttributionKind(
                    "runtime_guarded_target_year_month_money_attribution",
                    amountShareContract,
                    amountPerLeadContract));
            result.put("bridge_signed", true);
            result.put("execution_bridge", true);
            result.put("relationRef", joinPlan.relationRef());
            result.put("cardinality", joinPlan.cardinality());
            result.put("source_cohort", Map.of(
                    "field", joinPlan.sourceField()));
            result.put("target_event", Map.of(
                    "field", targetField,
                    "grain", "date"));
            result.put("conversion_window", Map.of(
                    "unit", "day",
                    "size", windowDays,
                    "order", windowOrder,
                    "boundary", "inclusive_start_exclusive_end"));
            result.put("targetPeriod", Map.of(
                    "stageField", firstOrNull(targetPeriodStageFields),
                    "stageFields", targetPeriodStageFields,
                    "grain", targetPeriodGrain,
                    "outputField", firstOrNull(targetPeriodOutputFields),
                    "outputFields", targetPeriodOutputFields));
            result.put("groupBy", expectedTargetPeriodGroupBy(groupKey, targetPeriodStageFields));
            result.put("amount", Map.of(
                    "field", "FactOrderQueryModel.amount",
                    "aggregation", "sum",
                    "source_metric", orderAmountMetric,
                    "metric", convertedAmountMetric));
            result.put("orderSelection", "converted_order_id_only");
            result.put("deduplication", "dedupe_order_id_after_signed_relation");
            result.put("orderStatusScope", "completed_paid_orders");
            result.put("currencyScope", "single_currency_no_conversion");
            result.put("runtime_guard_sql", true);
            result.put("null_key_policy", joinPlan.nullKeyPolicy());
            result.put("time_boundary_guard", true);
            result.put("cross_source_duplicate_order_guard", true);
            if (amountShareContract.declared()) {
                result.put("derivedMetric", amountShareContract.summary());
                result.put("denominator_scope", amountShareContract.denominatorScope());
                result.put("numerator", amountShareContract.numeratorMetric());
                result.put("denominator", amountShareContract.denominatorMetric());
                result.put("ratio_alias", amountShareContract.ratioAlias());
            } else if (amountPerLeadContract.declared()) {
                result.put("derivedMetric", amountPerLeadContract.summary());
                result.put("denominator_scope", amountPerLeadContract.denominatorScope());
                result.put("numerator", amountPerLeadContract.numeratorMetric());
                result.put("denominator", amountPerLeadContract.denominatorMetric());
                result.put("ratio_alias", amountPerLeadContract.ratioAlias());
                result.put("source_denominator", Map.of(
                        "model", "CrmLead",
                        "grain", "one_row_per_lead",
                        "groupBy", List.of(groupKey),
                        "execution_metric", "count(leadId)",
                        "semantic_metric", amountPerLeadContract.denominatorMetric()));
            }
            result.put("output", output);
            return result;
        }

        SqlGenerationResult wrap(SqlGenerationResult leftBase, SqlGenerationResult rightBase) {
            return wrap(null, leftBase, rightBase);
        }

        SqlGenerationResult wrap(SqlGenerationResult denominatorBase,
                                 SqlGenerationResult leftBase,
                                 SqlGenerationResult rightBase) {
            if (amountPerLeadContract.declared()) {
                validateBaseSql(denominatorBase, "DENOMINATOR");
            }
            validateBaseSql(leftBase, "LEFT");
            validateBaseSql(rightBase, "RIGHT");

            String denominatorSql = amountPerLeadContract.declared()
                    ? denominatorBase.getSql().trim()
                    : null;
            String leftSql = leftBase.getSql().trim();
            String rightSql = rightBase.getSql().trim();
            BridgeSqlDialect dialect = detectBridgeSqlDialect(denominatorBase, leftBase, rightBase);
            List<Object> params = new ArrayList<>();
            if (amountPerLeadContract.declared()) {
                params.addAll(denominatorBase.getParams());
            }
            params.addAll(leftBase.getParams());
            params.addAll(rightBase.getParams());

            String denominatorAlias = "dsl_cte_source_denominator";
            String leftAlias = "dsl_cte_join_left";
            String rightAlias = "dsl_cte_join_right";
            String guardAlias = "dsl_cte_join_guard";
            String joinAlias = "dsl_cte_join_align";
            String amountGuardAlias = "dsl_cte_funnel_amount_guard";
            String dedupedAlias = "dsl_cte_funnel_order_deduped";
            String convertedAmountAlias = "dsl_cte_funnel_converted_amount";
            String finalAlias = convertedAmountAlias;

            StringBuilder sql = new StringBuilder("WITH ");
            if (amountPerLeadContract.declared()) {
                sql.append(denominatorAlias).append(" AS (\n").append(denominatorSql).append("\n),\n");
            }
            sql.append(leftAlias).append(" AS (\n").append(leftSql).append("\n),\n");
            sql.append(rightAlias).append(" AS (\n").append(rightSql).append("\n),\n");
            appendGuardCte(sql, leftAlias, rightAlias, guardAlias, dialect);
            sql.append(",\n");
            appendWindowJoinAlignCte(sql, leftAlias, rightAlias, guardAlias, joinAlias, dialect);
            params.add(windowDays);
            sql.append(",\n");
            appendAmountGuardCte(sql, joinAlias, amountGuardAlias);
            sql.append(",\n");
            appendOrderDedupedCte(sql, joinAlias, amountGuardAlias, dedupedAlias);
            sql.append(",\n");
            appendConvertedAmountCte(sql, dedupedAlias, convertedAmountAlias);
            if (amountShareContract.declared()) {
                finalAlias = "dsl_cte_funnel_amount_share";
                sql.append(",\n");
                appendAmountShareCte(sql, convertedAmountAlias, finalAlias);
            } else if (amountPerLeadContract.declared()) {
                finalAlias = "dsl_cte_funnel_amount_per_lead";
                sql.append(",\n");
                appendAmountPerLeadCte(sql, denominatorAlias, convertedAmountAlias, finalAlias);
            }

            sql.append("SELECT ");
            sql.append(String.join(", ", output.stream().map(DslCteDslRequestMapper::quoteAlias).toList()));
            sql.append("\nFROM ").append(finalAlias);
            sql.append("\nORDER BY ").append(quoteAlias(groupKey)).append(" ASC");
            for (String targetPeriodStageField : targetPeriodStageFields) {
                sql.append(", ").append(quoteAlias(targetPeriodStageField)).append(" ASC");
            }
            return new SqlGenerationResult(sql.toString(), params, null);
        }

        private void appendAmountGuardCte(StringBuilder sql, String joinAlias, String amountGuardAlias) {
            sql.append(amountGuardAlias).append(" AS (\n");
            sql.append("SELECT (SELECT COUNT(*) FROM (SELECT ").append(quoteAlias(joinPlan.rightKey()))
                    .append(" FROM ").append(joinAlias)
                    .append(" GROUP BY ").append(quoteAlias(joinPlan.rightKey()))
                    .append(" HAVING COUNT(DISTINCT ").append(quoteAlias(groupKey)).append(") > 1")
                    .append(") cross_source_orders) AS ")
                    .append(quoteAlias("crossSourceDuplicateOrders")).append("\n");
            sql.append(")");
        }

        private void appendOrderDedupedCte(StringBuilder sql, String joinAlias,
                                           String amountGuardAlias, String dedupedAlias) {
            sql.append(dedupedAlias).append(" AS (\n");
            List<String> groupFields = new ArrayList<>();
            groupFields.add(groupKey);
            groupFields.addAll(targetPeriodStageFields);
            groupFields.add(joinPlan.rightKey());
            sql.append("SELECT ");
            List<String> selectItems = new ArrayList<>();
            for (String field : groupFields) {
                selectItems.add("j." + quoteAlias(field) + " AS " + quoteAlias(field));
            }
            selectItems.add("MAX(j." + quoteAlias(orderAmountMetric) + ") AS "
                    + quoteAlias("dedupedOrderAmount"));
            sql.append(String.join(", ", selectItems));
            sql.append("\nFROM ").append(joinAlias).append(" j\n");
            sql.append("CROSS JOIN ").append(amountGuardAlias).append(" ag\n");
            sql.append("WHERE ag.").append(quoteAlias("crossSourceDuplicateOrders")).append(" = 0\n");
            sql.append("GROUP BY ");
            sql.append(groupFields.stream()
                    .map(field -> "j." + quoteAlias(field))
                    .collect(java.util.stream.Collectors.joining(", ")));
            sql.append("\n)");
        }

        private void appendConvertedAmountCte(StringBuilder sql, String dedupedAlias, String finalAlias) {
            sql.append(finalAlias).append(" AS (\n");
            List<String> groupFields = new ArrayList<>();
            groupFields.add(groupKey);
            groupFields.addAll(targetPeriodStageFields);
            sql.append("SELECT ");
            List<String> selectItems = new ArrayList<>();
            for (String field : groupFields) {
                selectItems.add(quoteAlias(field));
            }
            selectItems.add("SUM(" + quoteAlias("dedupedOrderAmount") + ") AS "
                    + quoteAlias(convertedAmountMetric));
            sql.append(String.join(", ", selectItems))
                    .append("\nFROM ").append(dedupedAlias).append("\nGROUP BY ")
                    .append(groupFields.stream().map(DslCteDslRequestMapper::quoteAlias)
                            .collect(java.util.stream.Collectors.joining(", ")))
                    .append("\n)");
        }

        private void appendAmountShareCte(StringBuilder sql, String convertedAmountAlias, String shareAlias) {
            sql.append(shareAlias).append(" AS (\n");
            List<String> groupFields = new ArrayList<>();
            groupFields.add(groupKey);
            groupFields.addAll(targetPeriodStageFields);
            List<String> selectItems = new ArrayList<>();
            for (String field : groupFields) {
                selectItems.add(quoteAlias(field));
            }
            selectItems.add(quoteAlias(convertedAmountMetric));
            String denominatorExpr = "SUM(" + quoteAlias(convertedAmountMetric) + ") OVER (PARTITION BY "
                    + targetPeriodStageFields.stream().map(DslCteDslRequestMapper::quoteAlias)
                    .collect(java.util.stream.Collectors.joining(", ")) + ")";
            selectItems.add(denominatorExpr + " AS " + quoteAlias(amountShareContract.denominatorMetric()));
            selectItems.add("(1.0 * " + quoteAlias(convertedAmountMetric) + " / NULLIF("
                    + denominatorExpr + ", 0)) AS " + quoteAlias(amountShareContract.ratioAlias()));
            sql.append("SELECT ").append(String.join(", ", selectItems))
                    .append("\nFROM ").append(convertedAmountAlias)
                    .append("\n)");
        }

        private void appendAmountPerLeadCte(StringBuilder sql, String denominatorAlias,
                                            String convertedAmountAlias, String amountPerLeadAlias) {
            sql.append(amountPerLeadAlias).append(" AS (\n");
            List<String> selectItems = new ArrayList<>();
            selectItems.add("m." + quoteAlias(groupKey) + " AS " + quoteAlias(groupKey));
            for (String field : targetPeriodStageFields) {
                selectItems.add("m." + quoteAlias(field) + " AS " + quoteAlias(field));
            }
            selectItems.add("m." + quoteAlias(convertedAmountMetric) + " AS "
                    + quoteAlias(convertedAmountMetric));
            selectItems.add("d." + quoteAlias(amountPerLeadContract.denominatorMetric()) + " AS "
                    + quoteAlias(amountPerLeadContract.denominatorMetric()));
            selectItems.add("(1.0 * m." + quoteAlias(convertedAmountMetric) + " / NULLIF(d."
                    + quoteAlias(amountPerLeadContract.denominatorMetric()) + ", 0)) AS "
                    + quoteAlias(amountPerLeadContract.ratioAlias()));
            sql.append("SELECT ").append(String.join(", ", selectItems))
                    .append("\nFROM ").append(convertedAmountAlias).append(" m\n")
                    .append("JOIN ").append(denominatorAlias).append(" d ON d.")
                    .append(quoteAlias(groupKey)).append(" = m.").append(quoteAlias(groupKey))
                    .append("\n)");
        }

        private void appendGuardCte(StringBuilder sql, String leftAlias, String rightAlias, String guardAlias,
                                    BridgeSqlDialect dialect) {
            sql.append(guardAlias).append(" AS (\n");
            sql.append("SELECT ");
            sql.append("(SELECT COUNT(*) FROM (SELECT ").append(quoteAlias(joinPlan.rightKey()))
                    .append(" FROM ").append(rightAlias).append(" GROUP BY ").append(quoteAlias(joinPlan.rightKey()))
                    .append(" HAVING COUNT(*) > 1) duplicate_right_keys) AS ")
                    .append(quoteAlias("duplicateRightKeys")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l LEFT JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE l.").append(quoteAlias(joinPlan.leftKey())).append(" IS NOT NULL AND r.")
                    .append(quoteAlias(joinPlan.rightKey())).append(" IS NULL) AS ")
                    .append(quoteAlias("unmatchedLeftKeys")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias)
                    .append(" WHERE ").append(quoteAlias(joinPlan.leftKey())).append(" IS NULL) AS ")
                    .append(quoteAlias("nullLeftKeys")).append(", ");
            if (joinPlan.rejectNullLeftKeys()) {
                sql.append("(SELECT COUNT(*) FROM ").append(leftAlias)
                        .append(" WHERE ").append(quoteAlias(joinPlan.leftKey())).append(" IS NULL) AS ")
                        .append(quoteAlias("rejectedNullLeftKeys")).append(", ");
            } else {
                sql.append("0 AS ").append(quoteAlias("rejectedNullLeftKeys")).append(", ");
            }
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE l.").append(quoteAlias(joinPlan.sourceField())).append(" IS NULL) AS ")
                    .append(quoteAlias("missingSourceAttributionRows")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE r.").append(quoteAlias(targetField)).append(" IS NULL) AS ")
                    .append(quoteAlias("missingTargetAttributionRows")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE l.").append(quoteAlias(joinPlan.sourceField())).append(" IS NOT NULL")
                    .append(" AND r.").append(quoteAlias(targetField)).append(" IS NOT NULL")
                    .append(" AND ")
                    .append(bridgeDateOnly("r." + quoteAlias(targetField), dialect))
                    .append(" < ")
                    .append(bridgeDateOnly("l." + quoteAlias(joinPlan.sourceField()), dialect))
                    .append(") AS ")
                    .append(quoteAlias("targetBeforeSourceRows")).append("\n");
            sql.append(")");
        }

        private void appendWindowJoinAlignCte(StringBuilder sql, String leftAlias, String rightAlias,
                                              String guardAlias, String joinAlias, BridgeSqlDialect dialect) {
            sql.append(joinAlias).append(" AS (\n");
            sql.append("SELECT ");
            List<String> selectItems = new ArrayList<>();
            for (String field : joinPlan.joinOutput()) {
                selectItems.add(qualifiedJoinField(field) + " AS " + quoteAlias(field));
            }
            sql.append(String.join(", ", selectItems));
            sql.append("\nFROM ").append(leftAlias).append(" l\n");
            sql.append("JOIN ").append(rightAlias).append(" r ON l.")
                    .append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey())).append("\n");
            sql.append("CROSS JOIN ").append(guardAlias).append(" g\n");
            sql.append("WHERE l.").append(quoteAlias(joinPlan.leftKey())).append(" IS NOT NULL\n");
            sql.append("  AND ").append(bridgeDateOnly("r." + quoteAlias(targetField), dialect))
                    .append(" >= ")
                    .append(bridgeDateOnly("l." + quoteAlias(joinPlan.sourceField()), dialect))
                    .append("\n");
            sql.append("  AND ").append(bridgeDateOnly("r." + quoteAlias(targetField), dialect))
                    .append(" < ")
                    .append(bridgeDatePlusDays("l." + quoteAlias(joinPlan.sourceField()), dialect))
                    .append("\n");
            sql.append("  AND g.").append(quoteAlias("duplicateRightKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("unmatchedLeftKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("rejectedNullLeftKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("missingSourceAttributionRows")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("missingTargetAttributionRows")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("targetBeforeSourceRows")).append(" = 0\n");
            sql.append(")");
        }

        private String qualifiedJoinField(String field) {
            if (joinPlan.leftFields().contains(field)) {
                return "l." + quoteAlias(field);
            }
            if (joinPlan.rightFields().contains(field)) {
                return "r." + quoteAlias(field);
            }
            throw RX.throwB("DSL_CTE_FUNNEL_MONEY_ATTRIBUTION_UNAVAILABLE_FIELD: " + field);
        }

        private static void validateBaseSql(SqlGenerationResult base, String side) {
            if (base == null || base.getSql() == null || base.getSql().isBlank()) {
                throw RX.throwB("DSL_CTE_FUNNEL_MONEY_ATTRIBUTION_" + side + "_BASE_SQL_MISSING");
            }
            String sql = base.getSql().trim();
            if (base.hasCteStages() || sql.regionMatches(true, 0, "WITH ", 0, 5)) {
                throw RX.throwB("DSL_CTE_FUNNEL_MONEY_ATTRIBUTION_" + side + "_BASE_WITH_UNSUPPORTED");
            }
        }
    }

    public record CrossModelFunnelTimeAttributionBridgePlan(List<String> output,
                                                            CrossModelJoinAlignPlan joinPlan,
                                                            String targetField,
                                                            int windowDays,
                                                            String windowOrder,
                                                            String groupKey,
                                                            String denominatorMetric,
                                                            String matchedMetric,
                                                            String rateAlias,
                                                            List<String> targetPeriodStageFields,
                                                            String targetPeriodGrain,
                                                            List<String> targetPeriodOutputFields,
                                                            ZeroFillCalendarScaffoldContract zeroFillCalendarScaffold) {

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", targetPeriodAttribution()
                    ? targetPeriodKindSummary()
                    : "cross_model_funnel_time_attribution");
            result.put("bridge_scope", targetPeriodAttribution()
                    ? targetPeriodBridgeScope()
                    : "runtime_guarded_target_event_window");
            result.put("bridge_signed", true);
            result.put("execution_bridge", true);
            result.put("relationRef", joinPlan.relationRef());
            result.put("cardinality", joinPlan.cardinality());
            result.put("source_cohort", Map.of(
                    "field", joinPlan.sourceField()));
            result.put("target_event", Map.of(
                    "field", targetField,
                    "grain", "date"));
            result.put("conversion_window", Map.of(
                    "unit", "day",
                    "size", windowDays,
                    "order", windowOrder,
                    "boundary", "inclusive_start_exclusive_end"));
            if (targetPeriodAttribution()) {
                result.put("targetPeriod", Map.of(
                        "stageField", firstOrNull(targetPeriodStageFields),
                        "stageFields", targetPeriodStageFields,
                        "grain", targetPeriodGrain,
                        "outputField", firstOrNull(targetPeriodOutputFields),
                        "outputFields", targetPeriodOutputFields));
                result.put("denominator_scope", "fixed_per_source_group");
                result.put("numerator_bucket", "targetPeriod");
                result.put("groupBy", expectedTargetPeriodGroupBy(groupKey, targetPeriodStageFields));
                if (zeroFillTargetYearMonthCalendar()) {
                    result.put("calendarScaffold", zeroFillCalendarScaffold.summary(
                            targetPeriodStageFields, targetPeriodOutputFields));
                    result.put("fill_policy", zeroFillCalendarScaffold.fillPolicy());
                    result.put("scaffold_scope", zeroFillCalendarScaffold.scaffoldScope());
                }
            } else {
                result.put("groupBy", List.of(groupKey));
            }
            result.put("denominator", denominatorMetric);
            result.put("numerator", matchedMetric);
            result.put("ratio_alias", rateAlias);
            result.put("runtime_guard_sql", true);
            result.put("null_key_policy", joinPlan.nullKeyPolicy());
            result.put("time_boundary_guard", true);
            result.put("output", output);
            return result;
        }

        private boolean targetPeriodAttribution() {
            return targetPeriodStageFields != null && !targetPeriodStageFields.isEmpty()
                    && targetPeriodGrain != null && !targetPeriodGrain.isBlank()
                    && targetPeriodOutputFields != null && !targetPeriodOutputFields.isEmpty();
        }

        private boolean zeroFillTargetYearMonthCalendar() {
            return targetPeriodAttribution()
                    && zeroFillCalendarScaffold != null
                    && zeroFillCalendarScaffold.declared();
        }

        private String targetPeriodKindSummary() {
            if (zeroFillTargetYearMonthCalendar()) {
                return "cross_model_funnel_target_year_month_zero_fill_calendar";
            }
            return "year_month".equals(targetPeriodGrain)
                    ? "cross_model_funnel_target_year_month_attribution"
                    : "cross_model_funnel_target_month_attribution";
        }

        private String targetPeriodBridgeScope() {
            if (zeroFillTargetYearMonthCalendar()) {
                return "runtime_guarded_target_year_month_zero_fill_calendar";
            }
            return "year_month".equals(targetPeriodGrain)
                    ? "runtime_guarded_target_year_month_attribution"
                    : "runtime_guarded_target_month_attribution";
        }

        SqlGenerationResult wrap(SqlGenerationResult denominatorBase,
                                 SqlGenerationResult leftBase,
                                 SqlGenerationResult rightBase) {
            validateBaseSql(denominatorBase, "DENOMINATOR");
            validateBaseSql(leftBase, "LEFT");
            validateBaseSql(rightBase, "RIGHT");

            String denominatorSql = denominatorBase.getSql().trim();
            String leftSql = leftBase.getSql().trim();
            String rightSql = rightBase.getSql().trim();
            BridgeSqlDialect dialect = detectBridgeSqlDialect(denominatorBase, leftBase, rightBase);
            List<Object> params = new ArrayList<>();
            params.addAll(denominatorBase.getParams());
            params.addAll(leftBase.getParams());
            params.addAll(rightBase.getParams());

            String denominatorAlias = "dsl_cte_funnel_denominator";
            String leftAlias = "dsl_cte_join_left";
            String rightAlias = "dsl_cte_join_right";
            String guardAlias = "dsl_cte_join_guard";
            String joinAlias = "dsl_cte_join_align";
            String matchedAlias = "dsl_cte_funnel_window_matched";
            String calendarAlias = "dsl_cte_calendar_periods";
            String sourcePeriodGridAlias = "dsl_cte_source_period_grid";
            String rateAliasName = zeroFillTargetYearMonthCalendar()
                    ? "dsl_cte_funnel_zero_fill_rate"
                    : "dsl_cte_funnel_rate";

            StringBuilder sql = new StringBuilder("WITH ");
            sql.append(denominatorAlias).append(" AS (\n").append(denominatorSql).append("\n),\n");
            sql.append(leftAlias).append(" AS (\n").append(leftSql).append("\n),\n");
            sql.append(rightAlias).append(" AS (\n").append(rightSql).append("\n),\n");
            appendGuardCte(sql, leftAlias, rightAlias, guardAlias, dialect);
            sql.append(",\n");
            appendWindowJoinAlignCte(sql, leftAlias, rightAlias, guardAlias, joinAlias, dialect);
            params.add(windowDays);
            sql.append(",\n");
            sql.append(matchedAlias).append(" AS (\n");
            List<String> matchedGroupKeys = new ArrayList<>();
            matchedGroupKeys.add(groupKey);
            if (targetPeriodAttribution()) {
                matchedGroupKeys.addAll(targetPeriodStageFields);
            }
            sql.append("SELECT ");
            List<String> matchedSelectItems = new ArrayList<>();
            for (String groupField : matchedGroupKeys) {
                matchedSelectItems.add(quoteAlias(groupField));
            }
            matchedSelectItems.add("SUM(" + quoteAlias(joinPlan.leftMetric()) + ") AS " + quoteAlias(matchedMetric));
            sql.append(String.join(", ", matchedSelectItems))
                    .append("\nFROM ").append(joinAlias).append("\nGROUP BY ")
                    .append(matchedGroupKeys.stream().map(DslCteDslRequestMapper::quoteAlias)
                            .collect(java.util.stream.Collectors.joining(", ")))
                    .append("\n)");
            if (zeroFillTargetYearMonthCalendar()) {
                sql.append(",\n");
                appendCalendarPeriodsCte(sql, calendarAlias);
                sql.append(",\n");
                appendSourcePeriodGridCte(sql, denominatorAlias, calendarAlias, sourcePeriodGridAlias);
            }
            sql.append(",\n");
            sql.append(rateAliasName).append(" AS (\n");
            appendRateCte(sql, denominatorAlias, matchedAlias, guardAlias, sourcePeriodGridAlias);

            sql.append("SELECT ");
            sql.append(String.join(", ", output.stream().map(DslCteDslRequestMapper::quoteAlias).toList()));
            sql.append("\nFROM ").append(rateAliasName);
            sql.append("\nORDER BY ").append(quoteAlias(groupKey)).append(" ASC");
            if (targetPeriodAttribution()) {
                for (String targetPeriodStageField : targetPeriodStageFields) {
                    sql.append(", ").append(quoteAlias(targetPeriodStageField)).append(" ASC");
                }
            }
            return new SqlGenerationResult(sql.toString(), params, null);
        }

        private void appendCalendarPeriodsCte(StringBuilder sql, String calendarAlias) {
            sql.append(calendarAlias).append(" AS (\n");
            for (int i = 0; i < zeroFillCalendarScaffold.periods().size(); i++) {
                YearMonthPeriod period = zeroFillCalendarScaffold.periods().get(i);
                if (i > 0) {
                    sql.append("\nUNION ALL\n");
                }
                sql.append("SELECT ").append(period.year()).append(" AS ")
                        .append(quoteAlias(targetPeriodStageFields.get(0)))
                        .append(", ").append(period.month()).append(" AS ")
                        .append(quoteAlias(targetPeriodStageFields.get(1)));
            }
            sql.append("\n)");
        }

        private void appendSourcePeriodGridCte(StringBuilder sql, String denominatorAlias,
                                               String calendarAlias, String sourcePeriodGridAlias) {
            sql.append(sourcePeriodGridAlias).append(" AS (\n");
            sql.append("SELECT d.").append(quoteAlias(groupKey)).append(" AS ").append(quoteAlias(groupKey));
            for (String targetPeriodStageField : targetPeriodStageFields) {
                sql.append(", c.").append(quoteAlias(targetPeriodStageField)).append(" AS ")
                        .append(quoteAlias(targetPeriodStageField));
            }
            sql.append(", d.").append(quoteAlias(denominatorMetric)).append(" AS ")
                    .append(quoteAlias(denominatorMetric))
                    .append("\nFROM ").append(denominatorAlias).append(" d\n")
                    .append("CROSS JOIN ").append(calendarAlias).append(" c\n")
                    .append(")");
        }

        private void appendRateCte(StringBuilder sql, String denominatorAlias, String matchedAlias,
                                   String guardAlias, String sourcePeriodGridAlias) {
            if (zeroFillTargetYearMonthCalendar()) {
                appendZeroFillRateCte(sql, matchedAlias, guardAlias, sourcePeriodGridAlias);
                return;
            }
            sql.append("SELECT d.").append(quoteAlias(groupKey)).append(" AS ").append(quoteAlias(groupKey));
            if (targetPeriodAttribution()) {
                for (String targetPeriodStageField : targetPeriodStageFields) {
                    sql.append(", m.").append(quoteAlias(targetPeriodStageField)).append(" AS ")
                            .append(quoteAlias(targetPeriodStageField));
                }
            }
            sql.append(", d.").append(quoteAlias(denominatorMetric)).append(" AS ")
                    .append(quoteAlias(denominatorMetric))
                    .append(", COALESCE(m.").append(quoteAlias(matchedMetric)).append(", 0) AS ")
                    .append(quoteAlias(matchedMetric))
                    .append(", (1.0 * COALESCE(m.").append(quoteAlias(matchedMetric)).append(", 0) / NULLIF(d.")
                    .append(quoteAlias(denominatorMetric)).append(", 0)) AS ").append(quoteAlias(rateAlias))
                    .append("\nFROM ").append(denominatorAlias).append(" d\n");
            if (targetPeriodAttribution()) {
                sql.append("JOIN ").append(matchedAlias).append(" m ON d.").append(quoteAlias(groupKey))
                        .append(" = m.").append(quoteAlias(groupKey)).append("\n");
            } else {
                sql.append("LEFT JOIN ").append(matchedAlias).append(" m ON d.").append(quoteAlias(groupKey))
                        .append(" = m.").append(quoteAlias(groupKey)).append("\n");
            }
            sql.append("CROSS JOIN ").append(guardAlias).append(" g\n")
                    .append("WHERE g.").append(quoteAlias("duplicateRightKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("unmatchedLeftKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("rejectedNullLeftKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("missingSourceAttributionRows")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("missingTargetAttributionRows")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("targetBeforeSourceRows")).append(" = 0\n")
                    .append(")\n");
        }

        private void appendZeroFillRateCte(StringBuilder sql, String matchedAlias, String guardAlias,
                                           String sourcePeriodGridAlias) {
            sql.append("SELECT sp.").append(quoteAlias(groupKey)).append(" AS ").append(quoteAlias(groupKey));
            for (String targetPeriodStageField : targetPeriodStageFields) {
                sql.append(", sp.").append(quoteAlias(targetPeriodStageField)).append(" AS ")
                        .append(quoteAlias(targetPeriodStageField));
            }
            sql.append(", sp.").append(quoteAlias(denominatorMetric)).append(" AS ")
                    .append(quoteAlias(denominatorMetric))
                    .append(", COALESCE(m.").append(quoteAlias(matchedMetric)).append(", 0) AS ")
                    .append(quoteAlias(matchedMetric))
                    .append(", (1.0 * COALESCE(m.").append(quoteAlias(matchedMetric)).append(", 0) / NULLIF(sp.")
                    .append(quoteAlias(denominatorMetric)).append(", 0)) AS ").append(quoteAlias(rateAlias))
                    .append("\nFROM ").append(sourcePeriodGridAlias).append(" sp\n")
                    .append("LEFT JOIN ").append(matchedAlias).append(" m ON sp.").append(quoteAlias(groupKey))
                    .append(" = m.").append(quoteAlias(groupKey));
            for (String targetPeriodStageField : targetPeriodStageFields) {
                sql.append(" AND sp.").append(quoteAlias(targetPeriodStageField))
                        .append(" = m.").append(quoteAlias(targetPeriodStageField));
            }
            sql.append("\nCROSS JOIN ").append(guardAlias).append(" g\n")
                    .append("WHERE g.").append(quoteAlias("duplicateRightKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("unmatchedLeftKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("rejectedNullLeftKeys")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("missingSourceAttributionRows")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("missingTargetAttributionRows")).append(" = 0\n")
                    .append("  AND g.").append(quoteAlias("targetBeforeSourceRows")).append(" = 0\n")
                    .append(")\n");
        }

        private void appendGuardCte(StringBuilder sql, String leftAlias, String rightAlias, String guardAlias,
                                    BridgeSqlDialect dialect) {
            sql.append(guardAlias).append(" AS (\n");
            sql.append("SELECT ");
            sql.append("(SELECT COUNT(*) FROM (SELECT ").append(quoteAlias(joinPlan.rightKey()))
                    .append(" FROM ").append(rightAlias).append(" GROUP BY ").append(quoteAlias(joinPlan.rightKey()))
                    .append(" HAVING COUNT(*) > 1) duplicate_right_keys) AS ")
                    .append(quoteAlias("duplicateRightKeys")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l LEFT JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE l.").append(quoteAlias(joinPlan.leftKey())).append(" IS NOT NULL AND r.")
                    .append(quoteAlias(joinPlan.rightKey())).append(" IS NULL) AS ")
                    .append(quoteAlias("unmatchedLeftKeys")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias)
                    .append(" WHERE ").append(quoteAlias(joinPlan.leftKey())).append(" IS NULL) AS ")
                    .append(quoteAlias("nullLeftKeys")).append(", ");
            if (joinPlan.rejectNullLeftKeys()) {
                sql.append("(SELECT COUNT(*) FROM ").append(leftAlias)
                        .append(" WHERE ").append(quoteAlias(joinPlan.leftKey())).append(" IS NULL) AS ")
                        .append(quoteAlias("rejectedNullLeftKeys")).append(", ");
            } else {
                sql.append("0 AS ").append(quoteAlias("rejectedNullLeftKeys")).append(", ");
            }
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE l.").append(quoteAlias(joinPlan.sourceField())).append(" IS NULL) AS ")
                    .append(quoteAlias("missingSourceAttributionRows")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE r.").append(quoteAlias(targetField)).append(" IS NULL) AS ")
                    .append(quoteAlias("missingTargetAttributionRows")).append(", ");
            sql.append("(SELECT COUNT(*) FROM ").append(leftAlias).append(" l JOIN ").append(rightAlias)
                    .append(" r ON l.").append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey()))
                    .append(" WHERE l.").append(quoteAlias(joinPlan.sourceField())).append(" IS NOT NULL")
                    .append(" AND r.").append(quoteAlias(targetField)).append(" IS NOT NULL")
                    .append(" AND ")
                    .append(bridgeDateOnly("r." + quoteAlias(targetField), dialect))
                    .append(" < ")
                    .append(bridgeDateOnly("l." + quoteAlias(joinPlan.sourceField()), dialect))
                    .append(") AS ")
                    .append(quoteAlias("targetBeforeSourceRows")).append("\n");
            sql.append(")");
        }

        private void appendWindowJoinAlignCte(StringBuilder sql, String leftAlias, String rightAlias,
                                              String guardAlias, String joinAlias, BridgeSqlDialect dialect) {
            sql.append(joinAlias).append(" AS (\n");
            sql.append("SELECT ");
            List<String> selectItems = new ArrayList<>();
            for (String field : joinPlan.joinOutput()) {
                selectItems.add(qualifiedJoinField(field) + " AS " + quoteAlias(field));
            }
            sql.append(String.join(", ", selectItems));
            sql.append("\nFROM ").append(leftAlias).append(" l\n");
            sql.append("JOIN ").append(rightAlias).append(" r ON l.")
                    .append(quoteAlias(joinPlan.leftKey())).append(" = r.")
                    .append(quoteAlias(joinPlan.rightKey())).append("\n");
            sql.append("CROSS JOIN ").append(guardAlias).append(" g\n");
            sql.append("WHERE l.").append(quoteAlias(joinPlan.leftKey())).append(" IS NOT NULL\n");
            sql.append("  AND ").append(bridgeDateOnly("r." + quoteAlias(targetField), dialect))
                    .append(" >= ")
                    .append(bridgeDateOnly("l." + quoteAlias(joinPlan.sourceField()), dialect))
                    .append("\n");
            sql.append("  AND ").append(bridgeDateOnly("r." + quoteAlias(targetField), dialect))
                    .append(" < ")
                    .append(bridgeDatePlusDays("l." + quoteAlias(joinPlan.sourceField()), dialect))
                    .append("\n");
            sql.append("  AND g.").append(quoteAlias("duplicateRightKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("unmatchedLeftKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("rejectedNullLeftKeys")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("missingSourceAttributionRows")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("missingTargetAttributionRows")).append(" = 0\n");
            sql.append("  AND g.").append(quoteAlias("targetBeforeSourceRows")).append(" = 0\n");
            sql.append(")");
        }

        private String qualifiedJoinField(String field) {
            if (joinPlan.leftFields().contains(field)) {
                return "l." + quoteAlias(field);
            }
            if (joinPlan.rightFields().contains(field)) {
                return "r." + quoteAlias(field);
            }
            throw RX.throwB("DSL_CTE_FUNNEL_TIME_ATTRIBUTION_UNAVAILABLE_FIELD: " + field);
        }

        private static void validateBaseSql(SqlGenerationResult base, String side) {
            if (base == null || base.getSql() == null || base.getSql().isBlank()) {
                throw RX.throwB("DSL_CTE_FUNNEL_TIME_ATTRIBUTION_" + side + "_BASE_SQL_MISSING");
            }
            String sql = base.getSql().trim();
            if (base.hasCteStages() || sql.regionMatches(true, 0, "WITH ", 0, 5)) {
                throw RX.throwB("DSL_CTE_FUNNEL_TIME_ATTRIBUTION_" + side + "_BASE_WITH_UNSUPPORTED");
            }
        }
    }

    public record CrossModelFunnelTimeAttributionContractPlan(List<String> output,
                                                              String relationRef,
                                                              String cardinality,
                                                              String sourceStage,
                                                              String sourceModel,
                                                              String sourceField,
                                                              String targetStage,
                                                              String targetModel,
                                                              String targetField,
                                                              int windowDays,
                                                              String windowOrder,
                                                              String groupKey,
                                                              String denominatorMetric,
                                                              String matchedMetric,
                                                              String rateAlias,
                                                              String targetPeriodField,
                                                              String targetPeriodGrain,
                                                              List<String> targetPeriodStageFields,
                                                              List<String> targetPeriodOutputFields,
                                                              ZeroFillCalendarScaffoldContract zeroFillCalendarScaffold) {

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", targetPeriodAttribution()
                    ? targetPeriodKindSummary()
                    : "cross_model_funnel_time_attribution");
            result.put("bridge_scope", "validation_only");
            result.put("bridge_signed", true);
            result.put("execution_bridge", false);
            result.put("relationRef", relationRef);
            result.put("cardinality", cardinality);
            result.put("source_cohort", Map.of(
                    "stage", sourceStage,
                    "model", sourceModel,
                    "field", sourceField));
            result.put("target_event", Map.of(
                    "stage", targetStage,
                    "model", targetModel,
                    "field", targetField));
            result.put("conversion_window", Map.of(
                    "unit", "day",
                    "size", windowDays,
                    "order", windowOrder));
            result.put("groupBy", List.of(groupKey));
            if (targetPeriodAttribution()) {
                result.put("targetPeriod", Map.of(
                        "model", targetModel,
                        "field", targetPeriodField,
                        "grain", targetPeriodGrain,
                        "stageField", firstOrNull(targetPeriodStageFields),
                        "stageFields", targetPeriodStageFields,
                        "outputField", firstOrNull(targetPeriodOutputFields),
                        "outputFields", targetPeriodOutputFields));
                result.put("outputGrain", Map.of(
                        "sourceFields", List.of("CrmLead.leadSource"),
                        "targetPeriodFields", targetPeriodOutputFields));
                result.put("denominator_scope", "fixed_per_source_group");
                result.put("numerator_bucket", "targetPeriod");
                if (zeroFillTargetYearMonthCalendar()) {
                    result.put("calendarScaffold", zeroFillCalendarScaffold.summary(
                            targetPeriodStageFields, targetPeriodOutputFields));
                    result.put("fill_policy", zeroFillCalendarScaffold.fillPolicy());
                    result.put("scaffold_scope", zeroFillCalendarScaffold.scaffoldScope());
                }
            }
            result.put("denominator", denominatorMetric);
            result.put("numerator", matchedMetric);
            result.put("ratio_alias", rateAlias);
            result.put("output", output);
            result.put("required_execution_capabilities", List.of(
                    "target_event_window_join",
                    "source_to_target_time_boundary_guard",
                    "cross_model_governance_replay"));
            return result;
        }

        public boolean targetPeriodAttribution() {
            return targetPeriodField != null && !targetPeriodField.isBlank()
                    && targetPeriodGrain != null && !targetPeriodGrain.isBlank()
                    && targetPeriodStageFields != null && !targetPeriodStageFields.isEmpty()
                    && targetPeriodOutputFields != null && !targetPeriodOutputFields.isEmpty();
        }

        public String targetPeriodStageField() {
            return firstOrNull(targetPeriodStageFields);
        }

        public String targetPeriodOutputField() {
            return firstOrNull(targetPeriodOutputFields);
        }

        private boolean zeroFillTargetYearMonthCalendar() {
            return targetPeriodAttribution()
                    && zeroFillCalendarScaffold != null
                    && zeroFillCalendarScaffold.declared();
        }

        private String targetPeriodKindSummary() {
            if (zeroFillTargetYearMonthCalendar()) {
                return "cross_model_funnel_target_year_month_zero_fill_calendar";
            }
            return "year_month".equals(targetPeriodGrain)
                    ? "cross_model_funnel_target_year_month_attribution"
                    : "cross_model_funnel_target_month_attribution";
        }
    }

    public record CrossModelFunnelMoneyAttributionContractPlan(List<String> output,
                                                               String relationRef,
                                                               String cardinality,
                                                               String sourceStage,
                                                               String sourceModel,
                                                               String sourceField,
                                                               String targetStage,
                                                               String targetModel,
                                                               String targetField,
                                                               int windowDays,
                                                               String windowOrder,
                                                               String groupKey,
                                                               String orderAmountMetric,
                                                               String convertedAmountMetric,
                                                               String targetPeriodField,
                                                               String targetPeriodGrain,
                                                               List<String> targetPeriodStageFields,
                                                               List<String> targetPeriodOutputFields,
                                                               MoneyAmountShareContract amountShareContract,
                                                               MoneyAmountPerLeadContract amountPerLeadContract) {

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", moneyAttributionKind(
                    "cross_model_funnel_target_year_month_money_attribution",
                    amountShareContract,
                    amountPerLeadContract));
            result.put("bridge_scope", "validation_only");
            result.put("bridge_signed", true);
            result.put("execution_bridge", false);
            result.put("relationRef", relationRef);
            result.put("cardinality", cardinality);
            result.put("source_cohort", Map.of(
                    "stage", sourceStage,
                    "model", sourceModel,
                    "field", sourceField));
            result.put("target_event", Map.of(
                    "stage", targetStage,
                    "model", targetModel,
                    "field", targetField));
            result.put("conversion_window", Map.of(
                    "unit", "day",
                    "size", windowDays,
                    "order", windowOrder));
            result.put("targetPeriod", Map.of(
                    "model", targetModel,
                    "field", targetPeriodField,
                    "grain", targetPeriodGrain,
                    "stageField", firstOrNull(targetPeriodStageFields),
                    "stageFields", targetPeriodStageFields,
                    "outputField", firstOrNull(targetPeriodOutputFields),
                    "outputFields", targetPeriodOutputFields));
            result.put("groupBy", expectedTargetPeriodGroupBy(groupKey, targetPeriodStageFields));
            result.put("amount", Map.of(
                    "field", "FactOrderQueryModel.amount",
                    "aggregation", "sum",
                    "source_metric", orderAmountMetric,
                    "metric", convertedAmountMetric));
            result.put("orderSelection", "converted_order_id_only");
            result.put("deduplication", "dedupe_order_id_after_signed_relation");
            result.put("orderStatusScope", "completed_paid_orders");
            result.put("currencyScope", "single_currency_no_conversion");
            if (amountShareContract.declared()) {
                result.put("derivedMetric", amountShareContract.summary());
                result.put("denominator_scope", amountShareContract.denominatorScope());
                result.put("numerator", amountShareContract.numeratorMetric());
                result.put("denominator", amountShareContract.denominatorMetric());
                result.put("ratio_alias", amountShareContract.ratioAlias());
            } else if (amountPerLeadContract.declared()) {
                result.put("derivedMetric", amountPerLeadContract.summary());
                result.put("denominator_scope", amountPerLeadContract.denominatorScope());
                result.put("numerator", amountPerLeadContract.numeratorMetric());
                result.put("denominator", amountPerLeadContract.denominatorMetric());
                result.put("ratio_alias", amountPerLeadContract.ratioAlias());
                result.put("source_denominator", Map.of(
                        "model", "CrmLead",
                        "grain", "one_row_per_lead",
                        "groupBy", List.of(groupKey),
                        "execution_metric", "count(leadId)",
                        "semantic_metric", amountPerLeadContract.denominatorMetric()));
            }
            result.put("output", output);
            result.put("required_execution_capabilities", List.of(
                    "target_event_window_join",
                    "source_to_target_time_boundary_guard",
                    "order_id_dedup_after_signed_relation",
                    "cross_source_duplicate_order_guard",
                    "completed_paid_order_scope"));
            return result;
        }
    }

    private record YearMonthPeriod(int year, int month) {
        boolean after(YearMonthPeriod other) {
            return year > other.year || (year == other.year && month > other.month);
        }

        YearMonthPeriod next() {
            if (month == 12) {
                return new YearMonthPeriod(year + 1, 1);
            }
            return new YearMonthPeriod(year, month + 1);
        }

        String label() {
            return String.format(Locale.ROOT, "%04d-%02d", year, month);
        }
    }

    private record ZeroFillCalendarScaffoldContract(boolean declared,
                                                    String source,
                                                    String rangePolicy,
                                                    List<YearMonthPeriod> periods,
                                                    String fillPolicy,
                                                    String scaffoldScope) {
        static ZeroFillCalendarScaffoldContract notDeclared() {
            return new ZeroFillCalendarScaffoldContract(false, null, null, List.of(), null, null);
        }

        Map<String, Object> summary(List<String> stageFields, List<String> outputFields) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", source);
            result.put("rangePolicy", rangePolicy);
            result.put("range", Map.of(
                    "from", periods.isEmpty() ? null : periods.get(0).label(),
                    "to", periods.isEmpty() ? null : periods.get(periods.size() - 1).label()));
            result.put("period_count", periods.size());
            result.put("grain", "year_month");
            result.put("stageFields", stageFields);
            result.put("outputFields", outputFields);
            result.put("fillPolicy", fillPolicy);
            result.put("fillTarget", "matchedLeadCount");
            result.put("denominatorScope", "fixed_per_source_group");
            result.put("scaffoldScope", scaffoldScope);
            return result;
        }
    }

    private record TargetPeriodContract(boolean declared,
                                        List<String> stageFields,
                                        String semanticField,
                                        String grain,
                                        List<String> outputFields) {
        static TargetPeriodContract notDeclared() {
            return new TargetPeriodContract(false, List.of(), null, null, List.of());
        }
    }

    private record MoneyAmountShareContract(boolean declared,
                                            String numeratorMetric,
                                            String denominatorMetric,
                                            String denominatorScope,
                                            String ratioAlias,
                                            String formula) {
        static MoneyAmountShareContract notDeclared() {
            return new MoneyAmountShareContract(false, null, null, null, null, null);
        }

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "source_cohort_target_year_month_amount_share");
            result.put("metric", ratioAlias);
            result.put("numeratorMetric", numeratorMetric);
            result.put("denominatorMetric", denominatorMetric);
            result.put("denominatorScope", denominatorScope);
            result.put("formula", formula);
            return result;
        }
    }

    private record MoneyAmountPerLeadContract(boolean declared,
                                              String numeratorMetric,
                                              String denominatorMetric,
                                              String denominatorScope,
                                              String ratioAlias,
                                              String formula) {
        static MoneyAmountPerLeadContract notDeclared() {
            return new MoneyAmountPerLeadContract(false, null, null, null, null, null);
        }

        Map<String, Object> summary() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", "source_cohort_target_year_month_amount_per_lead");
            result.put("metric", ratioAlias);
            result.put("numeratorMetric", numeratorMetric);
            result.put("denominatorMetric", denominatorMetric);
            result.put("denominatorScope", denominatorScope);
            result.put("formula", formula);
            result.put("denominatorSemantics", "count_distinct(CrmLead.leadId) at source cohort grain");
            result.put("executionAssumption", "CrmLead grain is one row per lead, so count(leadId) is distinctLeadCount");
            return result;
        }
    }

    private static String moneyAttributionKind(String base,
                                               MoneyAmountShareContract amountShareContract,
                                               MoneyAmountPerLeadContract amountPerLeadContract) {
        if (amountShareContract.declared()) {
            return base.replace("target_year_month_money_attribution",
                    "target_year_month_amount_share");
        }
        if (amountPerLeadContract.declared()) {
            return base.replace("target_year_month_money_attribution",
                    "target_year_month_amount_per_lead");
        }
        return base;
    }

    private record OutputGrainContract(boolean valid, List<String> targetOutputFields) {
    }

    public record CrossModelJoinAlignBridgeResult(String status,
                                                  String leftModel,
                                                  SemanticQueryRequest leftRequest,
                                                  String rightModel,
                                                  SemanticQueryRequest rightRequest,
                                                  CrossModelJoinAlignPlan plan,
                                                  List<String> unsupported) {
        static CrossModelJoinAlignBridgeResult ready(String leftModel,
                                                     SemanticQueryRequest leftRequest,
                                                     String rightModel,
                                                     SemanticQueryRequest rightRequest,
                                                     CrossModelJoinAlignPlan plan) {
            return new CrossModelJoinAlignBridgeResult(STATUS_READY, leftModel, leftRequest, rightModel,
                    rightRequest, plan, List.of());
        }

        static CrossModelJoinAlignBridgeResult deferred(List<String> unsupported) {
            return new CrossModelJoinAlignBridgeResult(STATUS_DEFERRED, null, null, null, null, null,
                    List.copyOf(unsupported));
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }

        public Map<String, Object> summary() {
            return plan == null ? Map.of() : plan.summary();
        }

        public SqlGenerationResult wrap(SqlGenerationResult leftBase, SqlGenerationResult rightBase) {
            if (!ready()) {
                throw RX.throwB("DSL_CTE_CROSS_MODEL_JOIN_ALIGN_NOT_SUPPORTED: " + unsupported);
            }
            return plan.wrap(leftBase, rightBase);
        }
    }

    public record CrossModelFunnelSourceRateBridgeResult(String status,
                                                         String denominatorModel,
                                                         SemanticQueryRequest denominatorRequest,
                                                         String leftModel,
                                                         SemanticQueryRequest leftRequest,
                                                         String rightModel,
                                                         SemanticQueryRequest rightRequest,
                                                         CrossModelFunnelSourceRatePlan plan,
                                                         List<String> unsupported) {
        static CrossModelFunnelSourceRateBridgeResult ready(String denominatorModel,
                                                            SemanticQueryRequest denominatorRequest,
                                                            String leftModel,
                                                            SemanticQueryRequest leftRequest,
                                                            String rightModel,
                                                            SemanticQueryRequest rightRequest,
                                                            CrossModelFunnelSourceRatePlan plan) {
            return new CrossModelFunnelSourceRateBridgeResult(STATUS_READY, denominatorModel, denominatorRequest,
                    leftModel, leftRequest, rightModel, rightRequest, plan, List.of());
        }

        static CrossModelFunnelSourceRateBridgeResult deferred(List<String> unsupported) {
            return new CrossModelFunnelSourceRateBridgeResult(STATUS_DEFERRED, null, null, null, null, null,
                    null, null, List.copyOf(unsupported));
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }

        public Map<String, Object> summary() {
            return plan == null ? Map.of() : plan.summary();
        }

        public SqlGenerationResult wrap(SqlGenerationResult denominatorBase,
                                        SqlGenerationResult leftBase,
                                        SqlGenerationResult rightBase) {
            if (!ready()) {
                throw RX.throwB("DSL_CTE_CROSS_MODEL_FUNNEL_SOURCE_RATE_NOT_SUPPORTED: " + unsupported);
            }
            return plan.wrap(denominatorBase, leftBase, rightBase);
        }
    }

    public record CrossModelFunnelMoneyAttributionBridgeResult(String status,
                                                               String denominatorModel,
                                                               SemanticQueryRequest denominatorRequest,
                                                               String leftStage,
                                                               String leftModel,
                                                               SemanticQueryRequest leftRequest,
                                                               String rightStage,
                                                               String rightModel,
                                                               SemanticQueryRequest rightRequest,
                                                               CrossModelFunnelMoneyAttributionBridgePlan plan,
                                                               List<String> unsupported,
                                                               boolean relevant) {
        static CrossModelFunnelMoneyAttributionBridgeResult ready(String denominatorModel,
                                                                  SemanticQueryRequest denominatorRequest,
                                                                  String leftStage,
                                                                  String leftModel,
                                                                  SemanticQueryRequest leftRequest,
                                                                  String rightStage,
                                                                  String rightModel,
                                                                  SemanticQueryRequest rightRequest,
                                                                  CrossModelFunnelMoneyAttributionBridgePlan plan) {
            return new CrossModelFunnelMoneyAttributionBridgeResult(STATUS_READY, denominatorModel,
                    denominatorRequest, leftStage, leftModel,
                    leftRequest, rightStage, rightModel, rightRequest, plan, List.of(), true);
        }

        static CrossModelFunnelMoneyAttributionBridgeResult deferred(boolean relevant,
                                                                     List<String> unsupported) {
            return new CrossModelFunnelMoneyAttributionBridgeResult(STATUS_DEFERRED, null, null, null,
                    null, null, null, null, null, null, List.copyOf(unsupported), relevant);
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }

        public Map<String, Object> summary() {
            return plan == null ? Map.of() : plan.summary();
        }

        public SqlGenerationResult wrap(SqlGenerationResult leftBase, SqlGenerationResult rightBase) {
            if (!ready()) {
                throw RX.throwB("DSL_CTE_CROSS_MODEL_FUNNEL_MONEY_ATTRIBUTION_NOT_SUPPORTED: " + unsupported);
            }
            return plan.wrap(leftBase, rightBase);
        }

        public SqlGenerationResult wrap(SqlGenerationResult denominatorBase,
                                        SqlGenerationResult leftBase,
                                        SqlGenerationResult rightBase) {
            if (!ready()) {
                throw RX.throwB("DSL_CTE_CROSS_MODEL_FUNNEL_MONEY_ATTRIBUTION_NOT_SUPPORTED: " + unsupported);
            }
            return plan.wrap(denominatorBase, leftBase, rightBase);
        }
    }

    public record CrossModelFunnelTimeAttributionBridgeResult(String status,
                                                              String denominatorModel,
                                                              SemanticQueryRequest denominatorRequest,
                                                              String leftStage,
                                                              String leftModel,
                                                              SemanticQueryRequest leftRequest,
                                                              String rightStage,
                                                              String rightModel,
                                                              SemanticQueryRequest rightRequest,
                                                              CrossModelFunnelTimeAttributionBridgePlan plan,
                                                              List<String> unsupported,
                                                              boolean relevant) {
        static CrossModelFunnelTimeAttributionBridgeResult ready(String denominatorModel,
                                                                 SemanticQueryRequest denominatorRequest,
                                                                 String leftStage,
                                                                 String leftModel,
                                                                 SemanticQueryRequest leftRequest,
                                                                 String rightStage,
                                                                 String rightModel,
                                                                 SemanticQueryRequest rightRequest,
                                                                 CrossModelFunnelTimeAttributionBridgePlan plan) {
            return new CrossModelFunnelTimeAttributionBridgeResult(STATUS_READY, denominatorModel,
                    denominatorRequest, leftStage, leftModel, leftRequest, rightStage, rightModel, rightRequest,
                    plan, List.of(), true);
        }

        static CrossModelFunnelTimeAttributionBridgeResult deferred(boolean relevant,
                                                                    List<String> unsupported) {
            return new CrossModelFunnelTimeAttributionBridgeResult(STATUS_DEFERRED, null, null, null, null, null,
                    null, null, null, null, List.copyOf(unsupported), relevant);
        }

        public boolean ready() {
            return STATUS_READY.equals(status);
        }

        public Map<String, Object> summary() {
            return plan == null ? Map.of() : plan.summary();
        }

        public SqlGenerationResult wrap(SqlGenerationResult denominatorBase,
                                        SqlGenerationResult leftBase,
                                        SqlGenerationResult rightBase) {
            if (!ready()) {
                throw RX.throwB("DSL_CTE_CROSS_MODEL_FUNNEL_TIME_ATTRIBUTION_NOT_SUPPORTED: " + unsupported);
            }
            return plan.wrap(denominatorBase, leftBase, rightBase);
        }
    }

    public record CrossModelFunnelMoneyAttributionContractResult(String status,
                                                                 CrossModelFunnelMoneyAttributionContractPlan plan,
                                                                 List<String> unsupported,
                                                                 boolean relevant) {
        static CrossModelFunnelMoneyAttributionContractResult ready(CrossModelFunnelMoneyAttributionContractPlan plan) {
            return new CrossModelFunnelMoneyAttributionContractResult(STATUS_CONTRACT_READY, plan, List.of(), true);
        }

        static CrossModelFunnelMoneyAttributionContractResult deferred(boolean relevant,
                                                                      List<String> unsupported) {
            return new CrossModelFunnelMoneyAttributionContractResult(STATUS_DEFERRED, null,
                    List.copyOf(unsupported), relevant);
        }

        public boolean ready() {
            return STATUS_CONTRACT_READY.equals(status);
        }

        public Map<String, Object> summary() {
            return plan == null ? Map.of() : plan.summary();
        }
    }

    public record CrossModelFunnelTimeAttributionContractResult(String status,
                                                                CrossModelFunnelTimeAttributionContractPlan plan,
                                                                List<String> unsupported,
                                                                boolean relevant,
                                                                boolean targetPeriodRelevant) {
        static CrossModelFunnelTimeAttributionContractResult ready(
                CrossModelFunnelTimeAttributionContractPlan plan) {
            return new CrossModelFunnelTimeAttributionContractResult(
                    STATUS_CONTRACT_READY, plan, List.of(), true, plan.targetPeriodAttribution());
        }

        static CrossModelFunnelTimeAttributionContractResult deferred(boolean relevant,
                                                                      List<String> unsupported) {
            return deferred(relevant, unsupported, false);
        }

        static CrossModelFunnelTimeAttributionContractResult deferred(boolean relevant,
                                                                      List<String> unsupported,
                                                                      boolean targetPeriodRelevant) {
            return new CrossModelFunnelTimeAttributionContractResult(
                    STATUS_DEFERRED, null, List.copyOf(unsupported), relevant, targetPeriodRelevant);
        }

        public boolean ready() {
            return STATUS_CONTRACT_READY.equals(status);
        }

        public Map<String, Object> summary() {
            return plan == null ? Map.of() : plan.summary();
        }

        public boolean targetPeriodAttribution() {
            return plan != null && plan.targetPeriodAttribution();
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

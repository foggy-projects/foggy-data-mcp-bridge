package com.foggyframework.dataset.db.model.semantic.support;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a narrow DSL_CTE stage contract into the existing DSL request shape.
 *
 * <p>This bridge is intentionally smaller than {@link DslCtePlanValidator}.
 * P0.7 only proves that common post-aggregate stage plans can reuse the current
 * DSL SQL generator without opening arbitrary CTE compilation.</p>
 */
public final class DslCteDslRequestMapper {

    public static final String STATUS_READY = "BRIDGE_READY";
    public static final String STATUS_DEFERRED = "BRIDGE_DEFERRED";

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
            unsupported.add("DSL_CTE bridge v1 requires the first stage to be aggregate");
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

        for (int i = 1; i < stages.size(); i++) {
            Map<String, Object> stage = stages.get(i);
            String type = stringValue(stage.get("type"));
            if ("derive".equals(type)) {
                postAgg.addAll(postAggregateCalculations(stage.get("derived"), metrics.aliases(), unsupported));
            } else if ("postSlice".equals(type)) {
                postSlice = sliceItems(stage.get("filters"), unsupported);
            } else {
                unsupported.add("DSL_CTE bridge v1 does not execute stage type: " + type);
            }
        }

        request.setColumns(outputColumns(ctePlan.get("output"), aggregate.get("groupBy"), metrics, postAgg, unsupported));
        request.setPostAggregateCalculations(postAgg.isEmpty() ? null : postAgg);
        request.setPostSlice(postSlice);
        request.setReturnTotal(false);

        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            unsupported.add("DSL_CTE bridge request must have output columns");
        }
        if (!unsupported.isEmpty()) {
            return BridgeResult.deferred(unsupported);
        }
        return BridgeResult.ready(sourceModel(fallbackModel, aggregate), request);
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

    private static List<String> outputColumns(Object rawOutput, Object rawGroupBy, MetricMapping metrics,
                                              List<PostAggregateCalculationDef> postAgg,
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
            String metricExpr = metrics.columnByAlias().get(output);
            columns.add(metricExpr == null ? output : metricExpr);
        }
        if (columns.stream().anyMatch(col -> col == null || col.isBlank())) {
            unsupported.add("output columns must be non-blank");
        }
        return columns;
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
            SemanticQueryRequest.SliceItem item = new SemanticQueryRequest.SliceItem();
            item.setField(field);
            item.setOp(op);
            item.setValue(filter.get("value"));
            result.add(item);
        }
        return result;
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

    private record MetricMapping(Map<String, String> columnByAlias) {
        List<String> aliases() {
            return new ArrayList<>(columnByAlias.keySet());
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

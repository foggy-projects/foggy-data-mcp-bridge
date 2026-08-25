package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionContract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON schemas for full query-model DSL and restricted SemanticDSL Compose. */
final class FapAnalyticsAdvancedSchemas {

    private static final String REVISION = "^sha256:[0-9a-f]{64}$";

    private FapAnalyticsAdvancedSchemas() {
    }

    static Map<String, Object> queryModelArguments() {
        Map<String, Object> payload = queryModelPayload();
        return object(
                map(
                        "namespace", nonBlankString(),
                        "modelName", nonBlankString(),
                        "expectedModelRevision", string(REVISION),
                        "mode", enumString("validate", "execute"),
                        "payload", payload),
                List.of(
                        "namespace", "modelName", "expectedModelRevision", "mode", "payload"),
                false);
    }

    private static Map<String, Object> queryModelPayload() {
        Map<String, Object> payload = new LinkedHashMap<>(object(
                map(
                        "route", described(enumString("DSL_CTE"),
                                "Only for a documented controlled single-model staged recipe; ordinary totals, counts and grouped summaries must omit route."),
                        "executable_plan", describedOpenObject(
                                "Controlled shape {cte_plan:{stages:[...],output:[...]}}. The first stage uses input:{model:<exact described model>}; every later inputs entry names a prior stage. There is no implicit source stage. No raw SQL or free-form database functions."),
                        "executablePlan", describedOpenObject(
                                "Camel-case compatibility form of executable_plan with the same first-stage input and prior-stage inputs rules."),
                        "calculatedFields", calculatedFields(),
                        "columns", describedStringArray(
                                "Model fields or simple aggregate expressions such as sum(amount) as total. Omit when pivot is present."),
                        "slice", filterArray(
                                "Pre-aggregation filters; entries are ANDed unless nested under $or/$and."),
                        "having", map(
                                "type", "array",
                                "items", map("$ref", "#/properties/payload/properties/slice/items"),
                                "description", "Post-aggregation filters over described measures or aggregate aliases."),
                        "groupBy", groupBy(),
                        "orderBy", orderBy(),
                        "start", integer(0),
                        "limit", integer(1),
                        "returnTotal", map("type", "boolean"),
                        "distinct", map("type", "boolean"),
                        "withSubtotals", map("type", "boolean"),
                        "timeWindow", timeWindow(),
                        "pivot", pivot()),
                List.of(),
                false));
        payload.put("allOf", List.of(
                mutuallyExclusive("pivot", "columns"),
                mutuallyExclusive("pivot", "timeWindow")));
        return FapAnalyticsValues.object("schema", payload);
    }

    private static Map<String, Object> calculatedFields() {
        Map<String, Object> windowOrder = object(
                map(
                        "field", nonBlankString(),
                        "dir", enumString("asc", "desc")),
                List.of("field"),
                false);
        Map<String, Object> item = object(
                map(
                        "name", nonBlankString(),
                        "expression", nonBlankString(),
                        "agg", enumString(
                                "SUM", "AVG", "MAX", "MIN", "COUNT", "COUNT_DISTINCT",
                                "STDDEV_POP", "STDDEV_SAMP", "VAR_POP", "VAR_SAMP"),
                        "partitionBy", stringArray(),
                        "windowOrderBy", map("type", "array", "items", windowOrder),
                        "windowFrame", nonBlankString()),
                List.of("name", "expression"),
                false);
        return map(
                "type", "array",
                "items", item,
                "description", "Governed scalar, aggregate or window calculated-field definitions. Use timeWindow for period comparisons and pivot metrics for parentShare/baselineRatio.");
    }

    private static Map<String, Object> filterArray(String description) {
        Map<String, Object> standard = object(
                map(
                        "field", nonBlankString(),
                        "op", described(nonBlankString(),
                                "Semantic filter operator. Common values include =, !=, <>, ===, >, >=, <, <=, like, not like, left_like, right_like, in, not in, is null, is not null, [], [), (), (], childrenOf, descendantsOf, selfAndDescendantsOf, ancestorsOf, selfAndAncestorsOf, similar and hybrid."),
                        "value", map(
                                "description", "Literal value, array/range, or a field reference such as {$field: otherField}."),
                        "maxDepth", integer(0)),
                List.of("field", "op"),
                false);
        Map<String, Object> expression = object(
                map("$expr", nonBlankString()),
                List.of("$expr"),
                false);
        Map<String, Object> orGroup = object(
                map("$or", map(
                        "type", "array",
                        "minItems", 1,
                        "items", map("$ref", "#/properties/payload/properties/slice/items"))),
                List.of("$or"),
                false);
        Map<String, Object> andGroup = object(
                map("$and", map(
                        "type", "array",
                        "minItems", 1,
                        "items", map("$ref", "#/properties/payload/properties/slice/items"))),
                List.of("$and"),
                false);
        Map<String, Object> legacyEquality = map(
                "type", "object",
                "minProperties", 1,
                "maxProperties", 1,
                "propertyNames", map("not", map("enum", List.of(
                        "$or", "$and", "$expr", "field", "op", "value", "maxDepth"))),
                "description", "Legacy equality shorthand {fieldName: value}; prefer the standard field/op/value form.");
        return map(
                "type", "array",
                "items", map("oneOf", List.of(
                        standard, expression, orGroup, andGroup, legacyEquality)),
                "description", description);
    }

    private static Map<String, Object> groupBy() {
        Map<String, Object> item = object(
                map(
                        "field", nonBlankString(),
                        "agg", enumString(
                                "MAX", "MIN", "SUM", "AVG", "COUNT", "COUNT_DISTINCT",
                                "STDDEV_POP", "STDDEV_SAMP", "VAR_POP", "VAR_SAMP", "PK")),
                List.of("field"),
                false);
        return map(
                "type", "array",
                "items", map("oneOf", List.of(nonBlankString(), item)),
                "description", "Grouping fields as strings or {field, agg} objects.");
    }

    private static Map<String, Object> orderBy() {
        Map<String, Object> item = object(
                map(
                        "field", nonBlankString(),
                        "dir", enumString("asc", "desc", "ASC", "DESC"),
                        "nullFirst", map("type", "boolean"),
                        "nullLast", map("type", "boolean")),
                List.of("field"),
                false);
        return map(
                "type", "array",
                "items", map("oneOf", List.of(nonBlankString(), item)),
                "description", "Ordering as field, -field, field desc, or {field, dir, nullFirst/nullLast}.");
    }

    private static Map<String, Object> timeWindow() {
        return described(object(
                map(
                        "field", nonBlankString(),
                        "grain", enumString("day", "week", "month", "quarter", "year"),
                        "comparison", enumString(
                                "yoy", "mom", "wow", "ytd", "mtd",
                                "rolling_7d", "rolling_30d", "rolling_90d"),
                        "range", enumString("[)", "[]"),
                        "value", map(
                                "type", "array",
                                "items", nonBlankString(),
                                "minItems", 2,
                                "maxItems", 2),
                        "targetMetrics", stringArray(),
                        "rollingAggregator", enumString("sum", "avg", "count", "min", "max")),
                List.of("field", "grain", "comparison"),
                false),
                "Single-model YoY, MoM, WoW, YTD, MTD or rolling-window declaration.");
    }

    private static Map<String, Object> pivot() {
        Map<String, Object> axisHaving = object(
                map(
                        "metric", nonBlankString(),
                        "op", enumString(">", ">=", "<", "<=", "=", "!="),
                        "value", map("type", "number")),
                List.of("metric", "op", "value"),
                false);
        Map<String, Object> axisObject = object(
                map(
                        "field", nonBlankString(),
                        "hierarchyMode", enumString("flat", "tree"),
                        "expandDepth", map("type", "integer"),
                        "limit", integer(1),
                        "orderBy", stringArray(),
                        "having", map("type", "array", "items", axisHaving)),
                List.of("field"),
                false);
        Map<String, Object> axis = map(
                "type", "array",
                "items", map("oneOf", List.of(nonBlankString(), axisObject)));
        Map<String, Object> parentShare = object(
                map(
                        "name", nonBlankString(),
                        "type", map("const", "parentShare"),
                        "of", nonBlankString(),
                        "axis", enumString("rows"),
                        "level", nonBlankString(),
                        "parentLevel", nonBlankString()),
                List.of("name", "type", "of"),
                false);
        Map<String, Object> baselineRatio = object(
                map(
                        "name", nonBlankString(),
                        "type", map("const", "baselineRatio"),
                        "of", nonBlankString(),
                        "axis", enumString("columns"),
                        "baseline", enumString("first", "last")),
                List.of("name", "type", "of", "axis", "baseline"),
                false);
        Map<String, Object> metrics = map(
                "type", "array",
                "minItems", 1,
                "items", map("oneOf", List.of(
                        nonBlankString(),
                        map("oneOf", List.of(parentShare, baselineRatio)))));
        Map<String, Object> options = object(
                map(
                        "crossjoin", map("type", "boolean"),
                        "rowSubtotals", map("type", "boolean"),
                        "columnSubtotals", map(
                                "type", "boolean",
                                "description", "Currently unsupported by the engine; omit this option."),
                        "grandTotal", map("type", "boolean")),
                List.of(),
                false);
        Map<String, Object> layout = object(
                map("metricPlacement", enumString("columns", "rows")),
                List.of(),
                false);
        return described(object(
                map(
                        "rows", axis,
                        "columns", axis,
                        "metrics", metrics,
                        "properties", stringArray(),
                        "options", options,
                        "layout", layout,
                        "outputFormat", enumString("flat", "tree", "grid")),
                List.of("rows", "metrics"),
                false),
                "Pivot/cross-tab declaration. Mutually exclusive with top-level columns and timeWindow.");
    }

    private static Map<String, Object> mutuallyExclusive(String first, String second) {
        return map("not", map("allOf", List.of(
                map("required", List.of(first)),
                map("required", List.of(second)))));
    }

    static Map<String, Object> composeArguments() {
        Map<String, Object> script = map(
                "type", "string",
                "minLength", 1,
                "maxLength", 262_144,
                "description", "Restricted SemanticDSL only: dsl({...}), derived plan.query({...}), join and union. End by returning { plans: plan }; never use host, file, network, arbitrary SQL or direct execute access.");
        return object(
                map(
                        "namespace", nonBlankString(),
                        "mode", enumString("validate", "preview", "execute"),
                        "script", script,
                        "params", object(Map.of(), List.of(), true)),
                List.of("namespace", "mode", "script"),
                false);
    }

    static Map<String, Object> queryModelResult(String operation) {
        return result(operation, object(
                map(
                        "namespace", nonBlankString(),
                        "modelName", nonBlankString(),
                        "modelRevision", string(REVISION),
                        "mode", enumString("validate", "execute"),
                        "response", object(Map.of(), List.of(), true)),
                List.of("namespace", "modelName", "modelRevision", "mode", "response"),
                false));
    }

    static Map<String, Object> composeResult(String operation) {
        return result(operation, object(
                map(
                        "namespace", nonBlankString(),
                        "mode", enumString("validate", "preview", "execute"),
                        "valid", map("type", "boolean"),
                        "executed", map("type", "boolean"),
                        "value", map(),
                        "sql", map("type", List.of("string", "null")),
                        "params", map("type", "array"),
                        "warnings", describedStringArray("Compose warnings.")),
                List.of(
                        "namespace", "mode", "valid", "executed", "value",
                        "params", "warnings"),
                false));
    }

    private static Map<String, Object> result(
            String operation,
            Map<String, Object> dataSchema) {
        Map<String, Object> context = object(
                map("requestId", nonBlankString(), "traceId", nonBlankString()),
                List.of("requestId", "traceId"),
                false);
        return object(
                map(
                        "operation", map("const", operation),
                        "functionContractVersion", map(
                                "const", AnalyticsFunctionContract.VERSION),
                        "analyticsRuntimeApiVersion", nonBlankString(),
                        "schemaVersion", nonBlankString(),
                        "data", dataSchema,
                        "context", context),
                List.of(
                        "operation", "functionContractVersion",
                        "analyticsRuntimeApiVersion", "schemaVersion", "data", "context"),
                false);
    }

    private static Map<String, Object> describedOpenObject(String description) {
        Map<String, Object> result = new LinkedHashMap<>(
                object(Map.of(), List.of(), true));
        result.put("description", description);
        return FapAnalyticsValues.object("schema", result);
    }

    private static Map<String, Object> describedStringArray(String description) {
        return described(stringArray(), description);
    }

    private static Map<String, Object> stringArray() {
        return map("type", "array", "items", nonBlankString());
    }

    private static Map<String, Object> described(
            Map<String, Object> schema,
            String description) {
        Map<String, Object> result = new LinkedHashMap<>(schema);
        result.put("description", description);
        return FapAnalyticsValues.object("schema", result);
    }

    private static Map<String, Object> enumString(String... values) {
        return map("type", "string", "enum", List.of(values));
    }

    private static Map<String, Object> object(
            Map<String, Object> properties,
            List<String> required,
            boolean additionalProperties) {
        return map(
                "type", "object",
                "additionalProperties", additionalProperties,
                "required", required,
                "properties", properties);
    }

    private static Map<String, Object> string(String pattern) {
        return map("type", "string", "pattern", pattern);
    }

    private static Map<String, Object> nonBlankString() {
        return map("type", "string", "minLength", 1);
    }

    private static Map<String, Object> integer(int minimum) {
        return map("type", "integer", "minimum", minimum);
    }

    private static Map<String, Object> map(Object... entries) {
        if (entries.length % 2 != 0) {
            throw new IllegalArgumentException("schema map entries must be paired");
        }
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            value.put((String) entries[index], entries[index + 1]);
        }
        return FapAnalyticsValues.object("schema", value);
    }
}

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
        Map<String, Object> payload = object(
                map(
                        "route", enumString("DSL_CTE"),
                        "executable_plan", describedOpenObject(
                                "Controlled DSL_CTE stage plan; no raw SQL or free-form database functions."),
                        "executablePlan", describedOpenObject(
                                "Camel-case compatibility form of executable_plan."),
                        "calculatedFields", describedArray(
                                "Calculated fields for scalar, aggregate and governed window expressions."),
                        "columns", describedStringArray(
                                "Fields and simple aggregate expressions, for example sum(amount) as total."),
                        "slice", describedArray(
                                "Pre-aggregation filters; supports nested $and/$or and field references."),
                        "having", describedArray(
                                "Post-aggregation filters over measures or aggregate aliases."),
                        "groupBy", describedArray(
                                "Grouping fields as strings or {field, agg} objects."),
                        "orderBy", describedArray(
                                "Ordering as strings or {field, dir} objects."),
                        "start", integer(0),
                        "limit", integer(1),
                        "returnTotal", map("type", "boolean"),
                        "distinct", map("type", "boolean"),
                        "withSubtotals", map("type", "boolean"),
                        "timeWindow", describedOpenObject(
                                "Single-model YoY, MoM, YTD, MTD or rolling-window declaration."),
                        "pivot", describedOpenObject(
                                "Pivot/cross-tab declaration with rows, columns, metrics, totals and hierarchy options.")),
                List.of(),
                false);
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

    private static Map<String, Object> describedArray(String description) {
        return map("type", "array", "description", description);
    }

    private static Map<String, Object> describedStringArray(String description) {
        return map(
                "type", "array",
                "items", nonBlankString(),
                "description", description);
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

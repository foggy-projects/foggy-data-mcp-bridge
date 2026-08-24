package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionContract;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FapAnalyticsSchemas {

    private static final String LOGICAL_REF =
            "^[A-Za-z0-9][A-Za-z0-9._~-]{0,127}$";
    private static final String REVISION = "^sha256:[0-9a-f]{64}$";

    private FapAnalyticsSchemas() {
    }

    static Map<String, Object> noArguments() {
        return object(Map.of(), List.of(), false);
    }

    static Map<String, Object> bundleArguments() {
        return object(
                map(
                        "bundleRef", string(LOGICAL_REF),
                        "expectedBundleRevision", string(REVISION)),
                List.of("bundleRef"),
                false);
    }

    static Map<String, Object> renderArguments() {
        return object(
                map(
                        "bundleRef", string(LOGICAL_REF),
                        "artifactRef", string(LOGICAL_REF),
                        "expectedBundleRevision", string(REVISION),
                        "parameters", object(Map.of(), List.of(), true),
                        "timezone", nonBlankString(),
                        "locale", nonBlankString()),
                List.of(
                        "bundleRef",
                        "artifactRef",
                        "expectedBundleRevision",
                        "timezone",
                        "locale"),
                false);
    }

    static Map<String, Object> artifactArguments() {
        return object(
                map(
                        "bundleRef", string(LOGICAL_REF),
                        "artifactKind", map(
                                "type", "string",
                                "enum", List.of("report", "dashboard")),
                        "artifactRef", string(LOGICAL_REF),
                        "expectedBundleRevision", string(REVISION)),
                List.of(
                        "bundleRef",
                        "artifactKind",
                        "artifactRef",
                        "expectedBundleRevision"),
                false);
    }

    static Map<String, Object> semanticModelArguments() {
        return object(
                map(
                        "namespace", nonBlankString(),
                        "modelName", nonBlankString(),
                        "expectedModelRevision", string(REVISION)),
                List.of("namespace", "modelName", "expectedModelRevision"),
                false);
    }

    static Map<String, Object> semanticQueryArguments() {
        Map<String, Object> scalar = map(
                "type", List.of("string", "number", "integer", "boolean"));
        Map<String, Object> filter = object(
                map(
                        "field", nonBlankString(),
                        "operator", map(
                                "type", "string",
                                "enum", List.of(
                                        "=", "!=", ">", ">=", "<", "<=",
                                        "like", "left_like", "right_like", "in", "not in",
                                        "[]", "[)", "(]", "()", "is null", "is not null")),
                        "value", map(
                                "oneOf", List.of(
                                        scalar,
                                        map(
                                                "type", "array",
                                                "items", scalar,
                                                "minItems", 1,
                                                "maxItems", 256)))),
                List.of("field", "operator"),
                false);
        Map<String, Object> group = object(
                map(
                        "field", nonBlankString(),
                        "aggregation", map(
                                "type", "string",
                                "enum", List.of(
                                        "MAX", "MIN", "SUM", "AVG", "COUNT",
                                        "COUNT_DISTINCT", "STDDEV_POP", "STDDEV_SAMP",
                                        "VAR_POP", "VAR_SAMP", "PK"))),
                List.of("field"),
                false);
        Map<String, Object> order = object(
                map(
                        "field", nonBlankString(),
                        "direction", map(
                                "type", "string", "enum", List.of("asc", "desc"))),
                List.of("field", "direction"),
                false);
        Map<String, Object> query = object(
                map(
                        "columns", array(nonBlankString()),
                        "filters", array(filter),
                        "groupBy", array(group),
                        "orderBy", array(order),
                        "start", integer(0),
                        "limit", map(
                                "type", "integer", "minimum", 1, "maximum", 1000),
                        "returnTotal", map("type", "boolean"),
                        "distinct", map("type", "boolean")),
                List.of("columns"),
                false);
        return object(
                map(
                        "namespace", nonBlankString(),
                        "modelName", nonBlankString(),
                        "expectedModelRevision", string(REVISION),
                        "query", query),
                List.of("namespace", "modelName", "expectedModelRevision", "query"),
                false);
    }

    static Map<String, Object> capabilitiesResult(String operation) {
        return result(
                operation,
                object(
                        map(
                                "api", nonBlankString(),
                                "apiVersion", nonBlankString(),
                                "schemaVersion", nonBlankString(),
                                "enabled", map("type", "boolean"),
                                "securityMode", nonBlankString(),
                                "operations", map(
                                        "type", "object",
                                        "additionalProperties", nonBlankString()),
                                "limits", object(
                                        map(
                                                "maxRows", integer(1),
                                                "configuredBundles", integer(0)),
                                        List.of("maxRows", "configuredBundles"),
                                        false),
                                "warnings", array(nonBlankString())),
                        List.of(
                                "api",
                                "apiVersion",
                                "schemaVersion",
                                "enabled",
                                "securityMode",
                                "operations",
                                "limits",
                                "warnings"),
                        false));
    }

    static Map<String, Object> bundleListResult(String operation) {
        return result(
                operation,
                object(
                        map("bundles", array(bundleDescription())),
                        List.of("bundles"),
                        false));
    }

    static Map<String, Object> bundleDescriptionResult(String operation) {
        return result(operation, bundleDescription());
    }

    static Map<String, Object> artifactDescriptionResult(String operation) {
        return result(
                operation,
                object(
                        map(
                                "bundleRef", string(LOGICAL_REF),
                                "bundleRevision", string(REVISION),
                                "artifactKind", map(
                                        "type", "string",
                                        "enum", List.of("report", "dashboard")),
                                "artifactRef", string(LOGICAL_REF)),
                        List.of(
                                "bundleRef",
                                "bundleRevision",
                                "artifactKind",
                                "artifactRef"),
                        false));
    }

    static Map<String, Object> renderResult(String operation) {
        Map<String, Object> visual = object(
                map(
                        "kind", nonBlankString(),
                        "hints", map(
                                "type", "object",
                                "additionalProperties", map("type", "string"))),
                List.of("kind", "hints"),
                false);
        Map<String, Object> column = object(
                map(
                        "name", nonBlankString(),
                        "type", nonBlankString(),
                        "nullable", map("type", "boolean")),
                List.of("name", "type", "nullable"),
                false);
        Map<String, Object> widget = object(
                map(
                        "widgetRef", string(LOGICAL_REF),
                        "visual", visual,
                        "state", nonBlankString(),
                        "columns", array(column),
                        "rows", array(object(Map.of(), List.of(), true)),
                        "truncated", map("type", "boolean"),
                        "diagnostics", array(nonBlankString())),
                List.of(
                        "widgetRef",
                        "visual",
                        "state",
                        "columns",
                        "rows",
                        "truncated",
                        "diagnostics"),
                false);
        Map<String, Object> data = object(
                map(
                        "artifact", object(
                                map(
                                        "kind", nonBlankString(),
                                        "ref", string(LOGICAL_REF)),
                                List.of("kind", "ref"),
                                false),
                        "resolvedBundleRevision", string(REVISION),
                        "state", nonBlankString(),
                        "widgets", array(widget),
                        "diagnostics", array(nonBlankString())),
                List.of(
                        "artifact",
                        "resolvedBundleRevision",
                        "state",
                        "widgets",
                        "diagnostics"),
                false);
        return result(operation, data);
    }

    static Map<String, Object> semanticModelResult(String operation) {
        return result(
                operation,
                object(
                        map(
                                "namespace", nonBlankString(),
                                "modelName", nonBlankString(),
                                "modelRevision", string(REVISION),
                                "format", map("const", "markdown"),
                                "content", map(
                                        "type", "string",
                                        "minLength", 1,
                                        "maxLength", 1_048_576)),
                        List.of(
                                "namespace", "modelName", "modelRevision",
                                "format", "content"),
                        false));
    }

    static Map<String, Object> semanticQueryResult(String operation) {
        Map<String, Object> column = object(
                map(
                        "name", nonBlankString(),
                        "type", nonBlankString(),
                        "title", nullableNonBlankString()),
                List.of("name", "type", "title"),
                false);
        return result(
                operation,
                object(
                        map(
                                "namespace", nonBlankString(),
                                "modelName", nonBlankString(),
                                "modelRevision", string(REVISION),
                                "columns", array(column),
                                "rows", array(object(Map.of(), List.of(), true)),
                                "total", map(
                                        "type", List.of("integer", "null"),
                                        "minimum", 0),
                                "hasMore", map("type", "boolean"),
                                "truncated", map("type", "boolean"),
                                "warnings", array(nonBlankString())),
                        List.of(
                                "namespace", "modelName", "modelRevision", "columns",
                                "rows", "total", "hasMore", "truncated", "warnings"),
                        false));
    }

    private static Map<String, Object> bundleDescription() {
        return object(
                map(
                        "bundleRef", string(LOGICAL_REF),
                        "bundleRevision", nullableString(REVISION),
                        "definitionSchemaVersion", nullableNonBlankString(),
                        "namespaceRef", nullableNonBlankString(),
                        "sourceState", nonBlankString(),
                        "dependencyState", nullableNonBlankString(),
                        "writable", map("type", "boolean"),
                        "valid", map("type", "boolean"),
                        "errorCode", nullableNonBlankString()),
                List.of(
                        "bundleRef",
                        "bundleRevision",
                        "definitionSchemaVersion",
                        "namespaceRef",
                        "sourceState",
                        "dependencyState",
                        "writable",
                        "valid",
                        "errorCode"),
                false);
    }

    private static Map<String, Object> result(
            String operation,
            Map<String, Object> dataSchema) {
        Map<String, Object> context = object(
                map(
                        "requestId", nonBlankString(),
                        "traceId", nonBlankString()),
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
                        "operation",
                        "functionContractVersion",
                        "analyticsRuntimeApiVersion",
                        "schemaVersion",
                        "data",
                        "context"),
                false);
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

    private static Map<String, Object> array(Map<String, Object> items) {
        return map("type", "array", "items", items);
    }

    private static Map<String, Object> string(String pattern) {
        return map("type", "string", "pattern", pattern);
    }

    private static Map<String, Object> nonBlankString() {
        return map("type", "string", "minLength", 1);
    }

    private static Map<String, Object> nullableString(String pattern) {
        return map(
                "type", List.of("string", "null"),
                "pattern", pattern);
    }

    private static Map<String, Object> nullableNonBlankString() {
        return map(
                "type", List.of("string", "null"),
                "minLength", 1);
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

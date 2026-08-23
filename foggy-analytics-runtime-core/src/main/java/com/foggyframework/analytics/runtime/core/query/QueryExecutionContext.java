package com.foggyframework.analytics.runtime.core.query;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;

import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Typed runtime context passed to a governed QueryExecutor. */
public record QueryExecutionContext<A>(
        AnalyticsQuerySpec querySpec,
        AnalyticsModelDependency modelDependency,
        Map<String, Object> parameters,
        int rowLimit,
        ZoneId timezone,
        Locale locale,
        String requestId,
        String traceId,
        A authority) {

    public QueryExecutionContext {
        querySpec = Objects.requireNonNull(querySpec, "querySpec");
        modelDependency = Objects.requireNonNull(modelDependency, "modelDependency");
        if (!querySpec.namespaceRef().equals(modelDependency.namespace())
                || !querySpec.modelName().equals(modelDependency.modelName())) {
            throw new IllegalArgumentException(
                    "querySpec must match the pinned modelDependency identity");
        }
        parameters = immutableParameters(parameters);
        if (rowLimit <= 0) {
            throw new IllegalArgumentException("rowLimit must be positive");
        }
        timezone = Objects.requireNonNull(timezone, "timezone");
        locale = Objects.requireNonNull(locale, "locale");
        requestId = requireValue("requestId", requestId);
        traceId = requireValue("traceId", traceId);
        authority = Objects.requireNonNull(authority, "authority");
    }

    private static Map<String, Object> immutableParameters(Map<String, Object> parameters) {
        Objects.requireNonNull(parameters, "parameters");
        Map<String, Object> copy = new LinkedHashMap<>();
        parameters.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "parameter name"),
                value));
        return Collections.unmodifiableMap(copy);
    }

    private static String requireValue(String field, String value) {
        Objects.requireNonNull(value, field);
        if (value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}

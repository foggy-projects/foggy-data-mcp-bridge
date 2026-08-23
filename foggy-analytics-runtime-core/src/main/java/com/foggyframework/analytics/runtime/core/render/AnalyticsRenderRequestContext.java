package com.foggyframework.analytics.runtime.core.render;

import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;

import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Product-neutral caller context shared by Report preview and Dashboard render. */
public record AnalyticsRenderRequestContext(
        Map<String, Object> parameters,
        ZoneId timezone,
        Locale locale,
        QueryAuthorityBinding authorityBinding,
        String requestId,
        String traceId) {

    public AnalyticsRenderRequestContext {
        parameters = immutableParameters(parameters);
        timezone = Objects.requireNonNull(timezone, "timezone");
        locale = Objects.requireNonNull(locale, "locale");
        authorityBinding = Objects.requireNonNull(authorityBinding, "authorityBinding");
        requestId = requireValue("requestId", requestId);
        traceId = requireValue("traceId", traceId);
    }

    private static Map<String, Object> immutableParameters(Map<String, Object> parameters) {
        Objects.requireNonNull(parameters, "parameters");
        Map<String, Object> copy = new LinkedHashMap<>();
        parameters.forEach((key, value) -> copy.put(
                requireValue("parameter name", key),
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

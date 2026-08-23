package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionError;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;

/** Creates contract-versioned outcomes and normalizes correlation values once. */
public final class AnalyticsFunctionResponseFactory {

    private final String runtimeApiVersion;
    private final String schemaVersion;

    public AnalyticsFunctionResponseFactory(
            String runtimeApiVersion,
            String schemaVersion) {
        this.runtimeApiVersion = requireValue("runtimeApiVersion", runtimeApiVersion);
        this.schemaVersion = requireValue("schemaVersion", schemaVersion);
    }

    public AnalyticsFunctionContext context(AnalyticsFunctionRequestContext requested) {
        return AnalyticsFunctionContext.normalize(requested);
    }

    public <T> AnalyticsFunctionEnvelope<T> ok(
            T data,
            AnalyticsFunctionContext context) {
        return AnalyticsFunctionEnvelope.ok(
                runtimeApiVersion,
                schemaVersion,
                data,
                context);
    }

    public <T> AnalyticsFunctionEnvelope<T> fail(
            AnalyticsFunctionError error,
            AnalyticsFunctionContext context) {
        return AnalyticsFunctionEnvelope.fail(
                runtimeApiVersion,
                schemaVersion,
                error,
                context);
    }

    public String runtimeApiVersion() {
        return runtimeApiVersion;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    private static String requireValue(String field, String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(field + " must be non-blank and trimmed");
        }
        return value;
    }
}

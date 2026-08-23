package com.foggyframework.analytics.runtime.core.render;

import java.util.Objects;

/** Stable definition-resolution failures raised before governed query execution. */
public final class AnalyticsRenderException extends RuntimeException {

    public enum Code {
        REPORT_NOT_FOUND,
        DASHBOARD_NOT_FOUND,
        QUERY_NOT_FOUND,
        MODEL_DEPENDENCY_NOT_FOUND
    }

    private final Code code;

    public AnalyticsRenderException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}

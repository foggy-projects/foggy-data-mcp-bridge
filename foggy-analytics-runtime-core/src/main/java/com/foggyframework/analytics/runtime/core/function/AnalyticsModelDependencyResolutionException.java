package com.foggyframework.analytics.runtime.core.function;

import java.util.Objects;

/** Sanitizable model-dependency discovery failure at the host adapter boundary. */
public final class AnalyticsModelDependencyResolutionException extends RuntimeException {

    public enum Code {
        MODEL_NOT_FOUND,
        REVISION_UNAVAILABLE
    }

    private final Code code;

    public AnalyticsModelDependencyResolutionException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public Code code() {
        return code;
    }
}

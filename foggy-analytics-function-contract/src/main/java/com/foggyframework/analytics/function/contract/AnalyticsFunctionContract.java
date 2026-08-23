package com.foggyframework.analytics.function.contract;

/** Version constants shared by embedded and HTTP Analytics function clients. */
public final class AnalyticsFunctionContract {

    public static final String VERSION = "foggy-analytics-function/v1";
    public static final String DEFAULT_RUNTIME_API_VERSION =
            "foggy-analytics-runtime-api/v1";
    public static final String DEFAULT_SCHEMA_VERSION = "analytics-runtime/v1";
    public static final String ENGINE = "java";

    private AnalyticsFunctionContract() {
    }
}

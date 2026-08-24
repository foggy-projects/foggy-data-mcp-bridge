package com.foggyframework.analytics.runtime.api;

/** Stable Analytics Runtime API routes, independent from Foggy Runtime API v1. */
public final class AnalyticsRuntimeApiRoutes {

    public static final String API_V1 = "/analytics/api/v1";

    private AnalyticsRuntimeApiRoutes() {
    }

    public static final class V1 {

        public static final String CAPABILITIES = "/capabilities";
        public static final String BUNDLES = "/bundles";
        public static final String MODEL_DEPENDENCIES = "/model-dependencies";

        private V1() {
        }
    }
}

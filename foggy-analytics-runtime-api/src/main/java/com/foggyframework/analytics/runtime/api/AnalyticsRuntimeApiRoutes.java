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
        public static final String SEMANTIC_MODELS = "/semantic-models";
        public static final String COMPOSE = "/compose";

        private V1() {
        }
    }
}

package com.foggyframework.analytics.function.contract;

import java.util.Set;

/** Canonical operation names; products and transports must not invent aliases. */
public final class AnalyticsFunctionOperations {

    public static final String CAPABILITIES = "analytics.capabilities";
    public static final String BUNDLES_LIST = "analytics.bundles.list";
    public static final String BUNDLES_VALIDATE = "analytics.bundles.validate";
    public static final String BUNDLES_DESCRIBE = "analytics.bundles.describe";
    public static final String ARTIFACTS_DESCRIBE = "analytics.artifacts.describe";
    public static final String MODEL_DEPENDENCIES_RESOLVE =
            "analytics.model-dependencies.resolve";
    public static final String MODEL_DEPENDENCIES_LIST =
            "analytics.model-dependencies.list";
    public static final String SEMANTIC_MODELS_DESCRIBE =
            "analytics.semantic-models.describe";
    public static final String SEMANTIC_QUERIES_EXECUTE =
            "analytics.semantic-queries.execute";
    public static final String QUERY_MODEL_RUN = "analytics.query-model.run";
    public static final String COMPOSE_RUN = "analytics.compose.run";
    public static final String BUNDLES_PULL = "analytics.bundles.pull";
    public static final String BUNDLES_SAVE = "analytics.bundles.save";
    public static final String REPORTS_PREVIEW = "analytics.reports.preview";
    public static final String DASHBOARDS_PREVIEW = "analytics.dashboards.preview";
    public static final String DASHBOARDS_RENDER = "analytics.dashboards.render";

    public static final Set<String> SDK_V1 = Set.of(
            CAPABILITIES,
            BUNDLES_LIST,
            BUNDLES_VALIDATE,
            BUNDLES_DESCRIBE,
            ARTIFACTS_DESCRIBE,
            MODEL_DEPENDENCIES_RESOLVE,
            MODEL_DEPENDENCIES_LIST,
            SEMANTIC_MODELS_DESCRIBE,
            SEMANTIC_QUERIES_EXECUTE,
            QUERY_MODEL_RUN,
            COMPOSE_RUN,
            REPORTS_PREVIEW,
            DASHBOARDS_PREVIEW,
            DASHBOARDS_RENDER);

    /** Operations projected into the FAP function catalog. */
    public static final Set<String> FAP_V1 = Set.of(
            CAPABILITIES,
            BUNDLES_LIST,
            BUNDLES_VALIDATE,
            BUNDLES_DESCRIBE,
            ARTIFACTS_DESCRIBE,
            MODEL_DEPENDENCIES_LIST,
            SEMANTIC_MODELS_DESCRIBE,
            SEMANTIC_QUERIES_EXECUTE,
            QUERY_MODEL_RUN,
            COMPOSE_RUN,
            REPORTS_PREVIEW,
            DASHBOARDS_PREVIEW,
            DASHBOARDS_RENDER);

    private AnalyticsFunctionOperations() {
    }
}

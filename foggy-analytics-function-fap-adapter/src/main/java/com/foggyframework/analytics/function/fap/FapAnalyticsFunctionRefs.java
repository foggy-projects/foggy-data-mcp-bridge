package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import java.util.Map;

/** Exact FAP FunctionRef to Analytics operation registry. */
public final class FapAnalyticsFunctionRefs {

    public static final String CAPABILITIES =
            "foggy.analytics.capabilities@v1";
    public static final String BUNDLES_LIST =
            "foggy.analytics.bundles.list@v1";
    public static final String BUNDLES_VALIDATE =
            "foggy.analytics.bundles.validate@v1";
    public static final String BUNDLES_DESCRIBE =
            "foggy.analytics.bundles.describe@v1";
    public static final String ARTIFACTS_DESCRIBE =
            "foggy.analytics.artifacts.describe@v1";
    public static final String MODEL_DEPENDENCIES_LIST =
            "foggy.analytics.model-dependencies.list@v2";
    public static final String SEMANTIC_MODELS_DESCRIBE =
            "foggy.analytics.semantic-models.describe@v2";
    public static final String SEMANTIC_QUERIES_EXECUTE =
            "foggy.analytics.semantic-queries.execute@v2";
    public static final String QUERY_MODEL_RUN =
            "foggy.analytics.query-model.run@v2";
    public static final String COMPOSE_RUN =
            "foggy.analytics.compose.run@v1";
    public static final String REPORTS_PREVIEW =
            "foggy.analytics.reports.preview@v1";
    public static final String DASHBOARDS_PREVIEW =
            "foggy.analytics.dashboards.preview@v1";
    public static final String DASHBOARDS_RENDER =
            "foggy.analytics.dashboards.render@v1";

    private static final Map<String, String> OPERATION_BY_REF = Map.ofEntries(
            Map.entry(CAPABILITIES, AnalyticsFunctionOperations.CAPABILITIES),
            Map.entry(BUNDLES_LIST, AnalyticsFunctionOperations.BUNDLES_LIST),
            Map.entry(BUNDLES_VALIDATE, AnalyticsFunctionOperations.BUNDLES_VALIDATE),
            Map.entry(BUNDLES_DESCRIBE, AnalyticsFunctionOperations.BUNDLES_DESCRIBE),
            Map.entry(ARTIFACTS_DESCRIBE, AnalyticsFunctionOperations.ARTIFACTS_DESCRIBE),
            Map.entry(MODEL_DEPENDENCIES_LIST,
                    AnalyticsFunctionOperations.MODEL_DEPENDENCIES_LIST),
            Map.entry(SEMANTIC_MODELS_DESCRIBE,
                    AnalyticsFunctionOperations.SEMANTIC_MODELS_DESCRIBE),
            Map.entry(SEMANTIC_QUERIES_EXECUTE,
                    AnalyticsFunctionOperations.SEMANTIC_QUERIES_EXECUTE),
            Map.entry(QUERY_MODEL_RUN, AnalyticsFunctionOperations.QUERY_MODEL_RUN),
            Map.entry(COMPOSE_RUN, AnalyticsFunctionOperations.COMPOSE_RUN),
            Map.entry(REPORTS_PREVIEW, AnalyticsFunctionOperations.REPORTS_PREVIEW),
            Map.entry(DASHBOARDS_PREVIEW, AnalyticsFunctionOperations.DASHBOARDS_PREVIEW),
            Map.entry(DASHBOARDS_RENDER, AnalyticsFunctionOperations.DASHBOARDS_RENDER));

    private static final Map<String, String> REF_BY_OPERATION = Map.ofEntries(
            Map.entry(AnalyticsFunctionOperations.CAPABILITIES, CAPABILITIES),
            Map.entry(AnalyticsFunctionOperations.BUNDLES_LIST, BUNDLES_LIST),
            Map.entry(AnalyticsFunctionOperations.BUNDLES_VALIDATE, BUNDLES_VALIDATE),
            Map.entry(AnalyticsFunctionOperations.BUNDLES_DESCRIBE, BUNDLES_DESCRIBE),
            Map.entry(AnalyticsFunctionOperations.ARTIFACTS_DESCRIBE, ARTIFACTS_DESCRIBE),
            Map.entry(AnalyticsFunctionOperations.MODEL_DEPENDENCIES_LIST,
                    MODEL_DEPENDENCIES_LIST),
            Map.entry(AnalyticsFunctionOperations.SEMANTIC_MODELS_DESCRIBE,
                    SEMANTIC_MODELS_DESCRIBE),
            Map.entry(AnalyticsFunctionOperations.SEMANTIC_QUERIES_EXECUTE,
                    SEMANTIC_QUERIES_EXECUTE),
            Map.entry(AnalyticsFunctionOperations.QUERY_MODEL_RUN, QUERY_MODEL_RUN),
            Map.entry(AnalyticsFunctionOperations.COMPOSE_RUN, COMPOSE_RUN),
            Map.entry(AnalyticsFunctionOperations.REPORTS_PREVIEW, REPORTS_PREVIEW),
            Map.entry(AnalyticsFunctionOperations.DASHBOARDS_PREVIEW, DASHBOARDS_PREVIEW),
            Map.entry(AnalyticsFunctionOperations.DASHBOARDS_RENDER, DASHBOARDS_RENDER));

    public static String operation(String functionRef) {
        return OPERATION_BY_REF.get(functionRef);
    }

    public static String functionRef(String operation) {
        return REF_BY_OPERATION.get(operation);
    }

    public static Map<String, String> operationsByRef() {
        return OPERATION_BY_REF;
    }

    private FapAnalyticsFunctionRefs() {
    }
}

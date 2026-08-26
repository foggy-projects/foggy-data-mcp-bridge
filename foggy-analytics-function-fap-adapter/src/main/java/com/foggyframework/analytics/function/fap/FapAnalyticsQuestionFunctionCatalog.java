package com.foggyframework.analytics.function.fap;

import java.util.List;
import java.util.Map;

/**
 * Exact immutable Function projection set used by the Analytics question Skill.
 *
 * <p>This class only exposes publication material. A host-owned FAP deployment
 * must explicitly publish the returned projections with its management client;
 * the Analytics launcher and provider callback path never publish or mutate FAP
 * resources during startup or invocation.</p>
 */
public final class FapAnalyticsQuestionFunctionCatalog {

    public static final List<String> FUNCTION_REFS = List.of(
            FapAnalyticsFunctionRefs.MODEL_DEPENDENCIES_LIST,
            FapAnalyticsFunctionRefs.SEMANTIC_MODELS_DESCRIBE,
            FapAnalyticsFunctionRefs.SEMANTIC_QUERIES_EXECUTE,
            FapAnalyticsFunctionRefs.QUERY_MODEL_RUN,
            FapAnalyticsFunctionRefs.COMPOSE_RUN);

    private static final List<FapAnalyticsFunctionDescriptor> DESCRIPTORS =
            FUNCTION_REFS.stream()
                    .map(functionRef -> FapAnalyticsFunctionCatalog
                            .findByFunctionRef(functionRef)
                            .orElseThrow(() -> new IllegalStateException(
                                    "missing Analytics question Function: " + functionRef)))
                    .toList();

    private FapAnalyticsQuestionFunctionCatalog() {
    }

    public static List<FapAnalyticsFunctionDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    /** Exact BusinessFunctionProjection-compatible values for host publication. */
    public static List<Map<String, Object>> publicationValues() {
        return DESCRIPTORS.stream()
                .map(descriptor -> descriptor.projection().publicationValue())
                .toList();
    }
}

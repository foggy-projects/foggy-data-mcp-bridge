package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable FAP descriptor catalog for the synchronous Analytics Function v1 surface. */
public final class FapAnalyticsFunctionCatalog {

    private static final String EXAMPLE_REVISION =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final List<FapAnalyticsFunctionDescriptor> DESCRIPTORS =
            List.of(
                            descriptor(
                                    AnalyticsFunctionOperations.CAPABILITIES,
                                    FapAnalyticsFunctionRefs.CAPABILITIES,
                                    "analytics.capabilities",
                                    "Analytics capabilities",
                                    "Read the enabled Analytics Runtime operations and bounded limits.",
                                    "analytics runtime capabilities operations limits",
                                    List.of("analytics", "read", "runtime", "sync"),
                                    FapAnalyticsSchemas.noArguments(),
                                    FapAnalyticsSchemas.capabilitiesResult(
                                            AnalyticsFunctionOperations.CAPABILITIES),
                                    List.of(Map.of())),
                            descriptor(
                                    AnalyticsFunctionOperations.BUNDLES_LIST,
                                    FapAnalyticsFunctionRefs.BUNDLES_LIST,
                                    "analytics.bundles.list",
                                    "List Analytics bundles",
                                    "List configured Analytics definition bundles without owner or ACL data.",
                                    "analytics bundle report dashboard list definitions",
                                    List.of("analytics", "bundle", "read", "sync"),
                                    FapAnalyticsSchemas.noArguments(),
                                    FapAnalyticsSchemas.bundleListResult(
                                            AnalyticsFunctionOperations.BUNDLES_LIST),
                                    List.of(Map.of())),
                            descriptor(
                                    AnalyticsFunctionOperations.BUNDLES_VALIDATE,
                                    FapAnalyticsFunctionRefs.BUNDLES_VALIDATE,
                                    "analytics.bundles.validate",
                                    "Validate an Analytics bundle",
                                    "Validate one logical Analytics bundle and optional exact revision.",
                                    "analytics bundle validate revision definition",
                                    List.of("analytics", "bundle", "read", "sync", "validate"),
                                    FapAnalyticsSchemas.bundleArguments(),
                                    FapAnalyticsSchemas.bundleDescriptionResult(
                                            AnalyticsFunctionOperations.BUNDLES_VALIDATE),
                                    List.of(bundleExample())),
                            descriptor(
                                    AnalyticsFunctionOperations.BUNDLES_DESCRIBE,
                                    FapAnalyticsFunctionRefs.BUNDLES_DESCRIBE,
                                    "analytics.bundles.describe",
                                    "Describe an Analytics bundle",
                                    "Describe one logical Analytics bundle and its dependency state.",
                                    "analytics bundle describe revision dependency definition",
                                    List.of("analytics", "bundle", "describe", "read", "sync"),
                                    FapAnalyticsSchemas.bundleArguments(),
                                    FapAnalyticsSchemas.bundleDescriptionResult(
                                            AnalyticsFunctionOperations.BUNDLES_DESCRIBE),
                                    List.of(bundleExample())),
                            descriptor(
                                    AnalyticsFunctionOperations.ARTIFACTS_DESCRIBE,
                                    FapAnalyticsFunctionRefs.ARTIFACTS_DESCRIBE,
                                    "analytics.artifacts.describe",
                                    "Describe an Analytics artifact",
                                    "Confirm one parsed Report or Dashboard at an exact Bundle revision.",
                                    "analytics artifact report dashboard describe revision definition",
                                    List.of("analytics", "artifact", "describe", "read", "sync"),
                                    FapAnalyticsSchemas.artifactArguments(),
                                    FapAnalyticsSchemas.artifactDescriptionResult(
                                            AnalyticsFunctionOperations.ARTIFACTS_DESCRIBE),
                                    List.of(artifactExample())),
                            descriptor(
                                    AnalyticsFunctionOperations.SEMANTIC_MODELS_DESCRIBE,
                                    FapAnalyticsFunctionRefs.SEMANTIC_MODELS_DESCRIBE,
                                    "analytics.semantic-models.describe",
                                    "Describe a governed semantic model",
                                    "Read LLM-oriented metadata for one server-selected QM at an exact revision.",
                                    "analytics semantic model fields measures dimensions describe governed",
                                    List.of("analytics", "model", "question", "read", "sync"),
                                    FapAnalyticsSchemas.semanticModelArguments(),
                                    FapAnalyticsSchemas.semanticModelResult(
                                            AnalyticsFunctionOperations.SEMANTIC_MODELS_DESCRIBE),
                                    List.of(semanticModelExample())),
                            descriptor(
                                    AnalyticsFunctionOperations.SEMANTIC_QUERIES_EXECUTE,
                                    FapAnalyticsFunctionRefs.SEMANTIC_QUERIES_EXECUTE,
                                    "analytics.semantic-queries.execute",
                                    "Execute a governed semantic query",
                                    "Execute the strict read-only query subset against one exact QM using current user authority.",
                                    "analytics semantic query rows evidence governed question",
                                    List.of("analytics", "query", "question", "read", "sync"),
                                    FapAnalyticsSchemas.semanticQueryArguments(),
                                    FapAnalyticsSchemas.semanticQueryResult(
                                            AnalyticsFunctionOperations.SEMANTIC_QUERIES_EXECUTE),
                                    List.of(semanticQueryExample())),
                            descriptor(
                                    AnalyticsFunctionOperations.REPORTS_PREVIEW,
                                    FapAnalyticsFunctionRefs.REPORTS_PREVIEW,
                                    "analytics.reports.preview",
                                    "Preview an Analytics report",
                                    "Preview one report at an exact bundle revision with governed data authority.",
                                    "analytics report preview query governed data",
                                    List.of("analytics", "preview", "read", "report", "sync"),
                                    FapAnalyticsSchemas.renderArguments(),
                                    FapAnalyticsSchemas.renderResult(
                                            AnalyticsFunctionOperations.REPORTS_PREVIEW),
                                    List.of(renderExample("sales-report"))),
                            descriptor(
                                    AnalyticsFunctionOperations.DASHBOARDS_PREVIEW,
                                    FapAnalyticsFunctionRefs.DASHBOARDS_PREVIEW,
                                    "analytics.dashboards.preview",
                                    "Preview an Analytics dashboard",
                                    "Preview one dashboard at an exact bundle revision with governed data authority.",
                                    "analytics dashboard preview widgets governed data",
                                    List.of("analytics", "dashboard", "preview", "read", "sync"),
                                    FapAnalyticsSchemas.renderArguments(),
                                    FapAnalyticsSchemas.renderResult(
                                            AnalyticsFunctionOperations.DASHBOARDS_PREVIEW),
                                    List.of(renderExample("sales-dashboard"))),
                            descriptor(
                                    AnalyticsFunctionOperations.DASHBOARDS_RENDER,
                                    FapAnalyticsFunctionRefs.DASHBOARDS_RENDER,
                                    "analytics.dashboards.render",
                                    "Render an Analytics dashboard",
                                    "Render a renderer-neutral dashboard model at an exact bundle revision.",
                                    "analytics dashboard render widgets governed data",
                                    List.of("analytics", "dashboard", "read", "render", "sync"),
                                    FapAnalyticsSchemas.renderArguments(),
                                    FapAnalyticsSchemas.renderResult(
                                            AnalyticsFunctionOperations.DASHBOARDS_RENDER),
                                    List.of(renderExample("sales-dashboard"))))
                    .stream()
                    .sorted(Comparator.comparing(
                            descriptor -> descriptor.projection().functionRef()))
                    .toList();

    private FapAnalyticsFunctionCatalog() {
    }

    public static List<FapAnalyticsFunctionDescriptor> descriptors() {
        return DESCRIPTORS;
    }

    public static Optional<FapAnalyticsFunctionDescriptor> findByFunctionRef(
            String functionRef) {
        return DESCRIPTORS.stream()
                .filter(value -> value.projection().functionRef().equals(functionRef))
                .findFirst();
    }

    private static FapAnalyticsFunctionDescriptor descriptor(
            String operation,
            String functionRef,
            String name,
            String displayName,
            String description,
            String searchText,
            List<String> tags,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            List<?> examples) {
        return new FapAnalyticsFunctionDescriptor(
                operation,
                FapAnalyticsFunctionDescriptor.SideEffect.READ_ONLY,
                FapAnalyticsFunctionDescriptor.Confirmation.NOT_REQUIRED,
                FapAnalyticsFunctionDescriptor.Projection.create(
                        functionRef,
                        name,
                        displayName,
                        description,
                        searchText,
                        tags,
                        inputSchema,
                        outputSchema,
                        examples));
    }

    private static Map<String, Object> bundleExample() {
        return Map.of(
                "bundleRef", "sales-analytics",
                "expectedBundleRevision", EXAMPLE_REVISION);
    }

    private static Map<String, Object> renderExample(String artifactRef) {
        return Map.of(
                "bundleRef", "sales-analytics",
                "artifactRef", artifactRef,
                "expectedBundleRevision", EXAMPLE_REVISION,
                "parameters", Map.of("region", "east"),
                "timezone", "Asia/Shanghai",
                "locale", "zh-CN");
    }

    private static Map<String, Object> artifactExample() {
        return Map.of(
                "bundleRef", "sales-analytics",
                "artifactKind", "report",
                "artifactRef", "sales-report",
                "expectedBundleRevision", EXAMPLE_REVISION);
    }

    private static Map<String, Object> semanticModelExample() {
        return Map.of(
                "namespace", "sales",
                "modelName", "FactOrderQueryModel",
                "expectedModelRevision", EXAMPLE_REVISION);
    }

    private static Map<String, Object> semanticQueryExample() {
        return Map.of(
                "namespace", "sales",
                "modelName", "FactOrderQueryModel",
                "expectedModelRevision", EXAMPLE_REVISION,
                "query", Map.of(
                        "columns", List.of(
                                "customerName", "sum(totalAmount) as totalAmount"),
                        "groupBy", List.of(Map.of("field", "customerName")),
                        "orderBy", List.of(Map.of(
                                "field", "totalAmount", "direction", "desc")),
                        "limit", 20));
    }
}

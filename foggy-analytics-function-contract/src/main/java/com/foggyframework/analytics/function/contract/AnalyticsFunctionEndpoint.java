package com.foggyframework.analytics.function.contract;

/** Product-neutral synchronous Runtime endpoint used by every transport. */
public interface AnalyticsFunctionEndpoint {

    AnalyticsFunctionEnvelope<AnalyticsFunctionCapabilities> capabilities(
            AnalyticsFunctionRequestContext context);

    AnalyticsFunctionEnvelope<AnalyticsBundleList> listBundles(
            AnalyticsFunctionRequestContext context);

    AnalyticsFunctionEnvelope<AnalyticsBundleDescription> validateBundle(
            AnalyticsBundleFunctionRequest request);

    AnalyticsFunctionEnvelope<AnalyticsBundleDescription> describeBundle(
            AnalyticsBundleFunctionRequest request);

    /**
     * Resolves a current model into the internal digest stored by Analytics manifests.
     *
     * <p>This design-time read does not resolve product ownership, ACL, or query authority.
     * The default keeps older custom endpoint implementations binary compatible.</p>
     */
    default AnalyticsFunctionEnvelope<AnalyticsModelDependencyDescription>
            resolveModelDependency(AnalyticsModelDependencyResolutionRequest request) {
        throw new UnsupportedOperationException(
                "Analytics model dependency resolution is not implemented");
    }

    /** Lists current selectable model identities within one namespace. */
    default AnalyticsFunctionEnvelope<AnalyticsModelDependencyList>
            listModelDependencies(AnalyticsModelDependencyListRequest request) {
        throw new UnsupportedOperationException(
                "Analytics model dependency listing is not implemented");
    }

    /**
     * Describes one parsed Report or Dashboard at an exact Bundle revision.
     *
     * <p>The default preserves binary compatibility for older custom endpoints;
     * Runtime v1 implementations override it and advertise operation availability.</p>
     */
    default AnalyticsFunctionEnvelope<AnalyticsArtifactDescription> describeArtifact(
            AnalyticsArtifactFunctionRequest request) {
        throw new UnsupportedOperationException(
                "Analytics artifact inspection is not implemented");
    }

    /** Describes the current QM through the current caller's governed authority. */
    default AnalyticsFunctionEnvelope<AnalyticsSemanticModelDescription> describeSemanticModel(
            AnalyticsSemanticModelFunctionRequest request) {
        throw new UnsupportedOperationException(
                "Analytics semantic model description is not implemented");
    }

    /** Executes the strict Function v1 semantic-query subset; raw SQL is not accepted. */
    default AnalyticsFunctionEnvelope<AnalyticsSemanticQueryResult> executeSemanticQuery(
            AnalyticsSemanticQueryFunctionRequest request) {
        throw new UnsupportedOperationException(
                "Analytics semantic query execution is not implemented");
    }

    /** Runs the full MCP-compatible single-model query DSL in validate or execute mode. */
    default AnalyticsFunctionEnvelope<AnalyticsQueryModelResult> runQueryModel(
            AnalyticsQueryModelFunctionRequest request) {
        throw new UnsupportedOperationException(
                "Analytics query-model DSL is not implemented");
    }

    /** Runs restricted SemanticDSL Compose/CTE in validate, preview or execute mode. */
    default AnalyticsFunctionEnvelope<AnalyticsComposeResult> runCompose(
            AnalyticsComposeFunctionRequest request) {
        throw new UnsupportedOperationException(
                "Analytics Compose execution is not implemented");
    }

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
            AnalyticsRenderFunctionRequest request);

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
            AnalyticsRenderFunctionRequest request);

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
            AnalyticsRenderFunctionRequest request);
}

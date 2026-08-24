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
     * Resolves a current model into the stable identity stored by Analytics manifests.
     *
     * <p>This design-time read does not resolve product ownership, ACL, or query authority.
     * The default keeps older custom endpoint implementations binary compatible.</p>
     */
    default AnalyticsFunctionEnvelope<AnalyticsModelDependencyDescription>
            resolveModelDependency(AnalyticsModelDependencyResolutionRequest request) {
        throw new UnsupportedOperationException(
                "Analytics model dependency resolution is not implemented");
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

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
            AnalyticsRenderFunctionRequest request);

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
            AnalyticsRenderFunctionRequest request);

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
            AnalyticsRenderFunctionRequest request);
}

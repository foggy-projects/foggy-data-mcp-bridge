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

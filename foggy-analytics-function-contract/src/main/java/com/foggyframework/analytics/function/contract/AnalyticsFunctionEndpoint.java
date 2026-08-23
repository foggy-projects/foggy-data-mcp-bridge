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

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
            AnalyticsRenderFunctionRequest request);

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
            AnalyticsRenderFunctionRequest request);

    AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
            AnalyticsRenderFunctionRequest request);
}

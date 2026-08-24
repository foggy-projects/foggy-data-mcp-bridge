package com.foggyframework.analytics.function.sdk;

import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyResolutionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelDescription;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsSemanticQueryResult;

import java.util.Objects;

/** In-process facade over the same endpoint used by Analytics Runtime HTTP. */
public final class EmbeddedAnalyticsFunctionClient implements AnalyticsFunctionClient {

    private final AnalyticsFunctionEndpoint endpoint;

    public EmbeddedAnalyticsFunctionClient(AnalyticsFunctionEndpoint endpoint) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsFunctionCapabilities> capabilities(
            AnalyticsFunctionRequestContext context) {
        return requireOutcome(endpoint.capabilities(context));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleList> listBundles(
            AnalyticsFunctionRequestContext context) {
        return requireOutcome(endpoint.listBundles(context));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> validateBundle(
            AnalyticsBundleFunctionRequest request) {
        return requireOutcome(endpoint.validateBundle(request));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> describeBundle(
            AnalyticsBundleFunctionRequest request) {
        return requireOutcome(endpoint.describeBundle(request));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsArtifactDescription> describeArtifact(
            AnalyticsArtifactFunctionRequest request) {
        return requireOutcome(endpoint.describeArtifact(request));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsModelDependencyDescription>
            resolveModelDependency(AnalyticsModelDependencyResolutionRequest request) {
        return requireOutcome(endpoint.resolveModelDependency(request));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsSemanticModelDescription> describeSemanticModel(
            AnalyticsSemanticModelFunctionRequest request) {
        return requireOutcome(endpoint.describeSemanticModel(request));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsSemanticQueryResult> executeSemanticQuery(
            AnalyticsSemanticQueryFunctionRequest request) {
        return requireOutcome(endpoint.executeSemanticQuery(request));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
            AnalyticsRenderFunctionRequest request) {
        return requireOutcome(endpoint.previewReport(request));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
            AnalyticsRenderFunctionRequest request) {
        return requireOutcome(endpoint.previewDashboard(request));
    }

    @Override
    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
            AnalyticsRenderFunctionRequest request) {
        return requireOutcome(endpoint.renderDashboard(request));
    }

    private static <T> AnalyticsFunctionEnvelope<T> requireOutcome(
            AnalyticsFunctionEnvelope<T> outcome) {
        return Objects.requireNonNull(outcome, "Analytics function outcome");
    }
}

package com.foggyframework.analytics.function.fap;

import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.sdk.AnalyticsFunctionClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

final class FapAnalyticsAdapterTestSupport {

    static final String REVISION =
            "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String REQUEST_DIGEST =
            "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private FapAnalyticsAdapterTestSupport() {
    }

    static FapAnalyticsFunctionInvocation invocation(
            String functionRef,
            Map<String, Object> arguments) {
        return invocation(
                FapAnalyticsContract.SERVICE_PROVIDER_CONTRACT_VERSION,
                functionRef,
                arguments);
    }

    static FapAnalyticsFunctionInvocation invocation(
            String contractVersion,
            String functionRef,
            Map<String, Object> arguments) {
        return new FapAnalyticsFunctionInvocation(
                contractVersion,
                "request.analytics.1",
                "function-invocation.analytics.1",
                functionRef,
                arguments,
                REQUEST_DIGEST,
                new FapAnalyticsFunctionInvocation.Caller(
                        "provider.tms",
                        "tenant.acme",
                        "subject.alice",
                        "tms-user-42"));
    }

    static AnalyticsFunctionContext context(
            AnalyticsFunctionRequestContext value) {
        return AnalyticsFunctionContext.normalize(value);
    }

    static AnalyticsBundleDescription bundle() {
        return new AnalyticsBundleDescription(
                "sales-analytics",
                REVISION,
                "analytics-definition/v1",
                "sales",
                "PUBLISHED",
                "CURRENT",
                false,
                true,
                null);
    }

    static AnalyticsFunctionCapabilities capabilities() {
        return new AnalyticsFunctionCapabilities(
                "analytics",
                "foggy-analytics-runtime-api/v1",
                "analytics-runtime/v1",
                true,
                "HOST_AUTHORITY",
                Map.of("analytics.reports.preview", "supported"),
                new AnalyticsFunctionCapabilities.Limits(500, 1),
                List.of());
    }

    static AnalyticsRenderResult render() {
        return new AnalyticsRenderResult(
                new AnalyticsRenderResult.Artifact("REPORT", "sales-report"),
                REVISION,
                "READY",
                List.of(new AnalyticsRenderResult.Widget(
                        "sales-table",
                        new AnalyticsRenderResult.Visual(
                                "TABLE", Map.of("density", "compact")),
                        "READY",
                        List.of(new AnalyticsRenderResult.Column(
                                "amount", "DECIMAL", false)),
                        List.of(Map.of(
                                "amount", new BigDecimal("1234567890.123456789"))),
                        false,
                        List.of())),
                List.of());
    }

    abstract static class StubClient implements AnalyticsFunctionClient {

        int calls;

        RuntimeException unsupported() {
            calls++;
            return new UnsupportedOperationException("unexpected SDK operation");
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsFunctionCapabilities> capabilities(
                AnalyticsFunctionRequestContext context) {
            throw unsupported();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleList> listBundles(
                AnalyticsFunctionRequestContext context) {
            throw unsupported();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> validateBundle(
                AnalyticsBundleFunctionRequest request) {
            throw unsupported();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> describeBundle(
                AnalyticsBundleFunctionRequest request) {
            throw unsupported();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                AnalyticsRenderFunctionRequest request) {
            throw unsupported();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
                AnalyticsRenderFunctionRequest request) {
            throw unsupported();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
                AnalyticsRenderFunctionRequest request) {
            throw unsupported();
        }
    }
}

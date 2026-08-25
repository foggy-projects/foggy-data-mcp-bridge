package com.foggyframework.analytics.function.sdk;

import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsComposeFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsComposeResult;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionCapabilities;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContract;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEndpoint;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionError;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddedAnalyticsFunctionClientTest {

    private static final String REVISION = "sha256:" + "a".repeat(64);

    @Test
    void returnsTheExactRuntimeOutcomeWithoutReprojectingData() {
        AnalyticsFunctionEnvelope<AnalyticsRenderResult> expected =
                AnalyticsFunctionEnvelope.ok(
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                        renderResult(),
                        new AnalyticsFunctionContext("request-1", "trace-1"));
        AnalyticsFunctionClient client = AnalyticsFunctionClients.embedded(
                new EndpointStub() {
                    @Override
                    public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                            AnalyticsRenderFunctionRequest request) {
                        return expected;
                    }
                });

        assertSame(expected, client.previewReport(renderRequest()));
    }

    @Test
    void preservesCanonicalErrorsAndRejectsNullEndpointOutcomes() {
        AnalyticsFunctionEnvelope<AnalyticsBundleDescription> expected =
                AnalyticsFunctionEnvelope.fail(
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                        new AnalyticsFunctionError(
                                "ANALYTICS_BUNDLE_REVISION_CONFLICT",
                                "bundle",
                                "Analytics Bundle revision does not match.",
                                false),
                        new AnalyticsFunctionContext("request-2", "trace-2"));
        AnalyticsBundleFunctionRequest request = new AnalyticsBundleFunctionRequest(
                "sales",
                REVISION,
                new AnalyticsFunctionRequestContext("request-2", "trace-2"));
        AnalyticsFunctionClient preserving = AnalyticsFunctionClients.embedded(
                new EndpointStub() {
                    @Override
                    public AnalyticsFunctionEnvelope<AnalyticsBundleDescription>
                            validateBundle(AnalyticsBundleFunctionRequest ignored) {
                        return expected;
                    }
                });
        AnalyticsFunctionClient invalid = AnalyticsFunctionClients.embedded(
                new EndpointStub() {
                    @Override
                    public AnalyticsFunctionEnvelope<AnalyticsBundleDescription>
                            validateBundle(AnalyticsBundleFunctionRequest ignored) {
                        return null;
                    }
                });

        assertSame(expected, preserving.validateBundle(request));
        assertThrows(NullPointerException.class, () -> invalid.validateBundle(request));
    }

    @Test
    void delegatesAdvancedDslAndComposeOutcomesWithoutReprojection() {
        AnalyticsFunctionEnvelope<AnalyticsQueryModelResult> queryOutcome =
                AnalyticsFunctionEnvelope.ok(
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                        new AnalyticsQueryModelResult(
                                "default",
                                "FactOrderQueryModel",
                                REVISION,
                                "execute",
                                Map.of("rows", List.of())),
                        new AnalyticsFunctionContext("request-1", "trace-1"));
        AnalyticsFunctionEnvelope<AnalyticsComposeResult> composeOutcome =
                AnalyticsFunctionEnvelope.ok(
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION,
                        new AnalyticsComposeResult(
                                "default", "preview", true, false,
                                null, "SELECT 1", List.of(), List.of()),
                        new AnalyticsFunctionContext("request-1", "trace-1"));
        AnalyticsFunctionClient client = AnalyticsFunctionClients.embedded(
                new EndpointStub() {
                    @Override
                    public AnalyticsFunctionEnvelope<AnalyticsQueryModelResult>
                            runQueryModel(AnalyticsQueryModelFunctionRequest request) {
                        return queryOutcome;
                    }

                    @Override
                    public AnalyticsFunctionEnvelope<AnalyticsComposeResult> runCompose(
                            AnalyticsComposeFunctionRequest request) {
                        return composeOutcome;
                    }
                });

        assertSame(queryOutcome, client.runQueryModel(new AnalyticsQueryModelFunctionRequest(
                "default",
                "FactOrderQueryModel",
                REVISION,
                "execute",
                Map.of("columns", List.of("orderCount")),
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                new AnalyticsFunctionRequestContext("request-1", "trace-1"))));
        assertSame(composeOutcome, client.runCompose(new AnalyticsComposeFunctionRequest(
                "default",
                "preview",
                "return 1;",
                Map.of(),
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                new AnalyticsFunctionRequestContext("request-1", "trace-1"))));
    }

    private static AnalyticsRenderFunctionRequest renderRequest() {
        return new AnalyticsRenderFunctionRequest(
                "sales",
                "sales-summary",
                REVISION,
                Map.of(),
                "Asia/Shanghai",
                "zh-CN",
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                new AnalyticsFunctionRequestContext("request-1", "trace-1"));
    }

    private static AnalyticsRenderResult renderResult() {
        return new AnalyticsRenderResult(
                new AnalyticsRenderResult.Artifact("report", "sales-summary"),
                REVISION,
                "ready",
                List.of(),
                List.of());
    }

    private abstract static class EndpointStub implements AnalyticsFunctionEndpoint {

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsFunctionCapabilities> capabilities(
                AnalyticsFunctionRequestContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleList> listBundles(
                AnalyticsFunctionRequestContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> validateBundle(
                AnalyticsBundleFunctionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsBundleDescription> describeBundle(
                AnalyticsBundleFunctionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                AnalyticsRenderFunctionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewDashboard(
                AnalyticsRenderFunctionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AnalyticsFunctionEnvelope<AnalyticsRenderResult> renderDashboard(
                AnalyticsRenderFunctionRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}

package com.foggyframework.analytics.function.fap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foggyframework.analytics.function.contract.AnalyticsArtifactDescription;
import com.foggyframework.analytics.function.contract.AnalyticsArtifactFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FapAnalyticsFunctionRequestMappingTest {

    @Test
    void mapsRenderArgumentsAndHostAuthorityToOneSdkInvocation() {
        class Client extends FapAnalyticsAdapterTestSupport.StubClient {
            AnalyticsRenderFunctionRequest request;

            @Override
            public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                    AnalyticsRenderFunctionRequest value) {
                calls++;
                request = value;
                return AnalyticsFunctionEnvelope.ok(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        FapAnalyticsAdapterTestSupport.render(),
                        FapAnalyticsAdapterTestSupport.context(value.context()));
            }
        }
        Client client = new Client();
        AtomicInteger resolutions = new AtomicInteger();
        FapAnalyticsFunctionAdapter adapter = new FapAnalyticsFunctionAdapter(
                client,
                (caller, operation) -> {
                    resolutions.incrementAndGet();
                    assertThat(caller.externalSubjectRef()).isEqualTo("tms-user-42");
                    assertThat(operation).isEqualTo(
                            AnalyticsFunctionOperations.REPORTS_PREVIEW);
                    return new AnalyticsFunctionAuthority(
                            "tms", "opaque-authority-42");
                });
        Map<String, Object> mutableParameters = new LinkedHashMap<>();
        mutableParameters.put("threshold", new BigDecimal("0.1234567890123456789"));
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("bundleRef", "sales-analytics");
        arguments.put("artifactRef", "sales-report");
        arguments.put("expectedBundleRevision", FapAnalyticsAdapterTestSupport.REVISION);
        arguments.put("parameters", mutableParameters);
        arguments.put("timezone", "Asia/Shanghai");
        arguments.put("locale", "zh-CN");
        FapAnalyticsFunctionInvocation invocation =
                FapAnalyticsAdapterTestSupport.invocation(
                        FapAnalyticsFunctionRefs.REPORTS_PREVIEW,
                        arguments);
        mutableParameters.put("threshold", BigDecimal.ZERO);

        FapAnalyticsFunctionOutcome outcome = adapter.invoke(invocation);

        assertThat(outcome).isInstanceOf(FapAnalyticsFunctionOutcome.Success.class);
        assertThat(client.calls).isEqualTo(1);
        assertThat(resolutions).hasValue(1);
        assertThat(client.request.bundleRef()).isEqualTo("sales-analytics");
        assertThat(client.request.artifactRef()).isEqualTo("sales-report");
        assertThat(client.request.expectedBundleRevision())
                .isEqualTo(FapAnalyticsAdapterTestSupport.REVISION);
        assertThat(client.request.parameters().get("threshold"))
                .isEqualTo(new BigDecimal("0.1234567890123456789"));
        assertThat(client.request.authority())
                .isEqualTo(new AnalyticsFunctionAuthority(
                        "tms", "opaque-authority-42"));
        assertThat(client.request.context().requestId())
                .isEqualTo(invocation.requestId());
        assertThat(client.request.context().traceId())
                .isEqualTo(invocation.functionInvocationId());
    }

    @Test
    void listOperationNeverRequestsProductDataAuthority() {
        class Client extends FapAnalyticsAdapterTestSupport.StubClient {
            @Override
            public AnalyticsFunctionEnvelope<AnalyticsBundleList> listBundles(
                    AnalyticsFunctionRequestContext context) {
                calls++;
                return AnalyticsFunctionEnvelope.ok(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        new AnalyticsBundleList(
                                java.util.List.of(FapAnalyticsAdapterTestSupport.bundle())),
                        FapAnalyticsAdapterTestSupport.context(context));
            }
        }
        Client client = new Client();
        AtomicInteger resolutions = new AtomicInteger();
        FapAnalyticsFunctionAdapter adapter = new FapAnalyticsFunctionAdapter(
                client,
                (caller, operation) -> {
                    resolutions.incrementAndGet();
                    return new AnalyticsFunctionAuthority("tms", "unused");
                });

        FapAnalyticsFunctionOutcome outcome = adapter.invoke(
                FapAnalyticsAdapterTestSupport.invocation(
                        FapAnalyticsFunctionRefs.BUNDLES_LIST,
                        Map.of()));

        assertThat(outcome).isInstanceOf(FapAnalyticsFunctionOutcome.Success.class);
        assertThat(client.calls).isEqualTo(1);
        assertThat(resolutions).hasValue(0);
    }

    @Test
    void mapsExactArtifactInspectionWithoutResolvingDataAuthority() {
        class Client extends FapAnalyticsAdapterTestSupport.StubClient {
            AnalyticsArtifactFunctionRequest request;

            @Override
            public AnalyticsFunctionEnvelope<AnalyticsArtifactDescription> describeArtifact(
                    AnalyticsArtifactFunctionRequest value) {
                calls++;
                request = value;
                return AnalyticsFunctionEnvelope.ok(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        new AnalyticsArtifactDescription(
                                value.bundleRef(),
                                value.expectedBundleRevision(),
                                value.artifactKind(),
                                value.artifactRef()),
                        FapAnalyticsAdapterTestSupport.context(value.context()));
            }
        }
        Client client = new Client();
        AtomicInteger resolutions = new AtomicInteger();
        FapAnalyticsFunctionAdapter adapter = new FapAnalyticsFunctionAdapter(
                client,
                (caller, operation) -> {
                    resolutions.incrementAndGet();
                    return new AnalyticsFunctionAuthority("tms", "unused");
                });

        FapAnalyticsFunctionOutcome outcome = adapter.invoke(
                FapAnalyticsAdapterTestSupport.invocation(
                        FapAnalyticsFunctionRefs.ARTIFACTS_DESCRIBE,
                        Map.of(
                                "bundleRef", "sales-analytics",
                                "artifactKind", "report",
                                "artifactRef", "sales-report",
                                "expectedBundleRevision",
                                FapAnalyticsAdapterTestSupport.REVISION)));

        assertThat(outcome).isInstanceOf(FapAnalyticsFunctionOutcome.Success.class);
        assertThat(client.calls).isEqualTo(1);
        assertThat(resolutions).hasValue(0);
        assertThat(client.request.artifactKind()).isEqualTo("report");
        assertThat(client.request.artifactRef()).isEqualTo("sales-report");
    }

    @Test
    void invocationAndResultValuesAreRecursivelyImmutable() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("region", "east");
        FapAnalyticsFunctionInvocation invocation =
                FapAnalyticsAdapterTestSupport.invocation(
                        FapAnalyticsFunctionRefs.REPORTS_PREVIEW,
                        Map.of(
                                "bundleRef", "sales-analytics",
                                "artifactRef", "sales-report",
                                "expectedBundleRevision",
                                FapAnalyticsAdapterTestSupport.REVISION,
                                "parameters", nested,
                                "timezone", "Asia/Shanghai",
                                "locale", "zh-CN"));

        nested.put("region", "west");

        @SuppressWarnings("unchecked")
        Map<String, Object> captured = (Map<String, Object>)
                invocation.arguments().get("parameters");
        assertThat(captured).containsEntry("region", "east");
        assertThatThrownBy(() -> captured.put("region", "north"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

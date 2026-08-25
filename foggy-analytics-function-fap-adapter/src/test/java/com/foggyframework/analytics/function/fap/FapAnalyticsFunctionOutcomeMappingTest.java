package com.foggyframework.analytics.function.fap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContext;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionError;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionErrorCodes;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsQueryModelResult;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FapAnalyticsFunctionOutcomeMappingTest {

    @Test
    void canonicalDigestMatchesServiceProviderForNestedDecimalResults() {
        Map<String, Object> composeResult = Map.of(
                "rows",
                List.of(Map.of("amount", new BigDecimal("10998.0"))));

        assertThat(FapCanonicalDigests.json(composeResult)).isEqualTo(
                "sha256:433caec709b3646d32eabb27c9fc57eba6600513567aa4d455515b729c1c2a8d");
    }

    @Test
    void successProducesExactCallbackEnvelopeAndCanonicalResultDigest() {
        FapAnalyticsAdapterTestSupport.StubClient client = successClient();
        FapAnalyticsFunctionAdapter adapter = adapter(client);

        FapAnalyticsFunctionOutcome.Success success =
                (FapAnalyticsFunctionOutcome.Success) adapter.invoke(renderInvocation());

        assertThat(success.recommendedHttpStatus()).isEqualTo(200);
        assertThat(success.resultDigest()).isEqualTo(
                FapCanonicalDigests.json(success.result()));
        assertThat(success.callbackBody())
                .containsEntry("type", FapAnalyticsContract.CALLBACK_RESULT_TYPE)
                .containsEntry("functionInvocationId", success.functionInvocationId())
                .containsEntry("resultDigest", success.resultDigest());
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>)
                success.callbackBody().get("meta");
        assertThat(meta).containsOnly(
                org.assertj.core.data.MapEntry.entry(
                        "contractVersion",
                        FapAnalyticsContract.SERVICE_PROVIDER_CONTRACT_VERSION),
                org.assertj.core.data.MapEntry.entry("requestId", success.requestId()));
        assertThat(success.result())
                .containsEntry("operation", "analytics.reports.preview")
                .containsEntry(
                        "functionContractVersion", "foggy-analytics-function/v1");
        assertThatThrownBy(() -> success.result().put("operation", "tampered"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void analyticsFailurePreservesSafeCodeRetryabilityAndStatusFamily() {
        class Client extends FapAnalyticsAdapterTestSupport.StubClient {
            @Override
            public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                    AnalyticsRenderFunctionRequest request) {
                calls++;
                return AnalyticsFunctionEnvelope.fail(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        new AnalyticsFunctionError(
                                AnalyticsFunctionErrorCodes.BUNDLE_REVISION_CONFLICT,
                                "resolve",
                                "Bundle revision does not match",
                                true),
                        FapAnalyticsAdapterTestSupport.context(request.context()));
            }
        }
        Client client = new Client();

        FapAnalyticsFunctionOutcome.Failure failure =
                (FapAnalyticsFunctionOutcome.Failure)
                        adapter(client).invoke(renderInvocation());

        assertThat(client.calls).isEqualTo(1);
        assertThat(failure.code()).isEqualTo(
                AnalyticsFunctionErrorCodes.BUNDLE_REVISION_CONFLICT);
        assertThat(failure.retryable()).isTrue();
        assertThat(failure.recommendedHttpStatus()).isEqualTo(409);
        assertThat(failure.callbackBody()).containsOnly(
                org.assertj.core.data.MapEntry.entry(
                        "code", AnalyticsFunctionErrorCodes.BUNDLE_REVISION_CONFLICT),
                org.assertj.core.data.MapEntry.entry(
                        "message", "Bundle revision does not match"));
    }

    @Test
    void semanticQueryValidationFailureBecomesRepairablePreEffectToolData() {
        class Client extends FapAnalyticsAdapterTestSupport.StubClient {
            @Override
            public AnalyticsFunctionEnvelope<AnalyticsQueryModelResult> runQueryModel(
                    AnalyticsQueryModelFunctionRequest request) {
                calls++;
                return AnalyticsFunctionEnvelope.fail(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        new AnalyticsFunctionError(
                                AnalyticsFunctionErrorCodes.SEMANTIC_QUERY_INVALID,
                                "semantic-query",
                                "unsafe original field value must not cross the boundary",
                                false),
                        FapAnalyticsAdapterTestSupport.context(request.context()));
            }
        }
        Client client = new Client();
        FapAnalyticsFunctionInvocation invocation =
                FapAnalyticsAdapterTestSupport.invocation(
                        FapAnalyticsFunctionRefs.QUERY_MODEL_RUN,
                        Map.of(
                                "namespace", "default",
                                "modelName", "FactOrderQueryModel",
                                "mode", "validate",
                                "payload", Map.of(
                                        "columns", List.of("missingField"))));

        FapAnalyticsFunctionOutcome.Failure failure =
                (FapAnalyticsFunctionOutcome.Failure)
                        adapter(client).invoke(invocation);

        String expectedSchemaDigest = FapAnalyticsFunctionCatalog
                .findByFunctionRef(FapAnalyticsFunctionRefs.QUERY_MODEL_RUN)
                .orElseThrow()
                .projection()
                .schemaDigest();
        assertThat(client.calls).isEqualTo(1);
        assertThat(failure.code()).isEqualTo(
                FapAnalyticsErrorCodes.FUNCTION_ARGUMENT_INVALID);
        assertThat(failure.retryable()).isFalse();
        assertThat(failure.recommendedHttpStatus()).isEqualTo(422);
        assertThat(failure.callbackBody())
                .containsEntry("modelRepairable", true)
                .containsEntry("effectPhase", "PRE_EFFECT")
                .containsEntry("functionRef", FapAnalyticsFunctionRefs.QUERY_MODEL_RUN)
                .containsEntry("schemaDigest", expectedSchemaDigest);
        assertThat(failure.callbackBody().get("violations")).isEqualTo(List.of(Map.of(
                "instancePath", "/payload",
                "keyword", "semanticQuery",
                "messageKey", "SEMANTIC_QUERY_INVALID")));
        assertThat(failure.callbackBody().toString())
                .doesNotContain("unsafe original field value", "missingField");
    }

    @Test
    void stableSemanticValidatorKeyIsPreservedForModelRepair() {
        class Client extends FapAnalyticsAdapterTestSupport.StubClient {
            @Override
            public AnalyticsFunctionEnvelope<AnalyticsQueryModelResult> runQueryModel(
                    AnalyticsQueryModelFunctionRequest request) {
                calls++;
                return AnalyticsFunctionEnvelope.fail(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        new AnalyticsFunctionError(
                                AnalyticsFunctionErrorCodes.SEMANTIC_QUERY_INVALID,
                                "semantic-query",
                                "DSL_CTE_STAGE_REFERENCE_INVALID",
                                false),
                        FapAnalyticsAdapterTestSupport.context(request.context()));
            }
        }
        Client client = new Client();
        FapAnalyticsFunctionInvocation invocation =
                FapAnalyticsAdapterTestSupport.invocation(
                        FapAnalyticsFunctionRefs.QUERY_MODEL_RUN,
                        Map.of(
                                "namespace", "default",
                                "modelName", "FactOrderQueryModel",
                                "mode", "validate",
                                "payload", Map.of(
                                        "columns", List.of("amount"),
                                        "route", "DSL_CTE")));

        FapAnalyticsFunctionOutcome.Failure failure =
                (FapAnalyticsFunctionOutcome.Failure)
                        adapter(client).invoke(invocation);

        assertThat(client.calls).isEqualTo(1);
        assertThat(failure.code())
                .isEqualTo(FapAnalyticsErrorCodes.FUNCTION_ARGUMENT_INVALID);
        assertThat(failure.recommendedHttpStatus()).isEqualTo(422);
        assertThat(failure.callbackBody().get("violations")).isEqualTo(List.of(Map.of(
                "instancePath", "/payload",
                "keyword", "semanticQuery",
                "messageKey", "DSL_CTE_STAGE_REFERENCE_INVALID")));
    }

    @Test
    void invalidArgumentsFailBeforeAuthorityResolutionOrSdkCall() {
        FapAnalyticsAdapterTestSupport.StubClient client = successClient();
        AtomicInteger resolutions = new AtomicInteger();
        FapAnalyticsFunctionAdapter adapter = new FapAnalyticsFunctionAdapter(
                client,
                (caller, operation) -> {
                    resolutions.incrementAndGet();
                    return new AnalyticsFunctionAuthority("tms", "authority");
                });
        FapAnalyticsFunctionInvocation invalid =
                FapAnalyticsAdapterTestSupport.invocation(
                        FapAnalyticsFunctionRefs.REPORTS_PREVIEW,
                        Map.of(
                                "bundleRef", "sales-analytics",
                                "artifactRef", "sales-report",
                                "expectedBundleRevision",
                                FapAnalyticsAdapterTestSupport.REVISION,
                                "timezone", "Asia/Shanghai",
                                "locale", "zh-CN",
                                "rawSql", "select * from secret"));

        FapAnalyticsFunctionOutcome.Failure failure =
                (FapAnalyticsFunctionOutcome.Failure) adapter.invoke(invalid);
        FapAnalyticsFunctionOutcome.Failure unsafeRef =
                (FapAnalyticsFunctionOutcome.Failure) adapter.invoke(
                        FapAnalyticsAdapterTestSupport.invocation(
                                FapAnalyticsFunctionRefs.REPORTS_PREVIEW,
                                Map.of(
                                        "bundleRef", "../unsafe",
                                        "artifactRef", "sales-report",
                                        "expectedBundleRevision",
                                        FapAnalyticsAdapterTestSupport.REVISION,
                                        "timezone", "Asia/Shanghai",
                                        "locale", "zh-CN")));

        assertThat(failure.code()).isEqualTo(
                FapAnalyticsErrorCodes.ARGUMENTS_INVALID);
        assertThat(failure.recommendedHttpStatus()).isEqualTo(422);
        assertThat(unsafeRef.code()).isEqualTo(
                FapAnalyticsErrorCodes.ARGUMENTS_INVALID);
        assertThat(client.calls).isZero();
        assertThat(resolutions).hasValue(0);
    }

    @Test
    void authorityResolverDenialIsSanitizedAndDoesNotInvokeSdk() {
        FapAnalyticsAdapterTestSupport.StubClient client = successClient();
        FapAnalyticsFunctionAdapter adapter = new FapAnalyticsFunctionAdapter(
                client,
                (caller, operation) -> null);

        FapAnalyticsFunctionOutcome.Failure failure =
                (FapAnalyticsFunctionOutcome.Failure)
                        adapter.invoke(renderInvocation());

        assertThat(failure.code()).isEqualTo(
                FapAnalyticsErrorCodes.AUTHORITY_UNAVAILABLE);
        assertThat(failure.recommendedHttpStatus()).isEqualTo(403);
        assertThat(client.calls).isZero();
        assertThat(failure.callbackBody().toString())
                .doesNotContain("tms-user-42", "subject.alice");
    }

    @Test
    void unknownFunctionAndContractVersionFailWithoutSdkCall() {
        FapAnalyticsAdapterTestSupport.StubClient client = successClient();
        FapAnalyticsFunctionAdapter adapter = adapter(client);

        FapAnalyticsFunctionOutcome.Failure unknown =
                (FapAnalyticsFunctionOutcome.Failure) adapter.invoke(
                        FapAnalyticsAdapterTestSupport.invocation(
                                "unknown.analytics@v1", Map.of()));
        FapAnalyticsFunctionOutcome.Failure version =
                (FapAnalyticsFunctionOutcome.Failure) adapter.invoke(
                        FapAnalyticsAdapterTestSupport.invocation(
                                "fap.service-provider.v2",
                                FapAnalyticsFunctionRefs.BUNDLES_LIST,
                                Map.of()));

        assertThat(unknown.code()).isEqualTo(
                FapAnalyticsErrorCodes.FUNCTION_UNKNOWN);
        assertThat(unknown.recommendedHttpStatus()).isEqualTo(404);
        assertThat(version.code()).isEqualTo(
                FapAnalyticsErrorCodes.CONTRACT_UNSUPPORTED);
        assertThat(version.recommendedHttpStatus()).isEqualTo(422);
        assertThat(client.calls).isZero();
    }

    @Test
    void correlationMismatchAndUnsafeErrorCodeBecomeProtocolFailures() {
        class CorrelationClient extends FapAnalyticsAdapterTestSupport.StubClient {
            @Override
            public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                    AnalyticsRenderFunctionRequest request) {
                calls++;
                return AnalyticsFunctionEnvelope.ok(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        FapAnalyticsAdapterTestSupport.render(),
                        new AnalyticsFunctionContext("other-request", "other-trace"));
            }
        }
        CorrelationClient correlationClient = new CorrelationClient();
        FapAnalyticsFunctionOutcome.Failure correlation =
                (FapAnalyticsFunctionOutcome.Failure)
                        adapter(correlationClient).invoke(renderInvocation());

        class UnsafeErrorClient extends FapAnalyticsAdapterTestSupport.StubClient {
            @Override
            public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                    AnalyticsRenderFunctionRequest request) {
                calls++;
                return AnalyticsFunctionEnvelope.fail(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        new AnalyticsFunctionError(
                                "unsafe-error", "runtime", "unsafe", false),
                        FapAnalyticsAdapterTestSupport.context(request.context()));
            }
        }
        FapAnalyticsFunctionOutcome.Failure unsafe =
                (FapAnalyticsFunctionOutcome.Failure)
                        adapter(new UnsafeErrorClient()).invoke(renderInvocation());

        assertThat(correlation.code()).isEqualTo(
                FapAnalyticsErrorCodes.PROTOCOL_ERROR);
        assertThat(correlation.recommendedHttpStatus()).isEqualTo(502);
        assertThat(unsafe.code()).isEqualTo(FapAnalyticsErrorCodes.PROTOCOL_ERROR);
        assertThat(unsafe.recommendedHttpStatus()).isEqualTo(502);
    }

    @Test
    void sdkExceptionIsNotRetriedAndIsProjectedWithoutItsMessage() {
        class Client extends FapAnalyticsAdapterTestSupport.StubClient {
            @Override
            public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                    AnalyticsRenderFunctionRequest request) {
                calls++;
                throw new IllegalStateException("credential=secret-value");
            }
        }
        Client client = new Client();

        FapAnalyticsFunctionOutcome.Failure failure =
                (FapAnalyticsFunctionOutcome.Failure)
                        adapter(client).invoke(renderInvocation());

        assertThat(client.calls).isEqualTo(1);
        assertThat(failure.code()).isEqualTo(
                FapAnalyticsErrorCodes.ADAPTER_INTERNAL);
        assertThat(failure.recommendedHttpStatus()).isEqualTo(500);
        assertThat(failure.callbackBody().toString())
                .doesNotContain("secret-value", "credential");
    }

    private static FapAnalyticsFunctionAdapter adapter(
            FapAnalyticsAdapterTestSupport.StubClient client) {
        return new FapAnalyticsFunctionAdapter(
                client,
                (caller, operation) -> new AnalyticsFunctionAuthority(
                        "tms", "opaque-authority"));
    }

    private static FapAnalyticsAdapterTestSupport.StubClient successClient() {
        return new FapAnalyticsAdapterTestSupport.StubClient() {
            @Override
            public AnalyticsFunctionEnvelope<AnalyticsRenderResult> previewReport(
                    AnalyticsRenderFunctionRequest request) {
                calls++;
                return AnalyticsFunctionEnvelope.ok(
                        "foggy-analytics-runtime-api/v1",
                        "analytics-runtime/v1",
                        FapAnalyticsAdapterTestSupport.render(),
                        FapAnalyticsAdapterTestSupport.context(request.context()));
            }
        };
    }

    private static FapAnalyticsFunctionInvocation renderInvocation() {
        return FapAnalyticsAdapterTestSupport.invocation(
                FapAnalyticsFunctionRefs.REPORTS_PREVIEW,
                Map.of(
                        "bundleRef", "sales-analytics",
                        "artifactRef", "sales-report",
                        "expectedBundleRevision", FapAnalyticsAdapterTestSupport.REVISION,
                        "parameters", Map.of("region", "east"),
                        "timezone", "Asia/Shanghai",
                        "locale", "zh-CN"));
    }
}

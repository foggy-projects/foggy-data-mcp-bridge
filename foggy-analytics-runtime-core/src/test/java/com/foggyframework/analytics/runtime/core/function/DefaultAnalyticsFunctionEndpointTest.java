package com.foggyframework.analytics.runtime.core.function;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleLifecycle;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsRenderModel;
import com.foggyframework.analytics.definition.api.AnalyticsRenderState;
import com.foggyframework.analytics.definition.api.AnalyticsSchemaVersion;
import com.foggyframework.analytics.definition.core.AnalyticsBundleRegistration;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.definition.core.AnalyticsDefinitionSnapshot;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.function.contract.AnalyticsBundleFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionAuthority;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionContract;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionEnvelope;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionOperations;
import com.foggyframework.analytics.function.contract.AnalyticsFunctionRequestContext;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyDescription;
import com.foggyframework.analytics.function.contract.AnalyticsModelDependencyResolutionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderFunctionRequest;
import com.foggyframework.analytics.function.contract.AnalyticsRenderResult;
import com.foggyframework.analytics.runtime.core.render.AnalyticsDashboardRenderRequest;
import com.foggyframework.analytics.runtime.core.render.AnalyticsReportPreviewRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAnalyticsFunctionEndpointTest {

    private static final AnalyticsBundleRef SALES = new AnalyticsBundleRef("sales");
    private static final AnalyticsBundleRevision REVISION =
            AnalyticsBundleRevision.fromSha256Hex("a".repeat(64));

    @Test
    void exposesCanonicalOperationsAndHonestCompositionState() {
        DefaultAnalyticsFunctionEndpoint endpoint = endpoint(
                store(ignored -> resolved()),
                null);

        var outcome = endpoint.capabilities(
                new AnalyticsFunctionRequestContext("request-1", "trace-1"));

        assertTrue(outcome.success());
        assertEquals(AnalyticsFunctionContract.VERSION,
                outcome.functionContractVersion());
        assertEquals("supported", outcome.data().operations().get(
                AnalyticsFunctionOperations.BUNDLES_DESCRIBE));
        assertEquals("unsupported", outcome.data().operations().get(
                AnalyticsFunctionOperations.BUNDLES_SAVE));
        assertEquals("unavailable", outcome.data().operations().get(
                AnalyticsFunctionOperations.REPORTS_PREVIEW));
        assertEquals("unavailable", outcome.data().operations().get(
                AnalyticsFunctionOperations.MODEL_DEPENDENCIES_RESOLVE));
    }

    @Test
    void resolvesStableModelDependencyWithoutProductOrAuthorityContext() {
        AnalyticsModelDependencyOperations models = (namespace, modelKind, modelName) ->
                new AnalyticsModelDependencyDescription(
                        namespace, modelKind, modelName, REVISION.value());
        DefaultAnalyticsFunctionEndpoint endpoint = endpoint(
                store(ignored -> resolved()),
                models,
                null);

        var outcome = endpoint.resolveModelDependency(
                new AnalyticsModelDependencyResolutionRequest(
                        "default",
                        "qm",
                        "SalesQuery",
                        new AnalyticsFunctionRequestContext("request-model", "trace-model")));

        assertTrue(outcome.success());
        assertEquals(REVISION.value(), outcome.data().modelRevision());
        assertEquals("supported", endpoint.capabilities(
                AnalyticsFunctionRequestContext.empty()).data().operations().get(
                        AnalyticsFunctionOperations.MODEL_DEPENDENCIES_RESOLVE));
    }

    @Test
    void sanitizesUnavailableStableRevision() {
        AnalyticsModelDependencyOperations models = (namespace, modelKind, modelName) -> {
            throw new AnalyticsModelDependencyResolutionException(
                    AnalyticsModelDependencyResolutionException.Code.REVISION_UNAVAILABLE,
                    "private catalog provenance");
        };
        DefaultAnalyticsFunctionEndpoint endpoint = endpoint(
                store(ignored -> resolved()),
                models,
                null);

        var outcome = endpoint.resolveModelDependency(
                new AnalyticsModelDependencyResolutionRequest(
                        "default",
                        "qm",
                        "SalesQuery",
                        AnalyticsFunctionRequestContext.empty()));

        assertFalse(outcome.success());
        assertEquals(
                "ANALYTICS_MODEL_DEPENDENCY_REVISION_UNAVAILABLE",
                outcome.error().code());
        assertTrue(outcome.error().retryable());
        assertFalse(outcome.error().message().contains("private"));
    }

    @Test
    void validateAndDescribeShareExactLogicalBundleContract() {
        DefaultAnalyticsFunctionEndpoint endpoint = endpoint(
                store(ignored -> resolved()),
                null);
        AnalyticsBundleFunctionRequest request = new AnalyticsBundleFunctionRequest(
                "sales",
                REVISION.value(),
                new AnalyticsFunctionRequestContext("request-2", "trace-2"));

        var validated = endpoint.validateBundle(request);
        var described = endpoint.describeBundle(request);

        assertTrue(validated.success());
        assertEquals(validated.data(), described.data());
        assertEquals("default", validated.data().namespaceRef());
        assertEquals(REVISION.value(), validated.data().bundleRevision());
    }

    @Test
    void mapsFunctionRenderRequestOnceAndReturnsRendererNeutralProjection() {
        AtomicReference<AnalyticsReportPreviewRequest> captured = new AtomicReference<>();
        AnalyticsFunctionRenderOperations render = new AnalyticsFunctionRenderOperations() {
            @Override
            public AnalyticsRenderModel previewReport(
                    AnalyticsReportPreviewRequest request) {
                captured.set(request);
                return renderModel();
            }

            @Override
            public AnalyticsRenderModel previewDashboard(
                    AnalyticsDashboardRenderRequest request) {
                return renderModel();
            }

            @Override
            public AnalyticsRenderModel renderDashboard(
                    AnalyticsDashboardRenderRequest request) {
                return renderModel();
            }
        };
        DefaultAnalyticsFunctionEndpoint endpoint = endpoint(
                store(ignored -> resolved()),
                render);

        AnalyticsFunctionEnvelope<AnalyticsRenderResult> outcome =
                endpoint.previewReport(renderRequest());

        assertTrue(outcome.success());
        assertEquals("report", outcome.data().artifact().kind());
        assertEquals("sales-summary", outcome.data().artifact().ref());
        assertEquals("tms", captured.get().context().authorityBinding().provider());
        assertEquals("subject:42",
                captured.get().context().authorityBinding().reference());
        assertEquals("east", captured.get().context().parameters().get("region"));
        assertEquals("request-render", captured.get().context().requestId());
    }

    @Test
    void failsClosedWhenRenderCompositionIsUnavailable() {
        DefaultAnalyticsFunctionEndpoint endpoint = endpoint(
                store(ignored -> resolved()),
                null);

        AnalyticsFunctionEnvelope<AnalyticsRenderResult> outcome =
                endpoint.previewReport(renderRequest());

        assertFalse(outcome.success());
        assertEquals("ANALYTICS_RENDER_UNAVAILABLE", outcome.error().code());
        assertEquals("composition", outcome.error().phase());
    }

    @Test
    void sanitizesStoreFailuresBeforeAnyTransportSeesThem() {
        DefaultAnalyticsFunctionEndpoint endpoint = endpoint(
                store(ignored -> {
                    throw new AnalyticsBundleStoreException(
                            AnalyticsBundleStoreException.Code.INVALID_BUNDLE,
                            "invalid /srv/private/customer-a/manifest.json");
                }),
                null);
        AnalyticsBundleFunctionRequest request = new AnalyticsBundleFunctionRequest(
                "sales",
                REVISION.value(),
                new AnalyticsFunctionRequestContext("request-3", "trace-3"));

        var outcome = endpoint.validateBundle(request);

        assertFalse(outcome.success());
        assertEquals("ANALYTICS_BUNDLE_INVALID_BUNDLE", outcome.error().code());
        assertEquals("Analytics Bundle validation failed.",
                outcome.error().message());
        assertFalse(outcome.error().message().contains("/srv/private"));
    }

    @Test
    void classifiesUnexpectedNullFailuresAsInternalInsteadOfCallerErrors() {
        DefaultAnalyticsFunctionEndpoint endpoint = endpoint(
                store(ignored -> {
                    throw new NullPointerException("internal implementation detail");
                }),
                null);

        var outcome = endpoint.validateBundle(new AnalyticsBundleFunctionRequest(
                "sales",
                REVISION.value(),
                new AnalyticsFunctionRequestContext("request-4", "trace-4")));

        assertFalse(outcome.success());
        assertEquals("ANALYTICS_INTERNAL_ERROR", outcome.error().code());
        assertEquals("Analytics operation failed.", outcome.error().message());
    }

    private static DefaultAnalyticsFunctionEndpoint endpoint(
            AnalyticsBundleStore store,
            AnalyticsFunctionRenderOperations render) {
        return endpoint(store, null, render);
    }

    private static DefaultAnalyticsFunctionEndpoint endpoint(
            AnalyticsBundleStore store,
            AnalyticsModelDependencyOperations models,
            AnalyticsFunctionRenderOperations render) {
        AnalyticsBundleFunctionOperations bundleOperations =
                new AnalyticsBundleFunctionOperations(
                        List.of(new AnalyticsBundleRegistration(
                                SALES,
                                Path.of("target/function-endpoint-test/sales"),
                                AnalyticsBundleSourceState.CONFIGURED)),
                        store);
        return new DefaultAnalyticsFunctionEndpoint(
                true,
                "host-managed",
                1_000,
                bundleOperations,
                () -> models,
                () -> render,
                new AnalyticsFunctionResponseFactory(
                        AnalyticsFunctionContract.DEFAULT_RUNTIME_API_VERSION,
                        AnalyticsFunctionContract.DEFAULT_SCHEMA_VERSION),
                new AnalyticsFunctionFailureMapper());
    }

    private static AnalyticsRenderFunctionRequest renderRequest() {
        return new AnalyticsRenderFunctionRequest(
                "sales",
                "sales-summary",
                REVISION.value(),
                Map.of("region", "east"),
                "Asia/Shanghai",
                "zh-CN",
                new AnalyticsFunctionAuthority("tms", "subject:42"),
                new AnalyticsFunctionRequestContext(
                        "request-render", "trace-render"));
    }

    private static AnalyticsRenderModel renderModel() {
        return new AnalyticsRenderModel(
                new AnalyticsArtifactRef(
                        AnalyticsArtifactKind.REPORT,
                        "sales-summary"),
                REVISION,
                AnalyticsRenderState.READY,
                List.of(),
                List.of());
    }

    private static ResolvedAnalyticsBundle resolved() {
        return new ResolvedAnalyticsBundle(
                new AnalyticsBundleManifest(
                        AnalyticsBundleManifest.ANALYTICS_KIND,
                        AnalyticsSchemaVersion.V1,
                        SALES,
                        REVISION,
                        new AnalyticsNamespaceRef("default"),
                        List.of()),
                new AnalyticsBundleLifecycle(
                        AnalyticsBundleSourceState.CONFIGURED,
                        AnalyticsBundleDependencyState.CURRENT));
    }

    private static AnalyticsBundleStore store(
            Function<AnalyticsBundleRef, ResolvedAnalyticsBundle> resolver) {
        return new AnalyticsBundleStore() {
            @Override
            public ResolvedAnalyticsBundle resolve(AnalyticsBundleRef bundleRef) {
                return resolver.apply(bundleRef);
            }

            @Override
            public byte[] readArtifact(
                    AnalyticsBundleRef bundleRef,
                    AnalyticsBundleRevision expectedRevision,
                    String relativePath) {
                throw new UnsupportedOperationException();
            }

            @Override
            public AnalyticsDefinitionSnapshot readDefinitionSnapshot(
                    AnalyticsBundleRef bundleRef,
                    AnalyticsBundleRevision expectedRevision) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ResolvedAnalyticsBundle saveArtifact(
                    AnalyticsBundleRef bundleRef,
                    AnalyticsBundleRevision expectedRevision,
                    String relativePath,
                    byte[] content) {
                throw new UnsupportedOperationException();
            }
        };
    }
}

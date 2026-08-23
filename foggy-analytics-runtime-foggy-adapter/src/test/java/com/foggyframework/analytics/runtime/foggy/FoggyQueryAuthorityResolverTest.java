package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityBinding;
import com.foggyframework.analytics.runtime.core.query.QueryAuthorityRequest;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.CATALOG_IDENTITY;
import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.ENGINE_NAMESPACE;
import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.MODEL;
import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.MODEL_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoggyQueryAuthorityResolverTest {

    @Test
    void resolvesOpaqueAuthorityAgainstStableRevisionAndPinsCatalog() {
        AtomicReference<QueryAuthorityRequest> authorityRequest = new AtomicReference<>();
        AtomicReference<FoggyModelRevisionLookup> revisionLookup = new AtomicReference<>();
        NamespaceCatalogView view = FoggyAdapterTestFixtures.trackedView();
        CatalogResolution<QueryModel> resolution = view.resolutionsByModel().get(MODEL);
        FoggyQueryAuthorityResolver resolver = new FoggyQueryAuthorityResolver(
                catalog(view),
                lookup -> {
                    revisionLookup.set(lookup);
                    return Optional.of(MODEL_REVISION);
                },
                (request, selectedResolution) -> {
                    authorityRequest.set(request);
                    assertSame(resolution.model(), selectedResolution.model());
                    return SemanticRequestContext.ofNamespace(ENGINE_NAMESPACE);
                });

        FoggyAnalyticsAuthority authority = resolver.resolve(request(
                FoggyAdapterTestFixtures.queryDependency(),
                "opaque-authority-42"));

        assertEquals("opaque-authority-42", authorityRequest.get().binding().reference());
        assertEquals(CATALOG_IDENTITY, revisionLookup.get().catalogIdentity());
        assertEquals(MODEL, revisionLookup.get().canonicalModelName());
        assertEquals(FoggyAdapterTestFixtures.queryDependency(), authority.modelDependency());
        assertEquals(ENGINE_NAMESPACE, authority.engineNamespace());
        assertEquals(CATALOG_IDENTITY, authority.catalogIdentity());
        assertSame(
                authority.catalogResolution().model(),
                authority.semanticRequestContext().getCatalogResolution().model());
    }

    @Test
    void rejectsStalePersistedModelRevisionBeforeResolvingAuthority() {
        FoggyQueryAuthorityResolver resolver = new FoggyQueryAuthorityResolver(
                catalog(FoggyAdapterTestFixtures.trackedView()),
                lookup -> Optional.of(
                        AnalyticsModelRevision.fromSha256Hex("c".repeat(64))),
                (request, resolution) -> {
                    throw new AssertionError("stale dependency must not reach authority resolver");
                });

        FoggyAnalyticsAdapterException failure = assertThrows(
                FoggyAnalyticsAdapterException.class,
                () -> resolver.resolve(request(
                        FoggyAdapterTestFixtures.queryDependency(),
                        "opaque-authority-42")));

        assertEquals(
                FoggyAnalyticsAdapterException.Code.MODEL_REVISION_MISMATCH,
                failure.code());
    }

    @Test
    void rejectsAliasesAsPersistedModelIdentity() {
        AnalyticsModelDependency aliasDependency = FoggyAdapterTestFixtures.dependency(
                "qm",
                "SO",
                MODEL_REVISION);
        FoggyQueryAuthorityResolver resolver = new FoggyQueryAuthorityResolver(
                catalog(FoggyAdapterTestFixtures.trackedView()),
                lookup -> Optional.of(MODEL_REVISION),
                (request, resolution) -> SemanticRequestContext.ofNamespace(ENGINE_NAMESPACE));

        FoggyAnalyticsAdapterException failure = assertThrows(
                FoggyAnalyticsAdapterException.class,
                () -> resolver.resolve(request(aliasDependency, "opaque-authority-42")));

        assertEquals(
                FoggyAnalyticsAdapterException.Code.MODEL_NAME_NOT_CANONICAL,
                failure.code());
    }

    @Test
    void rejectsHostContextPinnedToAnotherCatalogResolution() {
        CatalogIdentity otherIdentity = new CatalogIdentity(
                ENGINE_NAMESPACE,
                new CatalogGeneration("catalog:test:2"),
                new SourceRevision("source:test:2"));
        CatalogResolution<QueryModel> otherResolution =
                FoggyAdapterTestFixtures.resolution(otherIdentity, MODEL);
        FoggyQueryAuthorityResolver resolver = new FoggyQueryAuthorityResolver(
                catalog(FoggyAdapterTestFixtures.trackedView()),
                lookup -> Optional.of(MODEL_REVISION),
                (request, resolution) -> SemanticRequestContext.ofNamespace(ENGINE_NAMESPACE)
                        .withCatalogResolution(otherResolution));

        FoggyAnalyticsAdapterException failure = assertThrows(
                FoggyAnalyticsAdapterException.class,
                () -> resolver.resolve(request(
                        FoggyAdapterTestFixtures.queryDependency(),
                        "opaque-authority-42")));

        assertEquals(
                FoggyAnalyticsAdapterException.Code.AUTHORITY_CATALOG_CONFLICT,
                failure.code());
    }

    private static QueryAuthorityRequest request(
            AnalyticsModelDependency dependency,
            String authorityReference) {
        return new QueryAuthorityRequest(
                dependency,
                new QueryAuthorityBinding("host", authorityReference),
                "request-1",
                "trace-1");
    }

    private static SemanticModelCatalogReadPort catalog(NamespaceCatalogView view) {
        return namespace -> view;
    }
}

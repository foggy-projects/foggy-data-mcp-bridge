package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelDigest;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.MODEL;
import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.MODEL_DIGEST;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FoggyAnalyticsBundleDependencyStateResolverTest {

    @Test
    void marksQmAndTmDependenciesCurrentAgainstOneExactCatalogView() {
        AnalyticsModelDigest tableDigest =
                AnalyticsModelDigest.fromSha256Hex("c".repeat(64));
        AnalyticsModelDependency queryDependency = FoggyAdapterTestFixtures.queryDependency();
        AnalyticsModelDependency tableDependency = FoggyAdapterTestFixtures.dependency(
                "tm",
                "SalesOrderTable",
                tableDigest);
        AtomicInteger catalogReads = new AtomicInteger();
        NamespaceCatalogView view = FoggyAdapterTestFixtures.trackedView();
        FoggyAnalyticsBundleDependencyStateResolver resolver =
                new FoggyAnalyticsBundleDependencyStateResolver(
                        namespace -> {
                            catalogReads.incrementAndGet();
                            return view;
                        },
                        lookup -> {
                            assertEquals(view.identity(), lookup.catalogIdentity());
                            return switch (lookup.modelKind()) {
                                case "qm" -> Optional.of(MODEL_DIGEST);
                                case "tm" -> Optional.of(tableDigest);
                                default -> Optional.empty();
                            };
                        });

        AnalyticsBundleDependencyState state = resolver.resolve(
                FoggyAdapterTestFixtures.manifest(List.of(
                        queryDependency,
                        tableDependency)));

        assertEquals(AnalyticsBundleDependencyState.CURRENT, state);
        assertEquals(1, catalogReads.get());
    }

    @Test
    void marksMissingOrChangedStableDigestStale() {
        FoggyAnalyticsBundleDependencyStateResolver missing = resolver(
                lookup -> Optional.empty());
        FoggyAnalyticsBundleDependencyStateResolver changed = resolver(
                lookup -> Optional.of(
                        AnalyticsModelDigest.fromSha256Hex("d".repeat(64))));

        assertEquals(
                AnalyticsBundleDependencyState.STALE,
                missing.resolve(FoggyAdapterTestFixtures.manifest(
                        List.of(FoggyAdapterTestFixtures.queryDependency()))));
        assertEquals(
                AnalyticsBundleDependencyState.STALE,
                changed.resolve(FoggyAdapterTestFixtures.manifest(
                        List.of(FoggyAdapterTestFixtures.queryDependency()))));
    }

    @Test
    void failsClosedForUntrackedCatalogOrDigestProviderFailure() {
        NamespaceCatalogView untracked = new NamespaceCatalogView(
                null,
                List.of(MODEL),
                java.util.Map.of(MODEL, "SO"),
                FoggyAdapterTestFixtures.trackedView().queryModels(),
                java.util.Map.of());
        FoggyAnalyticsBundleDependencyStateResolver legacyResolver =
                new FoggyAnalyticsBundleDependencyStateResolver(
                        namespace -> untracked,
                        lookup -> Optional.of(MODEL_DIGEST));
        FoggyAnalyticsBundleDependencyStateResolver failingResolver = resolver(
                lookup -> {
                    throw new IllegalStateException("digest registry unavailable");
                });

        assertEquals(
                AnalyticsBundleDependencyState.STALE,
                legacyResolver.resolve(FoggyAdapterTestFixtures.manifest(
                        List.of(FoggyAdapterTestFixtures.queryDependency()))));
        assertEquals(
                AnalyticsBundleDependencyState.STALE,
                failingResolver.resolve(FoggyAdapterTestFixtures.manifest(
                        List.of(FoggyAdapterTestFixtures.queryDependency()))));
    }

    private static FoggyAnalyticsBundleDependencyStateResolver resolver(
            FoggyStableModelDigestReadPort digestReadPort) {
        SemanticModelCatalogReadPort catalog = namespace ->
                FoggyAdapterTestFixtures.trackedView();
        return new FoggyAnalyticsBundleDependencyStateResolver(catalog, digestReadPort);
    }
}

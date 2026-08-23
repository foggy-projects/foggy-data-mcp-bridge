package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.MODEL;
import static com.foggyframework.analytics.runtime.foggy.FoggyAdapterTestFixtures.MODEL_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FoggyAnalyticsBundleDependencyStateResolverTest {

    @Test
    void marksQmAndTmDependenciesCurrentAgainstOneExactCatalogView() {
        AnalyticsModelRevision tableRevision =
                AnalyticsModelRevision.fromSha256Hex("c".repeat(64));
        AnalyticsModelDependency queryDependency = FoggyAdapterTestFixtures.queryDependency();
        AnalyticsModelDependency tableDependency = FoggyAdapterTestFixtures.dependency(
                "tm",
                "SalesOrderTable",
                tableRevision);
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
                                case "qm" -> Optional.of(MODEL_REVISION);
                                case "tm" -> Optional.of(tableRevision);
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
    void marksMissingOrChangedStableRevisionStale() {
        FoggyAnalyticsBundleDependencyStateResolver missing = resolver(
                lookup -> Optional.empty());
        FoggyAnalyticsBundleDependencyStateResolver changed = resolver(
                lookup -> Optional.of(
                        AnalyticsModelRevision.fromSha256Hex("d".repeat(64))));

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
    void failsClosedForUntrackedCatalogOrRevisionProviderFailure() {
        NamespaceCatalogView untracked = new NamespaceCatalogView(
                null,
                List.of(MODEL),
                java.util.Map.of(MODEL, "SO"),
                FoggyAdapterTestFixtures.trackedView().queryModels(),
                java.util.Map.of());
        FoggyAnalyticsBundleDependencyStateResolver legacyResolver =
                new FoggyAnalyticsBundleDependencyStateResolver(
                        namespace -> untracked,
                        lookup -> Optional.of(MODEL_REVISION));
        FoggyAnalyticsBundleDependencyStateResolver failingResolver = resolver(
                lookup -> {
                    throw new IllegalStateException("revision registry unavailable");
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
            FoggyStableModelRevisionReadPort revisionReadPort) {
        SemanticModelCatalogReadPort catalog = namespace ->
                FoggyAdapterTestFixtures.trackedView();
        return new FoggyAnalyticsBundleDependencyStateResolver(catalog, revisionReadPort);
    }
}

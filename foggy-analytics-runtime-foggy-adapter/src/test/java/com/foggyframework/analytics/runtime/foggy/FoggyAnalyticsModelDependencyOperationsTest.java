package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.runtime.core.function.AnalyticsModelDependencyResolutionException;
import com.foggyframework.dataset.model.semantic.port.SemanticModelCatalogReadPort;
import com.foggyframework.dataset.model.semantic.service.SemanticModelCatalogService.NamespaceCatalogView;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoggyAnalyticsModelDependencyOperationsTest {

    @Test
    void resolvesExactCanonicalQueryModelToStableRevision() {
        FoggyAnalyticsModelDependencyOperations operations = operations(
                FoggyAdapterTestFixtures.trackedView(),
                true);

        var resolved = operations.resolve(
                FoggyAdapterTestFixtures.NAMESPACE,
                "qm",
                FoggyAdapterTestFixtures.MODEL);

        assertEquals(FoggyAdapterTestFixtures.MODEL_REVISION.value(),
                resolved.modelRevision());
        assertEquals(FoggyAdapterTestFixtures.NAMESPACE, resolved.namespace());
    }

    @Test
    void delegatesExactTableModelResolutionToTheStableRevisionPort() {
        FoggyAnalyticsModelDependencyOperations operations = operations(
                FoggyAdapterTestFixtures.trackedView(),
                true);

        var resolved = operations.resolve(
                FoggyAdapterTestFixtures.NAMESPACE,
                "tm",
                "SalesOrderModel");

        assertEquals("tm", resolved.modelKind());
        assertEquals("SalesOrderModel", resolved.modelName());
        assertEquals(FoggyAdapterTestFixtures.MODEL_REVISION.value(),
                resolved.modelRevision());
    }

    @Test
    void rejectsAliasAndMissingCanonicalModel() {
        FoggyAnalyticsModelDependencyOperations operations = operations(
                FoggyAdapterTestFixtures.trackedView(),
                true);

        AnalyticsModelDependencyResolutionException failure = assertThrows(
                AnalyticsModelDependencyResolutionException.class,
                () -> operations.resolve(
                        FoggyAdapterTestFixtures.NAMESPACE,
                        "qm",
                        "SO"));

        assertEquals(
                AnalyticsModelDependencyResolutionException.Code.MODEL_NOT_FOUND,
                failure.code());
    }

    @Test
    void failsClosedWhenStableRevisionIsUnavailable() {
        FoggyAnalyticsModelDependencyOperations operations = operations(
                FoggyAdapterTestFixtures.trackedView(),
                false);

        AnalyticsModelDependencyResolutionException failure = assertThrows(
                AnalyticsModelDependencyResolutionException.class,
                () -> operations.resolve(
                        FoggyAdapterTestFixtures.NAMESPACE,
                        "qm",
                        FoggyAdapterTestFixtures.MODEL));

        assertEquals(
                AnalyticsModelDependencyResolutionException.Code.REVISION_UNAVAILABLE,
                failure.code());
    }

    @Test
    void failsClosedForAMissingTableModelReportedByTheRevisionPort() {
        FoggyAnalyticsModelDependencyOperations operations = operations(
                FoggyAdapterTestFixtures.trackedView(),
                false);

        AnalyticsModelDependencyResolutionException failure = assertThrows(
                AnalyticsModelDependencyResolutionException.class,
                () -> operations.resolve(
                        FoggyAdapterTestFixtures.NAMESPACE,
                        "tm",
                        "MissingTableModel"));

        assertEquals(
                AnalyticsModelDependencyResolutionException.Code.REVISION_UNAVAILABLE,
                failure.code());
    }

    private static FoggyAnalyticsModelDependencyOperations operations(
            NamespaceCatalogView view,
            boolean revisionAvailable) {
        SemanticModelCatalogReadPort catalog = namespace -> view;
        FoggyStableModelRevisionReadPort revisions = lookup -> revisionAvailable
                ? Optional.of(FoggyAdapterTestFixtures.MODEL_REVISION)
                : Optional.empty();
        return new FoggyAnalyticsModelDependencyOperations(catalog, revisions);
    }
}

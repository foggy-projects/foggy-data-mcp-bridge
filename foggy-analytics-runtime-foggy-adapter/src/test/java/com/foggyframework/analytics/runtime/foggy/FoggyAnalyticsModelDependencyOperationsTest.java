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
    void resolvesExactCanonicalQueryModelToInternalDependencyDigest() {
        FoggyAnalyticsModelDependencyOperations operations = operations(
                FoggyAdapterTestFixtures.trackedView(),
                true);

        var resolved = operations.resolve(
                FoggyAdapterTestFixtures.NAMESPACE,
                "qm",
                FoggyAdapterTestFixtures.MODEL);

        assertEquals(FoggyAdapterTestFixtures.MODEL_DIGEST.value(),
                resolved.dependencyDigest());
        assertEquals(FoggyAdapterTestFixtures.NAMESPACE, resolved.namespace());
    }

    @Test
    void listsCurrentCanonicalQueryModelsWithoutReadingDigests() {
        FoggyAnalyticsModelDependencyOperations operations = operations(
                FoggyAdapterTestFixtures.trackedView(),
                false);

        var listed = operations.list(FoggyAdapterTestFixtures.NAMESPACE, "qm");

        assertEquals(FoggyAdapterTestFixtures.NAMESPACE, listed.namespace());
        assertEquals("qm", listed.modelKind());
        assertEquals(1, listed.models().size());
        assertEquals(FoggyAdapterTestFixtures.MODEL, listed.models().get(0).modelName());
        assertEquals("Sales order analysis", listed.models().get(0).description());
    }

    @Test
    void delegatesTableModelDigestResolutionToTheInternalReadPort() {
        FoggyAnalyticsModelDependencyOperations operations = operations(
                FoggyAdapterTestFixtures.trackedView(),
                true);

        var resolved = operations.resolve(
                FoggyAdapterTestFixtures.NAMESPACE,
                "tm",
                "SalesOrderModel");

        assertEquals("tm", resolved.modelKind());
        assertEquals("SalesOrderModel", resolved.modelName());
        assertEquals(FoggyAdapterTestFixtures.MODEL_DIGEST.value(),
                resolved.dependencyDigest());
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
    void failsClosedWhenDependencyDigestIsUnavailable() {
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
                AnalyticsModelDependencyResolutionException.Code.DIGEST_UNAVAILABLE,
                failure.code());
    }

    @Test
    void failsClosedForAMissingTableModelReportedByTheDigestPort() {
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
                AnalyticsModelDependencyResolutionException.Code.DIGEST_UNAVAILABLE,
                failure.code());
    }

    private static FoggyAnalyticsModelDependencyOperations operations(
            NamespaceCatalogView view,
            boolean digestAvailable) {
        SemanticModelCatalogReadPort catalog = namespace -> view;
        FoggyStableModelDigestReadPort digests = lookup -> digestAvailable
                ? Optional.of(FoggyAdapterTestFixtures.MODEL_DIGEST)
                : Optional.empty();
        return new FoggyAnalyticsModelDependencyOperations(catalog, digests);
    }
}

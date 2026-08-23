package com.foggyframework.analytics.runtime.api;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleLifecycle;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsReportDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsSchemaVersion;
import com.foggyframework.analytics.definition.api.AnalyticsVisualIntent;
import com.foggyframework.analytics.definition.api.AnalyticsVisualKind;
import com.foggyframework.analytics.definition.core.AnalyticsBundleIndex;
import com.foggyframework.analytics.definition.core.AnalyticsBundleRegistration;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.function.contract.AnalyticsBundleDescription;
import com.foggyframework.analytics.function.contract.AnalyticsBundleList;
import com.foggyframework.analytics.runtime.core.function.AnalyticsBundleFunctionOperations;
import com.foggyframework.analytics.runtime.core.render.AnalyticsRenderException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsBundleOperationsTest {

    private static final AnalyticsBundleRef SALES = new AnalyticsBundleRef("sales");
    private static final AnalyticsBundleRef BROKEN = new AnalyticsBundleRef("broken");
    private static final AnalyticsBundleRevision REVISION =
            AnalyticsBundleRevision.fromSha256Hex("a".repeat(64));

    @Test
    void listsResolvedAndUnavailableTrustedRegistrationsWithoutPaths() {
        AnalyticsBundleStore store = mock(AnalyticsBundleStore.class);
        when(store.resolve(SALES)).thenReturn(resolved());
        when(store.resolve(BROKEN)).thenThrow(new AnalyticsBundleStoreException(
                AnalyticsBundleStoreException.Code.DIGEST_MISMATCH,
                "private path must not escape"));
        AnalyticsBundleFunctionOperations operations =
                new AnalyticsBundleFunctionOperations(
                registrations(),
                store);

        AnalyticsBundleList response = operations.list();

        assertEquals(2, response.bundles().size());
        AnalyticsBundleDescription valid = response.bundles().get(0);
        assertEquals("sales", valid.bundleRef());
        assertEquals(REVISION.value(), valid.bundleRevision());
        assertTrue(valid.valid());
        assertFalse(valid.writable());
        AnalyticsBundleDescription invalid = response.bundles().get(1);
        assertEquals("broken", invalid.bundleRef());
        assertFalse(invalid.valid());
        assertEquals("ANALYTICS_BUNDLE_DIGEST_MISMATCH", invalid.errorCode());
    }

    @Test
    void validatesAnOptionalExactRevisionAndRejectsConflict() {
        AnalyticsBundleStore store = mock(AnalyticsBundleStore.class);
        when(store.resolve(SALES)).thenReturn(resolved());
        AnalyticsBundleFunctionOperations operations =
                new AnalyticsBundleFunctionOperations(
                registrations().subList(0, 1),
                store);

        assertEquals(
                REVISION.value(),
                operations.validate(SALES.value(), REVISION.value()).bundleRevision());
        AnalyticsBundleStoreException conflict = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> operations.validate(
                        SALES.value(),
                        AnalyticsBundleRevision.fromSha256Hex("b".repeat(64)).value()));
        assertEquals(
                AnalyticsBundleStoreException.Code.REVISION_CONFLICT,
                conflict.code());
    }

    @Test
    void rejectsWhitespaceRevisionInsteadOfTreatingItAsAbsent() {
        AnalyticsBundleStore store = mock(AnalyticsBundleStore.class);
        when(store.resolve(SALES)).thenReturn(resolved());
        AnalyticsBundleFunctionOperations operations =
                new AnalyticsBundleFunctionOperations(
                registrations().subList(0, 1),
                store);

        assertThrows(
                IllegalArgumentException.class,
                () -> operations.validate(SALES.value(), "   "));
    }

    @Test
    void describesOnlyAnExistingParsedArtifactAtTheExactRevision() {
        AnalyticsBundleStore store = mock(AnalyticsBundleStore.class);
        when(store.resolve(SALES)).thenReturn(resolved());
        AnalyticsArtifactRef reportRef = new AnalyticsArtifactRef(
                AnalyticsArtifactKind.REPORT, "sales-summary");
        AnalyticsBundleIndex index = new AnalyticsBundleIndex(
                resolved(),
                Map.of(),
                Map.of(reportRef, new AnalyticsReportDefinition(
                        reportRef,
                        new AnalyticsQueryRef("sales-query"),
                        new AnalyticsVisualIntent(AnalyticsVisualKind.TABLE, Map.of()))),
                Map.of());
        AnalyticsBundleFunctionOperations operations =
                new AnalyticsBundleFunctionOperations(
                        registrations().subList(0, 1),
                        store,
                        (bundleRef, revision) -> index);

        var description = operations.describeArtifact(
                "sales", "report", "sales-summary", REVISION.value());

        assertEquals("sales", description.bundleRef());
        assertEquals(REVISION.value(), description.bundleRevision());
        assertEquals("report", description.artifactKind());
        assertThrows(
                AnalyticsRenderException.class,
                () -> operations.describeArtifact(
                        "sales", "dashboard", "sales-summary", REVISION.value()));
    }

    private static List<AnalyticsBundleRegistration> registrations() {
        return List.of(
                new AnalyticsBundleRegistration(
                        SALES,
                        Path.of("target/analytics-api-test/sales"),
                        AnalyticsBundleSourceState.CONFIGURED),
                new AnalyticsBundleRegistration(
                        BROKEN,
                        Path.of("target/analytics-api-test/broken"),
                        AnalyticsBundleSourceState.PUBLISHED));
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
}

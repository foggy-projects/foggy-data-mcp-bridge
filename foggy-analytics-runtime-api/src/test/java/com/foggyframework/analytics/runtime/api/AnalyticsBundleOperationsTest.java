package com.foggyframework.analytics.runtime.api;

import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleLifecycle;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsSchemaVersion;
import com.foggyframework.analytics.definition.core.AnalyticsBundleRegistration;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStore;
import com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException;
import com.foggyframework.analytics.definition.core.ResolvedAnalyticsBundle;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleListResponse;
import com.foggyframework.analytics.runtime.api.dto.AnalyticsBundleSummary;
import com.foggyframework.analytics.runtime.api.service.AnalyticsBundleOperations;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

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
        AnalyticsBundleOperations operations = new AnalyticsBundleOperations(
                registrations(),
                store);

        AnalyticsBundleListResponse response = operations.list();

        assertEquals(2, response.bundles().size());
        AnalyticsBundleSummary valid = response.bundles().get(0);
        assertEquals("sales", valid.bundleRef());
        assertEquals(REVISION.value(), valid.bundleRevision());
        assertTrue(valid.valid());
        assertFalse(valid.writable());
        AnalyticsBundleSummary invalid = response.bundles().get(1);
        assertEquals("broken", invalid.bundleRef());
        assertFalse(invalid.valid());
        assertEquals("DIGEST_MISMATCH", invalid.errorCode());
    }

    @Test
    void validatesAnOptionalExactRevisionAndRejectsConflict() {
        AnalyticsBundleStore store = mock(AnalyticsBundleStore.class);
        when(store.resolve(SALES)).thenReturn(resolved());
        AnalyticsBundleOperations operations = new AnalyticsBundleOperations(
                registrations().subList(0, 1),
                store);

        assertEquals(
                REVISION.value(),
                operations.validate(SALES, REVISION.value()).bundleRevision());
        AnalyticsBundleStoreException conflict = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> operations.validate(
                        SALES,
                        AnalyticsBundleRevision.fromSha256Hex("b".repeat(64)).value()));
        assertEquals(
                AnalyticsBundleStoreException.Code.REVISION_CONFLICT,
                conflict.code());
    }

    @Test
    void rejectsWhitespaceRevisionInsteadOfTreatingItAsAbsent() {
        AnalyticsBundleStore store = mock(AnalyticsBundleStore.class);
        when(store.resolve(SALES)).thenReturn(resolved());
        AnalyticsBundleOperations operations = new AnalyticsBundleOperations(
                registrations().subList(0, 1),
                store);

        assertThrows(
                IllegalArgumentException.class,
                () -> operations.validate(SALES, "   "));
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

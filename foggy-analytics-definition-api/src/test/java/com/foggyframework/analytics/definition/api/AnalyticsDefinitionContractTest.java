package com.foggyframework.analytics.definition.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsDefinitionContractTest {

    private static final String SHA256_HEX = "A".repeat(64);

    @Test
    void manifestUsesStableTechnicalIdentityAndImmutableDependencies() {
        AnalyticsBundleRef bundleRef = new AnalyticsBundleRef("sales");
        AnalyticsBundleRevision sourceRevision =
                AnalyticsBundleRevision.fromSha256Hex(SHA256_HEX);
        AnalyticsModelDependency dependency = new AnalyticsModelDependency(
                new AnalyticsNamespaceRef("tenant-default"),
                "qm",
                "sales-order",
                new AnalyticsBundleRef("models-sales"),
                sourceRevision,
                "catalog:default/sales-order");

        AnalyticsBundleManifest manifest = new AnalyticsBundleManifest(
                AnalyticsBundleManifest.ANALYTICS_KIND,
                new AnalyticsSchemaVersion("1.0"),
                bundleRef,
                sourceRevision,
                new AnalyticsNamespaceRef("tenant-default"),
                List.of(dependency));

        assertEquals("analytics", manifest.kind());
        assertEquals("sha256:" + "a".repeat(64), manifest.bundleRevision().value());
        assertEquals("qm", manifest.modelDependencies().get(0).modelKind());
        assertThrows(UnsupportedOperationException.class,
                () -> manifest.modelDependencies().add(dependency));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsBundleManifest(
                AnalyticsBundleManifest.ANALYTICS_KIND,
                new AnalyticsSchemaVersion("1.0"),
                bundleRef,
                sourceRevision,
                new AnalyticsNamespaceRef("tenant-default"),
                List.of(dependency, dependency)));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsModelDependency(
                new AnalyticsNamespaceRef("tenant-default"),
                "fsscript",
                "unsafe-script",
                new AnalyticsBundleRef("scripts"),
                sourceRevision,
                "catalog:unsafe-script"));
    }

    @Test
    void revisionAndSchemaVersionFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsBundleRevision("sha256:abc"));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsBundleRevision("sha256:" + "g".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsSchemaVersion("v1"));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsBundleManifest(
                        "report",
                        new AnalyticsSchemaVersion("1"),
                        new AnalyticsBundleRef("sales"),
                        AnalyticsBundleRevision.fromSha256Hex(SHA256_HEX),
                        new AnalyticsNamespaceRef("default"),
                        List.of()));
    }

    @Test
    void lifecycleSeparatesStorageImmutabilityFromDependencyStaleness() {
        AnalyticsBundleLifecycle configured = new AnalyticsBundleLifecycle(
                AnalyticsBundleSourceState.CONFIGURED,
                AnalyticsBundleDependencyState.CURRENT);
        AnalyticsBundleLifecycle ownedStale = new AnalyticsBundleLifecycle(
                AnalyticsBundleSourceState.RUNTIME_OWNED,
                AnalyticsBundleDependencyState.STALE);
        AnalyticsBundleLifecycle published = new AnalyticsBundleLifecycle(
                AnalyticsBundleSourceState.PUBLISHED,
                AnalyticsBundleDependencyState.CURRENT);

        assertFalse(configured.isWritable());
        assertTrue(ownedStale.isWritable());
        assertTrue(ownedStale.isStale());
        assertTrue(published.isImmutable());
        assertFalse(published.isWritable());
    }
}

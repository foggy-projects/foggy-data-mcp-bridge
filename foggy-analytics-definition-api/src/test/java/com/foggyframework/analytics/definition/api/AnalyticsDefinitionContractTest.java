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
        AnalyticsBundleRevision bundleRevision =
                AnalyticsBundleRevision.fromSha256Hex(SHA256_HEX);
        AnalyticsModelDigest modelDigest =
                AnalyticsModelDigest.fromSha256Hex(SHA256_HEX);
        AnalyticsModelDependency dependency = new AnalyticsModelDependency(
                new AnalyticsNamespaceRef("tenant-default"),
                "qm",
                "sales-order",
                modelDigest);

        AnalyticsBundleManifest manifest = new AnalyticsBundleManifest(
                AnalyticsBundleManifest.ANALYTICS_KIND,
                new AnalyticsSchemaVersion("1.0"),
                bundleRef,
                bundleRevision,
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
                bundleRevision,
                new AnalyticsNamespaceRef("tenant-default"),
                List.of(dependency, dependency)));
        assertThrows(IllegalArgumentException.class, () -> new AnalyticsModelDependency(
                new AnalyticsNamespaceRef("tenant-default"),
                "fsscript",
                "unsafe-script",
                modelDigest));
    }

    @Test
    void digestRevisionAndSchemaVersionFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsBundleRevision("sha256:abc"));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsBundleRevision("sha256:" + "g".repeat(64)));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsModelDigest("catalog:boot-identity"));
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
    void logicalReferencesAreSingleUrlSafeSegments() {
        assertEquals("sales.v1_~", new AnalyticsBundleRef("sales.v1_~").value());
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsBundleRef("sales/eu"));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsArtifactRef(AnalyticsArtifactKind.REPORT, ".."));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsQueryRef("销售查询"));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalyticsQueryRef("a".repeat(129)));
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

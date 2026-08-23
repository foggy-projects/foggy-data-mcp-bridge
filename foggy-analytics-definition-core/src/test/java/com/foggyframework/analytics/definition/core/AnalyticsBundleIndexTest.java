package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsArtifactKind;
import com.foggyframework.analytics.definition.api.AnalyticsArtifactRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleLifecycle;
import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsDashboardWidget;
import com.foggyframework.analytics.definition.api.AnalyticsModelDependency;
import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;
import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;
import com.foggyframework.analytics.definition.api.AnalyticsQueryRef;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.definition.api.AnalyticsReportDefinition;
import com.foggyframework.analytics.definition.api.AnalyticsSchemaVersion;
import com.foggyframework.analytics.definition.api.AnalyticsVisualIntent;
import com.foggyframework.analytics.definition.api.AnalyticsVisualKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.REVISION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalyticsBundleIndexTest {

    private static final AnalyticsBundleRef SALES = new AnalyticsBundleRef("sales");

    @TempDir
    Path tempDir;

    @Test
    void storeBackedResolverReturnsTypedExactRevisionIndex() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("typed-index"));
        Path dashboardPath = bundle.resolve("dashboards/sales.dashboard.json");
        Files.createDirectories(dashboardPath.getParent());
        Files.writeString(dashboardPath, """
                {
                  "artifactRef": "sales-dashboard",
                  "widgets": [{
                    "widgetRef": "sales-widget",
                    "reportRef": "sales-report",
                    "visualIntent": {"kind": "CHART", "hints": {"shape": "bar"}}
                  }]
                }
                """, StandardCharsets.UTF_8);
        refreshManifestRevision(bundle);
        FileSystemAnalyticsBundleStore store = new FileSystemAnalyticsBundleStore(
                List.of(new AnalyticsBundleRegistration(
                        SALES,
                        bundle,
                        AnalyticsBundleSourceState.CONFIGURED)),
                manifest -> AnalyticsBundleDependencyState.CURRENT);
        ResolvedAnalyticsBundle resolved = store.resolve(SALES);
        StoreBackedAnalyticsDefinitionResolver resolver =
                new StoreBackedAnalyticsDefinitionResolver(store);

        AnalyticsBundleIndex index = resolver.resolve(SALES, resolved.bundleRevision());

        assertEquals("SalesOrder", index.query(
                new AnalyticsQueryRef("sales-by-region")).orElseThrow().modelName());
        assertEquals(AnalyticsVisualKind.TABLE, index.report(new AnalyticsArtifactRef(
                AnalyticsArtifactKind.REPORT,
                "sales-report")).orElseThrow().visualIntent().kind());
        assertEquals(1, index.dashboard(new AnalyticsArtifactRef(
                AnalyticsArtifactKind.DASHBOARD,
                "sales-dashboard")).orElseThrow().widgets().size());
        assertEquals(resolved.bundleRevision(), index.bundleRevision());
        assertThrows(UnsupportedOperationException.class, () -> index.queries().clear());

        AnalyticsDefinitionSnapshot snapshot = store.readDefinitionSnapshot(
                SALES,
                resolved.bundleRevision());
        byte[] firstRead = snapshot.readDefinition("queries/sales.query.json");
        byte[] expected = firstRead.clone();
        firstRead[0] = (byte) (firstRead[0] + 1);
        assertArrayEquals(expected, snapshot.readDefinition("queries/sales.query.json"));
        assertEquals(3, snapshot.definitionPaths().size());
        AnalyticsBundleStoreException conflict = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> resolver.resolve(
                        SALES,
                        AnalyticsBundleRevision.fromSha256Hex("0".repeat(64))));
        assertEquals(REVISION_CONFLICT, conflict.code());
    }

    @Test
    void typedDiffIsDeterministicAcrossDefinitionsAndModelRevision() {
        AnalyticsQueryRef queryRef = new AnalyticsQueryRef("sales-query");
        AnalyticsArtifactRef reportRef = new AnalyticsArtifactRef(
                AnalyticsArtifactKind.REPORT,
                "sales-report");
        AnalyticsArtifactRef dashboardRef = new AnalyticsArtifactRef(
                AnalyticsArtifactKind.DASHBOARD,
                "sales-dashboard");
        AnalyticsQuerySpec beforeQuery = query(queryRef, List.of("amount"));
        AnalyticsQuerySpec afterQuery = query(queryRef, List.of("amount", "tax"));
        AnalyticsReportDefinition report = new AnalyticsReportDefinition(
                reportRef,
                queryRef,
                visual(AnalyticsVisualKind.TABLE));
        AnalyticsDashboardDefinition dashboard = new AnalyticsDashboardDefinition(
                dashboardRef,
                List.of(new AnalyticsDashboardWidget(
                        "sales-widget",
                        null,
                        queryRef,
                        visual(AnalyticsVisualKind.CHART))));
        AnalyticsBundleIndex before = index(
                "a",
                "1",
                Map.of(queryRef, beforeQuery),
                Map.of(reportRef, report),
                Map.of());
        AnalyticsBundleIndex after = index(
                "b",
                "2",
                Map.of(queryRef, afterQuery),
                Map.of(),
                Map.of(dashboardRef, dashboard));

        AnalyticsBundleDiff diff = new AnalyticsBundleDiffer().diff(before, after);

        assertEquals(List.of(
                new AnalyticsDefinitionChange(
                        AnalyticsDefinitionType.MODEL_DEPENDENCY,
                        "default/qm/SalesOrder",
                        AnalyticsDefinitionChangeType.MODIFIED),
                new AnalyticsDefinitionChange(
                        AnalyticsDefinitionType.QUERY,
                        "sales-query",
                        AnalyticsDefinitionChangeType.MODIFIED),
                new AnalyticsDefinitionChange(
                        AnalyticsDefinitionType.REPORT,
                        "sales-report",
                        AnalyticsDefinitionChangeType.REMOVED),
                new AnalyticsDefinitionChange(
                        AnalyticsDefinitionType.DASHBOARD,
                        "sales-dashboard",
                        AnalyticsDefinitionChangeType.ADDED)), diff.changes());
        assertThrows(UnsupportedOperationException.class, () -> diff.changes().clear());
    }

    private static AnalyticsBundleIndex index(
            String bundleHex,
            String modelHex,
            Map<AnalyticsQueryRef, AnalyticsQuerySpec> queries,
            Map<AnalyticsArtifactRef, AnalyticsReportDefinition> reports,
            Map<AnalyticsArtifactRef, AnalyticsDashboardDefinition> dashboards) {
        AnalyticsBundleManifest manifest = new AnalyticsBundleManifest(
                AnalyticsBundleManifest.ANALYTICS_KIND,
                AnalyticsSchemaVersion.V1,
                SALES,
                AnalyticsBundleRevision.fromSha256Hex(bundleHex.repeat(64)),
                new AnalyticsNamespaceRef("default"),
                List.of(new AnalyticsModelDependency(
                        new AnalyticsNamespaceRef("default"),
                        "qm",
                        "SalesOrder",
                        AnalyticsModelRevision.fromSha256Hex(modelHex.repeat(64)))));
        return new AnalyticsBundleIndex(
                new ResolvedAnalyticsBundle(
                        manifest,
                        new AnalyticsBundleLifecycle(
                                AnalyticsBundleSourceState.CONFIGURED,
                                AnalyticsBundleDependencyState.CURRENT)),
                queries,
                reports,
                dashboards);
    }

    private static AnalyticsQuerySpec query(
            AnalyticsQueryRef queryRef,
            List<String> columns) {
        return new AnalyticsQuerySpec(
                queryRef,
                new AnalyticsNamespaceRef("default"),
                "SalesOrder",
                columns,
                List.of());
    }

    private static AnalyticsVisualIntent visual(AnalyticsVisualKind kind) {
        return new AnalyticsVisualIntent(kind, Map.of());
    }

    private Path copyFixture(Path target) throws Exception {
        Path source = fixtureRoot();
        try (var paths = Files.walk(source)) {
            for (Path path : paths.sorted(Comparator.naturalOrder()).toList()) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.write(destination, Files.readAllBytes(path));
                }
            }
        }
        return target;
    }

    private void refreshManifestRevision(Path bundle) throws Exception {
        AnalyticsBundleManifestJsonCodec codec = new AnalyticsBundleManifestJsonCodec();
        AnalyticsBundleRevision revision = new AnalyticsBundleRevisionCalculator(codec)
                .calculate(bundle);
        Path manifest = bundle.resolve("manifest.json");
        Files.write(
                manifest,
                codec.withBundleRevision(Files.readAllBytes(manifest), revision));
    }

    private Path fixtureRoot() throws URISyntaxException {
        return Path.of(AnalyticsBundleIndexTest.class
                .getResource("/fixtures/analytics-bundle/basic/manifest.json")
                .toURI())
                .getParent();
    }
}

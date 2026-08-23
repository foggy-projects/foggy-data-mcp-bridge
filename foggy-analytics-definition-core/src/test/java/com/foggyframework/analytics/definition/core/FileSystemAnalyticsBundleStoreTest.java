package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleDependencyState;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import com.foggyframework.analytics.definition.api.AnalyticsBundleSourceState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.DEPENDENCY_STALE;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.DIGEST_MISMATCH;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.IMMUTABLE_BUNDLE;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.INVALID_BUNDLE;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.REVISION_CONFLICT;
import static com.foggyframework.analytics.definition.core.AnalyticsBundleStoreException.Code.UNSAFE_PATH;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileSystemAnalyticsBundleStoreTest {

    private static final AnalyticsBundleRef SALES = new AnalyticsBundleRef("sales");
    private static final String REPORT_PATH = "reports/sales.report.json";

    private final AnalyticsBundleManifestJsonCodec manifestCodec =
            new AnalyticsBundleManifestJsonCodec();
    private final AnalyticsBundleRevisionCalculator revisionCalculator =
            new AnalyticsBundleRevisionCalculator(manifestCodec);

    @TempDir
    Path tempDir;

    @Test
    void resolvesValidatedBundleWithoutExposingItsFilesystemRoot() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("configured"));
        FileSystemAnalyticsBundleStore store = store(
                bundle,
                AnalyticsBundleSourceState.CONFIGURED);

        ResolvedAnalyticsBundle resolved = store.resolve(SALES);

        assertEquals(SALES, resolved.manifest().bundleRef());
        assertEquals(revisionCalculator.calculate(bundle), resolved.bundleRevision());
        assertEquals(AnalyticsBundleSourceState.CONFIGURED, resolved.lifecycle().sourceState());
    }

    @Test
    void unresolvedModelDependenciesFailClosedByDefault() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("default-stale"));
        FileSystemAnalyticsBundleStore store = new FileSystemAnalyticsBundleStore(List.of(
                registration(bundle, AnalyticsBundleSourceState.PUBLISHED)));

        AnalyticsBundleStoreException failure = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store.resolve(SALES));

        assertEquals(DEPENDENCY_STALE, failure.code());
    }

    @Test
    void configuredAndPublishedBundlesRejectWrites() throws Exception {
        Path configured = copyFixture(tempDir.resolve("configured"));
        Path published = copyFixture(tempDir.resolve("published"));
        byte[] replacement = changedReport(configured);

        AnalyticsBundleStoreException configuredFailure = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store(configured, AnalyticsBundleSourceState.CONFIGURED).saveArtifact(
                        SALES,
                        revisionCalculator.calculate(configured),
                        REPORT_PATH,
                        replacement));
        AnalyticsBundleStoreException publishedFailure = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store(published, AnalyticsBundleSourceState.PUBLISHED).saveArtifact(
                        SALES,
                        revisionCalculator.calculate(published),
                        REPORT_PATH,
                        replacement));

        assertEquals(IMMUTABLE_BUNDLE, configuredFailure.code());
        assertEquals(IMMUTABLE_BUNDLE, publishedFailure.code());
    }

    @Test
    void traversalAndSymlinkPathsFailClosed() throws Exception {
        Path traversalBundle = copyFixture(tempDir.resolve("traversal"));
        FileSystemAnalyticsBundleStore traversalStore = store(
                traversalBundle,
                AnalyticsBundleSourceState.RUNTIME_OWNED);
        AnalyticsBundleRevision revision = revisionCalculator.calculate(traversalBundle);

        AnalyticsBundleStoreException traversal = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> traversalStore.saveArtifact(
                        SALES,
                        revision,
                        "../outside.report.json",
                        changedReport(traversalBundle)));
        AnalyticsBundleStoreException normalizedTraversal = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> traversalStore.saveArtifact(
                        SALES,
                        revision,
                        "reports/../reports/sales.report.json",
                        changedReport(traversalBundle)));

        Path symlinkBundle = copyFixture(tempDir.resolve("symlink"));
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "outside", StandardCharsets.UTF_8);
        Files.createSymbolicLink(symlinkBundle.resolve("assets/link.txt"), outside);
        AnalyticsBundleStoreException symlink = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store(symlinkBundle, AnalyticsBundleSourceState.CONFIGURED).resolve(SALES));

        assertEquals(UNSAFE_PATH, traversal.code());
        assertEquals(UNSAFE_PATH, normalizedTraversal.code());
        assertEquals(UNSAFE_PATH, symlink.code());
        assertFalse(Files.exists(tempDir.resolve("outside.report.json")));
    }

    @Test
    void canonicalDigestMismatchIsRejected() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("digest-mismatch"));
        Files.write(bundle.resolve(REPORT_PATH), changedReport(bundle));

        AnalyticsBundleStoreException failure = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store(bundle, AnalyticsBundleSourceState.CONFIGURED).resolve(SALES));

        assertEquals(DIGEST_MISMATCH, failure.code());
    }

    @Test
    void runtimeOwnedSaveAtomicallyAdvancesRevisionAndGuardsReads() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("runtime-owned"));
        FileSystemAnalyticsBundleStore store = store(
                bundle,
                AnalyticsBundleSourceState.RUNTIME_OWNED);
        AnalyticsBundleRevision previous = store.resolve(SALES).bundleRevision();
        byte[] replacement = changedReport(bundle);

        ResolvedAnalyticsBundle saved = store.saveArtifact(
                SALES,
                previous,
                REPORT_PATH,
                replacement);

        assertNotEquals(previous, saved.bundleRevision());
        assertEquals(saved.bundleRevision(), manifestCodec.read(
                Files.readAllBytes(bundle.resolve("manifest.json"))).bundleRevision());
        assertArrayEquals(
                replacement,
                store.readArtifact(SALES, saved.bundleRevision(), REPORT_PATH));
        AnalyticsBundleStoreException conflict = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store.readArtifact(SALES, previous, REPORT_PATH));
        assertEquals(REVISION_CONFLICT, conflict.code());
        assertFalse(Files.exists(tempDir.resolve(
                ".runtime-owned.analytics-write-journal")));
    }

    @Test
    void restartRecoversInterruptedResourceAndManifestCommit() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("recoverable"));
        byte[] original = Files.readAllBytes(bundle.resolve(REPORT_PATH));
        AnalyticsBundleRevision originalRevision = revisionCalculator.calculate(bundle);
        DefaultAnalyticsAtomicFileWriter delegate = new DefaultAnalyticsAtomicFileWriter();
        AnalyticsAtomicFileWriter crashBeforeManifest = new AnalyticsAtomicFileWriter() {
            private boolean crash = true;

            @Override
            public void write(Path target, byte[] content) throws java.io.IOException {
                if (crash && "manifest.json".equals(target.getFileName().toString())) {
                    crash = false;
                    throw new SimulatedProcessCrash();
                }
                delegate.write(target, content);
            }
        };
        FileSystemAnalyticsBundleStore interrupted = new FileSystemAnalyticsBundleStore(
                List.of(registration(bundle, AnalyticsBundleSourceState.RUNTIME_OWNED)),
                manifest -> AnalyticsBundleDependencyState.CURRENT,
                manifestCodec,
                new AnalyticsBundleStructureValidator(),
                new AnalyticsArtifactPathPolicy(),
                crashBeforeManifest);

        assertThrows(SimulatedProcessCrash.class, () -> interrupted.saveArtifact(
                SALES,
                originalRevision,
                REPORT_PATH,
                changedReport(bundle)));

        FileSystemAnalyticsBundleStore restarted = store(
                bundle,
                AnalyticsBundleSourceState.RUNTIME_OWNED);
        ResolvedAnalyticsBundle recovered = restarted.resolve(SALES);

        assertEquals(originalRevision, recovered.bundleRevision());
        assertArrayEquals(original, Files.readAllBytes(bundle.resolve(REPORT_PATH)));
        assertFalse(Files.exists(tempDir.resolve(".recoverable.analytics-write-journal")));
    }

    @Test
    void missingCrossReferenceIsRejectedEvenWhenDigestMatches() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("invalid-reference"));
        Path report = bundle.resolve(REPORT_PATH);
        Files.writeString(
                report,
                Files.readString(report, StandardCharsets.UTF_8)
                        .replace("sales-by-region", "missing-query"),
                StandardCharsets.UTF_8);
        refreshManifestRevision(bundle);

        AnalyticsBundleStoreException failure = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store(bundle, AnalyticsBundleSourceState.CONFIGURED).resolve(SALES));

        assertEquals(INVALID_BUNDLE, failure.code());
    }

    @Test
    void dashboardReferencesAndForbiddenQueryFieldsAreValidated() throws Exception {
        Path dashboardBundle = copyFixture(tempDir.resolve("dashboard-reference"));
        Path dashboard = dashboardBundle.resolve("dashboards/sales.dashboard.json");
        Files.createDirectories(dashboard.getParent());
        Files.writeString(dashboard, """
                {
                  "artifactRef": "sales-dashboard",
                  "widgets": [
                    {
                      "widgetRef": "sales-widget",
                      "reportRef": "missing-report",
                      "visualIntent": {"kind": "TABLE"}
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        refreshManifestRevision(dashboardBundle);

        AnalyticsBundleStoreException missingReport = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store(
                        dashboardBundle,
                        AnalyticsBundleSourceState.CONFIGURED).resolve(SALES));

        Path rawSqlBundle = copyFixture(tempDir.resolve("raw-sql"));
        Path query = rawSqlBundle.resolve("queries/sales.query.json");
        Files.writeString(
                query,
                Files.readString(query, StandardCharsets.UTF_8)
                        .replace("\"groupBy\":", "\"rawSql\": \"select * from secret\",\n  \"groupBy\":"),
                StandardCharsets.UTF_8);
        refreshManifestRevision(rawSqlBundle);
        AnalyticsBundleStoreException rawSql = assertThrows(
                AnalyticsBundleStoreException.class,
                () -> store(rawSqlBundle, AnalyticsBundleSourceState.CONFIGURED).resolve(SALES));

        assertEquals(INVALID_BUNDLE, missingReport.code());
        assertEquals(INVALID_BUNDLE, rawSql.code());
    }

    private FileSystemAnalyticsBundleStore store(
            Path root,
            AnalyticsBundleSourceState sourceState) {
        return new FileSystemAnalyticsBundleStore(
                List.of(registration(root, sourceState)),
                manifest -> AnalyticsBundleDependencyState.CURRENT);
    }

    private AnalyticsBundleRegistration registration(
            Path root,
            AnalyticsBundleSourceState sourceState) {
        return new AnalyticsBundleRegistration(SALES, root, sourceState);
    }

    private byte[] changedReport(Path bundle) throws Exception {
        return Files.readString(bundle.resolve(REPORT_PATH), StandardCharsets.UTF_8)
                .replace("TABLE", "CHART")
                .getBytes(StandardCharsets.UTF_8);
    }

    private void refreshManifestRevision(Path bundle) throws Exception {
        Path manifest = bundle.resolve("manifest.json");
        AnalyticsBundleRevision revision = revisionCalculator.calculate(bundle);
        Files.write(
                manifest,
                manifestCodec.withBundleRevision(Files.readAllBytes(manifest), revision));
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

    private Path fixtureRoot() throws URISyntaxException {
        return Path.of(FileSystemAnalyticsBundleStoreTest.class
                .getResource("/fixtures/analytics-bundle/basic/manifest.json")
                .toURI())
                .getParent();
    }

    private static final class SimulatedProcessCrash extends Error {
    }
}

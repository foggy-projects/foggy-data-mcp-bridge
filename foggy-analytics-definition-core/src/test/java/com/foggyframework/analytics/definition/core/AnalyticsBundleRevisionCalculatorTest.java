package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalyticsBundleRevisionCalculatorTest {

    private final AnalyticsBundleManifestJsonCodec codec =
            new AnalyticsBundleManifestJsonCodec();
    private final AnalyticsBundleRevisionCalculator calculator =
            new AnalyticsBundleRevisionCalculator(codec);

    @TempDir
    Path tempDir;

    @Test
    void basicFixtureHasStableGoldenRevision() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("golden"));

        AnalyticsBundleRevision revision = calculator.calculate(bundle);

        assertEquals(
                "sha256:f92c55147438a95b865f2e78c9ee3523c9813a0157761cc466ef527f5b62b919",
                revision.value());
    }

    @Test
    void dynamicManifestFieldsAndJsonLineEndingsDoNotChangeRevision() throws Exception {
        Path first = copyFixture(tempDir.resolve("first"));
        Path second = copyFixture(tempDir.resolve("second"));
        Path manifest = second.resolve("manifest.json");
        String changedManifest = Files.readString(manifest, StandardCharsets.UTF_8)
                .replace("sha256:f92c55147438a95b865f2e78c9ee3523c9813a0157761cc466ef527f5b62b919",
                        "sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff")
                .replace("2026-08-23T00:00:00Z", "2026-08-23T01:02:03Z")
                .replace("\"algorithm\": \"none\"", "\"algorithm\": \"future\"");
        Files.writeString(manifest, changedManifest, StandardCharsets.UTF_8);
        Path query = second.resolve("queries/sales.query.json");
        Files.writeString(query,
                Files.readString(query, StandardCharsets.UTF_8).replace("\n", "\r\n"),
                StandardCharsets.UTF_8);

        assertEquals(calculator.calculate(first), calculator.calculate(second));
    }

    @Test
    void governedContentChangeProducesNewRevision() throws Exception {
        Path first = copyFixture(tempDir.resolve("first"));
        Path second = copyFixture(tempDir.resolve("second"));
        Path report = second.resolve("reports/sales.report.json");
        Files.writeString(report,
                Files.readString(report, StandardCharsets.UTF_8)
                        .replace("TABLE", "CHART"),
                StandardCharsets.UTF_8);

        assertNotEquals(calculator.calculate(first), calculator.calculate(second));
    }

    @Test
    void manifestCodecRejectsUnknownContractFields() {
        byte[] invalid = ("""
                {
                  "kind":"analytics",
                  "schemaVersion":"1",
                  "bundleRef":"sales",
                  "bundleRevision":"sha256:%s",
                  "namespaceRef":"default",
                  "modelDependencies":[],
                  "owner":"must-stay-in-product"
                }
                """).formatted("0".repeat(64)).getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> codec.read(invalid));
    }

    @Test
    void manifestCodecRejectsUnsupportedNumericSchemaVersion() {
        byte[] invalid = ("""
                {
                  "kind":"analytics",
                  "schemaVersion":"2.0",
                  "bundleRef":"sales",
                  "bundleRevision":"sha256:%s",
                  "namespaceRef":"default",
                  "modelDependencies":[]
                }
                """).formatted("0".repeat(64)).getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> codec.read(invalid));
    }

    @Test
    void manifestCodecRejectsRuntimeCatalogIdentity() {
        byte[] invalid = ("""
                {
                  "kind":"analytics",
                  "schemaVersion":"1.0",
                  "bundleRef":"sales",
                  "bundleRevision":"sha256:%s",
                  "namespaceRef":"default",
                  "modelDependencies":[{
                    "namespace":"default",
                    "modelKind":"qm",
                    "modelName":"SalesOrder",
                    "modelRevision":"sha256:%s",
                    "catalogIdentity":"runtime-only"
                  }]
                }
                """).formatted("0".repeat(64), "1".repeat(64))
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> codec.read(invalid));
    }

    @Test
    void manifestCodecReadsCurrentDigestAndLegacyRevisionWithoutMigration() {
        String template = """
                {
                  "kind":"analytics",
                  "schemaVersion":"1.0",
                  "bundleRef":"sales",
                  "bundleRevision":"sha256:%s",
                  "namespaceRef":"default",
                  "modelDependencies":[{
                    "namespace":"default",
                    "modelKind":"qm",
                    "modelName":"SalesOrder",
                    "%s":"sha256:%s"
                  }]
                }
                """;
        byte[] current = template.formatted(
                        "0".repeat(64), "modelDigest", "1".repeat(64))
                .getBytes(StandardCharsets.UTF_8);
        byte[] legacy = template.formatted(
                        "0".repeat(64), "modelRevision", "1".repeat(64))
                .getBytes(StandardCharsets.UTF_8);

        assertEquals(
                codec.read(current).modelDependencies().get(0).modelDigest(),
                codec.read(legacy).modelDependencies().get(0).modelDigest());
    }

    @Test
    void manifestCodecRejectsAmbiguousDigestFields() {
        byte[] invalid = ("""
                {
                  "kind":"analytics",
                  "schemaVersion":"1.0",
                  "bundleRef":"sales",
                  "bundleRevision":"sha256:%s",
                  "namespaceRef":"default",
                  "modelDependencies":[{
                    "namespace":"default",
                    "modelKind":"qm",
                    "modelName":"SalesOrder",
                    "modelDigest":"sha256:%s",
                    "modelRevision":"sha256:%s"
                  }]
                }
                """).formatted("0".repeat(64), "1".repeat(64), "1".repeat(64))
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class, () -> codec.read(invalid));
    }

    @Test
    void symlinkInsideBundleFailsClosed() throws Exception {
        Path bundle = copyFixture(tempDir.resolve("bundle"));
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "outside", StandardCharsets.UTF_8);
        createSymbolicLinkOrSkip(bundle.resolve("assets/link.txt"), outside);

        assertThrows(AnalyticsBundleRevisionCalculator.UnsafeBundlePathException.class,
                () -> calculator.calculate(bundle));
    }

    private void createSymbolicLinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (Exception failure) {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    false, "Symbolic links are unavailable: " + failure.getMessage());
        }
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
        return Path.of(AnalyticsBundleRevisionCalculatorTest.class
                .getResource("/fixtures/analytics-bundle/basic/manifest.json")
                .toURI())
                .getParent();
    }
}

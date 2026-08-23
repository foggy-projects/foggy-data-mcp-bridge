package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Strict filesystem shape and typed cross-reference validation for v1 bundles. */
public final class AnalyticsBundleStructureValidator {

    private final AnalyticsBundleIndexer indexer;
    private final AnalyticsArtifactPathPolicy pathPolicy;

    public AnalyticsBundleStructureValidator() {
        this(new AnalyticsBundleIndexer(), new AnalyticsArtifactPathPolicy());
    }

    AnalyticsBundleStructureValidator(
            AnalyticsBundleIndexer indexer,
            AnalyticsArtifactPathPolicy pathPolicy) {
        this.indexer = Objects.requireNonNull(indexer, "indexer");
        this.pathPolicy = Objects.requireNonNull(pathPolicy, "pathPolicy");
    }

    public void validate(Path bundleRoot, AnalyticsBundleManifest manifest) throws IOException {
        Objects.requireNonNull(bundleRoot, "bundleRoot");
        Objects.requireNonNull(manifest, "manifest");
        Map<String, byte[]> artifacts = new LinkedHashMap<>();

        try (var paths = Files.walk(bundleRoot)) {
            for (Path path : paths.filter(candidate -> !candidate.equals(bundleRoot)).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw invalid("Analytics Bundle must not contain symbolic links: " + path);
                }
                String relative = portable(bundleRoot, path);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!pathPolicy.isSupportedDirectory(relative)) {
                        throw invalid("Unsupported Analytics Bundle directory: " + relative);
                    }
                    continue;
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw invalid("Unsupported Analytics Bundle entry: " + relative);
                }
                if (AnalyticsBundleRevisionCalculator.MANIFEST_FILE.equals(relative)) {
                    continue;
                }
                if (!pathPolicy.isSupportedArtifact(relative)) {
                    throw invalid("Unsupported Analytics Bundle file: " + relative);
                }
                if (pathPolicy.isDefinitionArtifact(relative)) {
                    artifacts.put(relative, Files.readAllBytes(path));
                }
            }
        }

        indexer.validate(manifest, artifacts);
    }

    /** Performs local syntax/shape validation before a write transaction starts. */
    void validateArtifact(String relativePath, byte[] content) {
        indexer.validateArtifact(relativePath, content);
    }

    private static String portable(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

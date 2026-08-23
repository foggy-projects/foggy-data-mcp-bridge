package com.foggyframework.analytics.definition.core;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Analytics-specific path allowlist; it does not reuse or widen model resource paths. */
final class AnalyticsArtifactPathPolicy {

    Path resolve(Path root, String relativePath) {
        Objects.requireNonNull(root, "root");
        if (relativePath == null || relativePath.isBlank() || relativePath.indexOf('\0') >= 0) {
            throw unsafe("Artifact path must be non-blank");
        }
        String portablePath = relativePath.replace('\\', '/');
        Path parsed;
        try {
            parsed = Path.of(portablePath);
        } catch (RuntimeException invalidPath) {
            throw unsafe("Artifact path is invalid", invalidPath);
        }
        if (parsed.isAbsolute()) {
            throw unsafe("Absolute artifact paths are not allowed");
        }
        for (Path segment : parsed) {
            if (".".equals(segment.toString()) || "..".equals(segment.toString())) {
                throw unsafe("Artifact paths must not contain traversal segments");
            }
        }
        Path normalized = parsed.normalize();
        String canonical = normalized.toString().replace('\\', '/');
        if (canonical.isBlank()
                || ".".equals(canonical)
                || "..".equals(canonical)
                || canonical.startsWith("../")) {
            throw unsafe("Artifact path must remain inside the bundle root");
        }
        if (!isSupportedArtifact(canonical)) {
            throw new AnalyticsBundleStoreException(
                    AnalyticsBundleStoreException.Code.UNSUPPORTED_RESOURCE_PATH,
                    "Unsupported Analytics artifact path: " + relativePath);
        }
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw unsafe("Artifact path must remain inside the bundle root");
        }
        rejectExistingSymlinks(root, target);
        return target;
    }

    boolean isSupportedArtifact(String portablePath) {
        return isDefinitionArtifact(portablePath)
                || (portablePath.startsWith("assets/")
                && portablePath.length() > "assets/".length());
    }

    boolean isDefinitionArtifact(String portablePath) {
        return typedJson(portablePath, "queries/", ".query.json")
                || typedJson(portablePath, "reports/", ".report.json")
                || typedJson(portablePath, "dashboards/", ".dashboard.json");
    }

    boolean isSupportedDirectory(String portablePath) {
        return "queries".equals(portablePath)
                || portablePath.startsWith("queries/")
                || "reports".equals(portablePath)
                || portablePath.startsWith("reports/")
                || "dashboards".equals(portablePath)
                || portablePath.startsWith("dashboards/")
                || "assets".equals(portablePath)
                || portablePath.startsWith("assets/");
    }

    private static boolean typedJson(String path, String prefix, String suffix) {
        return path.startsWith(prefix)
                && path.length() > prefix.length() + suffix.length()
                && path.endsWith(suffix);
    }

    private static void rejectExistingSymlinks(Path root, Path target) {
        Path cursor = root;
        for (Path segment : root.relativize(target)) {
            cursor = cursor.resolve(segment);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(cursor)) {
                throw unsafe("Analytics artifact path must not traverse symbolic links");
            }
        }
    }

    private static AnalyticsBundleStoreException unsafe(String message) {
        return new AnalyticsBundleStoreException(
                AnalyticsBundleStoreException.Code.UNSAFE_PATH,
                message);
    }

    private static AnalyticsBundleStoreException unsafe(String message, Throwable cause) {
        return new AnalyticsBundleStoreException(
                AnalyticsBundleStoreException.Code.UNSAFE_PATH,
                message,
                cause);
    }
}

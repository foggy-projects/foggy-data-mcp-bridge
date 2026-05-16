package com.foggyframework.bundle.external;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Shared resource-location helpers for external bundle roots.
 */
public final class ExternalBundleResourceSupport {

    private static final ResourcePatternResolver RESOLVER = new PathMatchingResourcePatternResolver();

    private ExternalBundleResourceSupport() {
    }

    public static boolean isSpringResourceLocation(String path) {
        if (path == null) {
            return false;
        }
        String value = path.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("classpath:")
                || value.startsWith("classpath*:")
                || value.startsWith("file:")
                || value.startsWith("jar:")
                || value.startsWith("http:")
                || value.startsWith("https:");
    }

    public static String normalizeBaseLocation(String path) {
        String value = path == null ? "" : path.trim();
        while (value.endsWith("/") || value.endsWith("\\")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public static String toPatternLocation(String basePath, String pattern) {
        String base = normalizeBaseLocation(basePath).replace("\\", "/");
        String normalizedPattern = pattern == null ? "" : pattern.replace("\\", "/");
        if (normalizedPattern.startsWith("/")) {
            normalizedPattern = normalizedPattern.substring(1);
        }
        return base + "/" + normalizedPattern;
    }

    public static Resource[] getResources(String locationPattern) throws IOException {
        return RESOLVER.getResources(locationPattern);
    }

    public static boolean isReadableBundleRoot(String path) {
        if (!isSpringResourceLocation(path)) {
            File dir = new File(path);
            return dir.exists() && dir.isDirectory() && dir.canRead();
        }

        String baseLocation = normalizeBaseLocation(path);
        try {
            Resource root = RESOLVER.getResource(baseLocation);
            if (root.exists()) {
                if (root.isFile()) {
                    File file = root.getFile();
                    return file.isDirectory() && file.canRead();
                }
                return root.isReadable();
            }

            Resource[] children = RESOLVER.getResources(toPatternLocation(baseLocation, "**/*"));
            for (Resource child : children) {
                if (child.exists() && child.isReadable()) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}

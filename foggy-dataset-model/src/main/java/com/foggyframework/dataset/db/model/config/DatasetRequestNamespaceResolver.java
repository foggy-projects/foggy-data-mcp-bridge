package com.foggyframework.dataset.db.model.config;

/**
 * Resolves API-level default namespace without changing the storage-layer
 * meaning of blank namespace.
 */
public final class DatasetRequestNamespaceResolver {

    private DatasetRequestNamespaceResolver() {
    }

    public static String resolve(DatasetProperties properties, String namespace) {
        String normalized = blankToNull(namespace);
        if (normalized != null) {
            return normalized;
        }
        if (properties == null || properties.getRequest() == null) {
            return namespace;
        }
        String defaultNamespace = blankToNull(properties.getRequest().getDefaultNamespace());
        return defaultNamespace != null ? defaultNamespace : namespace;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

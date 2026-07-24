package com.foggyframework.dataset.model.engine.pivot;

/**
 * Contract descriptor for distributed Pivot outer-cache provider implementations.
 *
 * <p>This class is intentionally client-neutral. It does not bind the engine to
 * Redis, MQ, or a concrete serializer; it records the key and payload rules that
 * an external provider must preserve before replacing the local provider.</p>
 */
public record PivotOuterCacheDistributedProviderContract(String keyPrefix,
                                                         String payloadVersion,
                                                         String payloadContentType,
                                                         long ttlMillis,
                                                         boolean storesAbsoluteExpiresAtMillis,
                                                         boolean requiresNamespaceModelIndex,
                                                         boolean requiresCopyIsolation) {

    public static final String DEFAULT_KEY_PREFIX = "foggy:pivot:outer";
    public static final String DEFAULT_PAYLOAD_VERSION = "v1";
    public static final String DEFAULT_PAYLOAD_CONTENT_TYPE = "application/json";

    public PivotOuterCacheDistributedProviderContract {
        keyPrefix = normalizeRequired(keyPrefix, "keyPrefix");
        payloadVersion = normalizeRequired(payloadVersion, "payloadVersion");
        payloadContentType = normalizeRequired(payloadContentType, "payloadContentType");
        if (ttlMillis <= 0L) {
            throw new IllegalArgumentException("ttlMillis must be positive");
        }
    }

    public static PivotOuterCacheDistributedProviderContract json(String keyPrefix, long ttlMillis) {
        return new PivotOuterCacheDistributedProviderContract(
                keyPrefix,
                DEFAULT_PAYLOAD_VERSION,
                DEFAULT_PAYLOAD_CONTENT_TYPE,
                ttlMillis,
                true,
                true,
                true);
    }

    public static PivotOuterCacheDistributedProviderContract defaultJson(long ttlMillis) {
        return json(DEFAULT_KEY_PREFIX, ttlMillis);
    }

    public String responseKey(String keyHash) {
        return keyPrefix + ":response:" + normalizeRequired(keyHash, "keyHash");
    }

    public String namespaceIndexKey(String namespace) {
        return keyPrefix + ":idx:namespace:" + namespaceToken(namespace);
    }

    public String modelIndexKey(String namespace, String model) {
        String normalizedModel = model == null || model.isBlank() ? null : model.trim();
        if (normalizedModel == null) {
            throw new IllegalArgumentException("model must be non-blank for model index key");
        }
        return namespaceIndexKey(namespace) + ":model:" + normalizedModel;
    }

    public PivotOuterCacheInvalidationEvent evictionScope(String namespace, String model) {
        return PivotOuterCacheInvalidationEvent.of(namespace, model);
    }

    private static String namespaceToken(String namespace) {
        if (namespace == null) {
            return "all";
        }
        if (namespace.isBlank()) {
            return "default";
        }
        return namespace.trim();
    }

    private static String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.trim();
    }
}

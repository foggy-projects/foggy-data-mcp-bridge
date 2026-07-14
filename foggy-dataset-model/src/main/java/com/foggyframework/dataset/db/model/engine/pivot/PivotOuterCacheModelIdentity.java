package com.foggyframework.dataset.db.model.engine.pivot;

/**
 * Supplementary deployment/model tokens used by the Pivot outer-cache key.
 */
public record PivotOuterCacheModelIdentity(String bundleFingerprint,
                                           String modelFreshnessToken) {

    public static PivotOuterCacheModelIdentity empty() {
        return new PivotOuterCacheModelIdentity("", "");
    }

    public PivotOuterCacheModelIdentity normalized() {
        return new PivotOuterCacheModelIdentity(normalize(bundleFingerprint), normalize(modelFreshnessToken));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

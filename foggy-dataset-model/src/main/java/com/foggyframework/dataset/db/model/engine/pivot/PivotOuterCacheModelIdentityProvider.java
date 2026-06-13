package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.spi.QueryModel;

/**
 * Provides deployment/model identity for Pivot outer-cache keying.
 *
 * <p>Production deployments can replace this provider with one backed by a
 * signed model registry hash. The default implementation derives a stable
 * local fingerprint from runtime bundle resources when available.</p>
 */
public interface PivotOuterCacheModelIdentityProvider {

    PivotOuterCacheModelIdentity resolve(String namespace, String model, QueryModel queryModel);

    static PivotOuterCacheModelIdentityProvider empty() {
        return (namespace, model, queryModel) -> PivotOuterCacheModelIdentity.empty();
    }
}

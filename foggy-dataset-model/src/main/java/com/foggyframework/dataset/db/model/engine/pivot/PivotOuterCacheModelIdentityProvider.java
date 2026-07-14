package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.spi.QueryModel;

/**
 * Provides supplementary deployment/model tokens for Pivot outer-cache keying.
 *
 * <p>These tokens are additive key material only. They never replace the
 * catalog and datasource lifecycle identity resolved atomically by the model
 * loader. The default implementation derives a stable local fingerprint from
 * runtime bundle resources when available.</p>
 */
public interface PivotOuterCacheModelIdentityProvider {

    PivotOuterCacheModelIdentity resolve(String namespace, String model, QueryModel queryModel);

    static PivotOuterCacheModelIdentityProvider empty() {
        return (namespace, model, queryModel) -> PivotOuterCacheModelIdentity.empty();
    }
}

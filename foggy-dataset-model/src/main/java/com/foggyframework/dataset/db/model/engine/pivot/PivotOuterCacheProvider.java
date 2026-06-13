package com.foggyframework.dataset.db.model.engine.pivot;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;

/**
 * Provider boundary for Pivot outer response cache storage.
 *
 * <p>Implementations may be local or distributed, but they must preserve the
 * same correctness contract: disabled providers never hit or store, successful
 * lookups return response instances isolated from stored state, stores isolate
 * cached state from caller-side mutation, TTL expiry is reported as expired and
 * removes the stale entry, and eviction honors the namespace/model scope rules.
 */
public interface PivotOuterCacheProvider {

    String name();

    boolean isEnabled();

    long ttlMillis();

    LookupResult lookup(String keyHash, long nowMillis);

    void store(String keyHash,
               SemanticQueryResponse response,
               long nowMillis,
               String namespace,
               String model);

    int evict(String namespace, String model);

    int estimatePayloadBytes(SemanticQueryResponse response);

    record LookupResult(SemanticQueryResponse response, long ageMs, boolean hit, boolean expired) {
        public static LookupResult hit(SemanticQueryResponse response, long ageMs) {
            return new LookupResult(response, ageMs, true, false);
        }

        public static LookupResult miss() {
            return new LookupResult(null, 0L, false, false);
        }

        public static LookupResult expired(long ageMs) {
            return new LookupResult(null, ageMs, false, true);
        }
    }
}

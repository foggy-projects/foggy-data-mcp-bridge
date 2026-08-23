package com.foggyframework.analytics.definition.core;

import com.foggyframework.analytics.definition.api.AnalyticsBundleRef;
import com.foggyframework.analytics.definition.api.AnalyticsBundleRevision;

import java.util.Objects;

/** Typed definition resolver backed by a revision-guarded Analytics Bundle store. */
public final class StoreBackedAnalyticsDefinitionResolver
        implements AnalyticsDefinitionResolver {

    private final AnalyticsArtifactStore artifactStore;
    private final AnalyticsBundleIndexer indexer;

    public StoreBackedAnalyticsDefinitionResolver(AnalyticsArtifactStore artifactStore) {
        this(artifactStore, new AnalyticsBundleIndexer());
    }

    StoreBackedAnalyticsDefinitionResolver(
            AnalyticsArtifactStore artifactStore,
            AnalyticsBundleIndexer indexer) {
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
        this.indexer = Objects.requireNonNull(indexer, "indexer");
    }

    @Override
    public AnalyticsBundleIndex resolve(
            AnalyticsBundleRef bundleRef,
            AnalyticsBundleRevision expectedRevision) {
        try {
            return indexer.index(artifactStore.readDefinitionSnapshot(
                    bundleRef,
                    expectedRevision));
        } catch (AnalyticsBundleStoreException known) {
            throw known;
        } catch (IllegalArgumentException invalid) {
            throw new AnalyticsBundleStoreException(
                    AnalyticsBundleStoreException.Code.INVALID_BUNDLE,
                    "Analytics definition snapshot is invalid",
                    invalid);
        }
    }
}

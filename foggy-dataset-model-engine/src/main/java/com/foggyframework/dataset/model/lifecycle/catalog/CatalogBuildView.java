package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable input view captured before a detached catalog build starts.
 *
 * <p>A cold view has no base snapshot, but still carries a stable committed
 * source revision. Publication succeeds only while both the exact base
 * snapshot and this source revision remain current.</p>
 */
public record CatalogBuildView(
        String namespace,
        CatalogSnapshot baseSnapshot,
        SourceRevision sourceRevision,
        long storeRevision
) {

    public CatalogBuildView {
        namespace = CatalogIdentity.canonicalNamespace(namespace);
        Objects.requireNonNull(sourceRevision, "sourceRevision");
        if (baseSnapshot != null
                && !namespace.equals(baseSnapshot.identity().namespace())) {
            throw new IllegalArgumentException("build view/base namespace mismatch");
        }
        if (storeRevision < 0) {
            throw new IllegalArgumentException("storeRevision must not be negative");
        }
    }

    /** Compatibility constructor for callers that do not create store-owned views. */
    public CatalogBuildView(
            String namespace,
            CatalogSnapshot baseSnapshot,
            SourceRevision sourceRevision
    ) {
        this(namespace, baseSnapshot, sourceRevision, 0L);
    }

    public boolean cold() {
        return baseSnapshot == null;
    }

    public Optional<CatalogGeneration> catalogGeneration() {
        return baseSnapshot == null
                ? Optional.empty()
                : Optional.of(baseSnapshot.identity().generation());
    }
}

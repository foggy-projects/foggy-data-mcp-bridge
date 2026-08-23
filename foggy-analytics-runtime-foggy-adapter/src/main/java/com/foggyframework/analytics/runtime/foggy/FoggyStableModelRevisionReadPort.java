package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelRevision;

import java.util.Optional;

/**
 * Host bridge from an exact runtime catalog view to a persistable model revision.
 *
 * <p>The returned revision must identify the model content and governed dependency
 * closure represented by the supplied catalog identity. Implementations must not
 * hash or persist {@code CatalogIdentity}; its generation is process-local.</p>
 */
@FunctionalInterface
public interface FoggyStableModelRevisionReadPort {

    Optional<AnalyticsModelRevision> findRevision(FoggyModelRevisionLookup lookup);

    /** Safe default for hosts that have not installed a trusted revision source. */
    static FoggyStableModelRevisionReadPort unavailable() {
        return lookup -> Optional.empty();
    }
}

package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsModelDigest;

import java.util.Optional;

/**
 * Host bridge from an exact runtime catalog view to an internal content digest.
 *
 * <p>The returned digest identifies the model content and governed dependency
 * closure represented by the supplied catalog identity. It is used only for
 * Bundle audit and stale detection; live model selection never consults it.</p>
 */
@FunctionalInterface
public interface FoggyStableModelDigestReadPort {

    Optional<AnalyticsModelDigest> findDigest(FoggyModelDigestLookup lookup);

    /** Safe default for hosts that have not installed a trusted digest source. */
    static FoggyStableModelDigestReadPort unavailable() {
        return lookup -> Optional.empty();
    }
}

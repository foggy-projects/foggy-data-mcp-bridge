package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsNamespaceRef;

/** Maps a stable Analytics namespace reference to Foggy's runtime namespace key. */
@FunctionalInterface
public interface FoggyAnalyticsNamespaceMapper {

    String toEngineNamespace(AnalyticsNamespaceRef namespaceRef);

    /** Maps the public non-empty {@code default} reference to Foggy's empty default key. */
    static FoggyAnalyticsNamespaceMapper defaultConvention() {
        return namespaceRef -> "default".equals(namespaceRef.value())
                ? ""
                : namespaceRef.value();
    }
}

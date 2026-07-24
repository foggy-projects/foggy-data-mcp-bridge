package com.foggyframework.dataset.model.semantic.port;

/**
 * Narrow boundary for process-local Pivot outer-cache eviction.
 */
@FunctionalInterface
public interface PivotOuterCacheEvictionPort {

    /**
     * Evict entries by namespace and/or model.
     *
     * <p>{@code namespace == null} means all namespaces; {@code namespace == ""}
     * targets the default namespace. {@code model == null} or blank means all
     * models in the selected namespace scope.</p>
     *
     * @return number of local entries removed
     */
    int evictPivotOuterCache(String namespace, String model);
}

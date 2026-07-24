package com.foggyframework.dataset.model.lifecycle.refresh;

/** Builds and validates models inside one detached candidate; never commits it. */
@FunctionalInterface
public interface CatalogRefreshCallback {

    void build(CatalogRefreshBuildContext context) throws Exception;
}

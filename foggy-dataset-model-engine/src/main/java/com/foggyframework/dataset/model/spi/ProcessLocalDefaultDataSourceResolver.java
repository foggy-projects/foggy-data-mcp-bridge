package com.foggyframework.dataset.model.spi;

import com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding;

/**
 * Explicit opt-in for resolving a default datasource outside a named runtime
 * namespace.
 *
 * <p>The dedicated method preserves compatibility with existing
 * {@link NamedDataSourceResolver} implementations, whose
 * {@code resolveDefault(String)} method was historically called only with a
 * non-empty namespace.
 */
public interface ProcessLocalDefaultDataSourceResolver {

    /**
     * @return a process-local default binding, or {@code null} when none is configured
     */
    ResolvedDatasourceBinding resolveProcessLocalDefaultBinding();
}

package com.foggyframework.dataset.model.api;

/**
 * Stable public query entry point.
 *
 * <p>The public contract intentionally exposes only DTOs made from JDK types.
 * Engine contexts, JDBC results and managed relations belong to internal or
 * advanced ports.</p>
 *
 * @since 9.3.5
 */
public interface QueryFacade {

    QueryFacadeResult query(QueryFacadeRequest request);
}

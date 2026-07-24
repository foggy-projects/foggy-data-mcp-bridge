package com.foggyframework.dataset.model.engine.pivot.transport;

/**
 * Indicates where the DomainRelationRenderResult should be placed in the final SQL.
 */
public enum DomainTransportPlacement {
    /**
     * Injected at the top of the query as a Common Table Expression.
     */
    CTE,

    /**
     * Injected in the FROM or JOIN clause as a derived table.
     */
    DERIVED_TABLE
}

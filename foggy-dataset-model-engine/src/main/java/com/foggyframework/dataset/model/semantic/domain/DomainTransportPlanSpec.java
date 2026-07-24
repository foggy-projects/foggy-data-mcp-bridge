package com.foggyframework.dataset.model.semantic.domain;

/**
 * Engine-neutral contract for a large-domain transport plan carried by a
 * semantic request context.
 *
 * <p>The semantic layer treats plans as opaque request data. Dialect-specific
 * fields, tuples, validation, and rendering remain owned by the query engine.</p>
 */
public interface DomainTransportPlanSpec {

    String EXT_DATA_KEY = "pivotDomainTransportPlans";

    String getRelationName();

    int parameterCount();
}

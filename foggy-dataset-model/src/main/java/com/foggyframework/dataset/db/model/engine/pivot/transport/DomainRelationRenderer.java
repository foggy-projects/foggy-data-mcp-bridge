package com.foggyframework.dataset.db.model.engine.pivot.transport;

import com.foggyframework.dataset.db.dialect.FDialect;

/**
 * SPI for rendering DomainTransportPlan into dialect-specific SQL.
 */
public interface DomainRelationRenderer {
    /**
     * Render the transport plan.
     *
     * @param dialect         the target dialect
     * @param databaseVersion the database product version string (e.g., "8.0.19"), may be null
     * @param plan            the domain transport plan
     * @return the rendered result
     * @throws DomainTransportRefusalException if unsupported or exceeds dialect safety limits
     */
    DomainRelationRenderResult render(FDialect dialect, String databaseVersion, DomainTransportPlan plan) throws DomainTransportRefusalException;
}

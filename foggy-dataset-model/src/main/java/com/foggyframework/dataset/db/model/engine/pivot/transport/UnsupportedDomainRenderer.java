package com.foggyframework.dataset.db.model.engine.pivot.transport;

import com.foggyframework.dataset.db.dialect.FDialect;

public class UnsupportedDomainRenderer implements DomainRelationRenderer {
    @Override
    public DomainRelationRenderResult render(FDialect dialect, String databaseVersion, DomainTransportPlan plan) throws DomainTransportRefusalException {
        throw new DomainTransportRefusalException("Domain transport is completely unsupported for dialect: " + dialect.getProductName());
    }
}

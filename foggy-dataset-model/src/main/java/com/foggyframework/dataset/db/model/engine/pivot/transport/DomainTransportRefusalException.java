package com.foggyframework.dataset.db.model.engine.pivot.transport;

/**
 * Thrown when a DomainTransportPlan cannot be rendered safely for the current dialect.
 */
public class DomainTransportRefusalException extends RuntimeException {
    public DomainTransportRefusalException(String message) {
        super(message);
    }
}

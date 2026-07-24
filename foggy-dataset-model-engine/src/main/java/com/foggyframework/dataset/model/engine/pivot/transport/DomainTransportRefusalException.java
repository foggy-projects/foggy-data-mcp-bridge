package com.foggyframework.dataset.model.engine.pivot.transport;

/**
 * Thrown when a DomainTransportPlan cannot be rendered safely for the current dialect.
 */
public class DomainTransportRefusalException extends RuntimeException {
    public DomainTransportRefusalException(String message) {
        super(message);
    }

    public DomainTransportRefusalException(String message, Throwable cause) {
        super(message, cause);
    }
}

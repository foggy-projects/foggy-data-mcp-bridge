package com.foggyframework.dataset.model.lifecycle.port;

/** Raised when a captured datasource binding is no longer the active logical generation. */
public final class StaleDatasourceBindingException extends IllegalStateException {

    public StaleDatasourceBindingException(String bindingKey) {
        super("STALE_DATASOURCE_BINDING: " + bindingKey);
    }
}

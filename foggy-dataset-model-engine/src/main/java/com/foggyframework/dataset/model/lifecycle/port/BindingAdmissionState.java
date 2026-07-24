package com.foggyframework.dataset.model.lifecycle.port;

/** Frozen admission states for a generation-pinned datasource handle. */
public enum BindingAdmissionState {
    OPEN,
    RETIRING,
    REVOKED,
    CLOSED
}

package com.foggyframework.dataset.model.api.backend;

/**
 * Minimal backend discovery contract.
 *
 * <p>Operational capabilities are intentionally exposed through separate
 * small ports instead of growing this interface into an implementation API.</p>
 */
public interface BackendProvider {

    BackendDescriptor descriptor();
}

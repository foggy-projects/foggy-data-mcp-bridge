package com.foggyframework.dataset.model.validation;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;

/**
 * Request-local validation session whose model/script state cannot publish to
 * the live bundle context or catalog.
 *
 * @since 9.3.5
 */
public interface DetachedModelValidationSession extends AutoCloseable {

    Bundle sourceBundle();

    void validateTableModel(BundleResource resource, String namespace);

    void validateQueryModel(BundleResource resource);

    @Override
    void close();
}

package com.foggyframework.dataset.model.validation;

import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.spi.QueryModel;

/**
 * Request-local validation session whose model/script state cannot publish to
 * the live bundle context or catalog.
 *
 * @since 9.3.5
 */
public interface DetachedModelValidationSession extends AutoCloseable {

    Bundle sourceBundle();

    void validateTableModel(BundleResource resource, String namespace);

    /** Load and parse one request-local FSScript and its imports. */
    void validateFsscript(BundleResource resource);

    void validateQueryModel(BundleResource resource);

    /**
     * Resolve a query model from the request-local catalog used by this
     * detached session.
     */
    CatalogResolution<QueryModel> resolveQueryModel(
            String queryModelName,
            String namespace
    );

    /**
     * Read-only Bundle view paired with the detached model resolution.
     */
    SystemBundlesContext executionBundlesContext();

    @Override
    void close();
}

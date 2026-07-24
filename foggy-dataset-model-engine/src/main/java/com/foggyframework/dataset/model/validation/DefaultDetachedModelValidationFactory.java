package com.foggyframework.dataset.model.validation;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;

/**
 * Model-engine adapter that copies live loader configuration into an isolated
 * request-local validation session.
 *
 * @since 9.3.5
 */
public final class DefaultDetachedModelValidationFactory
        implements DetachedModelValidationFactory {

    private final SystemBundlesContext liveBundlesContext;
    private final TableModelLoaderManager liveTableModelLoaderManager;
    private final QueryModelLoader liveQueryModelLoader;

    public DefaultDetachedModelValidationFactory(
            SystemBundlesContext liveBundlesContext,
            TableModelLoaderManager liveTableModelLoaderManager,
            QueryModelLoader liveQueryModelLoader
    ) {
        this.liveBundlesContext = liveBundlesContext;
        this.liveTableModelLoaderManager = liveTableModelLoaderManager;
        this.liveQueryModelLoader = liveQueryModelLoader;
    }

    @Override
    public DetachedModelValidationSession open(
            String bundleName,
            String namespace,
            String path
    ) {
        return new DetachedModelValidationSessionImpl(
                liveBundlesContext,
                liveTableModelLoaderManager,
                liveQueryModelLoader,
                bundleName,
                namespace,
                path
        );
    }
}

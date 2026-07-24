package com.foggyframework.dataset.model.spi;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;

/**
 * Optional SPI for {@link QueryModelBuilder} implementations that can create a
 * request-local copy for detached model validation.
 *
 * <p>Builders that do not implement this contract are ignored by detached
 * validation. This prevents an unrelated addon builder from rejecting a
 * candidate handled by another builder while keeping live builder instances
 * out of the request-local validation catalog.</p>
 */
public interface DetachedQueryModelBuilderFactory {

    QueryModelBuilder createDetachedQueryModelBuilder(
            TableModelLoaderManager tableModelLoaderManager,
            SystemBundlesContext systemBundlesContext,
            FileFsscriptLoader fileFsscriptLoader
    );
}

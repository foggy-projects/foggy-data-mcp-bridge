package com.foggyframework.dataset.model.validation;

/**
 * Internal port for opening isolated TM/QM validation sessions.
 *
 * @since 9.3.5
 */
public interface DetachedModelValidationFactory {

    DetachedModelValidationSession open(
            String bundleName,
            String namespace,
            String path
    );
}

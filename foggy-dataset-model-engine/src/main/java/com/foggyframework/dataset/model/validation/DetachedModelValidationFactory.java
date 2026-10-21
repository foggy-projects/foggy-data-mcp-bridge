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
    /** Explicit authoring replacement; generic validation must never shadow by bundle name. */
    default DetachedModelValidationSession openReplacement(String bundleName, String namespace, String path) {
        throw new UnsupportedOperationException("detached replacement is not supported by this factory");
    }
}

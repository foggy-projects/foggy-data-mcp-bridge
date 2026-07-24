package com.foggyframework.dataset.model.engine.compose.capability;

import java.util.Set;

/**
 * Frozen error codes for v1.7 capability registry violations.
 *
 * <p>String constants must match Python
 * {@code foggy.dataset_model.engine.compose.capability.errors}
 * byte-for-byte. Append-only; renaming/removing requires SPI version bump.</p>
 *
 * @since 8.4.0
 */
public final class CapabilityErrorCodes {

    private CapabilityErrorCodes() { /* utility */ }

    public static final String CAPABILITY_NOT_REGISTERED     = "capability/not-registered";
    public static final String CAPABILITY_NOT_ALLOWED        = "capability/not-allowed";
    public static final String CAPABILITY_INVALID_DESCRIPTOR = "capability/invalid-descriptor";
    public static final String CAPABILITY_UNSUPPORTED_DIALECT = "capability/unsupported-dialect";
    public static final String CAPABILITY_METHOD_NOT_DECLARED = "capability/method-not-declared";
    public static final String CAPABILITY_SIDE_EFFECT_DENIED = "capability/side-effect-denied";
    public static final String CAPABILITY_RETURN_TYPE_DENIED = "capability/return-type-denied";
    public static final String CAPABILITY_TIMEOUT            = "capability/timeout";

    public static final Set<String> ALL_CODES = Set.of(
            CAPABILITY_NOT_REGISTERED,
            CAPABILITY_NOT_ALLOWED,
            CAPABILITY_INVALID_DESCRIPTOR,
            CAPABILITY_UNSUPPORTED_DIALECT,
            CAPABILITY_METHOD_NOT_DECLARED,
            CAPABILITY_SIDE_EFFECT_DENIED,
            CAPABILITY_RETURN_TYPE_DENIED,
            CAPABILITY_TIMEOUT
    );
}

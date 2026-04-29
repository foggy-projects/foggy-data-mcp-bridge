package com.foggyframework.dataset.db.model.engine.compose.capability;

/**
 * Base exception for all capability registry errors.
 *
 * <p>Every instance carries a structured {@link #code} from
 * {@link CapabilityErrorCodes}. Subclasses map to specific
 * error conditions matching the Python exception hierarchy.</p>
 *
 * @since 8.4.0
 */
public class CapabilityException extends RuntimeException {

    private final String code;

    public CapabilityException(String code, String message) {
        super(message);
        if (!CapabilityErrorCodes.ALL_CODES.contains(code)) {
            throw new IllegalArgumentException(
                    "CapabilityException code must be one of ALL_CODES, got '" + code + "'");
        }
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    // ---------------------------------------------------------------
    // Concrete subclasses
    // ---------------------------------------------------------------

    /** Function or object not registered in the capability registry. */
    public static class NotRegistered extends CapabilityException {
        public NotRegistered(String message) {
            super(CapabilityErrorCodes.CAPABILITY_NOT_REGISTERED, message);
        }
    }

    /** Capability is registered but current policy does not allow it. */
    public static class NotAllowed extends CapabilityException {
        public NotAllowed(String message) {
            super(CapabilityErrorCodes.CAPABILITY_NOT_ALLOWED, message);
        }
    }

    /** Descriptor fields are invalid or incomplete. */
    public static class InvalidDescriptor extends CapabilityException {
        public InvalidDescriptor(String message) {
            super(CapabilityErrorCodes.CAPABILITY_INVALID_DESCRIPTOR, message);
        }
    }

    /** SQL function does not support the current dialect. */
    public static class UnsupportedDialect extends CapabilityException {
        public UnsupportedDialect(String message) {
            super(CapabilityErrorCodes.CAPABILITY_UNSUPPORTED_DIALECT, message);
        }
    }

    /** Object facade method not declared in the descriptor. */
    public static class MethodNotDeclared extends CapabilityException {
        public MethodNotDeclared(String message) {
            super(CapabilityErrorCodes.CAPABILITY_METHOD_NOT_DECLARED, message);
        }
    }

    /** Descriptor or handler declares/exhibits side effects. */
    public static class SideEffectDenied extends CapabilityException {
        public SideEffectDenied(String message) {
            super(CapabilityErrorCodes.CAPABILITY_SIDE_EFFECT_DENIED, message);
        }
    }

    /** Return value type is not in the allowed set. */
    public static class ReturnTypeDenied extends CapabilityException {
        public ReturnTypeDenied(String message) {
            super(CapabilityErrorCodes.CAPABILITY_RETURN_TYPE_DENIED, message);
        }
    }

    /** Object facade method exceeded timeout. */
    public static class Timeout extends CapabilityException {
        public Timeout(String message) {
            super(CapabilityErrorCodes.CAPABILITY_TIMEOUT, message);
        }
    }
}

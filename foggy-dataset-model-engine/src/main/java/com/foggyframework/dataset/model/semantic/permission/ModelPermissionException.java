package com.foggyframework.dataset.model.semantic.permission;

/**
 * Stable, non-enumerating data-plane authorization failure.
 */
public class ModelPermissionException extends RuntimeException {

    private final String code;

    public ModelPermissionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ModelPermissionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ModelPermissionException denied() {
        return new ModelPermissionException(
                "MODEL_ACCESS_DENIED",
                "The requested model operation is not available."
        );
    }

    public static ModelPermissionException invalid(Throwable cause) {
        return new ModelPermissionException(
                "MODEL_PERMISSION_RESOLUTION_FAILED",
                "The requested model operation could not be authorized.",
                cause
        );
    }
}

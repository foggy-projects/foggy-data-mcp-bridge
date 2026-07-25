package com.foggyframework.runtime.api.service;

/** Stable pre-publication rejection for a Runtime-managed Bundle. */
public final class RuntimeBundleAdmissionException extends RuntimeException {

    private final String code;

    public RuntimeBundleAdmissionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

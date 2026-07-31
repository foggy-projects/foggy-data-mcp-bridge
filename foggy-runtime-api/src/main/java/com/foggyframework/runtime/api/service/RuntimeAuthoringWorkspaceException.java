package com.foggyframework.runtime.api.service;

public final class RuntimeAuthoringWorkspaceException extends IllegalStateException {

    private final String code;
    private final String phase;
    private final String path;
    private final boolean safeToAutoRepair;

    public RuntimeAuthoringWorkspaceException(
            String code,
            String phase,
            String message,
            String path,
            boolean safeToAutoRepair
    ) {
        super(message);
        this.code = code;
        this.phase = phase;
        this.path = path;
        this.safeToAutoRepair = safeToAutoRepair;
    }

    public String code() {
        return code;
    }

    public String phase() {
        return phase;
    }

    public String path() {
        return path;
    }

    public boolean safeToAutoRepair() {
        return safeToAutoRepair;
    }
}

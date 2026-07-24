package com.foggyframework.dataset.db.model.semantic.port;

/** Stable error boundary that prevents host adapters importing engine errors. */
public class ComposeExecutionException extends RuntimeException {

    public enum Kind {
        AUTHORITY,
        SANDBOX,
        SCHEMA,
        COMPILE
    }

    private final Kind kind;
    private final String code;
    private final String enginePhase;
    private final String field;
    private final String model;

    public ComposeExecutionException(
            Kind kind,
            String code,
            String enginePhase,
            String message,
            String field,
            String model,
            Throwable cause
    ) {
        super(message, cause);
        this.kind = kind;
        this.code = code;
        this.enginePhase = enginePhase;
        this.field = field;
        this.model = model;
    }

    public Kind kind() { return kind; }
    public String code() { return code; }
    public String enginePhase() { return enginePhase; }
    public String field() { return field; }
    public String model() { return model; }
}

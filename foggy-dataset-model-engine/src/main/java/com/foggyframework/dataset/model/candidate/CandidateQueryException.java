package com.foggyframework.dataset.model.candidate;

import java.util.Objects;

/** Sanitized, stable candidate-query failure. */
public final class CandidateQueryException extends IllegalStateException {

    private final CandidateQueryErrorCode code;
    private final String phase;
    private final String resource;

    public CandidateQueryException(
            CandidateQueryErrorCode code,
            String phase,
            String message,
            String resource
    ) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.phase = phase;
        this.resource = resource;
    }

    public CandidateQueryErrorCode code() {
        return code;
    }

    public String phase() {
        return phase;
    }

    public String resource() {
        return resource;
    }
}

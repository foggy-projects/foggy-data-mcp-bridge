package com.foggyframework.dataset.model.engine.compose.security;

/**
 * Structured failure for authority resolution.
 *
 * <p>Fail-closed: any violation of the M1 contract (missing binding, malformed
 * response, upstream failure, ir.rule unmapped field, principal mismatch, etc.)
 * must raise {@code AuthorityResolutionException}. Callers propagate; they do
 * not fall back to a partial resolution.</p>
 *
 * <p><b>Sanitisation.</b> Callers constructing this exception must not embed
 * raw physical column names, raw {@code ir.rule.domain_force} text, or other
 * users' identifiers in the message. Use the v1.3 error sanitiser utilities
 * upstream if needed.</p>
 *
 * @since 8.2.0.beta
 */
public class AuthorityResolutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String modelInvolved;
    private final String phase;

    public AuthorityResolutionException(String code, String message,
                                        String modelInvolved, String phase) {
        this(code, message, modelInvolved, phase, null);
    }

    public AuthorityResolutionException(String code, String message,
                                        String modelInvolved, String phase,
                                        Throwable cause) {
        super(message, cause);

        if (code == null || !AuthorityErrorCodes.ALL_CODES.contains(code)) {
            throw new IllegalArgumentException(
                    "AuthorityResolutionException.code must be one of "
                            + "AuthorityErrorCodes.ALL_CODES, got: " + code);
        }
        if (phase == null || !AuthorityErrorCodes.VALID_PHASES.contains(phase)) {
            throw new IllegalArgumentException(
                    "AuthorityResolutionException.phase must be one of "
                            + "AuthorityErrorCodes.VALID_PHASES, got: " + phase);
        }

        this.code = code;
        this.modelInvolved = modelInvolved;  // may be null (resolver-wide failure)
        this.phase = phase;
    }

    public String code() { return code; }
    public String modelInvolved() { return modelInvolved; }
    public String phase() { return phase; }

    @Override
    public String toString() {
        return "AuthorityResolutionException{code=" + code
                + ", modelInvolved=" + modelInvolved
                + ", phase=" + phase
                + ", message=" + getMessage()
                + '}';
    }
}

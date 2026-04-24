package com.foggyframework.dataset.db.model.engine.compose.compilation;

/**
 * Exception raised by the Compose Query SQL compiler (M6).
 *
 * <p>Mirrors the shape pattern used by
 * {@link com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException}
 * (M5) and
 * {@link com.foggyframework.dataset.db.model.engine.compose.schema.ComposeSchemaException}
 * (M4): a single exception class with a structured {@code code}/{@code phase}
 * pair. Callers discriminate on {@link #code()} rather than subclass, to
 * keep the error surface flat and to mirror the Python exception
 * {@code ComposeCompileError} 1:1.</p>
 *
 * <p>The code string is one of the 4 full {@code compose-compile-error/*}
 * strings from {@link ComposeCompileErrorCodes}; typos are rejected at
 * construction time so tests cannot silently assert the wrong code.</p>
 *
 * @since 8.2.0.beta
 */
public class ComposeCompileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String phase;

    /** @see #ComposeCompileException(String, String, String, Throwable) */
    public ComposeCompileException(String code, String phase, String message) {
        this(code, phase, message, null);
    }

    /**
     * Construct a structured compile exception.
     *
     * @param code    one of {@link ComposeCompileErrorCodes#ALL_CODES}; must be
     *                the full namespaced string (e.g.
     *                {@code "compose-compile-error/missing-binding"}), never
     *                just the {@code NAMESPACE} prefix.
     * @param phase   one of {@link ComposeCompileErrorCodes#VALID_PHASES}
     *                ({@code "plan-lower"} / {@code "compile"}).
     * @param message human-readable detail (never parsed programmatically).
     * @param cause   optional upstream exception; preserved on
     *                {@link Throwable#getCause()} for diagnostics.
     * @throws IllegalArgumentException when {@code code} or {@code phase} is
     *                                  invalid (fail-closed on typos).
     */
    public ComposeCompileException(String code, String phase, String message, Throwable cause) {
        super(validateAndFormat(code, phase, message), cause);
        this.code = code;
        this.phase = phase;
    }

    /** Validate fail-closed first, then format — avoids building the message
     *  string just to throw it away when code/phase is a typo. */
    private static String validateAndFormat(String code, String phase, String message) {
        if (!ComposeCompileErrorCodes.isValidCode(code)) {
            throw new IllegalArgumentException(
                    "Invalid ComposeCompileException code " + quote(code)
                            + "; must be one of " + ComposeCompileErrorCodes.ALL_CODES);
        }
        if (!ComposeCompileErrorCodes.isValidPhase(phase)) {
            throw new IllegalArgumentException(
                    "Invalid ComposeCompileException phase " + quote(phase)
                            + "; must be one of " + ComposeCompileErrorCodes.VALID_PHASES);
        }
        return formatMessage(code, phase, message);
    }

    /** Stable error-code discriminator (e.g.
     *  {@code "compose-compile-error/missing-binding"}). */
    public String code() { return code; }

    /** Phase at which the error was detected — {@code "plan-lower"} (before
     *  SQL generation) or {@code "compile"} (during SQL generation). */
    public String phase() { return phase; }

    private static String formatMessage(String code, String phase, String message) {
        return "[" + code + "] (" + phase + ") " + message;
    }

    private static String quote(String s) {
        return s == null ? "null" : "'" + s + "'";
    }
}

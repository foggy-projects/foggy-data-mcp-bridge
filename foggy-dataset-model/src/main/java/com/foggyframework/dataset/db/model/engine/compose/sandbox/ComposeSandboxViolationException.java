package com.foggyframework.dataset.db.model.engine.compose.sandbox;

/**
 * Structured failure for any Compose Query three-layer sandbox violation.
 *
 * <p>Fail-closed: any violation raised by the sandbox enforcement pipeline
 * (Layer A script host, Layer B DSL expression, Layer C QueryPlan verb)
 * must instantiate this class with a valid {@link #code()} and
 * {@link #phase()}. Callers propagate; they do not catch-and-continue.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.sandbox.exceptions.ComposeSandboxViolationError}.
 * Naming deliberately differs: Python uses the {@code Error} suffix
 * (module-style); Java uses {@code Exception} for {@link RuntimeException}
 * subclasses (consistent with
 * {@link com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException}).</p>
 *
 * <p>Attributes:</p>
 * <ul>
 *   <li>{@link #code()} — one of {@link ComposeSandboxErrorCodes#ALL_CODES}.
 *       Validated on construction.</li>
 *   <li>{@link #layer()} — derived from {@code code} at construction
 *       ({@code "A"} / {@code "B"} / {@code "C"}); kept as a first-class
 *       attribute for ergonomic test assertions so callers do not have to
 *       reparse the code string.</li>
 *   <li>{@link #kind()} — trailing kind segment of {@code code}
 *       (e.g. {@code "eval-denied"}), also derived at construction.</li>
 *   <li>{@link #phase()} — pipeline phase; validated against
 *       {@link ComposeSandboxErrorCodes#VALID_PHASES}.</li>
 *   <li>{@link #scriptLocation()} — optional {@code "line:column"}
 *       pointing to the offending source position. Not required; set when
 *       the host can produce it cheaply. Pass {@code null} when unknown.</li>
 * </ul>
 *
 * <p><b>Sanitisation.</b> Error messages must not embed raw QM physical
 * column names, raw {@code ir.rule.domain_force} text, other users'
 * identifiers, or verbatim snippets of the user script beyond what's
 * needed to disambiguate. Keep {@link #getMessage()} developer-facing
 * and redacted.</p>
 *
 * @since 8.2.0.beta
 */
public class ComposeSandboxViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String layer;          // derived from code — "A" | "B" | "C"
    private final String kind;           // derived from code — e.g. "eval-denied"
    private final String phase;
    private final String scriptLocation; // nullable

    public ComposeSandboxViolationException(String code, String message, String phase) {
        this(code, message, phase, null, null);
    }

    public ComposeSandboxViolationException(String code, String message,
                                            String phase, String scriptLocation) {
        this(code, message, phase, scriptLocation, null);
    }

    public ComposeSandboxViolationException(String code, String message,
                                            String phase, String scriptLocation,
                                            Throwable cause) {
        super(message, cause);

        if (code == null || !ComposeSandboxErrorCodes.ALL_CODES.contains(code)) {
            throw new IllegalArgumentException(
                    "ComposeSandboxViolationException.code must be one of "
                            + "ComposeSandboxErrorCodes.ALL_CODES, got: " + code);
        }
        if (phase == null || !ComposeSandboxErrorCodes.VALID_PHASES.contains(phase)) {
            throw new IllegalArgumentException(
                    "ComposeSandboxViolationException.phase must be one of "
                            + "ComposeSandboxErrorCodes.VALID_PHASES, got: " + phase);
        }

        this.code = code;
        // Derived once — callers do not have to reparse the code string.
        this.layer = ComposeSandboxErrorCodes.layerOf(code);
        this.kind = ComposeSandboxErrorCodes.kindOf(code);
        this.phase = phase;
        this.scriptLocation = scriptLocation;
    }

    public String code() { return code; }
    public String layer() { return layer; }
    public String kind() { return kind; }
    public String phase() { return phase; }
    public String scriptLocation() { return scriptLocation; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ComposeSandboxViolationException{code=")
                .append(code)
                .append(", layer=").append(layer)
                .append(", kind=").append(kind)
                .append(", phase=").append(phase);
        if (scriptLocation != null) {
            sb.append(", scriptLocation=").append(scriptLocation);
        }
        sb.append(", message=").append(getMessage()).append('}');
        return sb.toString();
    }
}

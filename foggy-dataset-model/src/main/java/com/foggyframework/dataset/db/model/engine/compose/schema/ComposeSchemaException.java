package com.foggyframework.dataset.db.model.engine.compose.schema;

/**
 * Structured failure for schema derivation.
 *
 * <p>Fail-closed: any violation of the M4 structural contract (duplicate
 * output names, unknown fields, union column-count mismatch, join on
 * unknown fields, join output column conflicts, malformed column specs)
 * must raise {@code ComposeSchemaException}. Callers propagate; they do
 * not fall back to a partial schema.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.schema.errors.ComposeSchemaError}.
 * Naming deliberately differs: Python uses the {@code Error} suffix
 * (module-style); Java uses {@code Exception} for {@link RuntimeException}
 * subclasses (consistent with
 * {@link com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException}).</p>
 *
 * <p>Attributes:
 * <ul>
 *   <li>{@link #code()} — one of {@link ComposeSchemaErrorCodes#ALL_CODES}.
 *       Validated on construction.</li>
 *   <li>{@link #phase()} — {@code plan-build} or {@code schema-derive}.
 *       Validated against {@link ComposeSchemaErrorCodes#VALID_PHASES}.</li>
 *   <li>{@link #planPath()} — optional human-readable path into the plan
 *       tree (e.g. {@code "union/left/query/2"}).</li>
 *   <li>{@link #offendingField()} — optional field / alias / model name
 *       that caused the failure.</li>
 * </ul>
 *
 * <p><b>Sanitisation.</b> Like other compose errors, messages must not
 * embed raw QM physical column names or {@code ir.rule.domain_force}
 * text. Schema derivation runs <i>before</i> authority binding, so in
 * practice only user-written aliases / model names / column references
 * appear here — but callers should still keep messages developer-facing
 * and concise.</p>
 *
 * @since 8.2.0.beta
 */
public class ComposeSchemaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final String phase;
    private final String planPath;        // nullable
    private final String offendingField;  // nullable

    public ComposeSchemaException(String code, String message,
                                  String phase, String planPath,
                                  String offendingField) {
        this(code, message, phase, planPath, offendingField, null);
    }

    public ComposeSchemaException(String code, String message,
                                  String phase, String planPath,
                                  String offendingField, Throwable cause) {
        super(message, cause);

        if (code == null || !ComposeSchemaErrorCodes.ALL_CODES.contains(code)) {
            throw new IllegalArgumentException(
                    "ComposeSchemaException.code must be one of "
                            + "ComposeSchemaErrorCodes.ALL_CODES, got: " + code);
        }
        if (phase == null || !ComposeSchemaErrorCodes.VALID_PHASES.contains(phase)) {
            throw new IllegalArgumentException(
                    "ComposeSchemaException.phase must be one of "
                            + "ComposeSchemaErrorCodes.VALID_PHASES, got: " + phase);
        }

        this.code = code;
        this.phase = phase;
        this.planPath = planPath;
        this.offendingField = offendingField;
    }

    /** Convenience constructor defaulting phase to {@code schema-derive}. */
    public ComposeSchemaException(String code, String message) {
        this(code, message, ComposeSchemaErrorCodes.PHASE_SCHEMA_DERIVE, null, null, null);
    }

    public String code() { return code; }
    public String phase() { return phase; }
    public String planPath() { return planPath; }
    public String offendingField() { return offendingField; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ComposeSchemaException{code=")
                .append(code)
                .append(", phase=").append(phase);
        if (planPath != null) {
            sb.append(", planPath=").append(planPath);
        }
        if (offendingField != null) {
            sb.append(", offendingField=").append(offendingField);
        }
        sb.append(", message=").append(getMessage()).append('}');
        return sb.toString();
    }
}

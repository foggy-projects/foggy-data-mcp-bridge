package com.foggyframework.dataset.db.model.engine.compose.security;

import java.util.Set;

/**
 * Frozen error-code constants for Compose Query authority resolution.
 *
 * <p><b>Cross-language parity contract.</b> Every string constant here must
 * match the Python {@code foggy.dataset_model.engine.compose.security.error_codes}
 * module byte-for-byte. A mirror test on the Python side asserts the same
 * seven codes exist; drift is a cross-repo regression.</p>
 *
 * <p>Extensions allowed (new codes may be appended). Renames and removals
 * are SemVer breakage and require an SPI-version bump.</p>
 *
 * @since 8.2.0.beta
 */
public final class AuthorityErrorCodes {

    private AuthorityErrorCodes() { /* utility */ }

    /** Top-level error namespace. Included here so tests can assert every
     *  concrete code is properly prefixed. */
    public static final String NAMESPACE = "compose-authority-resolve";

    // ------------------------------------------------------------------
    // Seven frozen codes (M1 contract)
    // ------------------------------------------------------------------

    public static final String RESOLVER_NOT_AVAILABLE = NAMESPACE + "/resolver-not-available";
    public static final String MODEL_BINDING_MISSING  = NAMESPACE + "/model-binding-missing";
    public static final String MODEL_NOT_MAPPED       = NAMESPACE + "/model-not-mapped";
    public static final String PRINCIPAL_MISMATCH     = NAMESPACE + "/principal-mismatch";
    public static final String UPSTREAM_FAILURE       = NAMESPACE + "/upstream-failure";
    public static final String INVALID_RESPONSE      = NAMESPACE + "/invalid-response";
    public static final String IR_RULE_UNMAPPED_FIELD = NAMESPACE + "/ir-rule-unmapped-field";

    public static final Set<String> ALL_CODES = Set.of(
            RESOLVER_NOT_AVAILABLE,
            MODEL_BINDING_MISSING,
            MODEL_NOT_MAPPED,
            PRINCIPAL_MISMATCH,
            UPSTREAM_FAILURE,
            INVALID_RESPONSE,
            IR_RULE_UNMAPPED_FIELD
    );

    // ------------------------------------------------------------------
    // Phase enum (strings, to mirror Python)
    // ------------------------------------------------------------------

    public static final String PHASE_AUTHORITY_RESOLVE = "authority-resolve";
    public static final String PHASE_SCHEMA_DERIVE     = "schema-derive";
    public static final String PHASE_COMPILE           = "compile";
    public static final String PHASE_EXECUTE           = "execute";

    /** All phases legal for AuthorityResolutionException, including the
     *  sandbox-adjacent phases shared with the broader compose pipeline. */
    public static final Set<String> VALID_PHASES = Set.of(
            PHASE_AUTHORITY_RESOLVE,
            PHASE_SCHEMA_DERIVE,
            PHASE_COMPILE,
            PHASE_EXECUTE,
            "script-parse",
            "script-eval",
            "plan-build"
    );
}

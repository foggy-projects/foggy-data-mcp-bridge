package com.foggyframework.dataset.db.model.engine.compose.sandbox;

import java.util.Set;

/**
 * Frozen error codes for Compose Query sandbox violations.
 *
 * <p>Three layers:</p>
 * <ul>
 *   <li><b>Layer A</b> — script host (JavaScript / fsscript surface)</li>
 *   <li><b>Layer B</b> — DSL expression (FSScript function whitelist)</li>
 *   <li><b>Layer C</b> — QueryPlan verb whitelist</li>
 * </ul>
 *
 * <p><b>Cross-language parity contract.</b> Every string constant here must
 * match the Python {@code foggy.dataset_model.engine.compose.sandbox.error_codes}
 * module byte-for-byte. Code names align with the M9 scaffold document
 * (see {@code docs/8.2.0.beta/M9-三层沙箱防护测试脚手架.md § 错误码定义}).</p>
 *
 * <p><b>Extensions</b>: append-only. Renaming / removing requires an SPI
 * version bump of the compose sandbox surface.</p>
 *
 * <p><b>Scope</b>: this class only declares the <i>error contract</i> (strings,
 * phase set, layer-of / kind-of helpers). The static AST validators / runtime
 * enforcement pipeline for Layer A/B/C are landed in M9; M3 delivers only
 * the contract so sandboxed components can raise
 * {@link ComposeSandboxViolationException} today and wire the concrete
 * checks later.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeSandboxErrorCodes {

    private ComposeSandboxErrorCodes() { /* utility */ }

    /** Top-level error namespace. */
    public static final String NAMESPACE = "compose-sandbox-violation";

    // ------------------------------------------------------------------
    // Layer A — 脚本宿主层 (8 codes)
    // ------------------------------------------------------------------

    public static final String LAYER_A_EVAL_DENIED =
            NAMESPACE + "/A/eval-denied";
    public static final String LAYER_A_ASYNC_DENIED =
            NAMESPACE + "/A/async-denied";
    public static final String LAYER_A_NETWORK_DENIED =
            NAMESPACE + "/A/network-denied";
    public static final String LAYER_A_IO_DENIED =
            NAMESPACE + "/A/io-denied";
    public static final String LAYER_A_GLOBAL_DENIED =
            NAMESPACE + "/A/global-denied";
    public static final String LAYER_A_TIME_DENIED =
            NAMESPACE + "/A/time-denied";
    public static final String LAYER_A_SECURITY_PARAM =
            NAMESPACE + "/A/security-param-denied";
    public static final String LAYER_A_CONTEXT_ACCESS =
            NAMESPACE + "/A/context-access-denied";

    // ------------------------------------------------------------------
    // Layer B — DSL 表达式层 (3 codes)
    // ------------------------------------------------------------------

    public static final String LAYER_B_FUNCTION_DENIED =
            NAMESPACE + "/B/function-denied";
    public static final String LAYER_B_DERIVED_FN_DENIED =
            NAMESPACE + "/B/derived-plan-function-denied";
    public static final String LAYER_B_INJECTION_SUSPECTED =
            NAMESPACE + "/B/injection-suspected";

    // ------------------------------------------------------------------
    // Layer C — Plan 动词白名单 (3 codes)
    // ------------------------------------------------------------------

    public static final String LAYER_C_METHOD_DENIED =
            NAMESPACE + "/C/method-denied";
    public static final String LAYER_C_RESULT_ITERATION =
            NAMESPACE + "/C/result-iteration-denied";
    public static final String LAYER_C_CROSS_DS =
            NAMESPACE + "/C/cross-datasource-denied";

    public static final Set<String> ALL_CODES = Set.of(
            // Layer A (8)
            LAYER_A_EVAL_DENIED,
            LAYER_A_ASYNC_DENIED,
            LAYER_A_NETWORK_DENIED,
            LAYER_A_IO_DENIED,
            LAYER_A_GLOBAL_DENIED,
            LAYER_A_TIME_DENIED,
            LAYER_A_SECURITY_PARAM,
            LAYER_A_CONTEXT_ACCESS,
            // Layer B (3)
            LAYER_B_FUNCTION_DENIED,
            LAYER_B_DERIVED_FN_DENIED,
            LAYER_B_INJECTION_SUSPECTED,
            // Layer C (3)
            LAYER_C_METHOD_DENIED,
            LAYER_C_RESULT_ITERATION,
            LAYER_C_CROSS_DS
    );

    // ------------------------------------------------------------------
    // Phase enumeration (string-typed; avoids a Phase enum to keep wire
    // format parity with Python where phases are plain strings)
    // ------------------------------------------------------------------

    public static final String PHASE_SCRIPT_PARSE = "script-parse";
    public static final String PHASE_SCRIPT_EVAL = "script-eval";
    public static final String PHASE_PLAN_BUILD = "plan-build";
    public static final String PHASE_SCHEMA_DERIVE = "schema-derive";
    public static final String PHASE_AUTHORITY_RESOLVE = "authority-resolve";
    public static final String PHASE_COMPILE = "compile";
    public static final String PHASE_EXECUTE = "execute";

    public static final Set<String> VALID_PHASES = Set.of(
            PHASE_SCRIPT_PARSE,
            PHASE_SCRIPT_EVAL,
            PHASE_PLAN_BUILD,
            PHASE_SCHEMA_DERIVE,
            PHASE_AUTHORITY_RESOLVE,
            PHASE_COMPILE,
            PHASE_EXECUTE
    );

    // ------------------------------------------------------------------
    // Layer ↔ code mapping (for validator / reflection helpers)
    // ------------------------------------------------------------------

    public static final String LAYER_PREFIX_A = NAMESPACE + "/A/";
    public static final String LAYER_PREFIX_B = NAMESPACE + "/B/";
    public static final String LAYER_PREFIX_C = NAMESPACE + "/C/";

    /**
     * Return the single-letter layer (<code>"A"</code> / <code>"B"</code>
     * / <code>"C"</code>) for a given code string.
     *
     * @throws IllegalArgumentException if {@code code} does not belong to
     *         a known sandbox layer (matches Python's {@code ValueError}
     *         semantics — fail-closed on unknown codes).
     */
    public static String layerOf(String code) {
        if (code == null) {
            throw new IllegalArgumentException(
                    "code must not be null");
        }
        if (code.startsWith(LAYER_PREFIX_A)) {
            return "A";
        }
        if (code.startsWith(LAYER_PREFIX_B)) {
            return "B";
        }
        if (code.startsWith(LAYER_PREFIX_C)) {
            return "C";
        }
        throw new IllegalArgumentException(
                "code '" + code + "' does not belong to any "
                        + "compose-sandbox-violation layer");
    }

    /**
     * Return the trailing <code>kind</code> segment of a code
     * (e.g. {@code "eval-denied"} for {@link #LAYER_A_EVAL_DENIED}).
     *
     * @throws IllegalArgumentException if {@code code} does not belong to
     *         a known sandbox layer.
     */
    public static String kindOf(String code) {
        String layer = layerOf(code);
        String prefix = NAMESPACE + "/" + layer + "/";
        return code.substring(prefix.length());
    }
}

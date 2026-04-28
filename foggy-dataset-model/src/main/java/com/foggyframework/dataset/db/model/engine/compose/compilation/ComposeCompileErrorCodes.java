package com.foggyframework.dataset.db.model.engine.compose.compilation;

import java.util.Set;

/**
 * Error-code constants for Compose Query SQL compilation (M6).
 *
 * <p>r3 shape: <b>4 codes + 1 NAMESPACE constant</b>. Tests assert the
 * namespace separately from the 4 full code strings.</p>
 *
 * <p>Phase labels carried by {@link ComposeCompileException#phase()}:
 * <ul>
 *   <li>{@value #PHASE_PLAN_LOWER} — structural validation before SQL
 *       generation (depth / unknown dialect / full-outer-on-sqlite / unknown
 *       QueryPlan subclass).</li>
 *   <li>{@value #PHASE_COMPILE} — SQL generation through the v1.3
 *       {@code generateSql} path.</li>
 * </ul></p>
 *
 * <p>These error codes intentionally do NOT overlap with
 * {@code compose-sandbox-violation/*} (M3),
 * {@code compose-schema-error/*} (M4), or
 * {@code compose-authority-resolve/*} (M1/M5). Errors from those phases
 * are propagated untouched by M6 — see spec §错误模型规划.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.compilation.error_codes}
 * byte-for-byte.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeCompileErrorCodes {

    private ComposeCompileErrorCodes() { /* utility */ }

    /** Top-level namespace shared by every M6 compile error code.
     *  This is NOT itself a usable code — {@link ComposeCompileException#code()}
     *  always carries one of the 4 full strings below. */
    public static final String NAMESPACE = "compose-compile-error";

    // ---------- 4 error codes — full {@code <namespace>/<kind>} strings ----------

    /** Plan shape this milestone does not compile. Examples:
     *  <ul>
     *    <li>{@code JoinPlan(type=FULL)} against SQLite</li>
     *    <li>Nested derivation depth &gt; {@link PlanHash#MAX_PLAN_DEPTH} (DOS guard)</li>
     *    <li>A plan node whose {@code QueryPlan} subclass is unrecognised</li>
     *    <li>Unknown dialect string</li>
     *  </ul> */
    public static final String UNSUPPORTED_PLAN_SHAPE =
            "compose-compile-error/unsupported-plan-shape";

    /** Union / join operands come from different data sources. The compiler
     *  raises this code when the {@code datasourceIds} map (resolved via
     *  {@link com.foggyframework.dataset.db.model.engine.compose.authority.DatasourceIdCollector})
     *  contains two or more distinct non-empty datasource identities among
     *  the plan's leaf models.
     *
     *  <p>Cross-repo invariant: mirrors Python
     *  {@code compose-compile-error/cross-datasource-rejected} raised by
     *  {@code _check_cross_datasource} in the Python compose planner.</p> */
    public static final String CROSS_DATASOURCE_REJECTED =
            "compose-compile-error/cross-datasource-rejected";

    /** An authority {@code ModelBinding} was expected for a
     *  {@code BaseModelPlan.model()} but the {@code bindings} map does not
     *  contain that key. In normal flow the M5 resolver would have already
     *  failed with its own {@code compose-authority-resolve/model-binding-missing}
     *  code; reaching this branch means either the caller passed a
     *  hand-rolled {@code bindings=...} with gaps, or the QM was not
     *  registered with the semantic service. */
    public static final String MISSING_BINDING =
            "compose-compile-error/missing-binding";

    /** {@code SemanticQueryServiceV3.generateSql} raised while compiling a
     *  {@code BaseModelPlan}. The original exception is kept on
     *  {@link Throwable#getCause()}; the M6 wrapper only adds enough context
     *  (model name) to route the error without forcing callers to parse
     *  v1.3 engine stacks. */
    public static final String PER_BASE_COMPILE_FAILED =
            "compose-compile-error/per-base-compile-failed";

    // ---------- phases ----------

    /** Structural validation phase (before any SQL is generated). */
    public static final String PHASE_PLAN_LOWER = "plan-lower";

    /** SQL generation phase (through v1.3 engine). */
    public static final String PHASE_COMPILE = "compile";

    // ---------- public registries ----------

    /** Immutable set of the 4 full error-code strings.
     *  Tests assert {@code size == 4} ({@link #NAMESPACE} is excluded by design). */
    public static final Set<String> ALL_CODES = Set.of(
            UNSUPPORTED_PLAN_SHAPE,
            CROSS_DATASOURCE_REJECTED,
            MISSING_BINDING,
            PER_BASE_COMPILE_FAILED);

    /** Valid phase labels carried by {@link ComposeCompileException#phase()}. */
    public static final Set<String> VALID_PHASES = Set.of(
            PHASE_PLAN_LOWER, PHASE_COMPILE);

    /** Return {@code true} iff {@code code} is one of the 4 registered compile codes.
     *  Used by {@link ComposeCompileException#ComposeCompileException(String, String, String, Throwable)}
     *  to fail-closed on typos. */
    public static boolean isValidCode(String code) {
        return code != null && ALL_CODES.contains(code);
    }

    /** Return {@code true} iff {@code phase} is one of the 2 registered phase labels. */
    public static boolean isValidPhase(String phase) {
        return phase != null && VALID_PHASES.contains(phase);
    }
}

/**
 * Compose Query SQL compilation subpackage (8.2.0.beta M6).
 *
 * <h2>Public API</h2>
 * <ul>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.compilation.ComposeSqlCompiler}
 *       — single entry-point; {@code QueryPlan} → {@code ComposedSql}.</li>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.compilation.ComposeCompileException}
 *       — structured error type; 4 codes total.</li>
 *   <li>{@link com.foggyframework.dataset.model.engine.compose.compilation.ComposeCompileErrorCodes}
 *       — error-code constants + {@code NAMESPACE}.</li>
 * </ul>
 *
 * <p>All other names ({@code PerBaseCompiler}, {@code ComposePlanner},
 * {@code PlanHash}) are implementation details and should not be depended
 * on by downstream code.</p>
 *
 * <h2>Cross-repo invariant</h2>
 * <p>Mirrors Python
 * {@code foggy.dataset_model.engine.compose.compilation} byte-for-byte on
 * error codes, fail-closed branches, and constants (e.g.
 * {@code MAX_PLAN_DEPTH = 32}). Python is the source of truth — see the
 * {@code docs/8.2.0.beta/M6-SQLCompilation-Java-execution-prompt.md}
 * prompt (ready-to-execute).</p>
 *
 * <h2>v1.3 reuse</h2>
 * <p>{@code fieldAccess} / {@code deniedColumns} / {@code systemSlice} are
 * injected into each per-base request through
 * {@link com.foggyframework.dataset.model.semantic.port.ComposeSemanticPlanningPort#generateComposeSql}
 * — M6 does not re-implement the governance steps. v1.3's
 * {@code beforeQuery} pipeline
 * ({@code PhysicalColumnPermissionStep} / {@code FieldAccessPermissionStep})
 * runs automatically when SQL is built.</p>
 *
 * @since 8.2.0.beta
 */
package com.foggyframework.dataset.model.engine.compose.compilation;

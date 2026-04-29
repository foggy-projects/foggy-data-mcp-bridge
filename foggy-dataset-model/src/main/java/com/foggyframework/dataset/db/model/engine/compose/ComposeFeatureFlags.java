package com.foggyframework.dataset.db.model.engine.compose;

/**
 * Feature flag dispatch for Compose Query engine evolutions.
 *
 * <p>Centralises every "is this evolution enabled?" decision so feature
 * code never reads system properties / Spring configuration directly. Tests
 * can flip flags via {@link #overrideG10Enabled(Boolean)} without
 * recreating Spring contexts; the override clears with
 * {@code overrideG10Enabled(null)}.</p>
 *
 * <h3>G10 — plan-aware engine refactor</h3>
 *
 * Property: {@code foggy.compose.g10.enabled} — default {@code false}.
 *
 * <p>When true:
 * <ul>
 *   <li>{@code SchemaDerivation.deriveJoin} marks overlapping columns
 *       {@code isAmbiguous=true} + sets {@code planProvenance} instead of
 *       throwing {@code JOIN_OUTPUT_COLUMN_CONFLICT}.</li>
 *   <li>{@code OutputSchema} accepts ambiguous duplicates; {@code get(name)}
 *       fails fast on ambiguity (callers use {@code requireUnique} / {@code getAll}).</li>
 *   <li>{@code ComposePlanner} compiles {@code PlanColumnRef} via plan-alias
 *       routing (PR3, follow-up).</li>
 *   <li>Compose plan-aware permission validator activates per-plan
 *       (PR4, follow-up).</li>
 * </ul>
 *
 * <p>When false (default during PR2 rollout):
 * <ul>
 *   <li>{@code deriveJoin} preserves the legacy {@code JOIN_OUTPUT_COLUMN_CONFLICT}
 *       throw on column overlap.</li>
 *   <li>{@code OutputSchema} rejects all duplicates as today.</li>
 * </ul>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.feature_flags}.</p>
 *
 * @since 8.3.0.beta
 */
public final class ComposeFeatureFlags {

    private ComposeFeatureFlags() { /* utility */ }

    /** System property name for the G10 flag. */
    public static final String G10_PROPERTY = "foggy.compose.g10.enabled";

    /** Environment variable name (uppercase, dotted → underscored). */
    public static final String G10_ENV_VAR = "FOGGY_COMPOSE_G10_ENABLED";

    /**
     * Test override slot. {@code null} means "no override — read from
     * system property / env"; non-null forces that value.
     *
     * <p>volatile so test threads observe writes without synchronisation.</p>
     */
    private static volatile Boolean g10Override = null;

    /**
     * Returns whether the G10 feature flag is enabled.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Test override (if non-null);</li>
     *   <li>System property {@code foggy.compose.g10.enabled};</li>
     *   <li>Environment variable {@code FOGGY_COMPOSE_G10_ENABLED};</li>
     *   <li>Default {@code false}.</li>
     * </ol>
     */
    public static boolean g10Enabled() {
        Boolean override = g10Override;
        if (override != null) {
            return override;
        }
        Boolean prop = parseBool(System.getProperty(G10_PROPERTY));
        if (prop != null) {
            return prop;
        }
        Boolean env = parseBool(System.getenv(G10_ENV_VAR));
        return env != null && env;
    }

    /** {@code null} when {@code raw} is {@code null}; otherwise
     *  {@code Boolean.parseBoolean(raw.trim())}. */
    private static Boolean parseBool(String raw) {
        return raw == null ? null : Boolean.parseBoolean(raw.trim());
    }

    /**
     * Test-only: pin the G10 flag to a specific value. Pass {@code null}
     * to clear the override (production property/env resolution resumes).
     *
     * <p>Always pair with a {@code try / finally} — leaking an override
     * across test classes corrupts the matrix.</p>
     */
    public static void overrideG10Enabled(Boolean value) {
        g10Override = value;
    }
}

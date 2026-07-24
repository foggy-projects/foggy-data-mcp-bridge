package com.foggyframework.dataset.model.engine.compose.plan;

/**
 * Marker exception raised by {@link QueryPlan#execute(
 * com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext)}
 * and {@link QueryPlan#toSql()} in 8.2.0.beta M2.
 *
 * <p>M2 delivers the {@code QueryPlan} object model only — the SQL compiler
 * lands in M6 and the script runner (which threads a
 * {@link com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext}
 * through {@code execute()}) lands in M7. Until then, any attempt to execute
 * a plan or produce SQL raises this exception.</p>
 *
 * <p>Cross-repo invariant: matches Python
 * {@code foggy.dataset_model.engine.compose.plan.UnsupportedInM2Error}
 * (which extends {@code NotImplementedError}). The Java shape intentionally
 * extends {@link RuntimeException} so callers do not need checked-exception
 * declarations during the M2 window.</p>
 *
 * @since 8.2.0.beta
 */
public class UnsupportedInM2Exception extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnsupportedInM2Exception(String message) {
        super(message);
    }

    public UnsupportedInM2Exception(String message, Throwable cause) {
        super(message, cause);
    }
}

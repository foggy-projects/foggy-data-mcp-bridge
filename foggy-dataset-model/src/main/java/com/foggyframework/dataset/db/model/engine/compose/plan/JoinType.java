package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.Locale;

/**
 * Join type enum for {@link JoinPlan}.
 *
 * <p>Mirrors Python's {@code "inner" | "left" | "right" | "full"} string
 * vocabulary. The public chain-sugar entry points ({@link QueryPlan#join})
 * accept either a {@link JoinType} instance or a case-insensitive string
 * (normalised via {@link #fromString(String)}).</p>
 *
 * <p>Cross joins are intentionally NOT represented — M2's
 * {@link JoinPlan} requires a non-empty {@code on} list, mirroring the
 * spec's position that cross joins are deferred to a later milestone.</p>
 *
 * @since 8.2.0.beta
 */
public enum JoinType {
    INNER,
    LEFT,
    RIGHT,
    FULL;

    /** Lower-case token used in logs, SQL emission, and cross-repo parity
     *  with Python (which stores {@code "inner" / "left" / "right" / "full"}
     *  as plain strings). */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Upper-case SQL keyword used by the M6 compiler when assembling
     *  {@code ... INNER/LEFT/RIGHT/FULL OUTER JOIN ...} fragments. */
    public String sqlKeyword() {
        switch (this) {
            case INNER: return "INNER";
            case LEFT:  return "LEFT";
            case RIGHT: return "RIGHT";
            case FULL:  return "FULL OUTER";
            default:    throw new IllegalStateException("Unknown JoinType " + this);
        }
    }

    /**
     * Normalise a raw string to a {@link JoinType}. Whitespace is trimmed
     * and casing is ignored. Unknown tokens raise
     * {@link IllegalArgumentException}.
     */
    public static JoinType fromString(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException(
                    "JoinType.fromString: input must not be null");
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        switch (norm) {
            case "inner": return INNER;
            case "left":  return LEFT;
            case "right": return RIGHT;
            case "full":  return FULL;
            default:
                throw new IllegalArgumentException(
                        "JoinType must be one of [inner, left, right, full], got: "
                                + raw);
        }
    }
}

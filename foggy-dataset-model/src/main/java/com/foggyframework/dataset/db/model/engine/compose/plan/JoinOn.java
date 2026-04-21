package com.foggyframework.dataset.db.model.engine.compose.plan;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One {@code ON} predicate inside a {@link JoinPlan}.
 *
 * <p>Shape matches the spec examples literally:
 * <pre>{@code
 *   {"left": "partnerId", "op": "=", "right": "partnerId"}
 * }</pre>
 *
 * <p>M2 accepts only the equality-family operators — the same 6-op set the
 * Python side locks in ({@code =, !=, <, >, <=, >=}). Richer predicates
 * ({@code IN}, {@code BETWEEN}, {@code IS NULL}) are deferred because they
 * introduce compile-time-vs-runtime null-handling decisions that the M6
 * SQL compiler owns.</p>
 *
 * <p>Cross-repo invariant: mirrors Python
 * {@code foggy.dataset_model.engine.compose.plan.JoinOn}.</p>
 *
 * @since 8.2.0.beta
 */
public final class JoinOn {

    public static final Set<String> ALLOWED_OPS =
            Set.of("=", "!=", "<", ">", "<=", ">=");

    private final String left;
    private final String op;
    private final String right;

    private JoinOn(Builder b) {
        if (b.left == null || b.left.isEmpty()) {
            throw new IllegalArgumentException("JoinOn.left must be non-empty");
        }
        if (b.right == null || b.right.isEmpty()) {
            throw new IllegalArgumentException("JoinOn.right must be non-empty");
        }
        if (b.op == null || !ALLOWED_OPS.contains(b.op)) {
            throw new IllegalArgumentException(
                    "JoinOn.op must be one of " + ALLOWED_OPS + ", got: " + b.op);
        }
        this.left = b.left;
        this.op = b.op;
        this.right = b.right;
    }

    public String left() { return left; }
    public String op() { return op; }
    public String right() { return right; }

    public static Builder builder() { return new Builder(); }

    /** Convenience factory for test-style construction. */
    public static JoinOn of(String left, String op, String right) {
        return builder().left(left).op(op).right(right).build();
    }

    /**
     * Coerce a script-style map ({@code {"left": ..., "op": ..., "right": ...}})
     * into a {@link JoinOn}. Missing keys produce {@link IllegalArgumentException}.
     * Used by {@link QueryPlan#join} when scripts pass plain maps.
     */
    public static JoinOn fromMap(Map<String, ?> map) {
        if (map == null) {
            throw new IllegalArgumentException(
                    "JoinOn.fromMap: map must not be null");
        }
        Object rawLeft = map.get("left");
        Object rawOp = map.get("op");
        Object rawRight = map.get("right");
        if (rawLeft == null || rawOp == null || rawRight == null) {
            throw new IllegalArgumentException(
                    "JoinOn map missing required keys; required: left, op, right; got: "
                            + map.keySet());
        }
        if (!(rawLeft instanceof String) || !(rawOp instanceof String)
                || !(rawRight instanceof String)) {
            throw new IllegalArgumentException(
                    "JoinOn map entries must be strings; got: left="
                            + typeOf(rawLeft) + ", op=" + typeOf(rawOp)
                            + ", right=" + typeOf(rawRight));
        }
        return of((String) rawLeft, (String) rawOp, (String) rawRight);
    }

    private static String typeOf(Object o) {
        return o == null ? "null" : o.getClass().getSimpleName();
    }

    public static final class Builder {
        private String left;
        private String op;
        private String right;

        public Builder left(String v) { this.left = v; return this; }
        public Builder op(String v) { this.op = v; return this; }
        public Builder right(String v) { this.right = v; return this; }

        public JoinOn build() { return new JoinOn(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JoinOn)) return false;
        JoinOn j = (JoinOn) o;
        return Objects.equals(left, j.left)
                && Objects.equals(op, j.op)
                && Objects.equals(right, j.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, op, right);
    }

    @Override
    public String toString() {
        return "JoinOn{" + left + " " + op + " " + right + "}";
    }
}

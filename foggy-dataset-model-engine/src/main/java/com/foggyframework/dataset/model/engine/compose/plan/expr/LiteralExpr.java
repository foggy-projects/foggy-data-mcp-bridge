package com.foggyframework.dataset.model.engine.compose.plan.expr;

import java.util.Objects;

/**
 * A literal value expression.
 *
 * @since 8.2.0.beta
 */
public final class LiteralExpr implements PlanExpression {
    private final Object value;
    private final boolean approximate;

    public LiteralExpr(Object value) {
        this(value, false);
    }

    private LiteralExpr(Object value, boolean approximate) {
        this.value = value;
        this.approximate = approximate;
    }

    public Object value() { return value; }
    public boolean approximate() { return approximate; }

    /** Explicit SQL approximate number, for ratios that must not inherit aggregate DECIMAL scale. */
    public static LiteralExpr approximate(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite SQL literal");
        return new LiteralExpr(value, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LiteralExpr that)) return false;
        return approximate == that.approximate && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return approximate ? Objects.hash(value, true) : Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return (approximate ? "ApproximateLiteralExpr(" : "LiteralExpr(") + value + ")";
    }
}

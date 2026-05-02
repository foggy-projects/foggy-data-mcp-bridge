package com.foggyframework.dataset.db.model.engine.compose.plan.expr;

import java.util.Objects;

/**
 * A literal value expression.
 *
 * @since 8.2.0.beta
 */
public final class LiteralExpr implements PlanExpression {
    private final Object value;

    public LiteralExpr(Object value) {
        this.value = value;
    }

    public Object value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LiteralExpr that)) return false;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "LiteralExpr(" + value + ")";
    }
}

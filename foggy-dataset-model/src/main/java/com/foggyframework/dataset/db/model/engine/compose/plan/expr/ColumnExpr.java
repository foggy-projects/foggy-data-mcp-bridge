package com.foggyframework.dataset.db.model.engine.compose.plan.expr;

import java.util.Objects;

/**
 * A direct reference to a column name in an expression context.
 *
 * @since 8.2.0.beta
 */
public final class ColumnExpr implements PlanExpression {
    private final String name;

    public ColumnExpr(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("ColumnExpr name cannot be empty");
        }
        this.name = name;
    }

    public String name() { return name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColumnExpr that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return "ColumnExpr(" + name + ")";
    }
}

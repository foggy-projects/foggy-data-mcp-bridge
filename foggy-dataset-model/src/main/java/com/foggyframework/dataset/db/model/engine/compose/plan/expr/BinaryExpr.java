package com.foggyframework.dataset.db.model.engine.compose.plan.expr;

import java.util.Objects;

/**
 * A binary expression node.
 *
 * @since 8.2.0.beta
 */
public final class BinaryExpr implements PlanExpression {
    private final PlanExpression left;
    private final String op;
    private final PlanExpression right;

    public BinaryExpr(PlanExpression left, String op, PlanExpression right) {
        if (left == null || op == null || right == null) {
            throw new IllegalArgumentException("BinaryExpr requires left, op, and right to be non-null");
        }
        this.left = left;
        this.op = op;
        this.right = right;
    }

    public PlanExpression left() { return left; }
    public String op() { return op; }
    public PlanExpression right() { return right; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BinaryExpr that)) return false;
        return Objects.equals(left, that.left) &&
               Objects.equals(op, that.op) &&
               Objects.equals(right, that.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, op, right);
    }

    @Override
    public String toString() {
        return "BinaryExpr(" + left + " " + op + " " + right + ")";
    }
}

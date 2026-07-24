package com.foggyframework.dataset.model.engine.compose.plan.expr;

/**
 * A raw SQL expression string that is passed through to the SQL compiler verbatim.
 * <p>
 * Used for post-calculatedFields in timeWindow context, where the expression
 * references timeWindow output columns (e.g. {@code "salesAmount__ratio * 100"}).
 * The expression is already validated to only reference available output columns.
 *
 * @since 8.5.0.beta
 */
public record RawExpr(String expression) implements PlanExpression {

    public RawExpr {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("RawExpr expression must not be null or blank");
        }
    }

    @Override
    public String toString() {
        return expression;
    }
}

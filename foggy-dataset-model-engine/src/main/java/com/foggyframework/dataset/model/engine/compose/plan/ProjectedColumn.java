package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.plan.expr.PlanExpression;

/**
 * A projected column with alias and optional caption.
 *
 * <p>Created via {@code fieldRef.as("alias", "caption")} or
 * {@code aggregateCol.as("alias")}. Used in {@code .select()} to define
 * the output schema of a new relation stage.</p>
 *
 * @since 8.2.0.beta
 */
public final class ProjectedColumn implements PlanExpression {

    private final PlanExpression expr;
    private final String alias;
    private final String caption;

    public ProjectedColumn(PlanExpression expr, String alias, String caption) {
        if (expr == null) {
            throw new IllegalArgumentException("ProjectedColumn expr cannot be null");
        }
        this.expr = expr;
        this.alias = alias;
        this.caption = caption;
    }

    public PlanExpression expr() { return expr; }
    public String alias() { return alias; }
    public String caption() { return caption; }

    /**
     * Generate the column expression string for the compiler.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "partnerId AS customerPartnerId"}</li>
     *   <li>{@code "SUM(amountTotal) AS totalAmount"}</li>
     *   <li>{@code "SUM(amountTotal)$总金额 AS totalAmount"}</li>
     * </ul>
     */
    public String toColumnExpr() {
        String baseExpr;
        if (expr instanceof AggregateColumn agg) {
            baseExpr = agg.toColumnExpr();
        } else if (expr instanceof WindowColumn win) {
            baseExpr = win.toColumnExpr();
        } else if (expr instanceof PlanColumnRef ref) {
            baseExpr = ref.toColumnExpr();
        } else {
            // For other PlanExpressions (like BinaryExpr), we cannot easily convert to string without a dialect.
            // This is a temporary fallback for legacy paths.
            baseExpr = expr.toString(); 
        }

        if (caption != null && !caption.isEmpty()) {
            return baseExpr + "$" + caption + " AS " + alias;
        }
        return baseExpr + " AS " + alias;
    }

    @Override
    public String toString() {
        return toColumnExpr();
    }
}

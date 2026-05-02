package com.foggyframework.dataset.db.model.engine.compose.plan.expr;

/**
 * Base marker interface for all Compose Query Plan expression AST nodes.
 *
 * <p>Implemented by {@link ColumnExpr}, {@link BinaryExpr},
 * {@link CaseWhenExpr}, {@link LiteralExpr}, as well as legacy
 * constructs like {@code AggregateColumn} and {@code WindowColumn}.</p>
 *
 * @since 8.2.0.beta
 */
public interface PlanExpression {
}

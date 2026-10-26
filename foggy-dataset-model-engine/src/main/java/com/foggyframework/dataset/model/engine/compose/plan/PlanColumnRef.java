package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.dataset.model.engine.compose.plan.expr.PlanExpression;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * A reference to a field within a specific {@link QueryPlan} stage.
 *
 * <p>Created automatically when fsscript accesses a property on a QueryPlan:
 * {@code sales.partnerId} → {@code new PlanColumnRef(salesPlan, "partnerId")}.
 * Supports fluent aggregation ({@code .sum()}, {@code .count()}, etc.) and
 * aliasing ({@code .as("alias", "caption")}).</p>
 *
 * <p>Implements {@link PropertyFunction} so that chained method calls like
 * {@code sales.partnerId.sum()} work in fsscript.</p>
 *
 * @since 8.2.0.beta
 */
public final class PlanColumnRef implements PropertyFunction, PlanExpression {

    private final QueryPlan plan;
    private final String name;

    public PlanColumnRef(QueryPlan plan, String name) {
        this.plan = plan;
        this.name = name;
    }

    public QueryPlan plan() { return plan; }
    public String name() { return name; }

    // ---- Aggregation factories ----

    public AggregateColumn sum() { return new AggregateColumn(this, "SUM"); }
    public AggregateColumn count() { return new AggregateColumn(this, "COUNT"); }
    public AggregateColumn avg() { return new AggregateColumn(this, "AVG"); }
    public AggregateColumn max() { return new AggregateColumn(this, "MAX"); }
    public AggregateColumn min() { return new AggregateColumn(this, "MIN"); }

    // ---- Window function builders ----

    public WindowColumnBuilder lag(int offset) { return new WindowColumnBuilder("LAG", this, java.util.List.of(offset)); }
    public WindowColumnBuilder lag() { return new WindowColumnBuilder("LAG", this, java.util.List.of(1)); }
    public WindowColumnBuilder lead(int offset) { return new WindowColumnBuilder("LEAD", this, java.util.List.of(offset)); }
    public WindowColumnBuilder lead() { return new WindowColumnBuilder("LEAD", this, java.util.List.of(1)); }

    // ---- Aliasing ----

    public ProjectedColumn as(String alias) {
        return new ProjectedColumn(this, alias, null);
    }

    public ProjectedColumn as(String alias, String caption) {
        return new ProjectedColumn(this, alias, caption);
    }

    // ---- Column expression (for compiler) ----

    public String toColumnExpr() {
        return name;
    }

    // ---- PropertyFunction: support method calls in fsscript ----

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        if (java.util.Set.of("sum", "count", "avg", "max", "min").contains(methodName)) {
            ComposeOptionValidator.arguments(args, 0, 0, methodName);
        }
        return switch (methodName) {
            case "sum" -> sum();
            case "count" -> count();
            case "avg" -> avg();
            case "max" -> max();
            case "min" -> min();
            case "lag", "lead" -> {
                Object[] checked = ComposeOptionValidator.arguments(args, 0, 1, methodName);
                int offset = 1;
                if (checked.length == 1) {
                    Integer exact = ComposeOptionValidator.integer(checked[0], methodName + ".offset", "offset");
                    if (exact == null) throw com.foggyframework.dataset.model.engine.compose.schema.ComposeParameterException.invalidType(
                            "COMPOSE_PARAMETER_TYPE_INVALID", methodName + ".offset", "offset", "an integer from 0 to 2147483647", null);
                    offset = exact;
                }
                yield "lag".equals(methodName) ? lag(offset) : lead(offset);
            }
            case "as" -> {
                Object[] checked = ComposeOptionValidator.aliasArguments(args);
                yield checked.length == 2 ? as((String) checked[0], (String) checked[1]) : as((String) checked[0]);
            }
            default -> throw new IllegalArgumentException(
                    "PlanColumnRef does not support method: " + methodName
                            + ". Available: sum(), count(), avg(), max(), min(), as(alias, caption?)");
        };
    }

    @Override
    public String toString() {
        return "FieldRef(" + name + ")";
    }
}

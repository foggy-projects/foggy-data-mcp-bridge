package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * An aggregate expression on a field reference.
 *
 * <p>Created via {@code planColumnRef.sum()}, {@code .count()}, etc.
 * Supports aliasing: {@code sales.amountTotal.sum().as("totalAmount")}.</p>
 *
 * @since 8.2.0.beta
 */
public final class AggregateColumn implements PropertyFunction, PlanExpression {

    private final PlanColumnRef ref;
    private final String func;

    public AggregateColumn(PlanColumnRef ref, String func) {
        this.ref = ref;
        this.func = func;
    }

    public PlanColumnRef ref() { return ref; }
    public String func() { return func; }

    // ---- Aliasing ----

    public ProjectedColumn as(String alias) {
        return new ProjectedColumn(this, alias, null);
    }

    public ProjectedColumn as(String alias, String caption) {
        return new ProjectedColumn(this, alias, caption);
    }

    // ---- Column expression (for compiler) ----

    public String toColumnExpr() {
        return func + "(" + ref.name() + ")";
    }

    // ---- Window function builder ----

    public WindowColumn over(java.util.Map<String, Object> config) {
        return new WindowColumn(func, ref, null, OverClause.fromMap(config));
    }

    // ---- PropertyFunction: support method calls in fsscript ----

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        if ("as".equals(methodName)) {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException("as() requires at least 1 argument (alias)");
            }
            return args.length >= 2
                    ? as((String) args[0], (String) args[1])
                    : as((String) args[0]);
        }
        if ("over".equals(methodName)) {
            if (args == null || args.length == 0) {
                return over(null);
            }
            if (args[0] instanceof java.util.Map<?, ?> map) {
                return over((java.util.Map<String, Object>) map);
            }
            throw new IllegalArgumentException("over() requires a Map configuration object");
        }
        throw new IllegalArgumentException(
                "AggregateColumn does not support method: " + methodName
                        + ". Available: as(alias, caption?), over(config)");
    }

    @Override
    public String toString() {
        return func + "(" + ref.name() + ")";
    }
}

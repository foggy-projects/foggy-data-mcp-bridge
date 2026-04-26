package com.foggyframework.dataset.db.model.engine.compose.plan;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.List;

import com.foggyframework.dataset.db.model.engine.compose.plan.expr.PlanExpression;

/**
 * Represents a window function expression.
 * e.g. SUM(amount) OVER (PARTITION BY ... ORDER BY ...)
 * or ROW_NUMBER() OVER (PARTITION BY ...)
 * 
 * @since 8.3.0.beta
 */
public final class WindowColumn implements PropertyFunction, PlanExpression {

    private final String func;
    private final PlanColumnRef ref; // Can be null for functions like row_number()
    private final List<Object> args; // Arguments like offset for lag/lead
    private final OverClause over;

    public WindowColumn(String func, PlanColumnRef ref, List<Object> args, OverClause over) {
        this.func = func;
        this.ref = ref;
        this.args = args != null ? List.copyOf(args) : List.of();
        this.over = over;
    }

    public String func() { return func; }
    public PlanColumnRef ref() { return ref; }
    public List<Object> args() { return args; }
    public OverClause over() { return over; }

    // ---- Column expression (for compiler) ----

    public String toColumnExpr() {
        StringBuilder sb = new StringBuilder(func).append("(");
        if (ref != null) {
            sb.append(ref.name());
        }
        if (!args.isEmpty()) {
            if (ref != null) sb.append(", ");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(args.get(i));
            }
        }
        sb.append(") OVER (");
        
        boolean hasPartition = over.getPartitionBy() != null && !over.getPartitionBy().isEmpty();
        boolean hasOrder = over.getOrderBy() != null && !over.getOrderBy().isEmpty();
        boolean hasFrame = over.getWindowFrame() != null;

        if (hasPartition) {
            sb.append("PARTITION BY ");
            sb.append(String.join(", ", over.getPartitionBy()));
        }

        if (hasOrder) {
            if (hasPartition) sb.append(" ");
            sb.append("ORDER BY ");
            for (int i = 0; i < over.getOrderBy().size(); i++) {
                String col = over.getOrderBy().get(i);
                if (i > 0) sb.append(", ");
                if (col.startsWith("-")) {
                    sb.append(col.substring(1)).append(" DESC");
                } else {
                    sb.append(col).append(" ASC");
                }
            }
        }

        if (hasFrame) {
            if (hasPartition || hasOrder) sb.append(" ");
            sb.append(over.getWindowFrame().toSql());
        }

        sb.append(")");
        return sb.toString();
    }

    // ---- Aliasing ----

    public ProjectedColumn as(String alias) {
        return new ProjectedColumn(this, alias, null);
    }

    public ProjectedColumn as(String alias, String caption) {
        return new ProjectedColumn(this, alias, caption);
    }

    // ---- PropertyFunction: support .as() in fsscript ----

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] methodArgs) {
        if ("as".equals(methodName)) {
            if (methodArgs == null || methodArgs.length == 0) {
                throw new IllegalArgumentException("as() requires at least 1 argument (alias)");
            }
            return methodArgs.length >= 2
                    ? as((String) methodArgs[0], (String) methodArgs[1])
                    : as((String) methodArgs[0]);
        }
        throw new IllegalArgumentException(
                "WindowColumn does not support method: " + methodName + ". Available: as(alias, caption?)");
    }
}

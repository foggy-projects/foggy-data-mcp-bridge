package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for JOIN operations.
 *
 * <p>Usage in fsscript:
 * <pre>{@code
 * const joined = customers.leftJoin(orders)
 *     .on(customers.id, orders.partnerId)
 *     .and(customers.companyId, orders.companyId);
 * }</pre>
 *
 * <p>Implements {@link PropertyFunction} so that {@code .on()} and
 * {@code .and()} can be called from fsscript.</p>
 *
 * @since 8.2.0.beta
 */
public final class ComposeJoinBuilder implements PropertyFunction {

    private final QueryPlan left;
    private final QueryPlan right;
    private final JoinType joinType;

    public ComposeJoinBuilder(QueryPlan left, QueryPlan right, JoinType joinType) {
        this.left = left;
        this.right = right;
        this.joinType = joinType;
    }

    /**
     * Specify the join condition. Returns a {@link JoinPlan}.
     */
    public JoinPlan on(PlanColumnRef leftCol, PlanColumnRef rightCol) {
        List<JoinOn> conditions = new ArrayList<>();
        conditions.add(JoinOn.of(leftCol.name(), "=", rightCol.name()));
        return JoinPlan.builder()
                .left(left)
                .right(right)
                .type(joinType)
                .on(conditions)
                .build();
    }

    // ---- PropertyFunction ----

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        if ("on".equals(methodName)) {
            if (args == null || args.length < 2) {
                throw new IllegalArgumentException(
                        "on() requires exactly 2 arguments: on(leftFieldRef, rightFieldRef)");
            }
            return on((PlanColumnRef) args[0], (PlanColumnRef) args[1]);
        }
        throw new IllegalArgumentException(
                "ComposeJoinBuilder does not support method: " + methodName
                        + ". Available: on(leftRef, rightRef)");
    }

    @Override
    public String toString() {
        return "JoinBuilder(" + joinType + ")";
    }
}

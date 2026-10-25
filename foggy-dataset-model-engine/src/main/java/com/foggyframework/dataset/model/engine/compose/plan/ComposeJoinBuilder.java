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
        conditions.add(JoinOn.ofReferences(leftCol, rightCol));
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
            validateArguments(args, "on");
            return on((PlanColumnRef) args[0], (PlanColumnRef) args[1]);
        }
        throw new IllegalArgumentException(
                "ComposeJoinBuilder does not support method: " + methodName
                        + ". Available: on(leftRef, rightRef)");
    }

    static void validateArguments(Object[] args, String method) {
        if (args == null || args.length != 2 || !(args[0] instanceof PlanColumnRef)
                || !(args[1] instanceof PlanColumnRef)) {
            throw com.foggyframework.dataset.model.engine.compose.schema.ComposeParameterException.invalidType(
                    "COMPOSE_PARAMETER_TYPE_INVALID", "join." + method, method,
                    "exactly two field references", args);
        }
    }

    @Override
    public String toString() {
        return "JoinBuilder(" + joinType + ")";
    }
}

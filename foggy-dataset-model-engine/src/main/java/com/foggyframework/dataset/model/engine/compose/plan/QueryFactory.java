package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * Global {@code Query} object injected into the fsscript sandbox.
 *
 * <p>Implements {@link PropertyFunction} so calls such as
 * {@code Query.from("ModelName")} and {@code Query.col("amount").sum()} work
 * as method calls in fsscript.</p>
 *
 * <p>Usage in fsscript:
 * <pre>{@code
 * const sales = Query.from("OdooSaleOrderModel");
 * const total = Query.col("amountTotal").sum().as("total");
 * }</pre>
 *
 * @since 8.2.0.beta
 */
public final class QueryFactory implements PropertyFunction {

    /** Singleton instance — no state, can be shared across scripts. */
    public static final QueryFactory INSTANCE = new QueryFactory();

    private QueryFactory() {}

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        if ("from".equals(methodName)) {
            Object[] checked = ComposeOptionValidator.arguments(args, 1, 1, "Query.from");
            String modelName = ComposeOptionValidator.requiredString(checked[0], "Query.from.model", "model");
            return BaseModelPlan.builder().model(modelName).build();
        }
        if ("col".equals(methodName)) {
            Object[] checked = ComposeOptionValidator.arguments(args, 1, 1, "Query.col");
            String columnName = ComposeOptionValidator.requiredString(checked[0], "Query.col.field", "field");
            return new PlanColumnRef(null, columnName);
        }
        throw new IllegalArgumentException(
                "Query does not support method: " + methodName
                        + ". Available: from(modelName), col(columnName)");
    }

    @Override
    public String toString() {
        return "Query";
    }
}

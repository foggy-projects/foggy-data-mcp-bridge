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
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException(
                        "Query.from() requires exactly 1 argument: the model name string");
            }
            String modelName = (String) args[0];
            if (modelName == null || modelName.isEmpty()) {
                throw new IllegalArgumentException(
                        "Query.from(modelName): modelName must be a non-empty string");
            }
            return BaseModelPlan.builder().model(modelName).build();
        }
        if ("col".equals(methodName)) {
            if (args == null || args.length == 0) {
                throw new IllegalArgumentException(
                        "Query.col() requires exactly 1 argument: the column name string");
            }
            String columnName = (String) args[0];
            if (columnName == null || columnName.isEmpty()) {
                throw new IllegalArgumentException(
                        "Query.col(columnName): columnName must be a non-empty string");
            }
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

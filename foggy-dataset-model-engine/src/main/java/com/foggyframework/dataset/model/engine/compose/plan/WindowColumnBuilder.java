package com.foggyframework.dataset.model.engine.compose.plan;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.List;
import java.util.Map;

/**
 * Intermediate builder for window functions.
 * Returned by e.g. {@code sales.amountTotal.lag(1)}.
 * Must be followed by {@code .over(...)}.
 */
public final class WindowColumnBuilder implements PropertyFunction {

    private final String func;
    private final PlanColumnRef ref;
    private final List<Object> args;

    public WindowColumnBuilder(String func, PlanColumnRef ref, List<Object> args) {
        this.func = func;
        this.ref = ref;
        this.args = args;
    }

    public WindowColumn over(Map<String, Object> config) {
        return new WindowColumn(func, ref, args, OverClause.fromMap(config));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] methodArgs) {
        if ("over".equals(methodName)) {
            if (methodArgs == null || methodArgs.length == 0) {
                return over(null);
            }
            if (methodArgs[0] instanceof Map<?, ?> map) {
                return over((Map<String, Object>) map);
            }
            throw new IllegalArgumentException("over() requires a Map configuration object");
        }
        throw new IllegalArgumentException(
                "WindowColumnBuilder only supports .over(config) method, got: " + methodName);
    }
}

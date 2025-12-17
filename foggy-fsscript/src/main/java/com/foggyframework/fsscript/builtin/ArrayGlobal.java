package com.foggyframework.fsscript.builtin;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.Collection;

/**
 * JavaScript Array 全局对象
 * <p>
 * 实现 PropertyFunction 接口，支持 Array.isArray() 等静态方法。
 * </p>
 *
 * <h3>支持的方法</h3>
 * <ul>
 *     <li>{@code Array.isArray(value)} - 判断是否为数组或 List</li>
 * </ul>
 *
 * @author Foggy
 * @since 1.0
 */
public final class ArrayGlobal implements PropertyFunction {

    public static final ArrayGlobal INSTANCE = new ArrayGlobal();

    private ArrayGlobal() {
    }

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        switch (methodName) {
            case "isArray":
                return isArray(args);
            default:
                throw new UnsupportedOperationException("Array." + methodName + " is not supported");
        }
    }

    /**
     * Array.isArray(value)
     * 判断值是否为数组或 Collection
     */
    private boolean isArray(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return false;
        }

        Object value = args[0];
        return value.getClass().isArray() || value instanceof Collection;
    }
}

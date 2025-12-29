package com.foggyframework.dataset.db.model.proxy;

import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

import java.util.Collections;
import java.util.List;

/**
 * loadTableModel 内置函数实现
 *
 * <p>在 QM V2 格式中，用于创建 {@link TableModelProxy} 对象：
 * <pre>{@code
 * const fo = loadTableModel('FactOrderModel');
 * const fp = loadTableModel('FactPaymentModel');
 * }</pre>
 *
 * @author Foggy Framework
 * @since 2.0
 */
public class LoadTableModelFunction implements FsscriptFunction {

    /**
     * 单例实例
     */
    private static final LoadTableModelFunction INSTANCE = new LoadTableModelFunction();

    /**
     * 获取单例实例
     */
    public static LoadTableModelFunction getInstance() {
        return INSTANCE;
    }

    private LoadTableModelFunction() {
    }

    @Override
    public Object threadSafeAccept(Object t) {
        if (t instanceof String modelName) {
            return new TableModelProxy(modelName);
        }
        throw new IllegalArgumentException("loadTableModel requires a model name string");
    }

    @Override
    public Object executeFunction(ExpEvaluator evaluator, Object... args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("loadTableModel requires a model name");
        }
        Object arg = args[0];
        if (arg instanceof String modelName) {
            return new TableModelProxy(modelName);
        }
        throw new IllegalArgumentException("loadTableModel requires a model name string, got: " +
                (arg == null ? "null" : arg.getClass().getName()));
    }

    @Override
    public List<Exp> getArgDefs() {
        return Collections.emptyList();
    }

    @Override
    public Object autoApply(ExpEvaluator ee) {
        throw new IllegalArgumentException("loadTableModel requires a model name");
    }

    @Override
    public Object apply(Object[] objects) {
        if (objects == null || objects.length == 0) {
            throw new IllegalArgumentException("loadTableModel requires a model name");
        }
        Object arg = objects[0];
        if (arg instanceof String modelName) {
            return new TableModelProxy(modelName);
        }
        throw new IllegalArgumentException("loadTableModel requires a model name string");
    }

    @Override
    public String toString() {
        return "loadTableModel(modelName)";
    }
}

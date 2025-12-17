package com.foggyframework.fsscript.builtin;

import com.foggyframework.fsscript.exp.AbstractExp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 内置全局对象表达式
 * <p>
 * 在解析阶段确定为内置全局对象（如 JSON、console、Array），直接返回对应的单例实例。
 * 相比 IdExp 每次 evalValue 时检查，这种方式避免了不必要的查找和判断。
 * </p>
 *
 * <h3>支持的全局对象</h3>
 * <ul>
 *     <li>{@link #JSON} - JSON.stringify() / JSON.parse()</li>
 *     <li>{@link #CONSOLE} - console.log() / console.warn() / console.error()</li>
 *     <li>{@link #ARRAY} - Array.isArray()</li>
 * </ul>
 *
 * @author Foggy
 * @since 1.0
 */
public class BuiltinGlobalExp extends AbstractExp<Object> {

    /**
     * JSON 全局对象表达式
     */
    public static final BuiltinGlobalExp JSON = new BuiltinGlobalExp("JSON", JsonGlobal.INSTANCE);

    /**
     * console 全局对象表达式
     */
    public static final BuiltinGlobalExp CONSOLE = new BuiltinGlobalExp("console", ConsoleGlobal.INSTANCE);

    /**
     * Array 全局对象表达式
     */
    public static final BuiltinGlobalExp ARRAY = new BuiltinGlobalExp("Array", ArrayGlobal.INSTANCE);

    private final String name;

    private BuiltinGlobalExp(String name, Object value) {
        super(value);
        this.name = name;
    }

    /**
     * 根据名称获取内置全局对象表达式
     *
     * @param name 全局对象名称
     * @return 对应的表达式，不存在返回 null
     */
    public static BuiltinGlobalExp get(String name) {
        switch (name) {
            case "JSON":
                return JSON;
            case "console":
                return CONSOLE;
            case "Array":
                return ARRAY;
            default:
                return null;
        }
    }

    @Override
    public Object evalValue(ExpEvaluator evaluator) {
        // 直接返回预设的全局对象，无需任何查找
        return value;
    }

    @Override
    public Class<?> getReturnType(ExpEvaluator evaluator) {
        return value.getClass();
    }

    @Override
    public String toString() {
        return "[BuiltinGlobal:" + name + "]";
    }
}

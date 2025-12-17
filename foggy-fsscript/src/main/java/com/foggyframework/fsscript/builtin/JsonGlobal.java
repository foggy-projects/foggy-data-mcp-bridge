package com.foggyframework.fsscript.builtin;

import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * JavaScript JSON 全局对象
 * <p>
 * 实现 PropertyFunction 接口，支持 JSON.stringify() 和 JSON.parse() 方法调用。
 * 使用单例模式，通过 {@link BuiltinGlobals} 注册和访问。
 * </p>
 *
 * <h3>支持的方法</h3>
 * <ul>
 *     <li>{@code JSON.stringify(value)} - 将对象序列化为 JSON 字符串</li>
 *     <li>{@code JSON.stringify(value, replacer, space)} - 格式化输出（space > 0 时）</li>
 *     <li>{@code JSON.parse(text)} - 将 JSON 字符串解析为对象</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // FSScript 中
 * let obj = { name: "张三", age: 30 };
 *
 * // 序列化
 * let str = JSON.stringify(obj);  // {"name":"张三","age":30}
 *
 * // 格式化输出
 * let pretty = JSON.stringify(obj, null, 2);
 *
 * // 反序列化
 * let parsed = JSON.parse('{"id":1}');  // parsed.id == 1
 * </pre>
 *
 * @author Foggy
 * @since 1.0
 */
public final class JsonGlobal implements PropertyFunction {

    /**
     * 单例实例
     */
    public static final JsonGlobal INSTANCE = new JsonGlobal();

    private JsonGlobal() {
        // 单例，禁止外部实例化
    }

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        switch (methodName) {
            case "stringify":
                return stringify(args);
            case "parse":
                return parse(args);
            default:
                throw new UnsupportedOperationException("JSON." + methodName + " is not supported");
        }
    }

    /**
     * JSON.stringify(value)
     * JSON.stringify(value, replacer, space)
     * <p>
     * 将 JavaScript 值序列化为 JSON 字符串。
     * </p>
     *
     * @param args 参数数组：[value, replacer?, space?]
     * @return JSON 字符串
     */
    private String stringify(Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return "null";
        }

        Object value = args[0];

        // JSON.stringify(obj, null, 2) - 格式化输出
        // 注意：replacer 参数暂不支持，仅检查 space 参数
        if (args.length >= 3 && args[2] instanceof Number) {
            int space = ((Number) args[2]).intValue();
            if (space > 0) {
                return JsonUtils.toJsonPrettyFormat(value);
            }
        }

        return JsonUtils.toJson(value);
    }

    /**
     * JSON.parse(text)
     * <p>
     * 将 JSON 字符串解析为 JavaScript 值（Map 或 List）。
     * </p>
     *
     * @param args 参数数组：[text]
     * @return 解析后的对象（Map、List 或基本类型）
     */
    private Object parse(Object[] args) {
        if (args.length == 0 || args[0] == null) {
            return null;
        }

        String text = args[0].toString();
        if (text.isEmpty()) {
            return null;
        }

        // 返回 Object.class 会自动转为 Map 或 List
        return JsonUtils.fromJson(text, Object.class);
    }
}

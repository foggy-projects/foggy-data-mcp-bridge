package com.foggyframework.fsscript.builtin;

import com.foggyframework.fsscript.exp.PropertyFunction;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import lombok.extern.slf4j.Slf4j;

/**
 * JavaScript console 全局对象
 * <p>
 * 实现 PropertyFunction 接口，支持 console.log()、console.warn()、console.error() 等方法。
 * 使用 SLF4J 进行实际的日志输出。
 * </p>
 *
 * <h3>支持的方法</h3>
 * <ul>
 *     <li>{@code console.log(...args)} - 输出 INFO 级别日志</li>
 *     <li>{@code console.info(...args)} - 输出 INFO 级别日志</li>
 *     <li>{@code console.warn(...args)} - 输出 WARN 级别日志</li>
 *     <li>{@code console.error(...args)} - 输出 ERROR 级别日志</li>
 *     <li>{@code console.debug(...args)} - 输出 DEBUG 级别日志</li>
 * </ul>
 *
 * @author Foggy
 * @since 1.0
 */
@Slf4j
public final class ConsoleGlobal implements PropertyFunction {

    public static final ConsoleGlobal INSTANCE = new ConsoleGlobal();

    private ConsoleGlobal() {
    }

    @Override
    public Object invoke(ExpEvaluator evaluator, String methodName, Object[] args) {
        String message = formatMessage(args);

        switch (methodName) {
            case "log":
            case "info":
                log.info(message);
                break;
            case "warn":
                log.warn(message);
                break;
            case "error":
                log.error(message);
                break;
            case "debug":
                log.debug(message);
                break;
            default:
                throw new UnsupportedOperationException("console." + methodName + " is not supported");
        }

        return null;
    }

    /**
     * 格式化输出消息，类似 JavaScript console.log 的行为
     */
    private String formatMessage(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(args[i] == null ? "null" : args[i].toString());
        }
        return sb.toString();
    }
}

package com.foggyframework.core.filter;

/**
 * Step 模式处理器接口
 *
 * <p>相比 Filter 模式，Step 模式无需手动调用 chain.doFilter()，
 * 通过返回值控制执行流程，避免遗忘传递链的问题。
 *
 * <h3>返回值说明：</h3>
 * <ul>
 *   <li>{@link #CONTINUE} (0): 继续执行下一个步骤</li>
 *   <li>{@link #ABORT} (1): 中止执行链（非错误情况）</li>
 *   <li>其他正数: 自定义中止码</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * public class MyStep implements FoggyStep<MyContext> {
 *     @Override
 *     public int process(MyContext ctx) {
 *         // 处理逻辑
 *         if (shouldStop) {
 *             return ABORT;
 *         }
 *         return CONTINUE;
 *     }
 * }
 * }</pre>
 *
 * @param <T> 上下文类型
 * @author foggy-framework
 * @since 8.0.0
 */
public interface FoggyStep<T> extends Comparable<FoggyStep<?>> {

    /**
     * 继续执行下一个步骤
     */
    int CONTINUE = 0;

    /**
     * 中止执行链（非错误）
     */
    int ABORT = 1;

    /**
     * 执行步骤处理
     *
     * @param ctx 上下文对象
     * @return 执行结果码，CONTINUE 继续，ABORT 或其他正数中止
     */
    int process(T ctx);

    /**
     * 获取执行顺序，越大越靠前
     *
     * @return 顺序值
     */
    default int order() {
        return 0;
    }

    @Override
    default int compareTo(FoggyStep<?> o) {
        return Integer.compare(o.order(), this.order());
    }
}

package com.foggyframework.dataset.db.model.plugins.query_execution;

import com.foggyframework.core.filter.FoggyStep;

/**
 * 查询执行步骤接口
 * <p>
 * 在 SQL 构建完成后、执行前后的处理步骤。
 * 可用于实现：
 * <ul>
 *   <li>预聚合重写（{@code beforeExecute} 中修改 SQL）</li>
 *   <li>L2 缓存（{@code beforeExecute} 检查，{@code afterExecute} 写入）</li>
 *   <li>SQL 审计、限流等扩展功能</li>
 * </ul>
 * </p>
 *
 * <h3>执行流程：</h3>
 * <pre>
 * SQL 构建完成
 *     ↓
 * beforeExecute (按 order 降序执行)
 *     ↓
 * [skipExecution?] → 跳过执行，使用 cachedResult
 *     ↓
 * SQL 执行
 *     ↓
 * afterExecute (按 order 升序执行)
 * </pre>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
public interface QueryExecutionStep extends FoggyStep<QueryExecutionContext> {

    /**
     * 判断该步骤是否支持当前的执行阶段
     *
     * @param phase 执行阶段
     * @param ctx   执行上下文
     * @return 如果支持返回 true
     */
    default boolean supports(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        // 默认向后兼容，只在普通查询时执行
        return phase == QueryExecutionPhase.NORMAL_QUERY;
    }

    /**
     * SQL 执行前处理
     * <p>
     * 可在此方法中：
     * <ul>
     *   <li>修改 SQL（如预聚合重写）</li>
     *   <li>检查缓存并设置 {@code skipExecution=true}</li>
     *   <li>添加执行前的日志、审计等</li>
     * </ul>
     * </p>
     *
     * @param phase 当前阶段
     * @param ctx 执行上下文
     * @return CONTINUE 继续，ABORT 中止
     */
    default int beforeExecute(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        return beforeExecute(ctx);
    }

    /**
     * 向后兼容的旧版方法
     */
    default int beforeExecute(QueryExecutionContext ctx) {
        return CONTINUE;
    }

    /**
     * SQL 执行后处理
     * <p>
     * 可在此方法中：
     * <ul>
     *   <li>写入缓存</li>
     *   <li>记录执行统计</li>
     *   <li>结果转换等</li>
     * </ul>
     * </p>
     *
     * @param phase 当前阶段
     * @param ctx 执行上下文（包含执行结果）
     * @return CONTINUE 继续，ABORT 中止
     */
    default int afterExecute(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        return afterExecute(ctx);
    }

    /**
     * 向后兼容的旧版方法
     */
    default int afterExecute(QueryExecutionContext ctx) {
        return CONTINUE;
    }

    /**
     * 默认 process 实现（委托给 afterExecute）
     */
    @Override
    default int process(QueryExecutionContext ctx) {
        return afterExecute(ctx);
    }
}

package com.foggyframework.dataset.db.model.plugins.query_execution;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 查询执行步骤执行器
 * <p>
 * 管理和执行所有 {@link QueryExecutionStep}。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class QueryExecutionStepExecutor {

    private final List<QueryExecutionStep> steps;

    public QueryExecutionStepExecutor(List<QueryExecutionStep> steps) {
        if (steps != null && !steps.isEmpty()) {
            this.steps = new ArrayList<>(steps);
            // 按 order 降序排序（order 大的先执行）
            Collections.sort(this.steps);
            log.debug("QueryExecutionStepExecutor initialized with {} steps: {}",
                    this.steps.size(),
                    this.steps.stream().map(s -> s.getClass().getSimpleName()).toList());
        } else {
            this.steps = Collections.emptyList();
        }
    }

    /**
     * 执行所有步骤的 beforeExecute 方法
     *
     * @param phase 当前阶段
     * @param ctx 执行上下文
     * @return 执行结果码
     */
    public int executeBeforeExecute(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        for (QueryExecutionStep step : steps) {
            if (!step.supports(phase, ctx)) {
                continue;
            }
            try {
                int result = step.beforeExecute(phase, ctx);
                if (result != QueryExecutionStep.CONTINUE) {
                    log.debug("Step {} beforeExecute returned {}, stopping",
                            step.getClass().getSimpleName(), result);
                    return result;
                }
            } catch (Exception e) {
                log.error("Step {} beforeExecute failed: {}",
                        step.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
        }
        return QueryExecutionStep.CONTINUE;
    }

    /**
     * 向后兼容的默认方法，使用 NORMAL_QUERY 阶段
     */
    public int executeBeforeExecute(QueryExecutionContext ctx) {
        return executeBeforeExecute(QueryExecutionPhase.NORMAL_QUERY, ctx);
    }

    /**
     * 执行所有步骤的 afterExecute 方法
     * <p>
     * 注意：afterExecute 按 order 升序执行（与 beforeExecute 相反）
     * </p>
     *
     * @param phase 当前阶段
     * @param ctx 执行上下文
     * @return 执行结果码
     */
    public int executeAfterExecute(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        // 反向遍历，实现升序执行
        for (int i = steps.size() - 1; i >= 0; i--) {
            QueryExecutionStep step = steps.get(i);
            if (!step.supports(phase, ctx)) {
                continue;
            }
            try {
                int result = step.afterExecute(phase, ctx);
                if (result != QueryExecutionStep.CONTINUE) {
                    log.debug("Step {} afterExecute returned {}, stopping",
                            step.getClass().getSimpleName(), result);
                    return result;
                }
            } catch (Exception e) {
                log.error("Step {} afterExecute failed: {}",
                        step.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
        }
        return QueryExecutionStep.CONTINUE;
    }

    /**
     * 向后兼容的默认方法，使用 NORMAL_QUERY 阶段
     */
    public int executeAfterExecute(QueryExecutionContext ctx) {
        return executeAfterExecute(QueryExecutionPhase.NORMAL_QUERY, ctx);
    }

    /**
     * 是否有步骤
     */
    public boolean hasSteps() {
        return !steps.isEmpty();
    }

    /**
     * 获取步骤数量
     */
    public int size() {
        return steps.size();
    }
}

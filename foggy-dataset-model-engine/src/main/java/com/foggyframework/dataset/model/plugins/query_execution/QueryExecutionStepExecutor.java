package com.foggyframework.dataset.model.plugins.query_execution;

import com.foggyframework.dataset.model.plugins.pipeline.LoopDecision;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
            validateUniqueOrders(this.steps);
            // 按 order 降序排序（order 大的先执行）
            this.steps.sort(Comparator.comparingInt(QueryExecutionStep::order).reversed());
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
            if (isTerminalBeforeExecuteStep(step)) {
                continue;
            }
            int result = executeBeforeStep(step, phase, ctx);
            if (result != QueryExecutionStep.CONTINUE) {
                return result;
            }
        }

        int loopResult = executeBeforeLoopHooks(phase, ctx);
        if (loopResult != QueryExecutionStep.CONTINUE) {
            return loopResult;
        }

        // Integer.MIN_VALUE is a reserved terminal slot. L2 lookup occupies it so
        // loop hooks and every other SQL/parameter rewrite are complete first.
        for (QueryExecutionStep step : steps) {
            if (!isTerminalBeforeExecuteStep(step)) {
                continue;
            }
            int result = executeBeforeStep(step, phase, ctx);
            if (result != QueryExecutionStep.CONTINUE) {
                return result;
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

    private int executeBeforeLoopHooks(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        if (ctx == null || ctx.getMaxLoopCount() <= 0) {
            return QueryExecutionStep.CONTINUE;
        }

        ctx.clearLoopStop();
        if (!hasLoopStep(phase, ctx)) {
            return QueryExecutionStep.CONTINUE;
        }

        for (int i = 0; i < ctx.getMaxLoopCount(); i++) {
            ctx.setLoopIndex(i);
            ctx.clearLoopChanged();

            for (QueryExecutionStep step : steps) {
                if (isTerminalBeforeExecuteStep(step) || !step.supportsLoop(phase, ctx)) {
                    continue;
                }

                LoopDecision decision = step.runLoop(phase, ctx);
                if (decision == null) {
                    decision = LoopDecision.unchanged("null decision");
                }
                ctx.addLoopTrace(step.getClass().getSimpleName(), decision);

                if (decision.isChanged()) {
                    ctx.markLoopChanged();
                }
                if (decision.isFail()) {
                    throw new IllegalStateException("Pipeline loop hook failed at step "
                            + step.getClass().getSimpleName() + ": " + decision.getReason());
                }
                if (decision.isStop()) {
                    ctx.requestLoopStop(decision.getReason());
                    return QueryExecutionStep.CONTINUE;
                }
            }

            if (!ctx.isLoopChanged()) {
                return QueryExecutionStep.CONTINUE;
            }
        }

        throw new IllegalStateException("Pipeline loop hook exceeded maxLoopCount="
                + ctx.getMaxLoopCount() + " for phase=" + phase);
    }

    private int executeBeforeStep(QueryExecutionStep step,
                                  QueryExecutionPhase phase,
                                  QueryExecutionContext ctx) {
        if (!step.supports(phase, ctx)) {
            return QueryExecutionStep.CONTINUE;
        }
        try {
            String sqlBefore = ctx != null ? ctx.getSql() : null;
            List<Object> paramsBefore = snapshotParams(ctx != null ? ctx.getParams() : null);
            int result = step.beforeExecute(phase, ctx);
            if (ctx != null && (!Objects.equals(sqlBefore, ctx.getSql())
                    || !Objects.equals(paramsBefore, ctx.getParams()))) {
                ctx.refreshPagingSql();
            }
            if (result != QueryExecutionStep.CONTINUE) {
                log.debug("Step {} beforeExecute returned {}, stopping",
                        step.getClass().getSimpleName(), result);
            }
            return result;
        } catch (Exception e) {
            log.error("Step {} beforeExecute failed: {}",
                    step.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    private boolean isTerminalBeforeExecuteStep(QueryExecutionStep step) {
        return step.order() == Integer.MIN_VALUE;
    }

    private boolean hasLoopStep(QueryExecutionPhase phase, QueryExecutionContext ctx) {
        for (QueryExecutionStep step : steps) {
            if (!isTerminalBeforeExecuteStep(step) && step.supportsLoop(phase, ctx)) {
                return true;
            }
        }
        return false;
    }

    private static void validateUniqueOrders(List<QueryExecutionStep> steps) {
        Map<Integer, QueryExecutionStep> seen = new HashMap<>();
        for (QueryExecutionStep step : steps) {
            if (step == null) {
                throw new IllegalStateException("QueryExecutionStep list contains null entry");
            }
            int order = step.order();
            QueryExecutionStep previous = seen.putIfAbsent(order, step);
            if (previous != null) {
                throw new IllegalStateException("Duplicate QueryExecutionStep order=" + order
                        + " for " + previous.getClass().getName() + " and " + step.getClass().getName());
            }
        }
    }

    private static List<Object> snapshotParams(List<Object> params) {
        return params == null ? null : new ArrayList<>(params);
    }
}

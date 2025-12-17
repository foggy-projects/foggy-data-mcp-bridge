package com.foggyframework.core.filter;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Step 执行器
 *
 * <p>按顺序执行所有注册的 Step，无需手动传递链。
 * 通过返回值控制是否继续执行。
 *
 * @param <T> 上下文类型
 * @param <S> Step 类型
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
@Getter
public class FoggyStepExecutor<T, S extends FoggyStep<T>> {

    private final List<S> steps;

    public FoggyStepExecutor(List<S> steps) {
        if (steps == null || steps.isEmpty()) {
            this.steps = Collections.emptyList();
        } else {
            // 按 order 排序（order 越大越靠前）
            this.steps = new ArrayList<>(steps);
            Collections.sort(this.steps);
        }
    }

    /**
     * 执行所有步骤
     *
     * @param ctx 上下文对象
     * @return 最后一个步骤的返回值，或 CONTINUE 如果全部执行完成
     */
    public int execute(T ctx) {
        for (S step : steps) {
            try {
                int result = step.process(ctx);
                if (result != FoggyStep.CONTINUE) {
                    log.debug("Step {} returned {}, stopping execution",
                            step.getClass().getSimpleName(), result);
                    return result;
                }
            } catch (Exception e) {
                log.error("Step {} execution failed: {}",
                        step.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
        }
        return FoggyStep.CONTINUE;
    }

    /**
     * 检查是否有注册的步骤
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

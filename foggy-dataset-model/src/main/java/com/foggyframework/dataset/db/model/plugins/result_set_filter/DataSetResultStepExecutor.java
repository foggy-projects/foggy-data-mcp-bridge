package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.filter.FoggyStepExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据集结果 Step 执行器
 *
 * <p>管理和执行所有 DataSetResultStep，支持：
 * <ul>
 *   <li>beforeQuery: 查询前处理（权限过滤等）</li>
 *   <li>process: 结果处理（格式转换等）</li>
 * </ul>
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
public class DataSetResultStepExecutor extends FoggyStepExecutor<ModelResultContext, DataSetResultStep> {

    private final List<DataSetResultStep> beforeQuerySteps;
    private final List<DataSetResultStep> processSteps;

    public DataSetResultStepExecutor(List<DataSetResultStep> steps) {
        super(steps);
        List<DataSetResultStep> safeSteps = steps == null ? List.of() : new ArrayList<>(steps);
        validateUniqueOrder(safeSteps, "beforeQuery", DataSetResultStep::beforeQueryOrder);
        validateUniqueOrder(safeSteps, "process", DataSetResultStep::processOrder);
        this.beforeQuerySteps = sort(safeSteps, DataSetResultStep::beforeQueryOrder);
        this.processSteps = sort(safeSteps, DataSetResultStep::processOrder);
        if (steps != null && !steps.isEmpty()) {
            log.debug("DataSetResultStepExecutor initialized with {} steps; beforeQuery={}, process={}",
                    steps.size(), stepNames(beforeQuerySteps), stepNames(processSteps));
        }
    }

    /**
     * 执行所有步骤的 beforeQuery 方法
     *
     * @param ctx 上下文
     * @return 执行结果码
     */
    public int executeBeforeQuery(ModelResultContext ctx) {
        for (DataSetResultStep step : beforeQuerySteps) {
            try {
                int result = step.beforeQuery(ctx);
                if (result != DataSetResultStep.CONTINUE) {
                    log.debug("Step {} beforeQuery returned {}, stopping",
                            step.getClass().getSimpleName(), result);
                    return result;
                }
            } catch (Exception e) {
                log.error("Step {} beforeQuery failed: {}",
                        step.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
        }
        return DataSetResultStep.CONTINUE;
    }

    /**
     * 执行所有步骤的 process 方法
     *
     * @param ctx 上下文
     * @return 执行结果码
     */
    public int executeProcess(ModelResultContext ctx) {
        for (DataSetResultStep step : processSteps) {
            try {
                int result = step.process(ctx);
                if (result != DataSetResultStep.CONTINUE) {
                    log.debug("Step {} process returned {}, stopping",
                            step.getClass().getSimpleName(), result);
                    return result;
                }
            } catch (Exception e) {
                log.error("Step {} process failed: {}",
                        step.getClass().getSimpleName(), e.getMessage(), e);
                throw e;
            }
        }
        return DataSetResultStep.CONTINUE;
    }

    private static List<DataSetResultStep> sort(List<DataSetResultStep> steps,
                                                java.util.function.ToIntFunction<DataSetResultStep> order) {
        List<DataSetResultStep> sorted = new ArrayList<>(steps);
        sorted.sort(Comparator.comparingInt(order));
        return List.copyOf(sorted);
    }

    private static void validateUniqueOrder(List<DataSetResultStep> steps,
                                            String phase,
                                            java.util.function.ToIntFunction<DataSetResultStep> order) {
        Map<Integer, DataSetResultStep> seen = new HashMap<>();
        for (DataSetResultStep step : steps) {
            if (step == null) {
                throw new IllegalStateException("DataSetResultStep list contains null entry");
            }
            if (!overridesPhase(step, phase)) {
                continue;
            }
            int value = order.applyAsInt(step);
            DataSetResultStep previous = seen.putIfAbsent(value, step);
            if (previous != null) {
                throw new IllegalStateException("Duplicate DataSetResultStep " + phase + " order=" + value
                        + " for " + AopUtils.getTargetClass(previous).getName()
                        + " and " + AopUtils.getTargetClass(step).getName());
            }
        }
    }

    private static boolean overridesPhase(DataSetResultStep step, String methodName) {
        try {
            Class<?> userClass = AopUtils.getTargetClass(step);
            Method method = userClass.getMethod(methodName, ModelResultContext.class);
            return method.getDeclaringClass() != DataSetResultStep.class;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Invalid DataSetResultStep implementation: "
                    + AopUtils.getTargetClass(step).getName(), e);
        }
    }

    private static List<String> stepNames(List<DataSetResultStep> steps) {
        return steps.stream().map(step -> step.getClass().getSimpleName()).toList();
    }
}

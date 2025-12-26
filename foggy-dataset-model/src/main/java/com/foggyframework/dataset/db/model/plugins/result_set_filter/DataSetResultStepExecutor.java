package com.foggyframework.dataset.db.model.plugins.result_set_filter;

import com.foggyframework.core.filter.FoggyStepExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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

    public DataSetResultStepExecutor(List<DataSetResultStep> steps) {
        super(steps);
        if (steps != null && !steps.isEmpty()) {
            log.debug("DataSetResultStepExecutor initialized with {} steps", steps.size());
        }
    }

    /**
     * 执行所有步骤的 beforeQuery 方法
     *
     * @param ctx 上下文
     * @return 执行结果码
     */
    public int executeBeforeQuery(ModelResultContext ctx) {
        for (DataSetResultStep step : getSteps()) {
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
        return execute(ctx);
    }
}

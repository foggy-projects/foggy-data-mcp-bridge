package com.foggyframework.dataset.jdbc.model.plugins.result_set_filter;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * 数据集结果处理管理器默认实现
 *
 * <p>使用 Step 模式处理查询前后的逻辑，通过返回值控制流程。
 *
 * @author foggy-framework
 * @since 8.0.0
 */
@Slf4j
public class DefaultDataSetResultFilterManagerImpl implements DataSetResultFilterManager {

    private final DataSetResultStepExecutor stepExecutor;

    public DefaultDataSetResultFilterManagerImpl(List<DataSetResultStep> steps) {
        this.stepExecutor = new DataSetResultStepExecutor(steps != null ? steps : Collections.emptyList());

        if (log.isDebugEnabled()) {
            log.debug("DataSetResultFilterManager initialized with {} steps", stepExecutor.size());
        }
    }

    @Override
    public PagingResultImpl process(PagingRequest<DbQueryRequestDef> form, PagingResultImpl pagingResult) {
        ModelResultContext ctx = new ModelResultContext(form, pagingResult);
        process(ctx);
        return ctx.getPagingResult();
    }

    @Override
    public PagingRequest<DbQueryRequestDef> beforeQuery(PagingRequest<DbQueryRequestDef> form) {
        ModelResultContext ctx = new ModelResultContext(form, null);
        beforeQuery(ctx);
        return form;
    }

    @Override
    public void process(ModelResultContext ctx) {
        if (stepExecutor.hasSteps()) {
            int result = stepExecutor.executeProcess(ctx);
            if (result != DataSetResultStep.CONTINUE) {
                log.debug("Step execution stopped with result: {}", result);
            }
        }
    }

    @Override
    public void beforeQuery(ModelResultContext ctx) {
        if (stepExecutor.hasSteps()) {
            int result = stepExecutor.executeBeforeQuery(ctx);
            if (result != DataSetResultStep.CONTINUE) {
                log.debug("Step beforeQuery stopped with result: {}", result);
            }
        }
    }

    /**
     * 获取步骤执行器
     */
    public DataSetResultStepExecutor getStepExecutor() {
        return stepExecutor;
    }
}

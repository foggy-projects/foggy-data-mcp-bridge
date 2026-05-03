package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.algo.SubtotalInjector;

/**
 * Phase 2.6: 小计/总计注入 (cache-aware)。
 *
 * <p>仅在 needsSubtotal 时执行。使用 rollupPlans 和 rollupCache
 * 为 non-additive metrics 注入精确的辅助查询结果。</p>
 */
public class SubtotalInjectionProcessor implements PivotPhase2Processor {

    @Override
    public void process(PivotPhase2Context ctx) {
        if (ctx.needsSubtotal()) {
            ctx.setResultSet(SubtotalInjector.apply(ctx.getResultSet(),
                    ctx.getRowFields(), ctx.getColFields(), ctx.getMetrics(), ctx.getOptions(),
                    ctx.getRollupPlans(), ctx.getRollupCache()));
        }
    }
}

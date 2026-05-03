package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.algo.AxisHavingFilter;

/**
 * Phase 2.1: 轴级 Having 过滤。
 *
 * <p>仅在非 SQL pushdown 路径执行（pushdown 场景已在 SQL 层完成）。</p>
 */
public class AxisHavingProcessor implements PivotPhase2Processor {

    @Override
    public void process(PivotPhase2Context ctx) {
        if (ctx.isSqlPushdownUsed()) {
            return;
        }
        ctx.setResultSet(AxisHavingFilter.apply(ctx.getResultSet(), ctx.getPivot().getRows(), ctx.getMetrics()));
        ctx.setResultSet(AxisHavingFilter.apply(ctx.getResultSet(), ctx.getPivot().getColumns(), ctx.getMetrics()));
    }
}

package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.algo.AxisTopNTruncator;

/**
 * Phase 2.2: 轴向 TopN 截断。
 *
 * <p>仅在非 SQL pushdown 路径执行（pushdown 场景已在 SQL 层完成）。</p>
 */
public class AxisTopNProcessor implements PivotPhase2Processor {

    @Override
    public void process(PivotPhase2Context ctx) {
        if (ctx.isSqlPushdownUsed()) {
            ctx.getLogger().debug("[Pivot] Phase 2: Skipping Having/TopN (already done in SQL pushdown)");
            return;
        }
        ctx.setResultSet(AxisTopNTruncator.apply(ctx.getResultSet(), ctx.getPivot().getRows()));
        ctx.setResultSet(AxisTopNTruncator.apply(ctx.getResultSet(), ctx.getPivot().getColumns()));
    }
}

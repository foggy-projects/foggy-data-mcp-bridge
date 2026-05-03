package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.algo.CrossJoinFiller;

/**
 * Phase 2.5: 骨架补全（CrossJoin）。
 *
 * <p>仅在 options.crossjoin=true 时执行，使用 rowDomain × colDomain 补全缺失交叉。</p>
 */
public class CrossJoinProcessor implements PivotPhase2Processor {

    @Override
    public void process(PivotPhase2Context ctx) {
        if (ctx.getOptions().isCrossjoin()) {
            ctx.setResultSet(CrossJoinFiller.apply(ctx.getResultSet(),
                    ctx.getRowFields(), ctx.getColFields(), ctx.getMetrics(),
                    ctx.getRowDomain(), ctx.getColDomain()));
        }
    }
}

package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.CardinalityBreaker;

import java.util.List;
import java.util.Set;

/**
 * Phase 2 (pre-rollup): 提取轴域并执行基数熔断校验。
 *
 * <p>在 Having/TopN 之后、Rollup 之前执行，确保 surviving domain 不超限。</p>
 */
public class DomainExtractionProcessor implements PivotPhase2Processor {

    private final CardinalityBreaker cardinalityBreaker;

    public DomainExtractionProcessor(CardinalityBreaker cardinalityBreaker) {
        this.cardinalityBreaker = cardinalityBreaker;
    }

    @Override
    public void process(PivotPhase2Context ctx) {
        Set<List<Object>> rowDomain = CardinalityBreaker.extractRowDomain(ctx.getResultSet(), ctx.getRowFields());
        Set<List<Object>> colDomain = CardinalityBreaker.extractColumnDomain(ctx.getResultSet(), ctx.getColFields());
        cardinalityBreaker.checkEstimate(rowDomain.size(), colDomain.size(), ctx.getPivot());
        ctx.setRowDomain(rowDomain);
        ctx.setColDomain(colDomain);
    }
}

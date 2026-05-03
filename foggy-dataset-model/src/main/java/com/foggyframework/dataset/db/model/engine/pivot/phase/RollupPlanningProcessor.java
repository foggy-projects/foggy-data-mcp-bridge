package com.foggyframework.dataset.db.model.engine.pivot.phase;

import com.foggyframework.dataset.db.model.engine.pivot.PivotTelemetry;
import com.foggyframework.dataset.db.model.engine.pivot.rollup.*;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;

import java.util.List;

/**
 * Phase 2.3-2.4: Rollup 规划与辅助查询。
 *
 * <p>仅在需要小计/总计时执行。包含：
 * <ul>
 *   <li>MetricAdditivityAnalyzer 分析 rollup 策略</li>
 *   <li>UNSUPPORTED 策略 fail-closed</li>
 *   <li>Non-additive 辅助查询（UNION ALL 或串行降级）</li>
 *   <li>Domain limit exceeded fail-closed</li>
 * </ul>
 * </p>
 */
public class RollupPlanningProcessor implements PivotPhase2Processor {

    private final SemanticQueryServiceV3 semanticQueryService;

    public RollupPlanningProcessor(SemanticQueryServiceV3 semanticQueryService) {
        this.semanticQueryService = semanticQueryService;
    }

    @Override
    public void process(PivotPhase2Context ctx) {
        if (!ctx.needsSubtotal()) {
            return;
        }

        List<RollupMetricPlan> rollupPlans = MetricAdditivityAnalyzer.analyze(
                ctx.getMetrics(), ctx.getQueryModel(), ctx.getRequest().getCalculatedFields());
        ctx.getLogger().debug("[Pivot] Phase 2.3: Rollup plans: {}", rollupPlans);

        // 检查是否有不支持的 metric 参与 subtotal
        for (RollupMetricPlan plan : rollupPlans) {
            if (plan.getStrategy() == RollupStrategy.UNSUPPORTED) {
                throw new IllegalArgumentException(
                        "度量 '" + plan.getMetricName() + "' 的聚合类型（" +
                        plan.getAggregation() + "）不支持参与小计/总计。" +
                        "请移除该度量或关闭 rowSubtotals/columnSubtotals/grandTotal");
            }
        }

        ctx.setRollupPlans(rollupPlans);

        // 如果有 non-additive metrics，执行辅助查询
        if (MetricAdditivityAnalyzer.hasNonAdditiveMetrics(rollupPlans)) {
            List<RollupGrain> grains = RollupGrainEnumerator.enumerate(
                    ctx.getRowFields(), ctx.getColFields(), ctx.getOptions());
            ctx.getLogger().debug("[Pivot] Phase 2.4: Auxiliary rollup queries, {} grains", grains.size());

            NonAdditiveRollupExecutor executor = new NonAdditiveRollupExecutor(semanticQueryService);
            try {
                RollupCache cache = executor.execute(ctx.getModel(), ctx.getRequest(), ctx.getContext(),
                        grains, rollupPlans, ctx.getRowFields(), ctx.getColFields(),
                        ctx.getRowDomain(), ctx.getColDomain());
                ctx.setRollupCache(cache);
            } catch (NonAdditiveRollupDomainTooLargeException e) {
                // Stage 4 fail-closed: SQL pushdown 后 surviving domain 超限，
                // 无法为 non-additive subtotal 生成精确 tuple 约束。
                PivotTelemetry.domainLimitExceeded(ctx.getLogger(), ctx.getModel(),
                        e.getDomainSize(), e.getMaxAllowed(),
                        ctx.isSqlPushdownUsed(), ctx.getRowDomain().size(), ctx.getColDomain().size());
                throw new IllegalStateException(
                        "Pivot subtotal/grandTotal: non-additive metric (AVG/COUNT_DISTINCT) 的辅助查询 " +
                        "surviving domain 超过安全限制（" + e.getDomainSize() + " > " + e.getMaxAllowed() + "）。" +
                        "请减少 TopN limit 数量，或关闭 rowSubtotals/columnSubtotals/grandTotal。", e);
            }
        }
    }
}

package com.foggyframework.dataset.db.model.engine.pivot.rollup;

import com.foggyframework.dataset.db.model.spi.DbAggregation;

import java.util.Collections;
import java.util.List;

/**
 * 单个度量的 Rollup 计划
 *
 * <p>描述一个 metric 在小计/总计行中应如何计算。</p>
 */
public class RollupMetricPlan {

    private final String metricName;
    private final RollupStrategy strategy;
    private final DbAggregation aggregation;

    /** RECOMPUTE_FROM_BASE 时需要的底层度量名列表 */
    private final List<String> requiredBaseMetrics;

    /** calculatedField 的表达式（用于 recompute） */
    private final String expression;

    public RollupMetricPlan(String metricName, RollupStrategy strategy, DbAggregation aggregation) {
        this(metricName, strategy, aggregation, Collections.emptyList(), null);
    }

    public RollupMetricPlan(String metricName, RollupStrategy strategy, DbAggregation aggregation,
                            List<String> requiredBaseMetrics, String expression) {
        this.metricName = metricName;
        this.strategy = strategy;
        this.aggregation = aggregation;
        this.requiredBaseMetrics = requiredBaseMetrics != null ? requiredBaseMetrics : Collections.emptyList();
        this.expression = expression;
    }

    public String getMetricName() { return metricName; }
    public RollupStrategy getStrategy() { return strategy; }
    public DbAggregation getAggregation() { return aggregation; }
    public List<String> getRequiredBaseMetrics() { return requiredBaseMetrics; }
    public String getExpression() { return expression; }

    /**
     * 是否需要辅助聚合查询
     */
    public boolean needsAuxQuery() {
        return strategy == RollupStrategy.AUX_REQUERY
                || strategy == RollupStrategy.RECOMPUTE_FROM_BASE;
    }

    @Override
    public String toString() {
        return metricName + ":" + strategy +
                (requiredBaseMetrics.isEmpty() ? "" : " base=" + requiredBaseMetrics);
    }
}

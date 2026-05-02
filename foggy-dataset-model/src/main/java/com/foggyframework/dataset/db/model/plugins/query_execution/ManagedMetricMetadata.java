package com.foggyframework.dataset.db.model.plugins.query_execution;

import lombok.Builder;
import lombok.Getter;

/**
 * 受管关系中单个度量的元数据
 *
 * <p>由 {@code prepareManagedRelation} 阶段产出，写入 {@link ManagedSqlRelation}。
 * 外层 Planner 消费此 metadata 决定聚合策略，不重新推断。</p>
 */
@Getter
@Builder
public class ManagedMetricMetadata {

    /**
     * 度量字段名（与 base relation SELECT 列别名一致）
     */
    private final String metricName;

    /**
     * 可加性分类
     */
    private final AdditiveKind additiveKind;

    /**
     * 度量的聚合函数名称（如 SUM, AVG, COUNT_DISTINCT）
     * <p>用于 Planner 在 domain CTE 中选择正确的再聚合函数。
     * 仅当 {@code additiveKind == ADDITIVE} 时 Planner 才会使用此值。</p>
     */
    private final String aggregationFunction;
}

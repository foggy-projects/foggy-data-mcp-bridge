package com.foggyframework.dataset.model.plugins.query_execution;

/**
 * 受管关系中度量的可加性分类
 *
 * <p>由 queryModel prepare 阶段写入 {@link ManagedMetricMetadata}，
 * 供外层 Planner 判断该度量是否可安全参与 domain 聚合（如 TopN 排序、Having 过滤）。</p>
 *
 * <p>Planner 不应自行推断可加性，避免与 queryModel 对同一字段得出不同判断。</p>
 */
public enum AdditiveKind {
    /**
     * 可加度量（SUM, COUNT, MIN, MAX）
     * <p>可在 domain CTE 中安全聚合。</p>
     */
    ADDITIVE,

    /**
     * 不可加度量（AVG, COUNT_DISTINCT, 比率类 calculatedFields）
     * <p>不能在 domain CTE 中直接聚合；参与 orderBy/having 时 Planner 必须 fail-closed。</p>
     */
    NON_ADDITIVE,

    /**
     * 无法判断可加性（通常是 calculatedFields 无法解析语义时）
     * <p>Planner 必须视同 NON_ADDITIVE，fail-closed。</p>
     */
    UNKNOWN
}

package com.foggyframework.dataset.model.plugins.query_execution;

/**
 * 查询执行阶段
 * <p>
 * 用于标识当前处于 QueryModel 生命周期的哪一个阶段，
 * 从而决定 {@link QueryExecutionStep} 的行为。
 */
public enum QueryExecutionPhase {
    /**
     * 普通查询执行
     */
    NORMAL_QUERY,

    /**
     * 准备受管关系代数
     * <p>仅执行预聚合重写、物理列权限等修改 SQL / 拦截风险的操作，
     * 但不执行缓存读短路（或读但不短路），不执行真实数据库访问。
     */
    PREPARE_MANAGED_RELATION,

    /**
     * 执行受管关系代数
     * <p>外层完成 SQL 包装后调用，处理缓存写入、SQL 执行后逻辑。
     */
    EXECUTE_MANAGED_RELATION
}

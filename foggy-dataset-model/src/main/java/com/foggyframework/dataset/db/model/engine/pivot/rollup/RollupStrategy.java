package com.foggyframework.dataset.db.model.engine.pivot.rollup;

/**
 * 度量 Rollup 策略
 *
 * <p>定义小计/总计场景中每个度量的计算方式。</p>
 */
public enum RollupStrategy {

    /** SUM/COUNT: 子节点直接内存求和 */
    IN_MEMORY_SUM,

    /** MIN: 子节点取最小值 */
    IN_MEMORY_MIN,

    /** MAX: 子节点取最大值 */
    IN_MEMORY_MAX,

    /** AVG/COUNT_DISTINCT 等: 需要辅助聚合查询获取父级 grain 正确值 */
    AUX_REQUERY,

    /** 比例型 calculatedFields: 从 base metrics 在父级 grain 上重算表达式 */
    RECOMPUTE_FROM_BASE,

    /** GROUP_CONCAT/CUSTOM/WINDOW/NONE/PK 等: 不支持参与小计 */
    UNSUPPORTED
}

package com.foggyframework.dataset.model.def.preagg;

import lombok.Data;

/**
 * 预聚合刷新配置定义
 * <p>
 * 定义预聚合表的刷新策略和调度配置。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class PreAggRefreshDef {

    /**
     * 刷新策略：FULL（全量）, INCREMENTAL（增量）
     */
    private String strategy = "FULL";

    /**
     * 调度表达式（Cron 格式）
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code 0 2 * * *} - 每天凌晨2点</li>
     *   <li>{@code 0 3 1 * *} - 每月1日凌晨3点</li>
     *   <li>{@code 0 0/6 * * *} - 每6小时</li>
     * </ul>
     * </p>
     */
    private String schedule;

    /**
     * 水位线列名（用于增量刷新）
     * <p>
     * 格式：dimensionName$propertyName 或 dimensionName$id
     * </p>
     */
    private String watermarkColumn;

    /**
     * 回溯天数（用于处理迟到数据）
     * <p>
     * 增量刷新时，会额外刷新最近 N 天的数据以处理迟到记录。
     * </p>
     */
    private Integer lookbackDays;

    /**
     * 是否为全量刷新策略
     */
    public boolean isFullRefresh() {
        return "FULL".equalsIgnoreCase(strategy);
    }

    /**
     * 是否为增量刷新策略
     */
    public boolean isIncrementalRefresh() {
        return "INCREMENTAL".equalsIgnoreCase(strategy);
    }
}

package com.foggyframework.dataset.mcp.config;

import lombok.Data;

/**
 * 性能信息模型
 *
 * 对应工具配置中的性能提示
 */
@Data
public class PerformanceInfo {

    /**
     * 平均响应时间
     */
    private String averageResponseTime;

    /**
     * 最大数据点数
     */
    private Integer maxDataPoints;

    /**
     * 数据点限制
     */
    private Integer dataPointsLimit;

    /**
     * 推荐分页
     */
    private Boolean paginationRecommended;

    /**
     * 推荐流式传输
     */
    private Boolean streamingRecommended;

    /**
     * 是否可缓存
     */
    private Boolean cacheable;

    /**
     * returnTotal性能说明
     */
    private String returnTotalPerformance;

    /**
     * limit建议
     */
    private String limitRecommendation;

    /**
     * 组合两个操作
     */
    private Boolean combinesTwoOperations;
}

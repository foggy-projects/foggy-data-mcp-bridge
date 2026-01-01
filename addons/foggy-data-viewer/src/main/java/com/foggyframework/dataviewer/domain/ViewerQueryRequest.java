package com.foggyframework.dataviewer.domain;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 前端查询请求
 * <p>
 * 用于数据浏览器前端发起的数据查询请求
 */
@Data
public class ViewerQueryRequest {

    /**
     * 起始位置
     */
    private Integer start = 0;

    /**
     * 每页大小
     */
    private Integer limit = 50;

    /**
     * 用户在浏览器中添加的额外过滤条件
     */
    private List<Map<String, Object>> additionalFilters;

    /**
     * 覆盖排序条件
     */
    private List<Map<String, Object>> orderBy;

    /**
     * 动态分组字段（聚合模式）
     */
    private List<String> groupByFields;

    /**
     * 聚合项
     */
    private List<AggregationItem> aggregations;

    @Data
    public static class AggregationItem {
        private String field;
        private String type; // sum, avg, min, max, count
    }
}

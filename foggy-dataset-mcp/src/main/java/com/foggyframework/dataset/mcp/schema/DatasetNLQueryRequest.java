package com.foggyframework.dataset.mcp.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据集自然语言查询请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatasetNLQueryRequest {

    /**
     * 自然语言查询
     */
    private String query;

    /**
     * 会话ID（用于上下文保持）
     */
    private String sessionId;

    /**
     * 分页游标
     */
    private String cursor;

    /**
     * 返回格式：table, json, summary
     */
    @Builder.Default
    private String format = "table";

    /**
     * 查询提示
     */
    private QueryHints hints;

    /**
     * 是否启用流式响应
     */
    @Builder.Default
    private Boolean stream = true;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueryHints {
        /**
         * 时间范围
         */
        private TimeRange timeRange;

        /**
         * 数据源：auto, dataset, olap
         */
        private String dataSource;

        /**
         * 优先使用的模型
         */
        private List<String> preferredModels;

        /**
         * 额外提示
         */
        private Map<String, Object> extra;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeRange {
        /**
         * 预设范围：last_7d, last_30d, this_month, last_month, this_year
         */
        private String preset;

        /**
         * 开始时间
         */
        private String start;

        /**
         * 结束时间
         */
        private String end;
    }
}

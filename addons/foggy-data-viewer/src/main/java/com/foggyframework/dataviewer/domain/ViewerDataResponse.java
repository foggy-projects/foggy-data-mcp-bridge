package com.foggyframework.dataviewer.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 数据浏览器响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViewerDataResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 数据项
     */
    private List<Map<String, Object>> items;

    /**
     * 总行数
     */
    private Long total;

    /**
     * 起始位置
     */
    private Integer start;

    /**
     * 每页大小
     */
    private Integer limit;

    /**
     * 是否有更多数据
     */
    private boolean hasMore;

    /**
     * 聚合结果汇总（聚合模式时使用）
     */
    private Map<String, Object> aggregationSummary;

    /**
     * 全量数据汇总（包含总记录数和度量合计）
     * 由后端 returnTotal=true 时返回
     */
    private Object totalData;

    /**
     * 创建成功响应
     */
    public static ViewerDataResponse success(List<Map<String, Object>> items, Long total,
                                              Integer start, Integer limit) {
        return ViewerDataResponse.builder()
                .success(true)
                .items(items)
                .total(total)
                .start(start)
                .limit(limit)
                .hasMore(total != null && (start + items.size()) < total)
                .build();
    }

    /**
     * 创建成功响应（包含汇总数据）
     */
    public static ViewerDataResponse success(List<Map<String, Object>> items, Long total,
                                              Object totalData, Integer start, Integer limit) {
        return ViewerDataResponse.builder()
                .success(true)
                .items(items)
                .total(total)
                .totalData(totalData)
                .start(start)
                .limit(limit)
                .hasMore(total != null && (start + items.size()) < total)
                .build();
    }

    /**
     * 创建链接已过期的响应
     */
    public static ViewerDataResponse expired(String message) {
        return ViewerDataResponse.builder()
                .success(false)
                .error(message)
                .build();
    }

    /**
     * 创建错误响应
     */
    public static ViewerDataResponse error(String message) {
        return ViewerDataResponse.builder()
                .success(false)
                .error(message)
                .build();
    }
}

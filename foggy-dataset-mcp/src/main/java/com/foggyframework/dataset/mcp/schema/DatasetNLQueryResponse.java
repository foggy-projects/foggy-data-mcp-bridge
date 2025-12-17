package com.foggyframework.dataset.mcp.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据集查询响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatasetNLQueryResponse {

    /**
     * 响应类型：result, info, clarify, partial_result, error
     */
    private String type;

    /**
     * 查询结果数据
     */
    private List<Map<String, Object>> items;

    /**
     * 数据模式/字段定义
     */
    private Map<String, Object> schema;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 是否还有更多数据
     */
    private Boolean hasNext;

    /**
     * 分页游标
     */
    private String cursor;

    /**
     * 摘要说明
     */
    private String summary;

    /**
     * 备注信息
     */
    private String note;

    /**
     * 导出信息
     */
    private ExportsInfo exports;

    /**
     * 调试信息
     */
    private Map<String, Object> debug;

    // ========== Info 类型响应字段 ==========
    /**
     * 信息主题：models, fields, dictionaries, model_overview, usage, limits
     */
    private String topic;

    /**
     * 信息数据
     */
    private Object data;

    // ========== Clarify 类型响应字段 ==========
    /**
     * 澄清问题列表
     */
    private List<String> questions;

    /**
     * 候选选项
     */
    private Object candidates;

    /**
     * 示例
     */
    private Object examples;

    /**
     * 缺失信息
     */
    private Object missing;

    // ========== Error 类型响应字段 ==========
    /**
     * 错误代码
     */
    private String code;

    /**
     * 错误消息
     */
    private String msg;

    /**
     * 错误详情
     */
    private Object detail;

    /**
     * 导出信息（图表和Excel）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExportsInfo {
        private List<ChartExport> charts;
        private List<ExcelExport> excel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChartExport {
        private String url;
        private String type;
        private String title;
        private String format;
        private Integer width;
        private Integer height;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExcelExport {
        private String url;
        private String fileName;
        private List<String> sheets;
        private Integer rowCount;
        private Long fileSize;
        private LocalDateTime expiresAt;
    }

    /**
     * 创建查询结果响应
     */
    public static DatasetNLQueryResponse result(List<Map<String, Object>> items, Long total, String summary) {
        return DatasetNLQueryResponse.builder()
                .type("result")
                .items(items)
                .total(total)
                .summary(summary)
                .build();
    }

    /**
     * 创建信息响应
     */
    public static DatasetNLQueryResponse info(String topic, Object data, String note) {
        return DatasetNLQueryResponse.builder()
                .type("info")
                .topic(topic)
                .data(data)
                .note(note)
                .build();
    }

    /**
     * 创建澄清响应
     */
    public static DatasetNLQueryResponse clarify(List<String> questions, Object candidates) {
        return DatasetNLQueryResponse.builder()
                .type("clarify")
                .questions(questions)
                .candidates(candidates)
                .build();
    }

    /**
     * 创建错误响应
     */
    public static DatasetNLQueryResponse error(String code, String msg, Object detail) {
        return DatasetNLQueryResponse.builder()
                .type("error")
                .code(code)
                .msg(msg)
                .detail(detail)
                .build();
    }
}

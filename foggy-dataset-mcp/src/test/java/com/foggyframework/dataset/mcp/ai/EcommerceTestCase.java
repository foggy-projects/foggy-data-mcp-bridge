package com.foggyframework.dataset.mcp.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 电商场景 AI 测试用例
 *
 * 用于验证 AI 模型通过 MCP 工具执行数据查询的正确性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EcommerceTestCase {

    /**
     * 用例唯一ID
     * 例如: META-001, QUERY-001, AGG-001
     */
    private String id;

    /**
     * 测试分类
     */
    private TestCategory category;

    /**
     * 自然语言问题
     */
    private String question;

    /**
     * 期望使用的工具名称
     * 例如: dataset.get_metadata, dataset.query_model_v2
     */
    @JsonProperty("expected_tool")
    private String expectedTool;

    /**
     * 目标模型名称（对于查询类工具）
     * 例如: FactSalesQueryModel, FactOrderQueryModel
     */
    @JsonProperty("target_model")
    private String targetModel;

    /**
     * 预期结果验证规则
     */
    private ExpectedResult expected;

    /**
     * 测试用例描述（便于理解）
     */
    private String description;

    /**
     * 难度级别
     */
    private DifficultyLevel difficulty;

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 测试分类枚举
     */
    public enum TestCategory {
        /**
         * 元数据查询
         */
        METADATA,

        /**
         * 模型描述查询
         */
        DESCRIBE,

        /**
         * 简单查询（单表、无聚合）
         */
        SIMPLE_QUERY,

        /**
         * 带过滤条件的查询
         */
        FILTER_QUERY,

        /**
         * 聚合查询
         */
        AGGREGATION,

        /**
         * 多维度分析
         */
        MULTI_DIMENSION,

        /**
         * 时间范围查询
         */
        TIME_RANGE,

        /**
         * 排序和分页
         */
        SORT_PAGINATION,

        /**
         * 复杂组合查询
         */
        COMPLEX
    }

    /**
     * 难度级别
     */
    public enum DifficultyLevel {
        EASY,
        MEDIUM,
        HARD
    }

    /**
     * 预期结果定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExpectedResult {

        /**
         * 必须包含的列名
         */
        @JsonProperty("required_columns")
        private List<String> requiredColumns;

        /**
         * 禁止包含的列名
         */
        @JsonProperty("forbidden_columns")
        private List<String> forbiddenColumns;

        /**
         * 期望的数据样本（用于精确匹配）
         */
        private List<Map<String, Object>> data;

        /**
         * 最小行数
         */
        @JsonProperty("min_rows")
        private Integer minRows;

        /**
         * 最大行数
         */
        @JsonProperty("max_rows")
        private Integer maxRows;

        /**
         * 精确行数
         */
        @JsonProperty("exact_rows")
        private Integer exactRows;

        /**
         * 验证规则列表
         */
        private List<ValidationRule> rules;

        /**
         * 是否仅验证不报错（宽松模式）
         */
        @JsonProperty("success_only")
        @Builder.Default
        private boolean successOnly = false;
    }

    /**
     * 验证规则
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ValidationRule {

        /**
         * 规则类型
         */
        private RuleType type;

        /**
         * 目标列名
         */
        private String column;

        /**
         * 期望值
         */
        private Object value;

        /**
         * 操作符（对于比较规则）
         */
        private String operator;

        /**
         * 附加参数
         */
        private Map<String, Object> params;
    }

    /**
     * 规则类型枚举
     */
    public enum RuleType {
        /**
         * 检查列是否存在
         */
        COLUMN_EXISTS,

        /**
         * 检查行数
         */
        ROW_COUNT,

        /**
         * 检查行数范围
         */
        ROW_COUNT_RANGE,

        /**
         * 检查某列的值包含指定内容
         */
        VALUE_CONTAINS,

        /**
         * 检查某列的值在范围内
         */
        VALUE_IN_RANGE,

        /**
         * 检查某列的聚合值（SUM/AVG/MAX/MIN）
         */
        AGGREGATE_VALUE,

        /**
         * 检查排序是否正确
         */
        ORDER_BY,

        /**
         * 检查返回的模型列表包含指定模型
         */
        CONTAINS_MODEL,

        /**
         * 检查返回结果不为空
         */
        NOT_EMPTY,

        /**
         * 自定义表达式验证
         */
        EXPRESSION
    }

    /**
     * 获取测试用例的简短描述
     */
    public String getShortDescription() {
        if (description != null && !description.isEmpty()) {
            return description.length() > 50 ? description.substring(0, 47) + "..." : description;
        }
        return question.length() > 50 ? question.substring(0, 47) + "..." : question;
    }

    /**
     * 判断是否为元数据相关测试
     */
    public boolean isMetadataTest() {
        return category == TestCategory.METADATA || category == TestCategory.DESCRIBE;
    }

    /**
     * 判断是否为查询类测试
     */
    public boolean isQueryTest() {
        return !isMetadataTest();
    }
}

package com.foggyframework.dataset.db.model.semantic.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foggyframework.dataset.db.model.spi.DbColumnType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 语义查询响应
 */
@Data
@ApiModel("语义查询响应")
public class SemanticQueryResponse {

    @ApiModelProperty(value = "查询结果数据")
    private List<Map<String, Object>> items;

    @ApiModelProperty(value = "结果集schema信息")
    private SchemaInfo schema;

    @ApiModelProperty(value = "分页信息", notes = "包含实际使用的分页参数和数据范围信息")
    private PaginationInfo pagination;

    @ApiModelProperty(value = "总记录数，可选")
    private Long total;

    @ApiModelProperty(value = "汇总数据", notes = "应jdbc-model版本，加入汇总数据项，用于返回除了数量之外的其他汇总信息")
    private Object totalData;

    @ApiModelProperty(value = "是否有下一页")
    private Boolean hasNext;

    @ApiModelProperty(value = "分页游标")
    private String cursor;

    @ApiModelProperty(value = "警告信息")
    private List<String> warnings;

    @ApiModelProperty(value = "调试信息")
    private DebugInfo debug;

    @ApiModelProperty(value = "数据截断信息（仅在 MCP 查询且数据量过大时返回）")
    private Map<String, Object> truncationInfo;

    @ApiModelProperty(value = "结果语义提示，帮助上游区分成功但无匹配数据等终态")
    private SemanticInfo semantic;

    @ApiModelProperty(value = "执行计划终态或路由信息")
    private ExecutionInfo execution;

    /**
     * 分页信息
     */
    @Data
    @ApiModel("分页信息")
    public static class PaginationInfo {

        @ApiModelProperty(value = "起始位置（从0开始）", example = "0")
        private Integer start;

        @ApiModelProperty(value = "每页限制条数", example = "10")
        private Integer limit;

        @ApiModelProperty(value = "本次实际返回条数", example = "10")
        private Integer returned;

        @ApiModelProperty(value = "总记录数（仅当 returnTotal=true 时有值）", example = "156")
        private Long totalCount;

        @ApiModelProperty(value = "是否还有更多数据", example = "true",
                notes = "true 表示数据库中还有更多记录未返回，AI 应提示用户可以继续查询下一页或增加 limit")
        private Boolean hasMore;

        @ApiModelProperty(value = "数据范围说明（人类可读）", example = "显示第 1-10 条，共 156 条",
                notes = "帮助 AI 向用户解释当前数据范围")
        private String rangeDescription;
    }

    @Data
    @ApiModel("结果语义提示")
    public static class SemanticInfo {

        @ApiModelProperty(value = "是否为空结果")
        private Boolean emptyResult;

        @ApiModelProperty(value = "空结果原因")
        private String emptyReason;

        @ApiModelProperty(value = "上游是否应直接回答而不是继续探查")
        private Boolean shouldAnswerDirectly;
    }

    @Data
    @ApiModel("执行计划信息")
    public static class ExecutionInfo {

        @ApiModelProperty(value = "路由结果，如 DSL_CTE、SEMANTIC_SQL、CLARIFY、REJECT")
        private String route;

        @ApiModelProperty(value = "执行计划状态，如 PLAN_READY、CLARIFY、REJECT、COMPILE_ERROR")
        private String status;

        @ApiModelProperty(value = "治理或语义风险标签")
        @JsonProperty("risk_flags")
        private List<String> riskFlags;

        @ApiModelProperty(value = "拒绝或澄清原因")
        private List<String> why;

        @ApiModelProperty(value = "澄清问题")
        @JsonProperty("clarifying_questions")
        private List<String> clarifyingQuestions;

        @ApiModelProperty(value = "可执行计划。CLARIFY/REJECT 终态必须为空")
        @JsonProperty("executable_plan")
        private Object executablePlan;

        @ApiModelProperty(value = "DSL_CTE 阶段计划校验证据。仅表示 P0 contract 通过，不表示已执行")
        @JsonProperty("dsl_cte_validation")
        private Map<String, Object> dslCteValidation;

        @ApiModelProperty(value = "虚拟语义 SQL。仅作为表达增强 contract，不表示物理 SQL 已执行")
        @JsonProperty("semantic_sql")
        private String semanticSql;

        @ApiModelProperty(value = "虚拟语义 SQL AST 白名单校验证据")
        @JsonProperty("ast_validation")
        private Map<String, Object> astValidation;

        @ApiModelProperty(value = "虚拟语义 SQL 到 DSL v1 子集映射证据。仅表示计划可审计，不表示已执行")
        @JsonProperty("semantic_sql_dsl_plan")
        private Map<String, Object> semanticSqlDslPlan;

        @ApiModelProperty(value = "Memory Grid 二次分析计划。仅表示 guardrail 通过，不表示已执行")
        @JsonProperty("memory_grid_plan")
        private Map<String, Object> memoryGridPlan;

        @ApiModelProperty(value = "Memory Grid guardrail 校验证据")
        @JsonProperty("memory_grid_validation")
        private Map<String, Object> memoryGridValidation;

        @ApiModelProperty(value = "Memory Grid 最小执行闭环摘要。仅在显式执行且 resolver 解析成功时返回")
        @JsonProperty("memory_grid_execution_summary")
        private Map<String, Object> memoryGridExecutionSummary;

        @ApiModelProperty(value = "错误码")
        @JsonProperty("error_code")
        private String errorCode;
    }
    
    /**
     * Schema信息
     */
    @Data
    @ApiModel("Schema信息")
    public static class SchemaInfo {

        @ApiModelProperty(value = "列定义")
        private List<ColumnDef> columns;

        @ApiModelProperty(value = "结果摘要（Markdown格式）", notes = "帮助AI理解查询结果的结构")
        private String summary;

        /**
         * 列定义
         */
        @Data
        @ApiModel("列定义")
        public static class ColumnDef {

            @ApiModelProperty(value = "列名", example = "team$caption")
            private String name;

            @ApiModelProperty(value = "数据类型", example = "STRING")
            private DbColumnType dataType;

            @ApiModelProperty(value = "列标题", example = "团队")
            private String title;

            @ApiModelProperty(value = "是否由时间窗口(timeWindow DSL)派生生成")
            private Boolean derivedFromTimeWindow;

            @ApiModelProperty(value = "时间窗口派生字段的语义说明")
            private String timeWindowDesc;
        }
    }
    
    /**
     * 调试信息
     */
    @Data
    @ApiModel("调试信息")
    public static class DebugInfo {
        
        @ApiModelProperty(value = "归一化后的请求")
        private NormalizedRequest normalized;
        
        @ApiModelProperty(value = "执行耗时(毫秒)")
        private Long durationMs;
        
        @ApiModelProperty(value = "其他调试信息")
        private Map<String, Object> extra;
        
        /**
         * 归一化后的请求
         */
        @Data
        @ApiModel("归一化后的请求")
        public static class NormalizedRequest {
            
            @ApiModelProperty(value = "归一化后的过滤条件")
            private List<SemanticQueryRequest.SliceItem> slice;

            @ApiModelProperty(value = "归一化后的聚合后过滤条件")
            private List<SemanticQueryRequest.SliceItem> having;
            
            @ApiModelProperty(value = "归一化后的分组字段")
            private List<SemanticQueryRequest.GroupByItem> groupBy;
            
            @ApiModelProperty(value = "归一化后的排序字段")
            private List<SemanticQueryRequest.OrderItem> orderBy;
        }
    }
}

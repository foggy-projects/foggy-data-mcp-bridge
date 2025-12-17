package com.foggyframework.dataset.jdbc.model.semantic.domain;

import com.foggyframework.dataset.jdbc.model.spi.JdbcColumnType;
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
            private JdbcColumnType dataType;

            @ApiModelProperty(value = "列标题", example = "团队")
            private String title;
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
            
            @ApiModelProperty(value = "归一化后的分组字段")
            private List<SemanticQueryRequest.GroupByItem> groupBy;
            
            @ApiModelProperty(value = "归一化后的排序字段")
            private List<SemanticQueryRequest.OrderItem> orderBy;
        }
    }
}
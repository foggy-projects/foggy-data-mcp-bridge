package com.foggyframework.dataset.db.model.semantic.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.semantic.enums.CaptionMatchMode;
import com.foggyframework.dataset.db.model.semantic.enums.MismatchHandleStrategy;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 语义查询请求
 * 支持 $caption 条件/分组/排序，服务端将其归一化为稳定的 $id 后执行
 */
@Data
@ApiModel("语义查询请求")
public class SemanticQueryRequest {

    @ApiModelProperty(value = "查询列，可含 $caption", example = "[\"customerName\", \"team$caption\", \"totalAmount\"]")
    private List<String> columns;

    @ApiModelProperty(value = "计算字段定义列表，支持动态创建基于表达式的虚拟字段")
    private List<CalculatedFieldDef> calculatedFields;

    @ApiModelProperty(value = "过滤条件")
    private List<SliceItem> slice;

    @ApiModelProperty(value = "分组字段，可含 $caption", example = "[\"team$caption\"]")
    private List<GroupByItem> groupBy;

    @ApiModelProperty(value = "排序字段")
    private List<OrderItem> orderBy;

    @ApiModelProperty(value = "起始位置，用于分页，默认0", example = "0")
    private Integer start = 0;

    @ApiModelProperty(value = "返回数量限制", example = "在不同的场景下，有不同的默认值，例如导出图片时，默认1000，或后续根据配置决定")
    private Integer limit;

    @ApiModelProperty(value = "游标，用于分页（暂未支持）")
    private String cursor;

    @ApiModelProperty(value = "查询提示，可选")
    private Map<String, Object> hints;

    @ApiModelProperty(value = "是否启用流式返回", example = "false")
    private Boolean stream;

    @ApiModelProperty(value = "Caption匹配模式，默认EXACT（精准匹配）", example = "EXACT")
    private CaptionMatchMode captionMatchMode = CaptionMatchMode.EXACT;

    @ApiModelProperty(value = "匹配失败处理策略，默认ABORT（中止查询）", example = "ABORT")
    private MismatchHandleStrategy mismatchHandleStrategy = MismatchHandleStrategy.ABORT;

    @ApiModelProperty(value = "是否返回总记录数。当用户询问'共有多少条'、'总数是多少'、需要分页显示总页数时，应设为 true", example = "true")
    private Boolean returnTotal = false;

    /**
     * 过滤条件项
     */
    @Data
    @ApiModel("过滤条件项")
    public static class SliceItem {

        @ApiModelProperty(value = "字段名，可含 $caption", required = true, example = "customerType$caption")
        private String field;

        @ApiModelProperty(value = "条件类型", required = true, example = "in")
        private String op;

        @ApiModelProperty(value = "条件值", required = true, example = "[\"企业\", \"个人\"]")
        private Object value;

        @ApiModelProperty(value = "OR 条件组：子条件用 OR 连接")
        @JsonProperty("$or")
        private List<SliceItem> or;

        @ApiModelProperty(value = "AND 条件组：子条件用 AND 连接")
        @JsonProperty("$and")
        private List<SliceItem> and;

        /**
         * 判断是否为 OR 逻辑组
         */
        public boolean _isOrGroup() {
            return or != null && !or.isEmpty();
        }

        /**
         * 判断是否为 AND 逻辑组
         */
        public boolean _isAndGroup() {
            return and != null && !and.isEmpty();
        }

        /**
         * 判断是否为逻辑组合条件（$or 或 $and）
         */
        public boolean _isLogicalGroup() {
            return _isOrGroup() || _isAndGroup();
        }

        /**
         * 获取逻辑组合的子条件
         */
        public List<SliceItem> _getGroupChildren() {
            if (_isOrGroup()) {
                return or;
            }
            if (_isAndGroup()) {
                return and;
            }
            return null;
        }

        /**
         * 获取逻辑组合的连接类型
         * @return "OR" 或 "AND"，如果不是逻辑组合则返回 null
         */
        public String _getGroupLink() {
            if (_isOrGroup()) {
                return "OR";
            }
            if (_isAndGroup()) {
                return "AND";
            }
            return null;
        }
    }
    /**
     * 分组
     */
    @Data
    @ApiModel("过滤条件项")
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupByItem {

        @ApiModelProperty(value = "字段名，可含 $caption", required = true, example = "customerType$caption")
        private String field;

        @ApiModelProperty(value = "聚合类型", required = true, example = "SUM、AVG")
        private String agg;

    }
    /**
     * 排序项
     */
    @Data
    @ApiModel("排序项")
    public static class OrderItem {

        @ApiModelProperty(value = "字段名，可含 $caption", required = true, example = "team$caption")
        private String field;

        @ApiModelProperty(value = "排序方向", required = true, example = "asc")
        private String dir;
    }
}
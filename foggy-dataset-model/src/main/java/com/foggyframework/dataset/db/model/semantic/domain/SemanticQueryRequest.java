package com.foggyframework.dataset.db.model.semantic.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.PostAggregateCalculationDef;
import com.foggyframework.dataset.db.model.semantic.domain.deserializer.OrderItemListDeserializer;
import com.foggyframework.dataset.db.model.semantic.enums.CaptionMatchMode;
import com.foggyframework.dataset.db.model.semantic.enums.MismatchHandleStrategy;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import com.foggyframework.dataset.db.model.semantic.memorygrid.MemoryGridInputBinding;
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

    @ApiModelProperty(value = "LLM 路由结果。CLARIFY/REJECT 会作为执行期终态处理，不进入查询编译")
    private String route;

    @ApiModelProperty(value = "执行计划状态。CLARIFY/REJECT 会作为执行期终态处理")
    private String status;

    @ApiModelProperty(value = "治理或语义风险标签")
    @JsonProperty("risk_flags")
    private List<String> riskFlags;

    @ApiModelProperty(value = "澄清问题。仅用于 CLARIFY 终态")
    @JsonProperty("clarifying_questions")
    private List<String> clarifyingQuestions;

    @ApiModelProperty(value = "拒绝或澄清原因")
    private List<String> why;

    @ApiModelProperty(value = "可执行计划。CLARIFY/REJECT 终态必须为空")
    @JsonProperty("executable_plan")
    private Object executablePlan;

    @ApiModelProperty(value = "虚拟语义 SQL。仅允许引用当前虚拟语义模型和 TM/QM 暴露字段")
    @JsonProperty("semantic_sql")
    private String semanticSql;

    @ApiModelProperty(value = "Memory Grid 二次分析计划。P0 仅做受治理、有界输入 guardrail")
    @JsonProperty("memory_grid_plan")
    private Map<String, Object> memoryGridPlan;

    @ApiModelProperty(value = "Memory Grid SQL。仅允许引用 bindings 中声明的 result handle alias")
    @JsonProperty("grid_sql")
    private String gridSql;

    @ApiModelProperty(value = "Memory Grid SQL 输入绑定。外部字段名为 bindings，内部限定为 memoryGridBindings")
    @JsonProperty("bindings")
    private List<MemoryGridInputBinding> memoryGridBindings;

    @ApiModelProperty(value = "计算字段定义列表，支持动态创建基于表达式的虚拟字段")
    private List<CalculatedFieldDef> calculatedFields;

    @ApiModelProperty(value = "过滤条件")
    private List<SliceItem> slice;

    @ApiModelProperty(value = "聚合后过滤条件。仅用于 groupBy/聚合查询后的 HAVING；普通明细字段过滤继续使用 slice")
    private List<SliceItem> having;

    @ApiModelProperty(value = "结果阶段过滤条件。仅用于窗口计算字段或 postAggregateCalculations 生成的外层结果集别名过滤；不会自动从 slice 下推")
    private List<SliceItem> postSlice;

    @ApiModelProperty(value = "分组字段，可含 $caption", example = "[\"team$caption\"]")
    private List<GroupByItem> groupBy;

    @ApiModelProperty(value = "排序字段")
    @JsonDeserialize(using = OrderItemListDeserializer.class)
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

    @ApiModelProperty(value = "是否去重。适用于'列出所有…'类查询，生成 SELECT DISTINCT。与聚合查询互斥，有 groupBy 时自动忽略", example = "true")
    private Boolean distinct = false;

    @ApiModelProperty(value = "是否追加小计/总计行。对 groupBy 聚合结果追加分组小计和总计行，通过 _rowType 字段标记行类型", example = "true")
    private Boolean withSubtotals = false;

    @ApiModelProperty(value = "时间窗口定义，用于声明式时间分析（同环比/累计/滚动）。nullable — 不指定时走常规查询", notes = "AI 通过 timeWindow DSL 表达 YoY/MoM/Rolling 等分析，引擎自动映射到 QueryPlan AST")
    private Map<String, Object> timeWindow;

    @ApiModelProperty(value = "聚合后计算字段", notes = "在 groupBy 聚合结果外层计算，首期支持 ratioToTotal")
    private List<PostAggregateCalculationDef> postAggregateCalculations;

    @ApiModelProperty(value = "输出显示格式元数据", notes = "仅用于前端/报表展示，不改变 SQL、过滤、排序、分桶、派生计算或 raw items 值")
    private List<OutputFormattingItem> outputFormatting;

    @ApiModelProperty(value = "多维透视请求（与 columns 互斥）",
            notes = "当 pivot 非 null 时，引擎自动切换到 Pivot Pipeline（四阶段内存加工）。" +
                    "pivot 与 columns/timeWindow 不能同时出现")
    private PivotRequest pivot;

    /**
     * 是否为 Pivot 透视模式
     */
    public boolean isPivotMode() {
        return pivot != null;
    }

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

    /**
     * 输出显示格式项。
     */
    @Data
    @ApiModel("输出显示格式项")
    public static class OutputFormattingItem {

        @ApiModelProperty(value = "最终输出字段名", required = true, example = "collectionRate")
        private String field;

        @ApiModelProperty(value = "显示格式类型，首期支持 decimal", required = true, example = "decimal")
        private String kind;

        @ApiModelProperty(value = "显示小数位数，首期允许 0-6", example = "2")
        private Integer scale;

        @ApiModelProperty(value = "显示层舍入模式，可选；不影响引擎计算", example = "HALF_UP")
        private String mode;

        @ApiModelProperty(value = "格式作用域，首期只允许 display_only", example = "display_only")
        private String scope = "display_only";
    }
}

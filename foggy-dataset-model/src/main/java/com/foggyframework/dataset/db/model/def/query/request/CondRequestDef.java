package com.foggyframework.dataset.db.model.def.query.request;



import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Data
public class CondRequestDef {
    @ApiModelProperty("列名称")
    String field;

    @ApiModelProperty(value = "查询类型", notes = "如=、like、in、[)等,CondType")
    String op;

    @ApiModelProperty("查询值")
    Object value;

    @ApiModelProperty(value = "最大层级深度", notes = "用于父子维度的 childrenOf/descendantsOf 操作，限制查询的层级深度")
    Integer maxDepth;

    @ApiModelProperty("OR 条件组：子条件用 OR 连接")
    @JsonProperty("$or")
    List<CondRequestDef> or;

    @ApiModelProperty("AND 条件组：子条件用 AND 连接")
    @JsonProperty("$and")
    List<CondRequestDef> and;

    /**
     * 表达式条件（$expr）
     * <p>
     * 当设置此字段时，忽略 field/op/value，直接将表达式解析为 SQL 条件。
     * 支持字段间比较，如 "fieldA > fieldB" 或 "fieldA > fieldB * 1.1"。
     * </p>
     * <p>示例：</p>
     * <pre>
     * { "$expr": "actualAmount > budgetAmount" }
     * { "$expr": "salesAmount > costAmount * 1.2" }
     * </pre>
     *
     * @since 8.3.0
     */
    @ApiModelProperty(value = "表达式条件", notes = "直接使用表达式作为条件，支持字段间比较，如 actualAmount > budgetAmount")
    @JsonProperty("$expr")
    String expr;

    /**
     * 判断是否为逻辑组合条件（$or 或 $and）
     */
    public boolean _isOrGroup() {
        return or != null && !or.isEmpty();
    }

    /**
     * 判断是否为逻辑组合条件（$or 或 $and）
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
    public List<CondRequestDef> _getGroupChildren() {
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

    public CondRequestDef(String field, String op, Object value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }

    /**
     * 创建 OR 条件组
     */
    public static CondRequestDef or(List<CondRequestDef> conditions) {
        CondRequestDef def = new CondRequestDef();
        def.or = conditions;
        return def;
    }

    /**
     * 创建 AND 条件组
     */
    public static CondRequestDef and(List<CondRequestDef> conditions) {
        CondRequestDef def = new CondRequestDef();
        def.and = conditions;
        return def;
    }

    /**
     * 创建表达式条件
     *
     * @param expression 表达式，如 "fieldA > fieldB"
     * @return 条件定义
     * @since 8.3.0
     */
    public static CondRequestDef expr(String expression) {
        CondRequestDef def = new CondRequestDef();
        def.expr = expression;
        return def;
    }

    // ==================== 字段间比较支持 ====================

    /**
     * 字段引用标记
     */
    public static final String FIELD_REFERENCE_KEY = "$field";

    /**
     * 判断是否为表达式条件（$expr）
     * <p>
     * 表达式条件直接使用表达式作为 SQL 条件，支持字段间比较。
     * </p>
     *
     * @return true 如果是表达式条件
     * @since 8.3.0
     */
    public boolean _isExpressionCondition() {
        return expr != null && !expr.isEmpty();
    }

    /**
     * 判断 value 是否为字段引用
     * <p>
     * 字段引用格式：{@code { "$field": "fieldName" }}
     * </p>
     * <p>示例：</p>
     * <pre>
     * { "field": "actualAmount", "op": ">", "value": { "$field": "budgetAmount" } }
     * </pre>
     *
     * @return true 如果 value 是字段引用
     * @since 8.3.0
     */
    @SuppressWarnings("rawtypes")
    public boolean _isFieldReference() {
        if (value instanceof Map) {
            Map map = (Map) value;
            return map.containsKey(FIELD_REFERENCE_KEY);
        }
        return false;
    }

    /**
     * 获取引用的字段名
     * <p>
     * 当 value 为字段引用时，返回被引用的字段名。
     * </p>
     *
     * @return 被引用的字段名，如果不是字段引用则返回 null
     * @since 8.3.0
     */
    @SuppressWarnings("rawtypes")
    public String _getReferencedField() {
        if (_isFieldReference()) {
            return (String) ((Map) value).get(FIELD_REFERENCE_KEY);
        }
        return null;
    }
}

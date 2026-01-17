package com.foggyframework.dataset.db.model.def.query.request;



import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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
}

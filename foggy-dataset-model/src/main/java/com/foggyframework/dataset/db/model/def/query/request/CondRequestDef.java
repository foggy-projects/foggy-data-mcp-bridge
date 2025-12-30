package com.foggyframework.dataset.db.model.def.query.request;



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

    @ApiModelProperty("默认或1为and,2为or")
    int link;

    List<CondRequestDef> children;

    public boolean _hasChildren() {
        return children != null && !children.isEmpty();
    }

    public CondRequestDef(String field, String op, Object value, int link, List<CondRequestDef> children) {
        this.field = field;
        this.op = op;
        this.value = value;
        this.link = link;
        this.children = children;
    }

    public CondRequestDef(String field, String op, Object value) {
        this.field = field;
        this.op = op;
        this.value = value;
    }
}

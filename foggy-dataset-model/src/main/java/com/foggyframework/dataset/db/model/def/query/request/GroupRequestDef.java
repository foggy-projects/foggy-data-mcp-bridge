package com.foggyframework.dataset.db.model.def.query.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class GroupRequestDef {
    @ApiModelProperty("列名称")
    String field;

    @ApiModelProperty(value = "", notes = "")
    String agg;

//    @ApiModelProperty(value = "如果为true，不会拼到group by后面")
//    boolean ignoreGroupBy;
}

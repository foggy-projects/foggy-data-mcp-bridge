package com.foggyframework.dataset.jdbc.model.def.query.request;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class OrderRequestDef  {
    @ApiModelProperty("列名称")
    String field;

    @ApiModelProperty(value = "排序类型", notes = "asc,desc")
    String order;

    boolean nullLast;

    boolean nullFirst;
}

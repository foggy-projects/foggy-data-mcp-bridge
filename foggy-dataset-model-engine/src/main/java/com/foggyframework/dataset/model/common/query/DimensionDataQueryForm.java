package com.foggyframework.dataset.model.common.query;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DimensionDataQueryForm {

    @ApiModelProperty("查询模型")
    String queryModel;

    @ApiModelProperty("查询的维度")
    String dimension;

    @ApiModelProperty(value = "层次", notes = "可能为空")
    String hierarchy;

    @ApiModelProperty("成员查询扩展数据，可用于 external patch 透传")
    Object extData;

    public DimensionDataQueryForm(String queryModel, String dimension) {
        this.queryModel = queryModel;
        this.dimension = dimension;
    }
}

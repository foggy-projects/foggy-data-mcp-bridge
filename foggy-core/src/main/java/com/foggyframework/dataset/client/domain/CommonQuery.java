package com.foggyframework.dataset.client.domain;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Api("通用查询表单")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommonQuery<T> {

    @ApiModelProperty("类名，必须全称，要求此类必须在common-default里出现，或者foggysource-service-query依赖了它，当它存在时，查询的结果集，会使用它进行转换")
    String clazz;

    @ApiModelProperty("数据集名称")
    String name;

    @ApiModelProperty("查询条件")
    T data;

    @ApiModelProperty("为1时，将会把字段名，如get_name转换为getName,注意，仅对sql的分页查询生效，mongo的查询无效,现在默认都是fix")
    Integer fix;
}

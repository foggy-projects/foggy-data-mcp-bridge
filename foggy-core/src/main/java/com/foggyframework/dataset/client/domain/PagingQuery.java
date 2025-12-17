package com.foggyframework.dataset.client.domain;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Api("分页查询表单")
@Data
@NoArgsConstructor
public class PagingQuery<T> {

    @ApiModelProperty("数据集名称")
    String name;

    @ApiModelProperty("类名，必须全称，要求此类必须在common-default里出现，或者foggysource-service-query依赖了它，当它存在时，查询的结果集，会使用它进行转换")
    String clazz;

    @ApiModelProperty("为1时，将会把字段名，如get_name转换为getName,注意，仅对sql的分页查询生效，mongo的查询无效,现在默认都是fix")
    Integer fix;

    @ApiModelProperty("查询的分页参数")
    PagingRequest<T> data;

    @ApiModelProperty("是否返回总条数，空或1时返回总条数，为0不返回")
    Integer returnTotal;

    /**
     * 子类里面需要自己写一个Builder的构造器，来重写父类参数
     **/
    @Builder()
    public PagingQuery(String name, String clazz, Integer fix, Integer page, Integer pageSize, Integer start, Integer limit, T param, Integer returnTotal) {
        this.name = name;
        this.clazz = clazz;
        this.fix = fix;
        this.returnTotal = returnTotal;
        data = (PagingRequest<T>) PagingRequest.builder().page(page).pageSize(pageSize).start(start).limit(limit).param(param).build();
    }

    public boolean _isReturnTotal() {
        return (returnTotal == null || returnTotal == 1) ? true : false;
    }
}

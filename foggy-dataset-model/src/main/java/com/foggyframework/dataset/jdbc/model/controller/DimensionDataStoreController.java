package com.foggyframework.dataset.jdbc.model.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.jdbc.model.common.result.DbDataItem;
import com.foggyframework.dataset.jdbc.model.service.JdbcService;
import com.foggyframework.dataset.model.PagingResultImpl;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 为前端提供维度的数据源
 */
@RestController
@RequestMapping("jdbc-model/dimension")
public class DimensionDataStoreController {

    @Resource
    JdbcService jdbcService;

    @PostMapping("queryDimensionData")
    @ApiModelProperty("建议使用queryDimensionDataV2替代,URL上带上模型名称,更有利于缓存,第一层权限过滤等")
    @Deprecated
    public RX<PagingResultImpl<DbDataItem>> queryDimensionData(@RequestBody PagingRequest<DimensionDataQueryForm> form) {
        PagingResultImpl<DbDataItem> v = jdbcService.queryDimensionData(form);

        return RX.success(v);
    }

    @PostMapping("v2/{model}/{dimension}")
    public RX<PagingResultImpl<DbDataItem>> queryDimensionDataV2(
//            @RequestBody PagingRequest<DimensionDataQueryForm> form,
            @ApiParam(value = "模型", required = true) @PathVariable String model,
            @ApiParam(value = "维度", required = true) @PathVariable String dimension) {

        PagingRequest<DimensionDataQueryForm> form = PagingRequest.buildPagingRequest(new DimensionDataQueryForm(model, dimension));
        PagingResultImpl<DbDataItem> v = jdbcService.queryDimensionData(form);

        return RX.success(v);
    }

}

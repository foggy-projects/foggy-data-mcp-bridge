package com.foggyframework.dataset.jdbc.model.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.common.result.KpiResultImpl;
import com.foggyframework.dataset.jdbc.model.def.query.request.JdbcQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.DataSetResultFilterManager;
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
@RequestMapping("jdbc-model/query-model")
public class JdbcQueryModelDataStoreController {

    @Resource
    JdbcService jdbcService;

    @Resource
    DataSetResultFilterManager dataSetResultFilterManager;

    @PostMapping("queryModelData")
    @ApiModelProperty("建议使用queryModelDataV2替代,URL上带上模型名称,更有利于缓存,第一层权限过滤等")
    public RX<PagingResultImpl> queryModelData(@RequestBody PagingRequest<JdbcQueryRequestDef> form) {
        form =  dataSetResultFilterManager.beforeQuery(form);
        PagingResultImpl v = jdbcService.queryModelData(form);
        v = dataSetResultFilterManager.process(form,v);
        return RX.success(v);
    }

    @PostMapping("v2/{model}")
    public RX<PagingResultImpl> queryModelDataV2(
            @ApiParam(value = "模型", required = true) @PathVariable String model,
            @RequestBody PagingRequest<JdbcQueryRequestDef> form) {

        form.getParam().setQueryModel(model);

        form =  dataSetResultFilterManager.beforeQuery(form);
        PagingResultImpl v = jdbcService.queryModelData(form);
        v = dataSetResultFilterManager.process(form,v);
        return RX.success(v);
    }
}

package com.foggyframework.dataset.jdbc.model.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.jdbc.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.jdbc.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 查询模型数据接口
 * <p>
 * 为前端提供数据查询服务，使用 QueryFacade 统一处理查询生命周期。
 * </p>
 */
@RestController
@RequestMapping("jdbc-model/query-model")
public class JdbcQueryModelDataStoreController {

    @Resource
    QueryFacade queryFacade;

    @PostMapping("queryModelData")
    @ApiModelProperty("建议使用queryModelDataV2替代,URL上带上模型名称,更有利于缓存,第一层权限过滤等")
    public RX<PagingResultImpl> queryModelData(@RequestBody PagingRequest<DbQueryRequestDef> form) {
        PagingResultImpl v = queryFacade.queryModelData(form);
        return RX.success(v);
    }

    @PostMapping("v2/{model}")
    public RX<PagingResultImpl> queryModelDataV2(
            @ApiParam(value = "模型", required = true) @PathVariable String model,
            @RequestBody PagingRequest<DbQueryRequestDef> form) {

        form.getParam().setQueryModel(model);
        PagingResultImpl v = queryFacade.queryModelData(form);
        return RX.success(v);
    }
}

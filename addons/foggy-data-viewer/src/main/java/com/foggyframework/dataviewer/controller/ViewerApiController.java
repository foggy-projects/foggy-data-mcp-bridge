package com.foggyframework.dataviewer.controller;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.ViewerDataResponse;
import com.foggyframework.dataviewer.domain.ViewerQueryRequest;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 数据浏览器API控制器
 * <p>
 * 集成 QueryFacade 执行真实查询，使用类型安全的请求类
 */
@Slf4j
@RestController
@RequestMapping("/data-viewer/api")
@RequiredArgsConstructor
public class ViewerApiController {

    private final QueryCacheService cacheService;
    private final QueryFacade queryFacade;

    /**
     * 获取查询元数据（用于初始页面加载）
     */
    @GetMapping("/query/{queryId}/meta")
    public ResponseEntity<QueryMetaResponse> getQueryMeta(@PathVariable String queryId) {
        return cacheService.getQuery(queryId)
                .map(ctx -> ResponseEntity.ok(new QueryMetaResponse(
                        ctx.getTitle(),
                        ctx.getSchema(),
                        ctx.getEstimatedRowCount(),
                        ctx.getExpiresAt().toString(),
                        ctx.getModel(),
                        ctx.getColumns()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 执行查询并返回数据
     */
    @PostMapping("/query/{queryId}/data")
    public ResponseEntity<ViewerDataResponse> queryData(
            @PathVariable String queryId,
            @RequestBody ViewerQueryRequest request) {

        Optional<CachedQueryContext> ctxOpt = cacheService.getQuery(queryId);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.status(410).body(
                    ViewerDataResponse.expired("Query link has expired")
            );
        }

        CachedQueryContext ctx = ctxOpt.get();

        try {
            // 构建查询请求，合并缓存参数与用户覆盖
            DbQueryRequestDef queryDef = buildQueryDef(ctx, request);

            // 构建分页请求
            PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
            pagingRequest.setParam(queryDef);
            pagingRequest.setStart(request.getStart());
            pagingRequest.setLimit(request.getLimit());

            // 使用 QueryFacade 执行查询
            PagingResultImpl result = queryFacade.queryModelData(pagingRequest);

            return ResponseEntity.ok(ViewerDataResponse.success(
                    result.getItems(),
                    result.getTotal(),
                    request.getStart(),
                    request.getLimit()
            ));
        } catch (Exception e) {
            log.error("Error executing query for queryId: {}", queryId, e);
            return ResponseEntity.ok(ViewerDataResponse.error(e.getMessage()));
        }
    }

    /**
     * 构建查询请求，合并缓存参数与用户覆盖
     */
    private DbQueryRequestDef buildQueryDef(CachedQueryContext ctx, ViewerQueryRequest request) {
        DbQueryRequestDef def = ctx.toDbQueryRequestDef();

        // 合并额外的过滤条件
        if (request.getAdditionalFilters() != null && !request.getAdditionalFilters().isEmpty()) {
            List<SliceRequestDef> mergedSlice = new ArrayList<>(ctx.getSlice());
            mergedSlice.addAll(request.getAdditionalFilters());
            def.setSlice(mergedSlice);
        }

        // 覆盖排序条件（如果用户指定）
        if (request.getOrderBy() != null && !request.getOrderBy().isEmpty()) {
            def.setOrderBy(request.getOrderBy());
        }

        // 覆盖分组条件（如果用户指定，用于聚合模式）
        if (request.getGroupBy() != null && !request.getGroupBy().isEmpty()) {
            def.setGroupBy(request.getGroupBy());
        }

        def.setReturnTotal(true);
        return def;
    }

    /**
     * 查询元数据响应
     */
    public record QueryMetaResponse(
            String title,
            List<CachedQueryContext.ColumnSchema> schema,
            Long estimatedRowCount,
            String expiresAt,
            String model,
            List<String> columns
    ) {}
}

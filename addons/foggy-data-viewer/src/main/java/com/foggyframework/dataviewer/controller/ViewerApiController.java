package com.foggyframework.dataviewer.controller;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.ViewerDataResponse;
import com.foggyframework.dataviewer.domain.ViewerQueryRequest;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.db.model.common.result.DbDataItem;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.service.JdbcService;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final JdbcService jdbcService;

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
                        ctx.getColumns(),
                        ctx.getSlice()  // 返回初始过滤条件
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
     * 从前端直接创建查询（用于 DSL 输入）
     */
    @PostMapping("/query/create")
    public ResponseEntity<CreateQueryResponse> createQuery(
            @RequestBody QueryCacheService.OpenInViewerRequest request) {
        try {
            // 验证必要参数
            if (request.getModel() == null || request.getModel().isBlank()) {
                return ResponseEntity.badRequest().body(
                        new CreateQueryResponse(false, null, null, "model 不能为空"));
            }
            if (request.getColumns() == null || request.getColumns().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new CreateQueryResponse(false, null, null, "columns 不能为空"));
            }
            if (request.getSlice() == null || request.getSlice().isEmpty()) {
                return ResponseEntity.badRequest().body(
                        new CreateQueryResponse(false, null, null, "slice 不能为空，请提供至少一个过滤条件"));
            }

            // 缓存查询
            CachedQueryContext ctx = cacheService.cacheQuery(request, null);

            return ResponseEntity.ok(new CreateQueryResponse(
                    true,
                    ctx.getQueryId(),
                    "/data-viewer/view/" + ctx.getQueryId(),
                    null
            ));
        } catch (Exception e) {
            log.error("Error creating query", e);
            return ResponseEntity.ok(new CreateQueryResponse(
                    false, null, null, e.getMessage()));
        }
    }

    /**
     * 创建查询响应
     */
    public record CreateQueryResponse(
            boolean success,
            String queryId,
            String viewerUrl,
            String error
    ) {}

    /**
     * 获取维度成员列表（用于过滤器下拉）
     */
    @GetMapping("/query/{queryId}/filter-options/{columnName}")
    public ResponseEntity<FilterOptionsResponse> getFilterOptions(
            @PathVariable String queryId,
            @PathVariable String columnName) {

        Optional<CachedQueryContext> ctxOpt = cacheService.getQuery(queryId);
        if (ctxOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        CachedQueryContext ctx = ctxOpt.get();

        // 查找列的 schema
        CachedQueryContext.ColumnSchema columnSchema = null;
        if (ctx.getSchema() != null) {
            columnSchema = ctx.getSchema().stream()
                    .filter(s -> s.getName().equals(columnName))
                    .findFirst()
                    .orElse(null);
        }

        if (columnSchema == null) {
            return ResponseEntity.badRequest().body(
                    new FilterOptionsResponse(List.of(), 0, "Column not found: " + columnName));
        }

        try {
            List<FilterOption> options = new ArrayList<>();

            if ("dimension".equals(columnSchema.getFilterType()) && columnSchema.getDimensionRef() != null) {
                // 加载维度成员
                options = loadDimensionMembers(ctx.getModel(), columnSchema.getDimensionRef());
            } else if ("dict".equals(columnSchema.getFilterType()) && columnSchema.getDictItems() != null) {
                // 从 schema 获取字典项
                options = columnSchema.getDictItems().stream()
                        .map(item -> new FilterOption(item.getValue(), item.getLabel()))
                        .toList();
            }

            return ResponseEntity.ok(new FilterOptionsResponse(options, options.size(), null));
        } catch (Exception e) {
            log.error("Error loading filter options for column: {}", columnName, e);
            return ResponseEntity.ok(new FilterOptionsResponse(List.of(), 0, e.getMessage()));
        }
    }

    /**
     * 加载维度成员
     */
    private List<FilterOption> loadDimensionMembers(String modelName, String dimensionRef) {
        try {
            DimensionDataQueryForm form = new DimensionDataQueryForm(modelName, dimensionRef);
            PagingRequest<DimensionDataQueryForm> request = PagingRequest.buildPagingRequest(form);
            request.setLimit(50000);  // 最多加载5万条

            PagingResultImpl<DbDataItem> result = jdbcService.queryDimensionData(request);

            if (result.getItems() == null) {
                return List.of();
            }

            return result.getItems().stream()
                    .map(item -> new FilterOption(item.getId(), item.getCaption()))
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load dimension members for {}.{}: {}", modelName, dimensionRef, e.getMessage());
            return List.of();
        }
    }

    /**
     * 过滤选项响应
     */
    public record FilterOptionsResponse(
            List<FilterOption> options,
            long total,
            String error
    ) {}

    /**
     * 过滤选项
     */
    public record FilterOption(
            Object value,
            String label
    ) {}

    /**
     * 构建查询请求，合并缓存参数与用户覆盖
     */
    private DbQueryRequestDef buildQueryDef(CachedQueryContext ctx, ViewerQueryRequest request) {
        DbQueryRequestDef def = ctx.toDbQueryRequestDef();

        // 合并缓存的 slice 与用户的 slice（前端直接传递 DSL 格式）
        List<SliceRequestDef> mergedSlice = new ArrayList<>(ctx.getSlice() != null ? ctx.getSlice() : List.of());
        if (request.getSlice() != null && !request.getSlice().isEmpty()) {
            mergedSlice.addAll(request.getSlice());
        }
        def.setSlice(mergedSlice);

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
            List<String> columns,
            List<SliceRequestDef> initialSlice
    ) {}
}

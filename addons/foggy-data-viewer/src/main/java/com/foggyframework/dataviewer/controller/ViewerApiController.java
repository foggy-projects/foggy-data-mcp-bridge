package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.ViewerDataResponse;
import com.foggyframework.dataviewer.domain.ViewerQueryRequest;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Autowired(required = false)
    private SemanticServiceV3 semanticService;

    /**
     * 获取查询元数据（用于初始页面加载）
     */
    @GetMapping("/query/{queryId}/meta")
    public RX getQueryMeta(@PathVariable String queryId) {
        return cacheService.getQuery(queryId)
                .map(ctx -> RX.ok(new QueryMetaResponse(
                        ctx.getTitle(),
                        ctx.getTableConfig(),
                        ctx.getEstimatedRowCount(),
                        ctx.getExpiresAt().toString(),
                        ctx.getSlice()  // 返回初始过滤条件
                )))
                .orElse(RX.notFound().build());
    }

    /**
     * 下载 QM Schema（供前端离线开发使用）
     * <p>
     * 返回完整的 QM 模型字段元数据，可保存为 JSON 文件
     */
    @GetMapping("/schema/download/{qmModel}")
    public ResponseEntity<String> downloadQmSchema(@PathVariable String qmModel) {
        if (semanticService == null) {
            return ResponseEntity.status(503)
                    .body("{\"error\": \"SemanticService not available\"}");
        }

        try {
            // 构建请求，获取完整的字段元数据
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(Arrays.asList(qmModel));
            request.setLevels(Arrays.asList(1, 2, 3)); // 获取全量字段
            request.setIncludeExamples(true); // 包含示例数据

            // 获取 JSON 格式的元数据
            SemanticMetadataResponse response = semanticService.getMetadata(request, "json");

            if (response == null || response.getContent() == null) {
                return ResponseEntity.notFound().build();
            }

            // 设置响应头，提示下载文件
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", qmModel + "-schema.json");
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(response.getContent());

        } catch (Exception e) {
            log.error("Error downloading QM schema for model: {}", qmModel, e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 获取 QM Schema（供前端运行时使用）
     * <p>
     * 返回 QM 模型的字段元数据，用于前端构建列配置
     */
    @GetMapping("/schema/{qmModel}")
    public RX<SemanticMetadataResponse> getQmSchema(@PathVariable String qmModel) {
        if (semanticService == null) {
            return RX.status(HttpStatusCode.valueOf(503))
                    .msg(" SemanticService not available").build();
        }

        try {
            // 构建请求，获取字段元数据
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(Arrays.asList(qmModel));
            request.setLevels(Arrays.asList(1, 2, 3));
            request.setIncludeExamples(false); // 不需要示例数据

            // 获取 JSON 格式的元数据
            SemanticMetadataResponse response = semanticService.getMetadata(request, "json");

            if (response == null || response.getData() == null) {
                return RX.notFound().build();
            }

            return RX.ok(response.getData());

        } catch (Exception e) {
            log.error("Error fetching QM schema for model: {}", qmModel, e);
            return RX.error("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * 执行查询并返回数据
     */
    @PostMapping("/query/{queryId}/data")
    public RX<ViewerDataResponse> queryData(
            @PathVariable String queryId,
            @RequestBody ViewerQueryRequest request) {

        Optional<CachedQueryContext> ctxOpt = cacheService.getQuery(queryId);
        if (ctxOpt.isEmpty()) {
            return new RX<>(410, null, "Query link has expired",
                    ViewerDataResponse.expired("Query link has expired"));
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

            return RX.ok(ViewerDataResponse.success(
                    result.getItems(),
                    result.getTotal(),
                    result.getTotalData(),
                    request.getStart(),
                    request.getLimit()
            ));
        } catch (Exception e) {
            log.error("Error executing query for queryId: {}", queryId, e);
            return RX.failB(e.getMessage(), ViewerDataResponse.error(e.getMessage()));
        }
    }

    /**
     * 从前端直接创建查询（用于 DSL 输入）
     * <p>
     * 接收 payload 结构（与 dataset.query_model 格式一致）
     */
    @PostMapping("/query/create")
    public RX<CreateQueryResponse> createQuery(
            @RequestBody CreateQueryFromFrontendRequest frontendRequest) {
        try {
            // 验证必要参数
            if (frontendRequest.getModel() == null || frontendRequest.getModel().isBlank()) {
                return RX.failB("model 不能为空",
                        new CreateQueryResponse(false, null, null, "model 不能为空"));
            }
            if (frontendRequest.getPayload() == null) {
                return RX.failB("payload 不能为空",
                        new CreateQueryResponse(false, null, null, "payload 不能为空"));
            }

            CreateQueryPayload payload = frontendRequest.getPayload();
            if (payload.getColumns() == null || payload.getColumns().isEmpty()) {
                return RX.failB("payload.columns 不能为空",
                        new CreateQueryResponse(false, null, null, "payload.columns 不能为空"));
            }
//            if (payload.getSlice() == null || payload.getSlice().isEmpty()) {
//                return RX.failB("payload.slice 不能为空，请提供至少一个过滤条件",
//                        new CreateQueryResponse(false, null, null, "payload.slice 不能为空，请提供至少一个过滤条件"));
//            }

            // 转换为内部请求格式
            QueryCacheService.OpenInViewerRequest request = new QueryCacheService.OpenInViewerRequest();
            request.setModel(frontendRequest.getModel());
            request.setTitle(frontendRequest.getTitle());
            request.setColumns(payload.getColumns());
            request.setSlice(payload.getSlice());
            request.setGroupBy(payload.getGroupBy());
            request.setOrderBy(payload.getOrderBy());
            request.setCalculatedFields(payload.getCalculatedFields());

            // 缓存查询
            CachedQueryContext ctx = cacheService.cacheQuery(request, null);

            return RX.ok(new CreateQueryResponse(
                    true,
                    ctx.getQueryId(),
                    "/data-viewer/view/" + ctx.getQueryId(),
                    null
            ));
        } catch (Exception e) {
            log.error("Error creating query", e);
            return RX.failB(e.getMessage(),
                    new CreateQueryResponse(false, null, null, e.getMessage()));
        }
    }

    /**
     * 前端创建查询请求（payload 结构）
     */
    @lombok.Data
    public static class CreateQueryFromFrontendRequest {
        private String model;
        private CreateQueryPayload payload;
        private String title;
    }

    /**
     * 查询 payload（与 dataset.query_model 格式一致）
     */
    @lombok.Data
    public static class CreateQueryPayload {
        private List<String> columns;
        private List<SliceRequestDef> slice;
        private List<GroupRequestDef> groupBy;
        private List<OrderRequestDef> orderBy;
        private List<CalculatedFieldDef> calculatedFields;
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
            CachedQueryContext.TableConfig tableConfig,
            Long estimatedRowCount,
            String expiresAt,
            List<SliceRequestDef> initialSlice
    ) {}
}

package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.core.ex.RX;
import com.foggyframework.core.utils.JsonUtils;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.FrontendMeta;
import com.foggyframework.dataviewer.domain.MemberQueryRequest;
import com.foggyframework.dataviewer.domain.MemberQueryResponse;
import com.foggyframework.dataviewer.domain.ViewerDataResponse;
import com.foggyframework.dataviewer.domain.ViewerQueryRequest;
import com.foggyframework.dataviewer.service.FrontendMetaConverter;
import com.foggyframework.dataviewer.service.MemberQueryService;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.QueryFacadeResult;
import com.foggyframework.dataviewer.service.StableQueryFacadeRequestMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
@ConditionalOnProperty(prefix = "foggy.data-viewer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ViewerApiController {

    private final QueryCacheService cacheService;
    private final QueryFacade queryFacade;
    private final DatasetProperties datasetProperties;

    @Autowired(required = false)
    private SemanticServiceV3 semanticService;

    private final FrontendMetaConverter frontendMetaConverter = new FrontendMetaConverter();
    private final MemberQueryService memberQueryService;

    /**
     * 获取查询元数据（用于初始页面加载）
     */
    @GetMapping("/query/{model}/{queryId}/meta")
    public RX getQueryMeta(@PathVariable String model, @PathVariable String queryId) {
        return cacheService.getQuery(queryId)
                .map(ctx -> {
                    if (!model.equals(ctx.getModel())) {
                        return RX.failB("URL中的model与查询不匹配", null);
                    }
                    return RX.ok(new QueryMetaResponse(
                            ctx.getTitle(),
                            ctx.getTableConfig(),
                            ctx.getEstimatedRowCount(),
                            ctx.getExpiresAt().toString(),
                            ctx.getSlice()  // 返回初始过滤条件
                    ));
                })
                .orElse(RX.notFound().build());
    }

    /**
     * 下载 QM Schema（供前端离线开发使用）
     * <p>
     * 返回完整的 QM 模型字段元数据，可保存为 JSON 文件
     */
    @GetMapping("/schema/download/{qmModel}")
    public ResponseEntity<String> downloadQmSchema(
            @PathVariable String qmModel,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
            @RequestParam(value = "namespace", required = false) String queryNamespace) {
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
            String namespace = resolveNamespace(headerNamespace, queryNamespace);
            SemanticMetadataResponse response = semanticService.getMetadata(
                    request, "json", SemanticRequestContext.of(namespace, authorization));

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
    public RX<SemanticMetadataResponse> getQmSchema(
            @PathVariable String qmModel,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
            @RequestParam(value = "namespace", required = false) String queryNamespace) {
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
            String namespace = resolveNamespace(headerNamespace, queryNamespace);
            SemanticMetadataResponse response = semanticService.getMetadata(
                    request, "json", SemanticRequestContext.of(namespace, authorization));

            if (response == null || response.getData() == null) {
                return RX.notFound().build();
            }

            return RX.ok(response.getData());

        } catch (Exception e) {
            log.error("Error fetching QM schema for model: {}", qmModel, e);
            return RX.error("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    // ── frontend-meta v1 ──

    /**
     * 获取前端元数据契约 (frontend-meta v1)
     * <p>
     * 将 V3 语义元数据转换为面向前端渲染的标准结构：
     * fields 为有序数组、自动推导 memberLookup、新增 category/sortable/uiHints。
     */
    @GetMapping("/frontend-meta/{qmModel}")
    public RX<FrontendMeta> getFrontendMeta(
            @PathVariable String qmModel,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
            @RequestParam(value = "namespace", required = false) String queryNamespace) {
        if (semanticService == null) {
            return RX.status(HttpStatusCode.valueOf(503))
                    .msg("SemanticService not available").build();
        }

        try {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(Arrays.asList(qmModel));
            request.setLevels(Arrays.asList(1, 2, 3));
            request.setIncludeExamples(false);

            String namespace = resolveNamespace(headerNamespace, queryNamespace);
            SemanticMetadataResponse response = semanticService.getMetadata(
                    request, "json", SemanticRequestContext.of(namespace, authorization));

            if (response == null || response.getData() == null) {
                return RX.notFound().build();
            }

            FrontendMeta meta = frontendMetaConverter.convert(response.getData());
            if (meta == null) {
                return RX.notFound().build();
            }

            return RX.ok(meta);
        } catch (Exception e) {
            log.error("Error building frontend-meta for model: {}", qmModel, e);
            return RX.error(e.getMessage());
        }
    }

    /**
     * 下载前端元数据契约 (frontend-meta v1) 为 JSON 文件
     * <p>
     * 供代码生成器离线使用或 CI 快照
     */
    @GetMapping("/frontend-meta/download/{qmModel}")
    public ResponseEntity<String> downloadFrontendMeta(
            @PathVariable String qmModel,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
            @RequestParam(value = "namespace", required = false) String queryNamespace) {
        if (semanticService == null) {
            return ResponseEntity.status(503)
                    .body("{\"error\": \"SemanticService not available\"}");
        }

        try {
            SemanticMetadataRequest request = new SemanticMetadataRequest();
            request.setQmModels(Arrays.asList(qmModel));
            request.setLevels(Arrays.asList(1, 2, 3));
            request.setIncludeExamples(false);

            String namespace = resolveNamespace(headerNamespace, queryNamespace);
            SemanticMetadataResponse response = semanticService.getMetadata(
                    request, "json", SemanticRequestContext.of(namespace, authorization));

            if (response == null || response.getData() == null) {
                return ResponseEntity.notFound().build();
            }

            FrontendMeta meta = frontendMetaConverter.convert(response.getData());
            if (meta == null) {
                return ResponseEntity.notFound().build();
            }

            String json = JsonUtils.toJson(meta);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment",
                    qmModel + ".frontend-meta.json");
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json; charset=UTF-8");

            return ResponseEntity.ok().headers(headers).body(json);
        } catch (Exception e) {
            log.error("Error downloading frontend-meta for model: {}", qmModel, e);
            return ResponseEntity.internalServerError()
                    .body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    // ── 维度成员查询 ──

    /**
     * 查询维度成员（远程搜索、分页、回填）
     * <p>
     * 前端只需传 qmModel + fieldName，内部自动映射到 synthetic member-QM。
     * 返回的 selectionFieldName 是前端生成 DSL slice 时必须使用的字段。
     */
    @PostMapping("/members/query")
    public RX<MemberQueryResponse> queryMembers(
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
            @RequestBody MemberQueryRequest request) {
        if (request.getQmModel() == null || request.getQmModel().isBlank()) {
            return RX.failB("qmModel 不能为空", null);
        }
        if (request.getFieldName() == null || request.getFieldName().isBlank()) {
            return RX.failB("fieldName 不能为空", null);
        }

        try {
            String namespace = resolveNamespace(headerNamespace, request.getNamespace());
            request.setNamespace(namespace);
            MemberQueryResponse response = memberQueryService.query(request, namespace);
            return RX.ok(response);
        } catch (Exception e) {
            log.error("Error querying members for {}.{}: {}",
                    request.getQmModel(), request.getFieldName(), e.getMessage(), e);
            return RX.failB("维度成员查询失败: " + e.getMessage(), null);
        }
    }

    // ── 过滤选项（兼容 DataViewer queryId 模式） ──

    /**
     * 获取过滤选项（维度成员或字典项）
     * <p>
     * DataViewer 页面通过此接口加载下拉选项。
     * 对 dimension 类型的列，委托给 MemberQueryService 查询维度成员。
     */
    @GetMapping("/query/{model}/{queryId}/filter-options/{columnName}")
    public RX getFilterOptions(
            @PathVariable String model,
            @PathVariable String queryId,
            @PathVariable String columnName,
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
            @RequestParam(value = "namespace", required = false) String queryNamespace) {

        // 验证 queryId 有效（复用缓存上下文获取 qmModel）
        Optional<CachedQueryContext> ctxOpt = cacheService.getQuery(queryId);
        if (ctxOpt.isEmpty()) {
            return RX.notFound().build();
        }

        CachedQueryContext ctx = ctxOpt.get();
        String qmModel = ctx.getTableConfig() != null ? ctx.getTableConfig().getQmModel() : null;
        if (qmModel == null) {
            qmModel = model;
        }

        try {
            // 委托给 MemberQueryService 查询维度成员
            MemberQueryRequest memberReq = new MemberQueryRequest();
            memberReq.setQmModel(qmModel);
            memberReq.setFieldName(columnName);
            memberReq.setStart(0);
            memberReq.setLimit(100);
            String namespace = resolveNamespace(headerNamespace, firstNonBlank(queryNamespace, ctx.getNamespace()));
            memberReq.setNamespace(namespace);

            MemberQueryResponse memberResp = memberQueryService.query(memberReq, namespace);

            // 转换为旧的 FilterOption 格式 { options: [{value, label}], total }
            List<java.util.Map<String, Object>> options = new ArrayList<>();
            if (memberResp.getItems() != null) {
                for (MemberQueryResponse.MemberOption item : memberResp.getItems()) {
                    java.util.Map<String, Object> opt = new java.util.LinkedHashMap<>();
                    opt.put("value", item.getValue());
                    opt.put("label", item.getLabel());
                    options.add(opt);
                }
            }

            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("options", options);
            result.put("total", memberResp.getTotal());
            return RX.ok(result);
        } catch (Exception e) {
            log.warn("Failed to load filter options for {}.{}: {}", qmModel, columnName, e.getMessage());
            // 返回空选项而非错误，避免前端组件报错
            java.util.Map<String, Object> emptyResult = new java.util.LinkedHashMap<>();
            emptyResult.put("options", List.of());
            emptyResult.put("total", 0);
            return RX.ok(emptyResult);
        }
    }

    // ── 直连查询（无需 queryId） ──

    /**
     * 直连查询数据（无需提前创建 queryId）
     * <p>
     * 适用于生成组件的标准用法：前端只需 qmModel + 分页/筛选/排序参数即可查询。
     * 现有 queryId 模式保留给 DataViewer / SavedQuery 等需要缓存上下文的场景。
     */
    @PostMapping("/query/direct/{qmModel}")
    public RX<ViewerDataResponse> queryDirect(
            @PathVariable String qmModel,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
            @RequestBody ViewerQueryRequest request) {
        try {
            if (request == null) {
                request = new ViewerQueryRequest();
            }

            List<String> columns = normalizeExplicitColumns(request.getColumns());
            if (columns.isEmpty()) {
                String message = "columns 不能为空，直连查询必须显式指定输出列";
                return RX.failB(message, ViewerDataResponse.error(message));
            }

            String namespace = resolveNamespace(headerNamespace, request.getNamespace());
            DbQueryRequestDef queryDef = new DbQueryRequestDef();
            queryDef.setQueryModel(qmModel);
            queryDef.setReturnTotal(true);
            queryDef.setExtData(request.getExtData());
            queryDef.setColumns(columns);

            // 直接使用前端传入的 slice / orderBy / groupBy
            if (request.getSlice() != null) {
                queryDef.setSlice(request.getSlice());
            }
            if (request.getOrderBy() != null) {
                queryDef.setOrderBy(request.getOrderBy());
            }
            if (request.getGroupBy() != null) {
                queryDef.setGroupBy(request.getGroupBy());
            }

            PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
            pagingRequest.setParam(queryDef);
            pagingRequest.setStart(request.getStart() != null ? request.getStart() : 0);
            pagingRequest.setLimit(request.getLimit() != null ? request.getLimit() : 50);

            QueryFacadeResult result = queryFacade.query(
                    StableQueryFacadeRequestMapper.from(pagingRequest, authorization, namespace));

            return RX.ok(ViewerDataResponse.success(
                    result.getItems(),
                    result.getTotal(),
                    result.getTotalData(),
                    pagingRequest.getStart(),
                    pagingRequest.getLimit()
            ));
        } catch (ExRuntimeExceptionImpl e) {
            String message = safeErrorMessage(e);
            log.warn("Direct query business error for model {}: {}", qmModel, message);
            return viewerDataBusinessError(e);
        } catch (Exception e) {
            String message = safeErrorMessage(e);
            log.warn("Direct query failed for model {}: {}: {}", qmModel, e.getClass().getName(), message);
            return RX.failB(message, ViewerDataResponse.error(message));
        }
    }

    /**
     * 执行查询并返回数据
     */
    @PostMapping("/query/{model}/{queryId}/data")
    public RX<ViewerDataResponse> queryData(
            @PathVariable String model,
            @PathVariable String queryId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
            @RequestBody ViewerQueryRequest request) {

        Optional<CachedQueryContext> ctxOpt = cacheService.getQuery(queryId);
        if (ctxOpt.isEmpty()) {
            return new RX<>(410, null, "Query link has expired",
                    ViewerDataResponse.expired("Query link has expired"));
        }

        CachedQueryContext ctx = ctxOpt.get();

        if (!model.equals(ctx.getModel())) {
            return RX.failB("URL中的model与查询不匹配", null);
        }

        try {
            String namespace = resolveNamespace(headerNamespace, firstNonBlank(request.getNamespace(), ctx.getNamespace()));
            String effectiveAuthorization = firstNonBlank(authorization, ctx.getAuthorization());

            // 构建查询请求，合并缓存参数与用户覆盖
            DbQueryRequestDef queryDef = buildQueryDef(ctx, request);

            // 构建分页请求
            PagingRequest<DbQueryRequestDef> pagingRequest = new PagingRequest<>();
            pagingRequest.setParam(queryDef);
            pagingRequest.setStart(request.getStart());
            pagingRequest.setLimit(request.getLimit());

            // 使用 QueryFacade 执行查询
            QueryFacadeResult result = queryFacade.query(
                    StableQueryFacadeRequestMapper.from(pagingRequest, effectiveAuthorization, namespace));

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
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-NS", required = false) String headerNamespace,
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
            request.setExtData(payload.getExtData());
            request.setNamespace(resolveNamespace(headerNamespace, frontendRequest.getNamespace()));

            // 缓存查询
            CachedQueryContext ctx = cacheService.cacheQuery(request, authorization);

            return RX.ok(new CreateQueryResponse(
                    true,
                    ctx.getQueryId(),
                    "/data-viewer/view/" + request.getModel() + "/" + ctx.getQueryId(),
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
        private String namespace;
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
        private Object extData;
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

        def.setExtData(mergeExtData(def.getExtData(), request.getExtData()));
        def.setReturnTotal(true);
        return def;
    }

    private Object mergeExtData(Object cachedExtData, Object requestExtData) {
        if (requestExtData == null) {
            return cachedExtData;
        }
        if (cachedExtData instanceof Map<?, ?> cachedMap && requestExtData instanceof Map<?, ?> requestMap) {
            Map<String, Object> merged = new LinkedHashMap<>();
            cachedMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            requestMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            return merged;
        }
        return requestExtData;
    }

    private String resolveNamespace(String headerNamespace, String bodyNamespace) {
        return DatasetRequestNamespaceResolver.resolve(datasetProperties, headerNamespace, bodyNamespace);
    }

    private List<String> normalizeExplicitColumns(List<String> columns) {
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String column : columns) {
            if (column == null) {
                continue;
            }
            String trimmed = column.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private RX<ViewerDataResponse> viewerDataBusinessError(ExRuntimeExceptionImpl e) {
        String message = safeErrorMessage(e);
        return new RX<>(e.getCode(), e.getExCode(), message, ViewerDataResponse.error(message));
    }

    private String safeErrorMessage(Throwable e) {
        return firstNonBlank(e instanceof ExRuntimeExceptionImpl ex ? ex.getUserTip() : null,
                e.getMessage(), e.getClass().getSimpleName());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
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

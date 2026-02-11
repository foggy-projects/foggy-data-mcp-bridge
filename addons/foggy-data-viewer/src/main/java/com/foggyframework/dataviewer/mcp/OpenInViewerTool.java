package com.foggyframework.dataviewer.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryCacheService.OpenInViewerRequest;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.dataset.db.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.db.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 在浏览器中打开数据 - MCP工具
 * <p>
 * 将查询参数转换为可分享的浏览器链接，
 * 用于处理大数据集的交互式浏览
 * <p>
 * 注意：此工具通过 {@link com.foggyframework.dataviewer.config.DataViewerAutoConfiguration}
 * 自动配置创建，不使用 @Component 注解，以确保只有在 MongoDB 可用时才加载。
 */
@Slf4j
public class OpenInViewerTool implements McpTool {

    private final QueryCacheService cacheService;
    private final QueryScopeConstraintService constraintService;
    private final DataViewerProperties properties;
    private final ObjectMapper objectMapper;
    private final int serverPort;

    public OpenInViewerTool(QueryCacheService cacheService,
                            QueryScopeConstraintService constraintService,
                            DataViewerProperties properties,
                            ObjectMapper objectMapper,
                            int serverPort) {
        this.cacheService = cacheService;
        this.constraintService = constraintService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.serverPort = serverPort;
    }

    @Override
    public String getName() {
        return "dataset.open_in_viewer";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return Set.of(ToolCategory.QUERY, ToolCategory.EXPORT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        log.info("Executing open_in_viewer tool with traceId: {}", context.getTraceId());

        // 解析请求参数
        OpenInViewerRequest request = parseRequest(arguments);

        // 验证并强制执行范围约束
        List<SliceRequestDef> constrainedSlice = constraintService.enforceConstraints(
                request.getModel(),
                request.getSlice()
        );
        request.setSlice(constrainedSlice);

        // 缓存查询
        CachedQueryContext ctx = cacheService.cacheQuery(request, context.getAuthorization());

        // 构建响应
        String viewerUrl = getBaseUrl() + "/view/" + request.getModel() + "/" + ctx.getQueryId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewerUrl", viewerUrl);
        result.put("queryId", ctx.getQueryId());
        result.put("expiresAt", ctx.getExpiresAt().toString());

        if (ctx.getEstimatedRowCount() != null) {
            result.put("estimatedRowCount", ctx.getEstimatedRowCount());
        }

        result.put("message", String.format(
                "Data viewer link created. The link expires at %s. " +
                        "Users can browse, filter, sort, and export the data interactively.",
                ctx.getExpiresAt()
        ));

        log.info("Created viewer link: {} for queryId: {}", viewerUrl, ctx.getQueryId());
        return result;
    }

    // 注意：getDescription() 和 getInputSchema() 从配置文件加载，不再硬编码
    // 描述文件: classpath:/schemas/descriptions/open_in_viewer.md
    // Schema文件: classpath:/schemas/open_in_viewer_schema.json

    @SuppressWarnings("unchecked")
    private OpenInViewerRequest parseRequest(Map<String, Object> arguments) {
        OpenInViewerRequest request = new OpenInViewerRequest();

        // 顶层参数
        request.setModel((String) arguments.get("model"));
        request.setTitle((String) arguments.get("title"));

        // 从 payload 中提取查询参数（与 query_model 格式一致）
        Map<String, Object> payload = (Map<String, Object>) arguments.get("payload");
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }

        request.setColumns((List<String>) payload.get("columns"));

        // 使用 ObjectMapper 转换类型安全的请求对象
        Object sliceArg = payload.get("slice");
        if (sliceArg != null) {
            request.setSlice(objectMapper.convertValue(sliceArg,
                    new TypeReference<List<SliceRequestDef>>() {}));
        }

        Object groupByArg = payload.get("groupBy");
        if (groupByArg != null) {
            request.setGroupBy(objectMapper.convertValue(groupByArg,
                    new TypeReference<List<GroupRequestDef>>() {}));
        }

        Object orderByArg = payload.get("orderBy");
        if (orderByArg != null) {
            request.setOrderBy(objectMapper.convertValue(orderByArg,
                    new TypeReference<List<OrderRequestDef>>() {}));
        }

        Object calculatedFieldsArg = payload.get("calculatedFields");
        if (calculatedFieldsArg != null) {
            request.setCalculatedFields(objectMapper.convertValue(calculatedFieldsArg,
                    new TypeReference<List<CalculatedFieldDef>>() {}));
        }

        // 验证必需参数
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw new IllegalArgumentException("payload.columns is required");
        }
        if (request.getSlice() == null || request.getSlice().isEmpty()) {
            throw new IllegalArgumentException("payload.slice is required - at least one filter condition must be provided");
        }

        return request;
    }

    /**
     * 获取基础URL，如果未配置则使用默认值
     */
    private String getBaseUrl() {
        String baseUrl = properties.getBaseUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
            return baseUrl;
        }
        return String.format("http://localhost:%d/data-viewer", serverPort);
    }
}

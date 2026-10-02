package com.foggyframework.dataviewer.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryCacheService.OpenInViewerRequest;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.dataviewer.service.ViewerLaunchLinkProvider;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.def.query.request.GroupRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
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
 * 自动配置创建，不使用 @Component 注解；可选用 MongoDB 或 SQLite 查询上下文存储。
 */
@Slf4j
public class OpenInViewerTool implements McpTool {

    private final QueryCacheService cacheService;
    private final QueryScopeConstraintService constraintService;
    private final DataViewerProperties properties;
    private final ObjectMapper objectMapper;
    private final int serverPort;
    private final ViewerLaunchLinkProvider launchLinkProvider;

    public OpenInViewerTool(QueryCacheService cacheService,
                            QueryScopeConstraintService constraintService,
                            DataViewerProperties properties,
                            ObjectMapper objectMapper,
                            int serverPort,
                            ViewerLaunchLinkProvider launchLinkProvider) {
        this.cacheService = cacheService;
        this.constraintService = constraintService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.serverPort = serverPort;
        this.launchLinkProvider = launchLinkProvider;
    }

    public OpenInViewerTool(QueryCacheService cacheService,
                            QueryScopeConstraintService constraintService,
                            DataViewerProperties properties,
                            ObjectMapper objectMapper,
                            int serverPort) {
        this(cacheService, constraintService, properties, objectMapper, serverPort, null);
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
        request.setNamespace(resolveNamespace(context, arguments));

        // 验证并强制执行范围约束
        List<SliceRequestDef> constrainedSlice = constraintService.enforceConstraints(
                request.getModel(),
                request.getSlice()
        );
        request.setSlice(constrainedSlice);

        // 缓存查询
        CachedQueryContext ctx = cacheService.cacheQuery(request, null);

        // 构建响应
        String defaultViewerUrl = getBaseUrl() + "/view/" + request.getModel() + "/" + ctx.getQueryId();
        ViewerLaunchLinkProvider.ViewerLaunchLink link = launchLinkProvider == null
                ? new ViewerLaunchLinkProvider.ViewerLaunchLink(defaultViewerUrl, ctx.getExpiresAt())
                : launchLinkProvider.createViewerLink(ctx, context.getAuthorization(), defaultViewerUrl);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("viewerUrl", link.url());
        result.put("queryId", ctx.getQueryId());
        result.put("expiresAt", link.expiresAt() == null ? null : link.expiresAt().toString());
        result.put("queryExpiresAt", ctx.getExpiresAt() == null ? null : ctx.getExpiresAt().toString());
        result.put("viewerLinkExpiresAt", link.expiresAt() == null ? null : link.expiresAt().toString());

        if (ctx.getEstimatedRowCount() != null) {
            result.put("estimatedRowCount", ctx.getEstimatedRowCount());
        }

        result.put("message", link.expiresAt() == null
                ? "Reusable data viewer link created with no expiry."
                : "Reusable data viewer link created. Expires at " + link.expiresAt() + ".");

        // Launch URLs carry an opaque reusable link secret in their fragment. Never log the URL/secret.
        log.info("Created viewer link.");
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

        Object havingArg = payload.get("having");
        if (havingArg != null) {
            request.setHaving(objectMapper.convertValue(havingArg,
                    new TypeReference<List<SliceRequestDef>>() {}));
        }

        Object groupByArg = payload.get("groupBy");
        if (groupByArg != null) {
            request.setGroupBy(parseGroupBy(groupByArg));
        }

        Object orderByArg = payload.get("orderBy");
        if (orderByArg != null) {
            request.setOrderBy(parseOrderBy(orderByArg));
        }

        Object calculatedFieldsArg = payload.get("calculatedFields");
        if (calculatedFieldsArg != null) {
            request.setCalculatedFields(objectMapper.convertValue(calculatedFieldsArg,
                    new TypeReference<List<CalculatedFieldDef>>() {}));
        }
        request.setExtData(payload.get("extData"));

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

    private List<GroupRequestDef> parseGroupBy(Object value) {
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException("payload.groupBy must be an array");
        }
        List<GroupRequestDef> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            GroupRequestDef item;
            if (entry instanceof String field) {
                item = new GroupRequestDef();
                item.setField(field.trim());
            } else if (entry instanceof Map<?, ?>) {
                item = objectMapper.convertValue(entry, GroupRequestDef.class);
            } else {
                throw new IllegalArgumentException("payload.groupBy entries must be field strings or objects");
            }
            if (item.getField() == null || item.getField().isBlank()) {
                throw new IllegalArgumentException("payload.groupBy field is required");
            }
            result.add(item);
        }
        return result;
    }

    private List<OrderRequestDef> parseOrderBy(Object value) {
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException("payload.orderBy must be an array");
        }
        List<OrderRequestDef> result = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            OrderRequestDef item;
            if (entry instanceof String shorthand) {
                item = parseOrderByShorthand(shorthand);
            } else if (entry instanceof Map<?, ?>) {
                item = objectMapper.convertValue(entry, OrderRequestDef.class);
            } else {
                throw new IllegalArgumentException("payload.orderBy entries must be field strings or objects");
            }
            if (item.getField() == null || item.getField().isBlank()) {
                throw new IllegalArgumentException("payload.orderBy field is required");
            }
            result.add(item);
        }
        return result;
    }

    private OrderRequestDef parseOrderByShorthand(String text) {
        String shorthand = text.trim();
        String field = shorthand;
        String direction = "asc";
        if (shorthand.startsWith("-")) {
            field = shorthand.substring(1).trim();
            direction = "desc";
        } else {
            int space = shorthand.lastIndexOf(' ');
            if (space > 0) {
                String suffix = shorthand.substring(space + 1).trim().toLowerCase(Locale.ROOT);
                if ("asc".equals(suffix) || "desc".equals(suffix)) {
                    field = shorthand.substring(0, space).trim();
                    direction = suffix;
                }
            }
        }
        OrderRequestDef item = new OrderRequestDef();
        item.setField(field);
        item.setDir(direction);
        return item;
    }

    private String resolveNamespace(ToolExecutionContext context, Map<String, Object> arguments) {
        Object explicit = arguments.get("namespace");
        if (explicit instanceof String ns && !ns.isBlank()) {
            return ns.trim();
        }
        if (context == null) {
            return null;
        }
        String headerNamespace = context.getHeader("X-NS");
        if (headerNamespace != null && !headerNamespace.isBlank()) {
            return headerNamespace.trim();
        }
        String contextNamespace = context.getNamespace();
        return contextNamespace != null && !contextNamespace.isBlank()
                ? contextNamespace.trim()
                : contextNamespace;
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

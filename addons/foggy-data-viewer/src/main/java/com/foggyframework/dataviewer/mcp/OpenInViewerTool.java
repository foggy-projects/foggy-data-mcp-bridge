package com.foggyframework.dataviewer.mcp;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataviewer.service.QueryCacheService.OpenInViewerRequest;
import com.foggyframework.dataviewer.service.QueryScopeConstraintService;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 在浏览器中打开数据 - MCP工具
 * <p>
 * 将查询参数转换为可分享的浏览器链接，
 * 用于处理大数据集的交互式浏览
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenInViewerTool implements McpTool {

    private final QueryCacheService cacheService;
    private final QueryScopeConstraintService constraintService;
    private final DataViewerProperties properties;

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
        List<Map<String, Object>> constrainedSlice = constraintService.enforceConstraints(
                request.getModel(),
                request.getSlice()
        );
        request.setSlice(constrainedSlice);

        // 缓存查询
        CachedQueryContext ctx = cacheService.cacheQuery(request, context.getAuthorization());

        // 构建响应
        String viewerUrl = properties.getBaseUrl() + "/view/" + ctx.getQueryId();

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

    @Override
    public String getDescription() {
        return """
                Generate a shareable link to view large datasets in an interactive browser.

                **When to use:**
                - Detailed data queries expecting many rows (500+)
                - When user asks for "all", "list", "export" type queries
                - When data exploration (filtering, sorting, pagination) would be valuable

                **When NOT to use (use dataset.query_model_v2 instead):**
                - Aggregated queries with groupBy (returns summary, small result set)
                - Queries with explicit small limit (≤100 rows)
                - When AI needs to analyze/interpret the data directly

                **IMPORTANT - Filter Requirement:**
                You MUST provide at least one filter condition in the `slice` parameter to limit the
                query scope. This is mandatory to prevent unbounded queries on large tables.

                **Tip:** Use dataset.query_model_v2 with returnTotal=true and limit=1 to estimate
                row count before deciding which tool to use.
                """;
    }

    @Override
    public Map<String, Object> getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("model", "columns", "slice"));

        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("model", Map.of(
                "type", "string",
                "description", "The query model name (e.g., FactOrderQueryModel)"
        ));

        properties.put("columns", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Columns to display"
        ));

        properties.put("slice", Map.of(
                "type", "array",
                "minItems", 1,
                "description", "Filter conditions (REQUIRED). At least one filter must be provided to limit query scope."
        ));

        properties.put("groupBy", Map.of(
                "type", "array",
                "description", "Grouping/aggregation fields"
        ));

        properties.put("orderBy", Map.of(
                "type", "array",
                "description", "Sort order"
        ));

        properties.put("title", Map.of(
                "type", "string",
                "description", "Optional title for the data view"
        ));

        schema.put("properties", properties);
        return schema;
    }

    @SuppressWarnings("unchecked")
    private OpenInViewerRequest parseRequest(Map<String, Object> arguments) {
        OpenInViewerRequest request = new OpenInViewerRequest();
        request.setModel((String) arguments.get("model"));
        request.setColumns((List<String>) arguments.get("columns"));
        request.setSlice((List<Map<String, Object>>) arguments.get("slice"));
        request.setGroupBy((List<Map<String, Object>>) arguments.get("groupBy"));
        request.setOrderBy((List<Map<String, Object>>) arguments.get("orderBy"));
        request.setTitle((String) arguments.get("title"));

        // 验证必需参数
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new IllegalArgumentException("model is required");
        }
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw new IllegalArgumentException("columns is required");
        }
        if (request.getSlice() == null || request.getSlice().isEmpty()) {
            throw new IllegalArgumentException("slice is required - at least one filter condition must be provided");
        }

        return request;
    }
}

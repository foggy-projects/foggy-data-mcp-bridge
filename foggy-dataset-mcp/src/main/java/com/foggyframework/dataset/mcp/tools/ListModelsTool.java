package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.service.ModelCatalogService;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 发现所有可用模型的轻量级路由工具。
 *
 * Public MCP list_models intentionally has no caller-controlled parameters.
 * Hosts that need format, model filtering, or permission inputs should use
 * POST /semantic/v3/list-models.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListModelsTool implements McpTool {

    private final ModelCatalogService modelCatalogService;

    @Override
    public String getName() {
        return "dataset.list_models";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.METADATA);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        log.info("Listing all models for AI routing, namespace={}", context.getNamespace());

        Map<String, Object> dataMap = modelCatalogService.buildCatalogResponse(
                Map.of("format", "markdown"),
                context.getNamespace(),
                context.getAuthorization()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("data", dataMap);
        return result;
    }
}

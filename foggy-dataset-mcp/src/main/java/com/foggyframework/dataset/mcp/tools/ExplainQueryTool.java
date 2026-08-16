package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainRequest;
import com.foggyframework.dataset.model.semantic.explain.SemanticExplainService;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** MCP adapter for the Java semantic explain service. */
@Component
@RequiredArgsConstructor
public class ExplainQueryTool implements McpTool {

    private final SemanticExplainService semanticExplainService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "dataset.explain_query";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        // The namespace policy is the intended exposure boundary. These two
        // categories keep the tool open on all three existing MCP role lanes.
        return EnumSet.of(ToolCategory.NATURAL_LANGUAGE, ToolCategory.QUERY);
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String model = arguments == null ? null : asString(arguments.get("model"));
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Missing required parameter: model");
        }

        Map<String, Object> requestArguments = new LinkedHashMap<>(arguments);
        requestArguments.remove("model");
        SemanticExplainRequest request = objectMapper.convertValue(requestArguments, SemanticExplainRequest.class);
        PermissionAction action = request.getPayload() == null ? PermissionAction.DESCRIBE : PermissionAction.EXECUTE;
        SemanticRequestContext semanticContext = SemanticRequestContext
                .of(context.getNamespace(), context.getAuthorization())
                .withPermissionAction(action);
        // Return a JSON tree instead of the response record itself. The legacy
        // JSON-RPC adapter serializes arbitrary objects with a minimal mapper;
        // records containing trace timestamps can otherwise fall back to their
        // Java toString() representation instead of the public JSON contract.
        return objectMapper.valueToTree(semanticExplainService.explain(model, request, semanticContext));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

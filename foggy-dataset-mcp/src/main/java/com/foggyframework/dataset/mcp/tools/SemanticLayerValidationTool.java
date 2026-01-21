package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.mcp.validation.SemanticLayerValidationService;
import com.foggyframework.dataset.mcp.validation.ValidationRequest;
import com.foggyframework.dataset.mcp.validation.ValidationResult;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 语义层验证MCP工具
 *
 * <p>通过MCP协议验证外部语义层文件（TM/QM）
 * <p>供OpenHands等AI工具调用
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Slf4j
@Component
public class SemanticLayerValidationTool implements McpTool {

    @Resource
    private SemanticLayerValidationService validationService;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "semantic_layer.validate";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return Set.of(ToolCategory.ADMIN, ToolCategory.SYSTEM);
    }

    @Override
    public String getDescription() {
        return "验证语义层模型文件（TM/QM）的正确性。" +
                "支持动态加载外部目录中的TM和QM文件，检查语法错误、字段引用、表结构等，并返回详细的验证结果。" +
                "适用于OpenHands等AI工具编写完语义层文件后进行验证。";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "语义层文件夹路径（必填）。目录下应包含model/和query/子目录，分别存放TM和QM文件。"
                        ),
                        "namespace", Map.of(
                                "type", "string",
                                "description", "命名空间（可选，默认为openhands）。用于隔离不同环境的模型，避免命名冲突。",
                                "default", "openhands"
                        ),
                        "watch", Map.of(
                                "type", "boolean",
                                "description", "是否监听文件变化（可选，默认为false）。启用后文件修改时会自动重新加载。",
                                "default", false
                        ),
                        "clearExisting", Map.of(
                                "type", "boolean",
                                "description", "是否清除已注册的同名Bundle（可选，默认为true）。避免重复注册导致的冲突。",
                                "default", true
                        ),
                        "includeStackTrace", Map.of(
                                "type", "boolean",
                                "description", "是否返回详细的堆栈跟踪（可选，默认为false）。开发调试时建议开启。",
                                "default", false
                        )
                ),
                "required", List.of("path")
        );
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        log.info("执行语义层验证工具: arguments={}, traceId={}", arguments, context.getTraceId());

        try {
            // 1. 构建验证请求
            ValidationRequest request = buildRequest(arguments);

            log.info("验证请求参数: path={}, namespace={}, watch={}",
                    request.getPath(), request.getNamespace(), request.isWatch());

            // 2. 执行验证
            ValidationResult result = validationService.validate(request);

            // 3. 转换为Map返回（MCP要求返回Map或JSON兼容对象）
            return convertToMap(result);

        } catch (Exception e) {
            log.error("语义层验证工具执行失败: traceId={}, error={}",
                    context.getTraceId(), e.getMessage(), e);

            // 返回错误结果
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "errorType", e.getClass().getSimpleName()
            );
        }
    }

    /**
     * 构建验证请求
     */
    private ValidationRequest buildRequest(Map<String, Object> arguments) {
        String path = (String) arguments.get("path");
        String namespace = (String) arguments.getOrDefault("namespace", "openhands");
        Boolean watch = (Boolean) arguments.getOrDefault("watch", false);
        Boolean clearExisting = (Boolean) arguments.getOrDefault("clearExisting", true);
        Boolean includeStackTrace = (Boolean) arguments.getOrDefault("includeStackTrace", false);

        return ValidationRequest.builder()
                .path(path)
                .namespace(namespace)
                .watch(watch)
                .clearExisting(clearExisting)
                .includeStackTrace(includeStackTrace)
                .build();
    }

    /**
     * 转换ValidationResult为Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(ValidationResult result) {
        try {
            // 使用ObjectMapper转换为Map
            String json = objectMapper.writeValueAsString(result);
            return objectMapper.readValue(json, Map.class);

        } catch (Exception e) {
            log.error("转换ValidationResult失败: {}", e.getMessage());

            // 降级：手动构建Map
            Map<String, Object> map = new HashMap<>();
            map.put("success", result.isSuccess());
            map.put("namespace", result.getNamespace());
            map.put("totalFiles", result.getTotalFiles());
            map.put("validFiles", result.getValidFiles());
            map.put("invalidFiles", result.getInvalidFiles());
            map.put("errorCount", result.getErrors().size());
            map.put("warningCount", result.getWarnings().size());

            // 简化错误信息
            if (!result.getErrors().isEmpty()) {
                map.put("errors", result.getErrors().stream()
                        .map(error -> Map.of(
                                "file", error.getFile(),
                                "type", error.getType(),
                                "message", error.getMessage()
                        ))
                        .toList());
            }

            return map;
        }
    }
}

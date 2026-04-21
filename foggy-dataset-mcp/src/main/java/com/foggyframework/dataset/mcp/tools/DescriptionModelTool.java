package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 模型描述工具 - 获取模型详细元数据
 *
 * 对应 Python 版的 dataset.describe_model_internal
 *
 * <p>AI Chat 契约治理（v1.3）：</p>
 * <ul>
 *   <li><b>LLM 可见 schema</b>（{@code describe_model_internal_schema.json}）不暴露
 *       {@code format} 参数——这是"LLM 不能选择格式"的单一治理点。LLM 的 tool call 里
 *       不会出现 {@code format}，默认走 markdown 分支，保证 AI Chat 路径 deterministic。</li>
 *   <li><b>内部程序化消费</b>（权限治理 / 字段映射 / 管理端 Model Overview / Mapping Preview
 *       等 Odoo Pro 服务）通过 {@code GatewayBackend} 复用同一 MCP 入口，会<b>显式</b>
 *       传入 {@code format="json"} 请求结构化元数据。此时 tool 必须继续按原分支返回 JSON，
 *       不得为了"统一"而破坏这些链路（参见总控治理原则）。</li>
 *   <li>结论：LLM 不知道 {@code format} 存在 → 默认 markdown；内部调用方显式传 format
 *       → 按其选择输出。无需在 tool 层强行忽略 format。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DescriptionModelTool implements McpTool {

    /** AI Chat 默认格式。LLM 不会传 format（schema 不暴露），因此命中该默认即为 AI Chat 路径。 */
    static final String AI_CHAT_DEFAULT_FORMAT = "markdown";

    private final DatasetAccessor datasetAccessor;

    @Override
    public String getName() {
        return "dataset.describe_model_internal";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        // 元数据查询工具，适合数据分析师
        return EnumSet.of(ToolCategory.METADATA);
    }

    // 注意：getDescription() 和 getInputSchema() 从配置文件加载，不再硬编码

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String traceId = context.getTraceId();
        String authorization = context.getAuthorization();
        String namespace = context.getNamespace();

        String model = (String) arguments.get("model");

        if (model == null || model.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", "缺少必要参数: model");
            return error;
        }

        // format 默认为 markdown（schema 不暴露该参数，LLM 不会传入）；
        // 内部程序化消费方（如 Odoo Pro 的列权限 / 字段映射服务）显式传 format="json"
        // 时按其请求返回结构化 JSON，避免破坏管理端结构化解析链路。
        String format = (String) arguments.getOrDefault("format", AI_CHAT_DEFAULT_FORMAT);

        log.info("Describing model: {}, traceId={}, namespace={}, accessMode={}, format={}",
                model, traceId, namespace, datasetAccessor.getAccessMode(), format);

        return datasetAccessor.describeModel(model, format, traceId, authorization, namespace, arguments);
    }
}

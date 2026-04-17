package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 模型查询工具 - 执行数据查询
 *
 * 对应 Python 版的 dataset.query_model
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryModelTool implements McpTool {

    private final DatasetAccessor datasetAccessor;

    @Override
    public String getName() {
        return "dataset.query_model";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        // 数据查询工具，适合数据分析师
        return EnumSet.of(ToolCategory.QUERY);
    }

    // 注意：getDescription() 和 getInputSchema() 从配置文件加载，不再硬编码

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String traceId = context.getTraceId();
        String authorization = context.getAuthorization();
        String namespace = context.getNamespace();

        String model = (String) arguments.get("model");
        Map<String, Object> payload = (Map<String, Object>) arguments.get("payload");
        String mode = (String) arguments.getOrDefault("mode", "execute");

        if (model == null || model.isBlank()) {
            return RX.failB("缺少必要参数: model");
        }
        if (payload == null) {
            return RX.failB("缺少必要参数: payload");
        }

        log.info("Querying model: {}, mode={}, traceId={}, namespace={}, accessMode={}",
                model, mode, traceId, namespace, datasetAccessor.getAccessMode());

        return datasetAccessor.queryModel(model, payload, mode, traceId, authorization, namespace, arguments);
    }

    /**
     * 执行查询并返回强类型结果
     *
     * @param model         模型名称
     * @param payload       查询参数
     * @param mode          执行模式
     * @param traceId       追踪ID
     * @param authorization 授权信息
     * @return 查询响应
     */
    @SuppressWarnings("unchecked")
    public RX<SemanticQueryResponse> executeQuery(String model, Map<String, Object> payload, String mode,
                                                   String traceId, String authorization) {
        if (model == null || model.isBlank()) {
            return RX.failB("缺少必要参数: model");
        }
        if (payload == null) {
            return RX.failB("缺少必要参数: payload");
        }

        log.info("Querying model: {}, mode={}, traceId={}, accessMode={}",
                model, mode, traceId, datasetAccessor.getAccessMode());

        return datasetAccessor.queryModel(model, payload, mode, traceId, authorization, null);
    }
}

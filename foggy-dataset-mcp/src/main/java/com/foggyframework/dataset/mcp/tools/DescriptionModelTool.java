package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 模型描述工具 - 获取模型详细元数据
 *
 * 对应 Python 版的 dataset.description_model_internal
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DescriptionModelTool implements McpTool {

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
    public Object execute(Map<String, Object> arguments, String traceId, String authorization) {
        String model = (String) arguments.get("model");
        String format = (String) arguments.getOrDefault("format", "markdown");

        if (model == null || model.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", "缺少必要参数: model");
            return error;
        }

        log.info("Describing model: {}, traceId={}, accessMode={}",
                model, traceId, datasetAccessor.getAccessMode());

        return datasetAccessor.describeModel(model, format, traceId, authorization);
    }
}

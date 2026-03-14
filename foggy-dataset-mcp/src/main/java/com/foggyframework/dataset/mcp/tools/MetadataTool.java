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
 * 元数据工具 - 获取用户级元数据包
 *
 * 对应 Python 版的 dataset.get_metadata
 *
 * 工具描述和参数配置从 config/tools/tool-configs.yml 和 descriptions/get_metadata.md 加载
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataTool implements McpTool {

    private final DatasetAccessor datasetAccessor;

    @Override
    public String getName() {
        return "dataset.get_metadata";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        // 元数据查询工具，适合数据分析师
        return EnumSet.of(ToolCategory.METADATA);
    }

    // 注意：getDescription() 和 getInputSchema() 方法已从配置文件加载
    // 不需要在这里硬编码，使用接口的默认实现即可

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        String traceId = context.getTraceId();
        String authorization = context.getAuthorization();
        String namespace = context.getNamespace();
        log.info("Fetching metadata, traceId={}, namespace={}, accessMode={}", traceId, namespace, datasetAccessor.getAccessMode());
        return datasetAccessor.getMetadata(traceId, authorization, namespace);
    }
}

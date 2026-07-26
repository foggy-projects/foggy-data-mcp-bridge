package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ProgressEvent;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ExportWithXChartTool implements McpTool {

    private final ExportWithChartExecutor executor;

    @Override
    public String getName() {
        return "dataset.export_with_xchart";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        return EnumSet.of(
                ToolCategory.QUERY,
                ToolCategory.VISUALIZATION,
                ToolCategory.EXPORT
        );
    }

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        return executor.execute(arguments, context, "xchart");
    }

    @Override
    public Flux<ProgressEvent> executeWithProgress(
            Map<String, Object> arguments,
            ToolExecutionContext context
    ) {
        return executor.executeWithProgress(arguments, context, "xchart");
    }
}

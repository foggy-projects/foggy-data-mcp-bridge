package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.service.QueryImageExportService;
import com.foggyframework.mcp.spi.McpTool;
import com.foggyframework.mcp.spi.ProgressEvent;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class QueryImageExportTool implements McpTool {
    private final QueryImageExportService service;
    public QueryImageExportTool(QueryImageExportService service) { this.service = service; }
    @Override public String getName() { return "dataset.export_image"; }
    @Override public Set<ToolCategory> getCategories() {
        return EnumSet.of(ToolCategory.QUERY, ToolCategory.VISUALIZATION, ToolCategory.EXPORT);
    }
    @Override public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
        return service.export(arguments, context);
    }
    @Override public boolean supportsStreaming() { return true; }
    @Override public Flux<ProgressEvent> executeWithProgress(Map<String, Object> arguments, ToolExecutionContext context) {
        return Flux.concat(Flux.just(ProgressEvent.progress("querying", 20)),
                Flux.defer(() -> Flux.just(ProgressEvent.complete(service.export(arguments, context)))));
    }
}

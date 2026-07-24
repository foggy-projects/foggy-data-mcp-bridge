package com.foggyframework.mcp.spi;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class McpSpiContractTest {

    @Test
    void executionContextShouldResolveHeadersCaseInsensitively() {
        ToolExecutionContext context = ToolExecutionContext.builder()
                .authorization("Bearer fallback")
                .headers(Map.of(
                        "X-Tenant-Id", "tenant-1",
                        "authorization", "Bearer header"))
                .build();

        assertThat(context.getHeader("x-tenant-id")).isEqualTo("tenant-1");
        assertThat(context.getHeader("Authorization")).isEqualTo("Bearer header");
        assertThat(context.getHeader("missing")).isNull();
        assertThat(context.getHeader(" ")).isNull();
    }

    @Test
    void executionContextShouldFallBackToAuthorizationField() {
        ToolExecutionContext context = ToolExecutionContext.of("trace-1", "Bearer fallback");

        assertThat(context.getHeader("authorization")).isEqualTo("Bearer fallback");
        assertThat(context.getHeaders()).isEmpty();

        context.setHeaders(null);
        assertThat(context.getHeaders()).isEmpty();
    }

    @Test
    void progressEventFactoriesShouldExposeStableEventTypesAndPayloads() {
        ProgressEvent progress = ProgressEvent.progress("query", 40);
        ProgressEvent partial = ProgressEvent.partialResult(Map.of("rows", 2));
        ProgressEvent complete = ProgressEvent.complete("done");
        ProgressEvent error = ProgressEvent.error("EXECUTION_ERROR", "failed");

        assertThat(progress.getId()).isNotBlank();
        assertThat(progress.getEventType()).isEqualTo("progress");
        assertThat(progress.getData()).isEqualTo(Map.of("phase", "query", "percent", 40));
        assertThat(partial.getEventType()).isEqualTo("partial_result");
        assertThat(partial.getData()).isEqualTo(Map.of("rows", 2));
        assertThat(complete.getEventType()).isEqualTo("complete");
        assertThat(complete.getData()).isEqualTo("done");
        assertThat(error.getEventType()).isEqualTo("error");
        assertThat(error.getData()).isEqualTo(Map.of("code", "EXECUTION_ERROR", "message", "failed"));
    }

    @Test
    void defaultProgressExecutionShouldPublishCompletion() {
        McpTool tool = toolReturning("result");

        List<ProgressEvent> events = tool.executeWithProgress(Map.of(), new ToolExecutionContext())
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("complete");
        assertThat(events.get(0).getData()).isEqualTo("result");
    }

    @Test
    void defaultProgressExecutionShouldConvertExceptionToErrorEvent() {
        McpTool tool = new McpTool() {
            @Override
            public String getName() {
                return "failing-tool";
            }

            @Override
            public Set<ToolCategory> getCategories() {
                return Set.of(ToolCategory.SYSTEM);
            }

            @Override
            public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
                throw new IllegalStateException("boom");
            }
        };

        List<ProgressEvent> events = tool.executeWithProgress(Map.of(), new ToolExecutionContext())
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("error");
        assertThat(events.get(0).getData())
                .isEqualTo(Map.of("code", "EXECUTION_ERROR", "message", "boom"));
    }

    @Test
    void toolDefaultsShouldRemainNonStreamingAndUnspecified() {
        McpTool tool = toolReturning(null);

        assertThat(tool.getDescription()).isNull();
        assertThat(tool.getInputSchema()).isNull();
        assertThat(tool.supportsStreaming()).isFalse();
        assertThat(tool.getCategories()).containsExactly(ToolCategory.QUERY);
    }

    private static McpTool toolReturning(Object result) {
        return new McpTool() {
            @Override
            public String getName() {
                return "test-tool";
            }

            @Override
            public Set<ToolCategory> getCategories() {
                return Set.of(ToolCategory.QUERY);
            }

            @Override
            public Object execute(Map<String, Object> arguments, ToolExecutionContext context) {
                return result;
            }
        };
    }
}

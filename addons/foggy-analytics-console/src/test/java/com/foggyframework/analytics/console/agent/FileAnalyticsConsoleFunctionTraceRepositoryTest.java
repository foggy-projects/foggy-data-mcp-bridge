package com.foggyframework.analytics.console.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.analytics.console.agent.AnalyticsConsoleFunctionTraceRepository.FunctionTrace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileAnalyticsConsoleFunctionTraceRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsRawPayloadsByTurnAndRejectsInvocationMutation() {
        ObjectMapper json = new ObjectMapper();
        Path root = tempDir.resolve("function-traces");
        var repository = new FileAnalyticsConsoleFunctionTraceRepository(root, json);
        FunctionTrace trace = new FunctionTrace(
                "conversation-1",
                "ask-request-1",
                "ask-invocation-1",
                "function-invocation-1",
                "foggy.analytics.semantic-queries.execute@v1",
                json.createObjectNode().put("modelName", "FactOrderQueryModel"),
                json.createObjectNode().put("total", 6),
                200);

        repository.save(trace);
        repository.save(trace);

        var reloaded = new FileAnalyticsConsoleFunctionTraceRepository(root, json);
        assertThat(reloaded.findByTurn("conversation-1", "ask-invocation-1"))
                .containsExactly(trace);
        assertThat(reloaded.findByTurn("conversation-1", "ask-invocation-2"))
                .isEmpty();
        assertThatThrownBy(() -> reloaded.save(new FunctionTrace(
                "conversation-1",
                "ask-request-1",
                "ask-invocation-1",
                "function-invocation-1",
                "foggy.analytics.semantic-queries.execute@v1",
                trace.arguments(),
                json.createObjectNode().put("total", 7),
                200)))
                .hasMessageContaining("changed");
    }
}

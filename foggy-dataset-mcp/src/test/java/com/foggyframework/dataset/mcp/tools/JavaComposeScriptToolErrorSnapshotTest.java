package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java-side producer for Python P0-22 compose-script MCP error payload replay.
 */
@DisplayName("JavaComposeScriptToolErrorSnapshotTest · Python alignment P0-22")
class JavaComposeScriptToolErrorSnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    @DisplayName("writes java_compose_script_tool_error_snapshot_parity.json for Python replay")
    void shouldProduceComposeScriptToolErrorSnapshot() throws Exception {
        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "scriptRuntimeToolErrors");
        snapshot.put("source", "JavaComposeScriptToolErrorSnapshotTest");
        snapshot.put("tool", "dataset.compose_script");
        snapshot.put("cases", cases());

        for (Map<String, Object> c : cases()) {
            assertJavaToolErrorContract(c);
        }

        Path pythonTarget = pythonFixturePath();
        Files.createDirectories(pythonTarget.getParent());
        MAPPER.writeValue(pythonTarget.toFile(), snapshot);

        Path localCopy = Path.of("target", "parity", "java_compose_script_tool_error_snapshot_parity.json");
        Files.createDirectories(localCopy.getParent());
        MAPPER.writeValue(localCopy.toFile(), snapshot);
        assertTrue(Files.exists(pythonTarget),
                "snapshot was not written: " + pythonTarget.toAbsolutePath());
    }

    private static List<Map<String, Object>> cases() {
        return List.of(
                caseDef(
                        "resolver-null-host-misconfig",
                        Map.of("script", "return 1;"),
                        toolContextSnapshot(),
                        expected(
                                "host-misconfig",
                                "internal",
                                List.of("resolver", "returned"),
                                List.of("NullPointerException", "Traceback", "Exception:", "at com.")
                        )
                )
        );
    }

    private static Map<String, Object> caseDef(String id,
                                               Map<String, Object> arguments,
                                               Map<String, Object> context,
                                               Map<String, Object> expected) {
        Map<String, Object> c = ordered();
        c.put("id", id);
        c.put("arguments", arguments);
        c.put("context", context);
        c.put("expected", expected);
        return c;
    }

    private static Map<String, Object> toolContextSnapshot() {
        Map<String, Object> c = ordered();
        c.put("traceId", "java-compose-script-tool-error-snapshot");
        c.put("namespace", "demo");
        c.put("headers", Map.of(
                "X-User-Id", "snapshot-user",
                "X-Namespace", "demo"
        ));
        return c;
    }

    private static Map<String, Object> expected(String errorCode,
                                                String phase,
                                                List<String> messageMarkers,
                                                List<String> forbiddenMarkers) {
        Map<String, Object> e = ordered();
        e.put("status", "error");
        e.put("errorCode", errorCode);
        e.put("phase", phase);
        e.put("messageMarkers", messageMarkers);
        e.put("forbiddenMarkers", forbiddenMarkers);
        return e;
    }

    private static void assertJavaToolErrorContract(Map<String, Object> c) {
        ComposeScriptTool tool = new ComposeScriptTool(noopSemanticService(), ctx -> null, "mysql");

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) c.get("arguments");
        @SuppressWarnings("unchecked")
        Map<String, Object> actual = (Map<String, Object>) tool.execute(arguments, toolContext());
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");

        assertEquals(expected.get("status"), actual.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) actual.get("data");
        assertInstanceOf(Map.class, data);
        assertEquals(expected.get("errorCode"), data.get("error_code"));
        assertEquals(expected.get("phase"), data.get("phase"));
        assertFalse(data.containsKey("model"), "host-misconfig payload should not carry model");

        String payload = data.toString();
        @SuppressWarnings("unchecked")
        List<String> messageMarkers = (List<String>) expected.get("messageMarkers");
        for (String marker : messageMarkers) {
            assertTrue(payload.contains(marker), "message marker missing: " + marker + "\n" + payload);
        }
        @SuppressWarnings("unchecked")
        List<String> forbiddenMarkers = (List<String>) expected.get("forbiddenMarkers");
        for (String marker : forbiddenMarkers) {
            assertFalse(payload.contains(marker), "payload leaked forbidden marker: " + marker + "\n" + payload);
        }
    }

    private static ToolExecutionContext toolContext() {
        return ToolExecutionContext.builder()
                .traceId("java-compose-script-tool-error-snapshot")
                .namespace("demo")
                .headers(Map.of(
                        "X-User-Id", "snapshot-user",
                        "X-Namespace", "demo"
                ))
                .build();
    }

    private static SemanticQueryServiceV3 noopSemanticService() {
        return new SemanticQueryServiceV3() {
            @Override
            public SqlGenerationResult generateSql(String model, SemanticQueryRequest req,
                                                   SemanticRequestContext ctx) {
                throw new UnsupportedOperationException("generateSql is not used by tool error snapshots");
            }

            @Override
            public SemanticQueryResponse queryModel(String model, SemanticQueryRequest req,
                                                    String mode, SemanticRequestContext ctx) {
                throw new UnsupportedOperationException("queryModel is not used by tool error snapshots");
            }

            @Override
            public SemanticQueryResponse validateQuery(String model, SemanticQueryRequest req,
                                                       SemanticRequestContext ctx) {
                throw new UnsupportedOperationException("validateQuery is not used by tool error snapshots");
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params,
                                                        String routeModel) {
                throw new UnsupportedOperationException("executeSql is not used by tool error snapshots");
            }
        };
    }

    private static Path pythonFixturePath() {
        for (Path pythonRoot : List.of(
                Path.of("..", "foggy-data-mcp-bridge-python"),
                Path.of("..", "..", "foggy-data-mcp-bridge-python")
        )) {
            Path fixture = pythonRoot
                    .resolve("tests")
                    .resolve("fixtures")
                    .resolve("java_compose_script_tool_error_snapshot_parity.json")
                    .normalize();
            if (Files.exists(pythonRoot.resolve("pyproject.toml").normalize())) {
                return fixture;
            }
        }
        throw new IllegalStateException("Unable to locate foggy-data-mcp-bridge-python from "
                + Path.of("").toAbsolutePath());
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }
}

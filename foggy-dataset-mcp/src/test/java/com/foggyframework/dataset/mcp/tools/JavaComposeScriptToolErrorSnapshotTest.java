package com.foggyframework.dataset.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java-side producer for Python compose-script MCP error payload replay.
 */
@DisplayName("JavaComposeScriptToolErrorSnapshotTest · Python alignment P0-22/P0-26/P0-34/P0-40")
class JavaComposeScriptToolErrorSnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    @DisplayName("writes java_compose_script_tool_error_snapshot_parity.json for Python replay")
    void shouldProduceComposeScriptToolErrorSnapshot() throws Exception {
        List<Map<String, Object>> snapshotCases = cases();
        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "scriptRuntimeToolErrors");
        snapshot.put("source", "JavaComposeScriptToolErrorSnapshotTest");
        snapshot.put("tool", "dataset.compose_script");
        snapshot.put("cases", snapshotCases);

        for (Map<String, Object> c : snapshotCases) {
            assertJavaToolErrorContract(c);
        }

        Path localArtifact = Path.of("target", "parity",
                "java_compose_script_tool_error_snapshot_parity.json");
        Files.createDirectories(localArtifact.getParent());
        MAPPER.writeValue(localArtifact.toFile(), snapshot);
        assertTrue(Files.exists(localArtifact),
                "snapshot was not written: " + localArtifact.toAbsolutePath());
    }

    private static List<Map<String, Object>> cases() {
        return List.of(
                caseDef(
                        "missing-script",
                        Map.of(),
                        toolContextSnapshot(),
                        expected(
                                "missing-script",
                                "internal",
                                List.of("script", "required"),
                                List.of("NullPointerException", "Traceback", "Exception:", "at com.")
                        )
                ),
                caseDef(
                        "missing-context",
                        Map.of("script", "return 1;"),
                        null,
                        expected(
                                "internal-error",
                                "internal",
                                List.of("ToolExecutionContext", "required"),
                                List.of("NullPointerException", "Traceback", "Exception:", "at com.")
                        )
                ),
                caseDef(
                        "missing-user-id-header",
                        Map.of("script", "return 1;"),
                        missingUserIdToolContextSnapshot(),
                        expected(
                                "internal-error",
                                "internal",
                                List.of("X-User-Id", "required"),
                                List.of("NullPointerException", "Traceback", "Exception:", "at com.")
                        )
                ),
                caseDef(
                        "missing-namespace-header",
                        Map.of("script", "return 1;"),
                        missingNamespaceToolContextSnapshot(),
                        expected(
                                "internal-error",
                                "internal",
                                List.of("X-Namespace", "required"),
                                List.of("NullPointerException", "Traceback", "Exception:", "at com.")
                        )
                ),
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
                ),
                caseDef(
                        "resolver-factory-exception",
                        Map.of("script", "return 1;"),
                        toolContextSnapshot(),
                        expected(
                                "internal-error",
                                "internal",
                                List.of("resolver", "factory", "boom"),
                                List.of("NullPointerException", "Traceback", "Exception:", "at com.")
                        )
                ),
                caseDef(
                        "resolver-resolve-exception",
                        Map.of("script", """
                                return from({
                                  model: "FactSalesModel",
                                  columns: ["salesAmount"]
                                }).execute();
                                """),
                        toolContextSnapshot(),
                        expected(
                                "compose-authority-resolve/upstream-failure",
                                "permission-resolve",
                                List.of("AuthorityResolver.resolve", "unexpected exception", "details"),
                                List.of("NullPointerException", "Traceback", "Exception:", "at com.")
                        )
                ),
                caseDef(
                        "remote-principal-mismatch",
                        remotePrincipalMismatchArguments(),
                        remoteToolContextSnapshot(),
                        expected(
                                "compose-authority-resolve/principal-mismatch",
                                "permission-resolve",
                                List.of("principal", "differs"),
                                List.of("NullPointerException", "Traceback", "Exception:", "at com.")
                        )
                ),
                caseDef(
                        "remote-missing-authority-binding",
                        remoteMissingAuthorityBindingArguments(),
                        remoteToolContextSnapshot(),
                        expected(
                                "compose-authority-resolve/invalid-response",
                                "permission-resolve",
                                List.of("authority", "binding"),
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

    private static Map<String, Object> missingUserIdToolContextSnapshot() {
        Map<String, Object> c = ordered();
        c.put("traceId", "java-compose-script-tool-error-snapshot");
        c.put("namespace", "demo");
        c.put("headers", Map.of(
                "X-Namespace", "demo"
        ));
        return c;
    }

    private static Map<String, Object> missingNamespaceToolContextSnapshot() {
        Map<String, Object> c = ordered();
        c.put("traceId", "java-compose-script-tool-error-snapshot");
        c.put("namespace", null);
        c.put("headers", Map.of(
                "X-User-Id", "snapshot-user"
        ));
        return c;
    }

    private static Map<String, Object> remoteToolContextSnapshot() {
        Map<String, Object> c = ordered();
        c.put("traceId", "java-compose-script-tool-error-snapshot");
        c.put("namespace", "odoo");
        c.put("headers", Map.of(
                ComposeScriptTool.REMOTE_COMPOSE_HEADER, "1",
                "X-User-Id", "u1",
                "X-Tenant-Id", "t1",
                "X-Namespace", "odoo",
                "X-Roles", "analyst"
        ));
        return c;
    }

    private static Map<String, Object> remotePrincipalMismatchArguments() {
        Map<String, Object> args = ordered();
        args.put("script", """
                return from({
                  model: "FactSalesModel",
                  columns: ["salesAmount"]
                }).execute();
                """);
        args.put(ComposeScriptTool.AUTHORITY_BINDING_ARGUMENT, Map.of(
                "version", "foggy.compose.authority-binding.v1",
                "issuer", "test-fixture-issuer",
                "namespace", "odoo",
                "principal", Map.of("userId", "u2", "tenantId", "t1"),
                "bindings", Map.of("FactSalesModel", Map.of(
                        "fieldAccess", List.of("salesAmount"),
                        "deniedColumns", List.of(),
                        "systemSlice", List.of()
                ))
        ));
        return args;
    }

    private static Map<String, Object> remoteMissingAuthorityBindingArguments() {
        Map<String, Object> args = ordered();
        args.put("script", """
                return from({
                  model: "FactSalesModel",
                  columns: ["salesAmount"]
                }).execute();
                """);
        return args;
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
        ComposeScriptTool tool = new ComposeScriptTool(
                noopSemanticService(),
                resolverFactoryFor((String) c.get("id")),
                "mysql"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) c.get("arguments");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) c.get("context");
        @SuppressWarnings("unchecked")
        Map<String, Object> actual = (Map<String, Object>) tool.execute(
                new LinkedHashMap<>(arguments),
                context == null ? null : toolContext(context)
        );
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

    private static Function<ToolExecutionContext, AuthorityResolver> resolverFactoryFor(String caseId) {
        if ("resolver-null-host-misconfig".equals(caseId)) {
            return ctx -> null;
        }
        if ("resolver-factory-exception".equals(caseId)) {
            return ctx -> {
                throw new IllegalStateException("resolver factory boom");
            };
        }
        if ("resolver-resolve-exception".equals(caseId)) {
            return ctx -> request -> {
                throw new IllegalStateException("resolver resolve boom");
            };
        }
        return ctx -> request -> {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String modelName : request.modelNames()) {
                bindings.put(modelName, ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        };
    }

    private static ToolExecutionContext toolContext(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) c.get("headers");
        return ToolExecutionContext.builder()
                .traceId((String) c.get("traceId"))
                .namespace((String) c.get("namespace"))
                .headers(headers)
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

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }
}

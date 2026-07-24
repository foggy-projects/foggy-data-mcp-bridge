package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeHolder;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityErrorCodes;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("ComposeScriptTool · remote authority binding")
class ComposeScriptToolBindingTest {

    private static final String SCRIPT = """
            const sales = dsl({
                model: "SalesQM",
                columns: ["amount"]
            });
            return sales.execute();
            """;

    @Test
    @DisplayName("remote header consumes host-private binding and passes authority context to semantic service")
    void remoteHeaderUsesAuthorityBinding() {
        CapturingSemanticService service = new CapturingSemanticService();
        ComposeScriptTool tool = new ComposeScriptTool(service, fallbackResolver(), "mysql");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("script", SCRIPT);
        args.put(ComposeScriptTool.AUTHORITY_BINDING_ARGUMENT, validEnvelope("u1", "t1"));

        Map<String, Object> result = executeWithBundle(tool, service, args, remoteToolContext(), "odoo");

        assertEquals("success", result.get("status"));
        assertFalse(args.containsKey(ComposeScriptTool.AUTHORITY_BINDING_ARGUMENT));
        assertNotNull(service.capturedContext);
        assertEquals("odoo", service.capturedContext.getNamespace());
        assertEquals(List.of("amount"), List.copyOf(service.capturedContext.getFieldAccess()));
        assertEquals("secret_amount", service.capturedContext.getDeniedColumns().get(0).getColumn());
        assertEquals("customer_key", service.capturedContext.getSystemSlice().get(0).getField());
        assertEquals(42, service.capturedContext.getSystemSlice().get(0).getValue());
    }

    @Test
    @DisplayName("non-remote forged binding is ignored and embedded resolver remains authoritative")
    void nonRemoteForgedEnvelopeIgnored() {
        CapturingSemanticService service = new CapturingSemanticService();
        ComposeScriptTool tool = new ComposeScriptTool(service, fallbackResolver(), "mysql");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("script", SCRIPT);
        args.put(ComposeScriptTool.AUTHORITY_BINDING_ARGUMENT, invalidEnvelope());

        Map<String, Object> result = executeWithBundle(tool, service, args,
                ToolExecutionContext.of("trace-1", null), "odoo");

        assertEquals("success", result.get("status"));
        assertFalse(args.containsKey(ComposeScriptTool.AUTHORITY_BINDING_ARGUMENT));
        assertNotNull(service.capturedContext);
        assertEquals("fallback_allowed", service.capturedContext.getFieldAccess().iterator().next());
        assertEquals(7, service.capturedContext.getSystemSlice().get(0).getValue());
    }

    @Test
    @DisplayName("remote header without binding fails closed")
    void remoteMissingEnvelopeFailsClosed() {
        CapturingSemanticService service = new CapturingSemanticService();
        ComposeScriptTool tool = new ComposeScriptTool(service, fallbackResolver(), "mysql");

        Map<String, Object> result = executeWithBundle(tool, service,
                new LinkedHashMap<>(Map.of("script", SCRIPT)), remoteToolContext(), "odoo");

        assertEquals("error", result.get("status"));
        Map<?, ?> data = assertInstanceOf(Map.class, result.get("data"));
        assertEquals(AuthorityErrorCodes.INVALID_RESPONSE, data.get("error_code"));
        assertEquals("permission-resolve", data.get("phase"));
    }

    @Test
    @DisplayName("remote binding principal mismatch fails closed")
    void remotePrincipalMismatchFailsClosed() {
        CapturingSemanticService service = new CapturingSemanticService();
        ComposeScriptTool tool = new ComposeScriptTool(service, fallbackResolver(), "mysql");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("script", SCRIPT);
        args.put(ComposeScriptTool.AUTHORITY_BINDING_ARGUMENT, validEnvelope("u2", "t1"));

        Map<String, Object> result = executeWithBundle(tool, service, args, remoteToolContext(), "odoo");

        assertEquals("error", result.get("status"));
        Map<?, ?> data = assertInstanceOf(Map.class, result.get("data"));
        assertEquals(AuthorityErrorCodes.PRINCIPAL_MISMATCH, data.get("error_code"));
        assertEquals("permission-resolve", data.get("phase"));
    }

    @Test
    @DisplayName("remote header mode builds compose context without ambient runtime bundle")
    void remoteHeaderModeBuildsContextWithoutBundle() {
        CapturingSemanticService service = new CapturingSemanticService();
        ComposeScriptTool tool = new ComposeScriptTool(service, fallbackResolver(), "mysql");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("script", SCRIPT);
        args.put(ComposeScriptTool.AUTHORITY_BINDING_ARGUMENT, validEnvelope("u1", "t1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) tool.execute(args, remoteHeaderToolContext());

        assertEquals("success", result.get("status"));
        assertNotNull(service.capturedContext);
        assertEquals("odoo", service.capturedContext.getNamespace());
        assertEquals(List.of("amount"), List.copyOf(service.capturedContext.getFieldAccess()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> executeWithBundle(ComposeScriptTool tool,
                                                         CapturingSemanticService service,
                                                         Map<String, Object> args,
                                                         ToolExecutionContext toolContext,
                                                         String namespace) {
        ComposeQueryContext ctx = ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("u1")
                        .tenantId("t1")
                        .roles(List.of("analyst"))
                        .build())
                .namespace(namespace)
                .traceId("trace-1")
                .authorityResolver(request -> AuthorityResolution.builder()
                        .bindings(Map.of("SalesQM", ModelBinding.builder().build()))
                        .build())
                .build();
        ComposeRuntimeBundle bundle = ComposeRuntimeBundle.builder()
                .ctx(ctx)
                .semanticService(service)
                .dialect("mysql")
                .build();
        ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(bundle);
        try {
            return (Map<String, Object>) tool.execute(args, toolContext);
        } finally {
            ComposeRuntimeHolder.popBundle(token);
        }
    }

    private static ToolExecutionContext remoteToolContext() {
        return ToolExecutionContext.builder()
                .traceId("trace-1")
                .headers(Map.of(ComposeScriptTool.REMOTE_COMPOSE_HEADER, "1"))
                .build();
    }

    private static ToolExecutionContext remoteHeaderToolContext() {
        return ToolExecutionContext.builder()
                .traceId("trace-1")
                .headers(Map.of(
                        ComposeScriptTool.REMOTE_COMPOSE_HEADER, "1",
                        "X-User-Id", "u1",
                        "X-Tenant-Id", "t1",
                        "X-Namespace", "odoo",
                        "X-Roles", "analyst"))
                .build();
    }

    private static Function<ToolExecutionContext, AuthorityResolver> fallbackResolver() {
        return ignored -> request -> AuthorityResolution.builder()
                .bindings(Map.of("SalesQM", ModelBinding.builder()
                        .fieldAccess(List.of("fallback_allowed"))
                        .systemSlice(List.of(new SliceRequestDef("fallback_key", "eq", 7)))
                        .build()))
                .build();
    }

    private static Map<String, Object> validEnvelope(String userId, String tenantId) {
        return Map.of(
                "version", "foggy.compose.authority-binding.v1",
                "issuer", "test-fixture-issuer",
                "namespace", "odoo",
                "principal", Map.of(
                        "userId", userId,
                        "tenantId", tenantId),
                "bindings", Map.of("SalesQM", Map.of(
                        "fieldAccess", List.of("amount"),
                        "deniedColumns", List.of(Map.of(
                                "table", "fact_sales",
                                "column", "secret_amount")),
                        "systemSlice", List.of(Map.of(
                                "field", "customer_key",
                                "op", "eq",
                                "value", 42)))));
    }

    private static Map<String, Object> invalidEnvelope() {
        return Map.of(
                "version", "bad-version",
                "issuer", "bad-issuer",
                "namespace", "odoo",
                "principal", Map.of("userId", "attacker"),
                "bindings", Map.of());
    }

    private static final class CapturingSemanticService implements SemanticQueryServiceV3 {
        private SemanticRequestContext capturedContext;

        @Override
        public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                                SemanticRequestContext context) {
            throw new UnsupportedOperationException("queryModel is not used by compose script tests");
        }

        @Override
        public SemanticQueryResponse validateQuery(String model, SemanticQueryRequest request,
                                                   SemanticRequestContext context) {
            throw new UnsupportedOperationException("validateQuery is not used by compose script tests");
        }

        @Override
        public SqlGenerationResult generateSql(String model, SemanticQueryRequest request,
                                               SemanticRequestContext context) {
            this.capturedContext = context;
            return new SqlGenerationResult("select amount from fact_sales where customer_key = ?",
                    List.of(42), null);
        }

        @Override
        public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
            return List.of(Map.of("amount", 100));
        }
    }
}

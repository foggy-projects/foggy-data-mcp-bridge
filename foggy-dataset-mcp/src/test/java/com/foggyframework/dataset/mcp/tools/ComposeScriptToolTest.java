package com.foggyframework.dataset.mcp.tools;


import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeBundle;
import com.foggyframework.dataset.model.engine.compose.runtime.ComposeRuntimeHolder;

import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.mcp.spi.ToolCategory;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * M7 unit tests for {@link ComposeScriptTool}.
 *
 * @since 8.2.0.beta
 */
@DisplayName("ComposeScriptTool · MCP 工具单元测试")
class ComposeScriptToolTest {

    private SemanticQueryServiceV3 mockSvc;
    private Function<ToolExecutionContext, AuthorityResolver> resolverFactory;
    private AuthorityResolver mockResolver;
    private ToolExecutionContext mockCtx;

    @BeforeEach
    void setUp() {
        mockSvc = mock(SemanticQueryServiceV3.class);
        mockResolver = mock(AuthorityResolver.class);
        resolverFactory = ctx -> mockResolver;
        mockCtx = mock(ToolExecutionContext.class);
    }

    private ComposeScriptTool tool() {
        return new ComposeScriptTool(mockSvc, resolverFactory, "mysql");
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicPropertiesTest {

        @Test
        @DisplayName("name is dataset.compose_script")
        void name() {
            assertEquals("dataset.compose_script", tool().getName());
        }

        @Test
        @DisplayName("categories includes QUERY")
        void categories() {
            assertTrue(tool().getCategories().contains(ToolCategory.QUERY));
        }

        @Test
        @DisplayName("constructor rejects null semanticService")
        void nullSvc() {
            assertThrows(NullPointerException.class,
                    () -> new ComposeScriptTool(null, resolverFactory, "mysql"));
        }

        @Test
        @DisplayName("constructor rejects null resolverFactory")
        void nullResolver() {
            assertThrows(NullPointerException.class,
                    () -> new ComposeScriptTool(mockSvc, null, "mysql"));
        }
    }

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTest {

        @Test
        @DisplayName("missing script returns error with phase=internal")
        void missingScript() {
            Map<String, Object> result = (Map<String, Object>) tool()
                    .execute(Map.of(), mockCtx);
            assertEquals("error", result.get("status"));
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertEquals("missing-script", data.get("error_code"));
            assertEquals("internal", data.get("phase"));
        }

        @Test
        @DisplayName("blank script returns error with phase=internal")
        void blankScript() {
            Map<String, Object> result = (Map<String, Object>) tool()
                    .execute(Map.of("script", "  "), mockCtx);
            assertEquals("error", result.get("status"));
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertEquals("missing-script", data.get("error_code"));
        }

        @Test
        @DisplayName("null script returns error")
        void nullScript() {
            Map<String, Object> args = new HashMap<>();
            args.put("script", null);
            Map<String, Object> result = (Map<String, Object>) tool().execute(args, mockCtx);
            assertEquals("error", result.get("status"));
        }
    }

    @Nested
    @DisplayName("Resolver Factory")
    class ResolverFactoryTest {

        @Test
        @DisplayName("resolver factory returning null → host-misconfig error")
        void resolverNull() {
            ComposeScriptTool t = new ComposeScriptTool(mockSvc, ctx -> null, "mysql");
            Map<String, Object> result = (Map<String, Object>) t
                    .execute(Map.of("script", "return 1;"), mockCtx);
            assertEquals("error", result.get("status"));
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertEquals("host-misconfig", data.get("error_code"));
            assertEquals("internal", data.get("phase"));
        }
    }

    @Nested
    @DisplayName("Error Phase Mapping")
    class ErrorPhaseMappingTest {

        /**
         * Helper that pre-sets a ComposeRuntimeBundle so ContextBridge returns
         * a ComposeQueryContext, then executes the tool.
         */
        @SuppressWarnings("unchecked")
        private Map<String, Object> executeWithBundle(String script) {
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(Principal.builder()
                            .userId("u1").tenantId("t1").roles(List.of("analyst")).build())
                    .namespace("ns1")
                    .traceId("trace-1")
                    .authorityResolver(mockResolver)
                    .build();
            ComposeRuntimeBundle bundle = ComposeRuntimeBundle.builder()
                    .ctx(ctx)
                    .semanticService(mockSvc)
                    .dialect("mysql")
                    .build();
            ComposeRuntimeHolder.Token token = ComposeRuntimeHolder.setBundle(bundle);
            try {
                return (Map<String, Object>) tool().execute(Map.of("script", script), mockCtx);
            } finally {
                ComposeRuntimeHolder.popBundle(token);
            }
        }

        @Test
        @DisplayName("no bundle (header mode) → internal error UnsupportedOperationException")
        void noBundleHeaderMode() {
            // Don't pre-set bundle — ContextBridge throws UnsupportedOperationException
            Map<String, Object> result = (Map<String, Object>) tool()
                    .execute(Map.of("script", "return 1;"), mockCtx);
            // UnsupportedOperationException is not RuntimeException in the catch chain,
            // but it IS a RuntimeException subclass
            assertEquals("error", result.get("status"));
        }

        @Test
        @DisplayName("simple return with bundle → success")
        void simpleReturn_success() {
            Map<String, Object> result = executeWithBundle("return 1;");
            assertEquals("success", result.get("status"));
        }

        @Test
        @DisplayName("empty plans result carries terminal semantic")
        void emptyPlansSemantic_success() {
            Map<String, Object> result = executeWithBundle("return {plans: []};");
            assertEquals("success", result.get("status"));
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            Map<String, Object> value = (Map<String, Object>) data.get("value");
            Map<String, Object> semantic = (Map<String, Object>) value.get("semantic");
            assertEquals(Boolean.TRUE, semantic.get("emptyResult"));
            assertEquals("NO_MATCHING_ROWS_AFTER_COMPOSE", semantic.get("emptyReason"));
            assertEquals(Boolean.TRUE, semantic.get("shouldAnswerDirectly"));
        }
    }
}

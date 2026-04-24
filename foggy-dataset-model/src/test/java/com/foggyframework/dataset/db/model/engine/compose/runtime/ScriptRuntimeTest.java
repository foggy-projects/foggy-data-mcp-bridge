package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * M7 unit tests for {@link ScriptRuntime}.
 *
 * @since 8.2.0.beta
 */
@DisplayName("ScriptRuntime · 脚本执行沙箱单元测试")
class ScriptRuntimeTest {

    private static ComposeQueryContext dummyCtx() {
        AuthorityResolver resolver = request -> {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String name : request.modelNames()) {
                bindings.put(name, ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        };
        return ComposeQueryContext.builder()
                .principal(Principal.builder()
                        .userId("u1").tenantId("t1").roles(List.of("analyst")).build())
                .namespace("ns1")
                .traceId("trace-1")
                .authorityResolver(resolver)
                .build();
    }

    @Test
    @DisplayName("ALLOWED_SCRIPT_GLOBALS is exactly {from, dsl}")
    void allowedGlobals_frozen() {
        assertEquals(Set.of("from", "dsl"), ScriptRuntime.ALLOWED_SCRIPT_GLOBALS);
    }

    @Test
    @DisplayName("ALLOWED_SCRIPT_GLOBALS has exactly 2 elements")
    void allowedGlobals_size() {
        assertEquals(2, ScriptRuntime.ALLOWED_SCRIPT_GLOBALS.size());
    }

    @Test
    @DisplayName("runScript(null ctx) throws IAE")
    void nullCtx_throwsIAE() {
        assertThrows(IllegalArgumentException.class,
                () -> ScriptRuntime.runScript("return 1;", null,
                        mock(SemanticQueryServiceV3.class), "mysql"));
    }

    @Test
    @DisplayName("runScript(null semanticService) throws IAE")
    void nullService_throwsIAE() {
        assertThrows(IllegalArgumentException.class,
                () -> ScriptRuntime.runScript("return 1;", dummyCtx(), null, "mysql"));
    }

    @Test
    @DisplayName("simple return expression evaluates")
    void simpleReturn_succeeds() {
        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                "return 42;", dummyCtx(),
                mock(SemanticQueryServiceV3.class), "mysql");
        assertNotNull(result);
        // Note: fsscript eval may return int or long depending on implementation
        assertTrue(result.value() instanceof Number);
    }

    @Test
    @DisplayName("e2e: from() call executes CTE query and returns rows")
    void endToEnd_fromCall_executesCTE() {
        // Setup mock rows to return
        List<Map<String, Object>> expectedRows = List.of(
                Map.of("amount", 100),
                Map.of("amount", 200)
        );

        // Build a fake semantic service that can "compile" and "execute"
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) {
                // Return a dummy SQL when compiled
                return new com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult(
                        "SELECT amount FROM fake_table", List.of(), null);
            }

            @Override
            public com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                return expectedRows;
            }
        };

        String script = 
                "let base = dsl({model: 'SaleOrderQM', columns: ['amount']});\n" +
                "let cte = dsl({source: base, columns: ['amount'], limit: 10});\n" +
                "return cte.execute();";

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                script, dummyCtx(), fakeSvc, "mysql");

        assertNotNull(result);
        assertEquals(expectedRows, result.value());
    }


    @Test
    @DisplayName("ThreadLocal bundle is cleaned up after runScript")
    void bundleCleanedUp_afterRunScript() {
        assertNull(ComposeRuntimeHolder.currentBundle(), "precondition: no bundle before");
        ScriptRuntime.runScript("return 1;", dummyCtx(),
                mock(SemanticQueryServiceV3.class), "mysql");
        assertNull(ComposeRuntimeHolder.currentBundle(), "postcondition: no bundle after");
    }

    @Test
    @DisplayName("ThreadLocal bundle is cleaned up even on script error")
    void bundleCleanedUp_onScriptError() {
        assertNull(ComposeRuntimeHolder.currentBundle());
        try {
            // script syntax that may cause an error
            ScriptRuntime.runScript("throw 'boom';", dummyCtx(),
                    mock(SemanticQueryServiceV3.class), "mysql");
        } catch (Exception ignored) {
            // ignore script errors
        }
        assertNull(ComposeRuntimeHolder.currentBundle(),
                "bundle must be cleaned up even if script throws");
    }

    @Test
    @DisplayName("dialect defaults to mysql when null")
    void dialectDefaults_toMysql() {
        // Should not throw — just verifying the null-dialect path
        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                "return 1;", dummyCtx(),
                mock(SemanticQueryServiceV3.class), null);
        assertNotNull(result);
    }

    // ---- Safety red-line scans (compile-time, not runtime) ----

    private static final java.nio.file.Path SCRIPT_RUNTIME_SRC = java.nio.file.Path.of(
            "src/main/java/com/foggyframework/dataset/db/model/engine/compose/runtime/ScriptRuntime.java");

    private static String readScriptRuntimeSource() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                java.nio.file.Files.exists(SCRIPT_RUNTIME_SRC),
                "Skipping source-scan test: source file not found at " + SCRIPT_RUNTIME_SRC
                        + " (CWD=" + System.getProperty("user.dir") + ")");
        return new String(java.nio.file.Files.readAllBytes(SCRIPT_RUNTIME_SRC));
    }

    @Test
    @DisplayName("ScriptRuntime source does not contain Runtime.getRuntime().exec")
    void noRuntimeExec() throws Exception {
        String source = readScriptRuntimeSource();
        assertFalse(source.contains("Runtime.getRuntime().exec"),
                "ScriptRuntime must not call Runtime.exec()");
    }

    @Test
    @DisplayName("ScriptRuntime source does not contain ProcessBuilder")
    void noProcessBuilder() throws Exception {
        String source = readScriptRuntimeSource();
        assertFalse(source.contains("ProcessBuilder"),
                "ScriptRuntime must not use ProcessBuilder");
    }
}

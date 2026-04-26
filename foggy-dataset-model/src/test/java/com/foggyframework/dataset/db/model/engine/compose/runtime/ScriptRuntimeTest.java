package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
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

    private static SemanticQueryServiceV3 previewOnlySemanticService() {
        return new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) {
                return new com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult(
                        "SELECT 1 AS __stub__", List.of(), null);
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
                throw new AssertionError("Preview-mode script resource test must not execute SQL");
            }
        };
    }

    @Test
    @DisplayName("ALLOWED_SCRIPT_GLOBALS is exactly {from, dsl, Query}")
    void allowedGlobals_frozen() {
        assertEquals(Set.of("from", "dsl", "Query"), ScriptRuntime.ALLOWED_SCRIPT_GLOBALS);
    }

    @Test
    @DisplayName("ALLOWED_SCRIPT_GLOBALS has exactly 3 elements")
    void allowedGlobals_size() {
        assertEquals(3, ScriptRuntime.ALLOWED_SCRIPT_GLOBALS.size());
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
    @DisplayName("e2e: runScript auto-executes QueryPlans inside plans map (previewMode=false)")
    void endToEnd_fromCall_returnsMapAndExecutesPlans() {
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
                "return { plans: { summary: cte }, metadata: { title: 'hello' } };";

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                script, dummyCtx(), fakeSvc, "mysql", false);

        assertNotNull(result);
        assertTrue(result.value() instanceof Map);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> mapVal = (Map<String, Object>) result.value();
        assertEquals("hello", ((Map<String, Object>) mapVal.get("metadata")).get("title"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> plansMap = (Map<String, Object>) mapVal.get("plans");
        assertEquals(expectedRows, plansMap.get("summary"));
    }

    @Test
    @DisplayName("e2e: runScript auto-generates SQL for QueryPlans inside plans map (previewMode=true)")
    void endToEnd_fromCall_returnsMapAndPreviewsPlans() {
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) {
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
                throw new UnsupportedOperationException(); // Should not be called in preview mode
            }
        };

        String script =
                "let base = dsl({model: 'SaleOrderQM', columns: ['amount']});\n" +
                "let cte = dsl({source: base, columns: ['amount'], limit: 10});\n" +
                "return { plans: { summary: cte }, metadata: { title: 'hello' } };";

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                script, dummyCtx(), fakeSvc, "mysql", true);

        assertNotNull(result);
        assertTrue(result.value() instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> mapVal = (Map<String, Object>) result.value();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> plansMap = (Map<String, Object>) mapVal.get("plans");
        Object summaryPlanSql = plansMap.get("summary");
        assertNotNull(summaryPlanSql);
        assertTrue(((com.foggyframework.dataset.db.model.engine.compose.ComposedSql) summaryPlanSql).getSql().contains("SELECT amount FROM fake_table"));
    }

    @Test
    @DisplayName("resource CTE scenario scripts preview through ScriptRuntime")
    void resourceCteScenarioScripts_previewThroughScriptRuntime() throws Exception {
        Path scriptDir = Path.of("src/test/resources/scripts");
        List<String> scriptNames = List.of(
                "derived_query_scenario.js",
                "join_scenario.js",
                "union_scenario.js");

        for (String scriptName : scriptNames) {
            Path scriptPath = scriptDir.resolve(scriptName);
            assertTrue(Files.exists(scriptPath), "missing scenario script: " + scriptPath);

            ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                    Files.readString(scriptPath),
                    dummyCtx(),
                    previewOnlySemanticService(),
                    "mysql8",
                    true);

            assertNotNull(result);
            assertTrue(result.value() instanceof Map, scriptName + " should return a result map");

            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result.value();
            assertTrue(resultMap.get("metadata") instanceof Map,
                    scriptName + " should preserve metadata");
            assertTrue(resultMap.get("plans") instanceof Map,
                    scriptName + " should return named plans");

            @SuppressWarnings("unchecked")
            Map<String, Object> plans = (Map<String, Object>) resultMap.get("plans");
            assertFalse(plans.isEmpty(), scriptName + " should contain at least one plan");
            for (Map.Entry<String, Object> entry : plans.entrySet()) {
                assertTrue(entry.getValue() instanceof com.foggyframework.dataset.db.model.engine.compose.ComposedSql,
                        scriptName + " plan '" + entry.getKey() + "' should preview to ComposedSql");
                com.foggyframework.dataset.db.model.engine.compose.ComposedSql sql =
                        (com.foggyframework.dataset.db.model.engine.compose.ComposedSql) entry.getValue();
                assertNotNull(sql.getSql(), scriptName + " plan '" + entry.getKey() + "' SQL");
                assertFalse(sql.getSql().isBlank(), scriptName + " plan '" + entry.getKey() + "' SQL");
            }
        }
    }

    @Test
    @DisplayName("e2e: runScript auto-executes QueryPlans inside plans list")
    void endToEnd_fromCall_returnsListAndExecutesPlans() {
        List<Map<String, Object>> expectedRows = List.of(Map.of("amount", 100));
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) { return new com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult("SELECT 1", java.util.List.of(), null); }
            @Override
            public com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) { return null; }
            @Override
            public com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) { return null; }
            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                return expectedRows;
            }
        };

        String script = "let cte = dsl({model: 'QM', columns: ['amount']});\n" +
                "return { plans: [cte] };";

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(script, dummyCtx(), fakeSvc, "mysql", false);
        Map<String, Object> mapVal = (Map<String, Object>) result.value();
        List<Object> plansList = (List<Object>) mapVal.get("plans");
        assertEquals(expectedRows, plansList.get(0));
    }

    @Test
    @DisplayName("e2e: runScript auto-executes QueryPlan as single object in plans")
    void endToEnd_fromCall_returnsSinglePlanAndExecutes() {
        List<Map<String, Object>> expectedRows = List.of(Map.of("amount", 100));
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) { return new com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult("SELECT 1", java.util.List.of(), null); }
            @Override
            public com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) { return null; }
            @Override
            public com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext ctx) { return null; }
            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                return expectedRows;
            }
        };

        String script = "let cte = dsl({model: 'QM', columns: ['amount']});\n" +
                "return { plans: cte };";

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(script, dummyCtx(), fakeSvc, "mysql", false);
        Map<String, Object> mapVal = (Map<String, Object>) result.value();
        assertEquals(expectedRows, mapVal.get("plans"));
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

package com.foggyframework.dataset.model.engine.compose.runtime;

import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.compose.capability.CapabilityException;
import com.foggyframework.dataset.model.engine.compose.capability.CapabilityPolicy;
import com.foggyframework.dataset.model.engine.compose.capability.CapabilityRegistry;
import com.foggyframework.dataset.model.engine.compose.capability.FunctionDescriptor;
import com.foggyframework.dataset.model.engine.compose.capability.MethodDescriptor;
import com.foggyframework.dataset.model.engine.compose.capability.ObjectFacadeDescriptor;
import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.ComposedDataSetResult;
import com.foggyframework.dataset.model.engine.compose.DslQueryFunction;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult(
                        "SELECT 1 AS __stub__", List.of(), null);
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                throw new AssertionError("Preview-mode script resource test must not execute SQL");
            }
        };
    }

    @Test
    @DisplayName("ALLOWED_SCRIPT_GLOBALS is exactly {from, dsl, Query, subquery}")
    void allowedGlobals_frozen() {
        assertEquals(Set.of("from", "dsl", "Query", "subquery"), ScriptRuntime.ALLOWED_SCRIPT_GLOBALS);
    }

    @Test
    @DisplayName("script variable aliases resolve post-join qualified field strings")
    void scriptVariableAliasesResolvePostJoinQualifiedFieldStrings() {
        String script = """
                const firstOrders = dsl({
                  model: "OdooSaleOrderQueryModel",
                  columns: ["partner$id", "partner$caption", "MIN(dateOrder$id) as firstOrderDate"],
                  groupBy: ["partner$id", "partner$caption"]
                });

                const mayFirstCustomers = firstOrders.query({
                  slice: [
                    {"field": "firstOrderDate", "op": ">=", "value": "2026-05-01"},
                    {"field": "firstOrderDate", "op": "<", "value": "2026-06-01"}
                  ]
                });

                const mayOrders = dsl({
                  model: "OdooSaleOrderQueryModel",
                  columns: ["partner$id", "count(id) as orderCount", "sum(amountTotal) as totalAmount"],
                  slice: [
                    {"field": "dateOrder$id", "op": ">=", "value": "2026-05-01"},
                    {"field": "dateOrder$id", "op": "<", "value": "2026-06-01"}
                  ],
                  groupBy: ["partner$id"]
                });

                const joined = mayFirstCustomers.join(mayOrders, "left", [{"left": "partner$id", "op": "=", "right": "partner$id"}]);

                const result = joined.query({
                  columns: ["firstOrders.partner$caption", "left.firstOrderDate", "mayOrders.orderCount", "right.totalAmount"],
                  orderBy: ["-mayOrders.totalAmount"]
                });

                return { plans: result };
                """;

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                script, dummyCtx(), previewOnlySemanticService(), "sqlite", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) result.value();
        Object plans = value.get("plans");
        assertInstanceOf(ComposedSql.class, plans);
        ComposedSql sql = (ComposedSql) plans;
        assertTrue(sql.getSql().contains("partner$caption"));
        assertTrue(sql.getSql().contains("ORDER BY"));
        assertTrue(sql.getSql().contains("totalAmount"));
        assertFalse(sql.getSql().contains("firstOrders.partner$caption"));
        assertFalse(sql.getSql().contains("mayOrders.totalAmount"));
    }

    @Test
    @DisplayName("ALLOWED_SCRIPT_GLOBALS has exactly 4 elements")
    void allowedGlobals_size() {
        assertEquals(4, ScriptRuntime.ALLOWED_SCRIPT_GLOBALS.size());
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
    @DisplayName("pure_runtime capability is visible only when registry and policy allow it")
    void pureRuntimeCapability_allowedByPolicy() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.registerFunction(new FunctionDescriptor(
                "fiscalYear",
                "pure_runtime",
                List.of(Map.of("name", "month", "type", "int")),
                "int",
                true,
                "none",
                List.of("compose_runtime"),
                "test.fiscalYear",
                null
        ), args -> ((Number) args.get("month")).intValue() >= 4 ? 2025 : 2024);
        CapabilityPolicy policy = new CapabilityPolicy(
                Set.of("fiscalYear"),
                Map.of(),
                Set.of()
        );

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                "return fiscalYear(4);",
                dummyCtx(),
                mock(SemanticQueryServiceV3.class),
                "mysql",
                registry,
                policy);

        assertEquals(2025, ((Number) result.value()).intValue());
    }

    @Test
    @DisplayName("pure_runtime capability is not injected by default or when policy denies it")
    void pureRuntimeCapability_policyDeny() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.registerFunction(new FunctionDescriptor(
                "fiscalYear",
                "pure_runtime",
                List.of(Map.of("name", "month", "type", "int")),
                "int",
                true,
                "none",
                List.of("compose_runtime"),
                "test.fiscalYear",
                null
        ), args -> 2025);

        assertThrows(RuntimeException.class, () -> ScriptRuntime.runScript(
                "return fiscalYear(4);",
                dummyCtx(),
                mock(SemanticQueryServiceV3.class),
                "mysql",
                registry,
                CapabilityPolicy.empty()));
    }

    @Test
    @DisplayName("pure_runtime capability rejects unsafe return values")
    void pureRuntimeCapability_returnTypeDeny() {
        CapabilityRegistry registry = new CapabilityRegistry();
        registry.registerFunction(new FunctionDescriptor(
                "unsafeValue",
                "pure_runtime",
                List.of(),
                "string",
                true,
                "none",
                List.of("compose_runtime"),
                "test.unsafeValue",
                null
        ), args -> new Thread());
        CapabilityPolicy policy = new CapabilityPolicy(
                Set.of("unsafeValue"),
                Map.of(),
                Set.of()
        );

        assertThrows(CapabilityException.ReturnTypeDenied.class, () -> ScriptRuntime.runScript(
                "return unsafeValue();",
                dummyCtx(),
                mock(SemanticQueryServiceV3.class),
                "mysql",
                registry,
                policy));
    }

    @Test
    @DisplayName("object facade exposes only declared method wrappers at runtime")
    void objectFacadeCapability_declaredMethodOnly() {
        CapabilityRegistry registry = new CapabilityRegistry();
        MethodDescriptor method = new MethodDescriptor(
                "getValue",
                List.of(),
                "string",
                "none",
                "read",
                5000,
                "test.getValue"
        );
        registry.registerObjectFacade(
                new ObjectFacadeDescriptor("biz", List.of(method)),
                new Object() {
                    public String getValue() { return "ok"; }
                    public String hidden() { return "secret"; }
                });
        CapabilityPolicy policy = new CapabilityPolicy(
                Set.of(),
                Map.of("biz", Set.of("getValue")),
                Set.of("read")
        );

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                "return biz.getValue();",
                dummyCtx(),
                mock(SemanticQueryServiceV3.class),
                "mysql",
                registry,
                policy);

        assertEquals("ok", result.value());
        assertThrows(RuntimeException.class, () -> ScriptRuntime.runScript(
                "return biz.hidden();",
                dummyCtx(),
                mock(SemanticQueryServiceV3.class),
                "mysql",
                registry,
                policy));
        assertThrows(RuntimeException.class, () -> ScriptRuntime.runScript(
                "return biz.getClass();",
                dummyCtx(),
                mock(SemanticQueryServiceV3.class),
                "mysql",
                registry,
                policy));
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
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                // Return a dummy SQL when compiled
                return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult(
                        "SELECT amount FROM fake_table", List.of(), null);
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
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
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult(
                        "SELECT amount FROM fake_table", List.of(), null);
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
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
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult(
                        "SELECT amount FROM fake_table", List.of(), null);
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
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
        assertTrue(((com.foggyframework.dataset.model.engine.compose.ComposedSql) summaryPlanSql).getSql().contains("SELECT amount FROM fake_table"));
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
                assertTrue(entry.getValue() instanceof com.foggyframework.dataset.model.engine.compose.ComposedSql,
                        scriptName + " plan '" + entry.getKey() + "' should preview to ComposedSql");
                com.foggyframework.dataset.model.engine.compose.ComposedSql sql =
                        (com.foggyframework.dataset.model.engine.compose.ComposedSql) entry.getValue();
                assertNotNull(sql.getSql(), scriptName + " plan '" + entry.getKey() + "' SQL");
                assertFalse(sql.getSql().isBlank(), scriptName + " plan '" + entry.getKey() + "' SQL");
            }
        }
    }

    @Test
    @DisplayName("dataset.compose_script dsl() maps calculatedFields into SemanticQueryRequest")
    void scriptRuntimeDsl_mapsCalculatedFieldsIntoRequest() {
        AtomicReference<SemanticQueryRequest> captured = new AtomicReference<>();
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, SemanticQueryRequest req, SemanticRequestContext ctx) {
                captured.set(req);
                return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult(
                        "SELECT name FROM employee", List.of(), null);
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, SemanticQueryRequest req, String mode, SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, SemanticQueryRequest req, SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                throw new AssertionError("preview mode must not execute SQL");
            }
        };

        ScriptRuntime.runScript(
                "return dsl({model: 'EmployeeQM', columns: ['name'], "
                        + "calculatedFields: [{name: 'genderCopy', expression: 'gender'}]});",
                dummyCtx(), fakeSvc, "mysql8", true);

        assertNotNull(captured.get());
        assertEquals(1, captured.get().getCalculatedFields().size());
        CalculatedFieldDef cf = captured.get().getCalculatedFields().get(0);
        assertEquals("genderCopy", cf.getName());
        assertEquals("gender", cf.getExpression());
    }

    @Test
    @DisplayName("dataset.compose_script dsl() normalizes orderBy shorthand into SemanticQueryRequest")
    void scriptRuntimeDsl_normalizesOrderByShorthandIntoRequest() {
        AtomicReference<SemanticQueryRequest> captured = new AtomicReference<>();
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, SemanticQueryRequest req, SemanticRequestContext ctx) {
                captured.set(req);
                return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult(
                        "SELECT id, name, date_order FROM employee", List.of(), null);
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, SemanticQueryRequest req, String mode, SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, SemanticQueryRequest req, SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                throw new AssertionError("preview mode must not execute SQL");
            }
        };

        ScriptRuntime.runScript(
                "return dsl({model: 'EmployeeQM', columns: ['id', 'name', 'dateOrder'], "
                        + "orderBy: ['-dateOrder', '+id', 'name', 'dateOrder desc']});",
                dummyCtx(), fakeSvc, "mysql8", true);

        assertNotNull(captured.get());
        assertEquals("dateOrder", captured.get().getOrderBy().get(0).getField());
        assertEquals("desc", captured.get().getOrderBy().get(0).getDir());
        assertEquals("id", captured.get().getOrderBy().get(1).getField());
        assertEquals("asc", captured.get().getOrderBy().get(1).getDir());
        assertEquals("name", captured.get().getOrderBy().get(2).getField());
        assertEquals("asc", captured.get().getOrderBy().get(2).getDir());
        assertEquals("dateOrder", captured.get().getOrderBy().get(3).getField());
        assertEquals("desc", captured.get().getOrderBy().get(3).getDir());
    }

    @Test
    @DisplayName("dsl() maps timeWindow into SemanticQueryRequest")
    void dslFunction_mapsTimeWindowIntoRequest() {
        AtomicReference<com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest> captured =
                new AtomicReference<>();
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                captured.set(req);
                com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse response =
                        new com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse();
                response.setItems(List.of());
                return response;
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                throw new UnsupportedOperationException();
            }
        };

        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("field", "salesDate$id");
        timeWindow.put("grain", "day");
        timeWindow.put("comparison", "rolling_7d");
        timeWindow.put("targetMetrics", List.of("salesAmount"));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", "SalesQM");
        params.put("columns", List.of("salesDate$id", "salesAmount", "salesAmount__rolling_7d"));
        params.put("groupBy", List.of("salesDate$id"));
        params.put("timeWindow", timeWindow);

        DslQueryFunction function = new DslQueryFunction(fakeSvc, SemanticRequestContext.empty());
        function.executeFunction(null, params);

        assertNotNull(captured.get());
        assertEquals(List.of("salesDate$id"), captured.get().getGroupBy().stream()
                .map(com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest.GroupByItem::getField)
                .toList());
        assertNotNull(captured.get().getTimeWindow());
        assertEquals("salesDate$id", captured.get().getTimeWindow().get("field"));
        assertEquals("rolling_7d", captured.get().getTimeWindow().get("comparison"));
        assertEquals(List.of("salesAmount"), captured.get().getTimeWindow().get("targetMetrics"));
    }

    @Test
    @DisplayName("legacy DslQueryFunction maps calculatedFields into SemanticQueryRequest")
    void dslFunction_mapsCalculatedFieldsIntoRequest() {
        AtomicReference<SemanticQueryRequest> captured = new AtomicReference<>();
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, SemanticQueryRequest req, SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, SemanticQueryRequest req, String mode, SemanticRequestContext ctx) {
                captured.set(req);
                com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse response =
                        new com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse();
                response.setItems(List.of());
                return response;
            }

            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, SemanticQueryRequest req, SemanticRequestContext ctx) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params, String routeModel) {
                throw new UnsupportedOperationException();
            }
        };

        Map<String, Object> calc = new LinkedHashMap<>();
        calc.put("name", "genderCopy");
        calc.put("expression", "gender");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", "SalesQM");
        params.put("columns", List.of("name"));
        params.put("calculatedFields", List.of(calc));

        DslQueryFunction function = new DslQueryFunction(fakeSvc, SemanticRequestContext.empty());
        function.executeFunction(null, params);

        assertNotNull(captured.get());
        assertEquals(1, captured.get().getCalculatedFields().size());
        assertEquals("genderCopy", captured.get().getCalculatedFields().get(0).getName());
        assertEquals("gender", captured.get().getCalculatedFields().get(0).getExpression());
    }

    @Test
    @DisplayName("ComposedDataSetResult maps groupBy and timeWindow into SemanticQueryRequest")
    void composedDataSetResult_mapsGroupByAndTimeWindowIntoRequest() throws Exception {
        Map<String, Object> groupByObject = new LinkedHashMap<>();
        groupByObject.put("field", "channel$id");

        Map<String, Object> timeWindow = new LinkedHashMap<>();
        timeWindow.put("field", "salesDate$id");
        timeWindow.put("grain", "day");
        timeWindow.put("comparison", "rolling_7d");
        timeWindow.put("targetMetrics", List.of("salesAmount"));
        Map<String, Object> calc = new LinkedHashMap<>();
        calc.put("name", "growthPercent");
        calc.put("expression", "salesAmount__rolling_7d * 100");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("columns", List.of("salesDate$id", "channel$id", "salesAmount", "salesAmount__rolling_7d"));
        params.put("groupBy", List.of("salesDate$id", groupByObject));
        params.put("timeWindow", timeWindow);
        params.put("calculatedFields", List.of(calc));

        ComposedDataSetResult result = new ComposedDataSetResult(
                mock(SemanticQueryServiceV3.class),
                SemanticRequestContext.empty(),
                null,
                Map.of(),
                Map.of(),
                "inner",
                "id");
        Method buildSemanticRequest = ComposedDataSetResult.class
                .getDeclaredMethod("buildSemanticRequest", Map.class);
        buildSemanticRequest.setAccessible(true);

        SemanticQueryRequest request = (SemanticQueryRequest) buildSemanticRequest.invoke(result, params);

        assertEquals(List.of("salesDate$id", "channel$id"), request.getGroupBy().stream()
                .map(SemanticQueryRequest.GroupByItem::getField)
                .toList());
        assertNotNull(request.getTimeWindow());
        assertEquals("salesDate$id", request.getTimeWindow().get("field"));
        assertEquals("rolling_7d", request.getTimeWindow().get("comparison"));
        assertEquals(List.of("salesAmount"), request.getTimeWindow().get("targetMetrics"));
        assertEquals(1, request.getCalculatedFields().size());
        assertEquals("growthPercent", request.getCalculatedFields().get(0).getName());
        assertEquals("salesAmount__rolling_7d * 100", request.getCalculatedFields().get(0).getExpression());
    }

    @Test
    @DisplayName("e2e: runScript auto-executes QueryPlans inside plans list")
    void endToEnd_fromCall_returnsListAndExecutesPlans() {
        List<Map<String, Object>> expectedRows = List.of(Map.of("amount", 100));
        SemanticQueryServiceV3 fakeSvc = new SemanticQueryServiceV3() {
            @Override
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) { return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult("SELECT 1", java.util.List.of(), null); }
            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) { return null; }
            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) { return null; }
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
            public com.foggyframework.dataset.model.engine.compose.SqlGenerationResult generateSql(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) { return new com.foggyframework.dataset.model.engine.compose.SqlGenerationResult("SELECT 1", java.util.List.of(), null); }
            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse queryModel(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    String mode, com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) { return null; }
            @Override
            public com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse validateQuery(
                    String model, com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest req,
                    com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext ctx) { return null; }
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

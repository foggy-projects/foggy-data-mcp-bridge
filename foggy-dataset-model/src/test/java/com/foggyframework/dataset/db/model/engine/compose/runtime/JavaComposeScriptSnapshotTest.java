package com.foggyframework.dataset.db.model.engine.compose.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilityPolicy;
import com.foggyframework.dataset.db.model.engine.compose.capability.CapabilityRegistry;
import com.foggyframework.dataset.db.model.engine.compose.capability.FunctionDescriptor;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java-side producer for Python P0-4 compose-script runtime/tool replay.
 */
@DisplayName("JavaComposeScriptSnapshotTest · Python alignment P0-4")
class JavaComposeScriptSnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    @DisplayName("writes java_compose_script_snapshot_parity.json for Python replay")
    void shouldProduceComposeScriptSnapshot() throws Exception {
        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "scriptRuntimeTool");
        snapshot.put("source", "JavaComposeScriptSnapshotTest");
        snapshot.put("tool", toolSnapshot());
        snapshot.put("runtime", runtimeSnapshot());
        snapshot.put("cases", cases());

        for (Map<String, Object> c : cases()) {
            assertJavaRuntimeContract(c);
        }

        Path pythonTarget = Path.of(
                "..",
                "..",
                "foggy-data-mcp-bridge-python",
                "tests",
                "fixtures",
                "java_compose_script_snapshot_parity.json"
        ).normalize();
        Files.createDirectories(pythonTarget.getParent());
        MAPPER.writeValue(pythonTarget.toFile(), snapshot);

        Path localCopy = Path.of("target", "parity", "java_compose_script_snapshot_parity.json");
        Files.createDirectories(localCopy.getParent());
        MAPPER.writeValue(localCopy.toFile(), snapshot);
        assertTrue(Files.exists(pythonTarget),
                "snapshot was not written: " + pythonTarget.toAbsolutePath());
    }

    private static Map<String, Object> toolSnapshot() throws Exception {
        Path schemaPath = Path.of(
                "..",
                "foggy-dataset-mcp",
                "src",
                "main",
                "resources",
                "schemas",
                "compose_query_schema.json"
        );
        JsonNode schema = MAPPER.readTree(schemaPath.toFile());
        Path descriptionPath = Path.of(
                "..",
                "foggy-dataset-mcp",
                "src",
                "main",
                "resources",
                "schemas",
                "descriptions",
                "compose_script_m2.md"
        );
        String description = Files.readString(descriptionPath);

        assertEquals("object", schema.get("type").asText());
        assertTrue(schema.get("required").toString().contains("script"));
        assertTrue(description.contains("dataset.compose_script"));
        assertFalse(description.contains("Query.from"));

        Map<String, Object> tool = ordered();
        tool.put("name", "dataset.compose_script");
        tool.put("schemaFile", "compose_query_schema.json");
        tool.put("descriptionFile", "compose_script_m2.md");
        tool.put("required", List.of("script"));
        tool.put("schemaMarkers", List.of(
                "SemanticDSL compose script",
                "single-model pivot",
                "baselineRatio",
                "return { plans:"
        ));
        tool.put("descriptionMarkers", List.of(
                "dataset.compose_script",
                "跨模型 Join / Union",
                "dataset.query_model.payload.pivot",
                "timeRole=business_date",
                "不要直接 `.execute()`"
        ));
        tool.put("forbiddenMarkers", List.of(
                "Query.from",
                "DataSetResult",
                "ComposedDataSetResult",
                "toList",
                "withJoin",
                "joinInMemory"
        ));
        return tool;
    }

    private static Map<String, Object> runtimeSnapshot() {
        Map<String, Object> runtime = ordered();
        runtime.put("allowedScriptGlobals", orderedAllowedScriptGlobals());
        runtime.put("acceptedPythonExtraGlobals", List.of(
                "JSON", "parseInt", "parseFloat", "toString",
                "String", "Number", "Boolean", "isNaN", "isFinite",
                "Array", "Object", "Function", "typeof", "params"
        ));
        return runtime;
    }

    private static List<String> orderedAllowedScriptGlobals() {
        List<String> globals = List.of("from", "subquery", "Query", "dsl");
        assertEquals(Set.copyOf(globals), ScriptRuntime.ALLOWED_SCRIPT_GLOBALS);
        return globals;
    }

    private static List<Map<String, Object>> cases() {
        return List.of(
                caseDef(
                        "literal-return",
                        "return 42;",
                        false,
                        expected("number", 42, false, List.of(), List.of(), null)
                ),
                caseDef(
                        "empty-plans-envelope",
                        "return { plans: [] };",
                        false,
                        expected("map", null, false, List.of(), List.of(), null)
                ),
                caseDef(
                        "preview-base-plan-sql",
                        """
                                return { plans: dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderStatus$caption"]
                                }) };
                                """,
                        true,
                        expected("map", null, true, List.of("SELECT 'FactSalesModel' AS __model__"), List.of(), null)
                ),
                caseDef(
                        "execute-base-plan-rows-envelope",
                        """
                                return { plans: dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderStatus$caption"]
                                }) };
                                """,
                        false,
                        expectedRowsEnvelope(List.of(row("routeModel", "FactSalesModel", "stub", 1)))
                ),
                caseDef(
                        "preview-derived-plan-sql",
                        """
                                const base = dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderStatus$caption", "salesAmount"],
                                  groupBy: ["orderStatus$caption"]
                                });
                                return { plans: dsl({
                                  source: base,
                                  columns: ["orderStatus$caption", "salesAmount"],
                                  slice: [{ field: "salesAmount", op: ">", value: 1000 }]
                                }) };
                                """,
                        true,
                        expected("map", null, true,
                                List.of("SELECT 'FactSalesModel' AS __model__", "FROM (", "salesAmount"),
                                List.of(), null)
                ),
                caseDef(
                        "execute-derived-plan-rows-envelope",
                        """
                                const base = dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderStatus$caption", "salesAmount"],
                                  groupBy: ["orderStatus$caption"]
                                });
                                return { plans: dsl({
                                  source: base,
                                  columns: ["orderStatus$caption", "salesAmount"],
                                  slice: [{ field: "salesAmount", op: ">", value: 1000 }]
                                }) };
                                """,
                        false,
                        expectedRowsEnvelope(List.of(row("routeModel", "FactSalesModel", "stub", 1)))
                ),
                caseDef(
                        "preview-union-plan-sql",
                        """
                                const current = dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderStatus$caption"]
                                });
                                const archived = dsl({
                                  model: "ArchivedSalesModel",
                                  columns: ["orderStatus$caption"]
                                });
                                return { plans: current.union(archived, { all: true }) };
                                """,
                        true,
                        expected("map", null, true,
                                List.of("SELECT 'FactSalesModel' AS __model__",
                                        "SELECT 'ArchivedSalesModel' AS __model__",
                                        "UNION ALL"),
                                List.of(), null)
                ),
                caseDef(
                        "execute-union-plan-rows-envelope",
                        """
                                const current = dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderStatus$caption"]
                                });
                                const archived = dsl({
                                  model: "ArchivedSalesModel",
                                  columns: ["orderStatus$caption"]
                                });
                                return { plans: current.union(archived, { all: true }) };
                                """,
                        false,
                        expectedRowsEnvelope(List.of(row("routeModel", "FactSalesModel", "stub", 1)))
                ),
                caseDef(
                        "preview-join-plan-sql",
                        """
                                const sales = dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderId", "salesAmount"]
                                });
                                const returns = dsl({
                                  model: "FactReturnModel",
                                  columns: ["orderId", "returnAmount"]
                                });
                                return { plans: sales.join(returns, "left", [
                                  { left: "orderId", op: "=", right: "orderId" }
                                ]) };
                                """,
                        true,
                        expected("map", null, true,
                                List.of("SELECT 'FactSalesModel' AS __model__",
                                        "SELECT 'FactReturnModel' AS __model__",
                                        "LEFT JOIN",
                                        "orderId"),
                                List.of(), null)
                ),
                caseDef(
                        "execute-join-plan-rows-envelope",
                        """
                                const sales = dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderId", "salesAmount"]
                                });
                                const returns = dsl({
                                  model: "FactReturnModel",
                                  columns: ["orderId", "returnAmount"]
                                });
                                return { plans: sales.join(returns, "left", [
                                  { left: "orderId", op: "=", right: "orderId" }
                                ]) };
                                """,
                        false,
                        expectedRowsEnvelope(List.of(row("routeModel", "FactSalesModel", "stub", 1)))
                ),
                caseDef(
                        "security-param-denied",
                        """
                                return dsl({
                                  model: "FactSalesModel",
                                  columns: ["orderStatus$caption"],
                                  systemSlice: [{ field: "tenant_id", op: "=", value: "demo" }]
                                });
                        """,
                        false,
                        expected("error", null, false, List.of(), List.of(), "Security parameters")
                ),
                caseDef(
                        "capability-pure-runtime-policy-allow",
                        "return fiscalYear(4);",
                        false,
                        "fiscal-year-allow",
                        expected("number", 2025, false, List.of(), List.of(), null)
                ),
                caseDef(
                        "capability-pure-runtime-policy-deny",
                        "return fiscalYear(4);",
                        false,
                        "fiscal-year-deny",
                        expected("error", null, false, List.of(), List.of(), "fiscalYear")
                )
        );
    }

    private static Map<String, Object> row(String key1, Object value1, String key2, Object value2) {
        Map<String, Object> row = ordered();
        row.put(key1, value1);
        row.put(key2, value2);
        return row;
    }

    private static Map<String, Object> caseDef(String id, String script, boolean previewMode,
                                               Map<String, Object> expected) {
        Map<String, Object> c = ordered();
        c.put("id", id);
        c.put("dialect", "mysql");
        c.put("previewMode", previewMode);
        c.put("script", script);
        c.put("expected", expected);
        return c;
    }

    private static Map<String, Object> caseDef(String id, String script, boolean previewMode,
                                               String capabilityScenario,
                                               Map<String, Object> expected) {
        Map<String, Object> c = caseDef(id, script, previewMode, expected);
        c.put("capabilityScenario", capabilityScenario);
        return c;
    }

    private static Map<String, Object> expectedRowsEnvelope(List<Map<String, Object>> rows) {
        Map<String, Object> e = expected("map", null, false, List.of(), List.of(), null);
        e.put("hasRows", true);
        e.put("rows", rows);
        return e;
    }

    private static Map<String, Object> expected(String valueType, Object value,
                                                boolean hasSql, List<String> sqlMarkers,
                                                List<Object> params, String errorMarker) {
        Map<String, Object> e = ordered();
        e.put("valueType", valueType);
        if (value != null) {
            e.put("value", value);
        }
        e.put("hasSql", hasSql);
        e.put("sqlMarkers", sqlMarkers);
        e.put("params", params);
        if (errorMarker != null) {
            e.put("errorMarker", errorMarker);
        }
        return e;
    }

    private static void assertJavaRuntimeContract(Map<String, Object> c) {
        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        RuntimeInputs inputs = runtimeInputs(c);
        String errorMarker = (String) expected.get("errorMarker");
        if (errorMarker != null) {
            try {
                ScriptRuntime.runScript(
                        (String) c.get("script"),
                        dummyCtx(),
                        previewOnlySemanticService(),
                        (String) c.get("dialect"),
                        Boolean.TRUE.equals(c.get("previewMode")),
                        inputs.registry(),
                        inputs.policy()
                );
                throw new AssertionError("Expected Java script case to fail: " + c.get("id"));
            } catch (RuntimeException ex) {
                assertTrue(ex.getMessage().contains(errorMarker),
                        "Expected error marker '" + errorMarker + "' in: " + ex.getMessage());
            }
            return;
        }

        ScriptRuntime.ScriptResult result = ScriptRuntime.runScript(
                (String) c.get("script"),
                dummyCtx(),
                previewOnlySemanticService(),
                (String) c.get("dialect"),
                Boolean.TRUE.equals(c.get("previewMode")),
                inputs.registry(),
                inputs.policy()
        );
        String valueType = (String) expected.get("valueType");
        if ("number".equals(valueType)) {
            assertInstanceOf(Number.class, result.value());
            assertEquals(((Number) expected.get("value")).intValue(),
                    ((Number) result.value()).intValue());
        } else if ("map".equals(valueType)) {
            assertInstanceOf(Map.class, result.value());
        }

        if (Boolean.TRUE.equals(expected.get("hasSql"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) result.value();
            Object plans = value.get("plans");
            assertInstanceOf(ComposedSql.class, plans);
            String sql = ((ComposedSql) plans).getSql();
            @SuppressWarnings("unchecked")
            List<String> markers = (List<String>) expected.get("sqlMarkers");
            for (String marker : markers) {
                assertTrue(sql.contains(marker), "SQL marker missing: " + marker + "\n" + sql);
            }
        }

        if (Boolean.TRUE.equals(expected.get("hasRows"))) {
            @SuppressWarnings("unchecked")
            Map<String, Object> value = (Map<String, Object>) result.value();
            Object plans = value.get("plans");
            assertInstanceOf(List.class, plans);
            assertEquals(expected.get("rows"), plans);
        }
    }

    private static RuntimeInputs runtimeInputs(Map<String, Object> c) {
        Object scenario = c.get("capabilityScenario");
        if ("fiscal-year-allow".equals(scenario)) {
            return fiscalYearInputs(true);
        }
        if ("fiscal-year-deny".equals(scenario)) {
            return fiscalYearInputs(false);
        }
        return new RuntimeInputs(null, null);
    }

    private static RuntimeInputs fiscalYearInputs(boolean allow) {
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
        CapabilityPolicy policy = allow
                ? new CapabilityPolicy(Set.of("fiscalYear"), Map.of(), Set.of())
                : CapabilityPolicy.empty();
        return new RuntimeInputs(registry, policy);
    }

    private record RuntimeInputs(CapabilityRegistry registry, CapabilityPolicy policy) {}

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
                        .userId("snapshot-user")
                        .tenantId("demo")
                        .roles(List.of("analyst"))
                        .build())
                .namespace("demo")
                .traceId("java-compose-script-snapshot")
                .authorityResolver(resolver)
                .build();
    }

    private static SemanticQueryServiceV3 previewOnlySemanticService() {
        return new SemanticQueryServiceV3() {
            @Override
            public SqlGenerationResult generateSql(String model, SemanticQueryRequest req,
                                                   SemanticRequestContext ctx) {
                return new SqlGenerationResult("SELECT '" + model + "' AS __model__", List.of(), null, List.of());
            }

            @Override
            public SemanticQueryResponse queryModel(String model, SemanticQueryRequest req,
                                                    String mode, SemanticRequestContext ctx) {
                throw new UnsupportedOperationException("queryModel is not used by script snapshots");
            }

            @Override
            public SemanticQueryResponse validateQuery(String model, SemanticQueryRequest req,
                                                       SemanticRequestContext ctx) {
                throw new UnsupportedOperationException("validateQuery is not used by script snapshots");
            }

            @Override
            public List<Map<String, Object>> executeSql(String sql, List<Object> params,
                                                        String routeModel) {
                return List.of(row("routeModel", routeModel, "stub", 1));
            }
        };
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }
}

package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.compilation.CompileTestHelpers.FakeSemanticService;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.plan.*;
import com.foggyframework.dataset.db.model.engine.compose.relation.*;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7c · {@link ComposeRelationCompiler#compileToRelation} unit tests.
 *
 * <p>Pure model-level tests — no Spring context, no live DB. Uses the same
 * {@link FakeSemanticService} and {@link CompileTestHelpers} as the M6
 * compile tests.</p>
 */
@DisplayName("ComposeRelationCompilerTest · S7c")
class ComposeRelationCompilerTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private FakeSemanticService svc() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT storeName, SUM(amount) AS salesAmount FROM fact_sales GROUP BY storeName");
        return svc;
    }

    private ComposeQueryContext ctx(Map<String, ModelBinding> bindings) {
        return CompileTestHelpers.context(CompileTestHelpers.resolverFor(bindings));
    }

    private Map<String, ModelBinding> bindings() {
        return Map.of("M", CompileTestHelpers.emptyBinding());
    }

    private BaseModelPlan basePlan() {
        return CompileTestHelpers.base("M", "storeName", "salesAmount");
    }

    private RelationCompileOptions.Builder opts(FakeSemanticService svc, String dialect) {
        return RelationCompileOptions.builder()
                .semanticService(svc)
                .bindings(bindings())
                .dialect(dialect);
    }

    // ------------------------------------------------------------------
    // 1. Base plan → CompiledRelation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("basePlan_compilesToRelation · base plan produces valid CompiledRelation")
    void basePlan_compilesToRelation() {
        FakeSemanticService svc = svc();
        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, "mysql8").build());

        assertNotNull(rel);
        assertEquals("rel_0", rel.alias());
        assertEquals("mysql8", rel.dialect());
        assertNotNull(rel.relationSql());
        assertNotNull(rel.outputSchema());
        assertNotNull(rel.capabilities());
        assertFalse(rel.relationSql().bodySql().isEmpty());
    }

    // ------------------------------------------------------------------
    // 2. Derived plan → CompiledRelation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("derivedPlan_compilesToRelation · derived wrapping produces valid relation")
    void derivedPlan_compilesToRelation() {
        FakeSemanticService svc = svc();
        DerivedQueryPlan derived = DerivedQueryPlan.builder()
                .source(basePlan())
                .columns(List.of("storeName", "salesAmount"))
                .build();

        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                derived,
                ctx(bindings()),
                opts(svc, "postgres").build());

        assertNotNull(rel);
        assertEquals("rel_0", rel.alias());
        assertEquals("postgres", rel.dialect());
        assertEquals(2, rel.outputSchema().size());
    }

    // ------------------------------------------------------------------
    // 3–5. TimeWindow plans with enriched OutputSchema
    // ------------------------------------------------------------------

    @Test
    @DisplayName("timeWindowYoy_compilesToRelation · comparative timeWindow enriches schema")
    void timeWindowYoy_compilesToRelation() {
        FakeSemanticService svc = svc();

        Map<String, Object> twMap = new LinkedHashMap<>();
        twMap.put("field", "salesDate$id");
        twMap.put("comparison", "yoy");
        twMap.put("grain", "month");
        twMap.put("range", "[)");
        twMap.put("value", List.of("2024-01-01", "2025-01-01"));
        twMap.put("targetMetrics", List.of("salesAmount"));
        TimeWindowDef twDef = TimeWindowDef.fromMap(twMap);

        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, "mysql8")
                        .timeWindowDef(twDef)
                        .dimensionFields(List.of("storeName"))
                        .measureFields(Set.of("salesAmount"))
                        .build());

        assertNotNull(rel);
        OutputSchema schema = rel.outputSchema();
        // YoY enriched schema should contain: storeName, salesAmount,
        // salesAmount__prior, salesAmount__diff, salesAmount__ratio,
        // salesDate$year, salesDate$month
        assertTrue(schema.contains("storeName"));
        assertTrue(schema.contains("salesAmount"));
        assertTrue(schema.contains("salesAmount__prior"));
        assertTrue(schema.contains("salesAmount__diff"));
        assertTrue(schema.contains("salesAmount__ratio"));
    }

    @Test
    @DisplayName("timeWindowRolling_compilesToRelation · rolling timeWindow produces derived columns")
    void timeWindowRolling_compilesToRelation() {
        FakeSemanticService svc = svc();

        Map<String, Object> twMap = new LinkedHashMap<>();
        twMap.put("field", "salesDate$id");
        twMap.put("comparison", "rolling_7d");
        twMap.put("grain", "day");
        twMap.put("value", List.of("2024-01-01", "2025-01-01"));
        twMap.put("targetMetrics", List.of("salesAmount"));
        TimeWindowDef twDef = TimeWindowDef.fromMap(twMap);

        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, "mysql8")
                        .timeWindowDef(twDef)
                        .dimensionFields(List.of("storeName"))
                        .measureFields(Set.of("salesAmount"))
                        .build());

        assertNotNull(rel);
        assertTrue(rel.outputSchema().contains("salesAmount__rolling_7d"));
    }

    @Test
    @DisplayName("timeWindowCumulative_compilesToRelation · cumulative timeWindow works")
    void timeWindowCumulative_compilesToRelation() {
        FakeSemanticService svc = svc();

        Map<String, Object> twMap = new LinkedHashMap<>();
        twMap.put("field", "salesDate$id");
        twMap.put("comparison", "ytd");
        twMap.put("grain", "day");
        twMap.put("value", List.of("2024-01-01", "2025-01-01"));
        twMap.put("targetMetrics", List.of("salesAmount"));
        TimeWindowDef twDef = TimeWindowDef.fromMap(twMap);

        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, "sqlite")
                        .timeWindowDef(twDef)
                        .dimensionFields(List.of("storeName"))
                        .measureFields(Set.of("salesAmount"))
                        .build());

        assertNotNull(rel);
        assertTrue(rel.outputSchema().contains("salesAmount__ytd"));
    }

    // ------------------------------------------------------------------
    // 6. Params flatten in stable order
    // ------------------------------------------------------------------

    @Test
    @DisplayName("paramsFlatten_stableOrder · params propagated from ComposedSql")
    void paramsFlatten_stableOrder() {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "SELECT storeName FROM fact_sales WHERE region = ?",
                "EAST");

        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, "sqlite").build());

        assertNotNull(rel.params());
        assertTrue(rel.params().contains("EAST"),
                "params must contain the v1.3 query param");
    }

    // ------------------------------------------------------------------
    // 7. SQL Server hoisted CTE, no FROM (WITH
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"mssql", "sqlserver"})
    @DisplayName("sqlServer_noFromWith · SQL Server SQL never contains FROM (WITH")
    void sqlServer_noFromWith(String dialect) {
        FakeSemanticService svc = svc();
        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, dialect).build());

        assertNotNull(rel);
        String sql = rel.relationSql().bodySql();
        assertFalse(ComposeRelationCompiler.containsFromWith(sql),
                "SQL Server SQL must NEVER contain FROM (WITH; got: " + sql);
    }

    // ------------------------------------------------------------------
    // 8. MySQL 5.7 + CTE → fail-closed
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mysql57"})
    @DisplayName("mysql57_withoutCte_succeeds · MySQL 5.7 allows simple relation SQL")
    void mysql57_withoutCte_succeeds(String dialect) {
        // Simple base plan → no CTE → should succeed on MySQL 5.7
        FakeSemanticService svc = svc();
        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, dialect).build());
        assertNotNull(rel, "Simple base plan should succeed on MySQL 5.7");
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mysql57"})
    @DisplayName("mysql57_withCte_failsClosed · MySQL 5.7 + CTE plan rejects")
    void mysql57_withCte_failsClosed(String dialect) {
        FakeSemanticService svc = new FakeSemanticService();
        svc.stub("M", "WITH cte_0 AS (SELECT storeName FROM fact_sales) "
                + "SELECT storeName FROM cte_0");

        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> ComposeRelationCompiler.compileToRelation(
                        basePlan(),
                        ctx(bindings()),
                        opts(svc, dialect).build()));

        assertEquals(ComposeCompileErrorCodes.RELATION_WRAP_UNSUPPORTED,
                ex.code());
        assertEquals(ComposeCompileErrorCodes.PHASE_RELATION_COMPILE,
                ex.phase());
    }

    // ------------------------------------------------------------------
    // 9–10. supportsOuterAggregate opened / supportsOuterWindow false
    // ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"mysql8", "postgres", "sqlite", "mssql", "mysql", "mysql57", "sqlserver"})
    @DisplayName("supportsOuterAggregate_true · S7e opens wrappable relation aggregate")
    void supportsOuterAggregate_true(String dialect) {
        FakeSemanticService svc = svc();
        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, dialect).build());

        assertTrue(rel.capabilities().supportsOuterAggregate(),
                "S7e must open outer aggregate for wrappable relation on " + dialect);
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql8", "postgres", "sqlite", "mssql", "mysql", "mysql57", "sqlserver"})
    @DisplayName("supportsOuterWindow_alwaysFalse · S7f not opened")
    void supportsOuterWindow_alwaysFalse(String dialect) {
        FakeSemanticService svc = svc();
        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, dialect).build());

        assertFalse(rel.capabilities().supportsOuterWindow(),
                "S7f must not open outer window for " + dialect);
    }

    // ------------------------------------------------------------------
    // 11. Datasource ID carried through
    // ------------------------------------------------------------------

    @Test
    @DisplayName("datasourceId_carriedThrough · datasource propagated to relation")
    void datasourceId_carriedThrough() {
        FakeSemanticService svc = svc();
        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, "mysql8")
                        .datasourceIds(Map.of("M", Optional.of("demo-ds")))
                        .build());

        assertEquals("demo-ds", rel.datasourceId());
    }

    // ------------------------------------------------------------------
    // 12. Permission state defaults to UNKNOWN
    // ------------------------------------------------------------------

    @Test
    @DisplayName("permissionState_defaultsToUnknown · default permission state")
    void permissionState_defaultsToUnknown() {
        FakeSemanticService svc = svc();
        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, "mysql8").build());

        assertEquals(RelationPermissionState.UNKNOWN, rel.permissionState());
    }

    // ------------------------------------------------------------------
    // 13. Stable alias follows convention
    // ------------------------------------------------------------------

    @Test
    @DisplayName("stableAlias_noCollision · alias follows rel_N convention")
    void stableAlias_noCollision() {
        FakeSemanticService svc = svc();
        CompiledRelation rel = ComposeRelationCompiler.compileToRelation(
                basePlan(),
                ctx(bindings()),
                opts(svc, "mysql8")
                        .relationAlias("my_view")
                        .build());

        assertEquals("my_view", rel.alias());
        assertEquals("my_view", rel.relationSql().preferredAlias());
    }

    // ------------------------------------------------------------------
    // 14. FROM (WITH detection
    // ------------------------------------------------------------------

    @Test
    @DisplayName("containsFromWith_detection · detects forbidden pattern")
    void containsFromWith_detection() {
        assertTrue(ComposeRelationCompiler.containsFromWith(
                "SELECT * FROM (WITH cte AS (SELECT 1))"));
        assertTrue(ComposeRelationCompiler.containsFromWith(
                "SELECT * FROM ( WITH cte AS (SELECT 1))"));
        assertTrue(ComposeRelationCompiler.containsFromWith(
                "SELECT * from (with cte AS (SELECT 1))"));
        assertFalse(ComposeRelationCompiler.containsFromWith(
                "WITH cte AS (SELECT 1) SELECT * FROM cte"));
        assertFalse(ComposeRelationCompiler.containsFromWith(
                "SELECT * FROM t0 WHERE x = 1"));
    }

    // ------------------------------------------------------------------
    // Additional: CTE detection
    // ------------------------------------------------------------------

    @Test
    @DisplayName("detectCtePresence · detects WITH prefix")
    void detectCtePresence() {
        assertTrue(ComposeRelationCompiler.detectCtePresence(
                "WITH cte_0 AS (SELECT 1) SELECT * FROM cte_0"));
        assertTrue(ComposeRelationCompiler.detectCtePresence(
                "  WITH cte_0 AS (SELECT 1) SELECT * FROM cte_0"));
        assertTrue(ComposeRelationCompiler.detectCtePresence(
                ";WITH cte_0 AS (SELECT 1) SELECT * FROM cte_0"));
        assertFalse(ComposeRelationCompiler.detectCtePresence(
                "SELECT * FROM (SELECT 1) AS t0"));
        assertFalse(ComposeRelationCompiler.detectCtePresence(""));
        assertFalse(ComposeRelationCompiler.detectCtePresence(null));
    }

    // ------------------------------------------------------------------
    // Input validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nullPlan_rejected · plan must not be null")
    void nullPlan_rejected() {
        FakeSemanticService svc = svc();
        assertThrows(IllegalArgumentException.class,
                () -> ComposeRelationCompiler.compileToRelation(
                        null, ctx(bindings()), opts(svc, "mysql8").build()));
    }

    @Test
    @DisplayName("nullOpts_rejected · opts must not be null")
    void nullOpts_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ComposeRelationCompiler.compileToRelation(
                        basePlan(), ctx(bindings()), null));
    }

    @Test
    @DisplayName("nullSemanticService_rejected · semanticService is required")
    void nullSemanticService_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ComposeRelationCompiler.compileToRelation(
                        basePlan(), ctx(bindings()),
                        RelationCompileOptions.builder()
                                .bindings(bindings())
                                .dialect("mysql8")
                                .build()));
    }
}

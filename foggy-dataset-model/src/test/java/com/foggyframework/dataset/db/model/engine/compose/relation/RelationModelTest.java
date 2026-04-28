package com.foggyframework.dataset.db.model.engine.compose.relation;

import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7a POC · Relation object model invariants.
 */
@DisplayName("RelationModelTest · S7a POC")
class RelationModelTest {

    private static OutputSchema sampleSchema() {
        return OutputSchema.of(List.of(
                ColumnSpec.builder().name("storeName").expression("storeName")
                        .semanticKind(SemanticKind.BASE_FIELD)
                        .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT).build(),
                ColumnSpec.builder().name("salesAmount").expression("salesAmount")
                        .semanticKind(SemanticKind.AGGREGATE_MEASURE)
                        .referencePolicy(ReferencePolicy.MEASURE_DEFAULT).build()
        ));
    }

    @Test
    @DisplayName("CompiledRelation builder produces valid instance")
    void compiledRelationBuilder() {
        RelationSql rSql = RelationSql.builder()
                .bodySql("SELECT storeName, SUM(amount) AS salesAmount FROM fact_sales GROUP BY storeName")
                .bodyParams(List.of())
                .preferredAlias("rel_0")
                .build();

        CompiledRelation rel = CompiledRelation.builder()
                .alias("rel_0")
                .relationSql(rSql)
                .outputSchema(sampleSchema())
                .datasourceId("demo")
                .dialect("mysql8")
                .capabilities(RelationCapabilities.forDialect("mysql8", false))
                .permissionState(RelationPermissionState.UNKNOWN)
                .build();

        assertEquals("rel_0", rel.alias());
        assertEquals("demo", rel.datasourceId());
        assertEquals("mysql8", rel.dialect());
        assertEquals(RelationPermissionState.UNKNOWN, rel.permissionState());
        assertEquals(2, rel.outputSchema().size());
        assertNotNull(rel.capabilities());
    }

    @Test
    @DisplayName("flattenParams follows withItems + bodyParams order")
    void flattenParamsOrder() {
        CteItem cte0 = CteItem.builder()
                .name("__rel0_tw_base")
                .sql("SELECT * FROM fact_sales WHERE d >= ?")
                .params(List.of("2024-01-01"))
                .build();
        CteItem cte1 = CteItem.builder()
                .name("__rel0_tw_prior")
                .sql("SELECT * FROM fact_sales WHERE d >= ?")
                .params(List.of("2023-01-01"))
                .build();
        RelationSql rSql = RelationSql.builder()
                .withItems(List.of(cte0, cte1))
                .bodySql("SELECT * FROM __rel0_tw_base JOIN __rel0_tw_prior ON ...")
                .bodyParams(List.of("body_param"))
                .preferredAlias("rel_0")
                .build();

        List<Object> flat = rSql.flattenParams();
        assertEquals(List.of("2024-01-01", "2023-01-01", "body_param"), flat);
    }

    @Test
    @DisplayName("containsWithItems correctly reflects CTE presence")
    void containsWithItems() {
        RelationSql noCte = RelationSql.builder()
                .bodySql("SELECT 1")
                .preferredAlias("rel_0")
                .build();
        assertFalse(noCte.containsWithItems());

        CteItem item = CteItem.builder()
                .name("cte_0").sql("SELECT 1").build();
        RelationSql withCte = RelationSql.builder()
                .withItems(List.of(item))
                .bodySql("SELECT * FROM cte_0")
                .preferredAlias("rel_0")
                .build();
        assertTrue(withCte.containsWithItems());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql8", "postgres", "sqlite"})
    @DisplayName("CTE-capable dialects with CTE items → hoisted_cte")
    void cteCapableDialectsWithCte(String dialect) {
        RelationCapabilities caps = RelationCapabilities.forDialect(dialect, true);
        assertEquals(RelationWrapStrategy.HOISTED_CTE, caps.relationWrapStrategy());
        assertTrue(caps.canHoistCte());
        assertTrue(caps.containsWithItems());
        assertFalse(caps.canInlineAsSubquery());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql8", "postgres", "sqlite", "mssql", "sqlserver", "mysql", "mysql57"})
    @DisplayName("no CTE items → inline_subquery for all dialects")
    void noCteAlwaysInline(String dialect) {
        RelationCapabilities caps = RelationCapabilities.forDialect(dialect, false);
        assertEquals(RelationWrapStrategy.INLINE_SUBQUERY, caps.relationWrapStrategy());
        assertTrue(caps.canInlineAsSubquery());
        assertFalse(caps.containsWithItems());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mssql", "sqlserver"})
    @DisplayName("SQL Server with CTE items → hoisted_cte, requiresTopLevelWith")
    void sqlServerWithCte(String dialect) {
        RelationCapabilities caps = RelationCapabilities.forDialect(dialect, true);
        assertEquals(RelationWrapStrategy.HOISTED_CTE, caps.relationWrapStrategy());
        assertTrue(caps.requiresTopLevelWith());
        assertTrue(caps.canHoistCte());
        assertFalse(caps.canInlineAsSubquery());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mysql", "mysql57"})
    @DisplayName("MySQL 5.7 with CTE items → fail_closed")
    void mysql57WithCte(String dialect) {
        RelationCapabilities caps = RelationCapabilities.forDialect(dialect, true);
        assertEquals(RelationWrapStrategy.FAIL_CLOSED, caps.relationWrapStrategy());
        assertFalse(caps.canHoistCte());
        assertFalse(caps.canInlineAsSubquery());
    }

    @Test
    @DisplayName("S7a: supportsOuterAggregate and supportsOuterWindow are always false")
    void outerCapabilitiesNotOpened() {
        for (String d : List.of("mysql8", "postgres", "sqlite", "mssql", "mysql")) {
            for (boolean hasCte : List.of(true, false)) {
                RelationCapabilities caps = RelationCapabilities.forDialect(d, hasCte);
                assertFalse(caps.supportsOuterAggregate(),
                        "S7a must not open outer aggregate for " + d);
                assertFalse(caps.supportsOuterWindow(),
                        "S7a must not open outer window for " + d);
            }
        }
    }

    @Test
    @DisplayName("CteItem rejects empty name or sql")
    void cteItemValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                CteItem.builder().name("").sql("SELECT 1").build());
        assertThrows(IllegalArgumentException.class, () ->
                CteItem.builder().name("x").sql("").build());
    }

    @Test
    @DisplayName("CompiledRelation rejects null required fields")
    void compiledRelationValidation() {
        RelationSql rSql = RelationSql.builder()
                .bodySql("SELECT 1").preferredAlias("rel_0").build();
        assertThrows(IllegalArgumentException.class, () ->
                CompiledRelation.builder()
                        .alias("").relationSql(rSql)
                        .outputSchema(sampleSchema())
                        .dialect("mysql8")
                        .capabilities(RelationCapabilities.forDialect("mysql8", false))
                        .build());
    }

    @Test
    @DisplayName("SemanticKind constants are valid")
    void semanticKindConstants() {
        assertEquals(5, SemanticKind.ALL.size());
        assertTrue(SemanticKind.isValid(SemanticKind.BASE_FIELD));
        assertFalse(SemanticKind.isValid("unknown_kind"));
    }

    @Test
    @DisplayName("ReferencePolicy constants are valid")
    void referencePolicyConstants() {
        assertEquals(5, ReferencePolicy.ALL.size());
        assertTrue(ReferencePolicy.isValid(ReferencePolicy.READABLE));
    }

    @Test
    @DisplayName("RelationWrapStrategy constants are valid")
    void wrapStrategyConstants() {
        assertEquals(4, RelationWrapStrategy.ALL.size());
        assertTrue(RelationWrapStrategy.isValid(RelationWrapStrategy.HOISTED_CTE));
    }
}

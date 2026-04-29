package com.foggyframework.dataset.db.model.engine.compose.compilation;

import com.foggyframework.dataset.db.model.engine.compose.relation.*;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7d · {@link RelationOuterQueryBuilder#buildOuterQuery} unit tests.
 *
 * <p>Tests build pre-compiled {@link CompiledRelation} fixtures directly
 * (no real SQL compilation) to validate outer query wrapping, column
 * validation, referencePolicy enforcement, and SQL generation.</p>
 */
@DisplayName("RelationOuterQueryBuilderTest · S7d")
class RelationOuterQueryBuilderTest {

    // ------------------------------------------------------------------
    // Fixtures — build CompiledRelation directly without compilation
    // ------------------------------------------------------------------

    /**
     * Build a CompiledRelation with enriched OutputSchema (referencePolicy set).
     * Columns:
     *   storeName  — readable, groupable, orderable (dimension)
     *   salesAmount — readable, orderable (measure)
     *   internalNote — NOT readable (empty referencePolicy)
     */
    private CompiledRelation enrichedRelation(String dialect) {
        return enrichedRelation(dialect, false);
    }

    private CompiledRelation enrichedRelation(String dialect, boolean hasCte) {
        ColumnSpec storeName = ColumnSpec.builder()
                .name("storeName").expression("storeName")
                .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                .build();
        ColumnSpec salesAmount = ColumnSpec.builder()
                .name("salesAmount").expression("salesAmount")
                .referencePolicy(ReferencePolicy.MEASURE_DEFAULT)
                .build();
        ColumnSpec internalNote = ColumnSpec.builder()
                .name("internalNote").expression("internalNote")
                .referencePolicy(Set.of())  // explicitly NOT readable
                .build();

        OutputSchema schema = OutputSchema.of(List.of(
                storeName, salesAmount, internalNote));

        String bodySql = hasCte
                ? "SELECT storeName, salesAmount, internalNote FROM fact_sales"
                : "SELECT storeName, salesAmount, internalNote FROM fact_sales";

        RelationCapabilities caps = RelationCapabilities.forDialect(dialect, hasCte);

        return CompiledRelation.builder()
                .alias("rel_0")
                .relationSql(RelationSql.builder()
                        .bodySql(bodySql)
                        .bodyParams(List.of("p1", "p2"))
                        .preferredAlias("rel_0")
                        .build())
                .params(List.of("p1", "p2"))
                .outputSchema(schema)
                .datasourceId("demo-ds")
                .dialect(dialect)
                .capabilities(caps)
                .build();
    }

    /**
     * Build a CompiledRelation with NON-enriched OutputSchema
     * (referencePolicy = null). This simulates a relation from
     * SchemaDerivation.derive() (no timeWindow enrichment).
     */
    private CompiledRelation plainRelation(String dialect) {
        ColumnSpec storeName = ColumnSpec.builder()
                .name("storeName").expression("storeName")
                .build();
        ColumnSpec salesAmount = ColumnSpec.builder()
                .name("salesAmount").expression("salesAmount")
                .build();

        OutputSchema schema = OutputSchema.of(List.of(storeName, salesAmount));

        return CompiledRelation.builder()
                .alias("rel_0")
                .relationSql(RelationSql.builder()
                        .bodySql("SELECT storeName, salesAmount FROM fact_sales")
                        .bodyParams(List.of())
                        .preferredAlias("rel_0")
                        .build())
                .outputSchema(schema)
                .datasourceId("demo-ds")
                .dialect(dialect)
                .capabilities(RelationCapabilities.forDialect(dialect, false))
                .build();
    }

    // ------------------------------------------------------------------
    // 1. readable select pass
    // ------------------------------------------------------------------

    @Test
    @DisplayName("readableSelect_pass · select readable columns succeeds")
    void readableSelect_pass() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName", "salesAmount"))
                        .build());

        assertNotNull(result);
        assertNotNull(result.sql());
        assertTrue(result.sql().contains("storeName"));
        assertTrue(result.sql().contains("salesAmount"));
        assertEquals("mysql8", result.dialect());
        assertEquals("demo-ds", result.datasourceId());
    }

    // ------------------------------------------------------------------
    // 2. orderable orderBy pass
    // ------------------------------------------------------------------

    @Test
    @DisplayName("orderableOrderBy_pass · orderBy on orderable columns succeeds")
    void orderableOrderBy_pass() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName", "salesAmount"))
                        .orderBy(List.of("salesAmount DESC"))
                        .build());

        assertNotNull(result);
        assertTrue(result.sql().contains("ORDER BY"));
        assertTrue(result.sql().contains("salesAmount"));
    }

    // ------------------------------------------------------------------
    // 3. filter readable column pass
    // ------------------------------------------------------------------

    @Test
    @DisplayName("filterReadableColumn_pass · WHERE on readable column succeeds")
    void filterReadableColumn_pass() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName", "salesAmount"))
                        .filter("rel_0.`storeName` = ?")
                        .filterParams(List.of("HQ"))
                        .filterColumns(Set.of("storeName"))
                        .build());

        assertNotNull(result);
        assertTrue(result.sql().contains("WHERE"));
    }

    @Test
    @DisplayName("filterWithoutDeclaredColumns_rejected · raw filter must declare columns")
    void filterWithoutDeclaredColumns_rejected() {
        CompiledRelation rel = enrichedRelation("mysql8");
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of("storeName"))
                                .filter("rel_0.`storeName` = ?")
                                .filterParams(List.of("HQ"))
                                .build()));

        assertEquals(ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                ex.code());
    }

    // ------------------------------------------------------------------
    // 4. limit + pagination pass
    // ------------------------------------------------------------------

    @Test
    @DisplayName("limitPagination_pass · LIMIT + OFFSET succeeds")
    void limitPagination_pass() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName"))
                        .limit(10)
                        .offset(20)
                        .build());

        assertNotNull(result);
        assertTrue(result.sql().contains("LIMIT 10"));
        assertTrue(result.sql().contains("OFFSET 20"));
    }

    // ------------------------------------------------------------------
    // 5. unknown column rejected
    // ------------------------------------------------------------------

    @Test
    @DisplayName("unknownColumn_rejected · nonexistent column throws RELATION_COLUMN_NOT_FOUND")
    void unknownColumn_rejected() {
        CompiledRelation rel = enrichedRelation("mysql8");
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of("nosuchcol"))
                                .build()));

        assertEquals(ComposeCompileErrorCodes.RELATION_COLUMN_NOT_FOUND,
                ex.code());
    }

    // ------------------------------------------------------------------
    // 6. non-readable column rejected
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nonReadableColumn_rejected · column without READABLE throws")
    void nonReadableColumn_rejected() {
        CompiledRelation rel = enrichedRelation("mysql8");
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of("internalNote"))
                                .build()));

        assertEquals(ComposeCompileErrorCodes.RELATION_COLUMN_NOT_READABLE,
                ex.code());
    }

    // ------------------------------------------------------------------
    // 7. non-orderable column rejected
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nonOrderableColumn_rejected · orderBy on non-orderable throws")
    void nonOrderableColumn_rejected() {
        // internalNote has empty referencePolicy, no ORDERABLE
        CompiledRelation rel = enrichedRelation("mysql8");
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of("storeName"))
                                .orderBy(List.of("internalNote"))
                                .build()));

        assertEquals(ComposeCompileErrorCodes.RELATION_COLUMN_NOT_ORDERABLE,
                ex.code());
    }

    // ------------------------------------------------------------------
    // 8. outer aggregate rejected
    // ------------------------------------------------------------------

    @Test
    @DisplayName("outerAggregate_rejected · SUM() throws RELATION_OUTER_AGGREGATE_NOT_SUPPORTED")
    void outerAggregate_rejected() {
        CompiledRelation rel = enrichedRelation("mysql8");
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of("SUM(salesAmount)"))
                                .build()));

        assertEquals(ComposeCompileErrorCodes.RELATION_OUTER_AGGREGATE_NOT_SUPPORTED,
                ex.code());
    }

    // ------------------------------------------------------------------
    // 9. outer window rejected
    // ------------------------------------------------------------------

    @Test
    @DisplayName("outerWindow_rejected · OVER() throws RELATION_OUTER_WINDOW_NOT_SUPPORTED")
    void outerWindow_rejected() {
        CompiledRelation rel = enrichedRelation("mysql8");
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of(
                                        "ROW_NUMBER() OVER(ORDER BY salesAmount)"))
                                .build()));

        assertEquals(ComposeCompileErrorCodes.RELATION_OUTER_WINDOW_NOT_SUPPORTED,
                ex.code());
    }

    // ------------------------------------------------------------------
    // 10. datasource ID preserved
    // ------------------------------------------------------------------

    @Test
    @DisplayName("datasourceId_preserved · datasource flows through outer query")
    void datasourceId_preserved() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName"))
                        .build());

        assertEquals("demo-ds", result.datasourceId());
    }

    // ------------------------------------------------------------------
    // 11. output schema stable after wrapping
    // ------------------------------------------------------------------

    @Test
    @DisplayName("outputSchema_stableAfterWrapping · schema is subset, order preserved")
    void outputSchema_stableAfterWrapping() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("salesAmount", "storeName"))
                        .build());

        OutputSchema schema = result.outputSchema();
        assertEquals(2, schema.size());
        assertEquals("salesAmount", schema.columns().get(0).name());
        assertEquals("storeName", schema.columns().get(1).name());
        // Metadata preserved
        assertNotNull(schema.columns().get(0).referencePolicy());
        assertTrue(schema.columns().get(0).referencePolicy().contains(
                ReferencePolicy.READABLE));
    }

    // ------------------------------------------------------------------
    // 12. inline subquery SQL shape
    // ------------------------------------------------------------------

    @Test
    @DisplayName("inlineSubquery_sqlShape · no-CTE → FROM (...) AS alias")
    void inlineSubquery_sqlShape() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName"))
                        .build());

        String sql = result.sql();
        assertTrue(sql.contains("FROM ("),
                "inline subquery must use FROM (...); got: " + sql);
        assertTrue(sql.contains(") AS rel_0"),
                "inline subquery must use ) AS alias; got: " + sql);
        assertFalse(sql.startsWith("WITH "),
                "inline subquery must NOT start with WITH; got: " + sql);
    }

    // ------------------------------------------------------------------
    // 13. hoisted CTE SQL shape
    // ------------------------------------------------------------------

    @Test
    @DisplayName("hoistedCte_sqlShape · CTE relation → WITH alias AS (...)")
    void hoistedCte_sqlShape() {
        // SQL Server relations use HOISTED_CTE when they have CTE items
        ColumnSpec storeName = ColumnSpec.builder()
                .name("storeName").expression("storeName")
                .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                .build();
        OutputSchema schema = OutputSchema.of(List.of(storeName));

        CompiledRelation rel = CompiledRelation.builder()
                .alias("rel_0")
                .relationSql(RelationSql.builder()
                        .bodySql("SELECT storeName FROM fact_sales")
                        .bodyParams(List.of())
                        .preferredAlias("rel_0")
                        .build())
                .outputSchema(schema)
                .dialect("mssql")
                .capabilities(RelationCapabilities.builder()
                        .canInlineAsSubquery(false)
                        .canHoistCte(true)
                        .containsWithItems(true)
                        .supportsOuterAggregate(false)
                        .supportsOuterWindow(false)
                        .requiresTopLevelWith(true)
                        .relationWrapStrategy(RelationWrapStrategy.HOISTED_CTE)
                        .build())
                .build();

        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName"))
                        .build());

        String sql = result.sql();
        assertTrue(sql.startsWith(";WITH rel_0 AS ("),
                "SQL Server hoisted CTE must start with ;WITH alias AS (...); got: "
                        + sql);
        assertTrue(sql.contains("FROM rel_0"),
                "hoisted CTE must reference alias in FROM; got: " + sql);
    }

    // ------------------------------------------------------------------
    // 14. FROM (WITH invariant
    // ------------------------------------------------------------------

    @Test
    @DisplayName("noFromWith_invariant · outer query SQL never contains FROM (WITH")
    void noFromWith_invariant() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName", "salesAmount"))
                        .orderBy(List.of("salesAmount DESC"))
                        .filter("rel_0.`storeName` = ?")
                        .filterParams(List.of("HQ"))
                        .filterColumns(Set.of("storeName"))
                        .limit(100)
                        .build());

        assertFalse(ComposeRelationCompiler.containsFromWith(result.sql()),
                "Outer query SQL must NEVER contain FROM (WITH; got: "
                        + result.sql());
    }

    // ------------------------------------------------------------------
    // 15. null referencePolicy → default readable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nullReferencePolicy_defaultReadable · non-enriched schema defaults to readable")
    void nullReferencePolicy_defaultReadable() {
        CompiledRelation rel = plainRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName", "salesAmount"))
                        .orderBy(List.of("salesAmount"))
                        .build());

        assertNotNull(result);
        assertEquals(2, result.outputSchema().size());
    }

    // ------------------------------------------------------------------
    // 16. select star → select all readable
    // ------------------------------------------------------------------

    @Test
    @DisplayName("selectStar_selectsAllReadable · null selectColumns = all readable columns")
    void selectStar_selectsAllReadable() {
        CompiledRelation rel = enrichedRelation("mysql8");
        // selectColumns = null → should select storeName and salesAmount
        // but NOT internalNote (not readable)
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder().build());

        assertNotNull(result);
        assertEquals(2, result.outputSchema().size());
        assertTrue(result.outputSchema().contains("storeName"));
        assertTrue(result.outputSchema().contains("salesAmount"));
        assertFalse(result.outputSchema().contains("internalNote"));
    }

    // ------------------------------------------------------------------
    // 17. params order: inner then filter
    // ------------------------------------------------------------------

    @Test
    @DisplayName("paramsOrder_innerThenFilter · inner params before filter params")
    void paramsOrder_innerThenFilter() {
        CompiledRelation rel = enrichedRelation("mysql8");
        RelationOuterQuery result = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName"))
                        .filter("rel_0.`storeName` = ?")
                        .filterParams(List.of("HQ"))
                        .filterColumns(Set.of("storeName"))
                        .build());

        List<Object> params = result.params();
        assertEquals(3, params.size());
        // Inner params come first
        assertEquals("p1", params.get(0));
        assertEquals("p2", params.get(1));
        // Filter params come last
        assertEquals("HQ", params.get(2));
    }

    // ------------------------------------------------------------------
    // Input validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("nullRelation_rejected · relation must not be null")
    void nullRelation_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        null, OuterQuerySpec.builder().build()));
    }

    @Test
    @DisplayName("nullSpec_rejected · spec must not be null")
    void nullSpec_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        enrichedRelation("mysql8"), null));
    }

    // ------------------------------------------------------------------
    // FAIL_CLOSED wrap strategy rejected
    // ------------------------------------------------------------------

    @Test
    @DisplayName("failClosedWrapStrategy_rejected · FAIL_CLOSED relation cannot build outer query")
    void failClosedWrapStrategy_rejected() {
        ColumnSpec col = ColumnSpec.builder()
                .name("id").expression("id")
                .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                .build();
        CompiledRelation rel = CompiledRelation.builder()
                .alias("rel_0")
                .relationSql(RelationSql.builder()
                        .bodySql("SELECT id FROM t")
                        .bodyParams(List.of())
                        .preferredAlias("rel_0")
                        .build())
                .outputSchema(OutputSchema.of(List.of(col)))
                .dialect("mysql")
                .capabilities(RelationCapabilities.builder()
                        .canInlineAsSubquery(false)
                        .canHoistCte(false)
                        .containsWithItems(true)
                        .supportsOuterAggregate(false)
                        .supportsOuterWindow(false)
                        .relationWrapStrategy(RelationWrapStrategy.FAIL_CLOSED)
                        .build())
                .build();

        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of("id"))
                                .build()));
        assertEquals(ComposeCompileErrorCodes.RELATION_WRAP_UNSUPPORTED,
                ex.code());
    }

    // ------------------------------------------------------------------
    // Dialect-specific quoting
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dialect_quoting · MySQL uses backticks, SQL Server uses brackets")
    void dialect_quoting() {
        CompiledRelation mysqlRel = enrichedRelation("mysql8");
        RelationOuterQuery mysqlResult = RelationOuterQueryBuilder.buildOuterQuery(
                mysqlRel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName"))
                        .build());
        assertTrue(mysqlResult.sql().contains("`storeName`"),
                "MySQL should use backtick quoting; got: " + mysqlResult.sql());

        // Postgres uses double-quotes
        CompiledRelation pgRel = enrichedRelation("postgres");
        RelationOuterQuery pgResult = RelationOuterQueryBuilder.buildOuterQuery(
                pgRel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName"))
                        .build());
        assertTrue(pgResult.sql().contains("\"storeName\""),
                "Postgres should use double-quote quoting; got: "
                        + pgResult.sql());
    }
}

package com.foggyframework.dataset.db.model.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.model.engine.compose.compilation.*;
import com.foggyframework.dataset.db.model.engine.compose.relation.*;
import com.foggyframework.dataset.db.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.db.model.engine.compose.schema.OutputSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7f Stable Relation Outer Window Snapshot Producer.
 *
 * <p>Produces {@code target/parity/_stable_relation_outer_window_snapshot.json}
 * with 5 contract cases covering window functions, referencePolicy enforcement,
 * dialect fail-closed, and CTE hoisting.</p>
 */
@DisplayName("StableRelationOuterWindowSnapshotTest · S7f")
class StableRelationOuterWindowSnapshotTest {

    @Test
    @DisplayName("produces _stable_relation_outer_window_snapshot.json")
    void shouldProduceSnapshot() throws Exception {
        List<Map<String, Object>> cases = List.of(
                rankWithRatioOrderCase(),
                movingAvgMeasureCase(),
                ratioInputRejectedCase(),
                mysql57RejectedCase(),
                hoistedSqlServerCase());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "StableRelationOuterWindowSnapshotTest");
        snapshot.put("contractVersion", "S7f-1");
        snapshot.put("generatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .format(new java.util.Date()));
        snapshot.put("cases", cases);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path target = Path.of("target", "parity",
                "_stable_relation_outer_window_snapshot.json");
        Files.createDirectories(target.getParent());
        mapper.writeValue(target.toFile(), snapshot);

        assertTrue(Files.exists(target), "snapshot file must be written");
        assertEquals(5, cases.size(), "expected 5 S7f window cases");
    }

    // ---- Case 1: RANK with ratio as ORDER BY key ----

    private Map<String, Object> rankWithRatioOrderCase() {
        CompiledRelation rel = windowRelation("mysql8", false);
        RelationOuterQuery outer = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of(
                                "storeName",
                                "RANK() OVER (ORDER BY salesAmount__ratio DESC) AS growthRank"))
                        .build());

        assertFalse(outer.sql().toUpperCase(Locale.ROOT).contains("FROM (WITH"));
        assertTrue(outer.outputSchema().contains("growthRank"));
        assertEquals(SemanticKind.WINDOW_CALC,
                outer.outputSchema().get("growthRank").semanticKind());

        Map<String, Object> c = baseCase("outer-rank-ratio-order-mysql8", "mysql8", "pass");
        c.put("sql", outer.sql());
        c.put("params", outer.params());
        c.put("sqlMarkers", List.of("RANK()", "OVER", "ORDER BY", "growthRank"));
        c.put("forbiddenSqlMarkers", List.of("FROM (WITH"));
        c.put("outputSchema", schemaEntries(outer.outputSchema()));
        c.put("capabilities", capsMap(rel.capabilities()));
        return c;
    }

    // ---- Case 2: moving AVG with frame ----

    private Map<String, Object> movingAvgMeasureCase() {
        CompiledRelation rel = windowRelation("mysql8", false);
        RelationOuterQuery outer = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of(
                                "AVG(salesAmount) OVER (PARTITION BY storeName ORDER BY salesDate ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS movingAvg"))
                        .build());

        assertFalse(outer.sql().toUpperCase(Locale.ROOT).contains("FROM (WITH"));
        assertTrue(outer.outputSchema().contains("movingAvg"));
        assertEquals(SemanticKind.WINDOW_CALC,
                outer.outputSchema().get("movingAvg").semanticKind());

        Map<String, Object> c = baseCase("outer-moving-avg-measure-mysql8", "mysql8", "pass");
        c.put("sql", outer.sql());
        c.put("params", outer.params());
        c.put("sqlMarkers", List.of("AVG", "OVER", "PARTITION BY", "ORDER BY",
                "ROWS BETWEEN", "movingAvg"));
        c.put("forbiddenSqlMarkers", List.of("FROM (WITH"));
        c.put("outputSchema", schemaEntries(outer.outputSchema()));
        c.put("capabilities", capsMap(rel.capabilities()));
        return c;
    }

    // ---- Case 3: ratio as window input rejected ----

    private Map<String, Object> ratioInputRejectedCase() {
        CompiledRelation rel = windowRelation("mysql8", false);
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of(
                                        "AVG(salesAmount__ratio) OVER (ORDER BY salesDate) AS badAvg"))
                                .build()));

        Map<String, Object> c = baseCase("outer-window-ratio-input-rejected-mysql8",
                "mysql8", "rejected");
        c.put("errorCode", ex.code());
        c.put("errorPhase", ex.phase());
        c.put("capabilities", capsMap(rel.capabilities()));
        return c;
    }

    // ---- Case 4: MySQL 5.7 rejected ----

    private Map<String, Object> mysql57RejectedCase() {
        CompiledRelation rel = windowRelation("mysql57", false);
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of(
                                        "RANK() OVER (ORDER BY salesAmount DESC) AS r"))
                                .build()));

        Map<String, Object> c = baseCase("outer-window-mysql57-rejected",
                "mysql57", "rejected");
        c.put("errorCode", ex.code());
        c.put("errorPhase", ex.phase());
        c.put("capabilities", capsMap(rel.capabilities()));
        return c;
    }

    // ---- Case 5: SQL Server hoisted CTE ----

    private Map<String, Object> hoistedSqlServerCase() {
        CompiledRelation rel = windowRelation("sqlserver", true);
        RelationOuterQuery outer = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of(
                                "ROW_NUMBER() OVER (ORDER BY salesAmount DESC) AS rowNum"))
                        .build());

        assertTrue(outer.sql().startsWith(";WITH"),
                "SQL Server hoisted CTE must start with ;WITH");
        assertFalse(outer.sql().toUpperCase(Locale.ROOT).contains("FROM (WITH"),
                "SQL must never contain FROM (WITH");
        assertTrue(outer.outputSchema().contains("rowNum"));
        assertEquals(SemanticKind.WINDOW_CALC,
                outer.outputSchema().get("rowNum").semanticKind());

        Map<String, Object> c = baseCase("outer-window-hoisted-sqlserver",
                "sqlserver", "pass");
        c.put("sql", outer.sql());
        c.put("params", outer.params());
        c.put("sqlMarkers", List.of(";WITH", "ROW_NUMBER()", "OVER", "ORDER BY",
                "FROM rel_0"));
        c.put("forbiddenSqlMarkers", List.of("FROM (WITH"));
        c.put("outputSchema", schemaEntries(outer.outputSchema()));
        c.put("capabilities", capsMap(rel.capabilities()));
        return c;
    }

    // ---- fixture builders ----

    private CompiledRelation windowRelation(
            String dialect, boolean hasWithItems) {
        ColumnSpec storeName = ColumnSpec.builder()
                .name("storeName").expression("storeName")
                .semanticKind(SemanticKind.BASE_FIELD)
                .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                .build();
        ColumnSpec salesAmount = ColumnSpec.builder()
                .name("salesAmount").expression("salesAmount")
                .semanticKind(SemanticKind.AGGREGATE_MEASURE)
                .referencePolicy(ReferencePolicy.MEASURE_DEFAULT)
                .build();
        ColumnSpec salesDate = ColumnSpec.builder()
                .name("salesDate").expression("salesDate")
                .semanticKind(SemanticKind.BASE_FIELD)
                .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                .build();
        ColumnSpec ratio = ColumnSpec.builder()
                .name("salesAmount__ratio").expression("salesAmount__ratio")
                .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                .lineage(Set.of("salesAmount"))
                .referencePolicy(ReferencePolicy.TIME_WINDOW_DERIVED_DEFAULT)
                .build();

        List<ColumnSpec> cols = List.of(storeName, salesAmount, salesDate, ratio);

        RelationSql.Builder sql = RelationSql.builder()
                .bodySql(hasWithItems
                        ? "SELECT storeName, salesAmount, salesDate, salesAmount__ratio FROM __rel0_base"
                        : "SELECT storeName, salesAmount, salesDate, salesAmount__ratio FROM fact_sales")
                .bodyParams(hasWithItems ? List.of("p1") : List.of())
                .preferredAlias("rel_0");
        if (hasWithItems) {
            sql.withItems(List.of(CteItem.builder()
                    .name("__rel0_base")
                    .sql("SELECT * FROM fact_sales")
                    .params(List.of("p0"))
                    .build()));
        }

        return CompiledRelation.builder()
                .alias("rel_0")
                .relationSql(sql.build())
                .outputSchema(OutputSchema.of(cols))
                .datasourceId("demo")
                .dialect(dialect)
                .capabilities(RelationCapabilities.forDialect(dialect, hasWithItems))
                .permissionState(RelationPermissionState.UNKNOWN)
                .build();
    }

    private Map<String, Object> baseCase(String id, String dialect, String status) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("dialect", dialect);
        c.put("status", status);
        return c;
    }

    private List<Map<String, Object>> schemaEntries(OutputSchema schema) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ColumnSpec cs : schema.columns()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", cs.name());
            entry.put("semanticKind", cs.semanticKind());
            if (cs.referencePolicy() != null) {
                entry.put("referencePolicy", new ArrayList<>(cs.referencePolicy()));
            }
            if (cs.lineage() != null) {
                entry.put("lineage", new ArrayList<>(cs.lineage()));
            }
            if (cs.valueMeaning() != null) {
                entry.put("valueMeaning", cs.valueMeaning());
            }
            entries.add(entry);
        }
        return entries;
    }

    private Map<String, Object> capsMap(RelationCapabilities caps) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("containsWithItems", caps.containsWithItems());
        m.put("canHoistCte", caps.canHoistCte());
        m.put("canInlineAsSubquery", caps.canInlineAsSubquery());
        m.put("supportsOuterAggregate", caps.supportsOuterAggregate());
        m.put("supportsOuterWindow", caps.supportsOuterWindow());
        m.put("wrapStrategy", caps.relationWrapStrategy());
        return m;
    }
}

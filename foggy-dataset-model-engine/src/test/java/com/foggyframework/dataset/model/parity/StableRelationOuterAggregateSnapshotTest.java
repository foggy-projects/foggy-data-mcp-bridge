package com.foggyframework.dataset.model.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.model.engine.compose.compilation.*;
import com.foggyframework.dataset.model.engine.compose.relation.*;
import com.foggyframework.dataset.model.engine.compose.schema.ColumnSpec;
import com.foggyframework.dataset.model.engine.compose.schema.OutputSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S7e Stable Relation Outer Aggregate Snapshot Producer.
 *
 * <p>Produces {@code target/parity/_stable_relation_outer_aggregate_snapshot.json}
 * without changing the frozen S7a schema snapshot contract.</p>
 */
@DisplayName("StableRelationOuterAggregateSnapshotTest · S7e")
class StableRelationOuterAggregateSnapshotTest {

    @Test
    @DisplayName("produces _stable_relation_outer_aggregate_snapshot.json")
    void shouldProduceSnapshot() throws Exception {
        List<Map<String, Object>> cases = List.of(
                passCase("outer-sum-groupby-mysql8", "mysql8", false),
                passCase("outer-sum-hoisted-sqlserver", "sqlserver", true),
                ratioRejectedCase(),
                mysql57CteFailClosedCase());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "StableRelationOuterAggregateSnapshotTest");
        snapshot.put("contractVersion", "S7e-1");
        snapshot.put("generatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .format(new java.util.Date()));
        snapshot.put("cases", cases);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path target = Path.of("target", "parity",
                "_stable_relation_outer_aggregate_snapshot.json");
        Files.createDirectories(target.getParent());
        mapper.writeValue(target.toFile(), snapshot);

        assertTrue(Files.exists(target), "snapshot file must be written");
        assertEquals(4, cases.size(), "expected 4 S7e aggregate cases");
    }

    private Map<String, Object> passCase(
            String id, String dialect, boolean hasWithItems) {
        CompiledRelation rel = relation(dialect, hasWithItems, false);
        RelationOuterQuery outer = RelationOuterQueryBuilder.buildOuterQuery(
                rel,
                OuterQuerySpec.builder()
                        .selectColumns(List.of("storeName", "SUM(salesAmount) AS totalSales"))
                        .groupBy(List.of("storeName"))
                        .build());

        assertFalse(outer.sql().toUpperCase(Locale.ROOT).contains("FROM (WITH"));
        assertTrue(outer.outputSchema().contains("totalSales"));
        assertEquals(SemanticKind.AGGREGATE_MEASURE,
                outer.outputSchema().get("totalSales").semanticKind());

        Map<String, Object> c = baseCase(id, dialect, "pass");
        c.put("sql", outer.sql());
        c.put("params", outer.params());
        c.put("sqlMarkers", List.of("SUM", "GROUP BY", "totalSales"));
        c.put("forbiddenSqlMarkers", List.of("FROM (WITH"));
        c.put("outputSchema", schemaEntries(outer.outputSchema()));
        c.put("capabilities", capsMap(rel.capabilities()));
        return c;
    }

    private Map<String, Object> ratioRejectedCase() {
        CompiledRelation rel = relation("mysql8", false, true);
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of(
                                        "SUM(salesAmount__ratio) AS badRatio"))
                                .build()));

        Map<String, Object> c = baseCase("outer-sum-ratio-rejected-mysql8",
                "mysql8", "rejected");
        c.put("errorCode", ex.code());
        c.put("errorPhase", ex.phase());
        c.put("capabilities", capsMap(rel.capabilities()));
        return c;
    }

    private Map<String, Object> mysql57CteFailClosedCase() {
        CompiledRelation rel = relation("mysql57", true, false);
        ComposeCompileException ex = assertThrows(
                ComposeCompileException.class,
                () -> RelationOuterQueryBuilder.buildOuterQuery(
                        rel,
                        OuterQuerySpec.builder()
                                .selectColumns(List.of(
                                        "SUM(salesAmount) AS totalSales"))
                                .build()));

        Map<String, Object> c = baseCase("outer-sum-cte-failclosed-mysql57",
                "mysql57", "rejected");
        c.put("errorCode", ex.code());
        c.put("errorPhase", ex.phase());
        c.put("capabilities", capsMap(rel.capabilities()));
        return c;
    }

    private CompiledRelation relation(
            String dialect, boolean hasWithItems, boolean includeRatio) {
        ColumnSpec storeName = ColumnSpec.builder()
                .name("storeName")
                .expression("storeName")
                .semanticKind(SemanticKind.BASE_FIELD)
                .referencePolicy(ReferencePolicy.DIMENSION_DEFAULT)
                .build();
        ColumnSpec salesAmount = ColumnSpec.builder()
                .name("salesAmount")
                .expression("salesAmount")
                .semanticKind(SemanticKind.AGGREGATE_MEASURE)
                .referencePolicy(ReferencePolicy.MEASURE_DEFAULT)
                .build();

        List<ColumnSpec> cols = new ArrayList<>(List.of(storeName, salesAmount));
        if (includeRatio) {
            cols.add(ColumnSpec.builder()
                    .name("salesAmount__ratio")
                    .expression("salesAmount__ratio")
                    .semanticKind(SemanticKind.TIME_WINDOW_DERIVED)
                    .lineage(Set.of("salesAmount"))
                    .referencePolicy(ReferencePolicy.TIME_WINDOW_DERIVED_DEFAULT)
                    .build());
        }

        RelationSql.Builder sql = RelationSql.builder()
                .bodySql(hasWithItems
                        ? "SELECT storeName, salesAmount FROM __rel0_base"
                        : "SELECT storeName, salesAmount FROM fact_sales")
                .bodyParams(hasWithItems ? List.of("p1") : List.of())
                .preferredAlias("rel_0");
        if (hasWithItems) {
            sql.withItems(List.of(CteItem.builder()
                    .name("__rel0_base")
                    .sql("SELECT storeName, salesAmount FROM fact_sales")
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
                entry.put("referencePolicy", orderedReferencePolicy(cs.referencePolicy()));
            }
            if (cs.lineage() != null) {
                entry.put("lineage", new ArrayList<>(cs.lineage()));
            }
            entries.add(entry);
        }
        return entries;
    }

    private List<String> orderedReferencePolicy(Set<String> policies) {
        List<String> ordered = new ArrayList<>();
        for (String policy : List.of(
                ReferencePolicy.READABLE,
                ReferencePolicy.GROUPABLE,
                ReferencePolicy.AGGREGATABLE,
                ReferencePolicy.WINDOWABLE,
                ReferencePolicy.ORDERABLE)) {
            if (policies.contains(policy)) {
                ordered.add(policy);
            }
        }
        return ordered;
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

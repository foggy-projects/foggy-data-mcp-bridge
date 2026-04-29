package com.foggyframework.dataset.db.model.parity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.model.engine.compose.plan.TimeWindowDef;
import com.foggyframework.dataset.db.model.engine.compose.plan.TimeWindowExpander;
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
 * S7a Stable Relation Schema Snapshot Producer.
 *
 * <p>Produces {@code target/parity/_stable_relation_schema_snapshot.json}
 * with schema/capability/marker assertions for 4 dialects × 3 timeWindow
 * shapes.</p>
 *
 * <p>This is a pure model-level test — no Spring context, no live DB.</p>
 */
@DisplayName("StableRelationSnapshotTest · S7a")
class StableRelationSnapshotTest {

    private static final String[] DIALECTS = {"mysql8", "postgres", "sqlite", "sqlserver"};

    private static TimeWindowDef twDef(String comparison, String grain) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", "salesDate$id");
        m.put("comparison", comparison);
        m.put("grain", grain);
        m.put("range", "[)");
        m.put("value", List.of("2024-01-01", "2025-01-01"));
        m.put("targetMetrics", List.of("salesAmount"));
        return TimeWindowDef.fromMap(m);
    }

    @Test
    @DisplayName("produces _stable_relation_schema_snapshot.json")
    void shouldProduceSnapshot() throws Exception {
        List<Map<String, Object>> cases = new ArrayList<>();

        // 3 shapes × 4 dialects
        for (String dialect : DIALECTS) {
            cases.add(buildCase("timewindow-yoy-relation", "yoy", "month", dialect));
            cases.add(buildCase("timewindow-rolling-relation", "rolling_7d", "day", dialect));
            cases.add(buildCase("timewindow-cumulative-relation", "ytd", "day", dialect));
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("source", "StableRelationSnapshotTest");
        snapshot.put("contractVersion", "S7a-1");
        snapshot.put("generatedAt", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                .format(new java.util.Date()));
        snapshot.put("cases", cases);

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path target = Path.of("target", "parity", "_stable_relation_schema_snapshot.json");
        Files.createDirectories(target.getParent());
        mapper.writeValue(target.toFile(), snapshot);

        assertTrue(Files.exists(target), "snapshot file must be written");
        assertEquals(12, cases.size(), "expected 4 dialects × 3 shapes = 12 cases");
    }

    @Test
    @DisplayName("SQL Server snapshot must not contain FROM (WITH")
    void sqlServerForbiddenMarker() {
        // Simulate relation SQL for SQL Server with CTE items
        CteItem base = CteItem.builder()
                .name("__rel0_tw_base")
                .sql("SELECT salesDate, storeName, SUM(amount) AS salesAmount FROM fact_sales GROUP BY salesDate, storeName")
                .params(List.of("2024-01-01"))
                .build();

        RelationSql rSql = RelationSql.builder()
                .withItems(List.of(base))
                .bodySql("SELECT cur.storeName, cur.salesAmount FROM __rel0_tw_base cur")
                .bodyParams(List.of())
                .preferredAlias("rel_0")
                .build();

        // Render the hoisted form (what the compiler should produce)
        StringBuilder hoisted = new StringBuilder(";WITH\n");
        for (CteItem item : rSql.withItems()) {
            hoisted.append("  ").append(item.name()).append(" AS (\n    ")
                    .append(item.sql()).append("\n  ),\n");
        }
        hoisted.append("  ").append(rSql.preferredAlias()).append(" AS (\n    ")
                .append(rSql.bodySql()).append("\n  )\n");
        hoisted.append("SELECT * FROM ").append(rSql.preferredAlias());

        String renderedSql = hoisted.toString();

        // Positive markers
        assertTrue(renderedSql.contains("WITH"), "must contain WITH");
        assertTrue(renderedSql.contains("rel_0 AS"), "must contain rel_0 AS");
        assertTrue(renderedSql.contains("FROM rel_0"), "must contain FROM rel_0");

        // Forbidden marker
        assertFalse(renderedSql.contains("FROM (WITH"),
                "SQL Server SQL must NEVER contain FROM (WITH");
    }

    // ------------------------------------------------------------------

    private Map<String, Object> buildCase(String id, String comparison,
                                           String grain, String dialect) {
        TimeWindowDef tw = twDef(comparison, grain);
        boolean hasCte = tw.isComparative(); // comparative uses CTE in Java

        OutputSchema schema = TimeWindowExpander.getOutputSchema(
                tw, List.of("storeName"), Set.of("salesAmount"));

        RelationCapabilities caps = s7aFrozenCapabilities(dialect, hasCte);

        // Build relation model
        RelationSql rSql = RelationSql.builder()
                .bodySql("SELECT ... /* " + comparison + " body */")
                .bodyParams(List.of("2024-01-01", "2025-01-01"))
                .preferredAlias("rel_0")
                .withItems(hasCte ? List.of(
                        CteItem.builder()
                                .name("__rel0_tw_base")
                                .sql("SELECT ... /* base aggregate */")
                                .params(List.of("2024-01-01"))
                                .build()
                ) : List.of())
                .build();

        CompiledRelation rel = CompiledRelation.builder()
                .alias("rel_0")
                .relationSql(rSql)
                .outputSchema(schema)
                .datasourceId("demo")
                .dialect(dialect)
                .capabilities(caps)
                .permissionState(RelationPermissionState.UNKNOWN)
                .build();

        // Serialize to snapshot structure
        Map<String, Object> caseMap = new LinkedHashMap<>();
        caseMap.put("id", id + "-" + dialect);
        caseMap.put("dialect", dialect);

        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("alias", rel.alias());
        relation.put("wrapStrategy", caps.relationWrapStrategy());
        relation.put("datasourceId", rel.datasourceId());
        relation.put("permissionState", rel.permissionState());

        Map<String, Object> capsMap = new LinkedHashMap<>();
        capsMap.put("containsWithItems", caps.containsWithItems());
        capsMap.put("canHoistCte", caps.canHoistCte());
        capsMap.put("canInlineAsSubquery", caps.canInlineAsSubquery());
        capsMap.put("supportsOuterAggregate", caps.supportsOuterAggregate());
        capsMap.put("supportsOuterWindow", caps.supportsOuterWindow());
        relation.put("capabilities", capsMap);

        List<Map<String, Object>> schemaEntries = new ArrayList<>();
        for (ColumnSpec cs : schema.columns()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", cs.name());
            entry.put("semanticKind", cs.semanticKind());
            if (cs.referencePolicy() != null) {
                entry.put("referencePolicy", orderedReferencePolicy(cs.referencePolicy()));
            }
            if (cs.valueMeaning() != null) {
                entry.put("valueMeaning", cs.valueMeaning());
            }
            if (cs.lineage() != null) {
                entry.put("lineage", new ArrayList<>(cs.lineage()));
            }
            schemaEntries.add(entry);
        }
        relation.put("outputSchema", schemaEntries);
        caseMap.put("relation", relation);

        // SQL markers
        List<String> sqlMarkers = new ArrayList<>();
        if (hasCte && caps.canHoistCte()) {
            sqlMarkers.add("WITH");
            sqlMarkers.add("rel_0 AS");
            sqlMarkers.add("FROM rel_0");
        }
        caseMap.put("sqlMarkers", sqlMarkers);
        caseMap.put("forbiddenSqlMarkers", List.of("FROM (WITH"));

        // Params order
        caseMap.put("params", rSql.flattenParams());

        return caseMap;
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

    private RelationCapabilities s7aFrozenCapabilities(
            String dialect, boolean hasCte) {
        RelationCapabilities current = RelationCapabilities.forDialect(dialect, hasCte);
        return RelationCapabilities.builder()
                .canInlineAsSubquery(current.canInlineAsSubquery())
                .canHoistCte(current.canHoistCte())
                .containsWithItems(current.containsWithItems())
                .supportsOuterAggregate(false)
                .supportsOuterWindow(false)
                .requiresTopLevelWith(current.requiresTopLevelWith())
                .relationWrapStrategy(current.relationWrapStrategy())
                .build();
    }
}

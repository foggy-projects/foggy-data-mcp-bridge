package com.foggyframework.dataset.db.model.engine.pivot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.MysqlDialect;
import com.foggyframework.dataset.db.dialect.PostgresDialect;
import com.foggyframework.dataset.db.dialect.SqliteDialect;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainRelationRenderResult;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainRelationRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportRefusalException;
import com.foggyframework.dataset.db.model.engine.pivot.transport.DomainTransportTuple;
import com.foggyframework.dataset.db.model.engine.pivot.transport.Mysql57DerivedTableDomainRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.Mysql8ValuesDomainRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.PostgresCteDomainRenderer;
import com.foggyframework.dataset.db.model.engine.pivot.transport.SqliteCteDomainRenderer;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.AxisField;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotMetricItem;
import com.foggyframework.dataset.db.model.semantic.domain.pivot.PivotRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Java-side producer for Python P0-7 pivot/domain transport neutral replay.
 *
 * <p>This snapshot deliberately stays below database execution and Odoo domain
 * models. It captures the shared Pivot DTO/translation boundary plus
 * dialect-renderer contracts that Python can replay offline.</p>
 */
@DisplayName("JavaPivotDomainSnapshotTest - Python alignment P0-7")
class JavaPivotDomainSnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    @DisplayName("writes java_pivot_domain_snapshot_parity.json for Python replay")
    void shouldProducePivotDomainSnapshot() throws Exception {
        List<Map<String, Object>> cases = cases();
        for (Map<String, Object> c : cases) {
            assertJavaContract(c);
        }

        Map<String, Object> snapshot = ordered();
        snapshot.put("schemaVersion", 1);
        snapshot.put("feature", "pivotDomainTransport");
        snapshot.put("source", "JavaPivotDomainSnapshotTest");
        snapshot.put("cases", cases);

        Path pythonTarget = Path.of(
                "..",
                "..",
                "foggy-data-mcp-bridge-python",
                "tests",
                "fixtures",
                "java_pivot_domain_snapshot_parity.json"
        ).normalize();
        Files.createDirectories(pythonTarget.getParent());
        MAPPER.writeValue(pythonTarget.toFile(), snapshot);

        Path localCopy = Path.of("target", "parity", "java_pivot_domain_snapshot_parity.json");
        Files.createDirectories(localCopy.getParent());
        MAPPER.writeValue(localCopy.toFile(), snapshot);
        assertTrue(Files.exists(pythonTarget),
                "snapshot was not written: " + pythonTarget.toAbsolutePath());
    }

    private static void assertJavaContract(Map<String, Object> c) {
        String type = (String) c.get("type");
        switch (type) {
            case "pivot-request-contract" -> assertPivotRequestContract(c);
            case "pivot-translation-contract" -> assertPivotTranslationContract(c);
            case "domain-renderer-contract" -> assertDomainRendererContract(c);
            case "domain-renderer-refusal" -> assertDomainRendererRefusal(c);
            case "documented-gap" -> assertDocumentedGap(c);
            default -> fail("Unknown pivot/domain snapshot case type: " + type);
        }
    }

    private static void assertPivotRequestContract(Map<String, Object> c) {
        PivotRequest pivot = pivotFrom(c.get("request"));
        pivot.validateMetrics();

        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        assertEquals(expected.get("rowFields"), axisNames(pivot.getRows()));
        assertEquals(expected.get("columnFields"), axisNames(pivot.getColumns()));
        assertEquals(expected.get("nativeMetrics"), pivot.getNativeMetricNames());
        assertEquals(expected.get("sqlMetrics"), pivot.getSqlMetricNames());
        assertEquals(expected.get("allOutputMetrics"), pivot.getAllOutputMetricNames());
        assertEquals(expected.get("parentShareMetrics"), metricNames(pivot.getParentShareMetrics()));
        assertEquals(expected.get("baselineRatioMetrics"), metricNames(pivot.getBaselineRatioMetrics()));
        assertEquals(expected.get("outputFormat"), pivot.getOutputFormat());
        assertEquals(expected.get("rowLevelCount"), pivot.getRowLevelCount());
        assertEquals(expected.get("columnLevelCount"), pivot.getColumnLevelCount());
        assertNotNull(pivot.getOptions());
        assertEquals(expected.get("grandTotal"), pivot.getOptions().isGrandTotal());
        assertNotNull(pivot.getLayout());
        assertEquals(expected.get("metricPlacement"), pivot.getLayout().getMetricPlacement());
    }

    private static void assertPivotTranslationContract(Map<String, Object> c) {
        PivotRequest pivot = pivotFrom(c.get("request"));
        pivot.validateMetrics();

        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("expected");
        List<String> groupBy = new ArrayList<>();
        groupBy.addAll(axisNames(pivot.getRows()));
        groupBy.addAll(axisNames(pivot.getColumns()));

        List<String> translatedColumns = new ArrayList<>(groupBy);
        translatedColumns.addAll(pivot.getSqlMetricNames());

        assertEquals(expected.get("translatedGroupBy"), groupBy);
        assertEquals(expected.get("translatedColumns"), translatedColumns);
        assertEquals(expected.get("wantGrandTotal"), pivot.getOptions().isGrandTotal());
        assertEquals(expected.get("parentShareMetricNames"), metricNames(pivot.getParentShareMetrics()));
    }

    private static void assertDomainRendererContract(Map<String, Object> c) {
        DomainRelationRenderer renderer = rendererFrom((String) c.get("renderer"));
        FDialect dialect = dialectFrom((String) c.get("dialect"));
        DomainRelationRenderResult result = renderer.render(
                dialect,
                (String) c.get("databaseVersion"),
                planFrom(c.get("plan")));

        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("javaExpected");
        assertEquals(expected.get("placement"), result.getPlacement().name());
        for (String marker : stringList(expected.get("sqlMarkers"))) {
            assertTrue(result.getSqlFragment().contains(marker),
                    "[" + c.get("id") + "] SQL marker missing: " + marker + "\n" + result.getSqlFragment());
        }
        for (String marker : stringList(expected.get("joinPredicateMarkers"))) {
            assertTrue(result.getJoinPredicate().contains(marker),
                    "[" + c.get("id") + "] predicate marker missing: " + marker + "\n" + result.getJoinPredicate());
        }
        assertEquals(((Number) expected.get("paramCount")).intValue(), result.getParams().size());
        if (expected.containsKey("params")) {
            assertEquals(expected.get("params"), result.getParams());
        }
    }

    private static void assertDomainRendererRefusal(Map<String, Object> c) {
        DomainRelationRenderer renderer = rendererFrom((String) c.get("renderer"));
        FDialect dialect = dialectFrom((String) c.get("dialect"));

        @SuppressWarnings("unchecked")
        Map<String, Object> expected = (Map<String, Object>) c.get("javaExpected");
        DomainTransportRefusalException ex = assertThrows(
                DomainTransportRefusalException.class,
                () -> renderer.render(dialect, (String) c.get("databaseVersion"), planFrom(c.get("plan"))));
        for (String marker : stringList(expected.get("messageMarkers"))) {
            assertTrue(ex.getMessage().contains(marker),
                    "[" + c.get("id") + "] refusal marker missing: " + marker + " in " + ex.getMessage());
        }
    }

    private static void assertDocumentedGap(Map<String, Object> c) {
        assertFalse(((String) c.get("parityGap")).isBlank());
        assertDomainRendererContract(c);
    }

    private static List<Map<String, Object>> cases() {
        List<Map<String, Object>> out = new ArrayList<>();
        out.add(pivotRequestCase());
        out.add(pivotTranslationCase());
        out.add(postgresDomainCase());
        out.add(sqliteTupleDomainCase());
        out.add(mysql8DomainCase());
        out.add(emptyColumnRefusalCase());
        out.add(mysql57DocumentedGapCase());
        return out;
    }

    private static Map<String, Object> pivotRequestCase() {
        Map<String, Object> c = ordered();
        c.put("id", "pivot-flat-native-parent-share-contract");
        c.put("type", "pivot-request-contract");
        c.put("request", pivotSemanticRequest());
        c.put("expected", expectedPivotSummary());
        return c;
    }

    private static Map<String, Object> pivotTranslationCase() {
        Map<String, Object> c = ordered();
        c.put("id", "pivot-flat-translation-contract");
        c.put("type", "pivot-translation-contract");
        c.put("request", pivotSemanticRequest());
        Map<String, Object> expected = ordered();
        expected.put("translatedGroupBy", List.of("product$categoryName", "orderStatus$caption"));
        expected.put("translatedColumns", List.of("product$categoryName", "orderStatus$caption", "salesAmount"));
        expected.put("wantGrandTotal", true);
        expected.put("parentShareMetricNames", List.of("categoryShare"));
        c.put("expected", expected);
        return c;
    }

    private static Map<String, Object> postgresDomainCase() {
        Map<String, Object> c = domainCaseBase("domain-postgres-single-field", "postgres", "postgres", singleFieldPlan());
        Map<String, Object> javaExpected = ordered();
        javaExpected.put("placement", "CTE");
        javaExpected.put("sqlMarkers", List.of(
                "_pivot_domain_transport(\"category\") AS (",
                "VALUES (?)"));
        javaExpected.put("joinPredicateMarkers", List.of(
                "_base.\"category\" IS NOT DISTINCT FROM _d.\"category\""));
        javaExpected.put("paramCount", 3);
        javaExpected.put("params", Arrays.asList("A", "B", null));
        c.put("javaExpected", javaExpected);

        Map<String, Object> pythonExpected = ordered();
        pythonExpected.put("placement", "CTE");
        pythonExpected.put("sqlMarkers", List.of(
                "WITH _pivot_domain_transport(\"category\") AS (",
                "VALUES (?), (?), (?)"));
        pythonExpected.put("joinPredicateMarkers", List.of(
                "_base.\"category\" IS NOT DISTINCT FROM _d.\"category\""));
        pythonExpected.put("paramCount", 3);
        pythonExpected.put("params", Arrays.asList("A", "B", null));
        c.put("pythonExpected", pythonExpected);
        return c;
    }

    private static Map<String, Object> sqliteTupleDomainCase() {
        Map<String, Object> c = domainCaseBase("domain-sqlite-two-field-null-safe", "sqlite", "sqlite", tupleFieldPlan());
        Map<String, Object> javaExpected = ordered();
        javaExpected.put("placement", "CTE");
        javaExpected.put("sqlMarkers", List.of(
                "_pivot_domain_transport(\"category\", \"product\") AS (",
                "VALUES (?, ?)"));
        javaExpected.put("joinPredicateMarkers", List.of(
                "_base.\"category\" IS _d.\"category\"",
                "_base.\"product\" IS _d.\"product\""));
        javaExpected.put("paramCount", 6);
        javaExpected.put("params", Arrays.asList("A", "p1", "A", null, "B", "p2"));
        c.put("javaExpected", javaExpected);

        Map<String, Object> pythonExpected = ordered();
        pythonExpected.put("placement", "CTE");
        pythonExpected.put("sqlMarkers", List.of(
                "WITH _pivot_domain_transport(\"category\", \"product\") AS (",
                "VALUES (?, ?), (?, ?), (?, ?)"));
        pythonExpected.put("joinPredicateMarkers", List.of(
                "_base.\"category\" IS _d.\"category\"",
                "_base.\"product\" IS _d.\"product\""));
        pythonExpected.put("paramCount", 6);
        pythonExpected.put("params", Arrays.asList("A", "p1", "A", null, "B", "p2"));
        c.put("pythonExpected", pythonExpected);
        return c;
    }

    private static Map<String, Object> mysql8DomainCase() {
        Map<String, Object> c = domainCaseBase("domain-mysql8-versioned-null-safe", "mysql8", "mysql", singleFieldPlan());
        c.put("databaseVersion", "8.0.19-commercial");
        c.put("acceptedDivergence", "Java emits VALUES ROW(?); Python emits CTE UNION ALL SELECT for MySQL8 stability.");

        Map<String, Object> javaExpected = ordered();
        javaExpected.put("placement", "CTE");
        javaExpected.put("sqlMarkers", List.of(
                "_pivot_domain_transport(`category`) AS (",
                "VALUES ROW(?)"));
        javaExpected.put("joinPredicateMarkers", List.of(
                "_base.`category` <=> _d.`category`"));
        javaExpected.put("paramCount", 3);
        javaExpected.put("params", Arrays.asList("A", "B", null));
        c.put("javaExpected", javaExpected);

        Map<String, Object> pythonExpected = ordered();
        pythonExpected.put("placement", "CTE");
        pythonExpected.put("sqlMarkers", List.of(
                "WITH _pivot_domain_transport(`category`) AS (",
                "SELECT ?",
                "UNION ALL SELECT ?"));
        pythonExpected.put("joinPredicateMarkers", List.of(
                "_base.`category` <=> _d.`category`"));
        pythonExpected.put("paramCount", 3);
        pythonExpected.put("params", Arrays.asList("A", "B", null));
        c.put("pythonExpected", pythonExpected);
        return c;
    }

    private static Map<String, Object> emptyColumnRefusalCase() {
        Map<String, Object> c = domainCaseBase("domain-empty-columns-refused", "postgres", "postgres", emptyColumnPlan());
        c.put("type", "domain-renderer-refusal");
        Map<String, Object> javaExpected = ordered();
        javaExpected.put("messageMarkers", List.of("Empty domain plan"));
        c.put("javaExpected", javaExpected);
        Map<String, Object> pythonExpected = ordered();
        pythonExpected.put("messageMarkers", List.of("PIVOT_DOMAIN_TRANSPORT_REFUSED", "empty columns"));
        c.put("pythonExpected", pythonExpected);
        return c;
    }

    private static Map<String, Object> mysql57DocumentedGapCase() {
        Map<String, Object> c = domainCaseBase("domain-mysql57-derived-table-java-only-gap", "mysql57", "mysql", singleFieldPlan());
        c.put("type", "documented-gap");
        c.put("parityGap", "Java renders MySQL 5.7 domain transport as DERIVED_TABLE; Python currently fails closed for mysql5.x.");

        Map<String, Object> javaExpected = ordered();
        javaExpected.put("placement", "DERIVED_TABLE");
        javaExpected.put("sqlMarkers", List.of(
                "SELECT ? AS `category`",
                "UNION ALL\n  SELECT ?"));
        javaExpected.put("joinPredicateMarkers", List.of(
                "_base.`category` <=> _d.`category`"));
        javaExpected.put("paramCount", 3);
        javaExpected.put("params", Arrays.asList("A", "B", null));
        c.put("javaExpected", javaExpected);

        Map<String, Object> pythonExpected = ordered();
        pythonExpected.put("status", "refused");
        pythonExpected.put("messageMarkers", List.of("PIVOT_DOMAIN_TRANSPORT_REFUSED", "mysql5.7"));
        c.put("pythonExpected", pythonExpected);
        return c;
    }

    private static Map<String, Object> domainCaseBase(String id,
                                                      String renderer,
                                                      String dialect,
                                                      Map<String, Object> plan) {
        Map<String, Object> c = ordered();
        c.put("id", id);
        c.put("type", "domain-renderer-contract");
        c.put("renderer", renderer);
        c.put("dialect", dialect);
        c.put("plan", plan);
        return c;
    }

    private static Map<String, Object> pivotSemanticRequest() {
        Map<String, Object> request = ordered();
        Map<String, Object> pivot = ordered();
        pivot.put("rows", List.of(field("product$categoryName")));
        pivot.put("columns", List.of(field("orderStatus$caption")));
        pivot.put("metrics", List.of(
                "salesAmount",
                metric("categoryShare", "parentShare", "salesAmount", "rows",
                        "product$categoryName", null)));
        pivot.put("options", Map.of("grandTotal", true));
        pivot.put("outputFormat", "flat");
        pivot.put("layout", Map.of("metricPlacement", "columns"));
        request.put("pivot", pivot);
        return request;
    }

    private static Map<String, Object> expectedPivotSummary() {
        Map<String, Object> expected = ordered();
        expected.put("rowFields", List.of("product$categoryName"));
        expected.put("columnFields", List.of("orderStatus$caption"));
        expected.put("nativeMetrics", List.of("salesAmount"));
        expected.put("sqlMetrics", List.of("salesAmount"));
        expected.put("allOutputMetrics", List.of("salesAmount", "categoryShare"));
        expected.put("parentShareMetrics", List.of("categoryShare"));
        expected.put("baselineRatioMetrics", List.of());
        expected.put("outputFormat", "flat");
        expected.put("rowLevelCount", 1);
        expected.put("columnLevelCount", 1);
        expected.put("grandTotal", true);
        expected.put("metricPlacement", "columns");
        return expected;
    }

    private static Map<String, Object> singleFieldPlan() {
        Map<String, Object> plan = ordered();
        plan.put("fields", List.of("category"));
        plan.put("tuples", Arrays.asList(
                Collections.singletonList("A"),
                Collections.singletonList("B"),
                Collections.singletonList(null)));
        return plan;
    }

    private static Map<String, Object> tupleFieldPlan() {
        Map<String, Object> plan = ordered();
        plan.put("fields", List.of("category", "product"));
        plan.put("tuples", Arrays.asList(
                Arrays.asList("A", "p1"),
                Arrays.asList("A", null),
                Arrays.asList("B", "p2")));
        return plan;
    }

    private static Map<String, Object> emptyColumnPlan() {
        Map<String, Object> plan = ordered();
        plan.put("fields", List.of());
        plan.put("tuples", List.of(List.of("A")));
        return plan;
    }

    private static Map<String, Object> field(String name) {
        Map<String, Object> out = ordered();
        out.put("field", name);
        return out;
    }

    private static Map<String, Object> metric(String name,
                                              String type,
                                              String of,
                                              String axis,
                                              String level,
                                              String parentLevel) {
        Map<String, Object> out = ordered();
        out.put("name", name);
        out.put("type", type);
        out.put("of", of);
        out.put("axis", axis);
        out.put("level", level);
        if (parentLevel != null) {
            out.put("parentLevel", parentLevel);
        }
        return out;
    }

    private static PivotRequest pivotFrom(Object rawRequest) {
        SemanticQueryRequest request = MAPPER.convertValue(rawRequest, SemanticQueryRequest.class);
        assertNotNull(request.getPivot());
        return request.getPivot();
    }

    private static DomainTransportPlan planFrom(Object rawPlan) {
        @SuppressWarnings("unchecked")
        Map<String, Object> node = (Map<String, Object>) rawPlan;
        List<DomainTransportField> fields = stringList(node.get("fields")).stream()
                .map(DomainTransportField::new)
                .toList();
        List<DomainTransportTuple> tuples = new ArrayList<>();
        @SuppressWarnings("unchecked")
        List<List<Object>> rows = (List<List<Object>>) node.get("tuples");
        for (List<Object> row : rows) {
            tuples.add(new DomainTransportTuple(row));
        }
        return DomainTransportPlan.builder()
                .fields(fields)
                .tuples(tuples)
                .build();
    }

    private static DomainRelationRenderer rendererFrom(String name) {
        return switch (name) {
            case "postgres" -> new PostgresCteDomainRenderer();
            case "sqlite" -> new SqliteCteDomainRenderer();
            case "mysql8" -> new Mysql8ValuesDomainRenderer();
            case "mysql57" -> new Mysql57DerivedTableDomainRenderer();
            default -> throw new IllegalArgumentException("Unknown renderer: " + name);
        };
    }

    private static FDialect dialectFrom(String name) {
        return switch (name) {
            case "postgres" -> new PostgresDialect();
            case "sqlite" -> new SqliteDialect();
            case "mysql" -> new MysqlDialect();
            default -> throw new IllegalArgumentException("Unknown dialect: " + name);
        };
    }

    private static List<String> axisNames(List<AxisField> axis) {
        if (axis == null) {
            return List.of();
        }
        return axis.stream().map(AxisField::getField).toList();
    }

    private static List<String> metricNames(List<PivotMetricItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(PivotMetricItem::getName).toList();
    }

    private static List<String> stringList(Object raw) {
        if (raw == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<String> value = (List<String>) raw;
        return value;
    }

    private static Map<String, Object> ordered() {
        return new LinkedHashMap<>();
    }
}

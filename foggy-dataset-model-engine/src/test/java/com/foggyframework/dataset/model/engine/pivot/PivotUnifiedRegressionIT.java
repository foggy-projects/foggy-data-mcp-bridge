package com.foggyframework.dataset.model.engine.pivot;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.engine.expression.QueryCalculationException;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/** Unified Pivot contracts: exact member grains, bound auxiliary execution and collision-free coordinates. */
@TestPropertySource(properties = "pivot.unified.regression=true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PivotUnifiedRegressionIT extends EcommerceTestSupport {
    @Resource SemanticQueryServiceV3 service;
    @Resource SemanticQueryPayloadMapper mapper;
    @Resource SystemBundlesContext bundles;
    @Resource com.fasterxml.jackson.databind.ObjectMapper json;
    final AtomicInteger evidenceSequence = new AtomicInteger();
    @TempDir static Path root;
    static final String TABLE = "pivot_unified_facts",
            NODES = "pivot_unified_nodes",
            BUNDLE = "pivot-unified-regression";

    @BeforeAll
    void setup() throws Exception {
        jdbcTemplate.execute(
                "CREATE TABLE "
                        + NODES
                        + " (id INTEGER PRIMARY KEY,parent_id INTEGER,caption VARCHAR(30),title VARCHAR(30))");
        jdbcTemplate.execute(
                "INSERT INTO "
                        + NODES
                        + " VALUES(1,NULL,'Root','root-title'),(2,1,'Left','left-title'),(3,1,'Right','right-title'),(4,2,'Leaf','leaf-title'),(10,11,'CycleA','cycle-a'),(11,10,'CycleB','cycle-b')");
        jdbcTemplate.execute(
                "CREATE TABLE "
                        + TABLE
                        + " (id INTEGER PRIMARY KEY,scenario VARCHAR(30),category VARCHAR(30),leaf VARCHAR(30),ym VARCHAR(30),node_id INTEGER,amount DECIMAL(18,2))");
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                1,
                "topn",
                "A",
                "x",
                "01",
                1,
                100);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                2,
                "topn",
                "A",
                "x",
                "02",
                1,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                3,
                "topn",
                "B",
                "x",
                "01",
                1,
                60);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                4,
                "topn",
                "B",
                "x",
                "02",
                1,
                60);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                5,
                "having",
                "A",
                "x",
                "01",
                1,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                6,
                "having",
                "A",
                "x",
                "02",
                1,
                20);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                7,
                "having",
                "B",
                "x",
                "01",
                1,
                15);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                8,
                "having",
                "B",
                "x",
                "02",
                1,
                15);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                9,
                "share",
                "P",
                "a",
                "01",
                1,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                10,
                "share",
                "P",
                "b",
                "01",
                1,
                20);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                11,
                "missing-base",
                "P",
                "x",
                "02",
                1,
                50);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                12,
                "missing-base",
                "Q",
                "x",
                "01",
                1,
                30);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                13,
                "missing-base",
                "Q",
                "x",
                "02",
                1,
                60);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                14,
                "ordered-base",
                "P",
                "x",
                "01",
                1,
                40);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                15,
                "ordered-base",
                "P",
                "x",
                "02",
                1,
                50);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                16,
                "tree",
                "P",
                "x",
                "01",
                1,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                17,
                "tree",
                "P",
                "x",
                "01",
                2,
                40);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                18,
                "tree",
                "P",
                "x",
                "01",
                3,
                130);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                19,
                "tree",
                "P",
                "x",
                "01",
                4,
                0);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                20,
                "tree-mixed",
                "P",
                "x",
                "01",
                1,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                21,
                "tree-mixed",
                "Q",
                "x",
                "01",
                1,
                20);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                22,
                "cycle",
                "P",
                "x",
                "01",
                10,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                23,
                "cycle",
                "P",
                "x",
                "01",
                11,
                20);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                24,
                "null-key",
                null,
                "x",
                "01",
                1,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                25,
                "null-key",
                null,
                "x",
                "02",
                1,
                20);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                26,
                "null-key",
                "null",
                "x",
                "01",
                1,
                100);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                27,
                "null-key",
                "null",
                "x",
                "02",
                1,
                300);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                28,
                "null-share",
                null,
                "a",
                "01",
                1,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                29,
                "null-share",
                null,
                "b",
                "01",
                1,
                30);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                30,
                "null-share",
                "null",
                "a",
                "01",
                1,
                20);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                31,
                "null-share",
                "null",
                "b",
                "01",
                1,
                20);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                32,
                "tree-key",
                "P",
                "c",
                "a|b",
                1,
                10);
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                33,
                "tree-key",
                "P",
                "b|c",
                "a",
                1,
                20);
        jdbcTemplate.update(
                "INSERT INTO "
                        + TABLE
                        + " VALUES(101,'tree-columns','P','x','01',1,10),(102,'tree-columns','P','x','02',1,20),(103,'tree-columns','P','x','01',2,5),(104,'tree-columns','P','x','02',2,6)");
        jdbcTemplate.update(
                "INSERT INTO "
                        + TABLE
                        + " VALUES(201,'independent-tree','P','x','02',1,0),(202,'independent-tree','P','x','01',2,1000),(203,'independent-tree','P','x','02',3,600),(204,'independent-tree','P','x','03',3,600)");
        int uniqueId = 300;
        for (String month : List.of("01", "02")) {
            for (String leaf :
                    (month.equals("01") ? List.of("a", "b", "c") : List.of("d", "e", "f")))
                jdbcTemplate.update(
                        "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                        uniqueId++,
                        "distinct",
                        "A",
                        leaf,
                        month,
                        1,
                        1);
            for (String leaf : List.of("u", "v", "w", "x"))
                jdbcTemplate.update(
                        "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                        uniqueId++,
                        "distinct",
                        "B",
                        leaf,
                        month,
                        1,
                        1);
        }
        jdbcTemplate.execute(
                "CREATE TABLE pivot_unified_branches(id INTEGER PRIMARY KEY,node_id INTEGER,title VARCHAR(30))");
        jdbcTemplate.update(
                "INSERT INTO pivot_unified_branches VALUES(1,1,'outer-root'),(2,2,'outer-left'),(3,3,'outer-right'),(4,4,'outer-leaf')");
        Files.createDirectories(root);
        Files.writeString(
                root.resolve("PivotUnified.tm"),
                "export const model={name:'PivotUnified',tableName:'pivot_unified_facts',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'scenario',column:'scenario',type:'STRING'},{name:'category',column:'category',type:'STRING'},{name:'leaf',column:'leaf',type:'STRING'},{name:'yearMonth',column:'ym',type:'STRING'}],dimensions:[{name:'node',tableName:'pivot_unified_nodes',foreignKey:'node_id',primaryKey:'id',captionColumn:'caption',properties:[{name:'parentId',column:'parent_id',type:'INTEGER'},{name:'title',column:'title',type:'STRING'}]}],measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'},{name:'avgAmount',column:'amount',type:'NUMBER',aggregation:'avg'},{name:'minAmount',column:'amount',type:'NUMBER',aggregation:'min'},{name:'maxAmount',column:'amount',type:'NUMBER',aggregation:'max'},{name:'uniqueLeaf',column:'leaf',type:'NUMBER',aggregation:'count_distinct'}]};");
        Files.writeString(
                root.resolve("PivotUnifiedBase.qm"),
                "const m=loadTableModel('PivotUnified');export const queryModel={name:'PivotUnifiedBase',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.scenario},{ref:m.category},{ref:m.leaf},{ref:m.yearMonth},{ref:m.node},{ref:m.amount},{ref:m.avgAmount},{ref:m.minAmount},{ref:m.maxAmount},{ref:m.uniqueLeaf},{name:'doubleValue',type:'NUMBER',formula:'sum(amount)*2'},{name:'averageCalc',type:'NUMBER',formula:'sum(amount)*1.0/count(id)'}]}],accesses:[]};");
        Files.writeString(
                root.resolve("PivotUnifiedRenamed.qm"),
                "const m=loadTableModel('PivotUnified');export const queryModel={name:'PivotUnifiedRenamed',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.scenario},{ref:m.category},{ref:m.yearMonth},{ref:m.amount},{ref:m.node$id,name:'nodeKey',alias:'nodeKeyOut'},{ref:m.node$caption,name:'nodeName',alias:'nodeNameOut'},{ref:m.node$title,name:'nodeTitle',alias:'nodeTitleOut'},{ref:m.node$parentId,name:'parentKey',alias:'parentKeyOut'}]}],accesses:[]};");
        Files.writeString(
                root.resolve("PivotNested.tm"),
                """
            export const model={name:'PivotNested',tableName:'pivot_unified_facts',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'scenario',column:'scenario',type:'STRING'}],dimensions:[{name:'branch',tableName:'pivot_unified_branches',foreignKey:'node_id',primaryKey:'id',captionColumn:'title',properties:[{name:'title',column:'title',type:'STRING'}],dimensions:[{name:'child',tableName:'pivot_unified_nodes',foreignKey:'node_id',primaryKey:'id',captionColumn:'caption',properties:[{name:'title',column:'title',type:'STRING'}]}]}],measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'}]};
            """);
        Files.writeString(
                root.resolve("PivotUnifiedNested.qm"),
                """
            const m=loadTableModel('PivotNested');export const queryModel={name:'PivotUnifiedNested',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.scenario},{ref:m.amount},{ref:m.branch.child$id,name:'nestedKey',alias:'nestedKeyOut'},{ref:m.branch.child$title,name:'nestedTitle',alias:'nestedTitleOut'}]}],accesses:[]};
            """);
        assertTrue(bundles.addExternalBundle(BUNDLE, "", root.toString(), false));
    }

    @AfterAll
    void cleanup() {
        bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("");
        jdbcTemplate.execute("DROP TABLE pivot_unified_branches");
        jdbcTemplate.execute("DROP TABLE " + TABLE);
        jdbcTemplate.execute("DROP TABLE " + NODES);
    }

    Map<String, Object> request(List<?> rows, List<?> columns, List<?> metrics, String format) {
        Map<String, Object> pivot = new LinkedHashMap<>();
        pivot.put("rows", rows);
        if (columns != null) pivot.put("columns", columns);
        pivot.put("metrics", metrics);
        pivot.put("outputFormat", format);
        pivot.put("options", Map.of("grandTotal", false));
        return new LinkedHashMap<>(Map.of("pivot", pivot));
    }

    Map<String, Object> basic(String format) {
        return request(List.of("category"), List.of("month"), List.of("amount"), format);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> pivot(Map<String, Object> request) {
        return (Map<String, Object>) request.get("pivot");
    }

    SemanticQueryResponse call(
            String model,
            Map<String, Object> request,
            String mode,
            SemanticRequestContext context) {
        var response =
                service.queryModel(
                        "PivotUnified" + model, mapper.toQueryRequest(request), mode, context);
        String evidence = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        if (evidence != null) {
            try {
                Path directory =
                        Files.createDirectories(
                                Path.of(evidence).resolve("pivot-unified-calls"));
                Files.writeString(
                        directory.resolve(
                                evidenceSequence.incrementAndGet()
                                        + "-"
                                        + model
                                        + "-"
                                        + mode
                                        + ".json"),
                        json.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(
                                        Map.of(
                                                "model",
                                                model,
                                                "mode",
                                                mode,
                                                "request",
                                                request,
                                                "response",
                                                response)));
            } catch (java.io.IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
        }
        return response;
    }

    SemanticQueryResponse checked(
            String model, Map<String, Object> request, SemanticRequestContext context) {
        assertNotNull(call(model, request, "validate", context));
        assertNotNull(
                service.validateQuery(
                        "PivotUnified" + model, mapper.toQueryRequest(request), context));
        return call(model, request, "execute", context);
    }

    SemanticQueryResponse checked(Map<String, Object> request) {
        return checked("Base", request, SemanticRequestContext.empty());
    }

    void rejected(String model, Map<String, Object> request, SemanticRequestContext context) {
        for (String mode : List.of("validate", "execute"))
            assertThrows(
                    RuntimeException.class,
                    () -> call(model, request, mode, context),
                    mode + ": " + request);
        assertThrows(
                RuntimeException.class,
                () ->
                        service.validateQuery(
                                "PivotUnified" + model, mapper.toQueryRequest(request), context));
    }

    void rejected(Map<String, Object> request) {
        rejected("Base", request, SemanticRequestContext.empty());
    }

    SemanticRequestContext deny(String table, String column) {
        return SemanticRequestContext.ofDeniedColumns(
                null, null, List.of(new DeniedPhysicalColumn(null, table, column)));
    }

    void number(String expected, Object actual) {
        assertNotNull(actual);
        assertEquals(
                0,
                new BigDecimal(expected).compareTo(new BigDecimal(actual.toString())),
                String.valueOf(actual));
    }

    Map<String, Map<String, Object>> index(List<Map<String, Object>> rows, String... fields) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (var row : rows) {
            String key =
                    String.join(
                            "/",
                            Arrays.stream(fields).map(f -> String.valueOf(row.get(f))).toList());
            assertNull(result.put(key, row), "duplicate cell: " + key);
        }
        return result;
    }

    Map<String, Object> p(
            String scenario, List<?> rows, List<?> cols, List<?> metrics, String format) {
        var request = request(rows, cols, metrics, format);
        request.put("slice", List.of(Map.of("field", "scenario", "op", "=", "value", scenario)));
        return request;
    }

    Map<String, Object> ratio(String name, String base) {
        return Map.of(
                "name",
                name,
                "type",
                "baselineRatio",
                "of",
                base,
                "axis",
                "columns",
                "baseline",
                "first");
    }

    Map<String, Object> share(String base) {
        return Map.of(
                "name",
                "share",
                "type",
                "parentShare",
                "of",
                base,
                "axis",
                "rows",
                "level",
                "leaf",
                "parentLevel",
                "category");
    }

    Map<String, Object> tree(String scenario, Object axis) {
        return p(scenario, List.of(axis), null, List.of("amount"), "tree");
    }

    @SuppressWarnings("unchecked")
    List<PivotResult.TreeNode> treeNodes(SemanticQueryResponse response) {
        return (List<PivotResult.TreeNode>) response.getItems().get(0).get("data");
    }

    @Test
    void nonAdditiveTopNRequeriesExactMemberGrainAndRetainsAllCells() {
        for (boolean offset : List.of(false, true)) {
            var axis =
                    new LinkedHashMap<String, Object>(
                            Map.of(
                                    "field",
                                    "category",
                                    "limit",
                                    1,
                                    "orderBy",
                                    List.of("-avgAmount")));
            if (offset) axis.put("start", 0);
            var req = p("topn", List.of(axis), List.of("yearMonth"), List.of("avgAmount"), "flat");
            var result = index(checked(req).getItems(), "category", "yearMonth");
            assertEquals(Set.of("B/01", "B/02"), result.keySet());
            for (var row : result.values()) number("60", row.get("avgAmount"));
            rejected("Base", req, deny(TABLE, "amount"));
        }
    }

    @Test
    void distinctRankingUsesGlobalDistinctMembersRatherThanCellCounts() {
        var req =
                p(
                        "distinct",
                        List.of(
                                Map.of(
                                        "field",
                                        "category",
                                        "limit",
                                        1,
                                        "orderBy",
                                        List.of("-uniqueLeaf"))),
                        List.of("yearMonth"),
                        List.of("uniqueLeaf"),
                        "flat");
        var result = index(checked(req).getItems(), "category", "yearMonth");
        assertEquals(Set.of("A/01", "A/02"), result.keySet());
        for (var row : result.values()) number("3", row.get("uniqueLeaf"));
        rejected("Base", req, deny(TABLE, "leaf"));
    }

    @Test
    void sortingCannotReadCoordinatesOutsideItsMemberGrain() {
        var req =
                p(
                        "topn",
                        List.of(
                                Map.of(
                                        "field",
                                        "category",
                                        "limit",
                                        1,
                                        "orderBy",
                                        List.of("yearMonth"))),
                        List.of("yearMonth"),
                        List.of("amount"),
                        "flat");
        rejected(req);
        req =
                p(
                        "share",
                        List.of(
                                Map.of("field", "category", "limit", 1, "orderBy", List.of("leaf")),
                                "leaf"),
                        null,
                        List.of("amount"),
                        "flat");
        rejected(req);
    }

    @Test
    void propertyLookupUsesTheRenamedPrimaryKeyAndPropertyRoles() {
        var req = p("tree", List.of("nodeKey"), null, List.of("amount"), "flat");
        pivot(req).put("properties", List.of("node$title"));
        var result =
                index(
                        checked("Renamed", req, SemanticRequestContext.empty()).getItems(),
                        "nodeKey");
        assertEquals("root-title", result.get("1").get("node$title"));
        rejected("Renamed", req, deny(NODES, "title"));
    }

    @Test
    void nestedPropertyUsesItsOwnerBindingAndDoesNotDemandUnrelatedParentProperties() {
        var req = p("tree", List.of("nestedKey"), null, List.of("amount"), "flat");
        pivot(req).put("properties", List.of("branch.child$title"));
        var result =
                index(
                        checked("Nested", req, SemanticRequestContext.empty()).getItems(),
                        "nestedKey");
        assertEquals("root-title", result.get("1").get("branch.child$title"));
        rejected("Nested", req, deny(NODES, "title"));
        checked("Nested", req, deny("pivot_unified_branches", "title"));
    }

    @Test
    void maxHavingIsInvariantAcrossPushdownAndMemory() {
        var axes =
                List.of(
                        Map.of(
                                "field",
                                "category",
                                "having",
                                List.of(Map.of("metric", "maxAmount", "op", ">=", "value", 25))));
        for (var metrics :
                List.of(List.of("maxAmount"), List.of("maxAmount", ratio("index", "maxAmount"))))
            assertTrue(
                    checked(p("having", axes, List.of("yearMonth"), metrics, "flat"))
                            .getItems()
                            .isEmpty());
    }

    @Test
    void parentDenominatorsFollowDeclaredMinMaxAndUnknownComputedAggregationIsRejected() {
        for (String metric : List.of("minAmount", "maxAmount")) {
            var req =
                    p(
                            "share",
                            List.of("category", "leaf"),
                            null,
                            List.of(metric, share(metric)),
                            "flat");
            var rows = index(checked(req).getItems(), "leaf");
            number(metric.equals("minAmount") ? "1" : "0.5", rows.get("a").get("share"));
            number(metric.equals("minAmount") ? "2" : "1", rows.get("b").get("share"));
        }
        rejected(
                p(
                        "share",
                        List.of("category", "leaf"),
                        null,
                        List.of("averageCalc", share("averageCalc")),
                        "flat"));
    }

    @Test
    void missingPrePageBaselineDoesNotFallBackToTheCurrentPage() {
        var derived = new LinkedHashMap<String, Object>(ratio("index", "amount"));
        derived.put("baselineScope", "prePageAxisDomain");
        var req =
                p(
                        "missing-base",
                        List.of("category"),
                        List.of(Map.of("field", "yearMonth", "start", 1, "limit", 1)),
                        List.of("amount", derived),
                        "flat");
        var rows = index(checked(req).getItems(), "category");
        assertNull(rows.get("P").get("index"));
        number("2", rows.get("Q").get("index"));
    }

    @Test
    void naturalBaselineIsIndependentOfWindowAndDescendingDisplayOrder() {
        for (boolean paged : List.of(false, true)) {
            var derived = new LinkedHashMap<String, Object>(ratio("index", "amount"));
            var col =
                    new LinkedHashMap<String, Object>(
                            Map.of(
                                    "field",
                                    "yearMonth",
                                    "limit",
                                    2,
                                    "orderBy",
                                    List.of("-yearMonth")));
            if (paged) {
                derived.put("baselineScope", "prePageAxisDomain");
                col.put("start", 0);
            }
            var result =
                    index(
                            checked(
                                            p(
                                                    "ordered-base",
                                                    List.of("category"),
                                                    List.of(col),
                                                    List.of("amount", derived),
                                                    "flat"))
                                    .getItems(),
                            "yearMonth");
            number("1", result.get("01").get("index"));
            number("1.25", result.get("02").get("index"));
        }
    }

    @Test
    void computedTotalsCompileAtTheirOwnGrainAndRetainPermissions() {
        var req =
                p(
                        "topn",
                        List.of("category"),
                        List.of("yearMonth"),
                        List.of("doubleValue"),
                        "flat");
        pivot(req).put("options", Map.of("grandTotal", true));
        var result = index(checked(req).getItems(), "category", "yearMonth");
        number("320", result.get("GRAND_TOTAL/01").get("doubleValue"));
        number("140", result.get("GRAND_TOTAL/02").get("doubleValue"));
        rejected("Base", req, deny(TABLE, "amount"));
    }

    @Test
    void calculatedNullTotalsRemainNull() {
        var req =
                p("topn", List.of("category"), List.of("yearMonth"), List.of("zeroRatio"), "flat");
        req.put(
                "calculatedFields",
                List.of(
                        Map.of(
                                "name",
                                "zeroRatio",
                                "expression",
                                "sum(amount)/nullif(sum(amount)-sum(amount),0)")));
        pivot(req).put("options", Map.of("grandTotal", true));
        for (var row : checked(req).getItems()) assertNull(row.get("zeroRatio"));
    }

    @Test
    void outputCollisionsAreRejectedBeforeQuery() {
        rejected(
                p(
                        "ordered-base",
                        List.of("category"),
                        List.of("yearMonth"),
                        List.of("amount", ratio("category", "amount")),
                        "flat"));
        rejected(
                p(
                        "ordered-base",
                        List.of("category", "category"),
                        List.of("yearMonth"),
                        List.of("amount"),
                        "flat"));
    }

    @Test
    void treeWindowsArePerParentDepthIsPresentationAndCyclesFailAtExecution() {
        var req =
                tree(
                        "tree",
                        Map.of(
                                "field",
                                "node$caption",
                                "hierarchyMode",
                                "tree",
                                "limit",
                                1,
                                "orderBy",
                                List.of("-amount")));
        var roots = treeNodes(checked(req));
        assertEquals(1, roots.size());
        number("10", roots.get(0).getCells().get("amount"));
        assertEquals("Right", roots.get(0).getChildren().get(0).getNode().get("node$caption"));
        assertEquals(1, roots.get(0).getChildren().size());
        roots =
                treeNodes(
                        checked(
                                tree(
                                        "tree",
                                        Map.of(
                                                "field",
                                                "node$caption",
                                                "hierarchyMode",
                                                "tree",
                                                "expandDepth",
                                                0))));
        assertEquals(1, roots.size());
        assertTrue(roots.get(0).getChildren() == null || roots.get(0).getChildren().isEmpty());
        number("10", roots.get(0).getCells().get("amount"));
        req = tree("cycle", Map.of("field", "node$caption", "hierarchyMode", "tree"));
        assertNotNull(call("Base", req, "validate", SemanticRequestContext.empty()));
        var cycle = req;
        assertThrows(
                RuntimeException.class,
                () -> call("Base", cycle, "execute", SemanticRequestContext.empty()));
        rejected(
                p(
                        "tree-mixed",
                        List.of(
                                "category",
                                Map.of("field", "node$caption", "hierarchyMode", "tree")),
                        null,
                        List.of("amount"),
                        "tree"));
    }

    @Test
    void treeColumnWindowsStillSelectCompleteNodeCells() {
        var req =
                p(
                        "tree-columns",
                        List.of(Map.of("field", "node$caption", "hierarchyMode", "tree")),
                        List.of(
                                Map.of(
                                        "field",
                                        "yearMonth",
                                        "limit",
                                        1,
                                        "orderBy",
                                        List.of("-amount"))),
                        List.of("amount"),
                        "tree");
        var rootNode = treeNodes(checked(req)).get(0);
        assertEquals(Set.of("02|amount"), rootNode.getCells().keySet());
        number("20", rootNode.getCells().get("02|amount"));
        number("6", rootNode.getChildren().get(0).getCells().get("02|amount"));
    }

    @Test
    void rowAndColumnTreeDomainsAreChosenFromTheSameFactScope() {
        var req =
                p(
                        "independent-tree",
                        List.of(
                                Map.of(
                                        "field",
                                        "node$caption",
                                        "hierarchyMode",
                                        "tree",
                                        "limit",
                                        1,
                                        "orderBy",
                                        List.of("-amount"))),
                        List.of(
                                Map.of(
                                        "field",
                                        "yearMonth",
                                        "limit",
                                        1,
                                        "orderBy",
                                        List.of("-amount"))),
                        List.of("amount"),
                        "tree");
        assertTrue(treeNodes(checked(req)).isEmpty());
    }

    @Test
    void renamedTreeBindsHiddenRolesFromMetadataAndChecksPhysicalPermissions() {
        var req = tree("tree", Map.of("field", "nodeName", "hierarchyMode", "tree"));
        var roots = treeNodes(checked("Renamed", req, SemanticRequestContext.empty()));
        assertEquals("Root", roots.get(0).getNode().get("nodeName"));
        assertEquals(2, roots.get(0).getChildren().size());
        rejected("Renamed", req, deny(NODES, "parent_id"));
        rejected(
                "Renamed",
                req,
                SemanticRequestContext.of(
                        null,
                        (ModelResultContext.SecurityContext) null,
                        Set.of("nodeName", "nodeKey", "amount", "scenario")));
        checked(
                "Renamed",
                req,
                SemanticRequestContext.of(
                        null,
                        (ModelResultContext.SecurityContext) null,
                        Set.of("nodeName", "nodeKey", "parentKey", "amount", "scenario")));
    }

    @Test
    void nullMembersAndLiteralNullUseDifferentRatioCoordinates() {
        var req =
                p(
                        "null-key",
                        List.of("category"),
                        List.of("yearMonth"),
                        List.of("amount", ratio("index", "amount")),
                        "flat");
        for (var row : checked(req).getItems())
            if (row.get("category") == null)
                number(row.get("yearMonth").equals("01") ? "1" : "2", row.get("index"));
        req =
                p(
                        "null-share",
                        List.of("category", "leaf"),
                        null,
                        List.of("amount", share("amount")),
                        "flat");
        for (var row : checked(req).getItems())
            number(
                    row.get("category") == null
                            ? (row.get("leaf").equals("a") ? "0.25" : "0.75")
                            : "0.5",
                    row.get("share"));
    }

    @Test
    void specialTreeCellLabelsCannotOverwriteEachOther() {
        var req =
                p(
                        "tree-key",
                        List.of(Map.of("field", "node$caption", "hierarchyMode", "tree")),
                        List.of("yearMonth", "leaf"),
                        List.of("amount"),
                        "tree");
        var cells = treeNodes(checked(req)).get(0).getCells();
        assertEquals(2, cells.size());
        assertEquals(
                Set.of("10", "20"),
                cells.values().stream()
                        .map(v -> new BigDecimal(v.toString()).stripTrailingZeros().toPlainString())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void validatePreparesNoDataSqlIncludingRollupsAndMemberGrains() throws Exception {
        var model =
                (JdbcQueryModelImpl) queryModelLoader.getJdbcQueryModel("PivotUnifiedBase", null);
        var original = model.getDataSource();
        AtomicInteger prepared = new AtomicInteger(), executed = new AtomicInteger();
        model.setDataSource(
                new DelegatingDataSource(original) {
                    @Override
                    public Connection getConnection() throws SQLException {
                        return observe(super.getConnection(), prepared, executed);
                    }
                });
        try {
            var total =
                    p(
                            "topn",
                            List.of("category"),
                            List.of("yearMonth"),
                            List.of("doubleValue"),
                            "flat");
            pivot(total).put("options", Map.of("grandTotal", true));
            var top =
                    p(
                            "topn",
                            List.of(
                                    Map.of(
                                            "field",
                                            "category",
                                            "limit",
                                            1,
                                            "orderBy",
                                            List.of("-avgAmount"))),
                            List.of("yearMonth"),
                            List.of("avgAmount"),
                            "flat");
            for (var req :
                    List.of(
                            total,
                            top,
                            tree(
                                    "tree",
                                    Map.of("field", "node$caption", "hierarchyMode", "tree")))) {
                call("Base", req, "validate", SemanticRequestContext.empty());
                service.validateQuery(
                        "PivotUnifiedBase",
                        mapper.toQueryRequest(req),
                        SemanticRequestContext.empty());
            }
            rejected(
                    "Base",
                    tree("tree", Map.of("field", "node$caption", "hierarchyMode", "tree")),
                    deny(NODES, "parent_id"));
            assertEquals(0, prepared.get());
            assertEquals(0, executed.get());
            call("Base", total, "execute", SemanticRequestContext.empty());
            assertTrue(prepared.get() > 0);
            assertTrue(executed.get() > 0);
        } finally {
            model.setDataSource(original);
        }
    }

    Connection observe(Connection connection, AtomicInteger prepared, AtomicInteger executed) {
        return (Connection)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, args) -> {
                            try {
                                Object result = method.invoke(connection, args);
                                if (result instanceof Statement statement) {
                                    String sql =
                                            args != null
                                                            && args.length > 0
                                                            && args[0] instanceof String text
                                                    ? text
                                                    : "";
                                    if (businessSql(sql)) prepared.incrementAndGet();
                                    Class<?> type =
                                            result instanceof PreparedStatement
                                                    ? PreparedStatement.class
                                                    : Statement.class;
                                    return Proxy.newProxyInstance(
                                            getClass().getClassLoader(),
                                            new Class<?>[] {type},
                                            (p, m, a) -> {
                                                String statementSql =
                                                        a != null
                                                                        && a.length > 0
                                                                        && a[0]
                                                                                instanceof
                                                                                String text
                                                                ? text
                                                                : sql;
                                                if (m.getName().startsWith("execute")
                                                        && businessSql(statementSql))
                                                    executed.incrementAndGet();
                                                try {
                                                    return m.invoke(statement, a);
                                                } catch (InvocationTargetException error) {
                                                    throw error.getCause();
                                                }
                                            });
                                }
                                return result;
                            } catch (InvocationTargetException error) {
                                throw error.getCause();
                            }
                        });
    }

    boolean businessSql(String sql) {
        return sql.toLowerCase(Locale.ROOT).contains(TABLE)
                || sql.toLowerCase(Locale.ROOT).contains(NODES);
    }
}

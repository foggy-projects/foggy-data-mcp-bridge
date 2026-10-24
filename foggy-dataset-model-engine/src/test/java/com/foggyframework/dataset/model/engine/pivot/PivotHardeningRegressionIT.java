package com.foggyframework.dataset.model.engine.pivot;

import static org.junit.jupiter.api.Assertions.*;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
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

/**
 * Unified Pivot contracts: exact member grains, bound auxiliary execution and collision-free
 * coordinates.
 */
@TestPropertySource(
        properties = {
            "pivot.hardening.regression=true",
            "foggy.dataset.pivot.outer-cache.enabled=true"
        })
@org.springframework.context.annotation.Import(PivotHardeningRegressionIT.AuditBindings.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PivotHardeningRegressionIT extends EcommerceTestSupport {
    @Resource SemanticQueryServiceV3 service;

    @Resource
    com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator coordinator;

    @Resource SemanticQueryPayloadMapper mapper;
    @Resource SystemBundlesContext bundles;
    @Resource com.fasterxml.jackson.databind.ObjectMapper json;
    final AtomicInteger evidenceSequence = new AtomicInteger();
    @TempDir static Path root;
    static final String TABLE = "pivot_unified_facts",
            NODES = "pivot_unified_nodes",
            BUNDLE = "pivot-hardening-regression";

    @BeforeAll
    void setup() throws Exception {
        jdbcTemplate.execute(
                "CREATE TABLE "
                        + NODES
                        + " (id INTEGER PRIMARY KEY,parent_id INTEGER,caption VARCHAR(30),title"
                        + " VARCHAR(30))");
        jdbcTemplate.execute(
                "INSERT INTO "
                        + NODES
                        + " VALUES(1,NULL,'Root','root-title'),(2,1,'Left','left-title'),(3,1,'Right','right-title'),(4,2,'Leaf','leaf-title'),(10,11,'CycleA','cycle-a'),(11,10,'CycleB','cycle-b')");
        jdbcTemplate.execute(
                "CREATE TABLE "
                        + TABLE
                        + " (id INTEGER PRIMARY KEY,scenario VARCHAR(30),category VARCHAR(30),leaf"
                        + " VARCHAR(30),ym VARCHAR(30),node_id INTEGER,amount DECIMAL(18,2))");
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
                "CREATE TABLE pivot_unified_branches(id INTEGER PRIMARY KEY,node_id INTEGER,title"
                        + " VARCHAR(30))");
        jdbcTemplate.update(
                "INSERT INTO pivot_unified_branches"
                    + " VALUES(1,1,'outer-root'),(2,2,'outer-left'),(3,3,'outer-right'),(4,4,'outer-leaf')");
        Files.createDirectories(root);
        Files.writeString(
                root.resolve("PivotUnified.tm"),
                "export const"
                    + " model={name:'PivotUnified',dataSourceName:'audit-fixed',tableName:'pivot_unified_facts',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'scenario',column:'scenario',type:'STRING'},{name:'category',column:'category',type:'STRING'},{name:'leaf',column:'leaf',type:'STRING'},{name:'yearMonth',column:'ym',type:'STRING'}],dimensions:[{name:'node',tableName:'pivot_unified_nodes',foreignKey:'node_id',primaryKey:'id',captionColumn:'caption',properties:[{name:'parentId',column:'parent_id',type:'INTEGER'},{name:'title',column:'title',type:'STRING'}]}],measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'},{name:'avgAmount',column:'amount',type:'NUMBER',aggregation:'avg'},{name:'minAmount',column:'amount',type:'NUMBER',aggregation:'min'},{name:'maxAmount',column:'amount',type:'NUMBER',aggregation:'max'},{name:'uniqueLeaf',column:'leaf',type:'NUMBER',aggregation:'count_distinct'}]};");
        Files.writeString(
                root.resolve("PivotUnifiedBase.qm"),
                "const m=loadTableModel('PivotUnified');export const"
                    + " queryModel={name:'PivotUnifiedBase',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.scenario},{ref:m.category},{ref:m.leaf},{ref:m.yearMonth},{ref:m.node},{ref:m.amount},{ref:m.avgAmount},{ref:m.minAmount},{ref:m.maxAmount},{ref:m.uniqueLeaf},{name:'doubleValue',type:'NUMBER',formula:'sum(amount)*2'},{name:'averageCalc',type:'NUMBER',formula:'sum(amount)*1.0/count(id)'}]}],accesses:[]};");
        Files.writeString(
                root.resolve("PivotUnifiedRenamed.qm"),
                "const m=loadTableModel('PivotUnified');export const"
                    + " queryModel={name:'PivotUnifiedRenamed',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.scenario},{ref:m.category},{ref:m.yearMonth},{ref:m.amount},{ref:m.node$id,name:'nodeKey',alias:'nodeKeyOut'},{ref:m.node$caption,name:'nodeName',alias:'nodeNameOut'},{ref:m.node$title,name:'nodeTitle',alias:'nodeTitleOut'},{ref:m.node$parentId,name:'parentKey',alias:'parentKeyOut'}]}],accesses:[]};");
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

        String prefix = "const m=loadTableModel('PivotUnified');export const queryModel={name:'";
        Files.writeString(
                root.resolve("PivotUnifiedDynamic.qm"),
                prefix
                        + "PivotUnifiedDynamic',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.scenario},{ref:m.category},{ref:m.yearMonth},{ref:m.amount}]}],fieldPermissions:{defaultVisible:false,visibleFields:[{fields:['scenario','category','yearMonth']},{when:{hasAnyRole:['finance']},fields:['amount']}]},accesses:[]};");
        Files.writeString(
                root.resolve("PivotUnifiedMultiKey.qm"),
                prefix
                        + "PivotUnifiedMultiKey',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.scenario},{ref:m.amount},{ref:m.node$id,name:'keyOne',alias:'oneOut'},{ref:m.node$id,name:'keyTwo',alias:'twoOut'},{ref:m.node$title,name:'titleName',alias:'titleOut'}]}],accesses:[]};");

        Files.writeString(
                root.resolve("PivotTmPermission.tm"),
                "export const"
                    + " model={name:'PivotTmPermission',dataSourceName:'audit-fixed',tableName:'pivot_unified_facts',idColumn:'id',properties:[{name:'scenario',column:'scenario',type:'STRING'},{name:'category',column:'category',type:'STRING'},{name:'yearMonth',column:'ym',type:'STRING'}],measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'}],fieldPermissions:{hiddenFields:[{when:{hasAnyRole:['guest']},fields:['amount']}]}};");
        Files.writeString(
                root.resolve("PivotUnifiedTmDynamic.qm"),
                "const m=loadTableModel('PivotTmPermission');export const"
                    + " queryModel={name:'PivotUnifiedTmDynamic',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.scenario},{ref:m.category},{ref:m.yearMonth},{ref:m.amount}]}],accesses:[]};");
        Files.writeString(
                root.resolve("PivotUnifiedContextual.qm"),
                Files.readString(root.resolve("PivotUnifiedBase.qm"))
                        .replace("PivotUnifiedBase", "PivotUnifiedContextual"));
        Path multi = root.resolve("PivotUnifiedMultiKey.qm");
        Files.writeString(
                multi,
                Files.readString(multi)
                        .replace(
                                "accesses:[]",
                                "fieldPermissions:{hiddenFields:[{when:{hasAnyRole:['guest']},fields:['titleName']}]},accesses:[]"));

        assertTrue(bundles.addExternalBundle(BUNDLE, "pivot-hardening", root.toString(), false));
    }

    @AfterAll
    void cleanup() {
        bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("pivot-hardening");
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
                        Files.createDirectories(Path.of(evidence).resolve("pivot-hardening-calls"));
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
        return checked("Base", request, SemanticRequestContext.ofNamespace("pivot-hardening"));
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
        rejected("Base", request, SemanticRequestContext.ofNamespace("pivot-hardening"));
    }

    SemanticRequestContext deny(String table, String column) {
        return SemanticRequestContext.ofDeniedColumns(
                "pivot-hardening", null, List.of(new DeniedPhysicalColumn(null, table, column)));
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

    SemanticRequestContext role(String name) {
        return SemanticRequestContext.of(
                "pivot-hardening",
                ModelResultContext.SecurityContext.builder().roles(List.of(name)).build());
    }

    void record(String label, Object value) throws Exception {
        String folder = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        if (folder != null) {
            Path directory =
                    Files.createDirectories(Path.of(folder).resolve("pivot-hardening-results"));
            Files.writeString(
                    directory.resolve(label + ".json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(value));
        }
    }

    boolean event(SemanticQueryResponse response, String event) {
        return response.getDebug() != null
                && String.valueOf(response.getDebug().getExtra()).contains(event);
    }

    @Test
    @Order(1)
    void cacheSealsTmQmFieldsAuxiliaryPermissionsAndContextualPredicates() throws Exception {
        var req = p("topn", List.of("category"), List.of("yearMonth"), List.of("amount"), "flat");
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (String model : List.of("Dynamic", "TmDynamic")) {
            rejected(model, req, role("guest"));
            var first = call(model, req, "execute", role("finance"));
            assertTrue(event(first, "pivot.cache.store"));
            var hit = call(model, req, "execute", role("finance"));
            assertTrue(event(hit, "pivot.cache.hit"));
            assertEquals(4, hit.getItems().size());
            rejected(model, req, role("guest"));
            evidence.put(model, hit);
        }
        var props = p("tree", List.of("keyTwo"), null, List.of("amount"), "flat");
        pivot(props).put("properties", List.of("node$title"));
        assertEquals(4, checked("MultiKey", props, role("finance")).getItems().size());
        assertTrue(event(call("MultiKey", props, "execute", role("finance")), "pivot.cache.hit"));
        rejected("MultiKey", props, role("guest"));
        var byCategory = p("topn", List.of("category"), null, List.of("amount"), "flat");
        for (String category : List.of("A", "B", "A")) {
            var context =
                    SemanticRequestContext.of(
                            "pivot-hardening",
                            ModelResultContext.SecurityContext.builder()
                                    .attributes(Map.of("auditCategory", category))
                                    .build());
            var result = call("Contextual", byCategory, "execute", context);
            assertEquals(1, result.getItems().size());
            assertEquals(category, result.getItems().get(0).get("category"));
            number(category.equals("A") ? "110" : "120", result.getItems().get(0).get("amount"));
            evidence.put("scope" + category, result);
        }
        String file = Files.readString(root.resolve("PivotUnifiedDynamic.qm"));
        Files.writeString(
                root.resolve("PivotUnifiedDynamic.qm"), file.replace("['finance']", "['nobody']"));
        coordinator.refresh(
                com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshRequest.namespace(
                        "pivot-hardening",
                        com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshTrigger
                                .RUNTIME_API));
        rejected("Dynamic", req, role("finance"));
        // The first dependency-rich Pivot call after refresh must retain its request pin.
        assertEquals(4, checked("MultiKey", props, role("finance")).getItems().size());
        record("permission-and-refresh", evidence);
    }

    @Test
    @Order(2)
    void everyDeclaredPrimaryProjectionProvesPropertyDependency() throws Exception {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (String key : List.of("keyOne", "keyTwo")) {
            var req = p("tree", List.of(key), null, List.of("amount"), "flat");
            pivot(req).put("properties", List.of("node$title"));
            var result =
                    checked("MultiKey", req, SemanticRequestContext.ofNamespace("pivot-hardening"));
            assertEquals(4, result.getItems().size());
            var values = index(result.getItems(), key);
            assertEquals("root-title", values.get("1").get("node$title"));
            evidence.put(key, result);
            rejected("MultiKey", req, deny(NODES, "title"));
        }
        var second = p("tree", List.of("keyTwo"), null, List.of("amount"), "flat");
        pivot(second).put("properties", List.of("node$title"));
        assertEquals(
                4,
                checked(
                                "MultiKey",
                                second,
                                SemanticRequestContext.of(
                                        "pivot-hardening",
                                        (ModelResultContext.SecurityContext) null,
                                        Set.of("scenario", "amount", "keyTwo", "node$title")))
                        .getItems()
                        .size());
        record("declared-projections", evidence);
    }

    @Test
    void incompleteDomainsFailInsteadOfProducingTruncatedTotalsOrBaselines() throws Exception {
        for (int i = 0; i < 1001; i++)
            jdbcTemplate.update(
                    "INSERT INTO " + TABLE + " VALUES(?,?,?,?,?,?,?)",
                    7000 + i,
                    "many-domain",
                    String.format("K%04d", i),
                    "x",
                    String.format("M%04d", i),
                    1,
                    i + 1);
        var domain =
                p(
                        "many-domain",
                        List.of(
                                Map.of(
                                        "field",
                                        "category",
                                        "domainSlice",
                                        List.of(Map.of("field", "id", "op", ">", "value", 0)))),
                        null,
                        List.of("amount"),
                        "flat");
        assertNotNull(
                call(
                        "Base",
                        domain,
                        "validate",
                        SemanticRequestContext.ofNamespace("pivot-hardening")));
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                call(
                                        "Base",
                                        domain,
                                        "execute",
                                        SemanticRequestContext.ofNamespace("pivot-hardening")));
        assertTrue(failure.getMessage().contains("PIVOT_AXIS_DOMAIN_LIMIT"));
        var br = new LinkedHashMap<>(ratio("index", "amount"));
        br.put("baseline", "last");
        br.put("baselineScope", "prePageAxisDomain");
        var baseline =
                p(
                        "many-domain",
                        List.of("leaf"),
                        List.of(Map.of("field", "yearMonth", "start", 1000, "limit", 1)),
                        List.of("amount", br),
                        "flat");
        assertNotNull(
                call(
                        "Base",
                        baseline,
                        "validate",
                        SemanticRequestContext.ofNamespace("pivot-hardening")));
        assertTrue(
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        call(
                                                "Base",
                                                baseline,
                                                "execute",
                                                SemanticRequestContext.ofNamespace(
                                                        "pivot-hardening")))
                        .getMessage()
                        .contains("PIVOT_AXIS_DOMAIN_LIMIT"));
        jdbcTemplate.update("DELETE FROM " + TABLE + " WHERE id=8000");
        assertEquals(1000, checked(domain).getItems().size());
        pivot(baseline)
                .put("columns", List.of(Map.of("field", "yearMonth", "start", 999, "limit", 1)));
        var result = checked(baseline);
        assertEquals(1, result.getItems().size());
        number("1", result.getItems().get(0).get("index"));
        record("complete-domain-boundary", result);
    }

    @Test
    void nullBucketsSurvivePagingAndCanUseAnExternalNonNullBaseline() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(6000,'null-column','P','x','01',1,10)");
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " VALUES(6001,'null-column','P','x',NULL,1,20)");
        var br = new LinkedHashMap<>(ratio("index", "amount"));
        br.put("baselineScope", "prePageAxisDomain");
        var req =
                p(
                        "null-column",
                        List.of("category"),
                        List.of(Map.of("field", "yearMonth", "start", 1, "limit", 1)),
                        List.of("amount", br),
                        "flat");
        var result = checked(req);
        assertEquals(1, result.getItems().size());
        assertNull(result.getItems().get(0).get("yearMonth"));
        number("2", result.getItems().get(0).get("index"));
        record("null-only-page", result);
        pivot(req).put("columns", List.of(Map.of("field", "yearMonth", "start", 0, "limit", 2)));
        var both = checked(req);
        assertEquals(2, both.getItems().size());
        assertEquals("01", both.getItems().get(0).get("yearMonth"));
        assertNull(both.getItems().get(1).get("yearMonth"));
        number("2", both.getItems().get(1).get("index"));
        var row = new LinkedHashMap<String, Object>();
        row.put("category", "P");
        row.put("yearMonth", null);
        row.put("amount", 20);
        var rows = new ArrayList<Map<String, Object>>();
        rows.add(row);
        var external = new LinkedHashMap<String, Number>();
        external.put(
                com.foggyframework.dataset.model.engine.pivot.algo.PivotCoordinates.key(
                        row, List.of("category")),
                10);
        com.foggyframework.dataset.model.engine.pivot.algo.BaselineRatioCalculator.apply(
                rows,
                mapper.toQueryRequest(req).getPivot(),
                List.of("category"),
                List.of("yearMonth"),
                Map.of("index", external));
        number("2", row.get("index"));
    }

    @Test
    void nullableTopNIsInvariantAcrossSqlMemoryAndDirection() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO "
                        + TABLE
                        + " VALUES(9000,'null-rank','A','x','01',1,NULL),(9001,'null-rank','B','x','01',1,10),(9002,'null-rank','C','x','01',1,20)");
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (String order : List.of("amount", "-amount"))
            for (boolean memory : List.of(false, true)) {
                var req =
                        p(
                                "null-rank",
                                List.of(
                                        Map.of(
                                                "field",
                                                "category",
                                                "limit",
                                                2,
                                                "orderBy",
                                                List.of(order))),
                                List.of("yearMonth"),
                                memory
                                        ? List.of("amount", ratio("index", "amount"))
                                        : List.of("amount"),
                                "flat");
                var result = checked(req);
                assertEquals(
                        order.startsWith("-") ? List.of("C", "B") : List.of("B", "C"),
                        result.getItems().stream().map(r -> r.get("category")).toList());
                evidence.put(order + memory, result);
            }
        record("nullable-ranks", evidence);
    }

    @Test
    void rowAndColumnOutputPreserveMemberRankWithoutLeakingInternalColumns() throws Exception {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (boolean memory : List.of(false, true)) {
            var req =
                    p(
                            "topn",
                            List.of(
                                    Map.of(
                                            "field",
                                            "category",
                                            "limit",
                                            2,
                                            "orderBy",
                                            List.of("-amount"))),
                            List.of("yearMonth"),
                            memory
                                    ? List.of("amount", ratio("index", "amount"))
                                    : List.of("amount"),
                            "flat");
            var result = checked(req);
            assertEquals(
                    List.of("B", "B", "A", "A"),
                    result.getItems().stream().map(r -> r.get("category")).toList());
            assertTrue(
                    result.getItems().stream()
                            .allMatch(
                                    r ->
                                            r.keySet().stream()
                                                    .noneMatch(k -> k.startsWith("_pivot_"))));
            evidence.put("flat" + memory, result);
        }
        var onlyOrder =
                p(
                        "topn",
                        List.of(Map.of("field", "category", "orderBy", List.of("-amount"))),
                        null,
                        List.of("amount"),
                        "flat");
        assertEquals(
                List.of("B", "A"),
                checked(onlyOrder).getItems().stream().map(r -> r.get("category")).toList());
        var tree =
                tree(
                        "tree",
                        Map.of(
                                "field",
                                "node$caption",
                                "hierarchyMode",
                                "tree",
                                "limit",
                                2,
                                "orderBy",
                                List.of("-amount")));
        var result = checked(tree);
        assertEquals(
                List.of("Right", "Left"),
                treeNodes(result).get(0).getChildren().stream()
                        .map(n -> n.getNode().get("node$caption"))
                        .toList());
        evidence.put("tree", result);
        record("output-order", evidence);
    }

    @Test
    void zeroWindowsRemainGovernedAndNeverExecuteDataIncludingValidateAndCacheHits()
            throws Exception {
        var model =
                (JdbcQueryModelImpl)
                        queryModelLoader.getJdbcQueryModel("PivotUnifiedBase", "pivot-hardening");
        DataSource original = model.getDataSource();
        AtomicInteger prepared = new AtomicInteger(), executed = new AtomicInteger();
        model.setDataSource(
                new DelegatingDataSource(original) {
                    @Override
                    public Connection getConnection() throws SQLException {
                        return observe(super.getConnection(), prepared, executed);
                    }
                });
        try {
            var full =
                    p("topn", List.of("category"), List.of("yearMonth"), List.of("amount"), "flat");
            assertNotNull(
                    call(
                            "Base",
                            full,
                            "validate",
                            SemanticRequestContext.ofNamespace("pivot-hardening")));
            assertEquals(0, prepared.get());
            assertEquals(0, executed.get());
            call("Base", full, "execute", SemanticRequestContext.ofNamespace("pivot-hardening"));
            assertTrue(executed.get() > 0);
            prepared.set(0);
            executed.set(0);
            assertTrue(
                    event(
                            call(
                                    "Base",
                                    full,
                                    "execute",
                                    SemanticRequestContext.ofNamespace("pivot-hardening")),
                            "pivot.cache.hit"));
            assertEquals(0, prepared.get());
            assertEquals(0, executed.get());
            for (String format : List.of("flat", "grid", "tree")) {
                var zero =
                        p(
                                "topn",
                                List.of(Map.of("field", "category", "start", 0, "limit", 0)),
                                null,
                                List.of("amount"),
                                format);
                assertNotNull(
                        call(
                                "Base",
                                zero,
                                "validate",
                                SemanticRequestContext.ofNamespace("pivot-hardening")));
                var result =
                        call(
                                "Base",
                                zero,
                                "execute",
                                SemanticRequestContext.ofNamespace("pivot-hardening"));
                assertEquals(0L, result.getTotal());
                rejected("Base", zero, deny(TABLE, "amount"));
            }
            var zeroTree =
                    tree(
                            "tree",
                            Map.of("field", "node$caption", "hierarchyMode", "tree", "limit", 0));
            assertNotNull(
                    call(
                            "Base",
                            zeroTree,
                            "validate",
                            SemanticRequestContext.ofNamespace("pivot-hardening")));
            assertEquals(
                    0L,
                    call(
                                    "Base",
                                    zeroTree,
                                    "execute",
                                    SemanticRequestContext.ofNamespace("pivot-hardening"))
                            .getTotal());
            assertEquals(0, prepared.get());
            assertEquals(0, executed.get());
            var invalid =
                    p(
                            "topn",
                            List.of(Map.of("field", "category", "limit", -1)),
                            null,
                            List.of("amount"),
                            "flat");
            rejected(invalid);
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

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class AuditBindings {
        @org.springframework.context.annotation.Bean
        com.foggyframework.dataset.model.plugins.result_set_filter.DataSetResultStep
                auditScopeStep() {
            return new com.foggyframework.dataset.model.plugins.result_set_filter
                    .DataSetResultStep() {
                public int beforeQueryOrder() {
                    return -17;
                }

                public int beforeQuery(ModelResultContext context) {
                    if ("PivotUnifiedContextual".equals(context.getCanonicalModelName())
                            && context.getSecurityContext() != null) {
                        Object category =
                                context.getSecurityContext().getAttributes() == null
                                        ? null
                                        : context.getSecurityContext()
                                                .getAttributes()
                                                .get("auditCategory");
                        if (category != null)
                            context.setSystemSlice(
                                    List.of(new SliceRequestDef("category", "=", category)));
                    }
                    return CONTINUE;
                }
            };
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        com.foggyframework.dataset.model.spi.NamedDataSourceResolver auditBinding(DataSource ds) {
            return new com.foggyframework.dataset.model.spi.NamedDataSourceResolver() {
                final com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity
                        identity =
                                new com.foggyframework.dataset.model.lifecycle.identity
                                        .DatasourceBindingIdentity(
                                        "audit-fixed",
                                        "audit-sqlite",
                                        new com.foggyframework.dataset.model.lifecycle.identity
                                                .DatasourceBindingGeneration("audit-generation-1"));

                public DataSource resolve(String name) {
                    return ds;
                }

                public DataSource resolveDefault(String ns) {
                    return ds;
                }

                public boolean isConfigured(String name) {
                    return true;
                }

                public com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding
                        resolveBinding(String name) {
                    return com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding
                            .tracked(ds, identity);
                }

                public com.foggyframework.dataset.model.lifecycle.port.ResolvedDatasourceBinding
                        resolveDefaultBinding(String ns) {
                    return resolveBinding("audit-fixed");
                }

                public com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness
                        currentness(
                                com.foggyframework.dataset.model.lifecycle.identity
                                                .DatasourceBindingIdentity
                                        captured) {
                    return identity.equals(captured)
                            ? com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness
                                    .CURRENT
                            : com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness
                                    .UNKNOWN;
                }

                public synchronized <T> T publishIfCurrent(
                        Collection<
                                        com.foggyframework.dataset.model.lifecycle.identity
                                                .DatasourceBindingIdentity>
                                captured,
                        java.util.function.Supplier<T> publication) {
                    assertTrue(captured.stream().allMatch(identity::equals));
                    return publication.get();
                }
            };
        }
    }
}

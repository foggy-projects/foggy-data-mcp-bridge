package com.foggyframework.dataset.model.semantic;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.impl.query.DbQueryOrderColumnImpl;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Real query compilation/execution; the same fixture runs on all five JDBC test profiles. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.test.context.TestPropertySource(properties = "issue145.test-isolation=true")
class DistinctOrderByRegressionIT extends EcommerceTestSupport {
    private static final String MODEL = "Issue145QueryModel";
    private static final String BUNDLE = "issue145-distinct";
    @Resource SemanticQueryServiceV3 service;
    @Resource SemanticQueryPayloadMapper mapper;
    @Resource SystemBundlesContext bundles;
    @TempDir static Path root;

    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE issue145_artist (id INTEGER PRIMARY KEY, name VARCHAR(50))");
        jdbcTemplate.execute("CREATE TABLE issue145_album (id INTEGER PRIMARY KEY, artist_id INTEGER)");
        jdbcTemplate.execute("CREATE TABLE issue145_track (id INTEGER PRIMARY KEY, album_id INTEGER)");
        jdbcTemplate.execute("CREATE TABLE issue145_line (id INTEGER PRIMARY KEY, track_id INTEGER, year_no INTEGER)");
        for (int id : List.of(3, 1, 4, 2)) {
            jdbcTemplate.update("INSERT INTO issue145_artist VALUES (?, ?)", id,
                    id == 3 ? "Zebra" : id == 4 ? "Alpha" : "Same");
            jdbcTemplate.update("INSERT INTO issue145_album VALUES (?, ?)", id, id);
            jdbcTemplate.update("INSERT INTO issue145_track VALUES (?, ?)", id, id);
        }
        int line = 1;
        for (int artist : List.of(3, 1, 3, 4, 2, 1)) {
            jdbcTemplate.update("INSERT INTO issue145_line VALUES (?, ?, ?)", line++, artist, 2012);
        }
        jdbcTemplate.update("INSERT INTO issue145_line VALUES (7, 3, 2013)");
        Files.createDirectories(root.resolve("model"));
        Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/Issue145Model.tm"), """
                export const model = {name:'Issue145Model',tableName:'issue145_line',idColumn:'id',
                  properties:[{column:'id',name:'lineId',type:'INTEGER'},
                    {column:'year_no',name:'year',type:'INTEGER'}],
                  dimensions:[{name:'track',tableName:'issue145_track',foreignKey:'track_id',
                    primaryKey:'id',captionColumn:'id',dimensions:[
                    {name:'album',tableName:'issue145_album',foreignKey:'album_id',
                     primaryKey:'id',captionColumn:'id',dimensions:[
                      {name:'artist',tableName:'issue145_artist',foreignKey:'artist_id',
                       primaryKey:'id',captionColumn:'name'}]}]}],measures:[]};
                """);
        Files.writeString(root.resolve("query/" + MODEL + ".qm"), """
                const m=loadTableModel('Issue145Model');
                export const queryModel={name:'Issue145QueryModel',model:m,
                  columnGroups:[{caption:'Lines',items:[{ref:m.lineId},{ref:m.year,alias:'saleYear'},
                    {ref:m.track.album.artist}]}],accesses:[]};
                """);
        Files.writeString(root.resolve("query/Issue145SelfJoinQueryModel.qm"), """
                const leftLine=loadTableModel('Issue145Model','leftLine');
                const rightLine=loadTableModel('Issue145Model','rightLine');
                export const queryModel={name:'Issue145SelfJoinQueryModel',model:leftLine,
                  joins:[leftLine.leftJoin(rightLine).on(leftLine.year,rightLine.year)],
                  columnGroups:[{caption:'Pairs',items:[{ref:leftLine.lineId},{ref:rightLine.lineId}]}],
                  accesses:[]};
                """);
        assertTrue(bundles.addExternalBundle(BUNDLE, "", root.toString(), false));
    }

    @AfterAll void cleanup() {
        if (bundles.containBundle(BUNDLE)) bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("");
        for (String table : List.of("line", "track", "album", "artist")) {
            jdbcTemplate.execute("DROP TABLE IF EXISTS issue145_" + table);
        }
    }

    private Map<String, Object> payload(String id, String caption, String dir) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("columns", List.of(id, caption));
        p.put("slice", List.of(Map.of("field", "year", "op", "=", "value", 2012)));
        p.put("distinct", true);
        p.put("orderBy", List.of(Map.of("field", id, "dir", dir)));
        p.put("limit", 100);
        p.put("returnTotal", true);
        return p;
    }

    private SemanticQueryResponse execute(Map<String, Object> p) {
        service.queryModel(MODEL, mapper.toQueryRequest(p), "validate", SemanticRequestContext.empty());
        return service.queryModel(MODEL, mapper.toQueryRequest(p), "execute", SemanticRequestContext.empty());
    }

    private String explain(Map<String, Object> p) {
        return service.compileForExplain(MODEL, mapper.toQueryRequest(p), SemanticRequestContext.empty())
                .sqlEvidence().finalPhysicalSql().toLowerCase(Locale.ROOT);
    }

    private List<Integer> ids(SemanticQueryResponse response, String field) {
        return response.getItems().stream().map(row -> ((Number) row.get(field)).intValue()).toList();
    }

    @ParameterizedTest
    @ValueSource(strings = {"artist", "track.album.artist", "track_album_artist"})
    void ascendingAliasesPreserveDistinctSetAndPhysicalOrder(String path) {
        var p = payload(path + "$id", path + "$caption", "asc");
        var result = execute(p);
        assertEquals(List.of(1, 2, 3, 4), ids(result, path + "$id"));
        assertEquals(4L, result.getTotal());
        assertEquals(2, result.getItems().stream().filter(row -> "Same".equals(row.get(path + "$caption"))).count());
        assertTrue(result.getItems().stream().allMatch(row -> row.size() == 2));
        assertTrue(explain(p).contains("order by"));
        assertTrue(result.getDebug().getExtra().get("sql").toString().toLowerCase(Locale.ROOT).contains("order by"));
    }

    @Test void descendingPaginationUsesDistinctRowsAndKeepsTotal() {
        var p = payload("artist$id", "artist$caption", "desc");
        p.put("start", 1); p.put("limit", 2);
        var result = execute(p);
        assertEquals(List.of(3, 2), ids(result, "artist$id"));
        assertEquals(4L, result.getTotal());
        assertTrue(explain(p).contains(" desc"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"artist$id", "track.album.artist$id", "track_album_artist$id"})
    void alternateOrderAliasMatchesTheSameSelectedExpression(String orderField) {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("orderBy", List.of(orderField));
        assertEquals(List.of(1, 2, 3, 4), ids(execute(p), "artist$id"));
    }

    @Test void captionSortUsesCaptionProjectionThenIdTieBreaker() {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("orderBy", List.of("artist$caption", "artist$id"));
        assertEquals(List.of(4, 1, 2, 3), ids(execute(p), "artist$id"));
    }

    @Test void renamedPropertyCanBeOrderedWithoutSelectingOriginalName() {
        var p = payload("artist$id", "year", "asc");
        p.put("orderBy", List.of(Map.of("field", "year", "dir", "desc"), "artist$id"));
        assertEquals(List.of(1, 2, 3, 4), ids(execute(p), "artist$id"));
        assertTrue(explain(p).contains("year_no desc"));
    }

    @Test void selectedCalculatedFieldPreservesExpressionAndDirection() {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("columns", List.of("artist$id", "artist$id * -1 AS reverseId"));
        p.put("orderBy", List.of("reverseId"));
        assertEquals(List.of(4, 3, 2, 1), ids(execute(p), "artist$id"));
        assertTrue(explain(p).contains("order by"));
    }

    @Test void unselectedExplicitOrderIsRejectedByEveryCompilationEntry() {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("orderBy", List.of("lineId"));
        for (String mode : List.of("validate", "execute")) {
            var error = assertThrows(RuntimeException.class,
                    () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
            assertTrue(error.getMessage().contains("DISTINCT_ORDER_BY_NOT_SELECTED"), error.toString());
        }
        var error = assertThrows(RuntimeException.class, () -> explain(p));
        assertTrue(error.getMessage().contains("DISTINCT_ORDER_BY_NOT_SELECTED"), error.toString());
    }

    @Test void defaultSortBindsProjectionWithoutMutatingCachedModel() {
        var model = getQueryModel(MODEL);
        var orders = model.getOrders();
        var original = new ArrayList<>(orders);
        var column = model.findJdbcColumnForCond("artist$id", true);
        var defaultOrder = new DbQueryOrderColumnImpl(column, "desc");
        try {
            orders.clear(); orders.add(defaultOrder);
            // A default unselected primary key must not alter the distinct grain.
            orders.add(new DbQueryOrderColumnImpl(model.findJdbcColumnForCond("lineId", true), "asc"));
            var p = payload("artist$id", "artist$caption", "asc"); p.remove("orderBy");
            assertEquals(List.of(4, 3, 2, 1), ids(execute(p), "artist$id"));
            assertEquals(2, orders.size());
            assertSame(column, defaultOrder.getSelectColumn());
            assertEquals(List.of(4, 3, 2, 1), ids(execute(p), "artist$id"));
        } finally {
            orders.clear(); orders.addAll(original);
        }
    }

    @Test void ordinaryQueryStillAllowsUnselectedOrder() {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("distinct", false); p.put("orderBy", List.of("lineId"));
        assertEquals(List.of(3, 1, 3, 4, 2, 1), ids(execute(p), "artist$id"));
        var compilation = service.compileForExplain(MODEL, mapper.toQueryRequest(p), SemanticRequestContext.empty());
        assertEquals(1, compilation.modelResultContext().getQuery().getOrder().getOrders().size());
    }

    @Test void ordinaryDuplicateAliasesKeepFirstDirectionAndDefaultTieBreaker() {
        var p = payload("artist$id", "artist$caption", "desc");
        p.put("distinct", false);
        p.put("orderBy", List.of(Map.of("field", "artist$id", "dir", "desc"),
                Map.of("field", "track.album.artist$id", "dir", "asc"), "lineId"));
        assertEquals(List.of(4, 3, 3, 2, 1, 1), ids(execute(p), "artist$id"));
        var compilation = service.compileForExplain(MODEL, mapper.toQueryRequest(p), SemanticRequestContext.empty());
        assertEquals(2, compilation.modelResultContext().getQuery().getOrder().getOrders().size());
    }

    @Test void ordinaryCaptionSortUsesDisplayValue() {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("distinct", false); p.put("orderBy", List.of("artist$caption", "lineId"));
        assertEquals(List.of(4, 1, 2, 1, 3, 3), ids(execute(p), "artist$id"));
    }

    @ParameterizedTest @ValueSource(strings = {"detail", "distinct", "grouped"})
    void selectedOutputAliasAndSourceNameShareOneOrderBinding(String mode) {
        var p = new LinkedHashMap<String, Object>();
        p.put("columns", List.of("lineId", "year"));
        p.put("distinct", "distinct".equals(mode));
        if ("grouped".equals(mode)) p.put("groupBy", List.of("lineId", "year"));
        p.put("orderBy", List.of(Map.of("field", "saleYear", "dir", "desc"), "year", "lineId"));
        p.put("limit", 3);
        assertEquals(List.of(7, 1, 2), ids(execute(p), "lineId"));
        var orders = service.compileForExplain(MODEL, mapper.toQueryRequest(p), SemanticRequestContext.empty())
                .modelResultContext().getQuery().getOrder().getOrders();
        assertEquals(2, orders.size());
        assertEquals("desc", orders.get(0).getOrder());
    }

    @Test void automaticGroupingBindsAlternateAliasBeforeValidatingOrder() {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("distinct", false);
        p.put("columns", List.of("artist$id", "count(lineId) AS lineCount"));
        p.put("orderBy", List.of(Map.of("field", "track.album.artist$id", "dir", "desc")));
        assertEquals(List.of(4, 3, 2, 1), ids(execute(p), "artist$id"));
        var compilation = service.compileForExplain(MODEL, mapper.toQueryRequest(p), SemanticRequestContext.empty());
        assertEquals(1, compilation.modelResultContext().getQuery().getOrder().getOrders().size());
    }

    @Test void automaticGroupingRejectsUnprojectedExplicitOrderAcrossEntrypoints() {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("distinct", false);
        p.put("columns", List.of("artist$id", "count(lineId) AS lineCount"));
        p.put("orderBy", List.of("year"));
        for (String mode : List.of("validate", "execute")) {
            var failure = assertThrows(RuntimeException.class,
                    () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
            assertTrue(failure.getMessage().contains("GROUP_BY_ORDER_BY_NOT_SELECTED"), failure.toString());
        }
        assertThrows(RuntimeException.class, () -> explain(p));
    }

    @ParameterizedTest @ValueSource(strings = {
            "detail-first", "detail-last", "distinct-first", "distinct-last", "grouped-first", "grouped-last"})
    void duplicateOrderPreservesRealNullPlacementAndPagination(String scenario) {
        String mode = scenario.split("-")[0];
        boolean nullFirst = scenario.endsWith("-first");
        jdbcTemplate.update("UPDATE issue145_line SET year_no=NULL WHERE id=7");
        try {
            var p = new LinkedHashMap<String, Object>();
            p.put("columns", List.of("lineId", "year"));
            p.put("distinct", "distinct".equals(mode));
            if ("grouped".equals(mode)) p.put("groupBy", List.of("lineId", "year"));
            p.put("orderBy", List.of(Map.of("field", "year", "dir", "asc",
                            "nullFirst", nullFirst, "nullLast", !nullFirst),
                    Map.of("field", "year", "dir", "desc", "nullLast", true), "lineId"));
            p.put("limit", 3); p.put("returnTotal", true);
            var response = execute(p);
            assertEquals(nullFirst ? List.of(7, 1, 2) : List.of(1, 2, 3), ids(response, "lineId"));
            assertEquals(7L, response.getTotal());
            var orders = service.compileForExplain(MODEL, mapper.toQueryRequest(p), SemanticRequestContext.empty())
                    .modelResultContext().getQuery().getOrder().getOrders();
            assertEquals(2, orders.size());
            assertEquals(nullFirst, orders.get(0).isNullFirst());
            assertEquals(!nullFirst, orders.get(0).isNullLast());
        } finally {
            jdbcTemplate.update("UPDATE issue145_line SET year_no=2013 WHERE id=7");
        }
    }

    @Test void postAggregateDuplicateOrderIsRemovedAtFinalStage() {
        var p = payload("artist$id", "artist$caption", "asc");
        p.put("distinct", false);
        p.put("columns", List.of("artist$id", "count(lineId) AS lineCount", "share"));
        p.put("postAggregateCalculations", List.of(Map.of("name", "share", "kind", "rankByMeasure",
                "measure", "lineCount", "scope", "grandTotal", "format", "value")));
        p.put("orderBy", List.of("share", Map.of("field", "share", "dir", "desc"), "artist$id"));
        if (!supportsWindowFunctions()) {
            // Unsupported backends must reject this feature, never emit invalid SQL.
            assertThrows(RuntimeException.class, () -> execute(p));
            return;
        }
        assertEquals(List.of(1, 3, 2, 4), ids(execute(p), "artist$id"));
        String sql = explain(p);
        String outerOrder = sql.substring(sql.lastIndexOf("order by"));
        assertEquals(1, java.util.regex.Pattern.compile("share").matcher(outerOrder).results().count(), sql);
    }

    @Test void explicitDirectionWinsOverDuplicateModelDefault() {
        var orders = getQueryModel(MODEL).getOrders();
        var original = new ArrayList<>(orders);
        var defaultOrder = new DbQueryOrderColumnImpl(
                getQueryModel(MODEL).findJdbcColumnForCond("artist$id", true), "asc");
        try {
            orders.clear(); orders.add(defaultOrder);
            var p = payload("artist$id", "artist$caption", "desc");
            var compilation = service.compileForExplain(MODEL, mapper.toQueryRequest(p), SemanticRequestContext.empty());
            var resolved = compilation.modelResultContext().getQuery().getOrder().getOrders();
            assertEquals(1, resolved.size());
            assertEquals("desc", resolved.get(0).getOrder());
            assertEquals(List.of(4, 3, 2, 1), ids(execute(p), "artist$id"));
            assertEquals("asc", defaultOrder.getOrder());
        } finally {
            orders.clear(); orders.addAll(original);
        }
    }

    @Test void sameTableInstancesCannotSatisfyEachOthersProjection() {
        var p = new LinkedHashMap<String, Object>();
        p.put("columns", List.of("leftLine.lineId"));
        p.put("distinct", true);
        p.put("orderBy", List.of("rightLine.lineId"));
        for (String mode : List.of("validate", "execute")) {
            var failure = assertThrows(RuntimeException.class, () -> service.queryModel(
                    "Issue145SelfJoinQueryModel", mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
            assertTrue(failure.getMessage().contains("DISTINCT_ORDER_BY_NOT_SELECTED"), failure.toString());
        }
        p.put("columns", List.of("leftLine.lineId", "rightLine.lineId"));
        p.put("orderBy", List.of(Map.of("field", "rightLine.lineId", "dir", "desc"), "leftLine.lineId"));
        p.put("limit", 3);
        var rows = service.queryModel("Issue145SelfJoinQueryModel", mapper.toQueryRequest(p),
                "execute", SemanticRequestContext.empty()).getItems();
        assertEquals(List.of(7, 1, 2), rows.stream().map(row -> ((Number) row.get("leftLine.lineId")).intValue()).toList());
        assertEquals(List.of(7, 6, 6), rows.stream().map(row -> ((Number) row.get("rightLine.lineId")).intValue()).toList());
    }

    @Test void nullPlacementOptionsRemainOnExplicitOrders() {
        var p = payload("artist$id", "artist$caption", "desc");
        p.put("orderBy", List.of(Map.of("field", "artist$id", "dir", "desc", "nullLast", true)));
        var compilation = service.compileForExplain(MODEL, mapper.toQueryRequest(p), SemanticRequestContext.empty());
        var orders = compilation.modelResultContext().getQuery().getOrder().getOrders();
        assertTrue(orders.get(0).isNullLast());
        assertFalse(orders.get(0).isNullFirst());
        assertEquals("desc", orders.get(0).getOrder());
    }

    @Test void ignoredModelDefaultRetainsItsExistingJoinWithoutWideningProjection() {
        String modelName = "Issue145SelfJoinQueryModel";
        var model = getQueryModel(modelName);
        var orders = model.getOrders();
        var original = new ArrayList<>(orders);
        try {
            orders.clear();
            orders.add(new DbQueryOrderColumnImpl(model.findJdbcColumnForCond("rightLine.lineId", true), "asc"));
            var p = Map.<String, Object>of("columns", List.of("leftLine.lineId"), "distinct", true,
                    "orderBy", List.of("leftLine.lineId"), "limit", 100);
            var compilation = service.compileForExplain(modelName, mapper.toQueryRequest(p), SemanticRequestContext.empty());
            assertTrue(compilation.sqlEvidence().finalPhysicalSql().contains("issue145_line rightLine"));
            var result = service.queryModel(modelName, mapper.toQueryRequest(p), "execute", SemanticRequestContext.empty());
            assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), ids(result, "leftLine.lineId"));
            assertTrue(result.getItems().stream().allMatch(row -> row.size() == 1));
        } finally {
            orders.clear(); orders.addAll(original);
        }
    }

    @Test void distinctOrderingPreservesFieldAndPhysicalPermissionChecks() {
        var p = payload("artist$id", "artist$caption", "asc");
        // Dimension permissions cover both ID and caption; deny the whole artist dimension.
        var fieldAccess = SemanticRequestContext.empty().withGovernance(Set.of("year"), null, null);
        var denied = SemanticRequestContext.empty().withGovernance(null, List.of(
                new com.foggyframework.dataset.model.semantic.domain.DeniedPhysicalColumn(null, "issue145_album", "artist_id")), null);
        for (var context : List.of(fieldAccess, denied)) {
            for (String mode : List.of("validate", "execute")) {
                assertThrows(RuntimeException.class,
                        () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, context));
            }
        }
    }

    @Test void groupByWithDistinctStillUsesAggregateOrdering() {
        var p = payload("artist$id", "artist$caption", "desc");
        p.put("columns", List.of("artist$id", "artist$caption", "count(lineId) AS lineCount"));
        p.put("groupBy", List.of("artist$id", "artist$caption"));
        assertEquals(List.of(4, 3, 2, 1), ids(execute(p), "artist$id"));
        assertFalse(explain(p).contains("select distinct"));
    }
}

package com.foggyframework.dataset.model.semantic;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Same fixture and numerical assertions on SQLite, MySQL, PostgreSQL and SQL Server. */
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ComparativeCalendarRegressionIT extends EcommerceTestSupport {
    @Resource SemanticQueryServiceV3 service;
    @Resource SemanticQueryPayloadMapper mapper;
    @Resource SystemBundlesContext bundles;
    @TempDir Path root;
    private static final String MODEL = "ComparativeCalendarQueryModel";
    private static final String BUNDLE = "comparative-calendar-regression";
    private static final String TABLE = "issue140_calendar";

    @BeforeEach void setup() throws Exception {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE);
        String type = getDialectKey().equals("sqlserver") ? "DATETIME2" : "TIMESTAMP";
        jdbcTemplate.execute("CREATE TABLE " + TABLE + " (id INTEGER PRIMARY KEY, occurred_at " + type
                + ", category VARCHAR(20), amount DECIMAL(18,2), date_only DATE)");
        insert(1, "2011-12-15 12:00:00", "42.62");
        insert(2, "2012-01-31 23:59:59", "37.62");
        insert(3, "2012-10-01 12:00:00", "42.62");
        insert(4, "2012-11-20 12:00:00", "37.62");
        insert(5, "2019-12-30 12:00:00", "20.00");
        insert(6, "2021-01-01 12:00:00", "30.00");
        insert(7, "2021-01-04 12:00:00", "40.00");
        Files.createDirectories(root.resolve("model"));
        Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/ComparativeCalendarTableModel.tm"), """
                export const model = {name:'ComparativeCalendarTableModel', tableName:'issue140_calendar', idColumn:'id',
                  dimensions:[], properties:[{column:'id',name:'id',type:'INTEGER'},
                    {column:'occurred_at',name:'occurredAt',type:'DATETIME'},
                    {column:'date_only',name:'dateOnly',type:'DATE'},
                    {column:'category',name:'category',type:'STRING'}],
                  measures:[{column:'amount',name:'amount',type:'NUMBER',aggregation:'sum'},
                    {column:'amount',name:'averageAmount',type:'NUMBER',aggregation:'avg'},
                    {column:'id',name:'uniqueCount',type:'INTEGER',aggregation:'count_distinct'}]};
                """);
        Files.writeString(root.resolve("query/" + MODEL + ".qm"), """
                const m=loadTableModel('ComparativeCalendarTableModel');
                export const queryModel={name:'ComparativeCalendarQueryModel',model:m,
                  columnGroups:[{caption:'Calendar',items:[{ref:m.id},{ref:m.occurredAt},{ref:m.dateOnly},{ref:m.category},{ref:m.amount},{ref:m.averageAmount},{ref:m.uniqueCount}]}],accesses:[]};
                """);
        assertTrue(bundles.addExternalBundle(BUNDLE, "", root.toString(), false));
    }

    @AfterEach void cleanup() {
        if (bundles.containBundle(BUNDLE)) bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace("");
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE);
    }

    private void insert(int id, String date, String amount) {
        // SQLite's fixture uses textual timestamps; other JDBC drivers accept the same bound ISO local value.
        jdbcTemplate.update("INSERT INTO " + TABLE + " VALUES (?, ?, ?, ?, ?)", id,
                getDialectKey().equals("sqlite") ? date : java.time.LocalDateTime.parse(date.replace(' ', 'T')), null, new BigDecimal(amount),
                getDialectKey().equals("sqlite") ? date.substring(0, 10) : java.sql.Date.valueOf(date.substring(0, 10)));
    }

    private Map<String,Object> payload(String comparison, String grain, String start, String end) {
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("columns", List.of("occurredAt", "category", "amount", "amount__prior", "amount__diff", "amount__ratio"));
        payload.put("groupBy", List.of("occurredAt", "category"));
        payload.put("orderBy", List.of("occurredAt"));
        payload.put("limit", 100);
        payload.put("timeWindow", Map.of("field", "occurredAt", "grain", grain, "comparison", comparison,
                "value", List.of(start, end), "range", "[]", "targetMetrics", List.of("amount")));
        return payload;
    }

    private SemanticQueryResponse execute(Map<String,Object> payload) {
        service.queryModel(MODEL, mapper.toQueryRequest(payload), "validate", SemanticRequestContext.empty());
        return service.queryModel(MODEL, mapper.toQueryRequest(payload), "execute", SemanticRequestContext.empty());
    }

    @Test void monthEndTimestampAndCrossYearPriorUseCompletePeriodWithNullBusinessKey() {
        var response = execute(payload("mom", "month", "2012-01-01", "2012-01-31"));
        assertEquals(1, response.getItems().size());
        var row = response.getItems().get(0);
        number("37.62", row.get("amount"));
        number("42.62", row.get("amount__prior"));
        number("-5", row.get("amount__diff"));
        assertEquals(-5.0 / 42.62, ((Number) row.get("amount__ratio")).doubleValue(), 1e-10);
        assertTrue(row.keySet().stream().noneMatch(k -> k.startsWith("__tw_")));
    }

    @Test void civilMidnightBoundsIncludeFirstInstantAndExcludeNextPeriod() {
        insert(80, "2012-01-01 00:00:00", "10");
        insert(81, "2012-02-01 00:00:00", "100000");
        insert(82, "2011-12-01 00:00:00", "20");
        var rows = execute(payload("mom", "month", "2012-01-01", "2012-01-31")).getItems();
        assertEquals(1, rows.size());
        number("47.62", rows.get(0).get("amount"));
        number("62.62", rows.get(0).get("amount__prior"));
        number("-15", rows.get(0).get("amount__diff"));
    }

    @Test void sliceRemainsHardConstraintOnBothBranches() {
        var p = payload("mom", "month", "2012-01-01", "2012-01-31");
        p.put("slice", List.of(Map.of("field", "occurredAt", "op", "[)", "value", List.of("2012-01-01", "2013-01-01"))));
        var rows = execute(p).getItems();
        assertEquals(1, rows.size());
        assertNull(rows.get(0).get("amount__prior"));
        assertNull(rows.get(0).get("amount__diff"));
    }

    @Test void nativeDateAxisUsesDateOnlyBoundsIncludingTheLastNaturalDay() {
        var p = payload("mom", "month", "2012-01-01", "2012-01-31");
        var window = new LinkedHashMap<>((Map<String,Object>) p.get("timeWindow"));
        window.put("field", "dateOnly"); p.put("timeWindow", window);
        p.put("columns", List.of("dateOnly", "amount", "amount__prior", "amount__diff"));
        p.put("groupBy", List.of("dateOnly")); p.put("orderBy", List.of("dateOnly"));
        var rows = execute(p).getItems();
        assertEquals(1, rows.size());
        number("37.62", rows.get(0).get("amount")); number("42.62", rows.get(0).get("amount__prior"));
        number("-5", rows.get(0).get("amount__diff"));
    }

    @Test void absentCalendarPeriodIsNotPreviousResultRow() {
        var rows = execute(payload("mom", "month", "2012-10-01", "2012-11-30")).getItems();
        assertEquals(2, rows.size());
        assertNull(rows.get(0).get("amount__prior"));
        number("-5", rows.get(1).get("amount__diff"));
    }

    @Test void weeklyIsoYearAndMissingWeek53() {
        var rows = execute(payload("yoy", "week", "2020-12-28", "2021-01-10")).getItems();
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).containsKey("amount__prior"), "missing ISO week must be an explicit null output");
        assertNull(rows.get(0).get("amount__prior"));
        number("20", rows.get(1).get("amount__prior"));
    }

    @Test void finalPaginationCountsUnpagedComparisonRows() {
        var p = payload("mom", "month", "2012-01-01", "2012-12-31");
        p.put("limit", 1); p.put("start", 1); p.put("returnTotal", true);
        var response = execute(p);
        assertEquals(1, response.getItems().size());
        assertEquals(3L, response.getPagination().getTotalCount());
        assertEquals(1, response.getPagination().getStart());
        assertEquals(1, response.getPagination().getLimit());
    }

    @Test void partialPeriodIsRejectedOnValidateAndDirectExecute() {
        var p = payload("mom", "month", "2012-01-02", "2012-01-31");
        for (String mode : List.of("validate", "execute")) {
            var error = assertThrows(RuntimeException.class, () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
            assertTrue(error.getMessage().contains("TIMEWINDOW_PARTIAL_PERIOD_UNSUPPORTED"));
        }
    }

    @Test void otherSupportedPeriodsPreserveCalendarSemantics() {
        number("37.62", execute(payload("yoy", "quarter", "2012-10-01", "2012-12-31")).getItems().get(0).get("amount__diff"));
        number("75.24", execute(payload("yoy", "year", "2012-01-01", "2012-12-31")).getItems().get(0).get("amount__diff"));
        number("10", execute(payload("wow", "week", "2021-01-04", "2021-01-10")).getItems().get(0).get("amount__diff"));
        insert(8, "2020-12-28 18:00:00", "12");
        number("28", execute(payload("wow", "day", "2021-01-04", "2021-01-04")).getItems().get(0).get("amount__diff"));
        insert(9, "2011-01-03 18:00:00", "10");
        number("27.62", execute(payload("yoy", "month", "2012-01-01", "2012-01-31")).getItems().get(0).get("amount__diff"));
    }

    @Test void nativeAverageAndDistinctAggregateOnceAndPreserveNonTargetMetric() {
        insert(8, "2012-01-10 18:00:00", "10");
        var p = payload("mom", "month", "2012-01-01", "2012-01-31");
        p.put("columns", List.of("occurredAt", "category", "amount", "averageAmount", "averageAmount__prior", "uniqueCount", "uniqueCount__prior"));
        var window = new LinkedHashMap<>((Map<String,Object>) p.get("timeWindow"));
        window.put("targetMetrics", List.of("averageAmount", "uniqueCount")); p.put("timeWindow", window);
        var row = execute(p).getItems().get(0);
        number("47.62", row.get("amount")); number("23.81", row.get("averageAmount"));
        number("42.62", row.get("averageAmount__prior"));
        number("2", row.get("uniqueCount")); number("1", row.get("uniqueCount__prior"));
    }

    private Map<String,Object> windowPayload(List<String> columns, List<Map<String,Object>> fields) {
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("columns", columns);
        request.put("groupBy", List.of("occurredAt"));
        request.put("slice", List.of(Map.of("field", "occurredAt", "op", "[)",
                "value", List.of("2012-10-01", "2012-12-01"))));
        request.put("orderBy", List.of("occurredAt"));
        request.put("calculatedFields", fields);
        request.put("limit", 100);
        return request;
    }

    private Map<String,Object> windowField(String name, String expression) {
        return Map.of("name", name, "expression", expression, "windowOrderBy", List.of(Map.of("field", "occurredAt", "dir", "asc")));
    }

    @Test void windowDependencyChainUsesPreviousStageAliases() {
        if (assertUnsupportedWindowDialect()) return;
        var p = windowPayload(List.of("occurredAt", "amount", "previous", "difference", "ratio"), List.of(
                Map.of("name", "ratio", "expression", "1.0 * difference / nullif(previous, 0)"),
                Map.of("name", "difference", "expression", "amount - previous"),
                windowField("previous", "lag(amount)")));
        var rows = execute(p).getItems();
        assertEquals(2, rows.size());
        assertEquals(Set.of("occurredAt", "amount", "previous", "difference", "ratio"), rows.get(0).keySet());
        assertNull(rows.get(0).get("difference"));
        number("42.62", rows.get(1).get("previous"));
        number("-5", rows.get(1).get("difference"));
        assertEquals(-5.0 / 42.62, ((Number) rows.get(1).get("ratio")).doubleValue(), 1e-10);
    }

    @Test void unusedWindowDeclarationDoesNotCreateAnEmptyResultStage() {
        if (assertUnsupportedWindowDialect()) return;
        var rows = execute(windowPayload(List.of("occurredAt", "amount"),
                List.of(windowField("unusedPrevious", "lag(amount)")))).getItems();
        assertEquals(2, rows.size());
        number("42.62", rows.get(0).get("amount")); number("37.62", rows.get(1).get("amount"));
        assertEquals(Set.of("occurredAt", "amount"), rows.get(0).keySet());
    }

    @Test void compositeWindowExpressionLiftsFunctionAndKeepsHiddenDependenciesPrivate() {
        if (assertUnsupportedWindowDialect()) return;
        var rows = execute(windowPayload(List.of("occurredAt", "difference"),
                List.of(windowField("difference", "amount - lag(amount)")))).getItems();
        assertEquals(2, rows.size());
        assertEquals(Set.of("occurredAt", "difference"), rows.get(0).keySet());
        assertNull(rows.get(0).get("difference"));
        number("-5", rows.get(1).get("difference"));
        assertEquals(Set.of("occurredAt", "difference"), rows.get(1).keySet());
    }

    @Test void hiddenWindowProducerAndPostSliceAreEvaluatedBeforeCountAndPagination() {
        if (assertUnsupportedWindowDialect()) return;
        var p = windowPayload(List.of("occurredAt", "difference"), List.of(
                Map.of("name", "difference", "expression", "amount - previous"), windowField("previous", "lag(amount)")));
        p.put("postSlice", List.of(Map.of("field", "difference", "op", "<", "value", 0)));
        p.put("returnTotal", true); p.put("limit", 1);
        var response = execute(p);
        assertEquals(1L, response.getPagination().getTotalCount());
        assertEquals(1, response.getItems().size());
        number("-5", response.getItems().get(0).get("difference"));
    }

    @Test void windowCanConsumeSelectedAggregateAlias() {
        if (assertUnsupportedWindowDialect()) return;
        var rows = execute(windowPayload(List.of("occurredAt", "sum(amount) AS totalAmount", "previous"),
                List.of(windowField("previous", "lag(totalAmount)")))).getItems();
        assertEquals(2, rows.size());
        number("42.62", rows.get(1).get("previous"));
    }

    @Test void nestedAggregateFailsOnValidateAndDirectExecuteWithTypedPayloadDiagnostic() {
        var p = windowPayload(List.of("occurredAt", "bad"), List.of(windowField("bad", "lag(sum(amount))")));
        for (String mode : List.of("validate", "execute")) {
            var failure = assertThrows(RuntimeException.class, () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
            var diagnostic = com.foggyframework.dataset.model.engine.expression.QueryCalculationException.find(failure);
            assertNotNull(diagnostic, failure.toString());
            assertEquals(supportsWindowFunctions() ? "WINDOW_NESTED_AGGREGATE_UNSUPPORTED" : "WINDOW_DIALECT_UNSUPPORTED", diagnostic.getCode());
            assertEquals("calculatedFields[0].expression", diagnostic.getPath());
        }
    }

    @Test void invalidPlanNeverPreparesBusinessSqlAndSuggestedAggregateAliasRepairExecutes() throws Exception {
        if (assertUnsupportedWindowDialect()) return;
        var model = (com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl)
                queryModelLoader.getJdbcQueryModel(MODEL, "");
        var original = model.getDataSource();
        var prepared = new java.util.concurrent.atomic.AtomicInteger();
        var observed = new org.springframework.jdbc.datasource.DelegatingDataSource(original) {
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException {
                var connection = super.getConnection();
                return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{java.sql.Connection.class}, (proxy, method, args) -> {
                            if (method.getName().equals("prepareStatement") && args != null && args[0] instanceof String sql
                                    && sql.contains(TABLE)) prepared.incrementAndGet();
                            try { return method.invoke(connection, args); }
                            catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                        });
            }
        };
        model.setDataSource(observed);
        try {
            var p = windowPayload(List.of("occurredAt", "previous"), List.of(windowField("previous", "lag(sum(amount))")));
            p.put("returnTotal", true);
            for (String mode : List.of("validate", "execute")) {
                var failure = assertThrows(RuntimeException.class,
                        () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
                var diagnostic = com.foggyframework.dataset.model.engine.expression.QueryCalculationException.find(failure);
                assertNotNull(diagnostic); assertEquals("WINDOW_NESTED_AGGREGATE_UNSUPPORTED", diagnostic.getCode());
                assertEquals(false, diagnostic.getDetails().get("safeToAutoRepair"));
            }
            assertEquals(0, prepared.get(), "Neither MAIN nor TOTAL business SQL may be prepared for an invalid plan");
            // Apply the advertised grouped-alias repair, preserving range, grain and window ordering.
            p.put("columns", List.of("occurredAt", "sum(amount) AS totalAmount", "previous"));
            p.put("calculatedFields", List.of(windowField("previous", "lag(totalAmount)")));
            var response = execute(p);
            assertEquals(2L, response.getPagination().getTotalCount());
            number("42.62", response.getItems().get(1).get("previous"));
            assertTrue(prepared.get() > 0, "The probe must observe successful business execution");
        } finally { model.setDataSource(original); }
    }

    @Test void resultFilterCanConsumeUnselectedWindowAndScalarFields() {
        if (assertUnsupportedWindowDialect()) return;
        var p = windowPayload(List.of("occurredAt", "amount"), List.of(
                Map.of("name", "difference", "expression", "amount - previous"), windowField("previous", "lag(amount)")));
        p.put("postSlice", List.of(Map.of("field", "difference", "op", "<", "value", 0)));
        p.put("orderBy", List.of(Map.of("field", "difference", "dir", "asc")));
        p.put("returnTotal", true);
        var response = execute(p);
        assertEquals(1L, response.getPagination().getTotalCount());
        number("37.62", response.getItems().get(0).get("amount"));
        assertEquals(Set.of("occurredAt", "amount"), response.getItems().get(0).keySet());
    }

    @Test void zeroWindowDenominatorProducesNullWithoutDefaultingMissingPreviousToZero() {
        if (assertUnsupportedWindowDialect()) return;
        jdbcTemplate.update("UPDATE " + TABLE + " SET amount=0 WHERE id=3");
        var p = windowPayload(List.of("occurredAt", "difference", "ratio"), List.of(
                windowField("previous", "lag(amount)"), Map.of("name", "difference", "expression", "amount-previous"),
                Map.of("name", "ratio", "expression", "difference/previous")));
        var rows = execute(p).getItems();
        assertNull(rows.get(0).get("difference")); number("37.62", rows.get(1).get("difference"));
        assertNull(rows.get(1).get("ratio"));
    }

    @Test void governanceConstraintsReachBothComparisonBranchesAndPhysicalPreparation() {
        var p = payload("mom", "month", "2012-01-01", "2012-01-31");
        var sourceLimit = new com.foggyframework.dataset.model.def.query.request.SliceRequestDef(
                "occurredAt", ">=", "2012-01-01");
        var governed = SemanticRequestContext.empty().withGovernance(null, null, List.of(sourceLimit));
        var response = service.queryModel(MODEL, mapper.toQueryRequest(p), "execute", governed);
        assertEquals(1, response.getItems().size());
        number("37.62", response.getItems().get(0).get("amount"));
        assertNull(response.getItems().get(0).get("amount__prior"));
        for (String mode : List.of("validate", "execute")) {
            var physical = SemanticRequestContext.empty().withGovernance(null,
                    List.of(new DeniedPhysicalColumn(null, TABLE, "amount")), null);
            var failure = assertThrows(RuntimeException.class, () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, physical));
            assertTrue(failure.getMessage().contains("amount"));
            var logical = SemanticRequestContext.empty().withGovernance(Set.of("occurredAt", "category"), null, null);
            assertThrows(RuntimeException.class, () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, logical));
        }
    }

    private boolean assertUnsupportedWindowDialect() {
        if (supportsWindowFunctions()) return false;
        var p = windowPayload(List.of("occurredAt", "previous"), List.of(windowField("previous", "lag(amount)")));
        for (String mode : List.of("validate", "execute")) {
            var error = assertThrows(RuntimeException.class, () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
            var diagnostic = com.foggyframework.dataset.model.engine.expression.QueryCalculationException.find(error);
            assertNotNull(diagnostic);
            assertEquals("WINDOW_DIALECT_UNSUPPORTED", diagnostic.getCode());
        }
        return true;
    }

    @Test void partitionedCompositeWindowDoesNotCrossBusinessKeys() {
        if (assertUnsupportedWindowDialect()) return;
        jdbcTemplate.update("UPDATE " + TABLE + " SET category='A' WHERE id IN (3,4)");
        insert(8, "2012-10-01 12:00:00", "100");
        insert(9, "2012-11-20 12:00:00", "103");
        jdbcTemplate.update("UPDATE " + TABLE + " SET category='B' WHERE id IN (8,9)");
        var field = new LinkedHashMap<String,Object>(windowField("difference", "amount-lag(amount)"));
        field.put("partitionBy", List.of("category"));
        var p = windowPayload(List.of("category", "occurredAt", "difference"), List.of(field));
        p.put("groupBy", List.of("category", "occurredAt"));
        p.put("orderBy", List.of("category", "occurredAt"));
        var rows = execute(p).getItems();
        assertEquals(4, rows.size());
        assertNull(rows.get(0).get("difference")); number("-5", rows.get(1).get("difference"));
        assertNull(rows.get(2).get("difference")); number("3", rows.get(3).get("difference"));
    }

    @Test void simpleLagRemainsSupportedForTwelveThirteenMonthsAndEightyThreeDetails() {
        if (assertUnsupportedWindowDialect()) return;
        jdbcTemplate.update("DELETE FROM " + TABLE);
        for (int i = 0; i < 13; i++) {
            var day = java.time.LocalDate.of(2011, 12, 15).plusMonths(i);
            insert(100 + i, day + " 12:00:00", Integer.toString(i));
        }
        var p = windowPayload(List.of("occurredAt", "amount", "previous"),
                List.of(windowField("previous", "lag(amount)")));
        p.put("slice", List.of(Map.of("field", "occurredAt", "op", "[)",
                "value", List.of("2011-12-01", "2013-01-01"))));
        var thirteen = execute(p).getItems();
        assertEquals(13, thirteen.size()); assertNull(thirteen.get(0).get("previous"));
        number("0", thirteen.get(1).get("previous")); number("11", thirteen.get(12).get("previous"));
        p.put("slice", List.of(Map.of("field", "occurredAt", "op", "[)",
                "value", List.of("2012-01-01", "2013-01-01"))));
        var twelve = execute(p).getItems();
        assertEquals(12, twelve.size()); assertNull(twelve.get(0).get("previous"));
        number("11", twelve.get(11).get("previous"));
        for (int i = 0; i < 70; i++) insert(200 + i, "2012-11-20 12:00:00", Integer.toString(1000 + i));
        p.put("slice", List.of()); p.put("columns", List.of("id", "occurredAt", "amount", "previous"));
        p.put("groupBy", List.of("id", "occurredAt")); p.put("orderBy", List.of("id"));
        p.put("calculatedFields", List.of(Map.of("name", "previous", "expression", "lag(amount)",
                "windowOrderBy", List.of(Map.of("field", "id", "dir", "asc")))));
        var details = execute(p).getItems();
        assertEquals(83, details.size()); assertNull(details.get(0).get("previous"));
        number("1068", details.get(82).get("previous"));
    }

    @Test void windowOfWindowDiagnosticRetainsOriginalOrderByPathAfterSorting() {
        if (assertUnsupportedWindowDialect()) return;
        var p = windowPayload(List.of("occurredAt", "bad"), List.of(
                Map.of("name", "bad", "expression", "lag(amount)",
                        "windowOrderBy", List.of(Map.of("field", "previous", "dir", "asc"))),
                windowField("previous", "lag(amount)")));
        for (String mode : List.of("validate", "execute")) {
            var failure = assertThrows(RuntimeException.class,
                    () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
            var diagnostic = com.foggyframework.dataset.model.engine.expression.QueryCalculationException.find(failure);
            assertNotNull(diagnostic);
            assertEquals("calculatedFields[0].windowOrderBy[0].field", diagnostic.getPath());
            assertEquals("bad", diagnostic.getField());
        }
    }

    @Test void sqlServerIsoWeekStartDoesNotDependOnDateFirst() {
        if (!getDialectKey().equals("sqlserver")) return;
        var dialect = com.foggyframework.dataset.utils.DbUtils.getDialect(jdbcTemplate.getDataSource());
        String expression = dialect.buildDateTruncateExpression("CAST('20210101' AS date)", "WEEK");
        jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (var statement = connection.createStatement()) {
                for (int firstDay : List.of(1, 7)) {
                    statement.execute("SET DATEFIRST " + firstDay);
                    try (var result = statement.executeQuery("SELECT " + expression)) {
                        assertTrue(result.next());
                        assertEquals(java.time.LocalDate.of(2020, 12, 28), result.getDate(1).toLocalDate());
                    }
                }
            }
            return null;
        });
    }

    private static void number(String expected, Object actual) {
        assertNotNull(actual);
        assertEquals(new BigDecimal(expected).doubleValue(), new BigDecimal(actual.toString()).doubleValue(), 1e-9);
    }
}

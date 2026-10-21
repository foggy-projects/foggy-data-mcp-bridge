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

/** #152-154: independent sparse civil-day oracle, identical assertions on every real database. */
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AccumulatingCalendarRegressionIT extends EcommerceTestSupport {
    @Resource SemanticQueryServiceV3 service;
    @Resource SemanticQueryPayloadMapper mapper;
    @Resource SystemBundlesContext bundles;
    @TempDir Path root;
    private static final String MODEL = "AccumulatingCalendarQueryModel", BUNDLE = "accumulating-calendar-regression", TABLE = "issue152_calendar";

    @BeforeEach void setup() throws Exception {
        jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE);
        jdbcTemplate.execute("CREATE TABLE " + TABLE + " (id INTEGER PRIMARY KEY, occurred_at "
                + (getDialectKey().equals("sqlserver") ? "DATETIME2" : "TIMESTAMP") + " NULL, category VARCHAR(20), amount DECIMAL(18,2))");
        insert(1, "2011-12-31 12:00:00", "999");
        insert(2, "2012-01-01 12:00:00", "10");
        insert(3, "2012-10-01 12:00:00", "20");
        insert(4, "2012-11-14 12:00:00", "0.99");
        insert(5, "2012-11-27 12:00:00", "1.98");
        insert(6, "2012-11-27 18:00:00", "1.98");
        insert(7, "2012-11-28 12:00:00", "3.96");
        insert(8, "2012-11-29 23:59:59", "5.94");
        insert(9, "2012-12-01 00:00:00", "1000");
        Files.createDirectories(root.resolve("model")); Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/AccumulatingCalendarTableModel.tm"), """
            export const model={name:'AccumulatingCalendarTableModel',tableName:'issue152_calendar',idColumn:'id',
              properties:[{column:'occurred_at',name:'occurredAt',type:'DATETIME'},{column:'category',name:'category',type:'STRING'}],
              dimensions:[{name:'invoiceDate',foreignKey:'occurred_at',primaryKey:'occurred_at',captionColumn:'occurred_at',type:'DATETIME',
                properties:[{column:'occurred_at',name:'year',type:'INTEGER',dialectFormulaDef:{
                  sqlite:{builder:(a)=>`CAST(strftime('%Y', ${a}.occurred_at) AS INTEGER)`},
                  postgresql:{builder:(a)=>`EXTRACT(YEAR FROM ${a}.occurred_at)`},
                  mysql:{builder:(a)=>`YEAR(${a}.occurred_at)`},sqlserver:{builder:(a)=>`YEAR(${a}.occurred_at)`}}},
                  {column:'occurred_at',name:'month',type:'INTEGER',dialectFormulaDef:{
                  sqlite:{builder:(a)=>`CAST(strftime('%m', ${a}.occurred_at) AS INTEGER)`},
                  postgresql:{builder:(a)=>`EXTRACT(MONTH FROM ${a}.occurred_at)`},
                  mysql:{builder:(a)=>`MONTH(${a}.occurred_at)`},sqlserver:{builder:(a)=>`MONTH(${a}.occurred_at)`}}}]}],
              measures:[{column:'amount',name:'amount',type:'NUMBER',aggregation:'sum'}]};
            """);
        Files.writeString(root.resolve("query/" + MODEL + ".qm"), """
            const m=loadTableModel('AccumulatingCalendarTableModel');
            export const queryModel={name:'AccumulatingCalendarQueryModel',model:m,
              columnGroups:[{caption:'Calendar',items:[{ref:m.occurredAt},{ref:m.category},{ref:m.invoiceDate},
                {ref:m.invoiceDate$year},{ref:m.invoiceDate$month},{ref:m.amount}]}],accesses:[]};
            """);
        assertTrue(bundles.addExternalBundle(BUNDLE, "", root.toString(), false));
    }
    @AfterEach void cleanup() {
        if (bundles.containBundle(BUNDLE)) bundles.removeBundle(BUNDLE);
        queryModelLoader.clearByNamespace(""); jdbcTemplate.execute("DROP TABLE IF EXISTS " + TABLE);
    }
    private void insert(int id, String date, String amount) {
        jdbcTemplate.update("INSERT INTO " + TABLE + " VALUES (?, ?, ?, ?)", id,
                getDialectKey().equals("sqlite") ? date : java.time.LocalDateTime.parse(date.replace(' ', 'T')), null, new BigDecimal(amount));
    }
    private Map<String,Object> payload(String comparison, String mode) {
        Map<String,Object> p = new LinkedHashMap<>();
        p.put("columns", List.of("occurredAt", "category", "amount", "amount__" + comparison));
        p.put("groupBy", List.of("occurredAt", "category")); p.put("orderBy", List.of("occurredAt")); p.put("limit", 100);
        Map<String,Object> w = new LinkedHashMap<>(Map.of("field", "occurredAt", "grain", "day", "comparison", comparison,
                "value", List.of("2012-11-15", "2012-12-01"), "targetMetrics", List.of("amount")));
        if (mode != null) w.put("rollingMode", mode); p.put("timeWindow", w); return p;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> window(Map<String,Object> p) { return (Map<String,Object>) p.get("timeWindow"); }
    private SemanticQueryResponse execute(Map<String,Object> p) {
        service.queryModel(MODEL, mapper.toQueryRequest(p), "validate", SemanticRequestContext.empty());
        service.queryModel(MODEL, mapper.toQueryRequest(p), "preview", SemanticRequestContext.empty());
        return service.queryModel(MODEL, mapper.toQueryRequest(p), "execute", SemanticRequestContext.empty());
    }
    private boolean rejectUnsupportedWindow(Map<String,Object> p) {
        if (getDialectKey().equals("mysql") && !jdbcTemplate.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseMajorVersion() >= 8)) {
            for (String mode : List.of("validate", "execute")) {
                var error = assertThrows(RuntimeException.class,
                        () -> service.queryModel(MODEL, mapper.toQueryRequest(p), mode, SemanticRequestContext.empty()));
                assertTrue(error.getMessage().contains("TIMEWINDOW_WINDOW_UNSUPPORTED"),error.getMessage());
            }
            return true;
        }
        return false;
    }
    private static void number(String expected, Object actual) {
        assertNotNull(actual);
        assertTrue(new BigDecimal(expected).subtract(new BigDecimal(actual.toString())).abs().compareTo(new BigDecimal("0.000001")) < 0,
                () -> "expected=" + expected + ", actual=" + actual);
    }
    private static List<String> dates(List<Map<String,Object>> rows) { return rows.stream().map(r -> String.valueOf(r.get("occurredAt")).substring(0,10)).toList(); }

    @Test void calendarSparseDaysAndSameDayRowsRespectDisplayRange() {
        var response = execute(payload("rolling_7d", "calendar"));
        var rows = response.getItems();
        assertEquals(List.of("2012-11-27", "2012-11-28", "2012-11-29"), dates(rows));
        for (int i=0;i<3;i++) number(List.of("3.96","7.92","13.86").get(i),rows.get(i).get("amount__rolling_7d"));
        assertEquals(Set.of("occurredAt","category","amount","amount__rolling_7d"), rows.get(0).keySet());
        assertEquals(rows.get(0).keySet(), response.getSchema().getColumns().stream().map(c -> c.getName()).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void calendarThirtyAndNinetyDayWindowsIncludeHistory() {
        for (String mode : List.of("rolling_30d", "rolling_90d")) {
            var rows=execute(payload(mode,"calendar")).getItems(); assertEquals(3,rows.size());
            number(mode.equals("rolling_30d") ? "4.95" : "24.95",rows.get(0).get("amount__"+mode));
        }
    }
    @Test void observedDefaultPreservesBucketAmountsButFiltersAfterWindow() {
        var p=payload("rolling_7d",null); if(rejectUnsupportedWindow(p)) return;
        var response=execute(p);
        assertTrue(response.getWarnings().stream().anyMatch(w -> w.startsWith("TIMEWINDOW_OBSERVED_DEFAULT")));
        var rows=response.getItems(); assertEquals(3,rows.size()); number("1033.95",rows.get(0).get("amount__rolling_7d"));
        window(p).put("rollingMode","observed"); assertEquals(rows,execute(p).getItems());
    }
    @Test void dailyMtdUsesHiddenMonthAndYearAndDoesNotResetAtDisplayStart() {
        var p=payload("mtd",null); if(rejectUnsupportedWindow(p)) return;
        var rows=execute(p).getItems(); assertEquals(3,rows.size()); number("4.95",rows.get(0).get("amount__mtd")); number("14.85",rows.get(2).get("amount__mtd"));
    }
    @Test void monthlyYtdDoesNotRequireRawDateProjectionOrSplitMonthlyGrain() {
        var p=payload("ytd",null);
        p.put("columns",List.of("invoiceDate$year","invoiceDate$month","amount","amount__ytd"));
        p.put("groupBy",List.of("invoiceDate$year","invoiceDate$month")); p.put("orderBy",List.of("invoiceDate$year","invoiceDate$month"));
        window(p).put("field","invoiceDate$id"); window(p).put("grain","month"); window(p).put("value",List.of("2012-10-01","2012-12-01"));
        if(rejectUnsupportedWindow(p)) return;
        var rows=execute(p).getItems(); assertEquals(2,rows.size()); number("30",rows.get(0).get("amount__ytd")); number("44.85",rows.get(1).get("amount__ytd"));
        assertEquals(Set.of("invoiceDate$year","invoiceDate$month","amount","amount__ytd"),rows.get(0).keySet());
    }
    @Test void closedRangeEmptyRangeAndFinalPagination() {
        var p=payload("rolling_7d","calendar"); window(p).put("range","[]"); window(p).put("value",List.of("2012-11-27","2012-11-29"));
        p.put("start",1);p.put("limit",1);p.put("returnTotal",true);
        var response=execute(p);assertEquals(3L,response.getPagination().getTotalCount());assertEquals(List.of("2012-11-28"),dates(response.getItems()));number("7.92",response.getItems().get(0).get("amount__rolling_7d"));
        p.remove("start");window(p).put("range","[)");window(p).put("value",List.of("2012-11-27","2012-11-27"));assertTrue(execute(p).getItems().isEmpty());
    }
    @Test void hardSourceSliceIsNeverWidenedToRestoreHistory() {
        var p=payload("rolling_7d","calendar");p.put("slice",List.of(Map.of("field","occurredAt","op",">=","value","2012-11-28")));
        var rows=execute(p).getItems();assertEquals(2,rows.size());number("3.96",rows.get(0).get("amount__rolling_7d"));number("9.90",rows.get(1).get("amount__rolling_7d"));
    }
    @Test void businessPartitionsDoNotMixIncludingNullKey() {
        insert(20,"2012-11-27 12:00:00","100");jdbcTemplate.update("UPDATE "+TABLE+" SET category='other' WHERE id=20");
        var rows=execute(payload("rolling_7d","calendar")).getItems();assertEquals(4,rows.size());
        var other=rows.stream().filter(r->"other".equals(r.get("category"))).findFirst().orElseThrow();number("100",other.get("amount__rolling_7d"));
        var first=rows.stream().filter(r->r.get("category")==null).findFirst().orElseThrow();number("3.96",first.get("amount__rolling_7d"));
    }
    @Test void invalidModeAndCalendarWeeklyShapeAreRejectedConsistently() {
        for(String mode:List.of("bad","calendar")) {
            var p=payload("rolling_90d",mode);window(p).put("grain","week");
            for(String phase:List.of("validate","execute")) {
                var ex=assertThrows(RuntimeException.class,()->service.queryModel(MODEL,mapper.toQueryRequest(p),phase,SemanticRequestContext.empty()));
                assertTrue(ex.getMessage().contains("TIMEWINDOW_"),ex.getMessage());
            }
        }
    }

    @Test void aliasesAndPostCalculationKeepOnlyRequestedSchema() {
        var p=payload("rolling_7d","calendar");
        p.put("columns",List.of("occurredAt AS dayOut","amount AS amountOut","amount__rolling_7d","doubled"));
        p.put("groupBy",List.of("occurredAt"));
        p.put("orderBy",List.of("dayOut"));
        p.put("calculatedFields",List.of(Map.of("name","doubled","expression","amount__rolling_7d * 2")));
        var rows=execute(p).getItems();assertEquals(3,rows.size());number("7.92",rows.get(0).get("doubled"));
        assertEquals(Set.of("dayOut","amountOut","amount__rolling_7d","doubled"),rows.get(0).keySet());
    }

    @Test void windowDateDependenciesRespectPhysicalAndFieldPermissions() {
        var p=payload("rolling_7d","calendar");
        var denied=SemanticRequestContext.of(null,(com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.SecurityContext)null,
                null,List.of(new DeniedPhysicalColumn(null,TABLE,"occurred_at")));
        var whitelist=SemanticRequestContext.of(null,(com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.SecurityContext)null,Set.of("category","amount"));
        for(var ctx:List.of(denied,whitelist)) for(String phase:List.of("validate","execute")) {
            var ex=assertThrows(RuntimeException.class,()->service.queryModel(MODEL,mapper.toQueryRequest(p),phase,ctx));
            assertFalse(ex.getMessage().contains("SQLITE_ERROR"),ex.getMessage());
        }
        var allowed=SemanticRequestContext.of(null,(com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.SecurityContext)null,
                Set.of("occurredAt","category","amount","amount__rolling_7d"));
        assertEquals(3,service.queryModel(MODEL,mapper.toQueryRequest(p),"execute",allowed).getItems().size());
    }

    @Test void cumulativeResetsAcrossYearAndMonthWithNoDisplayRange() {
        var p=payload("ytd",null);window(p).remove("value");if(rejectUnsupportedWindow(p))return;
        var rows=execute(p).getItems();number("999",rows.get(0).get("amount__ytd"));number("10",rows.get(1).get("amount__ytd"));
        p=payload("mtd",null);window(p).put("value",List.of("2012-11-29","2012-12-02"));
        rows=execute(p).getItems();assertEquals(2,rows.size());number("14.85",rows.get(0).get("amount__mtd"));number("1000",rows.get(1).get("amount__mtd"));
    }

    @Test void calendarLowerBoundaryIncludesSixDaysAgoAndExcludesSevenDaysAgo() {
        insert(30,"2012-11-20 23:59:59","100"); insert(31,"2012-11-21 00:00:00","10");
        var rows=execute(payload("rolling_7d","calendar")).getItems();
        var day=rows.stream().filter(r->String.valueOf(r.get("occurredAt")).startsWith("2012-11-27")).findFirst().orElseThrow();
        number("13.96",day.get("amount__rolling_7d"));
    }
}

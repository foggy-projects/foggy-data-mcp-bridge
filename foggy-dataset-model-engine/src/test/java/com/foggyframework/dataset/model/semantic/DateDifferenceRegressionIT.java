package com.foggyframework.dataset.model.semantic;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.expression.CalculatedFieldService;
import com.foggyframework.dataset.model.engine.expression.QueryCalculationException;
import com.foggyframework.dataset.model.semantic.domain.*;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.model.semantic.support.SemanticQueryPayloadMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="date-difference.test-isolation=true")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DateDifferenceRegressionIT extends EcommerceTestSupport {
    @Resource SystemBundlesContext bundles;
    @Resource SemanticQueryServiceV3 queries;
    @Resource SemanticQueryPayloadMapper mapper;
    @TempDir static Path root;
    static final String MODEL="DateDifferenceQM";
    static final String[][] DATES={
        {"2026-01-01T10:00:00","2026-01-01T12:00:00"},
        {"2026-01-01T23:30:00","2026-01-02T00:30:00"},
        {"2026-01-01T10:00:00","2026-01-02T22:00:00"},
        {"2026-01-02T22:00:00","2026-01-01T10:00:00"},
        {"2026-01-01T10:00:00","2026-01-01T11:59:59.999"},
        {"2026-01-01T11:59:59.999","2026-01-01T10:00:00"},
        {"2024-01-31T10:00:00","2024-02-29T10:00:00"},
        {"2024-02-29T10:00:00","2025-02-28T10:00:00"},
        {"2026-01-10T10:00:00","2026-04-10T09:59:59"},
        {"2026-01-10T10:00:00","2027-04-10T10:00:00"},
        {"2027-04-10T10:00:00","2026-01-10T10:00:00"},
        {"2024-02-29T10:00:00","2024-01-31T10:00:00"},
        {null,"2026-01-01T10:00:00"}, {"2026-01-01T10:00:00",null}};
    @BeforeAll void setup() throws Exception {
        String type=getDialectKey().equals("sqlserver")?"DATETIME2(3) NULL":getDialectKey().contains("mysql")?"DATETIME(3) NULL":"TIMESTAMP NULL";
        jdbcTemplate.execute("CREATE TABLE issue157_dates(id INTEGER PRIMARY KEY,started_at "+type+",finished_at "+type+")");
        for(int i=0;i<DATES.length;i++)jdbcTemplate.update("INSERT INTO issue157_dates VALUES(?,?,?)",i+1,date(DATES[i][0]),date(DATES[i][1]));
        Files.createDirectories(root.resolve("model"));Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/DateDifference.tm"),"""
            export const model={name:'DateDifference',tableName:'issue157_dates',idColumn:'id',properties:[
            {name:'id',column:'id',type:'INTEGER'}, {name:'startedAt',column:'started_at',type:'DATETIME'},
            {name:'finishedAt',column:'finished_at',type:'DATETIME'}]};
            """);
        Files.writeString(root.resolve("query/DateDifferenceQM.qm"),"""
            const m=loadTableModel('DateDifference');export const queryModel={name:'DateDifferenceQM',loader:'v2',model:m,
            columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.startedAt},{ref:m.finishedAt},
            {ref:m.startedAt,name:'begin',alias:'begin_out'},{ref:m.finishedAt,name:'end',alias:'end_out'}]}],accesses:[]};
            """);
        assertTrue(bundles.addExternalBundle("date-difference-regression","",root.toString(),false));
    }
    private Object date(String value) {return value==null?null:getDialectKey().equals("sqlite")?value.replace('T',' '):LocalDateTime.parse(value);}
    @AfterAll void cleanup() {
        bundles.removeBundle("date-difference-regression");queryModelLoader.clearByNamespace("");jdbcTemplate.execute("DROP TABLE issue157_dates");
    }
    private SemanticQueryRequest request(String expression) {
        return mapper.toQueryRequest(Map.of("columns",List.of("id","difference"),"orderBy",List.of(Map.of("field","id","dir","ASC")),
            "calculatedFields",List.of(Map.of("name","difference","expression",expression)),"limit",100));
    }
    private List<Map<String,Object>> rows(String expression) {
        queries.validateQuery(MODEL,request(expression),SemanticRequestContext.empty());
        assertNotNull(queries.generateSql(MODEL,request(expression),SemanticRequestContext.empty()).getSql());
        return queries.queryModel(MODEL,request(expression),"execute",SemanticRequestContext.empty()).getItems();
    }
    @Test void calendarDaysIgnoreTimeAndElapsedUnitsTruncateTowardZero() {
        var days=rows("DATEDIFF(finishedAt, startedAt)");
        assertEquals(DATES.length,days.size());
        for(int i=0;i<DATES.length;i++) {
            if(DATES[i][0]==null||DATES[i][1]==null)assertNull(days.get(i).get("difference"));
            else assertEquals(ChronoUnit.DAYS.between(LocalDateTime.parse(DATES[i][0]).toLocalDate(),LocalDateTime.parse(DATES[i][1]).toLocalDate()),
                    ((Number)days.get(i).get("difference")).longValue());
        }
        for(String unit:List.of("SECOND","MINUTE","HOUR","DAY","WEEK","MONTH","QUARTER","YEAR")) {
            var actual=rows("TIMESTAMPDIFF('"+unit+"', begin, end)");
            var bare=rows("TIMESTAMPDIFF("+unit+", startedAt, finishedAt)");
            for(int i=0;i<DATES.length;i++) {
                Object value=actual.get(i).get("difference");
                assertEquals(value,bare.get(i).get("difference"));
                if(DATES[i][0]==null||DATES[i][1]==null) {assertNull(value);continue;}
                LocalDateTime start=LocalDateTime.parse(DATES[i][0]),end=LocalDateTime.parse(DATES[i][1]);
                long seconds=Duration.between(start,end).toMillis()/1000;
                // Completed months must include the day/time remainder (Jan31 -> Feb29 is zero).
                long months=(end.getYear()-start.getYear())*12L+end.getMonthValue()-start.getMonthValue();
                int remainder=Comparator.comparingInt(LocalDateTime::getDayOfMonth).thenComparing(LocalDateTime::toLocalTime).compare(end,start);
                if(months>0&&remainder<0)months--;else if(months<0&&remainder>0)months++;
                long expected=switch(unit){case "SECOND"->seconds;case "MINUTE"->seconds/60;case "HOUR"->seconds/3600;
                    case "DAY"->seconds/86400;case "WEEK"->seconds/604800;case "MONTH"->months;case "QUARTER"->months/3;default->months/12;};
                assertEquals(expected,((Number)value).longValue(),unit+" row "+(i+1));
            }
        }
    }
    @Test void invalidArityAndUnitFailBeforeExecutionWithSourcePath() {
        for(String expression:List.of("DATEDIFF(finishedAt)","TIMESTAMPDIFF('BOGUS',startedAt,finishedAt)")) {
            var failure=assertThrows(RuntimeException.class,()->queries.queryModel(MODEL,request(expression),"validate",SemanticRequestContext.empty()));
            var diagnostic=QueryCalculationException.find(failure);
            assertNotNull(diagnostic,failure.toString());
            assertEquals(expression.startsWith("DATEDIFF")?"DATE_FUNCTION_ARITY_INVALID":"DATE_FUNCTION_UNIT_INVALID",diagnostic.getCode());
            assertEquals("calculatedFields[0].expression",diagnostic.getPath());
        }
    }
    @Test void unitIsSyntaxButDateFieldsRemainPermissionDependencies() {
        assertEquals(Set.of("startedAt","finishedAt"),new HashSet<>(CalculatedFieldService.extractColumnReferences("TIMESTAMPDIFF(HOUR,startedAt,finishedAt)")));
        var context=SemanticRequestContext.of(null,(com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext.SecurityContext)null,
                null,List.of(new DeniedPhysicalColumn(null,"issue157_dates","finished_at")));
        for(String unit:List.of("HOUR","'HOUR'")) {
            String expression="TIMESTAMPDIFF("+unit+",begin,end)";
            for(String mode:List.of("validate","execute"))
                assertThrows(RuntimeException.class,()->queries.queryModel(MODEL,request(expression),mode,context));
            assertThrows(RuntimeException.class,()->queries.generateSql(MODEL,request(expression),context));
        }
    }
    @Test void fractionalHoursAndLegacyDateDiffRemainCompatible() {
        var hours=rows("HOURS_BETWEEN(startedAt,finishedAt)");
        assertEquals(2.0,((Number)hours.get(0).get("difference")).doubleValue(),0.000001);
        assertEquals(36.0,((Number)hours.get(2).get("difference")).doubleValue(),0.000001);
        assertEquals(getDialectKey().equals("sqlite")?0L:1L,((Number)rows("DATE_DIFF(finishedAt,startedAt)").get(1).get("difference")).longValue());
    }
    @Test void microsecondsAreExplicitlySupportedOrRejectedByDialect() {
        String expression="TIMESTAMPDIFF(MICROSECOND,startedAt,finishedAt)";
        if(getDialectKey().equals("sqlite")) {
            var error=assertThrows(RuntimeException.class,()->queries.validateQuery(MODEL,request(expression),SemanticRequestContext.empty()));
            assertEquals("DATE_FUNCTION_UNIT_UNSUPPORTED",Objects.requireNonNull(QueryCalculationException.find(error)).getCode());
        } else {
            assertEquals(7200000000L,((Number)rows(expression).get(0).get("difference")).longValue());
        }
    }
}

package com.foggyframework.dataset.model.semantic;

import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in benchmark: run CalendarRollingBenchmark#comparePlans, outside functional gates. */
class CalendarRollingBenchmark extends AccumulatingCalendarRegressionIT {
    @Test void comparePlans() throws Exception {
        String output = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        assertNotNull(output, "Benchmark requires an external evidence directory");
        jdbcTemplate.update("DELETE FROM issue152_calendar");
        LocalDate first=LocalDate.of(2022,1,1), end=LocalDate.of(2025,1,1), start=end.minusDays(30);
        List<LocalDate> dates=new ArrayList<>(); List<Object[]> inserts=new ArrayList<>(); int id=1;
        for(LocalDate d=first;d.isBefore(end);d=d.plusDays(1)) {
            if(java.time.temporal.ChronoUnit.DAYS.between(first,d)%11==0) continue;
            dates.add(d);
            for(int g=0;g<100;g++) for(int detail=0;detail<2;detail++)
                inserts.add(new Object[]{id++,getDialectKey().equals("sqlite") ? d+" 12:00:00" : d.atTime(12,0),"g"+g,new java.math.BigDecimal("0.50")});
        }
        for(int i=0;i<inserts.size();i+=1000)
            jdbcTemplate.batchUpdate("INSERT INTO issue152_calendar VALUES (?,?,?,?)",inserts.subList(i,Math.min(i+1000,inserts.size())));
        jdbcTemplate.execute("CREATE INDEX idx_timeaxis_benchmark ON issue152_calendar(occurred_at,category)");
        List<Map<String,Object>> results=new ArrayList<>();
        for(String scenario:List.of("bounded-100-groups","bounded-1-group","full-history-1-group"))
        for(boolean calculated:List.of(false,true)) for(int days:List.of(7,30,90)) for(boolean nativePlan:List.of(false,true)) {
            boolean bounded=!scenario.startsWith("full-history"); int groupCount=scenario.equals("bounded-100-groups")?100:1;
            String field=calculated?"businessDate":"occurredAt", metric="amount__rolling_"+days+"d";
            Map<String,Object> p=new LinkedHashMap<>();
            p.put("columns",List.of(field,"category",metric));p.put("groupBy",List.of(field,"category"));p.put("limit",10000);
            if(calculated)p.put("calculatedFields",List.of(Map.of("name",field,"expression","date_add(occurredAt, 1, 'day')")));
            p.put("timeWindow",Map.of("field",field,"grain","day","comparison","rolling_"+days+"d","rollingMode","calendar",
                    "value",List.of(start.toString(),end.toString()),"targetMetrics",List.of("amount")));
            if(!bounded) p.put("timeWindow",Map.of("field",field,"grain","day","comparison","rolling_"+days+"d","rollingMode","calendar","targetMetrics",List.of("amount")));
            if(groupCount==1)p.put("slice",List.of(Map.of("field","category","op","=","value","g0")));
            ReflectionTestUtils.setField(timeWindowInterceptor,"calendarRangeEnabled",nativePlan);
            var sql=service.generateSql("AccumulatingCalendarQueryModel",mapper.toQueryRequest(p),SemanticRequestContext.empty());
            assertEquals(nativePlan,sql.getSql().contains("RANGE BETWEEN"));
            List<Double> elapsed=new ArrayList<>();
            for(int attempt=0;attempt<4;attempt++) {
                long began=System.nanoTime(); var rows=jdbcTemplate.queryForList(sql.getSql(),sql.getParams().toArray());
                double ms=(System.nanoTime()-began)/1_000_000.0; if(attempt>0)elapsed.add(ms);
                List<LocalDate> axisDates=dates.stream().map(d->calculated?d.plusDays(1):d).toList();
                Set<String> expected=new HashSet<>();
                for(LocalDate d:axisDates)if(!bounded||!d.isBefore(start)&&d.isBefore(end))for(int g=0;g<groupCount;g++)expected.add(d+"/g"+g);
                for(var row:rows) {
                    LocalDate d=LocalDate.parse(row.get(field).toString().substring(0,10));
                    assertTrue(expected.remove(d+"/"+row.get("category")),"duplicate/unexpected row");
                    long sum=axisDates.stream().filter(a->!a.isBefore(d.minusDays(days-1))&&!a.isAfter(d)).count();
                    assertEquals(0,new java.math.BigDecimal(sum).compareTo(new java.math.BigDecimal(row.get(metric).toString())));
                }
                assertTrue(expected.isEmpty());
            }
            List<Double> sorted=new ArrayList<>(elapsed);Collections.sort(sorted);
            results.add(Map.of("scenario",scenario,"calculatedAxis",calculated,"days",days,"nativeRange",nativePlan,"warmMs",elapsed,"medianMs",sorted.get(1),"sql",sql.getSql()));
        }
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                Path.of(output,"benchmark.json").toFile(),Map.of("database",getDialectKey(),"rawRows",inserts.size(),"groups",100,
                        "oracle","independent civil dates; complete unique row keys and exact decimal amounts","results",results));
    }
}

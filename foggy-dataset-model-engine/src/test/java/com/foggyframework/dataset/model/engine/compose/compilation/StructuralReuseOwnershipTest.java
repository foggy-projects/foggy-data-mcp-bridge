package com.foggyframework.dataset.model.engine.compose.compilation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.plan.*;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.port.ComposeSemanticPlanningPort;
import com.foggyframework.dataset.model.semantic.port.ComposeSqlGeneration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** A structural SQL hit must not act as admission for a different input occurrence. */
class StructuralReuseOwnershipTest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<Arguments> variants() {
        return Stream.of("group", "window", "join").flatMap(kind -> Stream.of(false, true)
                .flatMap(flag -> Stream.of(false, true).map(normalize -> Arguments.of(kind, flag, normalize))));
    }

    @ParameterizedTest @MethodSource("variants")
    void anInvalidOwnerCannotBorrowAnEarlierStructuralHit(String kind, boolean flag, boolean normalize) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try (Connection connection = fixture()) {
            var a = base(); var b = base();
            var id = new PlanColumnRef(a, "id"); var value = new PlanColumnRef(a, "v");
            QueryPlan good, bad;
            if (kind.equals("group")) {
                good = a.fluentGroupBy(id, value); bad = b.fluentGroupBy(id, value);
            } else if (kind.equals("window")) {
                // Deliberately share the expression object; only its input occurrence changes.
                var projected = new PlanColumnRef(null, "v").lag().over(Map.of(
                        "partitionBy", List.of(id), "orderBy", List.of("v"))).as("prior");
                good = a.fluentSelect(projected); bad = b.fluentSelect(projected);
            } else {
                var rightId = new PlanColumnRef(b, "id");
                good = a.innerJoin(b).on(id, rightId); bad = b.innerJoin(a).on(id, rightId);
            }
            assertEquals(PlanHash.planHash(good), PlanHash.planHash(bad), "The fixture must really collide structurally");
            FixturePlanner planner = new FixturePlanner(connection, normalize, kind + "-" + flag + "-" + normalize);
            var valid = planner.compile("valid-reuse", good.union(good, true));
            assertEquals(1, planner.generated.get(), "SQL generation remains deduplicated");
            List<Map<String,Object>> rows = planner.execute(valid);
            assertEquals(4, rows.size());
            if (kind.equals("window")) {
                assertTrue(rows.stream().allMatch(row -> row.get("prior") == null));
            } else {
                assertEquals(List.of(10,10,20,20), rows.stream().map(row -> ((Number)row.get("v")).intValue()).sorted().toList());
            }
            for (var entry : Map.of("invalid-alone", bad,
                    "valid-then-invalid", good.union(bad, true),
                    "invalid-then-valid", bad.union(good, true)).entrySet()) {
                int beforeSql = planner.executed.get();
                assertThrows(RuntimeException.class, () -> planner.compile(entry.getKey(), entry.getValue()));
                assertEquals(beforeSql, planner.executed.get(), "Rejected nodes cannot reach JDBC");
            }
        } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    @Test void legitimateStructuralReuseRetainsPaginationAndDoesNotRegenerateOrAddAliases() throws Exception {
        for (boolean flag : List.of(false,true)) for (boolean normalize : List.of(false,true)) {
            ComposeFeatureFlags.overrideG10Enabled(flag);
            try (Connection connection = fixture()) {
                var a = base(); var b = base();
                var left = DerivedQueryPlan.builder().source(a).columns(List.of("id","v"))
                        .groupBy(List.of("id","v")).orderBy(List.of("id")).limit(1).start(1).build();
                var right = DerivedQueryPlan.builder().source(b).columns(List.of("id","v"))
                        .groupBy(List.of("id","v")).orderBy(List.of("id")).limit(1).start(1).build();
                FixturePlanner planner = new FixturePlanner(connection, normalize, "valid-paging-"+flag+"-"+normalize);
                var reuse = planner.compile("distinct-valid-inputs", left.union(right,true));
                assertEquals(1, planner.generated.get(), "Admission must reuse the base SQL");
                var same = planner.compile("same-valid-input", left.union(left,true));
                assertEquals(reuse.getSql(), same.getSql(), "Admission does not allocate new CTE aliases");
                assertEquals(reuse.getParams(), same.getParams());
                assertEquals(List.of(Map.of("id",2,"v",20),Map.of("id",2,"v",20)), planner.execute(reuse));
            } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
        }
    }

    private static BaseModelPlan base() { return BaseModelPlan.builder().model("InputQM").columns(List.of("id","v")).build(); }

    private static Connection fixture() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE fact(id INTEGER,v INTEGER)");
            statement.execute("INSERT INTO fact VALUES(1,10),(2,20)");
        }
        return connection;
    }

    private static final class FixturePlanner {
        final Connection connection;
        final boolean normalize;
        final String label;
        final AtomicInteger generated = new AtomicInteger(), executed = new AtomicInteger();
        FixturePlanner(Connection connection, boolean normalize, String label) {
            this.connection=connection;this.normalize=normalize;this.label=label;
        }
        ComposedSql compile(String caseName, QueryPlan plan) throws Exception {
            generated.set(0);
            ComposeSemanticPlanningPort port = (model,request,context) -> {
                generated.incrementAndGet();
                return new ComposeSqlGeneration("SELECT id,v FROM fact",List.of(),List.of(),Map.of(),List.of("id","v"));
            };
            var context = ComposeQueryContext.builder().principal(Principal.builder().userId("structural-reuse-test").build())
                    .authorityResolver(request -> AuthorityResolution.builder()
                            .bindings(Map.of("InputQM",ModelBinding.builder().build())).build()).build();
            int before = executed.get(); Map<String,Object> evidence = new LinkedHashMap<>();
            evidence.put("label",label);evidence.put("case",caseName);evidence.put("normalize",normalize);
            try {
                var sql = ComposeSqlCompiler.compilePlanToSql(plan,context,ComposeSqlCompiler.CompileOptions.builder()
                        .planningPort(port).dialect("sqlite").normalizePlan(normalize).build());
                evidence.put("success",true);evidence.put("sql",sql.getSql());evidence.put("params",sql.getParams());
                return sql;
            } catch (RuntimeException error) {
                evidence.put("success",false);evidence.put("error",error.toString());throw error;
            } finally {
                evidence.put("generated",generated.get());evidence.put("executedDuringCompile",executed.get()-before);
                assertEquals(before,executed.get()); record(evidence);
            }
        }
        List<Map<String,Object>> execute(ComposedSql sql) throws Exception {
            executed.incrementAndGet();List<Map<String,Object>> rows=new ArrayList<>();
            try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery(sql.getSql())) {
                while(result.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=result.getMetaData().getColumnCount();i++)row.put(result.getMetaData().getColumnLabel(i),result.getObject(i));
                    rows.add(row);
                }
            }
            record(Map.of("label",label,"case","execute-control","rows",rows));
            return rows;
        }
        void record(Map<String,Object> evidence) throws Exception {
            String directory=System.getenv("FOGGY_TIMEAXIS_EVIDENCE");if(directory==null)return;
            Path folder=Path.of(directory,"structural-reuse-ownership");Files.createDirectories(folder);
            JSON.writerWithDefaultPrettyPrinter().writeValue(folder.resolve("%04d.json".formatted(SEQUENCE.incrementAndGet())).toFile(),evidence);
        }
    }
}

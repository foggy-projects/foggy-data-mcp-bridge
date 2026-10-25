package com.foggyframework.dataset.model.engine.compose.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.schema.ComposeParameterException;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = "compose-derived-inputs.test-isolation=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComposeDerivedInputsRegressionTest extends EcommerceTestSupport {
    @Resource SystemBundlesContext bundles;
    @Resource SemanticQueryServiceV3 semantic;
    @Resource ObjectMapper json;
    @TempDir static Path root;
    final AtomicInteger sequence = new AtomicInteger();

    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE compose_derived_inputs(id INTEGER PRIMARY KEY,grp INTEGER,amount INTEGER)");
        jdbcTemplate.update("INSERT INTO compose_derived_inputs VALUES(1,1,10),(2,1,20),(3,2,30),(4,2,40),(5,3,50),(6,3,60)");
        Files.createDirectories(root.resolve("model")); Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/DerivedInput.tm"), """
                export const model={name:'DerivedInput',tableName:'compose_derived_inputs',idColumn:'id',properties:[
                {name:'id',column:'id',type:'INTEGER'},{name:'grp',column:'grp',type:'INTEGER'},
                {name:'amount',column:'amount',type:'INTEGER'}]};
                """);
        Files.writeString(root.resolve("query/DerivedInputQM.qm"), """
                const m=loadTableModel('DerivedInput');export const queryModel={name:'DerivedInputQM',loader:'v2',model:m,
                columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.grp,name:'category',alias:'categoryOut'},
                {ref:m.amount,name:'revenue',alias:'revenueOut'}]}],accesses:[]};
                """);
        assertTrue(bundles.addExternalBundle("compose-derived-inputs", "", root.toString(), false));
    }

    @AfterAll void cleanup() {
        bundles.removeBundle("compose-derived-inputs"); queryModelLoader.clearByNamespace("");
        jdbcTemplate.execute("DROP TABLE compose_derived_inputs");
    }

    static Stream<Arguments> shapes() {
        return Stream.of("base", "derived", "join", "union").flatMap(shape ->
                Stream.of(false, true).map(g10 -> Arguments.of(shape, g10)));
    }

    String setup(String shape) {
        String source = "const bills=dsl({model:'DerivedInputQM',columns:['category as customer_id','sum(revenue) as bill_amount'],groupBy:['category']});";
        String plan = switch (shape) {
            case "derived" -> "bills.query({columns:['customer_id','bill_amount']})";
            case "union" -> "bills.union(bills)";
            case "join" -> "bills.join(dsl({model:'DerivedInputQM',columns:['category as line_customer_id','sum(revenue) as line_amount'],groupBy:['category']}),'left',[{left:'customer_id',op:'=',right:'line_customer_id'}])";
            default -> "bills";
        };
        return source + "const prev=" + plan + ";";
    }

    String wrap(String entry, String options) {
        return "return {plans:" + ("query".equals(entry) ? "prev.query({" : "dsl({" + entry + ":prev,") + options + "})};";
    }

    ComposeQueryContext context(String policy) {
        return ComposeQueryContext.builder().principal(Principal.builder().userId("derived-input-test").build())
                .authorityResolver(request -> {
                    Map<String, ModelBinding> bindings = new LinkedHashMap<>();
                    for (String name : request.modelNames()) {
                        bindings.put(name, ModelBinding.builder()
                                .fieldAccess("denied".equals(policy) ? List.of("id", "category", "raw_double") : List.of("id", "category", "revenue", "raw_double"))
                                .systemSlice("rows".equals(policy) ? List.of(new SliceRequestDef("id", "<=", 2)) : List.of())
                                .build());
                    }
                    return AuthorityResolution.builder().bindings(bindings).build();
                }).build();
    }

    String dialect() {
        String d = getDialectKey();
        if (d.contains("sqlserver")) return "mssql";
        if (d.contains("postgres")) return "postgres";
        if (d.contains("mysql")) return supportsWindowFunctions() ? "mysql8" : "mysql";
        return d;
    }

    Object run(String label, String script, ComposeScriptService.Mode mode, String policy) throws Exception {
        record("calls", String.format("%04d-%s-%s", sequence.incrementAndGet(), label, mode), Map.of("script", script, "mode", mode, "policy", policy));
        return ComposeScriptService.run(ComposeScriptService.ComposeScriptRequest.builder()
                .mode(mode).script(script).ctx(context(policy)).semanticService(semantic).dialect(dialect()).build()).value();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> checked(String label, String script, String policy) throws Exception {
        var validated = (ComposedSql) ((Map<?, ?>) run(label, script, ComposeScriptService.Mode.VALIDATE, policy)).get("plans");
        var previewed = (ComposedSql) ((Map<?, ?>) run(label, script, ComposeScriptService.Mode.PREVIEW, policy)).get("plans");
        assertEquals(validated.getSql(), previewed.getSql()); assertEquals(validated.getParams(), previewed.getParams());
        var rows = (List<Map<String, Object>>) ((Map<?, ?>) run(label, script, ComposeScriptService.Mode.EXECUTE, policy)).get("plans");
        record("results", label, rows);
        return rows;
    }

    @ParameterizedTest @MethodSource("shapes")
    void publicAndLegacyInputsMatchQueryIncludingAliasesSortAndPagination(String shape, boolean g10) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            String options = "columns:['customer_id','bill_amount','bill_amount * 2 as double_amount'],orderBy:[{field:'double_amount',dir:'desc'}],limit:1,offset:1";
            List<Map<String, Object>> expected = null;
            for (String entry : List.of("query", "model", "source")) {
                var rows = checked("shape-" + shape + "-" + g10 + "-" + entry, setup(shape) + wrap(entry, options), "open");
                assertEquals(1, rows.size());
                assertEquals(2, ((Number) rows.get(0).get("customer_id")).intValue());
                assertEquals(70, ((Number) rows.get(0).get("bill_amount")).intValue());
                assertEquals(140, ((Number) rows.get(0).get("double_amount")).intValue());
                assertEquals(Set.of("customer_id", "bill_amount", "double_amount"), rows.get(0).keySet());
                if (expected != null) assertEquals(expected, rows);
                expected = rows;
            }
        } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    @ParameterizedTest @MethodSource("shapes")
    void fieldAndRowPermissionsFollowOriginalPlans(String shape, boolean g10) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            for (String entry : List.of("query", "model", "source")) {
                String script = setup(shape) + wrap(entry, "columns:['customer_id','bill_amount','bill_amount * 2 as double_amount']");
                for (var mode : ComposeScriptService.Mode.values()) {
                    var denied = assertThrows(RuntimeException.class, () -> run("denied", script, mode, "denied"));
                    assertFalse(denied instanceof ClassCastException);
                    assertTrue(denied.getMessage().contains("revenue"), denied.getMessage());
                }
                var rows = checked("row-policy-" + shape + "-" + g10 + "-" + entry, script, "rows");
                assertEquals(1, rows.size());
                assertEquals(1, ((Number) rows.get(0).get("customer_id")).intValue());
                assertEquals(30, ((Number) rows.get(0).get("bill_amount")).intValue());
                assertEquals(60, ((Number) rows.get(0).get("double_amount")).intValue());
            }
        } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void inlineCalculationThenFilterAndBaseCalculatedFieldsRemainSupported(boolean g10) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            String calculated = setup("base") + "const doubled=dsl({model:prev,columns:['customer_id','bill_amount * 2 as double_amount']});"
                    + "return {plans:doubled.query({columns:['customer_id','double_amount'],slice:[{field:'double_amount',op:'>',value:100}],orderBy:['-double_amount']})};";
            var rows = checked("staged-calculation-" + g10, calculated, "open");
            assertEquals(List.of(220, 140), rows.stream().map(r -> ((Number) r.get("double_amount")).intValue()).toList());
            String baseCalculated = "const prev=dsl({model:'DerivedInputQM',columns:['id','raw_double'],calculatedFields:[{name:'raw_double',expression:'revenue * 2'}]});"
                    + wrap("model", "columns:['id','raw_double'],orderBy:['id'],limit:1");
            var baseRows = checked("base-calculation-" + g10, baseCalculated, "open");
            assertEquals(20, ((Number) baseRows.get(0).get("raw_double")).intValue());
            for (var mode : ComposeScriptService.Mode.values())
                assertThrows(RuntimeException.class, () -> run("base-calculation-denied", baseCalculated, mode, "denied"));
        } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void validatePreviewAndRejectedOptionsNeverQueryData(boolean g10) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        var model = (JdbcQueryModelImpl) queryModelLoader.getJdbcQueryModel("DerivedInputQM", "");
        DataSource original = model.getDataSource();
        AtomicInteger prepared = new AtomicInteger(), executed = new AtomicInteger();
        model.setDataSource(new DelegatingDataSource(original) {
            public Connection getConnection() throws SQLException { return observe(super.getConnection(), prepared, executed); }
        });
        try {
            for (String shape : List.of("base", "derived", "join", "union")) {
                for (String entry : List.of("query", "model", "source")) {
                    String valid = setup(shape) + wrap(entry, "columns:['customer_id','bill_amount']");
                    for (var mode : List.of(ComposeScriptService.Mode.VALIDATE, ComposeScriptService.Mode.PREVIEW))
                        assertNotNull(run("no-sql", valid, mode, "open"));
                    for (String option : List.of("calculatedFields:[{name:'x',expression:'revenue * 2'}]", "having:[{field:'bill_amount',op:'>',value:0}]")) {
                        String invalid = setup(shape) + wrap(entry, "columns:['customer_id','bill_amount']," + option);
                        for (var mode : ComposeScriptService.Mode.values()) {
                            var error = assertThrows(ComposeParameterException.class, () -> run("rejected-option", invalid, mode, "open"));
                            assertEquals("DERIVED_PARAMETER_UNSUPPORTED", error.code());
                        }
                    }
                    // Referencing a hidden source column remains a real visibility error, distinct from unsupported options.
                    String hidden = setup(shape) + wrap(entry, "columns:['revenue * 2 as leaked']");
                    for (var mode : ComposeScriptService.Mode.values()) assertThrows(RuntimeException.class, () -> run("hidden", hidden, mode, "open"));
                }
            }
            assertEquals(0, prepared.get()); assertEquals(0, executed.get());
            record("results", "no-sql-" + g10, Map.of("prepared", prepared.get(), "executed", executed.get()));
            run("observer-control", setup("base") + wrap("model", "columns:['customer_id','bill_amount']"), ComposeScriptService.Mode.EXECUTE, "open");
            assertTrue(executed.get() > 0, "positive control must observe real business SQL");
        } finally { model.setDataSource(original); ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    void record(String group, String label, Object value) throws Exception {
        String evidence = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        if (evidence == null || evidence.isBlank()) return;
        Path dir = Path.of(evidence, "compose-derived-input-" + group); Files.createDirectories(dir);
        json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(label + ".json").toFile(), value);
    }

    static Stream<Arguments> emptyOptions() {
        return Stream.of(false, true).flatMap(g10 -> Stream.of("null", "[]").map(value -> Arguments.of(g10, value)));
    }

    @ParameterizedTest @MethodSource("emptyOptions")
    void emptyDerivedDefinitionsKeepExecutionCompatibility(boolean g10, String value) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            for (String entry : List.of("query", "model", "source")) {
                var rows = checked("empty-" + g10 + "-" + ("null".equals(value) ? "null" : "array") + "-" + entry,
                        setup("base") + wrap(entry, "columns:['customer_id','bill_amount'],orderBy:['customer_id'],calculatedFields:"
                                + value + ",having:" + value), "open");
                assertEquals(List.of(30, 70, 110), rows.stream().map(r -> ((Number) r.get("bill_amount")).intValue()).toList());
            }
        } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    Connection observe(Connection connection, AtomicInteger prepared, AtomicInteger executed) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
            try {
                Object result = m.invoke(connection, a);
                if (!(result instanceof Statement statement)) return result;
                String sql = a != null && a.length > 0 && a[0] instanceof String text ? text : "";
                if (business(sql)) prepared.incrementAndGet();
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class}, (q, sm, sa) -> {
                    String actual = sa != null && sa.length > 0 && sa[0] instanceof String text ? text : sql;
                    if (sm.getName().startsWith("execute") && business(actual)) executed.incrementAndGet();
                    try { return sm.invoke(statement, sa); } catch (InvocationTargetException e) { throw e.getCause(); }
                });
            } catch (InvocationTargetException e) { throw e.getCause(); }
        });
    }

    boolean business(String sql) { return sql.toLowerCase(Locale.ROOT).contains("compose_derived_inputs"); }
}

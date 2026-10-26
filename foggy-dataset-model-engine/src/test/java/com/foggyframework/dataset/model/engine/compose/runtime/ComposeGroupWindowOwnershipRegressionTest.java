package com.foggyframework.dataset.model.engine.compose.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
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

import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties = "compose-group-window.test-isolation=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComposeGroupWindowOwnershipRegressionTest extends EcommerceTestSupport {
    @Resource SystemBundlesContext bundles;
    @Resource SemanticQueryServiceV3 semantic;
    @Resource ObjectMapper json;
    @TempDir static Path root;
    JdbcQueryModelImpl model;
    DataSource original;
    final AtomicInteger prepared = new AtomicInteger(), executed = new AtomicInteger(), sequence = new AtomicInteger();
    final String prefix = "const p=dsl({model:'GroupWindowInputQM',columns:['id','category as cat','revenue as v']});"
            + "const w=p.query({columns:['cat as v','v as renamed']});";

    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE compose_group_window(id INTEGER PRIMARY KEY,grp INTEGER,amount INTEGER)");
        jdbcTemplate.update("INSERT INTO compose_group_window VALUES(1,1,10),(2,1,20),(3,2,30),(4,2,40),(5,3,50),(6,3,60)");
        Files.createDirectories(root.resolve("model")); Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/GroupWindowInput.tm"), """
                export const model={name:'GroupWindowInput',tableName:'compose_group_window',idColumn:'id',properties:[
                {name:'id',column:'id',type:'INTEGER'},{name:'grp',column:'grp',type:'INTEGER'},
                {name:'amount',column:'amount',type:'INTEGER'}]};
                """);
        Files.writeString(root.resolve("query/GroupWindowInputQM.qm"), """
                const m=loadTableModel('GroupWindowInput');export const queryModel={name:'GroupWindowInputQM',loader:'v2',model:m,
                columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.grp,name:'category',alias:'categoryOut'},
                {ref:m.amount,name:'revenue',alias:'revenueOut'}]}],accesses:[]};
                """);
        assertTrue(bundles.addExternalBundle("compose-group-window", "", root.toString(), false));
        model = (JdbcQueryModelImpl) queryModelLoader.getJdbcQueryModel("GroupWindowInputQM", "");
        original = model.getDataSource();
        model.setDataSource(new DelegatingDataSource(original) {
            public Connection getConnection() throws SQLException { return observe(super.getConnection()); }
        });
    }

    @AfterAll void cleanup() {
        if (model != null) model.setDataSource(original);
        bundles.removeBundle("compose-group-window"); queryModelLoader.clearByNamespace("");
        jdbcTemplate.execute("DROP TABLE compose_group_window");
    }

    String dialect() {
        String d = getDialectKey();
        if (d.contains("sqlserver")) return "mssql";
        if (d.contains("postgres")) return "postgres";
        if (d.contains("mysql")) return supportsWindowFunctions() ? "mysql8" : "mysql";
        return d;
    }

    ComposeQueryContext context(boolean denied) {
        return ComposeQueryContext.builder().principal(Principal.builder().userId("group-window-test").build())
                .authorityResolver(request -> {
                    Map<String, ModelBinding> bindings = new LinkedHashMap<>();
                    for (String name : request.modelNames()) bindings.put(name, ModelBinding.builder()
                            .fieldAccess(denied ? List.of("id", "category") : List.of("id", "category", "revenue")).build());
                    return AuthorityResolution.builder().bindings(bindings).build();
                }).build();
    }

    Object run(String label, String script, ComposeScriptService.Mode mode, boolean denied) throws Exception {
        prepared.set(0); executed.set(0);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("label", label); evidence.put("script", script); evidence.put("mode", mode); evidence.put("denied", denied);
        try {
            Object value = ComposeScriptService.run(ComposeScriptService.ComposeScriptRequest.builder()
                    .mode(mode).script(script).ctx(context(denied)).semanticService(semantic).dialect(dialect()).build()).value();
            evidence.put("result", value); return value;
        } catch (Exception failure) {
            evidence.put("error", failure.toString()); throw failure;
        } finally {
            evidence.put("prepared", prepared.get()); evidence.put("executed", executed.get());
            if (mode != ComposeScriptService.Mode.EXECUTE) { assertEquals(0, prepared.get()); assertEquals(0, executed.get()); }
            String directory = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
            if (directory != null) {
                Path out = Path.of(directory, "group-window-ownership"); Files.createDirectories(out);
                json.writerWithDefaultPrettyPrinter().writeValue(out.resolve(String.format("%04d-%s-%s.json", sequence.incrementAndGet(), label, mode)).toFile(), evidence);
            }
        }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> checked(String label, String script) throws Exception {
        var validated = (ComposedSql) ((Map<?, ?>) run(label, script, ComposeScriptService.Mode.VALIDATE, false)).get("plans");
        var previewed = (ComposedSql) ((Map<?, ?>) run(label, script, ComposeScriptService.Mode.PREVIEW, false)).get("plans");
        assertEquals(validated.getSql(), previewed.getSql()); assertEquals(validated.getParams(), previewed.getParams());
        return (List<Map<String, Object>>) ((Map<?, ?>) run(label, script, ComposeScriptService.Mode.EXECUTE, false)).get("plans");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void groupingResolvesRenamedProducerInsteadOfSameNamedSibling(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try {
            // Every source output is grouped, so the SQL is valid on strict GROUP BY dialects too.
            var rows = checked("group-renamed-" + flag, prefix + "return {plans:w.groupBy(p.v,w.v)};");
            assertEquals(6, rows.size());
            assertEquals(List.of(10,20,30,40,50,60), rows.stream().map(r -> ((Number) r.get("renamed")).intValue()).sorted().toList());
            var control = checked("group-string-" + flag, prefix + "return {plans:w.groupBy('renamed','v')};");
            assertEquals(control.size(), rows.size());
            String nested = "const a=dsl({model:'GroupWindowInputQM',columns:['id as aid','revenue as av'],slice:[{field:'id',op:'=',value:1}]});"
                    + "const b=dsl({model:'GroupWindowInputQM',columns:['id as bid','revenue as bv'],slice:[{field:'id',op:'=',value:3}]});"
                    + "const j=a.join(b,'inner',[{left:'aid',op:'<',right:'bid'}]);const w=j.query({columns:['av as bv','bv as renamed']});"
                    + "return {plans:w.groupBy(b.bv,w.bv)};";
            var joined = checked("group-nested-join-" + flag, nested);
            assertEquals(30, ((Number) joined.get(0).get("renamed")).intValue());
        } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void windowKeysResolveTheirOwnProducerThroughRenaming(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try {
            String script = prefix + "return {plans:w.select(w.v,w.renamed,w.renamed.lag().over({partitionBy:[p.v],orderBy:[p.cat]}).as('prior'))};";
            if (!supportsWindowFunctions()) {
                for (var mode : ComposeScriptService.Mode.values()) {
                    var failure = assertThrows(RuntimeException.class, () -> run("window-unsupported-" + flag, script, mode, false));
                    assertTrue(failure.getMessage().contains("outer-window-not-supported"), failure.toString());
                    assertEquals(0, prepared.get()); assertEquals(0, executed.get());
                }
                return;
            }
            var rows = checked("window-renamed-" + flag, script);
            assertEquals(6, rows.size()); assertTrue(rows.stream().allMatch(row -> row.get("prior") == null));
            String ordered = prefix + "return {plans:w.select(w.renamed,w.renamed.lag().over({orderBy:[p.v]}).as('prior'))};";
            var byAmount = checked("window-order-" + flag, ordered).stream()
                    .sorted(Comparator.comparingInt(r -> ((Number) r.get("renamed")).intValue())).toList();
            assertNull(byAmount.get(0).get("prior"));
            for (int i = 1; i < byAmount.size(); i++) assertEquals(i * 10, ((Number) byAmount.get(i).get("prior")).intValue());
        } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void groupingAndWindowKeysRejectExternalHiddenAndOpaqueUnionOwners(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try {
            String outsiders = prefix + "const outside=dsl({model:'GroupWindowInputQM',columns:['revenue as v']});";
            List<String> invalid = List.of(outsiders + "return {plans:w.groupBy(outside.v)};",
                    prefix + "return {plans:w.groupBy(p.id)};",
                    prefix + "const u=p.union(p,{all:true});return {plans:u.groupBy(p.v)};",
                    outsiders + "return {plans:w.select(w.renamed.lag().over({partitionBy:[outside.v],orderBy:['renamed']}).as('prior'))};",
                    prefix + "return {plans:w.select(w.renamed.lag().over({orderBy:[p.id]}).as('prior'))};",
                    // Typed p.cat resolves to w.v, but its name must not authorize a missing bare key.
                    prefix + "return {plans:w.select(w.renamed.lag().over({partitionBy:[p.cat],orderBy:['cat']}).as('prior'))};",
                    prefix + "return {plans:w.select(w.renamed.lag().over({partitionBy:['cat'],orderBy:[p.cat]}).as('prior'))};",
                    prefix + "const u=p.union(p,{all:true});return {plans:u.select(u.v.lag().over({orderBy:[p.id]}).as('prior'))};");
            for (String script : invalid) for (var mode : ComposeScriptService.Mode.values()) {
                assertThrows(RuntimeException.class, () -> run("invalid-owner-" + flag, script, mode, false));
                assertEquals(0, prepared.get()); assertEquals(0, executed.get());
            }
            for (var mode : ComposeScriptService.Mode.values()) {
                assertThrows(RuntimeException.class, () -> run("denied-owner-" + flag,
                        prefix + "return {plans:w.groupBy(p.v,w.v)};", mode, true));
                assertEquals(0, prepared.get()); assertEquals(0, executed.get());
            }
        } finally { ComposeFeatureFlags.overrideG10Enabled(null); }
    }

    Connection observe(Connection connection) {
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (p,m,a) -> {
            try {
                Object result = m.invoke(connection,a);
                if (!(result instanceof Statement statement)) return result;
                String sql = a != null && a.length > 0 && a[0] instanceof String text ? text : "";
                if (sql.toLowerCase(Locale.ROOT).contains("compose_group_window")) prepared.incrementAndGet();
                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class}, (q,sm,sa) -> {
                    String actual = sa != null && sa.length > 0 && sa[0] instanceof String text ? text : sql;
                    if (sm.getName().startsWith("execute") && actual.toLowerCase(Locale.ROOT).contains("compose_group_window")) executed.incrementAndGet();
                    try { return sm.invoke(statement,sa); } catch (InvocationTargetException e) { throw e.getCause(); }
                });
            } catch (InvocationTargetException e) { throw e.getCause(); }
        });
    }
}

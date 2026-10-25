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

@TestPropertySource(properties = "compose-ownerships.test-isolation=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComposeOwnershipGovernanceRegressionTest extends EcommerceTestSupport {
    @Resource SystemBundlesContext bundles;
    @Resource SemanticQueryServiceV3 semantic;
    @Resource ObjectMapper json;
    @TempDir static Path root;
    final AtomicInteger sequence = new AtomicInteger();

    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE compose_ownership(id INTEGER PRIMARY KEY,grp INTEGER,amount INTEGER)");
        jdbcTemplate.update("INSERT INTO compose_ownership VALUES(1,1,10),(2,1,20),(3,2,30),(4,2,40),(5,3,50),(6,3,60),(7,NULL,NULL),(8,NULL,80)");
        Files.createDirectories(root.resolve("model")); Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/OwnershipInput.tm"), """
                export const model={name:'OwnershipInput',tableName:'compose_ownership',idColumn:'id',properties:[
                {name:'id',column:'id',type:'INTEGER'},{name:'grp',column:'grp',type:'INTEGER'},
                {name:'amount',column:'amount',type:'INTEGER'}]};
                """);
        Files.writeString(root.resolve("query/OwnershipInputQM.qm"), """
                const m=loadTableModel('OwnershipInput');export const queryModel={name:'OwnershipInputQM',loader:'v2',model:m,
                columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.grp,name:'category',alias:'categoryOut'},
                {ref:m.amount,name:'revenue',alias:'revenueOut'}]}],accesses:[]};
                """);
        assertTrue(bundles.addExternalBundle("compose-ownerships", "", root.toString(), false));
    }

    @AfterAll void cleanup() {
        bundles.removeBundle("compose-ownerships"); queryModelLoader.clearByNamespace("");
        jdbcTemplate.execute("DROP TABLE compose_ownership");
    }

    ComposeQueryContext context(String policy) {
        return ComposeQueryContext.builder().principal(Principal.builder().userId("derived-input-test").build())
                .authorityResolver(request -> {
                    Map<String, ModelBinding> bindings = new LinkedHashMap<>();
                    for (String name : request.modelNames()) {
                        bindings.put(name, ModelBinding.builder()
                                .fieldAccess("denied".equals(policy) ? List.of("id", "category", "raw_double", "running", "rn", "copy") : List.of("id", "category", "revenue", "raw_double", "running", "rn", "copy"))
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

    String base = "const a=dsl({model:'OwnershipInputQM',columns:['id as aid','revenue as av'],slice:[{field:'id',op:'=',value:1}]});"
            +"const b=dsl({model:'OwnershipInputQM',columns:['id as bid','revenue as bv'],slice:[{field:'id',op:'=',value:2}]});"
            +"const j=a.join(b,'inner',[{left:'aid',op:'<',right:'bid'}]);";

    @ParameterizedTest @ValueSource(booleans={false,true})
    void nestedOwnersAndOpaqueUnionAreConsistent(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try {
            String wrapper="const w=j.query({columns:['aid','av','bid','bv']});";
            for(String expr:List.of("'a.bv as picked'","a.bv.as('picked')")) {
                String script=base+wrapper+"return {plans:w.query({columns:["+expr+"]})};";
                for(var mode:ComposeScriptService.Mode.values())assertThrows(RuntimeException.class,()->run("wrong-owner",script,mode,"open"));
            }
            for(String expr:List.of("'b.bv as picked'","b.bv.as('picked')")) {
                var rows=checked("valid-owner-"+flag+"-"+expr.hashCode(),base+wrapper+"return {plans:w.query({columns:["+expr+"]})};","open");
                assertEquals(20,((Number)rows.get(0).get("picked")).intValue());
            }
            String duplicate="const a=dsl({model:'OwnershipInputQM',columns:['id','revenue as val'],slice:[{field:'id',op:'=',value:1}]});"
                    +"const b=dsl({model:'OwnershipInputQM',columns:['id','revenue as val'],slice:[{field:'id',op:'=',value:2}]});"
                    +"const j=a.join(b,'inner',[{left:'id',op:'<',right:'id'}]);"
                    +"const w=j.query({columns:[a.id.as('left_id'),b.val.as('right_value')]});return {plans:w.query({columns:[b.val.as('picked')]})};";
            assertEquals(20,((Number)checked("duplicate-renamed-"+flag,duplicate,"open").get(0).get("picked")).intValue());
            var renamed=checked("renamed-"+flag,base+"const w=j.query({columns:['aid','bv as renamed']});return {plans:w.query({columns:['b.bv as picked']})};","open");
            assertEquals(20,((Number)renamed.get(0).get("picked")).intValue());
            String u="const a=dsl({model:'OwnershipInputQM',columns:['id as uid','revenue as val'],slice:[{field:'id',op:'=',value:1}]});"
                    +"const b=dsl({model:'OwnershipInputQM',columns:['id as uid','revenue as val'],slice:[{field:'id',op:'=',value:2}]});"
                    +"const u=a.union(b,{all:true});const c=dsl({model:'OwnershipInputQM',columns:['id as cid'],slice:[{field:'id',op:'=',value:3}]});"
                    +"const j=u.join(c,'inner',[{left:'uid',op:'<',right:'cid'}]);";
            for(String expr:List.of("'b.val as picked'","b.val.as('picked')"))for(var mode:ComposeScriptService.Mode.values())
                assertThrows(RuntimeException.class,()->run("union-branch",u+"return {plans:j.query({columns:["+expr+"]})};",mode,"open"));
            String shared="const a=dsl({model:'OwnershipInputQM',columns:['id as uid','revenue as val'],slice:[{field:'id',op:'=',value:1}]});"
                    +"const b=dsl({model:'OwnershipInputQM',columns:['id as uid','revenue as val'],slice:[{field:'id',op:'=',value:2}]});"
                    +"const u=a.union(b,{all:true});const c=b.query({columns:['uid as bid','val as bv']});"
                    +"const j=u.join(c,'inner',[{left:'uid',op:'<',right:'bid'}]);return {plans:j.query({columns:['b.val as picked']})};";
            assertEquals(20,((Number)checked("union-reused-right-owner-"+flag,shared,"open").get(0).get("picked")).intValue());
            var rows=checked("union-owner-"+flag,u+"return {plans:j.query({columns:['u.val as picked'],orderBy:['picked']})};","open");
            assertEquals(List.of(10,20),rows.stream().map(row->((Number)row.get("picked")).intValue()).toList());
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void identityProjectionWinsOverAdditionalRenamedCopies(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try {
            for(String columns:List.of("['aid','bv','bv as copy']","['aid','bv as copy','bv']")) {
                String script=base+"const w=j.query({columns:"+columns+"});return {plans:w.query({columns:['b.bv as picked']})};";
                var rows=checked("identity-copy-"+flag+"-"+columns.hashCode(),script,"open");
                assertEquals(List.of(Map.of("picked",20)),rows);
            }
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void typedPassthroughRetainsTheRealProducer(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try {
            String typed=base+"const w=j.query({columns:[a.aid,b.bv]});";
            for(String expr:List.of("'b.bv as picked'","b.bv.as('picked')")) {
                var rows=checked("typed-passthrough-"+flag+"-"+expr.hashCode(),typed+"return {plans:w.query({columns:["+expr+"]})};","open");
                assertEquals(List.of(Map.of("picked",20)),rows);
            }
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void validateAndPreviewDoNotPrepareOrExecuteBusinessSql(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        var model=(JdbcQueryModelImpl)queryModelLoader.getJdbcQueryModel("OwnershipInputQM", "");
        DataSource original=model.getDataSource();
        AtomicInteger prepared=new AtomicInteger(),executed=new AtomicInteger();
        model.setDataSource(new DelegatingDataSource(original) {
            public Connection getConnection() throws SQLException {return observe(super.getConnection(),prepared,executed);}
        });
        try {
            String script=base+"const w=j.query({columns:['aid','bv']});return {plans:w.query({columns:['b.bv as picked']})};";
            String invalid=base+"const w=j.query({columns:['aid','bv']});return {plans:w.query({columns:['a.bv as picked']})};";
            for(var mode:List.of(ComposeScriptService.Mode.VALIDATE,ComposeScriptService.Mode.PREVIEW))run("zero-sql-valid",script,mode,"open");
            for(var mode:ComposeScriptService.Mode.values()) {
                assertThrows(RuntimeException.class,()->run("zero-sql-invalid",invalid,mode,"open"));
                assertThrows(RuntimeException.class,()->run("zero-sql-denied",script,mode,"denied"));
            }
            assertEquals(0,prepared.get());assertEquals(0,executed.get());
            record("results","zero-sql-"+flag,Map.of("prepared",prepared.get(),"executed",executed.get()));
            run("sql-positive-control",script,ComposeScriptService.Mode.EXECUTE,"open");
            assertTrue(executed.get()>0);
        } finally {model.setDataSource(original);ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void nullSafeAndCaseCollisionsHaveExplicitContracts(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try {
            String prefix="const a=dsl({model:'OwnershipInputQM',columns:['id as aid','category as ag'],slice:[{field:'id',op:'=',value:7}]});"
                    +"const b=dsl({model:'OwnershipInputQM',columns:['id as bid','category as bg'],slice:[{field:'id',op:'=',value:8}]});";
            for(boolean safe:List.of(false,true)) {
                var rows=checked("nullsafe-"+flag+"-"+safe,prefix+"return {plans:a.join(b,'inner',[{left:'ag',op:'=',right:'bg',nullSafe:"+safe+"}])};","open");
                assertEquals(safe?1:0,rows.size());
            }
            for(var mode:ComposeScriptService.Mode.values()) {
                assertThrows(RuntimeException.class,()->run("nullsafe-invalid",prefix+"return {plans:a.join(b,'inner',[{left:'ag',op:'<',right:'bg',nullSafe:true}])};",mode,"open"));
                assertThrows(RuntimeException.class,()->run("case-collision","return {plans:dsl({model:'OwnershipInputQM',columns:['id as X','revenue as x']})};",mode,"open"));
            }
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void calculationMetadataAndPermissionDependenciesSurvive(boolean flag) throws Exception {
        ComposeFeatureFlags.overrideG10Enabled(flag);
        try {
            String copy="return {plans:dsl({model:'OwnershipInputQM',columns:['id','copy'],calculatedFields:[{name:'copy',expression:'revenue',emptyDefault:0}],slice:[{field:'id',op:'=',value:7}]})};";
            assertEquals(0,((Number)checked("empty-default-"+flag,copy,"open").get(0).get("copy")).intValue());
            String window="return {plans:dsl({model:'OwnershipInputQM',columns:['id','running'],calculatedFields:[{name:'running',expression:'SUM(revenue)',partitionBy:['category'],windowOrderBy:[{field:'id',dir:'asc'}],windowFrame:'ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW'}],orderBy:['id']})};";
            if ("mssql".equals(dialect())) {
                var model=(JdbcQueryModelImpl)queryModelLoader.getJdbcQueryModel("OwnershipInputQM", "");
                DataSource original=model.getDataSource();
                AtomicInteger prepared=new AtomicInteger(),executed=new AtomicInteger();
                model.setDataSource(new DelegatingDataSource(original) {
                    public Connection getConnection() throws SQLException {return observe(super.getConnection(),prepared,executed);}
                });
                try {
                    for(var mode:ComposeScriptService.Mode.values()) {
                        var error=assertThrows(com.foggyframework.dataset.model.engine.compose.compilation.ComposeCompileException.class,
                                ()->run("window-cte-shape-unsupported",window,mode,"open"));
                        assertEquals(com.foggyframework.dataset.model.engine.compose.compilation.ComposeCompileErrorCodes.RELATION_CTE_HOIST_UNSUPPORTED,error.code());
                    }
                    assertEquals(0,prepared.get());assertEquals(0,executed.get());
                    record("results","window-cte-unsupported-"+flag,Map.of("prepared",prepared.get(),"executed",executed.get(),"code","RELATION_CTE_HOIST_UNSUPPORTED"));
                } finally {model.setDataSource(original);}
                var request=new com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest();
                request.setColumns(List.of("id","category","revenue","running"));
                // Native SQL Server windows require an explicit stage grain for hidden dependencies.
                request.setGroupBy(List.of(
                        new com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest.GroupByItem("id",null),
                        new com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest.GroupByItem("category",null),
                        new com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest.GroupByItem("revenue",null)));
                var field=new com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef("running","SUM(revenue)");
                field.setPartitionBy(List.of("category"));
                field.setWindowOrderBy(List.of(new com.foggyframework.dataset.model.def.query.request.WindowOrderDef("id","asc")));
                field.setWindowFrame("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW");
                request.setCalculatedFields(List.of(field));
                var nativeRows=semantic.queryModel("OwnershipInputQM",request,"execute",
                        com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext.empty()).getItems();
                assertEquals(8,nativeRows.size());
                assertEquals(30,((Number)nativeRows.stream().filter(row->((Number)row.get("id")).intValue()==2).findFirst().orElseThrow().get("running")).intValue());
                assertEquals(70,((Number)nativeRows.stream().filter(row->((Number)row.get("id")).intValue()==4).findFirst().orElseThrow().get("running")).intValue());
                record("results","window-native-mssql-"+flag,nativeRows.stream().sorted(Comparator.comparingInt(row->((Number)row.get("id")).intValue())).toList());
            } else if(supportsWindowFunctions()) {
                var rows=checked("window-"+flag,window,"open");
                assertEquals(30,((Number)rows.get(1).get("running")).intValue());
                assertEquals(70,((Number)rows.get(3).get("running")).intValue());
            } else for(var mode:ComposeScriptService.Mode.values())assertThrows(RuntimeException.class,()->run("window-unsupported",window,mode,"open"));
            for(String dependency:List.of("partitionBy:['revenue'],windowOrderBy:[{field:'id'}]","windowOrderBy:[{field:'revenue'}]")) {
                String denied="return {plans:dsl({model:'OwnershipInputQM',columns:['id','rn'],calculatedFields:[{name:'rn',expression:'ROW_NUMBER()',"+dependency+"}]})};";
                for(var mode:ComposeScriptService.Mode.values())assertThrows(RuntimeException.class,()->run("window-permission",denied,mode,"denied"));
            }
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    void record(String group, String label, Object value) throws Exception {
        String evidence = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        if (evidence == null || evidence.isBlank()) return;
        Path dir = Path.of(evidence, "compose-ownership-" + group); Files.createDirectories(dir);
        json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(label + ".json").toFile(), value);
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

    boolean business(String sql) { return sql.toLowerCase(Locale.ROOT).contains("compose_ownership"); }
}

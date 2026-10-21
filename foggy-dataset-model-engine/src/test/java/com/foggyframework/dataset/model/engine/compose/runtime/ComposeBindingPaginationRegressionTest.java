package com.foggyframework.dataset.model.engine.compose.runtime;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.ecommerce.EcommerceTestSupport;
import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.compose.ComposedSql;
import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import com.foggyframework.dataset.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.model.semantic.service.SemanticQueryServiceV3;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestPropertySource(properties="compose-pagination.test-isolation=true")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComposeBindingPaginationRegressionTest extends EcommerceTestSupport {
    @Resource SystemBundlesContext bundles;
    @Resource SemanticQueryServiceV3 semantic;
    @TempDir static Path root;
    @BeforeAll void setup() throws Exception {
        jdbcTemplate.execute("CREATE TABLE compose_page(id INTEGER PRIMARY KEY,grp INTEGER,amount INTEGER)");
        jdbcTemplate.update("INSERT INTO compose_page VALUES(1,1,10),(2,1,20),(3,2,30),(4,2,40),(5,3,50),(6,3,60)");
        Files.createDirectories(root.resolve("model")); Files.createDirectories(root.resolve("query"));
        Files.writeString(root.resolve("model/Page.tm"), """
            export const model={name:'Page',tableName:'compose_page',idColumn:'id',properties:[
            {name:'id',column:'id',type:'INTEGER'},{name:'grp',column:'grp',type:'INTEGER'},
            {name:'amount',column:'amount',type:'INTEGER'}]};
            """);
        Files.writeString(root.resolve("query/PageQM.qm"), """
            const m=loadTableModel('Page');export const queryModel={name:'PageQM',loader:'v2',model:m,
            columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.id,name:'queryId',alias:'resultId'},{ref:m.grp},{ref:m.amount},{ref:m.amount,name:'queryAmount',alias:'resultAmount'}]}],accesses:[]};
            """);
        Files.writeString(root.resolve("query/PageWindowQM.qm"), """
            const m=loadTableModel('Page');export const queryModel={name:'PageWindowQM',loader:'v2',model:m,
            columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.amount},
            {name:'ranked',formula:'ROW_NUMBER()',type:'INTEGER',windowOrderBy:[{field:'amount',dir:'DESC'}]}]}],accesses:[]};
            """);
        assertTrue(bundles.addExternalBundle("compose-page-regression","",root.toString(),false));
    }
    @AfterAll void cleanup() {
        bundles.removeBundle("compose-page-regression"); queryModelLoader.clearByNamespace("");
        jdbcTemplate.execute("DROP TABLE compose_page");
    }
    String dialect() {
        String d=getDialectKey();
        if(d.contains("sqlserver"))return "mssql";
        if(d.contains("postgres"))return "postgres";
        if(d.contains("mysql"))return supportsWindowFunctions()?"mysql8":"mysql";
        return d;
    }
    ComposeQueryContext context(boolean restricted) {
        return ComposeQueryContext.builder().principal(Principal.builder().userId("compose-test").build())
            .authorityResolver(request -> {
                Map<String,ModelBinding> bindings=new LinkedHashMap<>();
                for(String name:request.modelNames()) bindings.put(name, restricted
                    ? ModelBinding.builder().fieldAccess(List.of("id","grp","queryId")).build():ModelBinding.builder().build());
                return AuthorityResolution.builder().bindings(bindings).build();
            }).build();
    }
    Object run(String script, ComposeScriptService.Mode mode, boolean restricted) {
        return ComposeScriptService.run(ComposeScriptService.ComposeScriptRequest.builder()
            .mode(mode).script(script).ctx(context(restricted)).semanticService(semantic).dialect(dialect()).build()).value();
    }
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> rows(String script) {
        Object validate=run(script,ComposeScriptService.Mode.VALIDATE,false);
        Object preview=run(script,ComposeScriptService.Mode.PREVIEW,false);
        ComposedSql a=(ComposedSql)((Map<?,?>)validate).get("plans");
        ComposedSql b=(ComposedSql)((Map<?,?>)preview).get("plans");
        assertEquals(a.getSql(),b.getSql()); assertEquals(a.getParams(),b.getParams());
        return (List<Map<String,Object>>)((Map<?,?>)run(script,ComposeScriptService.Mode.EXECUTE,false)).get("plans");
    }
    List<Integer> ids(List<Map<String,Object>> rows,String key) {
        return rows.stream().map(r->((Number)r.get(key)).intValue()).sorted().toList();
    }
    String base(String extra) { return "dsl({model:'PageQM',columns:['id'],orderBy:['id']"+extra+"})"; }
    String joined(String leftExtra,String rightExtra,String key) {
        return "const a=dsl({model:'PageQM',columns:['id as left_id'],orderBy:['left_id']"+leftExtra+
            "});const b=dsl({model:'PageQM',columns:['id as right_id','amount'],orderBy:['right_id']"+rightExtra+
            "});return {plans:a.join(b,'left',[{left:'left_id',op:'=',right:'"+key+
            "'}]).query({columns:['left_id','amount'],orderBy:['left_id'],limit:100})};";
    }
    @ParameterizedTest @CsvSource({"0,2,1:2","2,2,3:4","9,2,","0,0,"})
    void baseWindow(int start,int limit,String expected) {
        List<Integer> want=expected==null?List.of():Arrays.stream(expected.split(":")).map(Integer::valueOf).toList();
        assertEquals(want,ids(rows("return {plans:"+base(",start:"+start+",limit:"+limit)+"};"),"id"));
        assertEquals(want,ids(rows(joined(",start:"+start+",limit:"+limit,"","right_id")),"left_id"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void qmOutputAliasBindsJoinAndDerivedStages(boolean g10) {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            String left="dsl({model:'PageQM',columns:['queryId'],orderBy:['queryId'],limit:2})";
            assertEquals(List.of(1,2), ids(rows("return {plans:"+left+"};"),"resultId"));
            String joined="const a="+left+";const b=dsl({model:'PageQM',columns:['id']});return {plans:a.join(b,'inner',[{left:'resultId',op:'=',right:'id'}]).query({columns:['id'],orderBy:['id'],limit:100})};";
            assertEquals(List.of(1,2),ids(rows(joined),"id"));
            assertEquals(List.of(1,2),ids(rows("return {plans:"+left+".query({columns:['resultId'],orderBy:['resultId'],limit:100})};"),"resultId"));
            for (var mode : ComposeScriptService.Mode.values()) {
                assertDoesNotThrow(() -> run(joined.replace("columns:['id'],orderBy:['id'],limit:100", "columns:['resultId'],orderBy:['resultId'],limit:100"),mode,true));
                assertThrows(RuntimeException.class, () -> run("return {plans:dsl({model:'PageQM',columns:['queryAmount'],limit:0})};",mode,true));
                assertThrows(RuntimeException.class, () -> run(joined.replace("left:'resultId'","left:'queryId'"),mode,false));
            }
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @Test void ordinaryQueryRequestAliasKeepsOneExplicitOrder() {
        var request = new com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest();
        request.setColumns(List.of("id as public_id"));
        var order = new com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest.OrderItem();
        order.setField("public_id"); order.setDir("asc"); request.setOrderBy(List.of(order));
        request.setStart(1); request.setLimit(1);
        var response = semantic.queryModel("PageQM",request,"execute",
                com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext.ofNamespace(""));
        assertEquals(List.of(2),ids(response.getItems(),"public_id"));
    }

    @Test void offsetOnlyAndUnbounded() {
        assertEquals(List.of(3,4,5,6),ids(rows("return {plans:"+base(",start:2")+"};"),"id"));
        assertEquals(List.of(1,2,3,4,5,6),ids(rows("return {plans:"+base("")+"};"),"id"));
    }
    @Test void rightInputLimitPreservesUnmatchedLeftRows() {
        var result=rows(joined("",",limit:2","right_id"));
        assertEquals(6,result.size());
        assertEquals(2,result.stream().filter(r->r.get("amount")!=null).count());
    }
    @Test void inputLimitIsNotFinalLimitForOneToManyJoin() {
        String script="const a=dsl({model:'PageQM',columns:['grp'],groupBy:['grp'],orderBy:['grp'],limit:1});"+
            "const b=dsl({model:'PageQM',columns:['id','grp as right_grp']});return {plans:a.join(b,'inner',[{left:'grp',op:'=',right:'right_grp'}]).query({columns:['id'],orderBy:['id'],limit:100})};";
        assertEquals(List.of(1,2),ids(rows(script),"id"));
    }
    @Test void aggregateAliasWindowFollowsHavingAndSort() {
        String script="return {plans:dsl({model:'PageQM',columns:['grp','sum(amount) as total'],groupBy:['grp'],"+
            "having:[{field:'total',op:'>',value:40}],orderBy:['-total'],start:1,limit:1})};";
        var result=rows(script);assertEquals(1,result.size());
        assertEquals(2,((Number)result.get(0).get("grp")).intValue());
        assertEquals(70,((Number)result.get(0).get("total")).intValue());
    }
    @Test void unionBranchesAndOuterWindowStayIndependent() {
        String script="const a="+base(",limit:2")+";const b="+base(",start:4,limit:2")+
            ";return {plans:a.union(b,{all:true}).query({columns:['id'],orderBy:['id'],start:1,limit:2})};";
        assertEquals(List.of(2,5),ids(rows(script),"id"));
    }
    @Test void derivedAndBaseWindowsAreBothAppliedOnce() {
        assertEquals(List.of(3),ids(rows("return {plans:"+base(",start:1,limit:3")+
            ".query({columns:['id'],orderBy:['id'],start:1,limit:1})};"),"id"));
    }
    @Test void filterIsAppliedBeforeWindow() {
        assertEquals(List.of(4),ids(rows("return {plans:"+base(",slice:[{field:'id',op:'>',value:2}],start:1,limit:1")+"};"),"id"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void invalidJoinRejectedInEveryModeWithEitherFlag(boolean g10) {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            for(String key:List.of("missing","id")) for(var mode:ComposeScriptService.Mode.values()) {
                var ex=assertThrows(RuntimeException.class,()->run(joined("","",key),mode,false));
                assertTrue(ex.getMessage().contains("right='"+key+"'"),ex.toString());
                assertTrue(ex.getMessage().contains("right_id"),ex.toString());
            }
            String left=joined("","","right_id").replace("left:'left_id'","left:'missing'");
            assertThrows(RuntimeException.class,()->run(left,ComposeScriptService.Mode.VALIDATE,false));
            assertEquals(6,rows(joined("","","right_id")).size());
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }
    @Test void aggregateAndDerivedJoinAliasesBindToOutputs() {
        String script="const a="+base(",limit:2")+".query({columns:['id as left_key']});"+
            "const b=dsl({model:'PageQM',columns:['grp as right_key','sum(amount) as total'],groupBy:['grp']});"+
            "return {plans:a.join(b,'inner',[{left:'left_key',op:'=',right:'right_key'}]).query({columns:['left_key','total']})};";
        assertEquals(List.of(1,2),ids(rows(script),"left_key"));
        assertThrows(RuntimeException.class,()->run(script.replace("right:'right_key'","right:'grp'"),ComposeScriptService.Mode.VALIDATE,false));
    }
    @Test void paginationDoesNotBypassFieldPermissions() {
        for(var mode:ComposeScriptService.Mode.values())
            assertThrows(RuntimeException.class,()->run(joined(",limit:0","","right_id"),mode,true));
    }
    @Test void subqueryFilterPrecedesInputPagination() {
        String script="const keys=dsl({model:'PageQM',columns:['grp'],slice:[{field:'grp',op:'=',value:2}]});"+
            "return {plans:dsl({model:'PageQM',columns:['id'],slice:[{field:'grp',op:'in',value:keys}],orderBy:['id'],start:1,limit:1})};";
        assertEquals(List.of(4),ids(rows(script),"id"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void orderedUnionInputsWithoutPaginationRemainIndependent(boolean g10) {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            String script="const a="+base(",slice:[{field:'id',op:'<=',value:3}]")+";const b="+
                base(",slice:[{field:'id',op:'>',value:3}]")+";return {plans:a.union(b,{all:true})};";
            assertEquals(List.of(1,2,3,4,5,6),ids(rows(script),"id"));
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @Test void rightNestedMixedUnionPreservesSetGrouping() {
        String one="dsl({model:'PageQM',columns:['grp'],groupBy:['grp'],slice:[{field:'grp',op:'=',value:1}]})";
        String two=one.replace("value:1","value:2");
        String script="const a="+two+";const b="+one+";const c="+one+
            ";return {plans:a.union(b.union(c,{all:true}))};";
        assertEquals(List.of(1,2),ids(rows(script),"grp"));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void omittedDerivedColumnsPreserveQmOutputAliases(boolean g10) {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            String script="const a=dsl({model:'PageQM',columns:['queryId'],orderBy:['queryId'],limit:3});"+
                "return {plans:a.query({limit:2}).query({columns:['resultId'],orderBy:['resultId']})};";
            assertEquals(List.of(1,2),ids(rows(script),"resultId"));
            assertDoesNotThrow(()->run(script,ComposeScriptService.Mode.EXECUTE,true));
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void unionArityIsRejectedBeforeDatabaseExecution(boolean g10) {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            String script="const a=dsl({model:'PageQM',columns:['id']});"+
                "const b=dsl({model:'PageQM',columns:['id','grp']});return {plans:a.union(b)};";
            for(var mode:List.of(ComposeScriptService.Mode.VALIDATE,ComposeScriptService.Mode.PREVIEW)) {
                var error=assertThrows(RuntimeException.class,()->run(script,mode,false));
                assertTrue(error.getMessage().contains("column count mismatch"),error.getMessage());
            }
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @Test void reusedPlanKeepsEachBranchWindowAndDuplicates() {
        String script="const a="+base(",limit:2")+";return {plans:a.union(a.query({limit:1}),{all:true})};";
        assertEquals(List.of(1,1,2),ids(rows(script),"id"));
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void subqueryThroughPassthroughUsesBoundOutputSchema(boolean g10) {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
            String script="const keys=dsl({model:'PageQM',columns:['queryId'],orderBy:['queryId'],limit:2}).query({limit:1});"+
                "return {plans:dsl({model:'PageQM',columns:['id'],slice:[{field:'id',op:'in',value:keys}],orderBy:['id']})};";
            assertEquals(List.of(1),ids(rows(script),"id"));
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void nestedJoinUsesExposedKeysAndKeepsPermissions(boolean g10) {
        ComposeFeatureFlags.overrideG10Enabled(g10);
        try {
        String script="const a="+base(",limit:2")+";const b=dsl({model:'PageQM',columns:['id as second_id']});"+
                "const c=dsl({model:'PageQM',columns:['id as third_id','amount']});"+
                "return {plans:a.join(b,'inner',[{left:'id',op:'=',right:'second_id'}])"+
                ".join(c,'inner',[{left:'second_id',op:'=',right:'third_id'}]).query({columns:['id','amount']})};";
        assertEquals(List.of(1,2),ids(rows(script),"id"));
        for(var mode:ComposeScriptService.Mode.values()) {
            assertDoesNotThrow(()->run(script.replace(",'amount'", ""),mode,true));
            var denied=assertThrows(RuntimeException.class,()->run(script,mode,true));
            assertTrue(denied.getMessage().contains("amount"),denied.getMessage());
        }
        } finally {ComposeFeatureFlags.overrideG10Enabled(null);}
    }

    @Test void derivedOffsetOnlyPreservesRemainingRows() {
        assertEquals(List.of(3,4,5,6),ids(rows("return {plans:"+base("")+
            ".query({columns:['id'],orderBy:['id'],start:2})};"),"id"));
    }

    @Test void orderedDerivedUnionBranchIsExecutable() {
        String script="const a="+base(",limit:2")+".query({columns:['id'],orderBy:['id']});"+
            "return {plans:a.union(a,{all:true})};";
        assertEquals(List.of(1,1,2,2),ids(rows(script),"id"));
    }

    @Test void zeroDerivedWindowWithOffsetKeepsPermissionChecks() {
        String script="return {plans:dsl({model:'PageQM',columns:['id','amount']})"+
            ".query({columns:['id','amount'],orderBy:['id'],start:2,limit:0})};";
        assertTrue(rows(script).isEmpty());
        for(var mode:ComposeScriptService.Mode.values())
            assertThrows(RuntimeException.class,()->run(script,mode,true));
    }

    @Test void windowResultIsPagedAfterRanking() {
        if ("mssql".equals(dialect())) {
            // Compose's current SQL Server lowering cannot hoist result-stage CTEs.
            for (var mode : ComposeScriptService.Mode.values()) {
                var error = assertThrows(RuntimeException.class, () -> run("return {plans:dsl({model:'PageWindowQM',columns:['id','ranked'],orderBy:['ranked'],limit:2})};",mode,false));
                assertTrue(error.getMessage().contains("relation-cte-hoist-unsupported"),error.getMessage());
            }
            return;
        }
        if (!supportsWindowFunctions()) {
            assertThrows(RuntimeException.class,()->rows("return {plans:dsl({model:'PageWindowQM',columns:['id','ranked'],orderBy:['ranked'],limit:2})};"));
            return;
        }
        assertEquals(List.of(5,6),ids(rows("return {plans:dsl({model:'PageWindowQM',columns:['id','ranked'],orderBy:['ranked'],limit:2})};"),"id"));
    }
}

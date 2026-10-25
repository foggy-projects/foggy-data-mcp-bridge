package io.foggytest.runtime;

import com.fasterxml.jackson.databind.*;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.model.engine.compose.security.*;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.plugins.result_set_filter.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.sql.DataSource;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class RuntimeComposeGovernanceIntegrationTest {
    static Path out;
    static final ObjectMapper json=new ObjectMapper();
    static final AtomicInteger prepared=new AtomicInteger(),executed=new AtomicInteger(),maskCalls=new AtomicInteger();
    static final List<Map<String,Object>> observations=new ArrayList<>();
    static boolean flag;
    static final String BASE="const base=dsl({model:'ComposeFollowQM',columns:['id','category as cat','revenue as v']});";
    static void call(MockMvc mvc,String label,String script) throws Exception {
        for(String mode:List.of("validate","preview","execute")) request(mvc,label,script,mode,null);
    }
    static void request(MockMvc mvc,String label,String script,String mode,Map<String,Object> nativePayload) throws Exception {
        prepared.set(0);executed.set(0);maskCalls.set(0);
        Map<String,Object> item=new LinkedHashMap<>();item.put("case",label);item.put("g10",flag);item.put("mode",mode);
        item.put("script",script);item.put("nativePayload",nativePayload);
        String endpoint=nativePayload==null?"/api/v1/compose/"+mode:"/api/v1/query/"+(label.equals("native-mask-control")?"ComposeFollowMasked":label.equals("native-execution-mask")?"ComposeFollowExecMask":label.equals("native-execution-deny")?"ComposeFollowExecDeny":"ComposeFollowQM")+"/"+mode;
        try {
            var response=mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body(label,script,nativePayload)))).andReturn().getResponse();
            item.put("httpStatus",response.getStatus());item.put("response",json.readTree(response.getContentAsString(StandardCharsets.UTF_8)));
        } catch(Throwable e) {item.put("transportError",e.toString());}
        item.put("maskProcessCalls",maskCalls.get());item.put("prepared",prepared.get());item.put("executed",executed.get());observations.add(item);
        json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("calls").resolve(String.format("%03d-%s-%s-%s.json",observations.size(),label,flag,mode)).toFile(),item);
    }
    static Object body(String label,String script,Map<String,Object> nativePayload) {
        if(nativePayload!=null)return nativePayload;
        Map<String,Object> body=new LinkedHashMap<>();body.put("script",script);body.put("options",Map.of("dialect","sqlite"));
        if(label.contains("params-security"))body.put("params",Map.of("model","ComposeFollowRows","columns",List.of("id"),"fieldAccess",List.of("revenue"),"systemSlice",List.of(),"namespace","untrusted"));
        return body;
    }
    static Connection observe(Connection c) {
        return (Connection)Proxy.newProxyInstance(RuntimeComposeGovernanceIntegrationTest.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            try {
                Object result=m.invoke(c,a);if(!(result instanceof Statement statement))return result;
                String sql=a!=null&&a.length>0&&a[0] instanceof String text?text:"";
                if(sql.toLowerCase(Locale.ROOT).contains("compose_followup"))prepared.incrementAndGet();
                return Proxy.newProxyInstance(RuntimeComposeGovernanceIntegrationTest.class.getClassLoader(),new Class<?>[]{statement instanceof PreparedStatement?PreparedStatement.class:Statement.class},(q,sm,sa)->{
                    String actual=sa!=null&&sa.length>0&&sa[0] instanceof String text?text:sql;
                    if(sm.getName().startsWith("execute")&&actual.toLowerCase(Locale.ROOT).contains("compose_followup"))executed.incrementAndGet();
                    try{return sm.invoke(statement,sa);}catch(InvocationTargetException e){throw e.getCause();}
                });
            }catch(InvocationTargetException e){throw e.getCause();}
        });
    }
    @org.junit.jupiter.api.Test
    void runtimeGovernanceOwnershipAndParameterContracts(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        String evidence=System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        out=evidence==null?temp:Path.of(evidence,"runtime-compose-governance");
        Files.createDirectories(out);observations.clear();Files.createDirectory(out.resolve("calls"));Path models=Files.createDirectory(out.resolve("models"));
        String origin=QueryPlan.class.getProtectionDomain().getCodeSource().getLocation().toString();

        Files.writeString(out.resolve("engine-origin.txt"),origin);
        Files.writeString(models.resolve("ComposeFollow.tm"),"export const model={name:'ComposeFollow',tableName:'compose_followup',idColumn:'id',properties:[{name:'id',column:'id',type:'INTEGER'},{name:'grp',column:'grp',type:'INTEGER'},{name:'amount',column:'amount',type:'INTEGER'}]};");
        for(String suffix:List.of("QM","Rows","Denied","Blocked","PlainQM","Masked","ShortCircuit","ExecMask","ExecDeny","PureCheck"))Files.writeString(models.resolve("ComposeFollow"+suffix+".qm"),
            "const m=loadTableModel('ComposeFollow');export const queryModel={name:'ComposeFollow"+suffix+"',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.grp,name:'category',alias:'categoryOut'},{ref:m.amount,name:'revenue',alias:'revenueOut'}]}],accesses:[]"+(suffix.equals("Blocked")?",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:false};}}":"")+"};");
        Files.writeString(models.resolve("ComposeFollowPlainQM.qm"),"const m=loadTableModel('ComposeFollow');export const queryModel={name:'ComposeFollowPlainQM',model:m,loader:'v2',columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.grp,name:'category',alias:'category'},{ref:m.amount,name:'revenue',alias:'revenue'}]}],accesses:[]};");
        new WebApplicationContextRunner().withUserConfiguration(App.class).withPropertyValues(
            "spring.datasource.url=jdbc:sqlite:"+out.resolve("fixture.sqlite"),"spring.datasource.driver-class-name=org.sqlite.JDBC",
            "foggy.runtime-api.enabled=true","foggy.data-viewer.enabled=false",
            "foggy.runtime-api.bundle-registry.path="+out.resolve("bundles.json"),"foggy.runtime-api.datasource-registry.path="+out.resolve("datasources.json"),
            "foggy.runtime-api.authoring-workspaces.path="+out.resolve("authoring")).run(context->{
                if(context.getStartupFailure()!=null)throw new AssertionError(context.getStartupFailure());
                var jdbc=context.getBean(JdbcTemplate.class);jdbc.execute("CREATE TABLE compose_followup(id INTEGER PRIMARY KEY,grp INTEGER,amount INTEGER)");
                jdbc.update("INSERT INTO compose_followup VALUES(1,1,10),(2,1,20),(3,2,30),(4,2,40),(5,3,50),(6,3,60),(7,NULL,NULL),(8,NULL,80)");
                var bundles=context.getBean(SystemBundlesContext.class);if(!bundles.addExternalBundle("compose-followup-audit","",models.toString(),false))throw new AssertionError();
                var loader=context.getBean(QueryModelLoader.class);Map<JdbcQueryModelImpl,DataSource> originals=new IdentityHashMap<>();
                try {
                    for(String suffix:List.of("QM","Rows","Denied","Blocked","PlainQM","Masked","ShortCircuit","ExecMask","ExecDeny","PureCheck")) {
                        var model=(JdbcQueryModelImpl)loader.getJdbcQueryModel("ComposeFollow"+suffix,"");var original=model.getDataSource();originals.put(model,original);
                        model.setDataSource(new DelegatingDataSource(original){public Connection getConnection()throws SQLException{return observe(super.getConnection());}});
                    }
                    var mvc=MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
                    for(boolean g10:List.of(false,true)) {
                        flag=g10;ComposeFeatureFlags.overrideG10Enabled(g10);
                        for(String model:List.of("ExecMask","ExecDeny","PureCheck")) {
                            String governed="const p=dsl({model:'ComposeFollow"+model+"',columns:['id','revenue'],slice:[{field:'id',op:'=',value:1}]});";
                            call(mvc,"execution-governance-"+model,governed+"return {plans:p};");
                            call(mvc,"execution-governance-explicit-"+model,governed+"return {plans:p.execute()};");
                        }
                        request(mvc,"native-execution-mask","","execute",Map.of("columns",List.of("id","revenue"),"slice",List.of(Map.of("field","id","op","=","value",1))));
                        request(mvc,"native-execution-deny","","execute",Map.of("columns",List.of("id","revenue")));
                        call(mvc,"explicit-readonly","const p=dsl({model:'ComposeFollowQM',columns:['id','revenue'],slice:[{field:'id',op:'=',value:1}]});return {plans:p.execute()};");
                        call(mvc,"explicit-discard","const p=dsl({model:'ComposeFollowQM',columns:['id'],limit:1});const rows=p.execute();return {ok:true};");
                        call(mvc,"explicit-function","const p=dsl({model:'ComposeFollowQM',columns:['id'],limit:1});function read(){return p.execute();}return {plans:read()};");
                        call(mvc,"explicit-toSql","const p=dsl({model:'ComposeFollowQM',columns:['id'],limit:1});return {plans:p.toSql()};");
                        String fluent="const a=dsl({model:'ComposeFollowPlainQM',columns:['id','category'],slice:[{field:'id',op:'=',value:1}]});const b=dsl({model:'ComposeFollowPlainQM',columns:['id','category'],slice:[{field:'id',op:'=',value:2}]});const c=dsl({model:'ComposeFollowPlainQM',columns:['id','category'],slice:[{field:'id',op:'=',value:3}]});";
                        call(mvc,"fluent-outsider",fluent+"return {plans:a.innerJoin(b).on(c.category,b.category)};");
                        call(mvc,"fluent-union-branch",fluent+"const u=a.union(c,{all:true});return {plans:u.innerJoin(b).on(c.category,b.category)};");
                        call(mvc,"fluent-and-outsider",fluent+"return {plans:a.innerJoin(b).on(a.category,b.category).and(c.id,b.id)};");
                        call(mvc,"fluent-extra-nullsafe",fluent+"return {plans:a.innerJoin(b).on(a.category,b.category,{nullSafe:true})};");
                        call(mvc,"fluent-and-extra",fluent+"return {plans:a.innerJoin(b).on(a.category,b.category).and(a.id,b.id,true)};");
                        String nested="const a=dsl({model:'ComposeFollowPlainQM',columns:['id as aid','revenue as av'],slice:[{field:'id',op:'=',value:1}]});const b=dsl({model:'ComposeFollowPlainQM',columns:['id as bid','revenue as bv'],slice:[{field:'id',op:'=',value:3}]});const j=a.join(b,'inner',[{left:'aid',op:'<',right:'bid'}]);const w=j.query({columns:['aid','av as bv','bv as renamed']});const c=dsl({model:'ComposeFollowPlainQM',columns:['id as cid','revenue as cv'],slice:[{field:'id',op:'=',value:3}]});";
                        call(mvc,"fluent-renamed-control",nested+"return {plans:w.innerJoin(c).on(b.bv,c.cv)};");
                        call(mvc,"control",BASE+"return {plans:base.query({columns:['id','v'],slice:[{field:'id',op:'=',value:1}]})};");
                        call(mvc,"slice-object",BASE+"return {plans:base.query({columns:['id','v'],slice:{field:'id',op:'=',value:1}})};");
                        call(mvc,"columns-scalar",BASE+"return {plans:base.query({columns:'id',limit:1})};");
                        call(mvc,"groupBy-scalar",BASE+"return {plans:base.query({columns:['sum(v) as total'],groupBy:'cat'})};");
                        call(mvc,"limit-string",BASE+"return {plans:base.query({columns:['id'],limit:'1'})};");
                        call(mvc,"limit-fraction",BASE+"return {plans:base.query({columns:['id'],limit:1.9})};");
                        call(mvc,"limit-overflow",BASE+"return {plans:base.query({columns:['id'],limit:4294967296})};");
                        call(mvc,"offset-string",BASE+"return {plans:base.query({columns:['id'],orderBy:['id'],limit:1,offset:'2'})};");
                        call(mvc,"dsl-limit-null",BASE+"return {plans:dsl({model:base,columns:['id'],limit:null})};");
                        call(mvc,"query-limit-null",BASE+"return {plans:base.query({columns:['id'],limit:null})};");
                        call(mvc,"query-source-ignored","const base=dsl({model:'ComposeFollowQM',columns:['id'],slice:[{field:'id',op:'=',value:1}]});const other=dsl({model:'ComposeFollowQM',columns:['id'],slice:[{field:'id',op:'=',value:2}]});return {plans:base.query({model:other,columns:['id']})};");
                        String selectSources="const base=dsl({model:'ComposeFollowQM',columns:['id'],slice:[{field:'id',op:'=',value:1}]});const other=dsl({model:'ComposeFollowQM',columns:['id'],slice:[{field:'id',op:'=',value:2}]});";
                        call(mvc,"query-source-key-ignored",selectSources+"return {plans:base.query({source:other,columns:['id']})};");
                        call(mvc,"dsl-source-selector-control",selectSources+"return {plans:dsl({source:other,columns:['id']})};");
                        call(mvc,"dsl-model-selector-control",selectSources+"return {plans:dsl({model:other,columns:['id']})};");
                        call(mvc,"query-security-ignored","const base=dsl({model:'ComposeFollowRows',columns:['id']});return {plans:base.query({columns:['id'],systemSlice:[],fieldAccess:['revenue'],namespace:'untrusted'})};");
                        call(mvc,"dsl-security-rejected","const base=dsl({model:'ComposeFollowRows',columns:['id']});return {plans:dsl({model:base,columns:['id'],systemSlice:[],fieldAccess:['revenue'],namespace:'untrusted'})};");
                        call(mvc,"query-extra-arguments",BASE+"return {plans:base.query({columns:['id'],limit:1},{calculatedFields:[{name:'x',expression:'revenue*2'}],slice:[{field:'id',op:'=',value:8}]})};");
                        call(mvc,"derived-calc-rejected",BASE+"return {plans:base.query({columns:['id'],calculatedFields:[{name:'x',expression:'revenue*2'}]})};");
                        call(mvc,"derived-having-rejected",BASE+"return {plans:dsl({model:base,columns:['id'],having:[{field:'id',op:'>',value:1}]})};");
                        String prefix="const a=dsl({model:'ComposeFollowPlainQM',columns:['id as aid','revenue as av'],slice:[{field:'id',op:'=',value:1}]});const b=dsl({model:'ComposeFollowPlainQM',columns:['id as bid','revenue as bv'],slice:[{field:'id',op:'=',value:2}]});const joined=a.join(b,'inner',[{left:'aid',op:'<',right:'bid'}]);";
                        call(mvc,"join-direct-wrong-owner",prefix+"return {plans:joined.query({columns:['a.bv as picked']})};");
                        call(mvc,"join-wrapped-wrong-owner",prefix+"const wrapped=joined.query({columns:['aid','av','bid','bv']});return {plans:wrapped.query({columns:['a.bv as picked']})};");
                        call(mvc,"join-wrapped-typed-wrong-owner",prefix+"const wrapped=joined.query({columns:['aid','av','bid','bv']});return {plans:wrapped.query({columns:[a.bv.as('picked')]})};");
                        call(mvc,"join-typed-passthrough-string",prefix+"const wrapped=joined.query({columns:[a.aid,b.bv]});return {plans:wrapped.query({columns:['b.bv as picked']})};");
                        call(mvc,"join-typed-passthrough-typed",prefix+"const wrapped=joined.query({columns:[a.aid,b.bv]});return {plans:wrapped.query({columns:[b.bv.as('picked')]})};");
                        call(mvc,"join-renamed-owner-control",prefix+"const wrapped=joined.query({columns:['aid','bv as renamed']});return {plans:wrapped.query({columns:['b.bv as picked']})};");
                        call(mvc,"join-wrapped-owner-control",prefix+"const wrapped=joined.query({columns:['aid','av','bid','bv']});return {plans:wrapped.query({columns:['b.bv as picked']})};");
                        call(mvc,"nested-join-wrong-owner",prefix+"const c=dsl({model:'ComposeFollowQM',columns:['id as cid'],slice:[{field:'id',op:'=',value:3}]});const nested=joined.join(c,'inner',[{left:'aid',op:'<',right:'cid'}]);return {plans:nested.query({columns:['a.bv as picked']})};");
                        call(mvc,"nested-join-denied-control",prefix.replace("model:'ComposeFollowPlainQM',columns:['id as bid'","model:'ComposeFollowDenied',columns:['id as bid'")+"const wrapped=joined.query({columns:['aid','av','bid','bv']});return {plans:wrapped.query({columns:['a.bv as picked']})};");
                        String union="const a=dsl({model:'ComposeFollowQM',columns:['id'],slice:[{field:'id',op:'=',value:1}]});const b=dsl({model:'ComposeFollowQM',columns:['id'],slice:[{field:'id',op:'=',value:1}]});";
                        String unionNested="const a=dsl({model:'ComposeFollowPlainQM',columns:['id as uid','revenue as val'],slice:[{field:'id',op:'=',value:1}]});const b=dsl({model:'ComposeFollowPlainQM',columns:['id as uid','revenue as val'],slice:[{field:'id',op:'=',value:2}]});const u=a.union(b,{all:true});const c=dsl({model:'ComposeFollowPlainQM',columns:['id as cid'],slice:[{field:'id',op:'=',value:3}]});";
                        call(mvc,"union-output-owner-control",unionNested+"const j=u.join(c,'inner',[{left:'uid',op:'<',right:'cid'}]);return {plans:j.query({columns:['u.val as picked']})};");
                        call(mvc,"union-direct-branch-owner",unionNested+"return {plans:u.query({columns:['b.val as picked']})};");
                        call(mvc,"union-nested-branch-owner",unionNested+"const j=u.join(c,'inner',[{left:'uid',op:'<',right:'cid'}]);return {plans:j.query({columns:['b.val as picked']})};");
                        call(mvc,"union-nested-typed-branch-owner",unionNested+"const j=u.join(c,'inner',[{left:'uid',op:'<',right:'cid'}]);return {plans:j.query({columns:[b.val.as('picked')]})};");
                        call(mvc,"query-params-security","const base=dsl({model:'ComposeFollowRows',columns:['id']});return {plans:base.query(params)};");
                        call(mvc,"query-params-security-denied","const base=dsl({model:'ComposeFollowDenied',columns:['id','revenue']});return {plans:base.query(params)};");
                        call(mvc,"dsl-params-security","return {plans:dsl(params)};");
                        call(mvc,"union-all-control",union+"return {plans:a.union(b,{all:true})};");
                        call(mvc,"union-all-string",union+"return {plans:a.union(b,{all:'true'})};");
                        call(mvc,"union-typed-branch-reference",union+"const u=a.union(b,{all:true});return {plans:u.query({columns:[b.id]})};");
                        call(mvc,"union-branch-qualification-control",union+"const u=a.union(b,{all:true});return {plans:u.query({columns:['b.id']})};");
                        String nullJoin="const a=dsl({model:'ComposeFollowQM',columns:['id as aid','category as ag'],slice:[{field:'id',op:'=',value:7}]});const b=dsl({model:'ComposeFollowQM',columns:['id as bid','category as bg'],slice:[{field:'id',op:'=',value:8}]});";
                        call(mvc,"join-normal-null-control",nullJoin+"return {plans:a.join(b,'inner',[{left:'ag',op:'=',right:'bg'}])};");
                        call(mvc,"join-nullsafe-type-invalid",nullJoin+"return {plans:a.join(b,'inner',[{left:'ag',op:'=',right:'bg',nullSafe:'true'}])};");
                        call(mvc,"join-nullsafe-map",nullJoin+"return {plans:a.join(b,'inner',[{left:'ag',op:'=',right:'bg',nullSafe:true}])};");
                        call(mvc,"join-nullsafe-not-equal-map",nullJoin+"return {plans:a.join(b,'inner',[{left:'ag',op:'<',right:'bg',nullSafe:true}])};");
                        call(mvc,"base-window-definition","return {plans:dsl({model:'ComposeFollowQM',columns:['id','category','running'],calculatedFields:[{name:'running',expression:'SUM(revenue)',partitionBy:['category'],windowOrderBy:[{field:'id',dir:'asc'}],windowFrame:'ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW'}],orderBy:['id']})};");
                        request(mvc,"native-window-control","","execute",Map.of("columns",List.of("id","category","running"),"calculatedFields",List.of(Map.of("name","running","expression","SUM(revenue)","partitionBy",List.of("category"),"windowOrderBy",List.of(Map.of("field","id","dir","asc")),"windowFrame","ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW")),"orderBy",List.of("id")));
                        call(mvc,"base-row-number-definition","return {plans:dsl({model:'ComposeFollowQM',columns:['id','rn'],calculatedFields:[{name:'rn',expression:'ROW_NUMBER()',windowOrderBy:[{field:'id',dir:'asc'}]}],orderBy:['id']})};");
                        request(mvc,"native-row-number-control","","execute",Map.of("columns",List.of("id","rn"),"calculatedFields",List.of(Map.of("name","rn","expression","ROW_NUMBER()","windowOrderBy",List.of(Map.of("field","id","dir","asc")))),"orderBy",List.of("id")));
                        call(mvc,"window-order-permission-denied","return {plans:dsl({model:'ComposeFollowDenied',columns:['id','rn'],calculatedFields:[{name:'rn',expression:'ROW_NUMBER()',windowOrderBy:[{field:'revenue',dir:'asc'}]}]})};");
                        call(mvc,"window-partition-permission-denied","return {plans:dsl({model:'ComposeFollowDenied',columns:['id','rn'],calculatedFields:[{name:'rn',expression:'ROW_NUMBER()',partitionBy:['revenue'],windowOrderBy:[{field:'id',dir:'asc'}]}]})};");
                        call(mvc,"base-empty-default","return {plans:dsl({model:'ComposeFollowQM',columns:['id','copy'],calculatedFields:[{name:'copy',expression:'revenue',emptyDefault:0}],slice:[{field:'id',op:'=',value:7}]})};");
                        request(mvc,"native-empty-default-control","","execute",Map.of("columns",List.of("id","copy"),"calculatedFields",List.of(Map.of("name","copy","expression","revenue","emptyDefault",0)),"slice",List.of(Map.of("field","id","op","=","value",7))));
                        call(mvc,"base-calc-container-object","return {plans:dsl({model:'ComposeFollowQM',columns:['id'],calculatedFields:{name:'x',expression:'revenue*2'}})};");
                        call(mvc,"case-colliding-output",BASE+"return {plans:base.query({columns:['id as X','v as x'],limit:1})};");
                        call(mvc,"case-output-control",BASE+"return {plans:base.query({columns:['id as X','v as value'],limit:1})};");
                        call(mvc,"compose-shortcircuit","return {plans:dsl({model:'ComposeFollowShortCircuit',columns:['id']})};");
                        call(mvc,"compose-masked-base","return {plans:dsl({model:'ComposeFollowMasked',columns:['id','revenue as val'],slice:[{field:'id',op:'=',value:1}]})};");
                        call(mvc,"compose-masked-derived","const m=dsl({model:'ComposeFollowMasked',columns:['id','revenue as val'],slice:[{field:'id',op:'=',value:1}]});return {plans:dsl({model:m,columns:['val']})};");
                        call(mvc,"compose-masked-join","const a=dsl({model:'ComposeFollowQM',columns:['id as aid'],slice:[{field:'id',op:'=',value:1}]});const b=dsl({model:'ComposeFollowMasked',columns:['id as bid','revenue as val'],slice:[{field:'id',op:'=',value:1}]});return {plans:a.join(b,'inner',[{left:'aid',op:'=',right:'bid'}]).query({columns:['val']})};");
                        call(mvc,"compose-masked-union","const a=dsl({model:'ComposeFollowQM',columns:['id','revenue as val']});const b=dsl({model:'ComposeFollowMasked',columns:['id','revenue as val']});return {plans:a.union(b,{all:true})};");
                        call(mvc,"compose-masked-limit-zero","return {plans:dsl({model:'ComposeFollowMasked',columns:['id','revenue as val'],limit:0})};");
                        request(mvc,"native-mask-control","","execute",Map.of("columns",List.of("id","revenue"),"slice",List.of(Map.of("field","id","op","=","value",1))));
                        call(mvc,"server-denial-control","const base=dsl({model:'ComposeFollowDenied',columns:['id','revenue']});return {plans:base.query({columns:['revenue']})};");
                        call(mvc,"model-denial-control","const base=dsl({model:'ComposeFollowBlocked',columns:['id']});return {plans:dsl({model:base,columns:['id']})};");
                    }
                }catch(Exception e){throw new RuntimeException(e);}finally{ComposeFeatureFlags.overrideG10Enabled(null);originals.forEach(JdbcQueryModelImpl::setDataSource);bundles.removeBundle("compose-followup-audit");}
            });
        json.writerWithDefaultPrettyPrinter().writeValue(out.resolve("observations.json").toFile(),observations);
        verify();
    }
    static void verify() {
        var successCases=Set.of("control","dsl-limit-null","query-limit-null","dsl-source-selector-control",
                "dsl-model-selector-control","join-wrapped-owner-control","union-all-control","join-nullsafe-map",
                "base-window-definition","base-row-number-definition","base-empty-default","case-output-control",
                "join-renamed-owner-control","union-output-owner-control","join-typed-passthrough-string","join-typed-passthrough-typed","join-normal-null-control",
                "native-window-control","native-row-number-control","native-empty-default-control","native-mask-control");
        List<String> failures=new ArrayList<>();
        for(var item:observations) {
            String label=(String)item.get("case"),mode=(String)item.get("mode");
            JsonNode response=(JsonNode)item.get("response");
            boolean expected=successCases.contains(label)||label.equals("native-execution-mask")||label.equals("fluent-renamed-control")||label.equals("explicit-toSql")
                    ||Set.of("explicit-readonly","explicit-discard","explicit-function").contains(label)&&mode.equals("execute");
            if(response==null||response.path("success").asBoolean()!=expected)
                failures.add(label+"/"+mode+"/"+item.get("g10")+": "+response);
            if(!mode.equals("execute")||!expected||label.equals("explicit-toSql")) {
                org.junit.jupiter.api.Assertions.assertEquals(0,item.get("prepared"),item.toString());
                org.junit.jupiter.api.Assertions.assertEquals(0,item.get("executed"),item.toString());
            } else {
                org.junit.jupiter.api.Assertions.assertTrue(((Number)item.get("executed")).intValue()>0,item.toString());
                JsonNode rows=response.at(label.startsWith("native-")?"/data/items":"/data/value/plans");
                if(label.equals("base-window-definition")) {
                    org.junit.jupiter.api.Assertions.assertEquals(8,rows.size());
                    org.junit.jupiter.api.Assertions.assertEquals(30,rows.get(1).path("running").asInt());
                    org.junit.jupiter.api.Assertions.assertEquals(70,rows.get(3).path("running").asInt());
                }
                if(label.equals("base-row-number-definition"))for(int i=0;i<8;i++)org.junit.jupiter.api.Assertions.assertEquals(i+1,rows.get(i).path("rn").asInt());
                if(label.equals("base-empty-default"))org.junit.jupiter.api.Assertions.assertEquals(0,rows.get(0).path("copy").asInt(-999));
                if(label.equals("join-nullsafe-map"))org.junit.jupiter.api.Assertions.assertEquals(1,rows.size());
                if(label.equals("join-normal-null-control"))org.junit.jupiter.api.Assertions.assertEquals(0,rows.size());
                if(label.equals("join-wrapped-owner-control")||label.equals("join-renamed-owner-control")||label.startsWith("join-typed-passthrough-"))org.junit.jupiter.api.Assertions.assertEquals(20,rows.get(0).path("picked").asInt());
                if(label.equals("union-output-owner-control"))org.junit.jupiter.api.Assertions.assertEquals(List.of(10,20),json.convertValue(rows,new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>(){}).stream().map(row->((Number)row.get("picked")).intValue()).sorted().toList());
                if(label.equals("native-mask-control")) {
                    org.junit.jupiter.api.Assertions.assertEquals(0,rows.get(0).path("revenueOut").asInt(-1));
                    org.junit.jupiter.api.Assertions.assertEquals(1,item.get("maskProcessCalls"));
                }
                if(label.equals("case-output-control"))org.junit.jupiter.api.Assertions.assertEquals(Set.of("X","value"),json.convertValue(rows.get(0),Map.class).keySet());
            }
            if(label.equals("fluent-renamed-control")&&mode.equals("execute")) org.junit.jupiter.api.Assertions.assertEquals(30,response.at("/data/value/plans/0/renamed").asInt());
            if(label.equals("native-execution-mask")) {
                org.junit.jupiter.api.Assertions.assertEquals(0,response.at("/data/items/0/revenueOut").asInt(-1));
                org.junit.jupiter.api.Assertions.assertEquals(1,item.get("maskProcessCalls"));
            }
            if(label.startsWith("execution-governance-")&&!label.contains("explicit")&&!label.endsWith("PureCheck"))
                org.junit.jupiter.api.Assertions.assertTrue(response.toString().contains("COMPOSE_EXECUTION_GOVERNANCE_UNPROVEN"),response.toString());
            if(Set.of("explicit-readonly","explicit-discard","explicit-function").contains(label)&&!mode.equals("execute"))
                org.junit.jupiter.api.Assertions.assertTrue(response.toString().contains("COMPOSE_EXPLICIT_EXECUTE_FORBIDDEN"),response.toString());
            if(label.equals("join-nullsafe-type-invalid"))org.junit.jupiter.api.Assertions.assertTrue(response.toString().contains("join.on[0].nullSafe"),response.toString());
            if(label.startsWith("compose-masked-"))org.junit.jupiter.api.Assertions.assertTrue(response.toString().contains("COMPOSE_RESULT_GOVERNANCE_UNPROVEN"),response.toString());
        }
        org.junit.jupiter.api.Assertions.assertEquals(List.of(),failures);

    }
    @SpringBootConfiguration(proxyBeanMethods=false)
    @EnableAutoConfiguration(excludeName={"org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration","org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration","org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"})
    static class App {
        @Bean DataSetResultStep shortCircuit() {return new DataSetResultStep() {
            public int beforeQueryOrder() {return 20001;}
            public int beforeQuery(ModelResultContext context) {
                if("ComposeFollowShortCircuit".equals(context.getCanonicalModelName()))context.setSkipQuery(true);
                return CONTINUE;
            }
        };}
        @Bean DataSetResultStep auditMask(){return new DataSetResultStep(){
            public boolean composeResultProcessingIsIdentity(ModelResultContext context) {
                return context!=null && !"ComposeFollowMasked".equals(context.getCanonicalModelName());
            }
            @SuppressWarnings("unchecked") public int process(ModelResultContext context){
                if("ComposeFollowMasked".equals(context.getCanonicalModelName())&&context.getPagingResult()!=null){
                    maskCalls.incrementAndGet();
                    for(Object item:context.getPagingResult().getItems())if(item instanceof Map<?,?> row)
                        for(String key:List.of("revenueOut","revenue","val"))if(row.containsKey(key))((Map<Object,Object>)row).put(key,0);
                }
                return CONTINUE;
            }
        };}
        @Bean com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStep executionGovernance(){return new com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStep(){
            public int order(){return 17001;}
            public boolean composeSqlOnlyIsSafe(com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext c){
                return c!=null&&!Set.of("ComposeFollowExecMask","ComposeFollowExecDeny").contains(c.getModelName());
            }
            public int prepareComposeSql(com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext c){return beforeExecute(c);}
            public int beforeExecute(com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext c){
                if(Set.of("ComposeFollowExecDeny","ComposeFollowPureCheck").contains(c.getModelName()))throw new IllegalArgumentException("EXECUTION_POLICY_DENIED");return CONTINUE;
            }
            @SuppressWarnings("unchecked") public int afterExecute(com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext c){
                if("ComposeFollowExecMask".equals(c.getModelName())&&c.getResult()!=null){maskCalls.incrementAndGet();for(Object item:c.getResult().getItems())if(item instanceof Map row)for(String k:List.of("revenueOut","revenue"))if(row.containsKey(k))row.put(k,0);}return CONTINUE;
            }
        };}
        @Bean AuthorityResolver auditAuthority(){return request->{Map<String,ModelBinding> bindings=new LinkedHashMap<>();for(String model:request.modelNames())bindings.put(model,ModelBinding.builder().fieldAccess(model.endsWith("Denied")?List.of("id","category"):null).systemSlice(model.endsWith("Rows")?List.of(new SliceRequestDef("id","<=",2)):List.of()).build());return AuthorityResolution.builder().bindings(bindings).build();};}
    }
}

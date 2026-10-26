package io.foggytest.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.engine.compose.ComposeFeatureFlags;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionContext;
import com.foggyframework.dataset.model.plugins.query_execution.QueryExecutionStep;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real Runtime contracts: governance admission, owner binding and script boundaries. */
class RuntimeComposeBoundaryContractIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger PREPARED = new AtomicInteger(), EXECUTED = new AtomicInteger();
    private final List<Map<String, Object>> observations = new ArrayList<>();
    private Path out;
    private boolean flag;

    @Test void timePlansAndScriptBoundariesRemainGoverned(@TempDir Path temp) throws Exception {
        String evidence = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        out = evidence == null ? temp : Path.of(evidence, "runtime-compose-boundaries");
        Files.createDirectories(out.resolve("calls"));
        Path models = Files.createDirectories(out.resolve("models"));
        Files.writeString(models.resolve("ComposeGate.tm"), """
                export const model={name:'ComposeGate',tableName:'compose_gate',idColumn:'id',properties:[
                {name:'id',column:'id',type:'INTEGER'},{name:'grp',column:'grp',type:'INTEGER'},
                {name:'amount',column:'amount',type:'NUMBER'},{name:'occurredAt',column:'occurred_at',type:'DATETIME'}],
                measures:[{name:'totalAmount',column:'amount',type:'NUMBER',aggregation:'sum'}]};
                """);
        for (String suffix : List.of("Open", "ExecMask", "ExecDeny", "PureCheck")) {
            Files.writeString(models.resolve("ComposeGate" + suffix + ".qm"), """
                    const m=loadTableModel('ComposeGate');export const queryModel={name:'ComposeGate%s',model:m,loader:'v2',
                    columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.grp,name:'category',alias:'category'},
                    {ref:m.amount,name:'revenue',alias:'revenue'},{ref:m.occurredAt},{ref:m.totalAmount}]}],accesses:[]};
                    """.formatted(suffix));
        }
        new WebApplicationContextRunner().withUserConfiguration(App.class).withPropertyValues(
                "spring.datasource.url=jdbc:sqlite:" + out.resolve("fixture.sqlite"),
                "spring.datasource.driver-class-name=org.sqlite.JDBC", "foggy.runtime-api.enabled=true",
                "foggy.data-viewer.enabled=false", "foggy.runtime-api.bundle-registry.path=" + out.resolve("bundles.json"),
                "foggy.runtime-api.datasource-registry.path=" + out.resolve("datasources.json"),
                "foggy.runtime-api.authoring-workspaces.path=" + out.resolve("authoring")).run(context -> {
            assertNull(context.getStartupFailure());
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            jdbc.execute("CREATE TABLE compose_gate(id INTEGER PRIMARY KEY,grp INTEGER,amount INTEGER,occurred_at TEXT)");
            jdbc.update("INSERT INTO compose_gate VALUES(1,1,10,'2026-10-01'),(2,1,20,'2026-10-01'),"
                    + "(3,2,30,'2026-10-02'),(4,2,40,'2026-10-02'),(5,3,50,'2026-10-03'),"
                    + "(6,3,60,'2026-10-03'),(7,NULL,NULL,'2026-10-03'),(8,NULL,80,'2026-10-03')");
            var bundles = context.getBean(SystemBundlesContext.class);
            assertTrue(bundles.addExternalBundle("compose-boundary-contract", "", models.toString(), false));
            Map<JdbcQueryModelImpl, DataSource> originals = new IdentityHashMap<>();
            try {
                for (String suffix : List.of("Open", "ExecMask", "ExecDeny", "PureCheck")) {
                    var model = (JdbcQueryModelImpl) context.getBean(QueryModelLoader.class)
                            .getJdbcQueryModel("ComposeGate" + suffix, "");
                    DataSource original = model.getDataSource(); originals.put(model, original);
                    model.setDataSource(new DelegatingDataSource(original) {
                        @Override public Connection getConnection() throws SQLException { return observe(super.getConnection()); }
                    });
                }
                MockMvc mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
                for (boolean enabled : List.of(false, true)) {
                    flag = enabled; ComposeFeatureFlags.overrideG10Enabled(enabled);
                    verifyScriptContracts(mvc);
                    verifyNativeTimePlans(mvc);
                }
            } catch (Exception failure) { throw new RuntimeException(failure); }
            finally {
                ComposeFeatureFlags.overrideG10Enabled(null);
                originals.forEach(JdbcQueryModelImpl::setDataSource);
                bundles.removeBundle("compose-boundary-contract");
            }
        });
        JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("observations.json").toFile(), observations);
        assertFalse(observations.isEmpty());
    }

    private void verifyScriptContracts(MockMvc mvc) throws Exception {
        String base = "const p=dsl({model:'ComposeGateOpen',columns:['id','category as cat','revenue as v']});";
        String renamed = base + "const w=p.query({columns:['id','cat as v','v as renamed']});";
        for (String mode : List.of("validate", "preview", "execute")) {
            JsonNode groups = compose(mvc, "group-renamed", renamed
                    + "return {plans:w.groupBy(p.v,w.v).select(w.v.as('cat'),p.v.as('amount'))};", mode, true);
            if (mode.equals("execute")) assertEquals(8, groups.at("/data/value/plans").size());
            JsonNode window = compose(mvc, "window-renamed", renamed
                    + "return {plans:w.select('id','renamed',w.renamed.lag().over({partitionBy:[p.v],orderBy:['id']}).as('prior'))};",
                    mode, true);
            if (mode.equals("execute")) for (JsonNode row : window.at("/data/value/plans")) assertTrue(row.path("prior").isNull(), row.toString());
            for (String kind : List.of("group", "partition", "order")) {
                String foreign = "const outsider=dsl({model:'ComposeGateOpen',columns:['revenue as v']});";
                String expression = kind.equals("group") ? "w.groupBy(outsider.v)"
                        : "w.select('id',w.renamed.lag().over({" + (kind.equals("partition") ? "partitionBy" : "orderBy") + ":[outsider.v]}).as('prior'))";
                compose(mvc, "owner-foreign-" + kind, renamed + foreign + "return {plans:" + expression + "};", mode, false);
            }
            String union = "const a=dsl({model:'ComposeGateOpen',columns:['id','revenue as v']});const b=dsl({model:'ComposeGateOpen',columns:['id','revenue as v']});const u=a.union(b,{all:true});";
            compose(mvc, "union-branch-group", union + "return {plans:u.groupBy(a.id)};", mode, false);
            compose(mvc, "union-branch-window", union + "return {plans:u.select('id',u.v.lag().over({orderBy:[a.id]}).as('prior'))};", mode, false);
            for (String offset : List.of("4294967296", "1.9", "'2'", "null", "-1"))
                for (String method : List.of("lag", "lead"))
                    compose(mvc, "offset-invalid-" + method + "-" + offset.replaceAll("[^a-zA-Z0-9]", "_"), base
                            + "return {plans:p.select('id',p.v." + method + "(" + offset + ").over({orderBy:['id']}).as('prior'))};", mode, false);
            for (String method : List.of("execute({limit:0})", "toSql({},'postgres')", "limit(0,2)", "offset(0,2)"))
                compose(mvc, "arity-" + method.substring(0, method.indexOf('(')), base + "return {plans:p." + method + "};", mode, false);
            for (String shape : List.of("[p]", "{value:{plan:p}}", "{plans:{a:[p]}}")) {
                JsonNode result = compose(mvc, "result-shape-" + shape.hashCode(), base + "return " + shape + ";", mode, false);
                assertTrue(result.toString().contains("COMPOSE_RESULT_SHAPE_UNSUPPORTED"), result.toString());
                assertFalse(result.at("/error/path").asText().isBlank(), result.toString());
            }
            JsonNode preflight = compose(mvc, "result-preflight", base + "return {plans:p,hidden:{plan:p}};", mode, false);
            assertTrue(preflight.toString().contains("COMPOSE_RESULT_SHAPE_UNSUPPORTED"));
            for (String descriptor : List.of("p.v", "p.innerJoin(p)", "p.v.lag()")) {
                JsonNode result = compose(mvc, "result-descriptor-" + descriptor.hashCode(), base + "return {value:" + descriptor + "};", mode, false);
                assertTrue(result.toString().contains("COMPOSE_RESULT_SHAPE_UNSUPPORTED"), result.toString());
            }
            JsonNode unsupported = compose(mvc, "timewindow-unsupported", "return {plans:dsl({model:'ComposeGateOpen',columns:['id'],timeWindow:{comparison:'mtd'}})};", mode, false);
            assertTrue(unsupported.toString().contains("timeWindow"));
            compose(mvc, "timewindow-empty-compatible", "return {plans:dsl({model:'ComposeGateOpen',columns:['id'],timeWindow:{},customExtension:{x:true},limit:0})};", mode, true);
            compose(mvc, "plain-data-compatible", "return {value:{items:[1,2]},plans:{literal:[1,2]}};", mode, true);
            compose(mvc, "explicit-execute", base + "return {plans:p.limit(1).execute()};", mode, mode.equals("execute"));
            JsonNode zero = compose(mvc, "offset-zero", base + "return {plans:p.select('id','v',p.v.lag(0).over({orderBy:['id']}).as('prior'))};", mode, true);
            if (mode.equals("execute")) for (JsonNode row : zero.at("/data/value/plans")) assertEquals(row.path("v"), row.path("prior"));
        }
    }

    private void verifyNativeTimePlans(MockMvc mvc) throws Exception {
        Map<String, Object> window = Map.of("columns", List.of("occurredAt", "totalAmount", "totalAmount__mtd"),
                "groupBy", List.of("occurredAt"), "orderBy", List.of("occurredAt"),
                "timeWindow", Map.of("field", "occurredAt", "grain", "day", "comparison", "mtd",
                        "value", List.of("2026-10-01", "2026-11-01"), "targetMetrics", List.of("totalAmount")));
        for (String model : List.of("Open", "ExecMask", "ExecDeny", "PureCheck")) {
            for (String mode : List.of("validate", "execute")) {
                boolean safe = model.equals("Open");
                JsonNode response = request(mvc, "native-mtd-" + model, "/api/v1/query/ComposeGate" + model + "/" + mode, window, safe, !mode.equals("execute") || !safe);
                if (safe && mode.equals("execute")) {
                    JsonNode rows = response.at("/data/items"); assertEquals(3, rows.size(), response.toString());
                    List<Integer> cumulative = new ArrayList<>();
                    for (JsonNode row : rows) cumulative.add(row.path("totalAmount__mtd").asInt());
                    assertEquals(List.of(30,100,290), cumulative);
                }
            }
        }
    }

    private JsonNode compose(MockMvc mvc, String label, String script, String mode, boolean success) throws Exception {
        return request(mvc, label, "/api/v1/compose/" + mode, Map.of("script", script, "options", Map.of("dialect", "sqlite")),
                success, !mode.equals("execute") || !success || label.equals("plain-data-compatible"));
    }

    private JsonNode request(MockMvc mvc, String label, String endpoint, Map<String, Object> payload, boolean success, boolean noSql) throws Exception {
        PREPARED.set(0); EXECUTED.set(0);
        var result = mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(payload)))
                .andReturn().getResponse();
        JsonNode response = JSON.readTree(result.getContentAsString(StandardCharsets.UTF_8));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("case", label); item.put("g10", flag); item.put("endpoint", endpoint); item.put("payload", payload);
        item.put("response", response); item.put("prepared", PREPARED.get()); item.put("executed", EXECUTED.get());
        observations.add(item);
        JSON.writerWithDefaultPrettyPrinter().writeValue(out.resolve("calls/%04d.json".formatted(observations.size())).toFile(), item);
        assertEquals(200, result.getStatus(), item.toString()); assertEquals(success, response.path("success").asBoolean(), item.toString());
        if (noSql) { assertEquals(0, PREPARED.get(), item.toString()); assertEquals(0, EXECUTED.get(), item.toString()); }
        return response;
    }

    private static Connection observe(Connection connection) {
        return (Connection) Proxy.newProxyInstance(RuntimeComposeBoundaryContractIntegrationTest.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            try {
                Object result = method.invoke(connection, args);
                if (!(result instanceof Statement statement)) return result;
                String sql = args != null && args.length > 0 && args[0] instanceof String text ? text : "";
                if (sql.contains("compose_gate")) PREPARED.incrementAndGet();
                return Proxy.newProxyInstance(RuntimeComposeBoundaryContractIntegrationTest.class.getClassLoader(),
                        new Class<?>[]{statement instanceof PreparedStatement ? PreparedStatement.class : Statement.class}, (p, m, a) -> {
                            String actual = a != null && a.length > 0 && a[0] instanceof String text ? text : sql;
                            if (m.getName().startsWith("execute") && actual.contains("compose_gate")) EXECUTED.incrementAndGet();
                            try { return m.invoke(statement, a); } catch (InvocationTargetException failure) { throw failure.getCause(); }
                        });
            } catch (InvocationTargetException failure) { throw failure.getCause(); }
        });
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"})
    static class App {
        @Bean QueryExecutionStep boundaryPolicy() { return new QueryExecutionStep() {
            @Override public int order() { return 17011; }
            @Override public boolean composeSqlOnlyIsSafe(QueryExecutionContext context) {
                return context != null && !Set.of("ComposeGateExecMask", "ComposeGateExecDeny").contains(context.getModelName());
            }
            @Override public int prepareComposeSql(QueryExecutionContext context) {
                if (context.getModelName().equals("ComposeGatePureCheck")) throw new IllegalArgumentException("BOUNDARY_POLICY_DENIED");
                return CONTINUE;
            }
            @Override public int beforeExecute(QueryExecutionContext context) {
                if (context.getModelName().equals("ComposeGateExecDeny")) throw new IllegalArgumentException("BOUNDARY_POLICY_DENIED");
                return CONTINUE;
            }
            @SuppressWarnings("unchecked") @Override public int afterExecute(QueryExecutionContext context) {
                if (context.getModelName().equals("ComposeGateExecMask") && context.getResult() != null)
                    for (Object row : context.getResult().getItems()) if (row instanceof Map<?, ?> map)
                        for (String key : List.of("revenue", "totalAmount")) if (map.containsKey(key)) ((Map<Object,Object>) map).put(key, 0);
                return CONTINUE;
            }
        }; }
    }
}

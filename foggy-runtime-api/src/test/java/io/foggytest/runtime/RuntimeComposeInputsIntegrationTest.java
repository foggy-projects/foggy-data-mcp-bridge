package io.foggytest.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.query_model.JdbcQueryModelImpl;
import com.foggyframework.dataset.model.engine.compose.security.*;
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
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real Runtime controller, Compose adapter, authority binding and SQLite execution. */
class RuntimeComposeInputsIntegrationTest {
    @TempDir Path root;
    final ObjectMapper json = new ObjectMapper();
    final AtomicInteger prepared = new AtomicInteger(), executed = new AtomicInteger(), sequence = new AtomicInteger();

    @Test void inputContractsSurviveValidatePreviewExecuteAndPermissions() throws Exception {
        Path models = Files.createDirectory(root.resolve("models"));
        Files.writeString(models.resolve("RuntimeCompose.tm"), """
                export const model={name:'RuntimeCompose',tableName:'runtime_compose_inputs',idColumn:'id',properties:[
                {name:'id',column:'id',type:'INTEGER'},{name:'category',column:'category',type:'STRING'},
                {name:'amount',column:'amount',type:'INTEGER'}]};
                """);
        for (String suffix : List.of("QM", "Restricted", "Rows", "Denied")) {
            Files.writeString(models.resolve("RuntimeCompose" + suffix + ".qm"),
                    "const m=loadTableModel('RuntimeCompose');export const queryModel={name:'RuntimeCompose" + suffix + "',loader:'v2',model:m,"
                            + "columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.category},{ref:m.amount,name:'revenue',alias:'revenueOut'}]}],accesses:[]"
                            + ("Denied".equals(suffix) ? ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return {allow:false};}}" : "") + "};");
        }
        new WebApplicationContextRunner().withUserConfiguration(QueryApplication.class)
                .withPropertyValues("spring.datasource.url=jdbc:sqlite:" + root.resolve("compose.sqlite"),
                        "spring.datasource.driver-class-name=org.sqlite.JDBC", "foggy.runtime-api.enabled=true",
                        "foggy.runtime-api.bundle-registry.path=" + root.resolve("bundles.json"),
                        "foggy.runtime-api.datasource-registry.path=" + root.resolve("datasources.json"),
                        "foggy.runtime-api.authoring-workspaces.path=" + root.resolve("authoring"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var jdbc = context.getBean(JdbcTemplate.class);
                    jdbc.execute("CREATE TABLE runtime_compose_inputs(id INTEGER PRIMARY KEY,category TEXT,amount INTEGER)");
                    jdbc.update("INSERT INTO runtime_compose_inputs VALUES(1,'A',10),(2,'A',20),(3,'B',40)");
                    var bundles = context.getBean(SystemBundlesContext.class);
                    assertThat(bundles.addExternalBundle("runtime-compose-inputs", "", models.toString(), false)).isTrue();
                    Map<JdbcQueryModelImpl, DataSource> originals = new IdentityHashMap<>();
                    try {
                        var loader = context.getBean(QueryModelLoader.class);
                        for (String suffix : List.of("QM", "Restricted", "Rows", "Denied")) {
                            var model = (JdbcQueryModelImpl) loader.getJdbcQueryModel("RuntimeCompose" + suffix, "");
                            DataSource original = model.getDataSource(); originals.put(model, original);
                            model.setDataSource(new DelegatingDataSource(original) {
                                public Connection getConnection() throws SQLException { return observe(super.getConnection()); }
                            });
                        }
                        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context.getSourceApplicationContext()).build();
                        for (String shape : List.of("base", "derived", "join", "union")) {
                            for (String entry : List.of("query", "model", "source")) {
                                String options = "columns:['category_id','bill_amount','bill_amount * 2 as double_amount'],orderBy:[{field:'double_amount',dir:'desc'}],limit:2";
                                String script = setup(shape, "QM") + wrap(entry, options);
                                JsonNode validated = send(mvc, script, "validate");
                                JsonNode previewed = send(mvc, script, "preview");
                                JsonNode result = send(mvc, script, "execute");
                                assertThat(validated.path("success").asBoolean()).isTrue();
                                assertThat(previewed.path("success").asBoolean()).isTrue();
                                assertThat(validated.path("data").path("value")).isEqualTo(previewed.path("data").path("value"));
                                assertThat(result.path("success").asBoolean()).isTrue();
                                var rows = result.path("data").path("value").path("plans");
                                assertThat(rows.size()).isEqualTo(2);
                                assertThat(rows.get(0).path("category_id").asText()).isEqualTo("B");
                                assertThat(rows.get(0).path("double_amount").asInt()).isEqualTo(80);
                                assertThat(rows.get(1).path("double_amount").asInt()).isEqualTo(60);
                            }
                        }
                        for (String mode : List.of("validate", "preview", "execute")) {
                            JsonNode badModel = send(mvc, "return {plans:dsl({model:[],columns:['id']})};", mode);
                            assertParameter(badModel, "MODEL_TYPE_INVALID", "model", "dsl.model");
                            for (String entry : List.of("query", "model", "source")) {
                                String prefix = setup("base", "QM");
                                var unsupported = send(mvc, prefix + wrap(entry, "columns:['category_id','double_amount'],calculatedFields:[{name:'double_amount',expression:'bill_amount * 2'}]"), mode);
                                assertParameter(unsupported, "DERIVED_PARAMETER_UNSUPPORTED", "calculatedFields", ("query".equals(entry) ? "query" : "dsl") + ".calculatedFields");
                                assertThat(unsupported.path("error").path("suggestedNextAction").asText()).contains("columns", "AS", ".query");
                                JsonNode denied = send(mvc, setup("base", "Restricted") + wrap(entry, "columns:['category_id','bill_amount']"), mode);
                                assertThat(denied.path("success").asBoolean()).isFalse();
                                var hidden = send(mvc, prefix + wrap(entry, "columns:['revenue * 2 as leaked']"), mode);
                                assertThat(hidden.path("success").asBoolean()).isFalse();
                                JsonNode rows = send(mvc, setup("base", "Rows") + wrap(entry, "columns:['category_id','bill_amount']"), mode);
                                assertThat(rows.path("success").asBoolean()).isTrue();
                                if ("execute".equals(mode)) {
                                    var values = rows.path("data").path("value").path("plans");
                                    assertThat(values.size()).isEqualTo(1);
                                    assertThat(values.get(0).path("bill_amount").asInt()).isEqualTo(30);
                                }
                            }
                            var deniedModel = send(mvc, setup("base", "Denied") + wrap("model", "columns:['category_id','bill_amount']"), mode);
                            assertThat(deniedModel.path("success").asBoolean()).isFalse();
                            var override = send(mvc, setup("base", "QM") + "return {plans:dsl({model:prev,columns:['category_id'],fieldAccess:['revenue']})};", mode);
                            assertThat(override.path("success").asBoolean()).isFalse();
                            assertThat(override.path("error").path("code").asText()).isEqualTo("COMPOSE_SANDBOX_VIOLATION");
                        }
                    } finally {
                        originals.forEach(JdbcQueryModelImpl::setDataSource);
                        bundles.removeBundle("runtime-compose-inputs");
                    }
                });
    }

    void assertParameter(JsonNode value, String code, String field, String path) {
        assertThat(value.path("success").asBoolean()).isFalse();
        assertThat(value.path("error").path("field").asText()).isEqualTo(field);
        assertThat(value.path("error").path("path").asText()).isEqualTo(path);
        assertThat(value.path("error").path("safeToAutoRepair").asBoolean()).isFalse();
        assertThat(value.path("error").path("message").asText()).doesNotContain("ClassCastException", "LaunchedClassLoader", "source output schema");
        assertThat(value.path("diagnostics").path("attributes").path("composeErrorCode").asText()).isEqualTo(code);
        assertThat(value.path("diagnostics").path("attributes").path("composeErrorPhase").asText()).isEqualTo("plan-build");
    }

    JsonNode send(MockMvc mvc, String script, String mode) throws Exception {
        prepared.set(0); executed.set(0);
        String request = json.writeValueAsString(Map.of("script", script));
        var response = mvc.perform(post("/api/v1/compose/" + mode).contentType(MediaType.APPLICATION_JSON).content(request))
                .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode value = json.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        if (!"execute".equals(mode) || !value.path("success").asBoolean()) {
            assertThat(prepared.get()).isZero(); assertThat(executed.get()).isZero();
        } else {
            assertThat(executed.get()).isPositive();
        }
        String evidence = System.getenv("FOGGY_TIMEAXIS_EVIDENCE");
        if (evidence != null && !evidence.isBlank()) {
            Path dir = Path.of(evidence, "runtime-compose-inputs"); Files.createDirectories(dir);
            json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(String.format("%04d-%s.json", sequence.incrementAndGet(), mode)).toFile(),
                    Map.of("script", script, "mode", mode, "prepared", prepared.get(), "executed", executed.get(), "response", value));
        }
        return value;
    }

    String setup(String shape, String suffix) {
        String base = "const bills=dsl({model:'RuntimeCompose" + suffix + "',columns:['category as category_id','sum(revenue) as bill_amount'],groupBy:['category']});";
        String plan = switch (shape) {
            case "derived" -> "bills.query({columns:['category_id','bill_amount']})";
            case "union" -> "bills.union(bills)";
            case "join" -> "bills.join(dsl({model:'RuntimeCompose" + suffix + "',columns:['category as right_id'],groupBy:['category']}),'left',[{left:'category_id',op:'=',right:'right_id'}])";
            default -> "bills";
        };
        return base + "const prev=" + plan + ";";
    }

    String wrap(String entry, String options) {
        return "return {plans:" + ("query".equals(entry) ? "prev.query({" : "dsl({" + entry + ":prev,") + options + "})};";
    }

    Connection observe(Connection connection) {
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

    boolean business(String sql) { return sql.toLowerCase(Locale.ROOT).contains("runtime_compose_inputs"); }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {"org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"})
    static class QueryApplication {
        @Bean AuthorityResolver composeInputAuthority() {
            return request -> {
                Map<String, ModelBinding> bindings = new LinkedHashMap<>();
                for (String name : request.modelNames()) bindings.put(name, ModelBinding.builder()
                        .fieldAccess(name.endsWith("Restricted") ? List.of("id", "category") : List.of("id", "category", "revenue"))
                        .systemSlice(name.endsWith("Rows") ? List.of(new SliceRequestDef("id", "<=", 2)) : List.of()).build());
                return AuthorityResolution.builder().bindings(bindings).build();
            };
        }
    }
}

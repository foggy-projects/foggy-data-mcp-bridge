package io.foggytest.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.*;

/** Real auto-configured engine and Runtime controller; the semantic service is not mocked. */
class RuntimePivotValidationIntegrationTest {
    @TempDir Path root;

    @Test void httpValidateAndExecuteUsePivotBindingAndPermissionContracts() throws Exception {
        Path models = Files.createDirectory(root.resolve("models"));
        Files.writeString(models.resolve("RuntimePivot.tm"), """
            export const model={name:'RuntimePivot',tableName:'runtime_pivot',idColumn:'id',
              properties:[{name:'id',column:'id',type:'INTEGER'},{name:'category',column:'category',type:'STRING'}],
              measures:[{name:'amount',column:'amount',type:'NUMBER',aggregation:'sum'}]};
            """);
        for (String model : new String[]{"RuntimePivotQM", "RuntimePivotDenied"}) {
            Files.writeString(
                    models.resolve(model + ".qm"),
                    "const m=loadTableModel('RuntimePivot');export const queryModel={name:'"
                            + model
                            + "',loader:'v2',model:m,columnGroups:[{caption:'Fields',items:[{ref:m.id},{ref:m.category,name:'region',alias:'regionOut'},{ref:m.amount,name:'revenue',alias:'revenueOut'}]}],accesses:[]"
                            + (model.endsWith("Denied")
                                    ? ",modelPermissions:{mode:'resolver',resolver:(ctx)=>{return"
                                            + " {allow:false};}}"
                                    : "")
                            + "};");
        }
        new WebApplicationContextRunner()
                .withUserConfiguration(QueryApplication.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:sqlite:" + root.resolve("query.sqlite"),
                        "spring.datasource.driver-class-name=org.sqlite.JDBC",
                        "foggy.runtime-api.enabled=true",
                        "foggy.runtime-api.bundle-registry.path=" + root.resolve("bundles.json"),
                        "foggy.runtime-api.datasource-registry.path="
                                + root.resolve("datasources.json"),
                        "foggy.runtime-api.authoring-workspaces.path=" + root.resolve("authoring"))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            var jdbc = context.getBean(JdbcTemplate.class);
                            jdbc.execute(
                                    "CREATE TABLE runtime_pivot(id INTEGER PRIMARY KEY,category"
                                            + " TEXT,amount NUMERIC)");
                            jdbc.update(
                                    "INSERT INTO runtime_pivot"
                                            + " VALUES(1,'A',10),(2,'A',20),(3,'B',40)");
                            var bundles = context.getBean(SystemBundlesContext.class);
                            assertThat(
                                            bundles.addExternalBundle(
                                                    "runtime-pivot-validation",
                                                    "",
                                                    models.toString(),
                                                    false))
                                    .isTrue();
                            var mvc =
                                    MockMvcBuilders.webAppContextSetup(
                                                    context.getSourceApplicationContext())
                                            .build();
                            try {
                                String payload =
                                        "{\"pivot\":{\"rows\":[\"region\"],\"metrics\":[\"revenue\"],\"outputFormat\":\"flat\",\"options\":{\"grandTotal\":false}}}";
                                assertThat(
                                                query(mvc, "RuntimePivotQM", payload, "validate")
                                                        .path("success")
                                                        .asBoolean())
                                        .isTrue();
                                var executed = query(mvc, "RuntimePivotQM", payload, "execute");
                                assertThat(executed.path("success").asBoolean()).isTrue();
                                assertThat(
                                                executed.path("data")
                                                        .path("items")
                                                        .get(0)
                                                        .path("region")
                                                        .asText())
                                        .isEqualTo("A");
                                assertThat(
                                                executed.path("data")
                                                        .path("items")
                                                        .get(0)
                                                        .path("revenue")
                                                        .asInt())
                                        .isEqualTo(30);
                                String zero =
                                        payload.replace(
                                                "[\"region\"]",
                                                "[{\"field\":\"region\",\"start\":0,\"limit\":0}]");
                                for (String mode : new String[] {"validate", "execute"}) {
                                    var empty = query(mvc, "RuntimePivotQM", zero, mode);
                                    assertThat(empty.path("success").asBoolean()).isTrue();
                                    if ("execute".equals(mode)) {
                                        assertThat(empty.path("data").path("items").size())
                                                .isZero();
                                        assertThat(empty.path("data").path("total").asLong())
                                                .isZero();
                                    }
                                    assertThat(
                                                    query(mvc, "RuntimePivotDenied", zero, mode)
                                                            .path("success")
                                                            .asBoolean())
                                            .isFalse();
                                    assertThat(
                                                    query(
                                                                    mvc,
                                                                    "RuntimePivotQM",
                                                                    zero.replace(
                                                                            "region", "missing"),
                                                                    mode)
                                                            .path("success")
                                                            .asBoolean())
                                            .isFalse();
                                }
                                for (String field :
                                        new String[] {"missingDomainField", "regionOut"}) {
                                    String zeroDomain =
                                            "{\"pivot\":{\"rows\":[{\"field\":\"region\",\"limit\":0,\"domainSlice\":[{\"field\":\""
                                                    + field
                                                    + "\",\"op\":\"=\",\"value\":\"x\"}]}],\"metrics\":[\"revenue\"],\"outputFormat\":\"flat\"}}";
                                    for (String mode : new String[] {"validate", "execute"})
                                        assertThat(
                                                        query(
                                                                        mvc,
                                                                        "RuntimePivotQM",
                                                                        zeroDomain,
                                                                        mode)
                                                                .path("success")
                                                                .asBoolean())
                                                .isFalse();
                                }
                                String total =
                                        "{\"calculatedFields\":[{\"name\":\"doubleRevenue\",\"expression\":\"sum(revenue)*2\"}],\"pivot\":{\"rows\":[\"region\"],\"metrics\":[\"doubleRevenue\"],\"outputFormat\":\"flat\",\"options\":{\"grandTotal\":true}}}";
                                assertThat(
                                                query(mvc, "RuntimePivotQM", total, "validate")
                                                        .path("success")
                                                        .asBoolean())
                                        .isTrue();
                                var summary = query(mvc, "RuntimePivotQM", total, "execute");
                                assertThat(summary.path("success").asBoolean()).isTrue();
                                assertThat(
                                                summary.path("data")
                                                        .path("items")
                                                        .findValues("doubleRevenue"))
                                        .anyMatch(
                                                n ->
                                                        n.decimalValue()
                                                                        .compareTo(
                                                                                new java.math
                                                                                        .BigDecimal(
                                                                                        "140"))
                                                                == 0);
                                jdbc.update(
                                        "INSERT INTO runtime_pivot"
                                                + " VALUES(10,NULL,20),(11,'01',10)");
                                String sparse =
                                        "{\"slice\":[{\"field\":\"id\",\"op\":\">=\",\"value\":10}],\"pivot\":{\"rows\":[{\"field\":\"id\",\"start\":0,\"limit\":2,\"orderBy\":[\"id\"]}],\"columns\":[{\"field\":\"region\",\"start\":0,\"limit\":2,\"orderBy\":[\"region\"]}],\"metrics\":[\"revenue\"],\"outputFormat\":\"grid\"}}";
                                assertThat(
                                                query(mvc, "RuntimePivotQM", sparse, "validate")
                                                        .path("success")
                                                        .asBoolean())
                                        .isTrue();
                                var grid = query(mvc, "RuntimePivotQM", sparse, "execute");
                                assertThat(grid.path("success").asBoolean()).isTrue();
                                var headers =
                                        grid.path("data")
                                                .path("items")
                                                .get(0)
                                                .path("columnHeaders");
                                assertThat(headers.get(0).path("region").asText()).isEqualTo("01");
                                assertThat(
                                                headers.get(1).path("region").isMissingNode()
                                                        || headers.get(1).path("region").isNull())
                                        .isTrue();
                                for (String mode : new String[] {"validate", "execute"}) {
                                    assertThat(
                                                    query(
                                                                    mvc,
                                                                    "RuntimePivotQM",
                                                                    payload.replace(
                                                                            "region\"",
                                                                            "regionOut\""),
                                                                    mode)
                                                            .path("success")
                                                            .asBoolean())
                                            .isFalse();
                                    assertThat(
                                                    query(
                                                                    mvc,
                                                                    "RuntimePivotQM",
                                                                    payload.replaceFirst(
                                                                            "\\{",
                                                                            "{\"columns\":[\"id\"],"),
                                                                    mode)
                                                            .path("success")
                                                            .asBoolean())
                                            .isFalse();
                                    assertThat(
                                                    query(mvc, "RuntimePivotDenied", payload, mode)
                                                            .path("success")
                                                            .asBoolean())
                                            .isFalse();
                                    String bad =
                                            payload.replace(
                                                    "\"region\"",
                                                    "{\"field\":\"region\",\"limit\":1,\"orderBy\":[\"-missing\"]}");
                                    var diagnostic = query(mvc, "RuntimePivotQM", bad, mode);
                                    assertThat(diagnostic.path("error").path("path").asText())
                                            .isEqualTo("pivot.rows[0].orderBy[0]");
                                    assertThat(
                                                    diagnostic
                                                            .path("error")
                                                            .path("safeToAutoRepair")
                                                            .asBoolean())
                                            .isFalse();
                                }
                            } finally {
                                bundles.removeBundle("runtime-pivot-validation");
                            }
                        });
    }

    JsonNode query(MockMvc mvc, String model, String payload, String mode) throws Exception {
        var response = mvc.perform(post("/api/v1/query/" + model + "/" + mode)
                .contentType(MediaType.APPLICATION_JSON).content(payload)).andReturn().getResponse();
        return new ObjectMapper().readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
    })
    static class QueryApplication { }
}

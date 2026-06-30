package com.foggyframework.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.bundle.external.ExternalBundleDefinition;
import com.foggyframework.dataset.db.model.engine.compose.SqlGenerationResult;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.service.SemanticModelCatalogService;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.db.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = RuntimeCapabilitiesControllerEnabledTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "foggy.runtime-api.enabled=true",
                "foggy.runtime-api.bundle-registry.path=target/runtime-api-test-bundles-${random.uuid}.json",
                "foggy.runtime-api.datasource-registry.path=target/runtime-api-test-datasources-${random.uuid}.json",
                "spring.autoconfigure.exclude=com.foggyframework.dataset.db.model.DbModelAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
        }
)
class RuntimeCapabilitiesControllerEnabledTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private SemanticModelCatalogService catalogService;

    @MockitoBean
    private SemanticServiceV3 semanticServiceV3;

    @MockitoBean
    private SemanticQueryServiceV3 semanticQueryServiceV3;

    @MockitoBean
    private SystemBundlesContext systemBundlesContext;

    @MockitoBean
    private QueryModelLoader queryModelLoader;

    @MockitoBean
    private TableModelLoaderManager tableModelLoaderManager;

    @MockitoBean
    private DataSource dataSource;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void shouldExposeCapabilitiesWhenRuntimeApiEnabled() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/capabilities",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("engine").asText()).isEqualTo("java");
        assertThat(body.path("runtimeApiVersion").asText()).isEqualTo("foggy-runtime-api/v1");
        assertThat(body.path("data").path("enabled").asBoolean()).isTrue();
        assertThat(body.path("data").path("securityMode").asText()).isEqualTo("none-dev-test-only");
        assertThat(body.path("data").path("capabilities").path("runtime.capabilities").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("models.list").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("models.describe").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("models.refresh").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("models.validate").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("bundles.list").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("bundles.add").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("bundles.update").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("bundles.remove").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("resources.export").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("resources.save").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("datasources.list").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("datasources.add").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("datasources.update").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("datasources.remove").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("datasources.test").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("datasources.bind").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("query.validate").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("query.execute").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("sql.query").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("tables.list").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("tables.inspect").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("compose.validate").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("compose.preview").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("compose.execute").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("fsscript.execute").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("fsscript.cteBridge").asText()).isEqualTo("supported");
    }

    @Test
    void shouldListModelsThroughRuntimeEnvelope() {
        when(catalogService.buildCatalogResponse(anyMap(), isNull(), isNull()))
                .thenReturn(Map.of(
                        "format", "json",
                        "data", Map.of(
                                "count", 1,
                                "models", List.of("OrderModel")
                        )
                ));

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/models",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("format").asText()).isEqualTo("json");
        assertThat(body.path("data").path("data").path("count").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("data").path("models").get(0).asText()).isEqualTo("OrderModel");
    }

    @Test
    void shouldDescribeModelThroughRuntimeEnvelope() {
        SemanticMetadataResponse metadata = new SemanticMetadataResponse();
        metadata.setFormat("json");
        metadata.setContent("{}");
        metadata.setData(Map.of("models", Map.of("OrderModel", Map.of("name", "OrderModel"))));
        when(semanticServiceV3.getMetadata(any(), eq("json"), any())).thenReturn(metadata);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/OrderModel/describe",
                Map.of("format", "json"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("format").asText()).isEqualTo("json");
        assertThat(body.path("data").path("data").path("models").path("OrderModel").path("name").asText())
                .isEqualTo("OrderModel");
    }

    @Test
    void shouldValidateModelsThroughRuntimeEnvelope() {
        Bundle bundle = mock(Bundle.class);
        BundleResource tmResource = bundleResource("Order.tm");
        BundleResource qmResource = bundleResource("OrderModel.qm");
        when(systemBundlesContext.addExternalBundle("runtime-validation-dev", "dev", ".", false)).thenReturn(true);
        when(systemBundlesContext.getBundleByName("runtime-validation-dev")).thenReturn(bundle);
        when(bundle.findBundleResources("**/*.tm")).thenReturn(new BundleResource[]{tmResource});
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{qmResource});
        when(queryModelLoader.loadJdbcQueryModel(qmResource)).thenReturn(mock(QueryModel.class));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/validate",
                Map.of("path", ".", "namespace", "dev"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("valid").asBoolean()).isTrue();
        assertThat(body.path("data").path("namespace").asText()).isEqualTo("dev");
        assertThat(body.path("data").path("totalFiles").asInt()).isEqualTo(2);
        assertThat(body.path("data").path("errors").isArray()).isTrue();
        verify(systemBundlesContext).removeBundle("runtime-validation-dev");
    }

    @Test
    void shouldReturnModelValidateFailedWithDiagnosticsWhenValidationFails() {
        Bundle bundle = mock(Bundle.class);
        BundleResource qmResource = bundleResource("OrderModel.qm");
        when(systemBundlesContext.addExternalBundle("runtime-validation-dev", "dev", ".", false)).thenReturn(true);
        when(systemBundlesContext.getBundleByName("runtime-validation-dev")).thenReturn(bundle);
        when(bundle.findBundleResources("**/*.tm")).thenReturn(new BundleResource[0]);
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{qmResource});
        when(queryModelLoader.loadJdbcQueryModel(qmResource))
                .thenThrow(new IllegalArgumentException("Unknown table model: Order"));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/validate",
                Map.of("path", ".", "namespace", "dev"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("MODEL_VALIDATE_FAILED");
        assertThat(body.path("error").path("phase").asText()).isEqualTo("models.validate");
        assertThat(body.path("diagnostics").path("attributes").path("validation").path("valid").asBoolean())
                .isFalse();
        assertThat(body.path("diagnostics").path("attributes").path("validation").path("errors").get(0)
                .path("message").asText()).isEqualTo("Unknown table model: Order");
        verify(systemBundlesContext).removeBundle("runtime-validation-dev");
    }

    @Test
    void shouldCleanupValidationBundleWhenClearExistingIsFalse() {
        Bundle bundle = mock(Bundle.class);
        BundleResource qmResource = bundleResource("OrderModel.qm");
        when(systemBundlesContext.addExternalBundle("runtime-validation-dev", "dev", ".", false)).thenReturn(true);
        when(systemBundlesContext.getBundleByName("runtime-validation-dev")).thenReturn(bundle);
        when(bundle.findBundleResources("**/*.tm")).thenReturn(new BundleResource[0]);
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{qmResource});
        when(queryModelLoader.loadJdbcQueryModel(qmResource)).thenReturn(mock(QueryModel.class));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/validate",
                Map.of("path", ".", "namespace", "dev", "clearExisting", false),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        verify(systemBundlesContext).removeBundle("runtime-validation-dev");
    }

    @Test
    void shouldCleanupValidationBundleWhenClearExistingIsFalseAndValidationFails() {
        Bundle bundle = mock(Bundle.class);
        BundleResource qmResource = bundleResource("OrderModel.qm");
        when(systemBundlesContext.addExternalBundle("runtime-validation-dev", "dev", ".", false)).thenReturn(true);
        when(systemBundlesContext.getBundleByName("runtime-validation-dev")).thenReturn(bundle);
        when(bundle.findBundleResources("**/*.tm")).thenReturn(new BundleResource[0]);
        when(bundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[]{qmResource});
        when(queryModelLoader.loadJdbcQueryModel(qmResource))
                .thenThrow(new IllegalArgumentException("Unknown table model: Order"));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/validate",
                Map.of("path", ".", "namespace", "dev", "clearExisting", false),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("MODEL_VALIDATE_FAILED");
        verify(systemBundlesContext).removeBundle("runtime-validation-dev");
    }

    @Test
    void shouldReuseExistingBundleForSameNamespaceAndPathDuringValidation() throws Exception {
        Path modelsDir = Files.createTempDirectory("runtime-api-existing-validation-bundle");
        Bundle existingBundle = mock(Bundle.class);
        BundleResource tmResource = bundleResource("Order.tm");
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                "host-loaded-models",
                "dev",
                modelsDir.toString(),
                true
        );
        when(existingBundle.getName()).thenReturn("host-loaded-models");
        when(existingBundle.getDefinition()).thenReturn(definition);
        when(existingBundle.getRootPath()).thenReturn(modelsDir.toString());
        when(existingBundle.findBundleResources("**/*.tm")).thenReturn(new BundleResource[]{tmResource});
        when(existingBundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[0]);
        when(systemBundlesContext.getBundleList()).thenReturn(List.of(existingBundle));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/validate",
                Map.of("path", modelsDir.toString(), "namespace", "dev"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("valid").asBoolean()).isTrue();
        assertThat(body.path("data").path("totalFiles").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("warnings").get(0).path("code").asText())
                .isEqualTo("BUNDLE_ALREADY_REGISTERED");
        verify(systemBundlesContext, never()).addExternalBundle(any(), any(), any(), anyBoolean());
        verify(systemBundlesContext, never()).removeBundle("runtime-validation-dev");
    }

    @Test
    void shouldClearStaleValidationBundleBeforeReusingExistingHostBundle() throws Exception {
        Path modelsDir = Files.createTempDirectory("runtime-api-clear-stale-validation-bundle");
        Bundle existingBundle = mock(Bundle.class);
        BundleResource tmResource = bundleResource("Order.tm");
        ExternalBundleDefinition definition = new ExternalBundleDefinition(
                "host-loaded-models",
                "dev",
                modelsDir.toString(),
                true
        );
        when(existingBundle.getName()).thenReturn("host-loaded-models");
        when(existingBundle.getDefinition()).thenReturn(definition);
        when(existingBundle.getRootPath()).thenReturn(modelsDir.toString());
        when(existingBundle.findBundleResources("**/*.tm")).thenReturn(new BundleResource[]{tmResource});
        when(existingBundle.findBundleResources("**/*.qm")).thenReturn(new BundleResource[0]);
        when(systemBundlesContext.containBundle("runtime-validation-dev")).thenReturn(true);
        when(systemBundlesContext.getBundleList()).thenReturn(List.of(existingBundle));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/validate",
                Map.of("path", modelsDir.toString(), "namespace", "dev"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("warnings").get(0).path("code").asText())
                .isEqualTo("BUNDLE_ALREADY_REGISTERED");
        verify(systemBundlesContext).removeBundle("runtime-validation-dev");
        verify(systemBundlesContext, never()).addExternalBundle(any(), any(), any(), anyBoolean());
    }

    @Test
    void shouldValidateQueryThroughRuntimeEnvelope() {
        SemanticQueryResponse queryResponse = new SemanticQueryResponse();
        SemanticQueryResponse.DebugInfo debug = new SemanticQueryResponse.DebugInfo();
        debug.setDurationMs(12L);
        queryResponse.setDebug(debug);
        when(semanticQueryServiceV3.queryModel(eq("OrderModel"), any(SemanticQueryRequest.class), eq("validate"), any()))
                .thenReturn(queryResponse);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/query/OrderModel/validate",
                Map.of(
                        "namespace", "dev",
                        "payload", Map.of("columns", List.of("orderNo"))
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("debug").path("durationMs").asLong()).isEqualTo(12L);
        verify(semanticQueryServiceV3).queryModel(eq("OrderModel"), any(SemanticQueryRequest.class), eq("validate"), any());
    }

    @Test
    void shouldMapQueryValidateFieldError() {
        when(semanticQueryServiceV3.queryModel(eq("OrderModel"), any(SemanticQueryRequest.class), eq("validate"), any()))
                .thenThrow(new IllegalArgumentException("Field not found: customerName"));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/query/OrderModel/validate",
                Map.of("payload", Map.of("columns", List.of("customerName"))),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("FIELD_NOT_FOUND");
        assertThat(body.path("error").path("phase").asText()).isEqualTo("query.validate");
        assertThat(body.path("error").path("field").asText()).isEqualTo("customerName");
        assertThat(body.path("error").path("safeToAutoRepair").asBoolean()).isTrue();
    }

    @Test
    void shouldMapQueryValidateFieldWarningAsError() {
        SemanticQueryResponse queryResponse = new SemanticQueryResponse();
        queryResponse.setWarnings(List.of("字段不存在: customerName"));
        when(semanticQueryServiceV3.queryModel(eq("OrderModel"), any(SemanticQueryRequest.class), eq("validate"), any()))
                .thenReturn(queryResponse);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/query/OrderModel/validate",
                Map.of("payload", Map.of("columns", List.of("customerName"))),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("FIELD_NOT_FOUND");
        assertThat(body.path("error").path("phase").asText()).isEqualTo("query.validate");
        assertThat(body.path("error").path("field").asText()).isEqualTo("customerName");
        assertThat(body.path("error").path("safeToAutoRepair").asBoolean()).isTrue();
    }

    @Test
    void shouldExecuteQueryThroughRuntimeEnvelope() {
        SemanticQueryResponse queryResponse = new SemanticQueryResponse();
        queryResponse.setItems(List.of(Map.of("orderNo", "SO-001")));
        when(semanticQueryServiceV3.queryModel(eq("OrderModel"), any(SemanticQueryRequest.class), eq("execute"), any()))
                .thenReturn(queryResponse);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/query/OrderModel/execute",
                Map.of("payload", Map.of("columns", List.of("orderNo"), "limit", 1)),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("items").get(0).path("orderNo").asText()).isEqualTo("SO-001");
        verify(semanticQueryServiceV3).queryModel(eq("OrderModel"), any(SemanticQueryRequest.class), eq("execute"), any());
    }

    @Test
    void shouldValidateComposeThroughRuntimeEnvelope() {
        when(semanticQueryServiceV3.generateSql(eq("FactOrderQueryModel"), any(SemanticQueryRequest.class), any()))
                .thenReturn(new SqlGenerationResult("SELECT order_id FROM fact_order", List.of(), null));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/compose/validate",
                Map.of(
                        "script", "const plan = dsl({model: 'FactOrderQueryModel', columns: ['orderId'], limit: 3}); return { plans: plan };",
                        "params", Map.of(),
                        "options", Map.of("diagnostics", "normal")
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("valid").asBoolean()).isTrue();
        assertThat(body.path("data").path("scriptKind").asText()).isEqualTo("compose");
        assertThat(body.path("data").path("mode").asText()).isEqualTo("validate");
        assertThat(body.path("data").path("value").path("plans").path("sql").asText())
                .contains("SELECT order_id FROM fact_order");
        verify(semanticQueryServiceV3, never()).executeSql(any(), any(), any());
    }

    @Test
    void shouldPreviewComposeThroughRuntimeEnvelope() {
        when(semanticQueryServiceV3.generateSql(eq("FactOrderQueryModel"), any(SemanticQueryRequest.class), any()))
                .thenReturn(new SqlGenerationResult("SELECT order_id FROM fact_order", List.of(), null));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/compose/preview",
                Map.of(
                        "script", "const plan = dsl({model: 'FactOrderQueryModel', columns: ['orderId'], limit: 3}); return { plans: plan };",
                        "params", Map.of()
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("mode").asText()).isEqualTo("preview");
        assertThat(body.path("data").path("value").path("plans").path("sql").asText())
                .contains("SELECT order_id FROM fact_order");
        verify(semanticQueryServiceV3, never()).executeSql(any(), any(), any());
    }

    @Test
    void shouldExecuteComposeThroughRuntimeEnvelope() {
        when(semanticQueryServiceV3.generateSql(eq("FactOrderQueryModel"), any(SemanticQueryRequest.class), any()))
                .thenReturn(new SqlGenerationResult("SELECT order_id FROM fact_order", List.of(), null));
        when(semanticQueryServiceV3.executeSql(any(), any(), eq("FactOrderQueryModel")))
                .thenReturn(List.of(Map.of("orderId", "FO-001")));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/compose/execute",
                Map.of(
                        "script", "const plan = dsl({model: 'FactOrderQueryModel', columns: ['orderId'], limit: 3}); return { plans: plan };",
                        "params", Map.of()
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("mode").asText()).isEqualTo("execute");
        assertThat(body.path("data").path("value").path("plans").get(0).path("orderId").asText())
                .isEqualTo("FO-001");
        verify(semanticQueryServiceV3).executeSql(any(), any(), eq("FactOrderQueryModel"));
    }

    @Test
    void shouldReturnComposeSandboxViolationThroughRuntimeEnvelope() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/compose/validate",
                Map.of("script", "import java.lang.System; return { plans: [] };"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("runtimeApiVersion").asText()).isEqualTo("foggy-runtime-api/v1");
        assertThat(body.path("error").path("code").asText()).isEqualTo("COMPOSE_SANDBOX_VIOLATION");
        assertThat(body.path("error").path("phase").asText()).isEqualTo("compose.validate");
        assertThat(body.path("error").path("safeToAutoRepair").asBoolean()).isFalse();
        verify(semanticQueryServiceV3, never()).generateSql(any(), any(), any());
        verify(semanticQueryServiceV3, never()).executeSql(any(), any(), any());
    }

    @Test
    void shouldListConfiguredBundleAsReadOnly() {
        when(systemBundlesContext.listExternalBundles()).thenReturn(List.of(
                new ExternalBundleDefinition("configured-demo", "dev", ".", false)
        ));

        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/bundles",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        JsonNode bundle = body.path("data").path("bundles").get(0);
        assertThat(bundle.path("name").asText()).isEqualTo("configured-demo");
        assertThat(bundle.path("source").asText()).isEqualTo("config");
        assertThat(bundle.path("managedByRuntimeApi").asBoolean()).isFalse();
        assertThat(bundle.path("canRemove").asBoolean()).isFalse();
    }

    @Test
    void shouldAddRuntimeManagedBundle() throws Exception {
        Path modelsDir = Files.createTempDirectory("runtime-api-bundle-test");
        when(systemBundlesContext.addExternalBundle("runtime-demo", "dev", modelsDir.toString(), true))
                .thenReturn(true);

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/bundles",
                Map.of(
                        "name", "runtime-demo",
                        "namespace", "dev",
                        "path", modelsDir.toString(),
                        "watch", true
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("bundle").path("name").asText()).isEqualTo("runtime-demo");
        assertThat(body.path("data").path("bundle").path("source").asText()).isEqualTo("runtime-registry");
        assertThat(body.path("data").path("bundle").path("managedByRuntimeApi").asBoolean()).isTrue();
        verify(systemBundlesContext).addExternalBundle("runtime-demo", "dev", modelsDir.toString(), true);
    }

    @Test
    void shouldRestoreExistingRuntimeManagedBundleWhenUpdateRegistrationFails() throws Exception {
        Path oldModelsDir = Files.createTempDirectory("runtime-api-bundle-rollback-old");
        Path newModelsDir = Files.createTempDirectory("runtime-api-bundle-rollback-new");
        when(systemBundlesContext.addExternalBundle("runtime-rollback", "dev", oldModelsDir.toString(), true))
                .thenReturn(true);

        ResponseEntity<JsonNode> addResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/bundles",
                Map.of(
                        "name", "runtime-rollback",
                        "namespace", "dev",
                        "path", oldModelsDir.toString(),
                        "watch", true
                ),
                JsonNode.class
        );
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(addResponse.getBody().path("success").asBoolean()).isTrue();

        when(systemBundlesContext.containBundle("runtime-rollback")).thenReturn(true);
        when(systemBundlesContext.removeBundle("runtime-rollback")).thenReturn(true);
        when(systemBundlesContext.addExternalBundle("runtime-rollback", "dev", newModelsDir.toString(), true))
                .thenReturn(false);

        ResponseEntity<JsonNode> updateResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/bundles/runtime-rollback",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of(
                        "namespace", "dev",
                        "path", newModelsDir.toString(),
                        "watch", true
                )),
                JsonNode.class
        );

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode updateBody = updateResponse.getBody();
        assertThat(updateBody).isNotNull();
        assertThat(updateBody.path("success").asBoolean()).isFalse();
        assertThat(updateBody.path("error").path("code").asText()).isEqualTo("BUNDLE_ADD_FAILED");
        verify(systemBundlesContext).removeBundle("runtime-rollback");
        verify(systemBundlesContext).addExternalBundle("runtime-rollback", "dev", newModelsDir.toString(), true);
        verify(systemBundlesContext, times(2))
                .addExternalBundle("runtime-rollback", "dev", oldModelsDir.toString(), true);

        ResponseEntity<JsonNode> listResponse = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/bundles",
                JsonNode.class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode restoredBundle = null;
        for (JsonNode candidate : listResponse.getBody().path("data").path("bundles")) {
            if ("runtime-rollback".equals(candidate.path("name").asText())) {
                restoredBundle = candidate;
                break;
            }
        }
        assertThat(restoredBundle).isNotNull();
        assertThat(restoredBundle.path("path").asText()).isEqualTo(oldModelsDir.toString());
    }

    @Test
    void shouldExportRuntimeManagedBundleResources() throws Exception {
        Path modelsDir = Files.createTempDirectory("runtime-api-resources-export-test");
        Files.createDirectories(modelsDir.resolve("model"));
        Files.createDirectories(modelsDir.resolve("query"));
        Files.writeString(modelsDir.resolve("model").resolve("Order.tm"), "table_model Order {}\n");
        Files.writeString(modelsDir.resolve("query").resolve("OrderModel.qm"), "query_model OrderModel {}\n");
        Files.writeString(modelsDir.resolve("notes.txt"), "ignored\n");
        when(systemBundlesContext.addExternalBundle("runtime-resource-export", "dev", modelsDir.toString(), true))
                .thenReturn(true);

        ResponseEntity<JsonNode> addResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/bundles",
                Map.of(
                        "name", "runtime-resource-export",
                        "namespace", "dev",
                        "path", modelsDir.toString(),
                        "watch", true
                ),
                JsonNode.class
        );
        assertThat(addResponse.getBody().path("success").asBoolean()).isTrue();

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/resources/export",
                Map.of(
                        "bundle", "runtime-resource-export",
                        "namespace", "dev"
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        JsonNode resources = body.path("data").path("resources");
        assertThat(resources).hasSize(2);
        assertThat(resources.get(0).path("path").asText()).isEqualTo("model/Order.tm");
        assertThat(resources.get(0).path("content").asText()).contains("table_model Order");
        assertThat(resources.get(0).path("writable").asBoolean()).isTrue();
        assertThat(resources.get(0).path("sha256").asText()).isNotBlank();
        assertThat(resources.get(1).path("path").asText()).isEqualTo("query/OrderModel.qm");
    }

    @Test
    void shouldSaveRuntimeManagedBundleResources() throws Exception {
        Path modelsDir = Files.createTempDirectory("runtime-api-resources-save-test");
        when(systemBundlesContext.addExternalBundle("runtime-resource-save", "dev", modelsDir.toString(), true))
                .thenReturn(true);

        ResponseEntity<JsonNode> addResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/bundles",
                Map.of(
                        "name", "runtime-resource-save",
                        "namespace", "dev",
                        "path", modelsDir.toString(),
                        "watch", true
                ),
                JsonNode.class
        );
        assertThat(addResponse.getBody().path("success").asBoolean()).isTrue();

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/resources/save",
                Map.of(
                        "bundle", "runtime-resource-save",
                        "namespace", "dev",
                        "validate", true,
                        "refresh", true,
                        "files", List.of(Map.of(
                                "path", "model/NewOrder.tm",
                                "content", "table_model NewOrder {}\n"
                        ))
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("savedCount").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("warnings")).hasSize(2);
        assertThat(body.path("data").path("savedResources").get(0).path("path").asText()).isEqualTo("model/NewOrder.tm");
        assertThat(Files.readString(modelsDir.resolve("model").resolve("NewOrder.tm")))
                .isEqualTo("table_model NewOrder {}\n");
    }

    @Test
    void shouldRejectInvalidResourceSaveBatchWithoutPartialWrites() throws Exception {
        Path modelsDir = Files.createTempDirectory("runtime-api-resources-batch-test");
        when(systemBundlesContext.addExternalBundle("runtime-resource-batch", "dev", modelsDir.toString(), true))
                .thenReturn(true);

        ResponseEntity<JsonNode> addResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/bundles",
                Map.of(
                        "name", "runtime-resource-batch",
                        "namespace", "dev",
                        "path", modelsDir.toString(),
                        "watch", true
                ),
                JsonNode.class
        );
        assertThat(addResponse.getBody().path("success").asBoolean()).isTrue();

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/resources/save",
                Map.of(
                        "bundle", "runtime-resource-batch",
                        "namespace", "dev",
                        "files", List.of(
                                Map.of(
                                        "path", "model/Partial.tm",
                                        "content", "table_model Partial {}\n"
                                ),
                                Map.of(
                                        "path", "notes.txt",
                                        "content", "not a managed model resource\n"
                                )
                        )
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("RESOURCE_TYPE_NOT_ALLOWED");
        assertThat(Files.exists(modelsDir.resolve("model").resolve("Partial.tm"))).isFalse();
    }

    @Test
    void shouldRejectSavingConfiguredBundleResources() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/resources/save",
                Map.of(
                        "bundle", "configured-demo",
                        "files", List.of(Map.of(
                                "path", "model/NewOrder.tm",
                                "content", "table_model NewOrder {}\n"
                        ))
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("RESOURCE_BUNDLE_NOT_WRITABLE");
    }

    @Test
    void shouldRejectRemovingConfiguredBundle() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/bundles/configured-demo",
                org.springframework.http.HttpMethod.DELETE,
                null,
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("BUNDLE_NOT_MANAGED");
        verify(systemBundlesContext, never()).removeBundle("configured-demo");
    }

    @Test
    void shouldListConfiguredDefaultDatasourceAsReadOnly() {
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/v1/datasources",
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        JsonNode datasource = body.path("data").path("datasources").get(0);
        assertThat(datasource.path("name").asText()).isEqualTo("default");
        assertThat(datasource.path("source").asText()).isEqualTo("config");
        assertThat(datasource.path("managedByRuntimeApi").asBoolean()).isFalse();
        assertThat(datasource.path("canRemove").asBoolean()).isFalse();
        assertThat(datasource.path("canTest").asBoolean()).isTrue();
    }

    @Test
    void shouldAddTestBindAndInspectRuntimeManagedSqliteDatasource() throws Exception {
        Path db = Files.createTempFile("runtime-api-datasource-test", ".db");
        String jdbcUrl = "jdbc:sqlite:" + db.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE sales_drop_daily (
                        id INTEGER PRIMARY KEY,
                        sales_date TEXT NOT NULL,
                        sales_amount REAL
                    )
                    """);
        }

        ResponseEntity<JsonNode> addResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/datasources",
                Map.of(
                        "name", "sales-sqlite",
                        "type", "sqlite",
                        "jdbcUrl", jdbcUrl
                ),
                JsonNode.class
        );
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(addResponse.getBody().path("success").asBoolean()).isTrue();
        assertThat(addResponse.getBody().path("data").path("datasource").path("managedByRuntimeApi").asBoolean())
                .isTrue();

        ResponseEntity<JsonNode> testResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/datasources/sales-sqlite/test",
                null,
                JsonNode.class
        );
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testResponse.getBody().path("success").asBoolean()).isTrue();
        assertThat(testResponse.getBody().path("data").path("connected").asBoolean()).isTrue();
        assertThat(testResponse.getBody().path("data").path("productName").asText()).containsIgnoringCase("sqlite");

        ResponseEntity<JsonNode> bindResponse = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/namespaces/dev/datasource",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of(
                        "namespace", "dev",
                        "dataSource", "sales-sqlite"
                )),
                JsonNode.class
        );
        assertThat(bindResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(bindResponse.getBody().path("success").asBoolean()).isTrue();
        assertThat(bindResponse.getBody().path("data").path("dataSource").asText()).isEqualTo("sales-sqlite");

        ResponseEntity<JsonNode> inspectResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/tables/inspect",
                Map.of(
                        "dataSource", "sales-sqlite",
                        "table", "sales_drop_daily",
                        "includeIndexes", true
                ),
                JsonNode.class
        );
        assertThat(inspectResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode inspectBody = inspectResponse.getBody();
        assertThat(inspectBody).isNotNull();
        assertThat(inspectBody.path("success").asBoolean()).isTrue();
        assertThat(inspectBody.path("data").path("dataSource").asText()).isEqualTo("sales-sqlite");
        assertThat(inspectBody.path("data").path("columns").get(0).path("name").asText()).isEqualTo("id");
        assertThat(inspectBody.path("data").path("columns").get(2).path("name").asText()).isEqualTo("sales_amount");
    }

    @Test
    void shouldRejectRemovingDefaultDatasource() {
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/v1/datasources/default",
                org.springframework.http.HttpMethod.DELETE,
                null,
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("DATASOURCE_NOT_MANAGED");
    }

    @Test
    void shouldListTablesAndRunReadOnlySqlOnRuntimeManagedSqliteDatasource() throws Exception {
        Path db = Files.createTempFile("runtime-api-sql-query-test", ".db");
        String jdbcUrl = "jdbc:sqlite:" + db.toAbsolutePath();
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE sales_probe_daily (
                        id INTEGER PRIMARY KEY,
                        region TEXT NOT NULL,
                        sales_amount REAL
                    )
                    """);
            statement.execute("INSERT INTO sales_probe_daily(region, sales_amount) VALUES ('East', 120.5)");
            statement.execute("INSERT INTO sales_probe_daily(region, sales_amount) VALUES ('West', 80.0)");
        }

        ResponseEntity<JsonNode> addResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/datasources",
                Map.of(
                        "name", "probe-sqlite",
                        "type", "sqlite",
                        "jdbcUrl", jdbcUrl
                ),
                JsonNode.class
        );
        assertThat(addResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(addResponse.getBody().path("success").asBoolean()).isTrue();

        ResponseEntity<JsonNode> listResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/tables/list",
                Map.of("dataSource", "probe-sqlite"),
                JsonNode.class
        );
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode listBody = listResponse.getBody();
        assertThat(listBody).isNotNull();
        assertThat(listBody.path("success").asBoolean()).isTrue();
        assertThat(listBody.path("data").path("dataSource").asText()).isEqualTo("probe-sqlite");
        boolean foundTable = false;
        for (JsonNode table : listBody.path("data").path("tables")) {
            foundTable = foundTable || table.path("name").asText().equals("sales_probe_daily");
        }
        assertThat(foundTable).isTrue();

        ResponseEntity<JsonNode> queryResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/sql/query",
                Map.of(
                        "dataSource", "probe-sqlite",
                        "sql", "select region, sales_amount from sales_probe_daily order by id",
                        "maxRows", 1
                ),
                JsonNode.class
        );
        assertThat(queryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode queryBody = queryResponse.getBody();
        assertThat(queryBody).isNotNull();
        assertThat(queryBody.path("success").asBoolean()).isTrue();
        assertThat(queryBody.path("data").path("dataSource").asText()).isEqualTo("probe-sqlite");
        assertThat(queryBody.path("data").path("columns").get(0).path("name").asText()).isEqualTo("region");
        assertThat(queryBody.path("data").path("rows").get(0).path("region").asText()).isEqualTo("East");
        assertThat(queryBody.path("data").path("rowCount").asInt()).isEqualTo(1);
        assertThat(queryBody.path("data").path("truncated").asBoolean()).isTrue();

        ResponseEntity<JsonNode> rejectedResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/sql/query",
                Map.of(
                        "dataSource", "probe-sqlite",
                        "sql", "delete from sales_probe_daily"
                ),
                JsonNode.class
        );
        assertThat(rejectedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode rejectedBody = rejectedResponse.getBody();
        assertThat(rejectedBody).isNotNull();
        assertThat(rejectedBody.path("success").asBoolean()).isFalse();
        assertThat(rejectedBody.path("error").path("code").asText()).isEqualTo("SQL_QUERY_REJECTED");
        assertThat(rejectedBody.path("error").path("phase").asText()).isEqualTo("sql.query");
    }

    @Test
    void shouldExecuteFsscriptThroughRuntimeEnvelope() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/fsscript/execute",
                Map.of("script", "return 1 + 2;"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("scriptKind").asText()).isEqualTo("fsscript");
        assertThat(body.path("data").path("mode").asText()).isEqualTo("execute");
        assertThat(body.path("data").path("value").asInt()).isEqualTo(3);
    }

    @Test
    void shouldDenyFsscriptCteBridgeByDefault() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/fsscript/execute",
                Map.of("script", "return foggy.cte.preview({script: \"return { plans: [] };\"});"),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("FSSCRIPT_CTE_BRIDGE_DENIED");
        assertThat(body.path("error").path("phase").asText()).isEqualTo("fsscript.execute");
        assertThat(body.path("error").path("safeToAutoRepair").asBoolean()).isFalse();
        verify(semanticQueryServiceV3, never()).generateSql(any(), any(), any());
        verify(semanticQueryServiceV3, never()).executeSql(any(), any(), any());
    }

    @Test
    void shouldPreviewComposeThroughFsscriptCteBridge() {
        when(semanticQueryServiceV3.generateSql(eq("FactOrderQueryModel"), any(SemanticQueryRequest.class), any()))
                .thenReturn(new SqlGenerationResult("SELECT order_id FROM fact_order", List.of(), null));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/fsscript/execute",
                Map.of(
                        "script", "return foggy.cte.preview({script: \"const plan = dsl({model: 'FactOrderQueryModel', columns: ['orderId'], limit: 3}); return { plans: plan };\"});",
                        "capabilities", Map.of("cteBridge", true)
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("scriptKind").asText()).isEqualTo("fsscript");
        JsonNode value = body.path("data").path("value");
        assertThat(value.path("scriptKind").asText()).isEqualTo("compose");
        assertThat(value.path("mode").asText()).isEqualTo("preview");
        assertThat(value.path("value").path("plans").path("sql").asText())
                .contains("SELECT order_id FROM fact_order");
        verify(semanticQueryServiceV3, never()).executeSql(any(), any(), any());
    }

    @Test
    void shouldReturnComposeSandboxViolationThroughFsscriptCteBridge() {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/fsscript/execute",
                Map.of(
                        "script", "return foggy.cte.validate({script: \"import java.lang.System; return { plans: [] };\"});",
                        "capabilities", Map.of("cteBridge", true)
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("COMPOSE_SANDBOX_VIOLATION");
        assertThat(body.path("error").path("phase").asText()).isEqualTo("compose.validate");
        assertThat(body.path("error").path("safeToAutoRepair").asBoolean()).isFalse();
        verify(semanticQueryServiceV3, never()).generateSql(any(), any(), any());
        verify(semanticQueryServiceV3, never()).executeSql(any(), any(), any());
    }

    @Test
    void shouldInspectTableThroughRuntimeEnvelope() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet columns = mock(ResultSet.class);
        ResultSet primaryKeys = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.getCatalog()).thenReturn(null);
        when(metadata.getTables(null, "public", "sale_order", new String[]{"TABLE", "VIEW"})).thenReturn(tables);
        when(tables.next()).thenReturn(true, false);
        when(tables.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(metadata.getColumns(null, "public", "sale_order", null)).thenReturn(columns);
        when(columns.next()).thenReturn(true, true, false);
        when(columns.getString("COLUMN_NAME")).thenReturn("id", "amount_total");
        when(columns.getString("TYPE_NAME")).thenReturn("BIGINT", "NUMERIC");
        when(columns.getInt("DATA_TYPE")).thenReturn(-5, 2);
        when(columns.getInt("COLUMN_SIZE")).thenReturn(19, 18);
        when(columns.getInt("DECIMAL_DIGITS")).thenReturn(0, 2);
        when(columns.getString("IS_NULLABLE")).thenReturn("NO", "YES");
        when(columns.getString("COLUMN_DEF")).thenReturn(null).thenReturn(null);
        when(columns.getInt("ORDINAL_POSITION")).thenReturn(1, 2);
        when(columns.wasNull()).thenReturn(false);
        when(metadata.getPrimaryKeys(null, "public", "sale_order")).thenReturn(primaryKeys);
        when(primaryKeys.next()).thenReturn(true, false);
        when(primaryKeys.getString("PK_NAME")).thenReturn("sale_order_pkey");
        when(primaryKeys.getString("COLUMN_NAME")).thenReturn("id");

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/tables/inspect",
                Map.of(
                        "schema", "public",
                        "table", "sale_order",
                        "includeForeignKeys", false,
                        "includeIndexes", false
                ),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("schema").asText()).isEqualTo("public");
        assertThat(body.path("data").path("table").asText()).isEqualTo("sale_order");
        assertThat(body.path("data").path("columns").get(0).path("name").asText()).isEqualTo("id");
        assertThat(body.path("data").path("columns").get(1).path("jdbcType").asText()).isEqualTo("NUMERIC");
        assertThat(body.path("data").path("primaryKey").path("columns").get(0).asText()).isEqualTo("id");
    }

    private static BundleResource bundleResource(String filename) {
        Resource resource = mock(Resource.class);
        when(resource.getFilename()).thenReturn(filename);
        BundleResource bundleResource = mock(BundleResource.class);
        when(bundleResource.getResource()).thenReturn(resource);
        return bundleResource;
    }

    @Test
    void shouldRefreshRequestedModelsThroughRuntimeEnvelope() {
        when(queryModelLoader.getJdbcQueryModel(eq("OrderModel"), isNull())).thenReturn(mock(QueryModel.class));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/refresh",
                Map.of("models", List.of("OrderModel")),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("scope").asText()).isEqualTo("models");
        assertThat(body.path("data").path("loadedCount").asInt()).isEqualTo(1);
        assertThat(body.path("data").path("failedCount").asInt()).isZero();
        assertThat(body.path("data").path("refreshedModels").get(0).asText()).isEqualTo("OrderModel");
        verify(tableModelLoaderManager).clearByNamespace(null);
        verify(queryModelLoader).clearByNamespace(null);
    }

    @Test
    void shouldReturnModelRefreshFailedWhenRequestedModelFails() {
        when(queryModelLoader.getJdbcQueryModel(eq("MissingModel"), isNull()))
                .thenThrow(new IllegalArgumentException("QM model was not found."));

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/models/refresh",
                Map.of("models", List.of("MissingModel")),
                JsonNode.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.path("success").asBoolean()).isFalse();
        assertThat(body.path("error").path("code").asText()).isEqualTo("MODEL_REFRESH_FAILED");
        assertThat(body.path("error").path("phase").asText()).isEqualTo("models.refresh");
        assertThat(body.path("error").path("model").asText()).isEqualTo("MissingModel");
        assertThat(body.path("diagnostics").path("attributes").path("refresh").path("failedCount").asInt())
                .isEqualTo(1);
        assertThat(body.path("diagnostics").path("attributes").path("refresh").path("failures").get(0)
                .path("model").asText()).isEqualTo("MissingModel");
    }

    @SpringBootApplication(scanBasePackages = "com.foggyframework.runtime.api")
    static class TestApplication {
    }
}

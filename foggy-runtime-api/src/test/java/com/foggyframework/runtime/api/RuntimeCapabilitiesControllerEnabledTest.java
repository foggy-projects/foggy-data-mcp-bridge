package com.foggyframework.runtime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.foggyframework.bundle.Bundle;
import com.foggyframework.bundle.BundleResource;
import com.foggyframework.bundle.SystemBundlesContext;
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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = RuntimeCapabilitiesControllerEnabledTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "foggy.runtime-api.enabled=true",
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
        assertThat(body.path("data").path("capabilities").path("query.validate").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("query.execute").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("tables.inspect").asText()).isEqualTo("supported");
        assertThat(body.path("data").path("capabilities").path("compose.validate").asText()).isEqualTo("unsupported");
        assertThat(body.path("data").path("capabilities").path("compose.execute").asText()).isEqualTo("unsupported");
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

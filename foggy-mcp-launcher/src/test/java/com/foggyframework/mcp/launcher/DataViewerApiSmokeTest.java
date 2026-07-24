package com.foggyframework.mcp.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.core.annotates.EnableFoggyFramework;
import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataset.model.config.DatasetProperties;
import com.foggyframework.dataset.model.spi.QueryModelLoader;
import com.foggyframework.dataset.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.model.spi.TableModelLoaderManager;
import com.foggyframework.dataviewer.controller.ViewerApiController;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
import com.foggyframework.dataviewer.service.MemberQueryService;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = DataViewerApiSmokeTest.SmokeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = DataViewerApiSmokeTest.Initializer.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Data Viewer real API smoke")
class DataViewerApiSmokeTest {

    private static final String QUERY_MODEL = "FactSalesSemanticScaleQueryModel";
    private static final String RUNTIME_FILTER_QUERY_MODEL = "OrderSalesAggregateRelationRuntimeFilterQueryModel";
    private static final String TABLE_MODEL = "FactSalesSemanticScaleModel";
    private static final String PHYSICAL_NAMESPACE = "data-viewer-smoke-physical";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatasetProperties datasetProperties;

    @Autowired
    private SystemBundlesContext systemBundlesContext;

    @Autowired
    private TableModelLoaderManager tableModelLoaderManager;

    @Autowired
    private QueryModelLoader queryModelLoader;

    private String physicalNamespace;

    @BeforeAll
    void registerPhysicalNamespace() {
        physicalNamespace = PHYSICAL_NAMESPACE;

        DatasetProperties.SemanticScaleConfig config = datasetProperties.getSemanticScale();
        config.setDefaultEnabled(true);
        List<String> disabledNamespaces = config.getDisabledNamespaces() == null
                ? new ArrayList<>()
                : new ArrayList<>(config.getDisabledNamespaces());
        disabledNamespaces.add(physicalNamespace);
        config.setDisabledNamespaces(disabledNamespaces);

        assertTrue(systemBundlesContext.addExternalBundle(
                "data-viewer-smoke-ecommerce",
                physicalNamespace,
                ecommerceBundlePath(),
                false));
        tableModelLoaderManager.clearByNamespace(physicalNamespace);
        queryModelLoader.clearByNamespace(physicalNamespace);
    }

    @Test
    @DisplayName("frontend-meta follows X-NS semanticScale settings")
    void frontendMetaUsesNamespace() throws Exception {
        JsonNode defaultMeta = getFrontendMeta(null);
        JsonNode physicalMeta = getFrontendMeta(physicalNamespace);

        JsonNode defaultField = findField(defaultMeta.path("data").path("fields"), "salesAmountYuan");
        JsonNode physicalField = findField(physicalMeta.path("data").path("fields"), "salesAmountYuan");

        assertNotNull(defaultField, "default namespace should expose salesAmountYuan");
        assertNotNull(physicalField, "physical namespace should expose salesAmountYuan");
        assertEquals("100", defaultField.path("semanticScaleFactor").asText());
        assertTrue(physicalField.path("semanticScaleFactor").isMissingNode(),
                "disabled namespace should not expose semanticScaleFactor");
    }

    @Test
    @DisplayName("query/direct follows X-NS semanticScale settings")
    void directQueryUsesNamespace() throws Exception {
        JsonNode defaultQuery = postDirectQuery(null);
        JsonNode physicalQuery = postDirectQuery(physicalNamespace);

        JsonNode defaultRow = firstRow(defaultQuery);
        JsonNode physicalRow = firstRow(physicalQuery);
        assertEquals(defaultRow.path("orderId").asText(), physicalRow.path("orderId").asText());

        BigDecimal defaultAmount = decimal(defaultRow.path("salesAmountYuan"));
        BigDecimal physicalAmount = decimal(physicalRow.path("salesAmountYuan"));
        assertEquals(0, normalize(defaultAmount.multiply(new BigDecimal("100"))).compareTo(normalize(physicalAmount)),
                "default namespace should return yuan while disabled namespace returns physical cents; default="
                + defaultAmount + ", physical=" + physicalAmount);
    }

    @Test
    @DisplayName("query/direct passes extData to aggregate relation runtime filter")
    void directQueryPassesExtDataToAggregateRelationRuntimeFilter() throws Exception {
        JsonNode query = postDirectRuntimeFilterQuery("""
                {
                  "start": 0,
                  "limit": 2,
                  "columns": ["orderId", "amount", "salesAmount", "uniqueCustomers"],
                  "orderBy": [
                    { "field": "orderId", "dir": "ASC" }
                  ],
                  "extData": {
                    "orderId": "ORD20240101000001"
                  }
                }
                """);

        assertEquals(200, query.path("code").asInt(), query.toString());
        assertTrue(query.path("data").path("success").asBoolean(), query.toString());

        JsonNode items = query.path("data").path("items");
        assertTrue(items.isArray() && items.size() >= 2, query.toString());

        JsonNode matchedOrder = items.get(0);
        JsonNode unmatchedOrder = items.get(1);
        assertEquals("ORD20240101000001", matchedOrder.path("orderId").asText(), matchedOrder.toString());
        assertEquals("ORD20240101000002", unmatchedOrder.path("orderId").asText(), unmatchedOrder.toString());
        assertEquals(0, new BigDecimal("9898.20").compareTo(decimal(matchedOrder.path("salesAmount"))),
                matchedOrder.toString());
        assertEquals(1, matchedOrder.path("uniqueCustomers").asInt(), matchedOrder.toString());
        assertTrue(isNullLike(unmatchedOrder.path("salesAmount")),
                "RHS runtime filter should not expose sales for a different order: " + unmatchedOrder);
    }

    @Test
    @DisplayName("query/direct aggregate relation runtime filter fails closed when extData is missing")
    void directQueryRuntimeFilterFailsClosedWhenExtDataMissing() throws Exception {
        JsonNode query = postDirectRuntimeFilterQuery("""
                {
                  "start": 0,
                  "limit": 1,
                  "columns": ["orderId", "amount", "salesAmount"]
                }
                """);

        assertEquals(600, query.path("code").asInt(), query.toString());
        assertTrue(query.path("msg").asText().contains("aggregate relation runtime filter")
                        && query.path("msg").asText().contains("不能为空"),
                query.toString());
    }

    @Test
    @DisplayName("query/direct aggregate relation runtime filter fails closed when extData value is blank")
    void directQueryRuntimeFilterFailsClosedWhenExtDataValueBlank() throws Exception {
        JsonNode query = postDirectRuntimeFilterQuery("""
                {
                  "start": 0,
                  "limit": 1,
                  "columns": ["orderId", "amount", "salesAmount"],
                  "extData": {
                    "orderId": ""
                  }
                }
                """);

        assertEquals(600, query.path("code").asInt(), query.toString());
        assertTrue(query.path("msg").asText().contains("aggregate relation runtime filter")
                        && query.path("msg").asText().contains("不能为空"),
                query.toString());
    }

    @Test
    @DisplayName("query/direct does not treat nested param.extData as direct runtime context")
    void directQueryRuntimeFilterDoesNotReadNestedParamExtData() throws Exception {
        JsonNode query = postDirectRuntimeFilterQuery("""
                {
                  "start": 0,
                  "limit": 1,
                  "columns": ["orderId", "amount", "salesAmount"],
                  "param": {
                    "extData": {
                      "orderId": "ORD20240101000001"
                    }
                  }
                }
                """);

        assertEquals(600, query.path("code").asInt(), query.toString());
        assertTrue(query.path("msg").asText().contains("aggregate relation runtime filter 值不能为空"),
                query.toString());
    }

    private JsonNode getFrontendMeta(String namespace) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        if (namespace != null) {
            headers.set("X-NS", namespace);
        }

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/data-viewer/api/frontend-meta/" + QUERY_MODEL,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(200, body.path("code").asInt(), body.toString());
        return body;
    }

    private JsonNode postDirectQuery(String namespace) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (namespace != null) {
            headers.set("X-NS", namespace);
        }

        String body = """
                {
                  "start": 0,
                  "limit": 1,
                  "columns": ["orderId", "salesAmountYuan"],
                  "orderBy": [
                    { "field": "orderId", "dir": "ASC" }
                  ]
                }
                """;

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/data-viewer/api/query/direct/" + QUERY_MODEL,
                new HttpEntity<>(body, headers),
                String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());

        JsonNode json = objectMapper.readTree(response.getBody());
        assertEquals(200, json.path("code").asInt(), json.toString());
        assertTrue(json.path("data").path("success").asBoolean(), json.toString());
        return json;
    }

    private JsonNode postDirectRuntimeFilterQuery(String body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/data-viewer/api/query/direct/" + RUNTIME_FILTER_QUERY_MODEL,
                new HttpEntity<>(body, headers),
                String.class);
        assertTrue(response.getStatusCode().is2xxSuccessful());
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode firstRow(JsonNode queryResponse) {
        JsonNode items = queryResponse.path("data").path("items");
        assertTrue(items.isArray() && !items.isEmpty(), queryResponse.toString());
        JsonNode row = items.get(0);
        assertFalse(row.path("orderId").isMissingNode(), row.toString());
        assertFalse(row.path("salesAmountYuan").isMissingNode(), row.toString());
        return row;
    }

    private JsonNode findField(JsonNode fields, String name) {
        assertTrue(fields.isArray(), fields.toString());
        for (JsonNode field : fields) {
            if (name.equals(field.path("name").asText())) {
                return field;
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node) {
        assertFalse(node.isMissingNode(), "amount field missing");
        return new BigDecimal(node.asText());
    }

    private boolean isNullLike(JsonNode node) {
        return node.isMissingNode() || node.isNull() || node.asText().isBlank();
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private String ecommerceBundlePath() {
        List<Path> candidates = List.of(
                Paths.get("foggy-dataset-demo", "src", "main", "resources", "foggy", "templates", "ecommerce"),
                Paths.get("..", "foggy-dataset-demo", "src", "main", "resources", "foggy", "templates", "ecommerce")
        );
        for (Path candidate : candidates) {
            if (hasSemanticScaleFixture(candidate)) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }
        fail("ecommerce bundle path should contain semantic scale fixtures: " + candidates);
        return "";
    }

    private boolean hasSemanticScaleFixture(Path path) {
        return Files.isDirectory(path)
                && Files.isRegularFile(path.resolve("model").resolve(TABLE_MODEL + ".tm"))
                && Files.isRegularFile(path.resolve("query").resolve(QUERY_MODEL + ".qm"));
    }

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            ConfigurableEnvironment environment = context.getEnvironment();
            Path sqliteDir = findSqliteDir();

            TestPropertyValues.of(
                    "spring.datasource.url=jdbc:sqlite:file:data-viewer-api-smoke?mode=memory&cache=shared",
                    "spring.datasource.driver-class-name=org.sqlite.JDBC",
                    "spring.autoconfigure.exclude=" +
                            "com.foggyframework.dataset.mcp.DatasetMcpAutoConfiguration," +
                            "com.foggyframework.dataviewer.config.DataViewerAutoConfiguration," +
                            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration," +
                            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration," +
                            "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration," +
                            "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration," +
                            "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration," +
                            "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration," +
                            "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration," +
                            "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration," +
                            "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration",
                    "spring.ai.model.audio.speech=none",
                    "spring.ai.model.audio.transcription=none",
                    "spring.ai.model.chat=none",
                    "spring.ai.model.embedding=none",
                    "spring.ai.model.image=none",
                    "spring.ai.model.moderation=none",
                    "spring.sql.init.mode=always",
                    "spring.sql.init.schema-locations=" + locations(sqliteDir,
                            "01-schema.sql", "04-preagg-schema.sql", "06-odoo-schema.sql", "08-odoo-closure-schema.sql"),
                    "spring.sql.init.data-locations=" + locations(sqliteDir,
                            "02-dict-data.sql", "03-test-data.sql", "05-preagg-data.sql", "07-odoo-data.sql",
                            "09-odoo-closure-data.sql"),
                    "foggy.dataset.templates-path=classpath:/foggy/templates/",
                    "foggy.dataset.show-sql=false",
                    "foggy.dataset.request.default-namespace=",
                    "foggy.mcp.audit.enabled=false",
                    "foggy.data-viewer.enabled=true",
                    "foggy.data-viewer.list-preset.storage=file"
            ).applyTo(environment);
        }

        private static Path findSqliteDir() {
            List<Path> candidates = List.of(
                    Paths.get("foggy-dataset-model-engine", "src", "test", "resources", "sqlite"),
                    Paths.get("..", "foggy-dataset-model-engine", "src", "test", "resources", "sqlite")
            );
            for (Path candidate : candidates) {
                if (Files.isRegularFile(candidate.resolve("01-schema.sql"))) {
                    return candidate.toAbsolutePath().normalize();
                }
            }
            throw new IllegalStateException("sqlite test resources not found: " + candidates);
        }

        private static String locations(Path dir, String... files) {
            List<String> locations = new ArrayList<>();
            for (String file : files) {
                locations.add(dir.resolve(file).toUri().toString());
            }
            return String.join(",", locations);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "com.foggyframework.dataset.mcp.DatasetMcpAutoConfiguration",
            "com.foggyframework.dataviewer.config.DataViewerAutoConfiguration",
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration",
            "org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration",
            "org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration"
    })
    @EnableFoggyFramework(bundleName = "data-viewer-api-smoke-test")
    static class SmokeApplication {

        public static void main(String[] args) {
            SpringApplication.run(SmokeApplication.class, args);
        }

        @Bean
        DataViewerProperties dataViewerProperties() {
            return new DataViewerProperties();
        }

        @Bean
        CachedQueryRepository cachedQueryRepository() {
            return Mockito.mock(CachedQueryRepository.class);
        }

        @Bean
        QueryCacheService queryCacheService(CachedQueryRepository repository,
                                            DataViewerProperties properties) {
            return new QueryCacheService(repository, properties);
        }

        @Bean
        MemberQueryService memberQueryService(AdvancedQueryFacade queryFacade) {
            return new MemberQueryService(queryFacade);
        }

        @Bean
        NamedDataSourceResolver dataViewerSmokeNamedDataSourceResolver(DataSource dataSource) {
            return new NamedDataSourceResolver() {
                @Override
                public DataSource resolve(String name) {
                    return null;
                }

                @Override
                public DataSource resolveDefault(String namespace) {
                    return PHYSICAL_NAMESPACE.equals(namespace) ? dataSource : null;
                }

                @Override
                public boolean isConfigured(String name) {
                    return false;
                }
            };
        }

        @Bean
        ViewerApiController viewerApiController(QueryCacheService cacheService,
                                                AdvancedQueryFacade queryFacade,
                                                DatasetProperties datasetProperties,
                                                MemberQueryService memberQueryService) {
            return new ViewerApiController(cacheService, queryFacade, datasetProperties, memberQueryService);
        }
    }
}

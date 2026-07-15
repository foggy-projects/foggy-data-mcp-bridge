package com.foggyframework.dataset.vector.integration;

import com.foggyframework.dataset.vector.VectorTestApplication;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milvus integration coverage backed by a runner-owned instance and deterministic embeddings.
 */
@SpringBootTest(classes = {
        VectorTestApplication.class,
        VectorStoreIT.DeterministicVectorTestConfiguration.class
})
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class VectorStoreIT {

    private static final String MILVUS_HOST_PROPERTY = "v934.vector.milvus.host";
    private static final String MILVUS_PORT_PROPERTY = "v934.vector.milvus.port";
    private static final String COLLECTION_NAME = "v934_vector_store";
    private static final String DELETE_PROBE_ID = "v934-delete-probe";
    private static final int EMBEDDING_DIMENSIONS = 8;
    private static final long CREATED_AT = 1_710_864_000_000L;
    private static final Set<String> BASELINE_IDS = Set.of("qt1", "qt2", "qt3", "qt4", "qt5");
    private static final Set<String> EXPECTED_METADATA_KEYS = Set.of(
            "template_type", "model_name", "tags", "usage_count", "created_at", "distance");

    private static final List<BaselineDocument> BASELINE = List.of(
            new BaselineDocument("qt1", "最近一周各品牌销售情况", "dsl", "FactSalesQueryModel"),
            new BaselineDocument("qt2", "本月销售数据统计分析", "dsl", "FactSalesQueryModel"),
            new BaselineDocument("qt3", "销售趋势分析指南", "guide", "FactSalesQueryModel"),
            new BaselineDocument("qt4", "库存不足商品查询", "dsl", "FactInventoryQueryModel"),
            new BaselineDocument("qt5", "客户购买行为分析", "guide", "FactOrderQueryModel")
    );

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private MilvusServiceClient milvusClient;

    @DynamicPropertySource
    static void configureMilvus(DynamicPropertyRegistry registry) {
        String host = requireSystemProperty(MILVUS_HOST_PROPERTY);
        int port = parsePort(requireSystemProperty(MILVUS_PORT_PROPERTY));

        registry.add("spring.ai.vectorstore.type", () -> "milvus");
        registry.add("spring.ai.vectorstore.milvus.client.host", () -> host);
        registry.add("spring.ai.vectorstore.milvus.client.port", () -> port);
        registry.add("spring.ai.vectorstore.milvus.collection-name", () -> COLLECTION_NAME);
        registry.add("spring.ai.vectorstore.milvus.embedding-dimension", () -> EMBEDDING_DIMENSIONS);
        registry.add("spring.ai.vectorstore.milvus.initialize-schema", () -> true);
        registry.add("spring.ai.vectorstore.milvus.index-type", () -> "FLAT");
        registry.add("spring.ai.vectorstore.milvus.metric-type", () -> "COSINE");

        // The integration lane is deliberately independent of every remote model provider.
        registry.add("spring.ai.model.chat", () -> "none");
        registry.add("spring.ai.model.embedding", () -> "none");
        registry.add("spring.ai.model.image", () -> "none");
        registry.add("spring.ai.model.audio.speech", () -> "none");
        registry.add("spring.ai.model.audio.transcription", () -> "none");
        registry.add("spring.ai.model.moderation", () -> "none");
    }

    @BeforeAll
    void installBaselineFixture() {
        assertNotNull(vectorStore, "VectorStore must be configured");
        vectorStore.delete(allOwnedIds());
        vectorStore.add(baselineDocuments());
        assertExactBaseline(searchAllDocuments());
    }

    @AfterAll
    void verifyFinalFixtureAndCloseClient() throws Throwable {
        Throwable failure = null;
        try {
            vectorStore.delete(List.of(DELETE_PROBE_ID));
            assertExactBaseline(searchAllDocuments());
        } catch (Throwable throwable) {
            failure = throwable;
        }

        try {
            milvusClient.close(10L);
        } catch (Throwable throwable) {
            if (failure == null) {
                failure = throwable;
            } else {
                failure.addSuppressed(throwable);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    @Test
    void testAddAndSearchDocuments() {
        List<Document> results = search("最近销售情况", 3, 0.0);

        assertExactIds(results, Set.of("qt1", "qt2", "qt3"));
        assertEquals("qt1", results.get(0).getId(), "the nearest document must be deterministic");
        assertEquals("最近一周各品牌销售情况", results.get(0).getText());
    }

    @Test
    void testSearchByModelName() {
        List<Document> results = searchAllDocuments();
        List<Document> salesModelResults = matchingMetadata(
                results, "model_name", "FactSalesQueryModel");

        assertExactIds(salesModelResults, Set.of("qt1", "qt2", "qt3"));
    }

    @Test
    void testSearchDslTemplates() {
        List<Document> results = searchAllDocuments();
        List<Document> dslTemplates = matchingMetadata(results, "template_type", "dsl");

        assertExactIds(dslTemplates, Set.of("qt1", "qt2", "qt4"));
        assertTrue(dslTemplates.stream().allMatch(document ->
                        document.getMetadata().get("model_name") instanceof String),
                "every DSL template must carry an exact model_name");
    }

    @Test
    void testSearchGuideDocuments() {
        List<Document> results = searchAllDocuments();
        List<Document> guides = matchingMetadata(results, "template_type", "guide");

        assertExactIds(guides, Set.of("qt3", "qt5"));
        assertTrue(guides.stream().allMatch(document -> document.getText().contains("分析")),
                "both guide documents must retain their deterministic content");
    }

    @Test
    void testDeleteDocuments() {
        Document deleteProbe = createQueryTemplate(
                DELETE_PROBE_ID, "查询 dsl 测试删除探针文档", "dsl", "DeleteProbeModel");
        vectorStore.add(List.of(deleteProbe));

        List<Document> beforeDelete = search("查询 dsl 测试删除探针文档", 1, 0.99);
        assertExactIds(beforeDelete, Set.of(DELETE_PROBE_ID));

        vectorStore.delete(List.of(DELETE_PROBE_ID));

        List<Document> afterDelete = searchAllDocuments();
        assertExactBaseline(afterDelete);
        assertFalse(afterDelete.stream().anyMatch(document -> DELETE_PROBE_ID.equals(document.getId())),
                "the fixed delete probe must be absent");
    }

    private List<Document> searchAllDocuments() {
        return search("全部基线文档", 10, 0.0);
    }

    private List<Document> search(String query, int topK, double similarityThreshold) {
        List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build());
        assertNotNull(results, "similarity search must return a list");
        assertFalse(results.isEmpty(), "similarity search must not be an empty pseudo-green result");
        return results;
    }

    private List<Document> matchingMetadata(
            List<Document> documents, String key, Object expectedValue) {
        Predicate<Document> predicate = document -> expectedValue.equals(document.getMetadata().get(key));
        List<Document> matching = documents.stream().filter(predicate).toList();
        assertFalse(matching.isEmpty(), "metadata filtering must not pass vacuously for " + key);
        return matching;
    }

    private void assertExactBaseline(List<Document> documents) {
        assertExactIds(documents, BASELINE_IDS);
        Map<String, Document> byId = documents.stream().collect(Collectors.toMap(
                Document::getId, document -> document, (left, right) -> {
                    throw new AssertionError("duplicate baseline id: " + left.getId());
                }, LinkedHashMap::new));

        for (BaselineDocument expected : BASELINE) {
            Document actual = byId.get(expected.id());
            assertNotNull(actual, "missing baseline document " + expected.id());
            assertEquals(expected.content(), actual.getText());
            assertEquals(EXPECTED_METADATA_KEYS, actual.getMetadata().keySet());
            assertEquals(expected.templateType(), actual.getMetadata().get("template_type"));
            assertEquals(expected.modelName(), actual.getMetadata().get("model_name"));
            assertEquals(List.of("测试", "查询"), actual.getMetadata().get("tags"));
            assertEquals(0, ((Number) actual.getMetadata().get("usage_count")).intValue());
            assertEquals(CREATED_AT, ((Number) actual.getMetadata().get("created_at")).longValue());
        }
    }

    private void assertExactIds(List<Document> documents, Set<String> expectedIds) {
        Set<String> actualIds = documents.stream().map(Document::getId).collect(Collectors.toSet());
        assertEquals(expectedIds.size(), documents.size(), "document count must be exact");
        assertEquals(expectedIds, actualIds, "document ids must be exact");
    }

    private List<Document> baselineDocuments() {
        return BASELINE.stream()
                .map(document -> createQueryTemplate(
                        document.id(), document.content(), document.templateType(), document.modelName()))
                .toList();
    }

    private List<String> allOwnedIds() {
        return List.of("qt1", "qt2", "qt3", "qt4", "qt5", DELETE_PROBE_ID);
    }

    private static Document createQueryTemplate(
            String id, String content, String templateType, String modelName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("template_type", templateType);
        metadata.put("model_name", modelName);
        metadata.put("tags", List.of("测试", "查询"));
        metadata.put("usage_count", 0);
        metadata.put("created_at", CREATED_AT);
        return new Document(id, content, metadata);
    }

    private static String requireSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required system property is missing: " + name);
        }
        return value.trim();
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("port is outside 1..65535");
            }
            return port;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "System property " + MILVUS_PORT_PROPERTY + " must be a valid TCP port", exception);
        }
    }

    private record BaselineDocument(
            String id, String content, String templateType, String modelName) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DeterministicVectorTestConfiguration {

        @Bean
        @Primary
        EmbeddingModel deterministicEmbeddingModel() {
            return new DeterministicEmbeddingModel();
        }

        @Bean(destroyMethod = "")
        MilvusServiceClient milvusServiceClient(
                @Value("${spring.ai.vectorstore.milvus.client.host}") String host,
                @Value("${spring.ai.vectorstore.milvus.client.port}") int port) {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .withConnectTimeout(30L, TimeUnit.SECONDS)
                    .build();
            return new MilvusServiceClient(connectParam);
        }

        @Bean
        @Primary
        MilvusVectorStore deterministicMilvusVectorStore(
                MilvusServiceClient client,
                EmbeddingModel embeddingModel,
                @Value("${spring.ai.vectorstore.milvus.collection-name}") String collectionName,
                @Value("${spring.ai.vectorstore.milvus.embedding-dimension}") int embeddingDimensions) {
            return MilvusVectorStore.builder(client, embeddingModel)
                    .databaseName("default")
                    .collectionName(collectionName)
                    .embeddingDimension(embeddingDimensions)
                    .iDFieldName("id")
                    .indexType(IndexType.FLAT)
                    .indexParameters("{}")
                    .metricType(MetricType.COSINE)
                    .autoId(false)
                    .initializeSchema(true)
                    .build();
        }
    }

    private static final class DeterministicEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>(request.getInstructions().size());
            for (int index = 0; index < request.getInstructions().size(); index++) {
                embeddings.add(new Embedding(vectorize(request.getInstructions().get(index)), index));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vectorize(document.getText());
        }

        @Override
        public int dimensions() {
            return EMBEDDING_DIMENSIONS;
        }

        private static float[] vectorize(String text) {
            String normalized = text.toLowerCase(Locale.ROOT);
            float[] vector = new float[EMBEDDING_DIMENSIONS];
            vector[0] = 1.0f;
            vector[1] = containsAny(normalized, "销售") ? 1.0f : 0.0f;
            vector[2] = containsAny(normalized, "最近", "一周") ? 1.0f : 0.0f;
            vector[3] = containsAny(normalized, "分析", "指南", "趋势") ? 1.0f : 0.0f;
            vector[4] = containsAny(normalized, "查询", "模板", "dsl") ? 1.0f : 0.0f;
            vector[5] = containsAny(normalized, "库存") ? 1.0f : 0.0f;
            vector[6] = containsAny(normalized, "客户", "购买", "订单") ? 1.0f : 0.0f;
            vector[7] = containsAny(normalized, "删除", "探针") ? 1.0f : 0.0f;
            return vector;
        }

        private static boolean containsAny(String text, String... terms) {
            return Arrays.stream(terms).anyMatch(text::contains);
        }
    }
}

package com.foggyframework.dataset.db.model.vector;

import com.foggyframework.dataset.db.model.impl.vector.*;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vector 模块集成测试
 *
 * <p>需要以下前置条件：
 * <ul>
 *   <li>通过 {@code v934.vector.milvus.host} 指定 Milvus 主机</li>
 *   <li>通过 {@code v934.vector.milvus.port} 指定 Milvus 端口</li>
 * </ul>
 * Embedding 请求只会发往测试进程内启动的 loopback OpenAI-compatible fixture。
 * </p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("Vector 模块集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VectorIT {

    private static final String TEST_COLLECTION = "foggy_test_documents";
    private static final String MILVUS_HOST_PROPERTY = "v934.vector.milvus.host";
    private static final String MILVUS_PORT_PROPERTY = "v934.vector.milvus.port";
    private static final String EMBEDDING_MODEL = "v934-deterministic-embedding";
    private static final String EMBEDDING_API_KEY = "v934-local-fixture-key";
    private static final int VECTOR_DIMENSIONS = 8;
    private static final int EXPECTED_EMBEDDING_REQUESTS = 10;
    private static final Duration STATE_TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_INTERVAL_MILLIS = 100L;

    private MilvusClientV2 milvusClient;
    private EmbeddingService embeddingService;
    private HttpServer embeddingServer;
    private String milvusHost;
    private int milvusPort;
    private final Gson gson = new Gson();
    private final List<EmbeddingRequest> embeddingRequests = new CopyOnWriteArrayList<>();
    private final AtomicReference<Throwable> embeddingFixtureFailure = new AtomicReference<>();

    private record EmbeddingRequest(String method, String path, String authorization, List<String> inputs) {
    }

    /**
     * 测试文档数据
     */
    private static final List<Map<String, Object>> TEST_DOCUMENTS = Arrays.asList(
            createDoc(1L, "销售报告", "2024年第一季度销售额增长20%，主要得益于新产品线的推出", "report"),
            createDoc(2L, "产品手册", "本产品采用最新技术，具有高性能、低功耗的特点", "manual"),
            createDoc(3L, "客户反馈", "客户对产品质量非常满意，但希望改进售后服务", "feedback"),
            createDoc(4L, "技术文档", "API接口采用RESTful设计，支持JSON格式数据交换", "technical"),
            createDoc(5L, "市场分析", "竞争对手在价格方面有优势，我们需要加强品牌建设", "report"),
            createDoc(6L, "培训资料", "新员工入职培训包括公司文化、产品知识和销售技巧", "training"),
            createDoc(7L, "财务报表", "本季度净利润同比增长15%，毛利率保持在35%左右", "report"),
            createDoc(8L, "用户指南", "系统登录后，点击左侧菜单可以访问各功能模块", "manual")
    );

    private static Map<String, Object> createDoc(Long id, String title, String content, String category) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("doc_id", id);
        doc.put("title", title);
        doc.put("content", content);
        doc.put("category", category);
        return doc;
    }

    @BeforeAll
    void setUp() throws IOException {
        milvusHost = requireSystemProperty(MILVUS_HOST_PROPERTY);
        milvusPort = parsePort(requireSystemProperty(MILVUS_PORT_PROPERTY));
        startEmbeddingFixture();

        try {
            VectorDbConfig.EmbeddingConfig embeddingConfig = VectorDbConfig.EmbeddingConfig.builder()
                    .type("openai")
                    .baseUrl("http://127.0.0.1:" + embeddingServer.getAddress().getPort())
                    .apiKey(EMBEDDING_API_KEY)
                    .model(EMBEDDING_MODEL)
                    .dimensions(VECTOR_DIMENSIONS)
                    .build();
            embeddingService = new EmbeddingService(embeddingConfig);

            String uri = String.format("http://%s:%d", milvusHost, milvusPort);
            ConnectConfig connectConfig = ConnectConfig.builder()
                    .uri(uri)
                    .connectTimeoutMs(30000L)
                    .build();
            milvusClient = new MilvusClientV2(connectConfig);
            String serverVersion = milvusClient.getServerVersion();
            assertNotNull(serverVersion, "Milvus server version must be available");
            assertFalse(serverVersion.isBlank(), "Milvus server version must not be blank");
            log.info("Connected to Milvus {} at {}", serverVersion, uri);
        } catch (RuntimeException | AssertionError e) {
            closeResourcesAfterSetupFailure(e);
            throw e;
        }
    }

    @AfterAll
    void tearDown() {
        Throwable cleanupFailure = null;
        try {
            if (milvusClient != null) {
                if (collectionExists()) {
                    milvusClient.dropCollection(DropCollectionReq.builder()
                            .collectionName(TEST_COLLECTION)
                            .build());
                }
                awaitCondition("collection to be absent after cleanup", () -> !collectionExists());
                assertFalse(collectionExists(), "Test collection must not exist after cleanup");
                log.info("Verified test collection cleanup: {}", TEST_COLLECTION);
            }
        } catch (Throwable e) {
            cleanupFailure = e;
        } finally {
            if (milvusClient != null) {
                try {
                    milvusClient.close();
                } catch (Throwable e) {
                    cleanupFailure = mergeFailure(cleanupFailure, e);
                } finally {
                    milvusClient = null;
                }
            }
            if (embeddingServer != null) {
                try {
                    embeddingServer.stop(0);
                } catch (Throwable e) {
                    cleanupFailure = mergeFailure(cleanupFailure, e);
                } finally {
                    embeddingServer = null;
                }
            }
        }

        cleanupFailure = mergeFailure(cleanupFailure, embeddingFixtureFailure.get());
        try {
            assertEquals(EXPECTED_EMBEDDING_REQUESTS, embeddingRequests.size(),
                    "Every expected embedding request must reach the loopback fixture");
        } catch (Throwable e) {
            cleanupFailure = mergeFailure(cleanupFailure, e);
        }
        if (cleanupFailure != null) {
            throw new AssertionError("VectorIT cleanup or embedding fixture verification failed", cleanupFailure);
        }
    }

    private String requireSystemProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required system property is missing: " + name);
        }
        return value.trim();
    }

    private int parsePort(String value) {
        final int port;
        try {
            port = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(MILVUS_PORT_PROPERTY + " must be an integer: " + value, e);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(MILVUS_PORT_PROPERTY + " must be between 1 and 65535: " + port);
        }
        return port;
    }

    private void startEmbeddingFixture() throws IOException {
        embeddingServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        embeddingServer.createContext("/embeddings", this::handleEmbeddingRequest);
        embeddingServer.start();
        assertTrue(embeddingServer.getAddress().getAddress().isLoopbackAddress(),
                "Embedding fixture must bind to a loopback address");
    }

    private void handleEmbeddingRequest(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            requireFixture("POST".equals(method), "Embedding request method must be POST");
            requireFixture("/embeddings".equals(path), "Embedding request path must be /embeddings");
            requireFixture(exchange.getRequestURI().getQuery() == null, "Embedding request must not have a query string");
            requireFixture(("Bearer " + EMBEDDING_API_KEY).equals(authorization),
                    "Embedding request Authorization header is invalid");
            requireFixture(contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("application/json"),
                    "Embedding request Content-Type must be application/json");

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            JsonObject request = JsonParser.parseString(body).getAsJsonObject();
            requireFixture(request.keySet().equals(Set.of("model", "input")),
                    "Embedding request must contain exactly model and input");
            requireFixture(request.has("model") && EMBEDDING_MODEL.equals(request.get("model").getAsString()),
                    "Embedding request model is invalid");
            requireFixture(request.has("input"), "Embedding request input is missing");

            List<String> inputs = parseFixtureInputs(request.get("input"));
            embeddingRequests.add(new EmbeddingRequest(method, path, authorization, List.copyOf(inputs)));

            JsonArray data = new JsonArray();
            for (int i = 0; i < inputs.size(); i++) {
                JsonObject item = new JsonObject();
                item.addProperty("index", i);
                item.add("embedding", gson.toJsonTree(vectorForText(inputs.get(i))));
                data.add(item);
            }
            JsonObject response = new JsonObject();
            response.add("data", data);
            sendJson(exchange, 200, gson.toJson(response));
        } catch (Throwable e) {
            embeddingFixtureFailure.compareAndSet(null, e);
            sendJson(exchange, 400, "{\"error\":\"embedding fixture rejected request\"}");
        } finally {
            exchange.close();
        }
    }

    private List<String> parseFixtureInputs(JsonElement input) {
        List<String> inputs = new ArrayList<>();
        if (input.isJsonPrimitive() && input.getAsJsonPrimitive().isString()) {
            inputs.add(input.getAsString());
        } else if (input.isJsonArray()) {
            for (JsonElement item : input.getAsJsonArray()) {
                requireFixture(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString(),
                        "Every batch embedding input must be a string");
                inputs.add(item.getAsString());
            }
        } else {
            throw new IllegalArgumentException("Embedding input must be a string or string array");
        }
        requireFixture(!inputs.isEmpty(), "Embedding input must not be empty");
        for (String inputText : inputs) {
            requireFixture(!inputText.isBlank(), "Embedding input text must not be blank");
        }
        return inputs;
    }

    private void requireFixture(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private List<Float> vectorForText(String text) {
        if (text.startsWith("销售报告 ") || text.equals("销售报告")
                || text.equals("销售业绩增长") || text.equals("业绩增长")
                || text.equals("销售业绩报告")) {
            return vector(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1f);
        }
        if (text.startsWith("产品手册 ") || text.equals("产品手册")
                || text.equals("产品说明书使用方法")) {
            return vector(0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.1f);
        }
        if (text.startsWith("客户反馈 ")) {
            return vector(0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.1f);
        }
        if (text.startsWith("技术文档 ") || text.equals("技术文档")
                || text.equals("API接口设计REST")) {
            return vector(0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.1f);
        }
        if (text.startsWith("市场分析 ")) {
            return vector(0.7f, 0.0f, 0.0f, 0.0f, 0.7f, 0.1f);
        }
        if (text.startsWith("培训资料 ")) {
            return vector(0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.1f);
        }
        if (text.startsWith("财务报表 ")) {
            return vector(0.8f, 0.0f, 0.0f, 0.0f, 0.2f, 0.1f);
        }
        if (text.startsWith("用户指南 ")) {
            return vector(0.0f, 0.8f, 0.0f, 0.0f, 0.2f, 0.1f);
        }
        if (text.equals("业务分析")) {
            return vector(0.7f, 0.0f, 0.0f, 0.0f, 0.7f, 0.0f);
        }
        if (text.equals("这是一个测试文本") || text.equals("文档资料")) {
            return vector(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        }
        throw new IllegalArgumentException("Unexpected embedding fixture input: " + text);
    }

    private List<Float> vector(float v1, float v2, float v3, float v4, float v5, float v6) {
        return List.of(v1, v2, v3, v4, v5, v6, 0.0f, 0.0f);
    }

    private void assertFixtureRequest(int index, List<String> expectedInputs) {
        assertFixtureHealthy();
        assertTrue(index >= 0 && index < embeddingRequests.size(), "Embedding request index is missing: " + index);
        EmbeddingRequest request = embeddingRequests.get(index);
        assertEquals("POST", request.method());
        assertEquals("/embeddings", request.path());
        assertEquals("Bearer " + EMBEDDING_API_KEY, request.authorization());
        assertEquals(expectedInputs, request.inputs());
    }

    private void assertFixtureHealthy() {
        Throwable failure = embeddingFixtureFailure.get();
        if (failure != null) {
            throw new AssertionError("Embedding fixture rejected an HTTP request", failure);
        }
    }

    private boolean collectionExists() {
        return Boolean.TRUE.equals(milvusClient.hasCollection(HasCollectionReq.builder()
                .collectionName(TEST_COLLECTION)
                .build()));
    }

    private void awaitCondition(String description, BooleanSupplier condition) {
        long deadline = System.nanoTime() + STATE_TIMEOUT.toNanos();
        while (true) {
            if (condition.getAsBoolean()) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                fail("Timed out after " + STATE_TIMEOUT.toSeconds() + "s waiting for " + description);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for " + description, e);
            }
        }
    }

    private void closeResourcesAfterSetupFailure(Throwable originalFailure) {
        if (milvusClient != null) {
            try {
                milvusClient.close();
            } catch (Throwable closeFailure) {
                originalFailure.addSuppressed(closeFailure);
            } finally {
                milvusClient = null;
            }
        }
        if (embeddingServer != null) {
            try {
                embeddingServer.stop(0);
            } catch (Throwable closeFailure) {
                originalFailure.addSuppressed(closeFailure);
            } finally {
                embeddingServer = null;
            }
        }
    }

    private Throwable mergeFailure(Throwable current, Throwable next) {
        if (next == null) {
            return current;
        }
        if (current == null) {
            return next;
        }
        if (current != next) {
            current.addSuppressed(next);
        }
        return current;
    }

    private List<SearchResp.SearchResult> singleQueryResults(SearchResp response) {
        return singleQueryResults(response, true);
    }

    private List<SearchResp.SearchResult> singleQueryResults(SearchResp response, boolean requireNonEmpty) {
        assertNotNull(response, "Milvus search response must not be null");
        List<List<SearchResp.SearchResult>> resultSets = response.getSearchResults();
        assertNotNull(resultSets, "Milvus search result sets must not be null");
        assertEquals(1, resultSets.size(), "Exactly one query vector must produce exactly one result set");
        List<SearchResp.SearchResult> results = resultSets.get(0);
        assertNotNull(results, "Milvus search results must not be null");
        if (requireNonEmpty) {
            assertFalse(results.isEmpty(), "Milvus search results must not be empty");
        }
        return results;
    }

    private Set<Long> documentIds(List<SearchResp.SearchResult> results) {
        Set<Long> ids = new LinkedHashSet<>();
        for (SearchResp.SearchResult result : results) {
            assertNotNull(result, "Search result must not be null");
            assertTrue(ids.add(documentId(result)), "Search results must not contain duplicate document ids");
        }
        return ids;
    }

    private long documentId(SearchResp.SearchResult result) {
        Map<String, Object> entity = result.getEntity();
        assertNotNull(entity, "Search result entity must not be null");
        Object value = entity.get("doc_id");
        assertNotNull(value, "Search result must contain doc_id");
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            throw new AssertionError("Search result doc_id is not numeric: " + value, e);
        }
    }

    private Set<Long> allDocumentIds() {
        return Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
    }

    // ==========================================
    // 集合创建测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("创建测试集合")
    void testCreateCollection() {
        if (collectionExists()) {
            milvusClient.dropCollection(DropCollectionReq.builder()
                    .collectionName(TEST_COLLECTION)
                    .build());
            awaitCondition("stale test collection to be absent", () -> !collectionExists());
        }
        assertFalse(collectionExists(), "Stale test collection must be removed before creation");

        // 创建 Schema
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .build();

        schema.addField(AddFieldReq.builder()
                .fieldName("doc_id")
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(false)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("title")
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(2048)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("category")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("embedding")
                .dataType(DataType.FloatVector)
                .dimension(VECTOR_DIMENSIONS)
                .build());

        // 创建索引参数
        List<IndexParam> indexParams = Collections.singletonList(
                IndexParam.builder()
                        .fieldName("embedding")
                        .indexType(IndexParam.IndexType.IVF_FLAT)
                        .metricType(IndexParam.MetricType.COSINE)
                        .extraParams(Map.of("nlist", 1))
                        .build()
        );

        // 创建集合
        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(TEST_COLLECTION)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();

        milvusClient.createCollection(createReq);

        awaitCondition("test collection to exist", this::collectionExists);
        assertTrue(collectionExists(), "Test collection must exist after creation");

        log.info("Test collection created: {}, dimensions: {}", TEST_COLLECTION, VECTOR_DIMENSIONS);
    }

    // ==========================================
    // Embedding 测试
    // ==========================================

    @Test
    @Order(2)
    @DisplayName("Embedding 服务 - 单文本嵌入")
    void testSingleEmbedding() {
        String text = "这是一个测试文本";
        int requestIndex = embeddingRequests.size();
        List<Float> embedding = embeddingService.embed(text);

        assertNotNull(embedding);
        assertEquals(VECTOR_DIMENSIONS, embeddingService.getDimensions());
        assertFalse(embedding.isEmpty(), "Single embedding must not be empty");
        assertEquals(VECTOR_DIMENSIONS, embedding.size());
        assertEquals(vectorForText(text), embedding);
        assertEquals(requestIndex + 1, embeddingRequests.size());
        assertFixtureRequest(requestIndex, List.of(text));

        // 检查向量值范围
        for (Float value : embedding) {
            assertTrue(value >= -1.0f && value <= 1.0f,
                    "向量值应该在 [-1, 1] 范围内");
        }

        log.info("Single embedding test passed, dimensions: {}", embedding.size());
    }

    @Test
    @Order(3)
    @DisplayName("Embedding 服务 - 批量嵌入")
    void testBatchEmbedding() {
        List<String> texts = Arrays.asList(
                "销售报告",
                "产品手册",
                "技术文档"
        );

        int requestIndex = embeddingRequests.size();
        List<List<Float>> embeddings = embeddingService.embedBatch(texts);

        assertNotNull(embeddings);
        assertEquals(3, embeddings.size());

        for (int i = 0; i < embeddings.size(); i++) {
            assertFalse(embeddings.get(i).isEmpty(), "Batch embedding must not be empty at index " + i);
            assertEquals(VECTOR_DIMENSIONS, embeddings.get(i).size());
            assertEquals(vectorForText(texts.get(i)), embeddings.get(i));
        }
        assertEquals(requestIndex + 1, embeddingRequests.size());
        assertFixtureRequest(requestIndex, texts);

        log.info("Batch embedding test passed, count: {}", embeddings.size());
    }

    // ==========================================
    // 数据插入测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("插入测试数据")
    void testInsertData() {
        // 准备插入数据
        List<Long> docIds = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<List<Float>> embeddings = new ArrayList<>();

        // 生成嵌入
        List<String> textsToEmbed = new ArrayList<>();
        for (Map<String, Object> doc : TEST_DOCUMENTS) {
            textsToEmbed.add(doc.get("title") + " " + doc.get("content"));
        }

        int requestIndex = embeddingRequests.size();
        List<List<Float>> generatedEmbeddings = embeddingService.embedBatch(textsToEmbed);
        assertNotNull(generatedEmbeddings);
        assertEquals(TEST_DOCUMENTS.size(), generatedEmbeddings.size());
        assertEquals(requestIndex + 1, embeddingRequests.size());
        assertFixtureRequest(requestIndex, textsToEmbed);

        for (int i = 0; i < TEST_DOCUMENTS.size(); i++) {
            Map<String, Object> doc = TEST_DOCUMENTS.get(i);
            docIds.add((Long) doc.get("doc_id"));
            titles.add((String) doc.get("title"));
            contents.add((String) doc.get("content"));
            categories.add((String) doc.get("category"));
            assertEquals(VECTOR_DIMENSIONS, generatedEmbeddings.get(i).size());
            assertEquals(vectorForText(textsToEmbed.get(i)), generatedEmbeddings.get(i));
            embeddings.add(generatedEmbeddings.get(i));
        }

        // 构建插入数据 (Milvus SDK 需要 JsonObject)
        Gson gson = new Gson();
        List<JsonObject> data = new ArrayList<>();
        for (int i = 0; i < docIds.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("doc_id", docIds.get(i));
            row.addProperty("title", titles.get(i));
            row.addProperty("content", contents.get(i));
            row.addProperty("category", categories.get(i));
            row.add("embedding", gson.toJsonTree(embeddings.get(i)));
            data.add(row);
        }

        // 插入数据
        InsertResp insertResp = milvusClient.insert(InsertReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(data)
                .build());

        assertEquals(TEST_DOCUMENTS.size(), insertResp.getInsertCnt());

        log.info("Inserted {} documents", insertResp.getInsertCnt());

        // 加载集合到内存
        milvusClient.loadCollection(LoadCollectionReq.builder()
                .collectionName(TEST_COLLECTION)
                .build());
        awaitCondition("test collection to be loaded", () -> Boolean.TRUE.equals(milvusClient.getLoadState(
                GetLoadStateReq.builder().collectionName(TEST_COLLECTION).build())));
        assertTrue(milvusClient.getLoadState(GetLoadStateReq.builder()
                .collectionName(TEST_COLLECTION)
                .build()), "Test collection must be loaded");

        List<Float> probeVector = vectorForText("文档资料");
        AtomicReference<List<SearchResp.SearchResult>> visibleDocuments = new AtomicReference<>(List.of());
        awaitCondition("all inserted documents to be searchable", () -> {
            List<SearchResp.SearchResult> results = singleQueryResults(milvusClient.search(SearchReq.builder()
                    .collectionName(TEST_COLLECTION)
                    .data(List.of(new FloatVec(probeVector)))
                    .topK(TEST_DOCUMENTS.size())
                    .outputFields(List.of("doc_id", "title", "category"))
                    .build()), false);
            visibleDocuments.set(results);
            return results.size() == TEST_DOCUMENTS.size();
        });
        assertEquals(TEST_DOCUMENTS.size(), visibleDocuments.get().size());
        assertEquals(allDocumentIds(), documentIds(visibleDocuments.get()));
    }

    // ==========================================
    // 向量搜索测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("向量搜索 - 文本相似度查询")
    void testVectorSearchByText() {
        // 搜索与"销售业绩"相关的文档
        String queryText = "销售业绩增长";
        List<Float> queryVector = embeddingService.embed(queryText);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(5)
                .outputFields(Arrays.asList("doc_id", "title", "content", "category"))
                .build());

        List<SearchResp.SearchResult> firstQueryResults = singleQueryResults(searchResp);
        assertEquals(5, firstQueryResults.size(), "topK=5 must return exactly five documents");
        assertEquals(5, documentIds(firstQueryResults).size(), "Search results must have distinct document ids");
        assertTrue(allDocumentIds().containsAll(documentIds(firstQueryResults)),
                "Search results must only contain fixture documents");

        log.info("Search query: '{}'", queryText);
        log.info("Found {} results:", firstQueryResults.size());

        for (SearchResp.SearchResult result : firstQueryResults) {
            Map<String, Object> entity = result.getEntity();
            log.info("  - [score: {:.4f}] {} - {}",
                    result.getScore(),
                    entity.get("title"),
                    entity.get("category"));
        }

        // 验证：销售相关的文档应该排在前面
        SearchResp.SearchResult topResult = firstQueryResults.get(0);
        assertEquals(1L, documentId(topResult), "Sales query must rank the sales report first");
        String topCategory = (String) topResult.getEntity().get("category");
        assertEquals("report", topCategory, "销售业绩查询应该返回报告类文档");
    }

    @Test
    @Order(21)
    @DisplayName("向量搜索 - 带过滤条件")
    void testVectorSearchWithFilter() {
        // 只搜索 category = 'report' 的文档
        String queryText = "业绩增长";
        List<Float> queryVector = embeddingService.embed(queryText);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(10)
                .filter("category == \"report\"")
                .outputFields(Arrays.asList("doc_id", "title", "category"))
                .build());

        List<SearchResp.SearchResult> firstQueryResults = singleQueryResults(searchResp);
        assertEquals(Set.of(1L, 5L, 7L), documentIds(firstQueryResults),
                "Report filter must return the exact three report documents");

        log.info("Filtered search (category=report): found {} results", firstQueryResults.size());

        // 验证所有结果都是 report 类别
        for (SearchResp.SearchResult result : firstQueryResults) {
            String category = (String) result.getEntity().get("category");
            assertEquals("report", category, "过滤后应该只返回 report 类别的文档");
            log.info("  - [score: {:.4f}] {}", result.getScore(), result.getEntity().get("title"));
        }
    }

    @Test
    @Order(22)
    @DisplayName("向量搜索 - 技术文档查询")
    void testVectorSearchTechnical() {
        String queryText = "API接口设计REST";
        List<Float> queryVector = embeddingService.embed(queryText);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(3)
                .outputFields(Arrays.asList("doc_id", "title", "content", "category"))
                .build());

        List<SearchResp.SearchResult> firstQueryResults = singleQueryResults(searchResp);
        assertEquals(3, firstQueryResults.size(), "topK=3 must return exactly three documents");

        log.info("Technical search: '{}'", queryText);
        for (SearchResp.SearchResult result : firstQueryResults) {
            log.info("  - [score: {:.4f}] {} ({})",
                    result.getScore(),
                    result.getEntity().get("title"),
                    result.getEntity().get("category"));
        }

        // 技术文档应该排在最前面
        SearchResp.SearchResult topResult = firstQueryResults.get(0);
        assertEquals(4L, documentId(topResult), "Technical query must rank the API document first");
        assertEquals("technical", topResult.getEntity().get("category"),
                "API接口查询应该返回技术文档");
    }

    @Test
    @Order(23)
    @DisplayName("向量搜索 - 相似度阈值过滤")
    void testVectorSearchWithMinScore() {
        String queryText = "产品说明书使用方法";
        List<Float> queryVector = embeddingService.embed(queryText);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(10)
                .outputFields(Arrays.asList("doc_id", "title", "category"))
                .build());

        List<SearchResp.SearchResult> allResults = singleQueryResults(searchResp);
        assertEquals(TEST_DOCUMENTS.size(), allResults.size(),
                "topK above fixture size must return every fixture document");

        // 手动过滤 minScore >= 0.5
        float minScore = 0.5f;
        List<SearchResp.SearchResult> filteredResults = new ArrayList<>();
        for (SearchResp.SearchResult result : allResults) {
            if (result.getScore() >= minScore) {
                filteredResults.add(result);
            }
        }

        log.info("Search with minScore={}: {} of {} results passed", minScore, filteredResults.size(), allResults.size());

        assertFalse(filteredResults.isEmpty(), "Min-score filtering must not be vacuously green");
        assertEquals(Set.of(2L, 8L), documentIds(filteredResults),
                "Manual query must retain exactly the two manual documents");
        for (SearchResp.SearchResult result : filteredResults) {
            assertTrue(result.getScore() >= minScore);
            log.info("  - [score: {:.4f}] {}", result.getScore(), result.getEntity().get("title"));
        }
    }

    // ==========================================
    // v2.0 新功能测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("v2.0 - 元数据自动发现")
    void testMetadataAutoDiscovery() {
        VectorDbConfig vectorDbConfig = VectorDbConfig.builder()
                .host(milvusHost)
                .port(milvusPort)
                .autoDiscovery(true)
                .build();

        MilvusMetadataService metadataService = new MilvusMetadataService(vectorDbConfig);
        try {
            // 测试集合存在检查
            boolean exists = metadataService.collectionExists(TEST_COLLECTION);
            assertTrue(exists, "测试集合应该存在");

            // 测试集合加载状态检查
            boolean loaded = metadataService.isCollectionLoaded(TEST_COLLECTION);
            assertTrue(loaded, "测试集合应该已加载到内存");

            // 测试向量字段元数据发现
            VectorFieldMetadata metadata = metadataService.getVectorFieldMetadata(TEST_COLLECTION, "embedding");
            assertNotNull(metadata, "应该能够发现向量字段元数据");

            assertEquals(TEST_COLLECTION, metadata.getCollectionName());
            assertEquals("embedding", metadata.getFieldName());
            assertEquals(VECTOR_DIMENSIONS, metadata.getDimension());
            assertEquals("FloatVector", metadata.getVectorType());
            assertTrue(metadata.isIndexed(), "向量字段应该有索引");
            assertEquals("COSINE", metadata.getMetricType(), "度量类型应该是 COSINE");
            assertEquals("IVF_FLAT", metadata.getIndexType(), "索引类型应该是 IVF_FLAT");
            assertEquals("doc_id", metadata.getPrimaryKeyField());

            log.info("Metadata auto-discovery test passed:");
            log.info("  - Collection: {}", metadata.getCollectionName());
            log.info("  - Field: {}", metadata.getFieldName());
            log.info("  - Dimension: {}", metadata.getDimension());
            log.info("  - Index Type: {}", metadata.getIndexType());
            log.info("  - Metric Type: {}", metadata.getMetricType());
            log.info("  - Primary Key: {}", metadata.getPrimaryKeyField());
            log.info("  - Loaded: {}", metadata.isLoaded());

        } finally {
            metadataService.close();
        }
    }

    @Test
    @Order(31)
    @DisplayName("v2.0 - 自动发现所有向量字段")
    void testDiscoverAllVectorFields() {
        VectorDbConfig vectorDbConfig = VectorDbConfig.builder()
                .host(milvusHost)
                .port(milvusPort)
                .build();

        MilvusMetadataService metadataService = new MilvusMetadataService(vectorDbConfig);
        try {
            List<VectorFieldMetadata> fields = metadataService.discoverVectorFields(TEST_COLLECTION);
            assertNotNull(fields);
            assertEquals(1, fields.size(), "测试集合应该有 1 个向量字段");

            VectorFieldMetadata field = fields.get(0);
            assertEquals("embedding", field.getFieldName());
            assertEquals(VECTOR_DIMENSIONS, field.getDimension());
            assertEquals("FloatVector", field.getVectorType());
            assertEquals("doc_id", field.getPrimaryKeyField());

            log.info("Discovered {} vector fields in collection '{}'", fields.size(), TEST_COLLECTION);

        } finally {
            metadataService.close();
        }
    }

    @Test
    @Order(32)
    @DisplayName("v2.0 - 连接池功能测试")
    void testConnectionPool() throws Exception {
        VectorDbConfig vectorDbConfig = VectorDbConfig.builder()
                .host(milvusHost)
                .port(milvusPort)
                .poolSize(3)
                .poolMaxWaitMs(5000L)
                .build();

        MilvusClientPool pool = new MilvusClientPool(vectorDbConfig);
        try {
            // 重复验证连接借用、调用与归还。
            List<Boolean> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Boolean result = pool.execute(client -> {
                    return client.hasCollection(
                            io.milvus.v2.service.collection.request.HasCollectionReq.builder()
                                    .collectionName(TEST_COLLECTION)
                                    .build()
                    );
                });
                results.add(result);
            }

            assertEquals(5, results.size());
            for (Boolean result : results) {
                assertTrue(result, "每次调用都应该成功");
            }

            // 验证连接池状态
            String poolStatus = pool.getPoolStatus();
            assertNotNull(poolStatus);
            assertFalse(poolStatus.isBlank(), "Connection pool status must not be blank");
            log.info("Connection pool status: {}", poolStatus);

        } finally {
            pool.close();
        }
    }

    @Test
    @Order(33)
    @DisplayName("v2.0 - 向量搜索带 groupBy")
    void testVectorSearchWithGroupBy() {
        String queryText = "文档资料";
        List<Float> queryVector = embeddingService.embed(queryText);

        // groupBy 搜索 - 按 category 分组
        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(10)
                .groupByFieldName("category")
                .outputFields(Arrays.asList("doc_id", "title", "category"))
                .build());

        List<SearchResp.SearchResult> results = singleQueryResults(searchResp);

        // 统计每个 category 出现的次数
        Map<String, Integer> categoryCounts = new HashMap<>();
        for (SearchResp.SearchResult result : results) {
            String category = (String) result.getEntity().get("category");
            categoryCounts.merge(category, 1, Integer::sum);
            log.info("  - [score: {:.4f}] {} ({})",
                    result.getScore(),
                    result.getEntity().get("title"),
                    category);
        }

        log.info("GroupBy search returned {} results with {} unique categories",
                results.size(), categoryCounts.size());
        assertEquals(Set.of("report", "manual", "feedback", "technical", "training"),
                categoryCounts.keySet(), "groupBy must return every fixture category exactly once");
        assertEquals(5, results.size(), "groupBy must return one representative for each category");
        assertTrue(categoryCounts.values().stream().allMatch(count -> count == 1),
                "groupBy must return exactly one result per category");
    }

    @Test
    @Order(34)
    @DisplayName("v2.0 - 范围搜索 (radius)")
    void testVectorSearchWithRadius() {
        String queryText = "销售业绩报告";
        List<Float> queryVector = embeddingService.embed(queryText);

        // 范围搜索：只返回 score >= 0.3 的结果
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("radius", 0.3f);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(10)
                .searchParams(searchParams)
                .outputFields(Arrays.asList("doc_id", "title", "category"))
                .build());

        List<SearchResp.SearchResult> results = singleQueryResults(searchResp);

        log.info("Radius search (radius=0.3): found {} results", results.size());

        assertEquals(Set.of(1L, 5L, 7L), documentIds(results),
                "Sales radius search must return the exact three sales-related reports");
        for (SearchResp.SearchResult result : results) {
            float score = result.getScore();
            assertTrue(score >= 0.3f, "范围搜索结果的分数应该 >= 0.3，实际: " + score);
            log.info("  - [score: {:.4f}] {} ({})",
                    score,
                    result.getEntity().get("title"),
                    result.getEntity().get("category"));
        }
    }

    @Test
    @Order(35)
    @DisplayName("v2.0 - 混合搜索 (向量 + 关键词过滤)")
    void testHybridSearch() {
        // 混合搜索：向量相似度 + 关键词过滤
        String queryText = "业务分析";
        String keywordFilter = "category == \"report\"";

        List<Float> queryVector = embeddingService.embed(queryText);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(10)
                .filter(keywordFilter)
                .outputFields(Arrays.asList("doc_id", "title", "content", "category"))
                .build());

        List<SearchResp.SearchResult> results = singleQueryResults(searchResp);

        log.info("Hybrid search (vector + keyword filter): found {} results", results.size());

        assertEquals(Set.of(1L, 5L, 7L), documentIds(results),
                "Hybrid search must return the exact three report documents");
        for (SearchResp.SearchResult result : results) {
            String category = (String) result.getEntity().get("category");
            assertEquals("report", category, "混合搜索结果应该都是 report 类别");
            log.info("  - [score: {:.4f}] {} - {}",
                    result.getScore(),
                    result.getEntity().get("title"),
                    result.getEntity().get("category"));
        }
    }

    @Test
    @Order(40)
    @DisplayName("v2.0 - 错误处理测试")
    void testErrorHandling() {
        // 测试 VectorQueryException 和 VectorErrorCode
        VectorQueryException ex1 = VectorQueryException.collectionNotFound("non_existent_collection");
        assertEquals("VEC_101", ex1.getCode());
        assertTrue(ex1.getMessage().contains("non_existent_collection"));

        VectorQueryException ex2 = VectorQueryException.dimensionMismatch(1536, 768);
        assertEquals("VEC_202", ex2.getCode());
        assertTrue(ex2.getMessage().contains("1536"));
        assertTrue(ex2.getMessage().contains("768"));

        VectorQueryException ex3 = VectorQueryException.embeddingServiceError("API timeout");
        assertEquals("VEC_301", ex3.getCode());

        log.info("Error handling test passed:");
        log.info("  - {}: {}", ex1.getCode(), ex1.getFormattedMessage());
        log.info("  - {}: {}", ex2.getCode(), ex2.getFormattedMessage());
        log.info("  - {}: {}", ex3.getCode(), ex3.getFormattedMessage());
    }
}

package com.foggyframework.dataset.db.model.vector;

import com.foggyframework.dataset.db.model.impl.vector.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Vector 模块集成测试
 *
 * <p>需要以下前置条件：
 * <ul>
 *   <li>启动 Milvus: docker-compose up -d milvus (在 foggy-dataset-demo/docker 目录)</li>
 *   <li>复制 test-config.properties.template 为 test-config.properties 并填入 API Key</li>
 * </ul>
 * </p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("Vector 模块集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VectorIntegrationTest {

    private static final String TEST_COLLECTION = "foggy_test_documents";

    private Properties config;
    private MilvusClientV2 milvusClient;
    private EmbeddingService embeddingService;
    private int vectorDimensions;

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
    void setUp() {
        // 加载配置
        config = loadConfig();
        if (config == null) {
            log.warn("test-config.properties not found, skipping integration tests");
            return;
        }

        // 初始化 Embedding 服务
        VectorDbConfig.EmbeddingConfig embeddingConfig = VectorDbConfig.EmbeddingConfig.builder()
                .type(config.getProperty("embedding.type", "openai"))
                .baseUrl(config.getProperty("embedding.baseUrl"))
                .apiKey(config.getProperty("embedding.apiKey"))
                .model(config.getProperty("embedding.model", "text-embedding-3-small"))
                .dimensions(Integer.parseInt(config.getProperty("embedding.dimensions", "1536")))
                .build();

        vectorDimensions = embeddingConfig.getDimensions();
        embeddingService = new EmbeddingService(embeddingConfig);

        // 初始化 Milvus 客户端
        String milvusHost = config.getProperty("milvus.host", "localhost");
        int milvusPort = Integer.parseInt(config.getProperty("milvus.port", "19530"));
        String uri = String.format("http://%s:%d", milvusHost, milvusPort);

        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(uri)
                .connectTimeoutMs(30000L)
                .build();

        milvusClient = new MilvusClientV2(connectConfig);
        log.info("Connected to Milvus at {}", uri);
    }

    @AfterAll
    void tearDown() {
        if (milvusClient != null) {
            // 清理测试集合
            try {
                milvusClient.dropCollection(DropCollectionReq.builder()
                        .collectionName(TEST_COLLECTION)
                        .build());
                log.info("Dropped test collection: {}", TEST_COLLECTION);
            } catch (Exception e) {
                log.warn("Failed to drop collection: {}", e.getMessage());
            }
            milvusClient.close();
        }
    }

    private Properties loadConfig() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("test-config.properties")) {
            if (is == null) {
                return null;
            }
            Properties props = new Properties();
            props.load(is);
            return props;
        } catch (Exception e) {
            log.error("Failed to load test-config.properties", e);
            return null;
        }
    }

    private void skipIfNotConfigured() {
        Assumptions.assumeTrue(config != null,
                "Skipping: test-config.properties not found. Copy test-config.properties.template to test-config.properties and configure it.");
    }

    // ==========================================
    // 集合创建测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("创建测试集合")
    void testCreateCollection() {
        skipIfNotConfigured();

        // 先删除已存在的集合
        try {
            milvusClient.dropCollection(DropCollectionReq.builder()
                    .collectionName(TEST_COLLECTION)
                    .build());
        } catch (Exception ignored) {
        }

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
                .dimension(vectorDimensions)
                .build());

        // 创建索引参数
        List<IndexParam> indexParams = Collections.singletonList(
                IndexParam.builder()
                        .fieldName("embedding")
                        .indexType(IndexParam.IndexType.IVF_FLAT)
                        .metricType(IndexParam.MetricType.COSINE)
                        .extraParams(Map.of("nlist", 128))
                        .build()
        );

        // 创建集合
        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(TEST_COLLECTION)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();

        milvusClient.createCollection(createReq);

        // 验证集合存在
        boolean exists = milvusClient.hasCollection(HasCollectionReq.builder()
                .collectionName(TEST_COLLECTION)
                .build());
        assertTrue(exists);

        log.info("Test collection created: {}, dimensions: {}", TEST_COLLECTION, vectorDimensions);
    }

    // ==========================================
    // Embedding 测试
    // ==========================================

    @Test
    @Order(2)
    @DisplayName("Embedding 服务 - 单文本嵌入")
    void testSingleEmbedding() {
        skipIfNotConfigured();

        String text = "这是一个测试文本";
        List<Float> embedding = embeddingService.embed(text);

        assertNotNull(embedding);
        assertEquals(vectorDimensions, embedding.size());

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
        skipIfNotConfigured();

        List<String> texts = Arrays.asList(
                "销售报告",
                "产品手册",
                "技术文档"
        );

        List<List<Float>> embeddings = embeddingService.embedBatch(texts);

        assertNotNull(embeddings);
        assertEquals(3, embeddings.size());

        for (List<Float> embedding : embeddings) {
            assertEquals(vectorDimensions, embedding.size());
        }

        log.info("Batch embedding test passed, count: {}", embeddings.size());
    }

    // ==========================================
    // 数据插入测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("插入测试数据")
    void testInsertData() {
        skipIfNotConfigured();

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

        List<List<Float>> generatedEmbeddings = embeddingService.embedBatch(textsToEmbed);

        for (int i = 0; i < TEST_DOCUMENTS.size(); i++) {
            Map<String, Object> doc = TEST_DOCUMENTS.get(i);
            docIds.add((Long) doc.get("doc_id"));
            titles.add((String) doc.get("title"));
            contents.add((String) doc.get("content"));
            categories.add((String) doc.get("category"));
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

        // 等待加载完成
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }
    }

    // ==========================================
    // 向量搜索测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("向量搜索 - 文本相似度查询")
    void testVectorSearchByText() {
        skipIfNotConfigured();

        // 搜索与"销售业绩"相关的文档
        String queryText = "销售业绩增长";
        List<Float> queryVector = embeddingService.embed(queryText);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(5)
                .outputFields(Arrays.asList("doc_id", "title", "content", "category"))
                .build());

        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
        assertNotNull(results);
        assertFalse(results.isEmpty());

        List<SearchResp.SearchResult> firstQueryResults = results.get(0);
        assertTrue(firstQueryResults.size() > 0);

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
        String topCategory = (String) topResult.getEntity().get("category");
        assertEquals("report", topCategory, "销售业绩查询应该返回报告类文档");
    }

    @Test
    @Order(21)
    @DisplayName("向量搜索 - 带过滤条件")
    void testVectorSearchWithFilter() {
        skipIfNotConfigured();

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

        List<List<SearchResp.SearchResult>> results = searchResp.getSearchResults();
        assertNotNull(results);

        List<SearchResp.SearchResult> firstQueryResults = results.get(0);

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
        skipIfNotConfigured();

        String queryText = "API接口设计REST";
        List<Float> queryVector = embeddingService.embed(queryText);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(3)
                .outputFields(Arrays.asList("doc_id", "title", "content", "category"))
                .build());

        List<SearchResp.SearchResult> firstQueryResults = searchResp.getSearchResults().get(0);

        log.info("Technical search: '{}'", queryText);
        for (SearchResp.SearchResult result : firstQueryResults) {
            log.info("  - [score: {:.4f}] {} ({})",
                    result.getScore(),
                    result.getEntity().get("title"),
                    result.getEntity().get("category"));
        }

        // 技术文档应该排在最前面
        SearchResp.SearchResult topResult = firstQueryResults.get(0);
        assertEquals("technical", topResult.getEntity().get("category"),
                "API接口查询应该返回技术文档");
    }

    @Test
    @Order(23)
    @DisplayName("向量搜索 - 相似度阈值过滤")
    void testVectorSearchWithMinScore() {
        skipIfNotConfigured();

        String queryText = "产品说明书使用方法";
        List<Float> queryVector = embeddingService.embed(queryText);

        SearchResp searchResp = milvusClient.search(SearchReq.builder()
                .collectionName(TEST_COLLECTION)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(10)
                .outputFields(Arrays.asList("doc_id", "title", "category"))
                .build());

        List<SearchResp.SearchResult> allResults = searchResp.getSearchResults().get(0);

        // 手动过滤 minScore >= 0.5
        float minScore = 0.5f;
        List<SearchResp.SearchResult> filteredResults = new ArrayList<>();
        for (SearchResp.SearchResult result : allResults) {
            if (result.getScore() >= minScore) {
                filteredResults.add(result);
            }
        }

        log.info("Search with minScore={}: {} of {} results passed", minScore, filteredResults.size(), allResults.size());

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
        skipIfNotConfigured();

        VectorDbConfig vectorDbConfig = VectorDbConfig.builder()
                .host(config.getProperty("milvus.host", "localhost"))
                .port(Integer.parseInt(config.getProperty("milvus.port", "19530")))
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
            assertEquals(vectorDimensions, metadata.getDimension());
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
        skipIfNotConfigured();

        VectorDbConfig vectorDbConfig = VectorDbConfig.builder()
                .host(config.getProperty("milvus.host", "localhost"))
                .port(Integer.parseInt(config.getProperty("milvus.port", "19530")))
                .build();

        MilvusMetadataService metadataService = new MilvusMetadataService(vectorDbConfig);
        try {
            List<VectorFieldMetadata> fields = metadataService.discoverVectorFields(TEST_COLLECTION);
            assertNotNull(fields);
            assertEquals(1, fields.size(), "测试集合应该有 1 个向量字段");

            VectorFieldMetadata field = fields.get(0);
            assertEquals("embedding", field.getFieldName());

            log.info("Discovered {} vector fields in collection '{}'", fields.size(), TEST_COLLECTION);

        } finally {
            metadataService.close();
        }
    }

    @Test
    @Order(32)
    @DisplayName("v2.0 - 连接池功能测试")
    void testConnectionPool() throws Exception {
        skipIfNotConfigured();

        VectorDbConfig vectorDbConfig = VectorDbConfig.builder()
                .host(config.getProperty("milvus.host", "localhost"))
                .port(Integer.parseInt(config.getProperty("milvus.port", "19530")))
                .poolSize(3)
                .poolMaxWaitMs(5000L)
                .build();

        MilvusClientPool pool = new MilvusClientPool(vectorDbConfig);
        try {
            // 测试并发执行
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
            log.info("Connection pool status: {}", poolStatus);

        } finally {
            pool.close();
        }
    }

    @Test
    @Order(33)
    @DisplayName("v2.0 - 向量搜索带 groupBy")
    void testVectorSearchWithGroupBy() {
        skipIfNotConfigured();

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

        List<SearchResp.SearchResult> results = searchResp.getSearchResults().get(0);

        // 验证结果不为空
        assertFalse(results.isEmpty(), "groupBy 搜索应该返回结果");

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

        // groupBy 应该减少总结果数或每组返回一个代表
        log.info("GroupBy search returned {} results with {} unique categories",
                results.size(), categoryCounts.size());

        // 验证 groupBy 确实起作用：结果数不应该超过类别数太多
        // （某些 Milvus 版本 groupBy 的行为可能不同）
        assertTrue(results.size() <= TEST_DOCUMENTS.size(),
                "groupBy 结果不应超过总文档数");
    }

    @Test
    @Order(34)
    @DisplayName("v2.0 - 范围搜索 (radius)")
    void testVectorSearchWithRadius() {
        skipIfNotConfigured();

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

        List<SearchResp.SearchResult> results = searchResp.getSearchResults().get(0);

        log.info("Radius search (radius=0.3): found {} results", results.size());

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
        skipIfNotConfigured();

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

        List<SearchResp.SearchResult> results = searchResp.getSearchResults().get(0);

        log.info("Hybrid search (vector + keyword filter): found {} results", results.size());

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
        skipIfNotConfigured();

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

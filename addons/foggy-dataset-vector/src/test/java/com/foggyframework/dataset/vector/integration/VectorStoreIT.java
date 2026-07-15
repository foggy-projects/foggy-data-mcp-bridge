package com.foggyframework.dataset.vector.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 向量数据库集成测试
 *
 * 运行前提：
 * 1. 启动 Milvus: cd foggy-dataset-demo/docker && docker-compose up -d milvus
 * 2. 配置 OpenAI API Key: export OPENAI_API_KEY=sk-xxx
 *
 * 或者使用阿里云百炼：
 * export OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
 * export OPENAI_API_KEY=sk-xxx
 * export OPENAI_EMBEDDING_MODEL=text-embedding-v3
 */
@SpringBootTest
@ActiveProfiles("test")
@Disabled("需要启动 Milvus 和配置 API Key 才能运行")
class VectorStoreIT {

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.*")
    void testAddAndSearchDocuments() {
        assertNotNull(vectorStore, "VectorStore should be configured");

        // 1. 准备测试数据
        List<Document> documents = Arrays.asList(
                createQueryTemplate("qt1", "最近一周各品牌销售情况", "dsl", "FactSalesQueryModel"),
                createQueryTemplate("qt2", "本月销售数据统计分析", "dsl", "FactSalesQueryModel"),
                createQueryTemplate("qt3", "销售趋势分析指南", "guide", "FactSalesQueryModel"),
                createQueryTemplate("qt4", "库存不足商品查询", "dsl", "FactInventoryQueryModel"),
                createQueryTemplate("qt5", "客户购买行为分析", "guide", "FactOrderQueryModel")
        );

        // 2. 写入向量数据库
        vectorStore.add(documents);

        // 3. 执行相似度搜索
        SearchRequest request = SearchRequest.builder()
                .query("最近销售情况")
                .topK(3)
                .similarityThreshold(0.7)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        // 4. 验证结果
        assertNotNull(results);
        assertFalse(results.isEmpty());

        // 最相似的应该是 "最近一周各品牌销售情况"
        Document topResult = results.get(0);
        assertTrue(topResult.getText().contains("销售"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.*")
    void testSearchByModelName() {
        assertNotNull(vectorStore);

        // 搜索特定模型的查询模板
        SearchRequest request = SearchRequest.builder()
                .query("销售数据查询")
                .topK(10)
                .similarityThreshold(0.6)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        // 过滤出 FactSalesQueryModel 的结果
        List<Document> salesModelResults = results.stream()
                .filter(d -> "FactSalesQueryModel".equals(d.getMetadata().get("model_name")))
                .toList();

        assertFalse(salesModelResults.isEmpty());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.*")
    void testSearchDslTemplates() {
        assertNotNull(vectorStore);

        SearchRequest request = SearchRequest.builder()
                .query("查询模板")
                .topK(10)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        // 过滤出 DSL 类型的模板
        List<Document> dslTemplates = results.stream()
                .filter(d -> "dsl".equals(d.getMetadata().get("template_type")))
                .toList();

        // 验证 DSL 模板包含必要的元数据
        for (Document doc : dslTemplates) {
            assertNotNull(doc.getMetadata().get("model_name"));
            assertEquals("dsl", doc.getMetadata().get("template_type"));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.*")
    void testSearchGuideDocuments() {
        assertNotNull(vectorStore);

        SearchRequest request = SearchRequest.builder()
                .query("分析指南")
                .topK(5)
                .similarityThreshold(0.5)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        // 过滤出指南类型的文档
        List<Document> guides = results.stream()
                .filter(d -> "guide".equals(d.getMetadata().get("template_type")))
                .toList();

        // 指南文档应该包含更详细的说明
        for (Document doc : guides) {
            assertEquals("guide", doc.getMetadata().get("template_type"));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = "sk-.*")
    void testDeleteDocuments() {
        assertNotNull(vectorStore);

        // 添加测试文档
        String testId = "test-delete-" + UUID.randomUUID();
        Document testDoc = createQueryTemplate(testId, "测试删除文档", "dsl", "TestModel");
        vectorStore.add(List.of(testDoc));

        // 删除文档
        vectorStore.delete(List.of(testId));

        // 验证删除成功（搜索不到）
        SearchRequest request = SearchRequest.builder()
                .query("测试删除文档")
                .topK(1)
                .similarityThreshold(0.95)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        // 应该搜索不到或者不是我们删除的那个
        boolean found = results.stream()
                .anyMatch(d -> testId.equals(d.getId()));
        assertFalse(found);
    }

    private Document createQueryTemplate(String id, String content, String templateType, String modelName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("template_type", templateType);
        metadata.put("model_name", modelName);
        metadata.put("tags", Arrays.asList("测试", "查询"));
        metadata.put("usage_count", 0);
        metadata.put("created_at", System.currentTimeMillis());

        return new Document(id, content, metadata);
    }
}

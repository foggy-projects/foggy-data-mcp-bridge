package com.foggyframework.dataset.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * VectorStore 查询功能单元测试
 */
@ExtendWith(MockitoExtension.class)
class VectorStoreQueryTest {

    @Mock
    private VectorStore vectorStore;

    private List<Document> mockDocuments;

    @BeforeEach
    void setUp() {
        // 准备模拟数据
        mockDocuments = Arrays.asList(
                createDocument("doc1", "最近一周各品牌销售情况查询模板", 0.95, "dsl", "FactSalesQueryModel"),
                createDocument("doc2", "本月销售数据统计分析", 0.85, "dsl", "FactSalesQueryModel"),
                createDocument("doc3", "销售趋势分析指南", 0.75, "guide", "FactSalesQueryModel"),
                createDocument("doc4", "库存不足商品查询", 0.65, "dsl", "FactInventoryQueryModel")
        );
    }

    @Test
    void testSimilaritySearch() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockDocuments);

        SearchRequest request = SearchRequest.builder()
                .query("最近销售情况")
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        assertNotNull(results);
        assertEquals(4, results.size());
        assertEquals("doc1", results.get(0).getId());

        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void testSimilaritySearchWithHighThreshold() {
        // 只返回高相似度的结果
        List<Document> highSimilarityDocs = mockDocuments.subList(0, 2);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(highSimilarityDocs);

        SearchRequest request = SearchRequest.builder()
                .query("品牌销售")
                .topK(10)
                .similarityThreshold(0.8)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        assertEquals(2, results.size());
        assertTrue((Double) results.get(0).getMetadata().get("distance") >= 0.8);
    }

    @Test
    void testSimilaritySearchEmptyResult() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());

        SearchRequest request = SearchRequest.builder()
                .query("不存在的查询")
                .topK(5)
                .similarityThreshold(0.9)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testDocumentMetadata() {
        Document doc = mockDocuments.get(0);

        assertEquals("doc1", doc.getId());
        assertEquals("最近一周各品牌销售情况查询模板", doc.getText());
        assertEquals("dsl", doc.getMetadata().get("template_type"));
        assertEquals("FactSalesQueryModel", doc.getMetadata().get("model_name"));
        assertEquals(0.95, doc.getMetadata().get("distance"));
    }

    @Test
    void testFilterByTemplateType() {
        // 模拟只返回 DSL 类型的模板
        List<Document> dslDocs = mockDocuments.stream()
                .filter(d -> "dsl".equals(d.getMetadata().get("template_type")))
                .toList();

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(dslDocs);

        SearchRequest request = SearchRequest.builder()
                .query("查询模板")
                .topK(10)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        assertEquals(3, results.size());
        results.forEach(doc ->
                assertEquals("dsl", doc.getMetadata().get("template_type"))
        );
    }

    private Document createDocument(String id, String content, double similarity,
                                    String templateType, String modelName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("distance", similarity);
        metadata.put("template_type", templateType);
        metadata.put("model_name", modelName);
        metadata.put("tags", Arrays.asList("销售", "统计"));
        metadata.put("usage_count", 10);

        return new Document(id, content, metadata);
    }
}

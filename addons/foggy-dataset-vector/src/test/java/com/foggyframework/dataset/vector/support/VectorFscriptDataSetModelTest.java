package com.foggyframework.dataset.vector.support;

import com.foggyframework.bundle.BundleResource;
import com.foggyframework.dataset.vector.VectorKey;
import com.foggyframework.dataset.vector.funs.VectorFileFsscriptLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * VectorFscriptDataSetModel 单元测试
 */
@ExtendWith(MockitoExtension.class)
class VectorFscriptDataSetModelTest {

    @Mock
    private BundleResource bundleResource;

    @Mock
    private VectorFileFsscriptLoader fileFsscriptLoader;

    @Mock
    private Resource resource;

    @Mock
    private VectorStore vectorStore;

    private VectorFscriptDataSetModel<?> model;

    @BeforeEach
    void setUp() {
        model = new VectorFscriptDataSetModel<>(bundleResource, fileFsscriptLoader);
    }

    @Test
    void testModelCreation() {
        assertNotNull(model);
        assertEquals(bundleResource, model.getBundleResource());
        assertEquals(fileFsscriptLoader, model.getFileFsscriptLoader());
    }

    @Test
    void testDocumentToMapConversion() {
        // 创建测试文档
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("distance", 0.95);
        metadata.put("template_type", "dsl");
        metadata.put("model_name", "FactSalesQueryModel");
        metadata.put("tags", Arrays.asList("销售", "统计"));

        Document doc = new Document("test-id", "测试内容", metadata);

        // 使用反射测试私有方法，或者通过公开方法间接测试
        // 这里我们验证 Document 的基本属性
        assertEquals("test-id", doc.getId());
        assertEquals("测试内容", doc.getText());
        assertEquals(0.95, doc.getMetadata().get("distance"));
        assertEquals("dsl", doc.getMetadata().get("template_type"));
    }

    @Test
    void testVectorKeyBuilder() {
        VectorKey key = new VectorKey(vectorStore, "测试查询", 10, 0.7, 0, 20);

        assertEquals("测试查询", key.getQuery());
        assertEquals(10, key.getTopK());
        assertEquals(0.7, key.getThreshold());
        assertEquals(0, key.getStart());
        assertEquals(20, key.getLimit());
    }

    @Test
    void testSearchRequestBuilder() {
        // 测试 SearchRequest 构建
        SearchRequest request = SearchRequest.builder()
                .query("销售数据查询")
                .topK(5)
                .similarityThreshold(0.8)
                .build();

        assertNotNull(request);
        assertEquals("销售数据查询", request.getQuery());
        assertEquals(5, request.getTopK());
        assertEquals(0.8, request.getSimilarityThreshold());
    }

    @Test
    void testVectorStoreSearch() {
        // 准备模拟数据
        List<Document> mockResults = Arrays.asList(
                createDocument("1", "查询模板1", 0.95),
                createDocument("2", "查询模板2", 0.85)
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockResults);

        // 执行搜索
        SearchRequest request = SearchRequest.builder()
                .query("测试")
                .topK(5)
                .similarityThreshold(0.7)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);

        // 验证结果
        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("1", results.get(0).getId());
        assertEquals(0.95, results.get(0).getMetadata().get("distance"));

        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    private Document createDocument(String id, String content, double similarity) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("distance", similarity);
        metadata.put("template_type", "dsl");
        metadata.put("model_name", "TestModel");

        return new Document(id, content, metadata);
    }
}

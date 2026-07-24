package com.foggyframework.dataset.model.vector;

import com.foggyframework.dataset.model.engine.VectorModelQueryEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorSearchParams 单元测试
 *
 * <p>测试向量搜索参数类的基本功能</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("VectorSearchParams 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorSearchParamsTest {

    // ==========================================
    // 默认值测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("默认值 - 检查初始状态")
    void testDefaultValues() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();

        assertNull(params.getField());
        assertNull(params.getText());
        assertNull(params.getVector());
        assertEquals(10, params.getTopK());  // 默认 topK = 10
        assertNull(params.getMinScore());

        log.info("默认值测试通过: topK={}", params.getTopK());
    }

    // ==========================================
    // 文本搜索参数测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("文本搜索 - 基本配置")
    void testTextSearch() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setField("embedding");
        params.setText("销售额趋势分析");
        params.setTopK(10);

        assertEquals("embedding", params.getField());
        assertEquals("销售额趋势分析", params.getText());
        assertEquals(10, params.getTopK());

        log.info("文本搜索配置测试通过: field={}, text={}", params.getField(), params.getText());
    }

    @Test
    @Order(11)
    @DisplayName("文本搜索 - 带最低分数阈值")
    void testTextSearchWithMinScore() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setField("content_embedding");
        params.setText("如何提升客户满意度");
        params.setTopK(20);
        params.setMinScore(0.7f);

        assertEquals("content_embedding", params.getField());
        assertEquals("如何提升客户满意度", params.getText());
        assertEquals(20, params.getTopK());
        assertEquals(0.7f, params.getMinScore());

        log.info("带阈值文本搜索测试通过: minScore={}", params.getMinScore());
    }

    // ==========================================
    // 向量搜索参数测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("向量搜索 - 直接传入向量")
    void testVectorSearch() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setField("embedding");

        List<Float> vector = Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);
        params.setVector(vector);
        params.setTopK(5);

        assertEquals("embedding", params.getField());
        assertNotNull(params.getVector());
        assertEquals(5, params.getVector().size());
        assertEquals(0.1f, params.getVector().get(0));
        assertEquals(5, params.getTopK());

        log.info("向量搜索测试通过: vectorDim={}", params.getVector().size());
    }

    @Test
    @Order(21)
    @DisplayName("向量搜索 - 高维向量")
    void testHighDimensionalVector() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setField("embedding");

        // 模拟 1536 维向量（OpenAI text-embedding-3-small）
        List<Float> vector = new java.util.ArrayList<>();
        for (int i = 0; i < 1536; i++) {
            vector.add((float) Math.random());
        }
        params.setVector(vector);

        assertEquals(1536, params.getVector().size());

        log.info("高维向量测试通过: dimensions={}", params.getVector().size());
    }

    // ==========================================
    // topK 参数测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("topK - 小值")
    void testTopKSmall() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setTopK(1);

        assertEquals(1, params.getTopK());

        log.info("小topK测试通过: topK={}", params.getTopK());
    }

    @Test
    @Order(31)
    @DisplayName("topK - 大值")
    void testTopKLarge() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setTopK(1000);

        assertEquals(1000, params.getTopK());

        log.info("大topK测试通过: topK={}", params.getTopK());
    }

    // ==========================================
    // minScore 参数测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("minScore - 边界值 0")
    void testMinScoreZero() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setMinScore(0.0f);

        assertEquals(0.0f, params.getMinScore());

        log.info("minScore=0 测试通过");
    }

    @Test
    @Order(41)
    @DisplayName("minScore - 边界值 1")
    void testMinScoreOne() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setMinScore(1.0f);

        assertEquals(1.0f, params.getMinScore());

        log.info("minScore=1 测试通过");
    }

    @Test
    @Order(42)
    @DisplayName("minScore - 典型值 0.7")
    void testMinScoreTypical() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setMinScore(0.7f);

        assertEquals(0.7f, params.getMinScore());

        log.info("minScore=0.7 测试通过");
    }

    // ==========================================
    // 综合测试
    // ==========================================

    @Test
    @Order(50)
    @DisplayName("综合 - 完整配置")
    void testFullConfig() {
        VectorModelQueryEngine.VectorSearchParams params = new VectorModelQueryEngine.VectorSearchParams();
        params.setField("doc_embedding");
        params.setText("年度销售报告分析");
        params.setTopK(15);
        params.setMinScore(0.65f);

        // 模拟已转换的向量
        List<Float> vector = Arrays.asList(0.1f, 0.2f, 0.3f);
        params.setVector(vector);

        assertEquals("doc_embedding", params.getField());
        assertEquals("年度销售报告分析", params.getText());
        assertEquals(15, params.getTopK());
        assertEquals(0.65f, params.getMinScore());
        assertNotNull(params.getVector());
        assertEquals(3, params.getVector().size());

        log.info("完整配置测试通过");
    }
}

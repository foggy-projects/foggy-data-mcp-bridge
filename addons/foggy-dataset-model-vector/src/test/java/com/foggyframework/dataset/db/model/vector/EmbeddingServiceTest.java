package com.foggyframework.dataset.db.model.vector;

import com.foggyframework.dataset.db.model.impl.vector.EmbeddingService;
import com.foggyframework.dataset.db.model.impl.vector.VectorDbConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingService 单元测试
 *
 * <p>测试 Embedding 服务的配置和基本功能。
 * 注意：实际的 API 调用测试需要配置有效的 API Key。</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("EmbeddingService 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmbeddingServiceTest {

    // ==========================================
    // 配置测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("配置 - 默认 OpenAI 配置")
    void testDefaultOpenAIConfig() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .type("openai")
                .baseUrl("https://api.openai.com/v1")
                .apiKey("sk-test")
                .model("text-embedding-3-small")
                .dimensions(1536)
                .build();

        assertEquals("openai", config.getType());
        assertEquals("https://api.openai.com/v1", config.getBaseUrl());
        assertEquals("text-embedding-3-small", config.getModel());
        assertEquals(1536, config.getDimensions());

        log.info("默认OpenAI配置测试通过");
    }

    @Test
    @Order(2)
    @DisplayName("配置 - Ollama 配置")
    void testOllamaConfig() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .type("ollama")
                .baseUrl("http://localhost:11434/v1")
                .model("nomic-embed-text")
                .dimensions(768)
                .build();

        assertEquals("ollama", config.getType());
        assertEquals("http://localhost:11434/v1", config.getBaseUrl());
        assertEquals("nomic-embed-text", config.getModel());
        assertEquals(768, config.getDimensions());

        log.info("Ollama配置测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("配置 - 阿里云 DashScope 配置")
    void testDashScopeConfig() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .type("dashscope")
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
                .apiKey("sk-xxx")
                .model("text-embedding-v3")
                .dimensions(1024)
                .build();

        assertEquals("dashscope", config.getType());
        assertTrue(config.getBaseUrl().contains("dashscope"));
        assertEquals("text-embedding-v3", config.getModel());
        assertEquals(1024, config.getDimensions());

        log.info("DashScope配置测试通过");
    }

    // ==========================================
    // 服务初始化测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("初始化 - 使用有效配置")
    void testInitWithValidConfig() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .type("openai")
                .baseUrl("https://api.openai.com/v1")
                .apiKey("sk-test-key")
                .model("text-embedding-3-small")
                .dimensions(1536)
                .build();

        // 不会抛出异常
        EmbeddingService service = new EmbeddingService(config);
        assertNotNull(service);
        assertEquals(1536, service.getDimensions());

        log.info("有效配置初始化测试通过");
    }

    @Test
    @Order(11)
    @DisplayName("初始化 - 使用默认 baseUrl")
    void testInitWithDefaultBaseUrl() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .apiKey("sk-test-key")
                .model("text-embedding-3-small")
                .dimensions(1536)
                .build();

        // baseUrl 为 null，应该使用默认值
        EmbeddingService service = new EmbeddingService(config);
        assertNotNull(service);

        log.info("默认baseUrl初始化测试通过");
    }

    @Test
    @Order(12)
    @DisplayName("初始化 - 使用空 baseUrl")
    void testInitWithEmptyBaseUrl() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .baseUrl("")
                .apiKey("sk-test-key")
                .model("text-embedding-3-small")
                .dimensions(1536)
                .build();

        // 空 baseUrl，应该使用默认值
        EmbeddingService service = new EmbeddingService(config);
        assertNotNull(service);

        log.info("空baseUrl初始化测试通过");
    }

    // ==========================================
    // 参数验证测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("参数验证 - null 文本应抛出异常")
    void testEmbedNullText() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .apiKey("sk-test-key")
                .build();

        EmbeddingService service = new EmbeddingService(config);

        assertThrows(IllegalArgumentException.class, () -> {
            service.embed(null);
        });

        log.info("null文本参数验证测试通过");
    }

    @Test
    @Order(21)
    @DisplayName("参数验证 - 空文本应抛出异常")
    void testEmbedEmptyText() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .apiKey("sk-test-key")
                .build();

        EmbeddingService service = new EmbeddingService(config);

        assertThrows(IllegalArgumentException.class, () -> {
            service.embed("");
        });

        log.info("空文本参数验证测试通过");
    }

    @Test
    @Order(22)
    @DisplayName("参数验证 - 空白文本应抛出异常")
    void testEmbedBlankText() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .apiKey("sk-test-key")
                .build();

        EmbeddingService service = new EmbeddingService(config);

        assertThrows(IllegalArgumentException.class, () -> {
            service.embed("   ");
        });

        log.info("空白文本参数验证测试通过");
    }

    // ==========================================
    // 维度配置测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("维度 - OpenAI text-embedding-3-small (1536)")
    void testDimensionsSmall() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .model("text-embedding-3-small")
                .dimensions(1536)
                .build();

        EmbeddingService service = new EmbeddingService(config);
        assertEquals(1536, service.getDimensions());

        log.info("text-embedding-3-small维度测试通过: {}", service.getDimensions());
    }

    @Test
    @Order(31)
    @DisplayName("维度 - OpenAI text-embedding-3-large (3072)")
    void testDimensionsLarge() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .model("text-embedding-3-large")
                .dimensions(3072)
                .build();

        EmbeddingService service = new EmbeddingService(config);
        assertEquals(3072, service.getDimensions());

        log.info("text-embedding-3-large维度测试通过: {}", service.getDimensions());
    }

    @Test
    @Order(32)
    @DisplayName("维度 - Ollama nomic-embed-text (768)")
    void testDimensionsOllama() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .model("nomic-embed-text")
                .dimensions(768)
                .build();

        EmbeddingService service = new EmbeddingService(config);
        assertEquals(768, service.getDimensions());

        log.info("nomic-embed-text维度测试通过: {}", service.getDimensions());
    }

    // ==========================================
    // 批量嵌入测试
    // ==========================================

    @Test
    @Order(40)
    @DisplayName("批量嵌入 - null 列表返回空")
    void testEmbedBatchNull() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .apiKey("sk-test-key")
                .build();

        EmbeddingService service = new EmbeddingService(config);
        List<List<Float>> result = service.embedBatch(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        log.info("null列表批量嵌入测试通过");
    }

    @Test
    @Order(41)
    @DisplayName("批量嵌入 - 空列表返回空")
    void testEmbedBatchEmpty() {
        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .apiKey("sk-test-key")
                .build();

        EmbeddingService service = new EmbeddingService(config);
        List<List<Float>> result = service.embedBatch(List.of());

        assertNotNull(result);
        assertTrue(result.isEmpty());

        log.info("空列表批量嵌入测试通过");
    }

    // ==========================================
    // 集成测试（需要真实 API Key，默认跳过）
    // ==========================================

    @Test
    @Order(100)
    @DisplayName("集成测试 - 真实 API 调用（需配置 API Key）")
    @Disabled("需要配置真实的 API Key 才能运行此测试")
    void testRealApiCall() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("OPENAI_API_KEY 环境变量未设置，跳过集成测试");
            return;
        }

        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .type("openai")
                .baseUrl("https://api.openai.com/v1")
                .apiKey(apiKey)
                .model("text-embedding-3-small")
                .dimensions(1536)
                .build();

        EmbeddingService service = new EmbeddingService(config);
        List<Float> embedding = service.embed("这是一个测试文本");

        assertNotNull(embedding);
        assertEquals(1536, embedding.size());

        // 检查向量值是否在合理范围内
        for (Float value : embedding) {
            assertTrue(value >= -1.0f && value <= 1.0f,
                    "向量值应该在 [-1, 1] 范围内");
        }

        log.info("真实API调用测试通过，向量维度: {}", embedding.size());
    }
}

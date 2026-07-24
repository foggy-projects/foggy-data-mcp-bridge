package com.foggyframework.dataset.model.vector;

import com.foggyframework.dataset.model.impl.vector.VectorDbConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorDbConfig 单元测试
 *
 * <p>测试向量数据库配置类的基本功能</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@DisplayName("VectorDbConfig 单元测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VectorDbConfigTest {

    // ==========================================
    // 默认配置测试
    // ==========================================

    @Test
    @Order(1)
    @DisplayName("默认配置 - 检查默认值")
    void testDefaultConfig() {
        VectorDbConfig config = new VectorDbConfig();

        assertEquals("milvus", config.getType());
        assertEquals("localhost", config.getHost());
        assertEquals(19530, config.getPort());
        assertEquals(10000, config.getConnectTimeoutMs());
        assertFalse(config.isSecure());
        assertNull(config.getDatabase());
        assertNull(config.getUsername());
        assertNull(config.getPassword());

        log.info("默认配置测试通过: {}", config);
    }

    // ==========================================
    // Builder 模式测试
    // ==========================================

    @Test
    @Order(10)
    @DisplayName("Builder 模式 - 完整配置")
    void testBuilderFullConfig() {
        VectorDbConfig config = VectorDbConfig.builder()
                .type("milvus")
                .host("192.168.1.100")
                .port(19530)
                .database("test_db")
                .username("admin")
                .password("secret123")
                .connectTimeoutMs(30000)
                .secure(true)
                .build();

        assertEquals("milvus", config.getType());
        assertEquals("192.168.1.100", config.getHost());
        assertEquals(19530, config.getPort());
        assertEquals("test_db", config.getDatabase());
        assertEquals("admin", config.getUsername());
        assertEquals("secret123", config.getPassword());
        assertEquals(30000, config.getConnectTimeoutMs());
        assertTrue(config.isSecure());

        log.info("Builder完整配置测试通过: host={}, port={}", config.getHost(), config.getPort());
    }

    @Test
    @Order(11)
    @DisplayName("Builder 模式 - 部分配置")
    void testBuilderPartialConfig() {
        VectorDbConfig config = VectorDbConfig.builder()
                .host("vector-db.example.com")
                .port(19531)
                .build();

        assertEquals("vector-db.example.com", config.getHost());
        assertEquals(19531, config.getPort());
        // 其他使用默认值
        assertEquals("milvus", config.getType());
        assertNull(config.getDatabase());

        log.info("Builder部分配置测试通过");
    }

    // ==========================================
    // Embedding 配置测试
    // ==========================================

    @Test
    @Order(20)
    @DisplayName("Embedding 配置 - 默认值")
    void testEmbeddingConfigDefaults() {
        VectorDbConfig.EmbeddingConfig embeddingConfig = new VectorDbConfig.EmbeddingConfig();

        assertEquals("openai", embeddingConfig.getType());
        assertEquals("text-embedding-3-small", embeddingConfig.getModel());
        assertEquals(1536, embeddingConfig.getDimensions());
        assertNull(embeddingConfig.getBaseUrl());
        assertNull(embeddingConfig.getApiKey());

        log.info("Embedding默认配置测试通过");
    }

    @Test
    @Order(21)
    @DisplayName("Embedding 配置 - OpenAI 配置")
    void testEmbeddingConfigOpenAI() {
        VectorDbConfig.EmbeddingConfig embeddingConfig = VectorDbConfig.EmbeddingConfig.builder()
                .type("openai")
                .baseUrl("https://api.openai.com/v1")
                .apiKey("sk-xxx")
                .model("text-embedding-3-large")
                .dimensions(3072)
                .build();

        assertEquals("openai", embeddingConfig.getType());
        assertEquals("https://api.openai.com/v1", embeddingConfig.getBaseUrl());
        assertEquals("sk-xxx", embeddingConfig.getApiKey());
        assertEquals("text-embedding-3-large", embeddingConfig.getModel());
        assertEquals(3072, embeddingConfig.getDimensions());

        log.info("OpenAI Embedding配置测试通过");
    }

    @Test
    @Order(22)
    @DisplayName("Embedding 配置 - Ollama 配置")
    void testEmbeddingConfigOllama() {
        VectorDbConfig.EmbeddingConfig embeddingConfig = VectorDbConfig.EmbeddingConfig.builder()
                .type("ollama")
                .baseUrl("http://localhost:11434/v1")
                .model("nomic-embed-text")
                .dimensions(768)
                .build();

        assertEquals("ollama", embeddingConfig.getType());
        assertEquals("http://localhost:11434/v1", embeddingConfig.getBaseUrl());
        assertEquals("nomic-embed-text", embeddingConfig.getModel());
        assertEquals(768, embeddingConfig.getDimensions());

        log.info("Ollama Embedding配置测试通过");
    }

    @Test
    @Order(23)
    @DisplayName("完整配置 - VectorDbConfig + EmbeddingConfig")
    void testFullConfigWithEmbedding() {
        VectorDbConfig.EmbeddingConfig embeddingConfig = VectorDbConfig.EmbeddingConfig.builder()
                .type("openai")
                .baseUrl("https://api.openai.com/v1")
                .apiKey("sk-test")
                .model("text-embedding-3-small")
                .dimensions(1536)
                .build();

        VectorDbConfig config = VectorDbConfig.builder()
                .type("milvus")
                .host("milvus.example.com")
                .port(19530)
                .database("production")
                .embedding(embeddingConfig)
                .build();

        assertNotNull(config.getEmbedding());
        assertEquals("openai", config.getEmbedding().getType());
        assertEquals(1536, config.getEmbedding().getDimensions());

        log.info("完整配置测试通过: vectorDb={}, embedding={}",
                config.getHost(), config.getEmbedding().getModel());
    }

    // ==========================================
    // 边界条件测试
    // ==========================================

    @Test
    @Order(30)
    @DisplayName("边界条件 - 空字符串配置")
    void testEmptyStringConfig() {
        VectorDbConfig config = VectorDbConfig.builder()
                .host("")
                .database("")
                .username("")
                .build();

        assertEquals("", config.getHost());
        assertEquals("", config.getDatabase());
        assertEquals("", config.getUsername());

        log.info("空字符串配置测试通过");
    }

    @Test
    @Order(31)
    @DisplayName("边界条件 - 特殊端口号")
    void testSpecialPorts() {
        // 最小端口
        VectorDbConfig config1 = VectorDbConfig.builder().port(1).build();
        assertEquals(1, config1.getPort());

        // 最大端口
        VectorDbConfig config2 = VectorDbConfig.builder().port(65535).build();
        assertEquals(65535, config2.getPort());

        log.info("特殊端口号测试通过");
    }

    @Test
    @Order(32)
    @DisplayName("边界条件 - 超时时间")
    void testTimeoutConfig() {
        // 最小超时
        VectorDbConfig config1 = VectorDbConfig.builder().connectTimeoutMs(1).build();
        assertEquals(1, config1.getConnectTimeoutMs());

        // 大超时值
        VectorDbConfig config2 = VectorDbConfig.builder().connectTimeoutMs(600000).build();
        assertEquals(600000, config2.getConnectTimeoutMs());

        log.info("超时时间配置测试通过");
    }
}

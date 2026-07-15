package com.foggyframework.dataset.db.model.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.db.model.impl.vector.EmbeddingService;
import com.foggyframework.dataset.db.model.impl.vector.VectorDbConfig;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingService 单元测试
 *
 * <p>测试 Embedding 服务的配置、请求契约和响应解析。</p>
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
    // HTTP 请求与响应解析
    // ==========================================

    @Test
    @Order(100)
    @DisplayName("HTTP - 本地 OpenAI 兼容接口")
    void testRealApiCall() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"data\":[{\"embedding\":[-0.25,0.0,0.75]}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        VectorDbConfig.EmbeddingConfig config = VectorDbConfig.EmbeddingConfig.builder()
                .type("openai")
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey("local-test-key")
                .model("local-embedding-model")
                .dimensions(3)
                .build();

        try {
            EmbeddingService service = new EmbeddingService(config);
            List<Float> embedding = service.embed("这是一个测试文本");

            assertEquals(List.of(-0.25f, 0.0f, 0.75f), embedding);
            assertEquals("POST", method.get());
            assertEquals("/embeddings", path.get());
            assertEquals("Bearer local-test-key", authorization.get());
            JsonNode request = new ObjectMapper().readTree(requestBody.get());
            assertEquals("local-embedding-model", request.path("model").asText());
            assertEquals("这是一个测试文本", request.path("input").asText());
        } finally {
            server.stop(0);
        }

        log.info("本地 OpenAI 兼容接口测试通过");
    }
}

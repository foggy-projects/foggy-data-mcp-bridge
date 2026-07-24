package com.foggyframework.dataset.model.impl.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Embedding 服务
 * <p>
 * 将文本转换为向量，支持 OpenAI 兼容接口。
 */
@Slf4j
public class EmbeddingService {

    private final VectorDbConfig.EmbeddingConfig config;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService(VectorDbConfig.EmbeddingConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();

        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://api.openai.com/v1";
        }

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 将文本转换为向量
     *
     * @param text 输入文本
     * @return 向量数组
     */
    public List<Float> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        try {
            Map<String, Object> request = Map.of(
                    "model", config.getModel(),
                    "input", text
            );

            String response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            return parseEmbeddingResponse(response);
        } catch (Exception e) {
            log.error("Failed to get embedding for text: {}", text.substring(0, Math.min(50, text.length())), e);
            throw new RuntimeException("Failed to get embedding: " + e.getMessage(), e);
        }
    }

    /**
     * 批量将文本转换为向量
     *
     * @param texts 输入文本列表
     * @return 向量数组列表
     */
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Map<String, Object> request = Map.of(
                    "model", config.getModel(),
                    "input", texts
            );

            String response = webClient.post()
                    .uri("/embeddings")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            return parseEmbeddingBatchResponse(response);
        } catch (Exception e) {
            log.error("Failed to get embeddings for {} texts", texts.size(), e);
            throw new RuntimeException("Failed to get embeddings: " + e.getMessage(), e);
        }
    }

    /**
     * 解析单个 embedding 响应
     */
    private List<Float> parseEmbeddingResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            if (data != null && data.isArray() && data.size() > 0) {
                JsonNode embedding = data.get(0).get("embedding");
                return parseEmbeddingArray(embedding);
            }
            throw new RuntimeException("Invalid embedding response format");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse embedding response: " + e.getMessage(), e);
        }
    }

    /**
     * 解析批量 embedding 响应
     */
    private List<List<Float>> parseEmbeddingBatchResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            List<List<Float>> results = new ArrayList<>();

            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode embedding = item.get("embedding");
                    results.add(parseEmbeddingArray(embedding));
                }
            }

            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse embedding batch response: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 embedding 数组
     */
    private List<Float> parseEmbeddingArray(JsonNode embeddingNode) {
        List<Float> embedding = new ArrayList<>();
        if (embeddingNode != null && embeddingNode.isArray()) {
            for (JsonNode value : embeddingNode) {
                embedding.add(value.floatValue());
            }
        }
        return embedding;
    }

    /**
     * 获取配置的向量维度
     */
    public int getDimensions() {
        return config.getDimensions();
    }
}

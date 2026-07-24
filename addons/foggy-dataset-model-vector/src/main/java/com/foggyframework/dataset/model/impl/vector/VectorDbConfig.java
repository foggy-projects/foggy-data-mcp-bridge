package com.foggyframework.dataset.model.impl.vector;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 向量数据库连接配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorDbConfig {

    /**
     * 向量数据库类型: milvus, pinecone, elasticsearch 等
     */
    @Builder.Default
    private String type = "milvus";

    /**
     * 数据库主机地址
     */
    @Builder.Default
    private String host = "localhost";

    /**
     * 数据库端口
     */
    @Builder.Default
    private int port = 19530;

    /**
     * 数据库名称（Milvus 中称为 database）
     */
    private String database;

    /**
     * 用户名（可选）
     */
    private String username;

    /**
     * 密码（可选）
     */
    private String password;

    /**
     * 连接超时时间（毫秒）
     */
    @Builder.Default
    private long connectTimeoutMs = 10000;

    /**
     * 是否使用安全连接
     */
    @Builder.Default
    private boolean secure = false;

    /**
     * 是否启用元数据自动发现
     * <p>
     * 启用后，向量字段的 dimension、metric、indexType 等信息将从 Milvus 自动获取，
     * 无需在 TM 文件中手动配置。
     */
    @Builder.Default
    private boolean autoDiscovery = true;

    /**
     * 连接池大小
     */
    @Builder.Default
    private int poolSize = 5;

    /**
     * 连接池最大等待时间（毫秒）
     */
    @Builder.Default
    private long poolMaxWaitMs = 5000;

    /**
     * Embedding 服务配置
     */
    private EmbeddingConfig embedding;

    /**
     * Embedding 服务配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmbeddingConfig {
        /**
         * Embedding 服务类型: openai, ollama, custom
         */
        @Builder.Default
        private String type = "openai";

        /**
         * API Base URL
         */
        private String baseUrl;

        /**
         * API Key
         */
        private String apiKey;

        /**
         * 模型名称
         */
        @Builder.Default
        private String model = "text-embedding-3-small";

        /**
         * 向量维度
         */
        @Builder.Default
        private int dimensions = 1536;
    }
}

package com.foggyframework.benchmark.spider2.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spider2 基准测试配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "spider2")
public class Spider2Properties {

    /**
     * Spider2-Lite JSONL 文件路径
     * 可通过环境变量 SPIDER2_BASE_PATH 设置基础路径
     */
    private String jsonlPath = "${SPIDER2_BASE_PATH:./spider2-data}/spider2-lite/spider2-lite.jsonl";

    /**
     * SQLite 数据库基础路径
     * 可通过环境变量 SPIDER2_BASE_PATH 设置基础路径
     */
    private String databaseBasePath = "${SPIDER2_BASE_PATH:./spider2-data}/spider2-lite/resource/databases/spider2-localdb";

    /**
     * 元数据基础路径（包含表描述和样本数据的 JSON 文件）
     * 可通过环境变量 SPIDER2_BASE_PATH 设置基础路径
     */
    private String metadataBasePath = "${SPIDER2_BASE_PATH:./spider2-data}/spider2-lite/resource/databases/sqlite";

    /**
     * 启用的数据库列表（只测试这些数据库）
     */
    private List<String> enabledDatabases = new ArrayList<>();

    /**
     * 是否只加载有 TM/QM 模型的测试用例
     */
    private boolean onlyWithModels = true;

    /**
     * 最大测试用例数量（0 表示不限制）
     */
    private int maxTestCases = 0;

    /**
     * AI 模型配置
     */
    private AiConfig ai = new AiConfig();

    @Data
    public static class AiConfig {
        /**
         * 默认超时时间（秒）
         */
        private int timeoutSeconds = 60;

        /**
         * 最大重试次数
         */
        private int maxRetries = 3;
    }
}

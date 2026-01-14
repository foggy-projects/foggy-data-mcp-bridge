package com.foggyframework.dataset.vector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 向量数据库测试应用
 *
 * 用于集成测试，需要配置：
 * - Milvus 连接信息
 * - OpenAI API Key（用于 Embedding）
 */
@SpringBootApplication
public class VectorTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(VectorTestApplication.class, args);
    }
}

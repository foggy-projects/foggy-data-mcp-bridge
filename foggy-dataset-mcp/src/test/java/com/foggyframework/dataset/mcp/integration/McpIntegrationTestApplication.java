package com.foggyframework.dataset.mcp.integration;

import com.foggyframework.core.annotates.EnableFoggyFramework;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * MCP 集成测试应用
 *
 * 启动完整的 Spring Context，包括：
 * - foggy-dataset-model 的服务层（SemanticService, JdbcService 等）
 * - MCP 工具层
 * - 真实数据库连接
 */
@SpringBootApplication(scanBasePackages = {
        "com.foggyframework.dataset.mcp",
        "com.foggyframework.dataset.jdbc.model"
})
@EnableFoggyFramework(bundleName = "foggy-dataset-mcp-integration-test")
public class McpIntegrationTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpIntegrationTestApplication.class, args);
    }

    @Bean
    @ConditionalOnMissingBean(name = "mongoTemplate")
    @Primary
    public MongoTemplate mongoTemplate(MongoDatabaseFactory mongoDatabaseFactory) {
        return new MongoTemplate(mongoDatabaseFactory);
    }
}

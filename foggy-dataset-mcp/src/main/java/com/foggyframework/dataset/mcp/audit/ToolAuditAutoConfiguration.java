package com.foggyframework.dataset.mcp.audit;

import com.foggyframework.dataset.mcp.config.McpProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * 审计日志自动配置
 *
 * <p>三层保护链路：
 * <ol>
 *   <li>{@code @ConditionalOnClass} — classpath 无 MongoDB 时整个配置类跳过</li>
 *   <li>{@code @ConditionalOnProperty} — {@code foggy.mcp.audit.enabled=true} 才激活</li>
 *   <li>{@code @ConditionalOnBean} — 必须已配置 MongoDB 连接（存在 MongoTemplate）</li>
 * </ol>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.data.mongodb.core.MongoTemplate")
@ConditionalOnProperty(prefix = "foggy.mcp.audit", name = "enabled", havingValue = "true")
public class ToolAuditAutoConfiguration {

    @Bean
    @ConditionalOnBean(MongoTemplate.class)
    public ToolAuditService toolAuditService(MongoTemplate mongoTemplate, McpProperties mcpProperties) {
        return new ToolAuditService(mongoTemplate, mcpProperties);
    }
}

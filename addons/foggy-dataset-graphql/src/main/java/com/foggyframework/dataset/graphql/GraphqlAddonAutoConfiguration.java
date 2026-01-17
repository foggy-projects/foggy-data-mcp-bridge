package com.foggyframework.dataset.graphql;

import com.foggyframework.dataset.graphql.controller.GraphqlEndpointController;
import com.foggyframework.dataset.graphql.converter.GraphqlToDslConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * GraphQL Addon 自动配置
 * <p>
 * 通过配置 foggy.dataset.graphql.enabled=true 启用 GraphQL 支持
 * </p>
 *
 * @author Foggy Framework
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "foggy.dataset.graphql.enabled", havingValue = "true", matchIfMissing = true)
@Import(GraphqlEndpointController.class)
public class GraphqlAddonAutoConfiguration {

    public GraphqlAddonAutoConfiguration() {
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║  Foggy Dataset GraphQL Addon 已启用                            ║");
        log.info("║  GraphQL endpoint: POST /graphql                              ║");
        log.info("║  支持 GraphQL 查询 → DSL 自动转换                              ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");
    }

    @Bean
    @ConditionalOnProperty(name = "foggy.dataset.graphql.converter.enabled", havingValue = "true", matchIfMissing = true)
    public GraphqlToDslConverter graphqlToDslConverter() {
        log.info("注册 GraphqlToDslConverter Bean");
        return new GraphqlToDslConverter();
    }
}

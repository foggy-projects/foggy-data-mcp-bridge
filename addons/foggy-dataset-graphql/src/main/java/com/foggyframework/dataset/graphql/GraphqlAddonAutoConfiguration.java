package com.foggyframework.dataset.graphql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.graphql.controller.GraphqlEndpointController;
import com.foggyframework.dataset.graphql.converter.GraphqlToDslConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * GraphQL Addon 自动配置
 * <p>
 * 通过配置 foggy.dataset.graphql.enabled=true 启用 GraphQL 支持
 * </p>
 *
 * @author Foggy Framework
 */
@Slf4j
@AutoConfiguration(
        after = JacksonAutoConfiguration.class,
        afterName = "com.foggyframework.dataset.db.model.DbModelAutoConfiguration")
@ConditionalOnProperty(name = "foggy.dataset.graphql.enabled", havingValue = "true", matchIfMissing = true)
public class GraphqlAddonAutoConfiguration {

    public GraphqlAddonAutoConfiguration() {
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║  Foggy Dataset GraphQL Addon 已启用                            ║");
        log.info("║  GraphQL endpoint: POST /graphql                              ║");
        log.info("║  支持 GraphQL 查询 → DSL 自动转换                              ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "foggy.dataset.graphql.converter.enabled", havingValue = "true", matchIfMissing = true)
    public GraphqlToDslConverter graphqlToDslConverter() {
        log.info("注册 GraphqlToDslConverter Bean");
        return new GraphqlToDslConverter();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(DispatcherServlet.class)
    @ConditionalOnBean({QueryFacade.class, ObjectMapper.class})
    @ConditionalOnProperty(name = "foggy.dataset.graphql.converter.enabled", havingValue = "true", matchIfMissing = true)
    static class GraphqlWebConfiguration {

        @Bean
        @ConditionalOnMissingBean
        GraphqlEndpointController graphqlEndpointController(QueryFacade queryFacade,
                                                             GraphqlToDslConverter converter,
                                                             ObjectMapper objectMapper) {
            return new GraphqlEndpointController(queryFacade, converter, objectMapper);
        }
    }
}

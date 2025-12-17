package com.foggyframework.dataset.mcp.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 多端口配置
 *
 * 支持 M1/M2 双端口架构：
 * - M1 (7108): 智能 Agent 接口
 * - M2 (7109): 数据分析师接口
 *
 * 通过 profile 控制启动哪个端口：
 * - spring.profiles.active=m1  -> 启动 7108
 * - spring.profiles.active=m2  -> 启动 7109
 * - spring.profiles.active=dev -> 使用默认端口
 */
@Configuration
public class MultiPortConfig {

    private final McpProperties mcpProperties;

    public MultiPortConfig(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    /**
     * M1 端口配置
     */
    @Bean
    @Profile("m1")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> m1PortCustomizer() {
        return factory -> {
            int port = mcpProperties.getService().getM1Port();
            factory.setPort(port);
        };
    }

    /**
     * M2 端口配置
     */
    @Bean
    @Profile("m2")
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> m2PortCustomizer() {
        return factory -> {
            int port = mcpProperties.getService().getM2Port();
            factory.setPort(port);
        };
    }
}

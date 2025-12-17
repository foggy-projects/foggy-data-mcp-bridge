package com.foggyframework.dataset.mcp.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * WebClient 配置
 */
@Configuration
public class WebClientConfig {

    private final McpProperties mcpProperties;

    public WebClientConfig(McpProperties mcpProperties) {
        this.mcpProperties = mcpProperties;
    }

    /**
     * 数据查询层 WebClient
     */
    @Bean(name = "datasetQueryWebClient")
    public WebClient datasetQueryWebClient() {
        int timeout = mcpProperties.getExternal().getDatasetQuery().getTimeoutSeconds();
        String baseUrl = mcpProperties.getExternal().getDatasetQuery().getBaseUrl();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout * 1000)
                .responseTimeout(Duration.ofSeconds(timeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeout, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * 图表渲染服务 WebClient
     */
    @Bean(name = "chartRenderWebClient")
    public WebClient chartRenderWebClient() {
        int timeout = mcpProperties.getExternal().getChartRender().getTimeoutSeconds();
        String baseUrl = mcpProperties.getExternal().getChartRender().getBaseUrl();
        String authToken = mcpProperties.getExternal().getChartRender().getAuthToken();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout * 1000)
                .responseTimeout(Duration.ofSeconds(timeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeout, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", authToken)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

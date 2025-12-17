package com.foggyframework.dataset.mcp.integration.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import static org.mockito.Mockito.mock;

/**
 * MCP 集成测试配置
 *
 * 当使用 local 模式时，DatasetAccessor 直接调用 SemanticService，
 * 不需要 datasetQueryWebClient。
 *
 * 此配置仅提供 chartRenderWebClient 的 mock（图表服务在集成测试中暂不测试）
 */
@Slf4j
@TestConfiguration
public class McpIntegrationTestConfig {

    /**
     * 创建 mock 的 chartRenderWebClient
     *
     * 图表服务在集成测试中暂不测试
     */
    @Bean
    @Primary
    public WebClient chartRenderWebClient() {
        log.info("Creating mock chartRenderWebClient for integration tests");
        return mock(WebClient.class);
    }

    /**
     * 创建 mock 的 datasetQueryWebClient
     *
     * 在 local 模式下不会使用，但需要满足 Bean 依赖（RemoteDatasetAccessor 可能需要）
     * 注意：由于配置了 access-mode=local，实际使用的是 LocalDatasetAccessor
     */
    @Bean("datasetQueryWebClient")
    public WebClient datasetQueryWebClient() {
        log.info("Creating mock datasetQueryWebClient for integration tests (local mode - not actually used)");
        return mock(WebClient.class);
    }
}

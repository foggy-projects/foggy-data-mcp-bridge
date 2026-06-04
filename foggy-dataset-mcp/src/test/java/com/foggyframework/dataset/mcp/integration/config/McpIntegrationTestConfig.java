package com.foggyframework.dataset.mcp.integration.config;

import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.mcp.tools.ComposeScriptTool;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

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

    private static final byte[] TEST_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII=");

    /**
     * 创建测试用 chartRenderWebClient
     *
     * 图表服务在集成测试中暂不测试，但需要返回稳定的图片字节，避免 AI 选择 export_with_chart
     * 时因裸 Mockito WebClient 的 post() 返回 null 而污染实测日志。
     */
    @Bean
    @Primary
    public WebClient chartRenderWebClient() {
        log.info("Creating stub chartRenderWebClient for integration tests");
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.IMAGE_PNG_VALUE)
                        .body(Flux.just(new DefaultDataBufferFactory().wrap(TEST_PNG)))
                        .build()))
                .build();
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

    /**
     * Enables ComposeScriptTool in embedded-mode integration tests.
     */
    @Bean("composeAuthorityResolverFactory")
    public Function<ToolExecutionContext, AuthorityResolver> composeAuthorityResolverFactory() {
        return toolContext -> request -> {
            Map<String, ModelBinding> bindings = new LinkedHashMap<>();
            for (String modelName : request.modelNames()) {
                bindings.put(modelName, ModelBinding.builder().build());
            }
            return AuthorityResolution.builder().bindings(bindings).build();
        };
    }

    @Bean
    @ConditionalOnMissingBean(ComposeScriptTool.class)
    public ComposeScriptTool composeScriptTool(
            SemanticQueryServiceV3 semanticQueryServiceV3,
            @Qualifier("composeAuthorityResolverFactory")
            Function<ToolExecutionContext, AuthorityResolver> resolverFactory,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect) {
        return new ComposeScriptTool(semanticQueryServiceV3, resolverFactory, defaultDialect);
    }
}

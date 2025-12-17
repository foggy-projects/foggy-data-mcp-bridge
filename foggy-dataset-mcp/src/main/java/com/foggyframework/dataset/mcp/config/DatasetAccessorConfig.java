package com.foggyframework.dataset.mcp.config;

import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import com.foggyframework.dataset.mcp.spi.impl.LocalDatasetAccessor;
import com.foggyframework.dataset.mcp.spi.impl.RemoteDatasetAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * DatasetAccessor 配置类
 *
 * <p>根据配置 {@code mcp.dataset.access-mode} 选择不同的实现：
 * <ul>
 *   <li>local: 使用 LocalDatasetAccessor（直接调用 SemanticServiceV3）</li>
 *   <li>remote: 使用 RemoteDatasetAccessor（通过 WebClient 调用）</li>
 * </ul>
 *
 * <h3>模式选择指南：</h3>
 * <ul>
 *   <li><b>local模式</b>：适用于单体应用或开发测试，直接在同一 JVM 中调用服务</li>
 *   <li><b>remote模式</b>：适用于微服务架构，MCP 服务与数据服务分离部署</li>
 * </ul>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class DatasetAccessorConfig {

    /**
     * 本地模式 DatasetAccessor
     *
     * <p>当 {@code mcp.dataset.access-mode=local} 时激活。
     *
     * <p>配置依赖：
     * <ul>
     *   <li>{@code mcp.semantic.model-list}: 可用模型列表</li>
     *   <li>{@code mcp.semantic.metadata}: 元数据查询的字段级别配置</li>
     *   <li>{@code mcp.semantic.internal}: 模型描述的字段级别配置</li>
     * </ul>
     *
     * @param semanticServiceResolver 语义服务解析器
     * @param mcpProperties           MCP 配置属性
     * @return 本地数据集访问器
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "mcp.dataset.access-mode", havingValue = "local")
    public DatasetAccessor localDatasetAccessor(
            SemanticServiceResolver semanticServiceResolver,
            McpProperties mcpProperties) {

        log.info("============================================");
        log.info("MCP Dataset Access Mode: LOCAL");
        log.info("直接调用 SemanticServiceV3，无需远程 HTTP 调用");
        log.info("可用模型: {}", mcpProperties.getSemantic().getModelList());
        log.info("============================================");

        return new LocalDatasetAccessor(semanticServiceResolver, mcpProperties);
    }

    /**
     * 远程模式 DatasetAccessor
     *
     * 当 mcp.dataset.access-mode=remote 或未配置时激活（默认）
     */
    @Bean
    @ConditionalOnMissingBean(DatasetAccessor.class)
    public DatasetAccessor remoteDatasetAccessor(
            @Qualifier("datasetQueryWebClient") WebClient datasetQueryWebClient,
            McpProperties mcpProperties) {

        String baseUrl = mcpProperties.getExternal().getDatasetQuery().getBaseUrl();

        log.info("============================================");
        log.info("MCP Dataset Access Mode: REMOTE");
        log.info("通过 HTTP 调用远程服务: {}", baseUrl);
        log.info("============================================");

        return new RemoteDatasetAccessor(datasetQueryWebClient);
    }
}

package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.dataset.jdbc.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.jdbc.model.semantic.service.SemanticQueryServiceV3;
import com.foggyframework.dataset.jdbc.model.semantic.service.SemanticServiceV3;
import com.foggyframework.dataset.mcp.spi.SemanticServiceResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 语义服务解析器实现
 *
 * <p>统一调用 V3 版本的语义服务。
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
@Component
public class SemanticServiceResolverImpl implements SemanticServiceResolver {

    private final SemanticServiceV3 semanticServiceV3;
    private final SemanticQueryServiceV3 semanticQueryServiceV3;

    public SemanticServiceResolverImpl(
            SemanticServiceV3 semanticServiceV3,
            SemanticQueryServiceV3 semanticQueryServiceV3) {
        this.semanticServiceV3 = semanticServiceV3;
        this.semanticQueryServiceV3 = semanticQueryServiceV3;
    }

    @PostConstruct
    public void init() {
        log.info("SemanticServiceResolver initialized (V3)");
    }

    @Override
    public SemanticMetadataResponse getMetadata(SemanticMetadataRequest request, String format) {
        log.debug("Using SemanticServiceV3 for metadata generation");
        return semanticServiceV3.getMetadata(request, format);
    }

    @Override
    public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode) {
        return queryModel(model, request, mode, null);
    }

    @Override
    public SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                            ModelResultContext.SecurityContext securityContext) {
        log.debug("Using SemanticQueryServiceV3 for query execution");
        return semanticQueryServiceV3.queryModel(model, request, mode, securityContext);
    }
}

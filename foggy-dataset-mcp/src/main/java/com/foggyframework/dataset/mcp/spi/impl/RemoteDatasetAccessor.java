package com.foggyframework.dataset.mcp.spi.impl;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.jdbc.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.mcp.spi.DatasetAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * 远程数据集访问实现
 *
 * <p>通过 HTTP WebClient 调用远程 foggy-dataset-model 服务，
 * 适用于微服务架构部署场景。
 *
 * <p><strong>注意：此实现暂不完善，当前优先使用 {@link LocalDatasetAccessor}。</strong>
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteDatasetAccessor implements DatasetAccessor {

    private final WebClient datasetQueryWebClient;

    @Override
    public RX<SemanticMetadataResponse> getMetadata(String traceId, String authorization) {
        // TODO: 暂不实现，优先使用 LocalDatasetAccessor
        log.warn("[Remote] getMetadata not implemented yet, please use LocalDatasetAccessor. traceId={}", traceId);
        return RX.failB("RemoteDatasetAccessor.getMetadata 暂未实现，请使用 local 模式");
    }

    @Override
    public RX<SemanticMetadataResponse> describeModel(String model, String format, String traceId, String authorization) {
        // TODO: 暂不实现，优先使用 LocalDatasetAccessor
        log.warn("[Remote] describeModel not implemented yet, please use LocalDatasetAccessor. model={}, traceId={}", model, traceId);
        return RX.failB("RemoteDatasetAccessor.describeModel 暂未实现，请使用 local 模式");
    }

    @Override
    public RX<SemanticQueryResponse> queryModel(String model, Map<String, Object> payload, String mode,
                             String traceId, String authorization) {
        // TODO: 暂不实现，优先使用 LocalDatasetAccessor
        log.warn("[Remote] queryModel not implemented yet, please use LocalDatasetAccessor. model={}, traceId={}", model, traceId);
        return RX.failB("RemoteDatasetAccessor.queryModel 暂未实现，请使用 local 模式");
    }

    @Override
    public String getAccessMode() {
        return "remote";
    }
}

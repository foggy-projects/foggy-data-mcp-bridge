package com.foggyframework.dataset.mcp.spi;

import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;

/**
 * 语义服务解析器
 *
 * <p>统一接口，调用 V3 版本的服务实现。
 *
 * @author foggy-dataset-mcp
 * @since 1.0.0
 */
public interface SemanticServiceResolver {

    /**
     * 获取元数据
     *
     * @param request 元数据请求
     * @param format  输出格式（json/markdown）
     * @return 元数据响应
     */
    SemanticMetadataResponse getMetadata(SemanticMetadataRequest request, String format);

    /**
     * 执行查询
     *
     * @param model   模型名称
     * @param request 查询请求
     * @param mode    执行模式（execute/validate）
     * @return 查询响应
     */
    SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode);

    /**
     * 执行查询（带安全上下文）
     *
     * @param model           模型名称
     * @param request         查询请求
     * @param mode            执行模式（execute/validate）
     * @param securityContext 安全上下文（授权信息）
     * @return 查询响应
     */
    default SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                             ModelResultContext.SecurityContext securityContext) {
        return queryModel(model, request, mode);
    }
}

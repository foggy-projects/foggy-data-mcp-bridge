package com.foggyframework.dataset.mcp.spi;

import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;

import java.util.List;

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
     * 获取元数据（带命名空间）
     *
     * @param request   元数据请求
     * @param format    输出格式（json/markdown）
     * @param namespace 命名空间（可选，用于多环境模型隔离）
     * @return 元数据响应
     */
    default SemanticMetadataResponse getMetadata(SemanticMetadataRequest request, String format, String namespace) {
        return getMetadata(request, format);
    }

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

    /**
     * 执行查询（带安全上下文和命名空间）
     *
     * @param model           模型名称
     * @param request         查询请求
     * @param mode            执行模式（execute/validate）
     * @param securityContext 安全上下文（授权信息）
     * @param namespace       命名空间（可选，用于多环境模型隔离）
     * @return 查询响应
     */
    default SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                             ModelResultContext.SecurityContext securityContext, String namespace) {
        return queryModel(model, request, mode, securityContext);
    }

    /**
     * 获取所有可用的模型名称（动态发现）
     *
     * <p>扫描所有 bundle 中的 .qm 文件，返回有效的模型名称列表。
     * 实现类应使用缓存机制，通过 {@link #invalidateModelCache()} 失效。
     *
     * @return 模型名称列表
     */
    List<String> getAllModelNames();

    /**
     * 清除模型名称缓存
     *
     * <p>当 QM 文件发生变化时调用此方法，下次 {@link #getAllModelNames()}
     * 调用将重新扫描文件系统。
     */
    default void invalidateModelCache() {
        // 默认空实现，子类可覆盖
    }
}

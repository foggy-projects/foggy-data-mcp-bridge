package com.foggyframework.dataset.mcp.spi;

import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.db.model.semantic.domain.SemanticRequestContext;

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
     * @param context 请求上下文（命名空间 + 安全信息），不可为 null
     * @return 元数据响应
     */
    SemanticMetadataResponse getMetadata(SemanticMetadataRequest request, String format,
                                         SemanticRequestContext context);

    /**
     * 执行查询
     *
     * @param model   模型名称
     * @param request 查询请求
     * @param mode    执行模式（execute/validate）
     * @param context 请求上下文（命名空间 + 安全信息），不可为 null
     * @return 查询响应
     */
    SemanticQueryResponse queryModel(String model, SemanticQueryRequest request, String mode,
                                     SemanticRequestContext context);

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

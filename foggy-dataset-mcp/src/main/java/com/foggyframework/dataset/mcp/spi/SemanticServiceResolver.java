package com.foggyframework.dataset.mcp.spi;

import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticMetadataResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;

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
     * <p>生命周期感知实现从当前 {@code NamespaceContext} 对应的 immutable
     * catalog snapshot 读取。兼容实现可以动态扫描，但不得建立第二套全局
     * names/alias authority。
     *
     * @return 模型名称列表
     */
    List<String> getAllModelNames();

    /**
     * 获取指定 namespace 下可见的模型名称。
     *
     * <p>默认实现保持历史兼容；生命周期感知实现必须读取指定 namespace 的同一
     * catalog snapshot，不能从无 namespace 的全局列表二次过滤。</p>
     *
     * @param namespace 命名空间；null 或空字符串表示底层默认命名空间
     * @return 模型名称列表
     */
    default List<String> getAllModelNames(String namespace) {
        return getAllModelNames();
    }

    /**
     * 兼容的模型名称失效入口
     *
     * <p>共享 lifecycle catalog 的实现应保持 no-op；source mutation 由核心
     * authority 发布新 snapshot。该方法只为旧调用方保留。
     */
    default void invalidateModelCache() {
        // 默认空实现，子类可覆盖
    }
}

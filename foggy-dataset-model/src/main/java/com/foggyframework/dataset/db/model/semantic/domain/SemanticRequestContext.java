package com.foggyframework.dataset.db.model.semantic.domain;

import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;

/**
 * 语义请求上下文 -- 携带跨切面关注点（namespace + 安全上下文）。
 *
 * <p>将 namespace 和 securityContext 统一为单一参数，
 * 消除语义服务接口中因可选参数组合导致的重载膨胀。</p>
 *
 * <p>两个字段均可为 null：</p>
 * <ul>
 *   <li>{@code namespace} -- null 表示使用默认命名空间</li>
 *   <li>{@code securityContext} -- null 表示无认证/安全信息</li>
 * </ul>
 *
 * @since 8.2.0
 */
public class SemanticRequestContext {

    private static final SemanticRequestContext EMPTY = new SemanticRequestContext(null, null);

    private final String namespace;
    private final ModelResultContext.SecurityContext securityContext;

    private SemanticRequestContext(String namespace, ModelResultContext.SecurityContext securityContext) {
        this.namespace = namespace;
        this.securityContext = securityContext;
    }

    /** 空上下文 -- 无命名空间、无安全信息 */
    public static SemanticRequestContext empty() {
        return EMPTY;
    }

    /** 仅命名空间 */
    public static SemanticRequestContext ofNamespace(String namespace) {
        if (namespace == null) {
            return EMPTY;
        }
        return new SemanticRequestContext(namespace, null);
    }

    /** 从 authorization 字符串自动构建 SecurityContext */
    public static SemanticRequestContext of(String namespace, String authorization) {
        ModelResultContext.SecurityContext sc = null;
        if (authorization != null && !authorization.isEmpty()) {
            sc = ModelResultContext.SecurityContext.fromAuthorization(authorization);
        }
        return new SemanticRequestContext(namespace, sc);
    }

    /** 显式传入 SecurityContext */
    public static SemanticRequestContext of(String namespace, ModelResultContext.SecurityContext securityContext) {
        return new SemanticRequestContext(namespace, securityContext);
    }

    public String getNamespace() {
        return namespace;
    }

    public ModelResultContext.SecurityContext getSecurityContext() {
        return securityContext;
    }

    /** 便捷方法：委托给 securityContext.getAuthorization() */
    public String getAuthorization() {
        return securityContext != null ? securityContext.getAuthorization() : null;
    }

    @Override
    public String toString() {
        return "SemanticRequestContext{namespace='" + namespace + "'}";
    }
}

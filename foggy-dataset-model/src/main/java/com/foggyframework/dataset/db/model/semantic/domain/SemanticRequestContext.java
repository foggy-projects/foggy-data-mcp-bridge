package com.foggyframework.dataset.db.model.semantic.domain;

import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 语义请求上下文 -- 携带跨切面关注点（namespace + 安全上下文 + 列权限）。
 *
 * <p>将 namespace、securityContext、fieldAccess 和 deniedColumns 统一为单一参数，
 * 消除语义服务接口中因可选参数组合导致的重载膨胀。</p>
 *
 * <p>所有字段均可为 null：</p>
 * <ul>
 *   <li>{@code namespace} -- null 表示使用默认命名空间</li>
 *   <li>{@code securityContext} -- null 表示无认证/安全信息</li>
 *   <li>{@code fieldAccess} -- null 表示不限制 QM 字段访问</li>
 *   <li>{@code deniedColumns} -- null 表示不限制物理列访问</li>
 * </ul>
 *
 * <p>fieldAccess 和 deniedColumns 是两套独立的列权限机制，可并存：</p>
 * <ul>
 *   <li>{@code fieldAccess} -- QM 字段名白名单（在 beforeQuery 阶段检查）</li>
 *   <li>{@code deniedColumns} -- 物理列黑名单（在 SQL 构建后、执行前检查）</li>
 * </ul>
 *
 * @since 8.2.0
 */
public class SemanticRequestContext {

    private static final SemanticRequestContext EMPTY = new SemanticRequestContext(null, null, null, null);

    private final String namespace;
    private final ModelResultContext.SecurityContext securityContext;
    private final Set<String> fieldAccess;
    private final List<DeniedPhysicalColumn> deniedColumns;

    private SemanticRequestContext(String namespace, ModelResultContext.SecurityContext securityContext,
                                   Set<String> fieldAccess, List<DeniedPhysicalColumn> deniedColumns) {
        this.namespace = namespace;
        this.securityContext = securityContext;
        // 防御性复制：防止调用方在创建后修改 mutable Set 导致权限绕过
        this.fieldAccess = fieldAccess != null ? Collections.unmodifiableSet(Set.copyOf(fieldAccess)) : null;
        this.deniedColumns = deniedColumns != null ? List.copyOf(deniedColumns) : null;
    }

    /** 空上下文 -- 无命名空间、无安全信息、无列权限限制 */
    public static SemanticRequestContext empty() {
        return EMPTY;
    }

    /** 仅命名空间 */
    public static SemanticRequestContext ofNamespace(String namespace) {
        if (namespace == null) {
            return EMPTY;
        }
        return new SemanticRequestContext(namespace, null, null, null);
    }

    /** 从 authorization 字符串自动构建 SecurityContext */
    public static SemanticRequestContext of(String namespace, String authorization) {
        ModelResultContext.SecurityContext sc = null;
        if (authorization != null && !authorization.isEmpty()) {
            sc = ModelResultContext.SecurityContext.fromAuthorization(authorization);
        }
        return new SemanticRequestContext(namespace, sc, null, null);
    }

    /** 显式传入 SecurityContext */
    public static SemanticRequestContext of(String namespace, ModelResultContext.SecurityContext securityContext) {
        return new SemanticRequestContext(namespace, securityContext, null, null);
    }

    /**
     * 构造：namespace + securityContext + fieldAccess（QM 字段名白名单）
     *
     * @param namespace       命名空间（可 null）
     * @param securityContext  安全上下文（可 null）
     * @param fieldAccess     运行时 QM 列权限白名单（null 表示不限制）
     * @since 8.2.0
     */
    public static SemanticRequestContext of(String namespace, ModelResultContext.SecurityContext securityContext,
                                            Set<String> fieldAccess) {
        return new SemanticRequestContext(namespace, securityContext, fieldAccess, null);
    }

    /**
     * 构造：namespace + securityContext + deniedColumns（物理列黑名单）
     *
     * @param namespace       命名空间（可 null）
     * @param securityContext  安全上下文（可 null）
     * @param deniedColumns   受限物理列黑名单（null 表示不限制）
     * @since 8.2.0
     */
    public static SemanticRequestContext ofDeniedColumns(String namespace,
                                                         ModelResultContext.SecurityContext securityContext,
                                                         List<DeniedPhysicalColumn> deniedColumns) {
        return new SemanticRequestContext(namespace, securityContext, null, deniedColumns);
    }

    /**
     * 完整构造：所有权限参数
     *
     * @param namespace       命名空间（可 null）
     * @param securityContext  安全上下文（可 null）
     * @param fieldAccess     QM 字段名白名单（null 表示不限制）
     * @param deniedColumns   物理列黑名单（null 表示不限制）
     * @since 8.2.0
     */
    public static SemanticRequestContext of(String namespace, ModelResultContext.SecurityContext securityContext,
                                            Set<String> fieldAccess, List<DeniedPhysicalColumn> deniedColumns) {
        return new SemanticRequestContext(namespace, securityContext, fieldAccess, deniedColumns);
    }

    public String getNamespace() {
        return namespace;
    }

    public ModelResultContext.SecurityContext getSecurityContext() {
        return securityContext;
    }

    /**
     * 获取运行时列权限白名单
     *
     * @return 允许访问的字段名集合；null 表示不限制
     * @since 8.2.0
     */
    public Set<String> getFieldAccess() {
        return fieldAccess;
    }

    /**
     * 获取受限物理列黑名单
     *
     * @return 受限列列表；null 表示不限制
     * @since 8.2.0
     */
    public List<DeniedPhysicalColumn> getDeniedColumns() {
        return deniedColumns;
    }

    /** 便捷方法：委托给 securityContext.getAuthorization() */
    public String getAuthorization() {
        return securityContext != null ? securityContext.getAuthorization() : null;
    }

    @Override
    public String toString() {
        return "SemanticRequestContext{namespace='" + namespace + "'" +
                (fieldAccess != null ? ", fieldAccess=" + fieldAccess.size() + " fields" : "") +
                (deniedColumns != null ? ", deniedColumns=" + deniedColumns.size() + " cols" : "") + "}";
    }
}

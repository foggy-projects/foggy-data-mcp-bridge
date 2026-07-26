package com.foggyframework.dataset.model.semantic.domain;

import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;

import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.semantic.permission.PermissionEvaluationSession;
import com.foggyframework.dataset.model.semantic.permission.PermissionAction;
import com.foggyframework.dataset.model.semantic.permission.RequestIdentity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final String namespace;
    private final ModelResultContext.SecurityContext securityContext;
    private final RequestIdentity requestIdentity;
    private final PermissionEvaluationSession permissionSession;
    private final Set<String> fieldAccess;
    private final List<DeniedPhysicalColumn> deniedColumns;
    private final List<SliceRequestDef> systemSlice;
    private final List<DomainTransportPlanSpec> domainTransportPlans;
    private final CatalogResolution<QueryModel> catalogResolution;
    private PermissionAction permissionAction;

    private SemanticRequestContext(String namespace, ModelResultContext.SecurityContext securityContext,
                                   Set<String> fieldAccess, List<DeniedPhysicalColumn> deniedColumns,
                                   List<SliceRequestDef> systemSlice,
                                   List<? extends DomainTransportPlanSpec> domainTransportPlans,
                                   CatalogResolution<QueryModel> catalogResolution,
                                   RequestIdentity requestIdentity,
                                   PermissionEvaluationSession permissionSession) {
        this.namespace = namespace;
        this.securityContext = securityContext;
        String authorization = securityContext != null ? securityContext.getAuthorization() : null;
        this.requestIdentity = requestIdentity != null
                ? requestIdentity
                : RequestIdentity.fromAuthorization(authorization);
        this.permissionSession = permissionSession != null
                ? permissionSession
                : new PermissionEvaluationSession();
        this.fieldAccess = fieldAccess != null ? Collections.unmodifiableSet(Set.copyOf(fieldAccess)) : null;
        this.deniedColumns = deniedColumns != null ? List.copyOf(deniedColumns) : null;
        this.systemSlice = systemSlice != null ? List.copyOf(systemSlice) : null;
        this.domainTransportPlans = domainTransportPlans != null ? List.copyOf(domainTransportPlans) : null;
        this.catalogResolution = catalogResolution;
    }

    /** 空上下文 -- 无命名空间、无安全信息、无列权限限制 */
    public static SemanticRequestContext empty() {
        return new SemanticRequestContext(null, null, null, null, null, null, null,
                RequestIdentity.anonymous(), new PermissionEvaluationSession());
    }

    /** 仅命名空间 */
    public static SemanticRequestContext ofNamespace(String namespace) {
        return new SemanticRequestContext(namespace, null, null, null, null, null, null,
                RequestIdentity.anonymous(), new PermissionEvaluationSession());
    }

    /** 从 authorization 字符串自动构建 SecurityContext */
    public static SemanticRequestContext of(String namespace, String authorization) {
        ModelResultContext.SecurityContext sc = null;
        if (authorization != null && !authorization.isEmpty()) {
            sc = ModelResultContext.SecurityContext.fromAuthorization(authorization);
        }
        RequestIdentity identity = RequestIdentity.fromAuthorization(authorization);
        return new SemanticRequestContext(namespace, sc, null, null, null, null, null,
                identity, new PermissionEvaluationSession());
    }

    /** 显式传入 SecurityContext */
    public static SemanticRequestContext of(String namespace, ModelResultContext.SecurityContext securityContext) {
        return new SemanticRequestContext(namespace, securityContext, null, null, null, null, null,
                null, new PermissionEvaluationSession());
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
        return new SemanticRequestContext(namespace, securityContext, fieldAccess, null, null, null, null,
                null, new PermissionEvaluationSession());
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
        return new SemanticRequestContext(namespace, securityContext, null, deniedColumns, null, null, null,
                null, new PermissionEvaluationSession());
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
        return new SemanticRequestContext(namespace, securityContext, fieldAccess, deniedColumns, null, null, null,
                null, new PermissionEvaluationSession());
    }

    /**
     * 完整构造：所有参数（含 systemSlice）
     *
     * @param namespace       命名空间（可 null）
     * @param securityContext  安全上下文（可 null）
     * @param fieldAccess     QM 字段名白名单（null 表示不限制）
     * @param deniedColumns   物理列黑名单（null 表示不限制）
     * @param systemSlice     系统注入的 slice 条件（绕过权限检查，如 ir.rule）
     * @since 8.2.0
     */
    public static SemanticRequestContext of(String namespace, ModelResultContext.SecurityContext securityContext,
                                            Set<String> fieldAccess, List<DeniedPhysicalColumn> deniedColumns,
                                            List<SliceRequestDef> systemSlice) {
        return new SemanticRequestContext(namespace, securityContext, fieldAccess, deniedColumns, systemSlice, null, null,
                null, new PermissionEvaluationSession());
    }

    public String getNamespace() {
        return namespace;
    }

    public ModelResultContext.SecurityContext getSecurityContext() {
        return securityContext;
    }

    public RequestIdentity getRequestIdentity() {
        return requestIdentity;
    }

    public PermissionEvaluationSession getPermissionSession() {
        return permissionSession;
    }

    public PermissionAction getPermissionAction() {
        return permissionAction;
    }

    /**
     * Returns a request-local view for one explicit model action.
     */
    public SemanticRequestContext withPermissionAction(PermissionAction action) {
        Objects.requireNonNull(action, "action");
        SemanticRequestContext copy = copy();
        copy.permissionAction = action;
        return copy;
    }

    /**
     * Adds resolver-controlled attributes for downstream field permissions
     * without changing the opaque request identity.
     */
    public SemanticRequestContext withPermissionAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return this;
        }
        ModelResultContext.SecurityContext next =
                ModelResultContext.SecurityContext.fromAuthorization(getAuthorization());
        if (securityContext != null) {
            next.setUserId(securityContext.getUserId());
            next.setRoles(securityContext.getRoles());
            next.setTenantId(securityContext.getTenantId());
            next.setDeptId(securityContext.getDeptId());
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (securityContext != null && securityContext.getAttributes() != null) {
            merged.putAll(securityContext.getAttributes());
        }
        merged.putAll(attributes);
        next.setAttributes(Collections.unmodifiableMap(merged));
        SemanticRequestContext copy = new SemanticRequestContext(
                namespace, next, fieldAccess, deniedColumns, systemSlice,
                domainTransportPlans, catalogResolution, requestIdentity, permissionSession);
        copy.permissionAction = permissionAction;
        return copy;
    }

    /**
     * Returns a per-model governance view while preserving the exact opaque
     * identity, request-local permission session, namespace, and lifecycle pin.
     */
    public SemanticRequestContext withGovernance(
            Set<String> nextFieldAccess,
            List<DeniedPhysicalColumn> nextDeniedColumns,
            List<SliceRequestDef> nextSystemSlice
    ) {
        SemanticRequestContext copy = new SemanticRequestContext(
                namespace,
                securityContext,
                nextFieldAccess,
                nextDeniedColumns,
                nextSystemSlice,
                domainTransportPlans,
                catalogResolution,
                requestIdentity,
                permissionSession
        );
        copy.permissionAction = permissionAction;
        return copy;
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

    /**
     * 获取系统注入的 slice 条件（绕过字段权限校验）
     *
     * @return 系统 slice 列表；null 表示无系统 slice
     * @since 8.2.0
     */
    public List<SliceRequestDef> getSystemSlice() {
        return systemSlice;
    }

    /**
     * 获取 Pivot 内部大域传输计划。
     *
     * @return domain transport plans；null 表示无大域传输
     * @since 9.1.0
     */
    @SuppressWarnings("unchecked")
    public List<DomainTransportPlan> getDomainTransportPlans() {
        // The compatibility write path below only accepts the legacy Pivot implementation.
        return (List<DomainTransportPlan>) (List<?>) domainTransportPlans;
    }

    /**
     * Returns the engine-neutral view used by semantic orchestration.
     *
     * <p>{@link #getDomainTransportPlans()} remains available for one
     * compatibility cycle so existing Pivot callers keep their source and
     * binary contract.</p>
     *
     * @return immutable transport-plan specifications, or {@code null}
     * @since 9.4.0
     */
    public List<DomainTransportPlanSpec> getDomainTransportPlanSpecs() {
        return domainTransportPlans;
    }

    /**
     * Exact immutable model/catalog projection selected at the outer semantic entry.
     * Downstream execution contexts use this pin to reject a mid-request generation switch.
     */
    public CatalogResolution<QueryModel> getCatalogResolution() {
        return catalogResolution;
    }

    /**
     * Return a context carrying one lifecycle pin. Re-applying the exact same
     * projection is idempotent; replacing it with another projection is rejected.
     */
    public SemanticRequestContext withCatalogResolution(CatalogResolution<QueryModel> resolution) {
        Objects.requireNonNull(resolution, "catalog resolution");
        String expectedNamespace = CatalogIdentity.canonicalNamespace(namespace);
        if (!expectedNamespace.equals(resolution.catalogIdentity().namespace())) {
            throw new IllegalArgumentException("catalog resolution namespace mismatch");
        }
        if (catalogResolution != null && !sameCatalogResolution(catalogResolution, resolution)) {
            throw new IllegalStateException("CONFLICTING_SEMANTIC_CATALOG_REPIN");
        }
        if (catalogResolution != null) {
            return this;
        }
        SemanticRequestContext copy = new SemanticRequestContext(namespace, securityContext, fieldAccess, deniedColumns,
                systemSlice, domainTransportPlans, resolution, requestIdentity, permissionSession);
        copy.permissionAction = permissionAction;
        return copy;
    }

    /**
     * 返回带有 Pivot 内部大域传输计划的新上下文，保留原有权限和 systemSlice。
     *
     * @param domainTransportPlans 大域传输计划；null 或空列表表示清空
     * @return 新上下文
     * @since 9.1.0
     */
    public SemanticRequestContext withDomainTransportPlans(List<DomainTransportPlan> domainTransportPlans) {
        List<DomainTransportPlan> plans = domainTransportPlans != null && !domainTransportPlans.isEmpty()
                ? domainTransportPlans
                : null;
        SemanticRequestContext copy = new SemanticRequestContext(namespace, securityContext, fieldAccess, deniedColumns,
                systemSlice, plans, catalogResolution, requestIdentity, permissionSession);
        copy.permissionAction = permissionAction;
        return copy;
    }

    private SemanticRequestContext copy() {
        SemanticRequestContext copy = new SemanticRequestContext(
                namespace, securityContext, fieldAccess, deniedColumns, systemSlice,
                domainTransportPlans, catalogResolution, requestIdentity, permissionSession);
        copy.permissionAction = permissionAction;
        return copy;
    }

    private static boolean sameCatalogResolution(CatalogResolution<QueryModel> left,
                                                 CatalogResolution<QueryModel> right) {
        return left.model() == right.model()
                && Objects.equals(left.canonicalName(), right.canonicalName())
                && Objects.equals(left.catalogIdentity(), right.catalogIdentity())
                && Objects.equals(left.dependencyBindings(), right.dependencyBindings())
                && left.bindingIdentityComplete() == right.bindingIdentityComplete();
    }

    /** 便捷方法：委托给 securityContext.getAuthorization() */
    public String getAuthorization() {
        return requestIdentity.authorization();
    }

    @Override
    public String toString() {
        return "SemanticRequestContext{namespace='" + namespace + "'" +
                (fieldAccess != null ? ", fieldAccess=" + fieldAccess.size() + " fields" : "") +
                (deniedColumns != null ? ", deniedColumns=" + deniedColumns.size() + " cols" : "") +
                (domainTransportPlans != null ? ", domainTransportPlans=" + domainTransportPlans.size() : "") +
                (catalogResolution != null ? ", catalogPinned=true" : "") + "}";
    }
}

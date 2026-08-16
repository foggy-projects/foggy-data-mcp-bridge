package com.foggyframework.dataset.model.semantic.domain;

import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportField;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportPlan;
import com.foggyframework.dataset.model.engine.pivot.transport.DomainTransportTuple;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.semantic.explain.ExplainTraceCollector;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SemanticRequestContext 单元测试
 */
@DisplayName("SemanticRequestContext")
class SemanticRequestContextTest {

    @Test
    @DisplayName("domain transport 使用语义层中立契约并兼容 Pivot 实现")
    void testDomainTransportBoundaryContract() throws NoSuchFieldException, NoSuchMethodException {
        assertTrue(DomainTransportPlanSpec.class.isAssignableFrom(DomainTransportPlan.class));

        Field plans = SemanticRequestContext.class.getDeclaredField("domainTransportPlans");
        ParameterizedType listType = assertInstanceOf(ParameterizedType.class, plans.getGenericType());
        assertEquals(DomainTransportPlanSpec.class, listType.getActualTypeArguments()[0]);

        Method compatibilityGetter = SemanticRequestContext.class.getMethod("getDomainTransportPlans");
        ParameterizedType compatibilityType = assertInstanceOf(
                ParameterizedType.class, compatibilityGetter.getGenericReturnType());
        assertEquals(DomainTransportPlan.class, compatibilityType.getActualTypeArguments()[0]);
    }

    @Test
    @DisplayName("empty() 返回 namespace 和 securityContext 均为 null 的实例")
    void testEmpty() {
        SemanticRequestContext ctx = SemanticRequestContext.empty();
        assertNotNull(ctx);
        assertNull(ctx.getNamespace());
        assertNull(ctx.getSecurityContext());
        assertNull(ctx.getAuthorization());
        assertNull(ctx.getExplainTraceCollector());
    }

    @Test
    @DisplayName("explain collector 在上下文派生过程中保持请求级同一实例")
    void explainCollectorSurvivesDerivedContexts() {
        ExplainTraceCollector collector = new ExplainTraceCollector();
        SemanticRequestContext explained = SemanticRequestContext.ofNamespace("sales")
                .withExplainTraceCollector(collector)
                .withPermissionAction(
                        com.foggyframework.dataset.model.semantic.permission.PermissionAction.EXECUTE)
                .withGovernance(Set.of("amount"), List.of(), List.of());

        assertSame(collector, explained.getExplainTraceCollector());
        assertTrue(explained.toString().contains("explainTrace=true"));
        assertThrows(IllegalStateException.class,
                () -> explained.withExplainTraceCollector(new ExplainTraceCollector()));
    }

    @Test
    @DisplayName("empty() 为每个请求创建独立权限会话")
    void testEmptySingleton() {
        SemanticRequestContext first = SemanticRequestContext.empty();
        SemanticRequestContext second = SemanticRequestContext.empty();

        assertNotSame(first, second);
        assertNotSame(first.getPermissionSession(), second.getPermissionSession());
    }

    @Test
    @DisplayName("ofNamespace(null) 仍创建独立权限会话")
    void testOfNamespaceNull() {
        SemanticRequestContext empty = SemanticRequestContext.empty();
        SemanticRequestContext namespaced = SemanticRequestContext.ofNamespace(null);

        assertNotSame(empty, namespaced);
        assertNotSame(empty.getPermissionSession(), namespaced.getPermissionSession());
    }

    @Test
    @DisplayName("ofNamespace 携带 namespace，securityContext 为 null")
    void testOfNamespace() {
        SemanticRequestContext ctx = SemanticRequestContext.ofNamespace("odoo");
        assertEquals("odoo", ctx.getNamespace());
        assertNull(ctx.getSecurityContext());
        assertNull(ctx.getAuthorization());
    }

    @Test
    @DisplayName("of(namespace, authorization) 自动构建 SecurityContext")
    void testOfWithAuthorization() {
        SemanticRequestContext ctx = SemanticRequestContext.of("odoo", "Bearer token123");
        assertEquals("odoo", ctx.getNamespace());
        assertNotNull(ctx.getSecurityContext());
        assertEquals("Bearer token123", ctx.getAuthorization());
        assertEquals("Bearer token123", ctx.getSecurityContext().getAuthorization());
    }

    @Test
    @DisplayName("of(namespace, null authorization) 时 securityContext 为 null")
    void testOfWithNullAuthorization() {
        SemanticRequestContext ctx = SemanticRequestContext.of("odoo", (String) null);
        assertEquals("odoo", ctx.getNamespace());
        assertNull(ctx.getSecurityContext());
        assertNull(ctx.getAuthorization());
    }

    @Test
    @DisplayName("of(namespace, empty authorization) 时 securityContext 为 null")
    void testOfWithEmptyAuthorization() {
        SemanticRequestContext ctx = SemanticRequestContext.of("odoo", "");
        assertEquals("odoo", ctx.getNamespace());
        assertNull(ctx.getSecurityContext());
    }

    @Test
    @DisplayName("of(namespace, securityContext) 显式传入 SecurityContext")
    void testOfWithSecurityContext() {
        ModelResultContext.SecurityContext sc = new ModelResultContext.SecurityContext();
        sc.setAuthorization("Bearer abc");
        sc.setUserId("user1");

        SemanticRequestContext ctx = SemanticRequestContext.of("test-ns", sc);
        assertEquals("test-ns", ctx.getNamespace());
        assertSame(sc, ctx.getSecurityContext());
        assertEquals("Bearer abc", ctx.getAuthorization());
        assertEquals("user1", ctx.getSecurityContext().getUserId());
    }

    @Test
    @DisplayName("of(null, securityContext) namespace 可为 null")
    void testOfWithNullNamespace() {
        ModelResultContext.SecurityContext sc = ModelResultContext.SecurityContext.fromAuthorization("Bearer xyz");
        SemanticRequestContext ctx = SemanticRequestContext.of(null, sc);
        assertNull(ctx.getNamespace());
        assertNotNull(ctx.getSecurityContext());
        assertEquals("Bearer xyz", ctx.getAuthorization());
    }

    @Test
    @DisplayName("toString 包含 namespace 信息")
    void testToString() {
        SemanticRequestContext ctx = SemanticRequestContext.ofNamespace("odoo");
        assertTrue(ctx.toString().contains("odoo"));
    }

    // ==========================================
    // fieldAccess 相关测试
    // ==========================================

    @Test
    @DisplayName("of(namespace, securityContext, fieldAccess) 传入列权限白名单")
    void testOfWithFieldAccess() {
        ModelResultContext.SecurityContext sc = new ModelResultContext.SecurityContext();
        sc.setAuthorization("Bearer token");
        Set<String> fieldAccess = Set.of("field1", "field2", "field3");

        SemanticRequestContext ctx = SemanticRequestContext.of("odoo", sc, fieldAccess);
        assertEquals("odoo", ctx.getNamespace());
        assertSame(sc, ctx.getSecurityContext());
        assertNotNull(ctx.getFieldAccess());
        assertEquals(3, ctx.getFieldAccess().size());
        assertTrue(ctx.getFieldAccess().contains("field1"));
        assertTrue(ctx.getFieldAccess().contains("field2"));
        assertTrue(ctx.getFieldAccess().contains("field3"));
    }

    @Test
    @DisplayName("of(namespace, securityContext, null fieldAccess) 返回 null")
    void testOfWithNullFieldAccess() {
        SemanticRequestContext ctx = SemanticRequestContext.of("odoo", (ModelResultContext.SecurityContext) null, null);
        assertEquals("odoo", ctx.getNamespace());
        assertNull(ctx.getFieldAccess());
    }

    @Test
    @DisplayName("empty() 的 fieldAccess 为 null（向后兼容）")
    void testEmptyFieldAccessIsNull() {
        assertNull(SemanticRequestContext.empty().getFieldAccess());
    }

    @Test
    @DisplayName("ofNamespace 的 fieldAccess 为 null")
    void testOfNamespaceFieldAccessIsNull() {
        assertNull(SemanticRequestContext.ofNamespace("odoo").getFieldAccess());
    }

    @Test
    @DisplayName("of(namespace, authorization) 的 fieldAccess 为 null")
    void testOfWithAuthFieldAccessIsNull() {
        assertNull(SemanticRequestContext.of("odoo", "Bearer token").getFieldAccess());
    }

    @Test
    @DisplayName("fieldAccess 防御性复制 — 修改原始 Set 不影响上下文")
    void testFieldAccessDefensiveCopy() {
        Set<String> mutable = new java.util.HashSet<>(Set.of("field1", "field2"));
        SemanticRequestContext ctx = SemanticRequestContext.of("ns", (ModelResultContext.SecurityContext) null, mutable);

        // 修改原始 Set
        mutable.add("field3");
        mutable.remove("field1");

        // 上下文中的 fieldAccess 不受影响
        assertEquals(2, ctx.getFieldAccess().size());
        assertTrue(ctx.getFieldAccess().contains("field1"), "应仍包含 field1");
        assertTrue(ctx.getFieldAccess().contains("field2"), "应仍包含 field2");
        assertFalse(ctx.getFieldAccess().contains("field3"), "不应包含后添加的 field3");
    }

    @Test
    @DisplayName("fieldAccess 不可变 — 尝试修改返回的 Set 抛异常")
    void testFieldAccessImmutable() {
        Set<String> fieldAccess = Set.of("field1", "field2");
        SemanticRequestContext ctx = SemanticRequestContext.of("ns", (ModelResultContext.SecurityContext) null, fieldAccess);

        assertThrows(UnsupportedOperationException.class,
                () -> ctx.getFieldAccess().add("field3"),
                "fieldAccess 应不可变");
    }

    @Test
    @DisplayName("toString 包含 fieldAccess 数量信息")
    void testToStringWithFieldAccess() {
        SemanticRequestContext ctx = SemanticRequestContext.of("odoo",
                (ModelResultContext.SecurityContext) null, Set.of("a", "b"));
        String str = ctx.toString();
        assertTrue(str.contains("fieldAccess="), "toString 应包含 fieldAccess 信息");
        assertTrue(str.contains("2 fields"), "toString 应包含字段数量");
    }

    @Test
    @DisplayName("CatalogResolution 为类型化附加上下文并由 domain transport 派生保留")
    void testCatalogResolutionPreservedByDomainTransportPlans() {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn("SalesQM");
        CatalogResolution<QueryModel> resolution = resolution(model, "catalog-a", "source-a");
        SemanticRequestContext pinned = SemanticRequestContext.ofNamespace("tenant-a")
                .withCatalogResolution(resolution);
        DomainTransportPlan plan = DomainTransportPlan.builder()
                .fields(List.of(new DomainTransportField("region")))
                .tuples(List.of(new DomainTransportTuple(List.of("east"))))
                .build();

        SemanticRequestContext transported = pinned.withDomainTransportPlans(List.of(plan));

        assertSame(resolution, pinned.getCatalogResolution());
        assertSame(resolution, transported.getCatalogResolution());
        assertEquals(List.of(plan), transported.getDomainTransportPlans());
        assertEquals(List.of(plan), transported.getDomainTransportPlanSpecs());
        assertSame(pinned, pinned.withCatalogResolution(new CatalogResolution<>(
                resolution.canonicalName(),
                resolution.model(),
                resolution.catalogIdentity(),
                resolution.dependencyBindings(),
                resolution.bindingIdentityComplete())));
        assertTrue(transported.toString().contains("catalogPinned=true"));
    }

    @Test
    @DisplayName("CatalogResolution 禁止 namespace 不一致与生命周期切换")
    void testCatalogResolutionRejectsNamespaceAndGenerationConflicts() {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn("SalesQM");
        CatalogResolution<QueryModel> first = resolution(model, "catalog-a", "source-a");
        CatalogResolution<QueryModel> switched = resolution(model, "catalog-b", "source-b");
        SemanticRequestContext pinned = SemanticRequestContext.ofNamespace("tenant-a")
                .withCatalogResolution(first);

        IllegalStateException switchedFailure = assertThrows(
                IllegalStateException.class,
                () -> pinned.withCatalogResolution(switched));
        IllegalArgumentException namespaceFailure = assertThrows(
                IllegalArgumentException.class,
                () -> SemanticRequestContext.ofNamespace("tenant-b")
                        .withCatalogResolution(first));

        assertEquals("CONFLICTING_SEMANTIC_CATALOG_REPIN", switchedFailure.getMessage());
        assertEquals("catalog resolution namespace mismatch", namespaceFailure.getMessage());
        assertSame(first, pinned.getCatalogResolution());
    }

    private CatalogResolution<QueryModel> resolution(QueryModel model,
                                                     String generation,
                                                     String sourceRevision) {
        return new CatalogResolution<>(
                "SalesQM",
                model,
                new CatalogIdentity(
                        "tenant-a",
                        new CatalogGeneration(generation),
                        new SourceRevision(sourceRevision)),
                Map.of(),
                true);
    }
}

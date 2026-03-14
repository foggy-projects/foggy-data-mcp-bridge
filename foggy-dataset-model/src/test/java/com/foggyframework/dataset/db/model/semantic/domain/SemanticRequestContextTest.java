package com.foggyframework.dataset.db.model.semantic.domain;

import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SemanticRequestContext 单元测试
 */
@DisplayName("SemanticRequestContext")
class SemanticRequestContextTest {

    @Test
    @DisplayName("empty() 返回 namespace 和 securityContext 均为 null 的实例")
    void testEmpty() {
        SemanticRequestContext ctx = SemanticRequestContext.empty();
        assertNotNull(ctx);
        assertNull(ctx.getNamespace());
        assertNull(ctx.getSecurityContext());
        assertNull(ctx.getAuthorization());
    }

    @Test
    @DisplayName("empty() 返回同一单例实例")
    void testEmptySingleton() {
        assertSame(SemanticRequestContext.empty(), SemanticRequestContext.empty());
    }

    @Test
    @DisplayName("ofNamespace(null) 返回 empty 单例")
    void testOfNamespaceNull() {
        assertSame(SemanticRequestContext.empty(), SemanticRequestContext.ofNamespace(null));
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
}

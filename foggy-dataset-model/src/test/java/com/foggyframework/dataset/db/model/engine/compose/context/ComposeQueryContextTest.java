package com.foggyframework.dataset.db.model.engine.compose.context;

import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 ComposeQueryContext 不变量 — 跨仓对齐 Python test_compose_query_context.py。
 */
@DisplayName("M1 ComposeQueryContext")
class ComposeQueryContextTest {

    private static final AuthorityResolver STUB_RESOLVER =
            request -> AuthorityResolution.builder().bindings(Map.of()).build();

    private static Principal makePrincipal() {
        return Principal.builder().userId("u001").tenantId("t001").build();
    }

    @Test
    @DisplayName("最小合法构造：三必填字段就绪")
    void minimalValidConstruction() {
        ComposeQueryContext ctx = ComposeQueryContext.builder()
                .principal(makePrincipal())
                .namespace("odoo")
                .authorityResolver(STUB_RESOLVER)
                .build();
        assertNotNull(ctx.principal());
        assertEquals("odoo", ctx.namespace());
        assertSame(STUB_RESOLVER, ctx.authorityResolver());
        assertNull(ctx.traceId());
        assertNull(ctx.params());
        assertNull(ctx.extensions());
    }

    @Test
    @DisplayName("principal 必填")
    void principalRequired() {
        assertThrows(NullPointerException.class,
                () -> ComposeQueryContext.builder()
                        .namespace("odoo")
                        .authorityResolver(STUB_RESOLVER)
                        .build());
    }

    @Test
    @DisplayName("namespace 必填且非空")
    void namespaceRequiredNonBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> ComposeQueryContext.builder()
                        .principal(makePrincipal())
                        .namespace("")
                        .authorityResolver(STUB_RESOLVER)
                        .build());
        assertThrows(IllegalArgumentException.class,
                () -> ComposeQueryContext.builder()
                        .principal(makePrincipal())
                        .namespace(null)
                        .authorityResolver(STUB_RESOLVER)
                        .build());
    }

    @Test
    @DisplayName("authorityResolver 必填 —— fail-closed 不容忍 null")
    void authorityResolverRequired() {
        assertThrows(NullPointerException.class,
                () -> ComposeQueryContext.builder()
                        .principal(makePrincipal())
                        .namespace("odoo")
                        .authorityResolver(null)
                        .build());
    }

    @Test
    @DisplayName("params 构造后为不可变快照")
    void paramsFrozenAfterConstruction() {
        Map<String, Object> src = new HashMap<>();
        src.put("orgId", "org001");
        ComposeQueryContext ctx = ComposeQueryContext.builder()
                .principal(makePrincipal())
                .namespace("odoo")
                .authorityResolver(STUB_RESOLVER)
                .params(src)
                .build();
        // 修改源 map 不影响 context 内部快照
        src.put("orgId", "org002");
        assertEquals("org001", ctx.params().get("orgId"),
                "ComposeQueryContext.params 必须对源 map 做快照");
        // context.params 本身不可变
        assertThrows(UnsupportedOperationException.class,
                () -> ctx.params().put("orgId", "org003"));
    }

    @Test
    @DisplayName("param() 在 params 为 null 时返回 defaultValue")
    void paramAccessorDefaultsWhenParamsNull() {
        ComposeQueryContext ctx = ComposeQueryContext.builder()
                .principal(makePrincipal())
                .namespace("odoo")
                .authorityResolver(STUB_RESOLVER)
                .build();
        assertNull(ctx.param("orgId", null));
        assertEquals("fallback", ctx.param("orgId", "fallback"));
    }

    @Test
    @DisplayName("param() 从 params 快照读值；缺失键返回 default")
    void paramAccessorReadsSnapshot() {
        ComposeQueryContext ctx = ComposeQueryContext.builder()
                .principal(makePrincipal())
                .namespace("odoo")
                .authorityResolver(STUB_RESOLVER)
                .params(Map.of("orgId", "org001", "deptId", "d1"))
                .build();
        assertEquals("org001", ctx.param("orgId", null));
        assertEquals("x", ctx.param("missing", "x"));
    }
}

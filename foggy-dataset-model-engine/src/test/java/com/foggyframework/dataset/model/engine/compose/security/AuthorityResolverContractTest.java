package com.foggyframework.dataset.model.engine.compose.security;

import com.foggyframework.dataset.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.model.engine.compose.context.Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M1 AuthorityResolver 接口 —— fake-resolver 契约 sanity 测试。
 * 跨仓对齐 Python test_authority_resolver_contract.py。
 */
@DisplayName("M1 AuthorityResolver")
class AuthorityResolverContractTest {

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    /** Good-citizen resolver: 为每个模型返回空 ModelBinding。 */
    private static final AuthorityResolver ECHO_RESOLVER = req -> {
        Map<String, ModelBinding> bindings = req.models().stream()
                .collect(Collectors.toMap(ModelQuery::model,
                        m -> ModelBinding.builder().build(),
                        (a, b) -> a, java.util.LinkedHashMap::new));
        return AuthorityResolution.builder().bindings(bindings).build();
    };

    /** 只返回第一个模型的 binding —— 契约违规（由调用方检测）。 */
    private static final AuthorityResolver PARTIAL_RESOLVER = req -> {
        ModelQuery first = req.models().get(0);
        return AuthorityResolution.builder()
                .bindings(Map.of(first.model(), ModelBinding.builder().build()))
                .build();
    };

    /** 总是抛出 AuthorityResolutionException 的 resolver。 */
    private static final AuthorityResolver RAISING_RESOLVER = req -> {
        throw new AuthorityResolutionException(
                AuthorityErrorCodes.UPSTREAM_FAILURE,
                "upstream offline",
                req.models().get(0).model(),
                AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE);
    };

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Principal principal() {
        return Principal.builder().userId("u001").tenantId("t001").build();
    }

    private static AuthorityRequest multiModelRequest() {
        return AuthorityRequest.builder()
                .principal(principal())
                .namespace("odoo")
                .models(List.of(
                        ModelQuery.builder().model("SaleOrderQM")
                                .tables(List.of("sale_order")).build(),
                        ModelQuery.builder().model("CrmLeadQM")
                                .tables(List.of("crm_lead")).build()))
                .build();
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("EchoResolver 满足 key-set 对齐契约")
    void echoResolverBindingsKeySetEqualsRequestModelSet() {
        AuthorityResolution resolution = ECHO_RESOLVER.resolve(multiModelRequest());
        assertEquals(java.util.Set.copyOf(multiModelRequest().modelNames()),
                resolution.bindings().keySet());
    }

    @Test
    @DisplayName("EchoResolver 每个 binding 默认集合均为空")
    void echoResolverDefaultEmptyCollections() {
        AuthorityResolution resolution = ECHO_RESOLVER.resolve(multiModelRequest());
        for (ModelBinding b : resolution.bindings().values()) {
            assertNull(b.fieldAccess());
            assertTrue(b.deniedColumns().isEmpty());
            assertTrue(b.systemSlice().isEmpty());
        }
    }

    @Test
    @DisplayName("PartialResolver 的不完整响应可被调用方通过 key-set 检测")
    void partialResponseDetectableByKeySetCheck() {
        AuthorityResolution resolution = PARTIAL_RESOLVER.resolve(multiModelRequest());
        java.util.Set<String> expected = java.util.Set.copyOf(multiModelRequest().modelNames());
        java.util.Set<String> actual = resolution.bindings().keySet();
        java.util.Set<String> missing = new java.util.HashSet<>(expected);
        missing.removeAll(actual);
        assertEquals(java.util.Set.of("CrmLeadQM"), missing,
                "PartialResolver 必须让第二个模型无 binding，"
                        + "以便调用方抛 MODEL_BINDING_MISSING");
    }

    @Test
    @DisplayName("RaisingResolver 的异常 fail-closed 向上传播")
    void raisingResolverPropagatesFailClosed() {
        AuthorityResolutionException ex = assertThrows(
                AuthorityResolutionException.class,
                () -> RAISING_RESOLVER.resolve(multiModelRequest()));
        assertEquals(AuthorityErrorCodes.UPSTREAM_FAILURE, ex.code());
        assertEquals("SaleOrderQM", ex.modelInvolved());
        assertEquals(AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE, ex.phase());
    }

    @Test
    @DisplayName("ComposeQueryContext 接受任意 AuthorityResolver 实现")
    void composeContextAcceptsAnyResolverImpl() {
        ComposeQueryContext ctx = ComposeQueryContext.builder()
                .principal(principal())
                .namespace("odoo")
                .authorityResolver(ECHO_RESOLVER)
                .build();
        assertSame(ECHO_RESOLVER, ctx.authorityResolver());
    }
}

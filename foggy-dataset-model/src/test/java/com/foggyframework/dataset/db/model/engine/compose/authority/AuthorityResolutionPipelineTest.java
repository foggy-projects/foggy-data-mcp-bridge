package com.foggyframework.dataset.db.model.engine.compose.authority;

import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.BadValueResolver;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.BoomResolver;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.EchoResolver;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.EmptyReturningProvider;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.ExtraKeyResolver;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.MissingKeyResolver;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.NullResponseResolver;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.RaisingResolver;
import com.foggyframework.dataset.db.model.engine.compose.authority.AuthorityTestDoubles.StaticTableProvider;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.plan.BaseModelPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.Dsl;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinOn;
import com.foggyframework.dataset.db.model.engine.compose.plan.JoinPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.QueryPlan;
import com.foggyframework.dataset.db.model.engine.compose.plan.UnionPlan;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityErrorCodes;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolutionException;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5 {@link AuthorityResolutionPipeline#resolve} — batch resolver plumbing
 * and fail-closed branches.
 *
 * <p>Mirrors Python {@code tests/compose/authority/test_resolve_authority_for_plan.py}.</p>
 */
@DisplayName("M5 AuthorityResolutionPipeline")
class AuthorityResolutionPipelineTest {

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Principal principal() {
        return Principal.builder().userId("u001").tenantId("t001")
                .roles(List.of("analyst")).build();
    }

    private static BaseModelPlan saleOrder() {
        return (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                .model("SaleOrderQM").columns(List.of("id", "amount")).build());
    }

    private static BaseModelPlan crmLead() {
        return (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                .model("CrmLeadQM").columns(List.of("id")).build());
    }

    private static BaseModelPlan partner() {
        return (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                .model("ResPartnerQM").columns(List.of("id", "name")).build());
    }

    private static ComposeQueryContext ctxFor(Principal p, EchoResolver resolver) {
        return ComposeQueryContext.builder()
                .principal(p).namespace("odoo").authorityResolver(resolver).build();
    }

    // ------------------------------------------------------------------
    // Happy path — single model
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("单模型 happy path")
    class SingleModel {

        @Test
        @DisplayName("单模型 round-trip")
        void singleModelRoundTrip() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            Map<String, ModelBinding> bindings =
                    AuthorityResolutionPipeline.resolve(saleOrder(), ctx);
            assertEquals(Map.of("SaleOrderQM", ModelBinding.builder().build()).keySet(),
                    bindings.keySet());
            assertNull(bindings.get("SaleOrderQM").fieldAccess());
        }

        @Test
        @DisplayName("resolver 正好被调用一次")
        void callsResolverExactlyOnce() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            AuthorityResolutionPipeline.resolve(saleOrder(), ctx);
            assertEquals(1, resolver.calls.size());
        }

        @Test
        @DisplayName("request 携带 principal 与 namespace")
        void requestCarriesPrincipalAndNamespace() {
            EchoResolver resolver = new EchoResolver();
            Principal p = principal();
            ComposeQueryContext ctx = ctxFor(p, resolver);
            AuthorityResolutionPipeline.resolve(saleOrder(), ctx);
            AuthorityRequest req = resolver.calls.get(0);
            assertSame(p, req.principal());
            assertEquals("odoo", req.namespace());
        }

        @Test
        @DisplayName("request 携带 traceId（当上下文提供时）")
        void requestCarriesTraceIdWhenSet() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(principal()).namespace("odoo")
                    .authorityResolver(resolver).traceId("trace-xyz").build();
            AuthorityResolutionPipeline.resolve(saleOrder(), ctx);
            assertEquals("trace-xyz", resolver.calls.get(0).traceId());
        }
    }

    // ------------------------------------------------------------------
    // Multi-model + dedup
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("多模型 + 请求级去重")
    class MultiModel {

        @Test
        @DisplayName("join 产生两个 binding")
        void joinProducesTwoBindings() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            JoinPlan joined = saleOrder().join(partner(), "left",
                    List.of(JoinOn.of("partnerId", "=", "id")));
            Map<String, ModelBinding> bindings =
                    AuthorityResolutionPipeline.resolve(joined, ctx);
            assertEquals(2, bindings.size());
            assertTrue(bindings.containsKey("SaleOrderQM"));
            assertTrue(bindings.containsKey("ResPartnerQM"));
        }

        @Test
        @DisplayName("request 保留左右前序")
        void requestPreservesLeftRightOrder() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            JoinPlan joined = saleOrder().join(partner(), "left",
                    List.of(JoinOn.of("partnerId", "=", "id")));
            AuthorityResolutionPipeline.resolve(joined, ctx);
            assertEquals(List.of("SaleOrderQM", "ResPartnerQM"),
                    resolver.calls.get(0).modelNames());
        }

        @Test
        @DisplayName("重复引用在请求中被去重")
        void duplicateReferencesDedupedInRequest() {
            BaseModelPlan saleOther = (BaseModelPlan) Dsl.from(Dsl.FromOptions.builder()
                    .model("SaleOrderQM").columns(List.of("id")).build());
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            UnionPlan union = saleOrder().union(saleOther);
            Map<String, ModelBinding> bindings =
                    AuthorityResolutionPipeline.resolve(union, ctx);
            assertEquals(List.of("SaleOrderQM"), List.copyOf(bindings.keySet()));
            assertEquals(1, resolver.calls.size());
            assertEquals(List.of("SaleOrderQM"), resolver.calls.get(0).modelNames());
        }
    }

    // ------------------------------------------------------------------
    // ModelInfoProvider integration
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("ModelInfoProvider 注入")
    class ModelInfoProviderIntegration {

        @Test
        @DisplayName("自定义 provider 传递 tables")
        void customProviderForwardsTables() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            StaticTableProvider provider = new StaticTableProvider(
                    Map.of("SaleOrderQM", List.of("sale_order", "sale_order_line")));
            AuthorityResolutionPipeline.resolve(saleOrder(), ctx, provider);
            assertEquals(List.of("sale_order", "sale_order_line"),
                    resolver.calls.get(0).models().get(0).tables());
        }

        @Test
        @DisplayName("provider 返回 Optional.empty() 被 coerce 为空 list")
        void providerReturningEmptyCoercedToEmptyList() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            AuthorityResolutionPipeline.resolve(saleOrder(), ctx, new EmptyReturningProvider());
            assertEquals(List.of(), resolver.calls.get(0).models().get(0).tables());
        }

        @Test
        @DisplayName("显式 NullModelInfoProvider 给空 tables")
        void nullProviderFallbackGivesEmptyTables() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            AuthorityResolutionPipeline.resolve(saleOrder(), ctx, new NullModelInfoProvider());
            assertEquals(List.of(), resolver.calls.get(0).models().get(0).tables());
        }

        @Test
        @DisplayName("默认 provider（2-arg 重载）等价于 NullModelInfoProvider")
        void defaultProviderIsNullProvider() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            AuthorityResolutionPipeline.resolve(saleOrder(), ctx);
            assertEquals(List.of(), resolver.calls.get(0).models().get(0).tables());
        }

        @Test
        @DisplayName("provider == null 与 2-arg 重载等价（走 NullModelInfoProvider 分支）")
        void explicitNullProviderArgTreatedAsDefault() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            AuthorityResolutionPipeline.resolve(saleOrder(), ctx, null);
            assertEquals(List.of(), resolver.calls.get(0).models().get(0).tables());
        }
    }

    // ------------------------------------------------------------------
    // Fail-closed: RESOLVER_NOT_AVAILABLE
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Fail-closed: RESOLVER_NOT_AVAILABLE")
    class FailClosedNoResolver {

        @Test
        @DisplayName("null context 抛 RESOLVER_NOT_AVAILABLE")
        void nullContextRaisesResolverNotAvailable() {
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(saleOrder(), null));
            assertEquals(AuthorityErrorCodes.RESOLVER_NOT_AVAILABLE, ex.code());
            assertEquals(AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE, ex.phase());
        }

        @Test
        @DisplayName("authorityResolver 为 null 的伪 context 抛 RESOLVER_NOT_AVAILABLE")
        void contextWithNullResolverRaisesResolverNotAvailable() {
            // ComposeQueryContext ctor rejects null resolver; construct a
            // real ctx then null the resolver field via reflection to
            // simulate a caller that bypasses the ctor (e.g. Mockito).
            ComposeQueryContext ctx = ctxFor(principal(), new EchoResolver());
            try {
                Field f = ComposeQueryContext.class.getDeclaredField("authorityResolver");
                f.setAccessible(true);
                f.set(ctx, null);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(saleOrder(), ctx));
            assertEquals(AuthorityErrorCodes.RESOLVER_NOT_AVAILABLE, ex.code());
        }
    }

    // ------------------------------------------------------------------
    // Fail-closed: resolver raises
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Fail-closed: resolver 抛异常")
    class FailClosedResolverRaises {

        @Test
        @DisplayName("AuthorityResolutionException 原样透传（不包装为 UPSTREAM_FAILURE）")
        void authorityErrorPropagatesVerbatim() {
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(principal()).namespace("odoo")
                    .authorityResolver(new RaisingResolver()).build();
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(saleOrder(), ctx));
            assertEquals(AuthorityErrorCodes.IR_RULE_UNMAPPED_FIELD, ex.code());
        }

        @Test
        @DisplayName("普通 RuntimeException 包装为 UPSTREAM_FAILURE，cause 保留")
        void plainExceptionWrappedAsUpstreamFailure() {
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(principal()).namespace("odoo")
                    .authorityResolver(new BoomResolver()).build();
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(saleOrder(), ctx));
            assertEquals(AuthorityErrorCodes.UPSTREAM_FAILURE, ex.code());
            assertNotNull(ex.getCause());
            assertEquals("kaboom", ex.getCause().getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Fail-closed: response shape
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Fail-closed: 响应形态")
    class FailClosedResponseShape {

        @Test
        @DisplayName("resolver 返回 null 抛 INVALID_RESPONSE")
        void nullResponseRaisesInvalidResponse() {
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(principal()).namespace("odoo")
                    .authorityResolver(new NullResponseResolver()).build();
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(saleOrder(), ctx));
            assertEquals(AuthorityErrorCodes.INVALID_RESPONSE, ex.code());
        }

        @Test
        @DisplayName("响应包含多余 key 抛 INVALID_RESPONSE")
        void extraKeyInResponseRaisesInvalidResponse() {
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(principal()).namespace("odoo")
                    .authorityResolver(new ExtraKeyResolver()).build();
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(saleOrder(), ctx));
            assertEquals(AuthorityErrorCodes.INVALID_RESPONSE, ex.code());
            assertTrue(ex.getMessage().contains("PhantomModel"));
        }

        @Test
        @DisplayName("响应缺 key 抛 MODEL_BINDING_MISSING，按请求顺序定位首缺")
        void missingKeyRaisesModelBindingMissing() {
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(principal()).namespace("odoo")
                    .authorityResolver(new MissingKeyResolver()).build();
            UnionPlan union = saleOrder().union(crmLead());
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(union, ctx));
            assertEquals(AuthorityErrorCodes.MODEL_BINDING_MISSING, ex.code());
            assertEquals("CrmLeadQM", ex.modelInvolved());
        }

        @Test
        @DisplayName("value 非 ModelBinding（反射注入）抛 INVALID_RESPONSE")
        void nonBindingValueRaisesInvalidResponse() {
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(principal()).namespace("odoo")
                    .authorityResolver(new BadValueResolver()).build();
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(saleOrder(), ctx));
            assertEquals(AuthorityErrorCodes.INVALID_RESPONSE, ex.code());
            assertEquals("SaleOrderQM", ex.modelInvolved());
        }
    }

    // ------------------------------------------------------------------
    // Phase tag
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("phase 标签")
    class ErrorPhaseTag {

        @Test
        @DisplayName("所有异常均标记 phase=authority-resolve")
        void allErrorsTaggedAuthorityResolvePhase() {
            ComposeQueryContext ctx = ComposeQueryContext.builder()
                    .principal(principal()).namespace("odoo")
                    .authorityResolver(new BoomResolver()).build();
            AuthorityResolutionException ex = assertThrows(AuthorityResolutionException.class,
                    () -> AuthorityResolutionPipeline.resolve(saleOrder(), ctx));
            assertEquals(AuthorityErrorCodes.PHASE_AUTHORITY_RESOLVE, ex.phase());
        }
    }

    // ------------------------------------------------------------------
    // Return shape
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("返回值契约")
    class ReturnShape {

        @Test
        @DisplayName("返回的 Map 为不可变")
        void returnedMapIsUnmodifiable() {
            EchoResolver resolver = new EchoResolver();
            ComposeQueryContext ctx = ctxFor(principal(), resolver);
            Map<String, ModelBinding> bindings =
                    AuthorityResolutionPipeline.resolve(saleOrder(), ctx);
            assertThrows(UnsupportedOperationException.class,
                    () -> bindings.put("X", ModelBinding.builder().build()));
        }
    }

    // ------------------------------------------------------------------
    // Reflective surface check (Layer-C-adjacent)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("静态工具类形态校验")
    class StaticShape {

        @Test
        @DisplayName("AuthorityResolutionPipeline 只暴露 resolve 静态方法，不暴露可变状态")
        void pipelineSurfaceIsStaticOnly() {
            for (Field f : AuthorityResolutionPipeline.class.getDeclaredFields()) {
                assertTrue(Modifier.isStatic(f.getModifiers())
                                || !Modifier.isPublic(f.getModifiers()),
                        () -> "Pipeline 不得暴露实例字段 " + f.getName());
            }
            for (Constructor<?> c : AuthorityResolutionPipeline.class.getDeclaredConstructors()) {
                assertFalse(Modifier.isPublic(c.getModifiers()),
                        "Pipeline 不得暴露 public ctor —— 它是纯静态工具类");
            }
        }
    }
}

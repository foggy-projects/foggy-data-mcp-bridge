package com.foggyframework.dataset.model.semantic.permission;

import com.foggyframework.dataset.model.def.permission.ModelPermissionsDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelPermissionServiceTest {

    private final ModelPermissionService service = new ModelPermissionService();

    @Test
    void absentAndExplicitPublicDefinitionsProducePublicDecision() {
        QueryModel absent = queryModel("OpenOrders", null);
        ModelPermissionsDef explicitPublic = new ModelPermissionsDef();
        explicitPublic.setMode("public");

        PermissionDecision absentDecision = service.evaluate(
                absent, "sales", PermissionAction.EXECUTE,
                RequestIdentity.anonymous(), new PermissionEvaluationSession("trace-1")
        );
        PermissionDecision explicitDecision = service.evaluate(
                queryModel("OpenCustomers", explicitPublic), "sales", PermissionAction.DESCRIBE,
                RequestIdentity.anonymous(), new PermissionEvaluationSession("trace-2")
        );

        assertThat(absentDecision.isAllow()).isTrue();
        assertThat(absentDecision.isPublicDecision()).isTrue();
        assertThat(explicitDecision.isPublicDecision()).isTrue();
    }

    @Test
    void resolverReceivesOpaqueAuthorizationAndRunsForAnonymousRequests() {
        FsscriptFunction resolver = mock(FsscriptFunction.class);
        when(resolver.threadSafeAccept(any())).thenAnswer(invocation -> {
            Map<?, ?> context = invocation.getArgument(0);
            assertThat(context.get("authorization")).isNull();
            assertThat(context.get("action")).isEqualTo("EXECUTE");
            assertThat(context.get("model")).isEqualTo("ProtectedOrders");
            assertThat(context.get("traceId")).isEqualTo("trace-anonymous");
            assertThat(context.get("predicate")).isInstanceOf(PermissionPredicateBuilder.class);
            assertThat(context.get("identity")).isEqualTo(Map.of("kind", "ANONYMOUS"));
            return Map.of("allow", true);
        });

        PermissionDecision decision = service.evaluate(
                queryModel("ProtectedOrders", resolverDefinition(resolver)),
                "sales",
                PermissionAction.EXECUTE,
                RequestIdentity.anonymous(),
                new PermissionEvaluationSession("trace-anonymous")
        );

        assertThat(decision.isAllow()).isTrue();
        assertThat(decision.isPublicDecision()).isFalse();
        verify(resolver).threadSafeAccept(any());
    }

    @Test
    void requestLocalSessionReusesOneImmutableDecisionPerAction() {
        FsscriptFunction resolver = mock(FsscriptFunction.class);
        when(resolver.threadSafeAccept(any())).thenReturn(Map.of(
                "allow", true,
                "decisionId", "decision-1",
                "policyVersion", "v7"
        ));
        QueryModel model = queryModel("ProtectedOrders", resolverDefinition(resolver));
        PermissionEvaluationSession session = new PermissionEvaluationSession("trace-reuse");
        RequestIdentity identity = RequestIdentity.fromAuthorization("Custom opaque token");

        PermissionDecision first = service.evaluate(
                model, "sales", PermissionAction.EXECUTE, identity, session
        );
        PermissionDecision second = service.evaluate(
                model, "sales", PermissionAction.EXECUTE, identity, session
        );
        PermissionDecision describe = service.evaluate(
                model, "sales", PermissionAction.DESCRIBE, identity, session
        );

        assertThat(second).isSameAs(first);
        assertThat(describe).isNotSameAs(first);
        assertThat(session.size()).isEqualTo(2);
        verify(resolver, times(2)).threadSafeAccept(any());
    }

    @Test
    void validatesDecisionShapeAndExpiryFailClosed() {
        assertResolutionFailure(Map.of());
        assertResolutionFailure(Map.of("allow", "yes"));
        assertResolutionFailure(Map.of(
                "allow", true,
                "expiresAt", Instant.now().minusSeconds(1).toString()
        ));
        assertResolutionFailure(Map.of(
                "allow", true,
                "rowPredicates", List.of(PermissionPredicate.unprovable("storeId", "custom SQL"))
        ));
    }

    @Test
    void typedPredicatePreservesProvenanceAndNormalizesEmptyInToContradiction() {
        PermissionPredicate predicate = new PermissionPredicateBuilder().in("storeId", new int[0]);

        assertThat(predicate.getOrigin()).isEqualTo(PermissionPredicate.Origin.QM_MODEL_PERMISSION);
        assertThat(predicate.getProofStatus()).isEqualTo(PermissionPredicate.ProofStatus.PROVABLE);
        assertThat(predicate.getReferencedFields()).containsExactly("storeId");
        assertThat(predicate.getValue()).isEqualTo(List.of());

        SliceRequestDef slice = predicate.toSlice();
        assertThat(slice.getAnd()).hasSize(2);
        assertThat(slice.getAnd().get(0).getOp()).isEqualTo("is null");
        assertThat(slice.getAnd().get(1).getOp()).isEqualTo("is not null");
    }

    @Test
    void opaqueIdentityPreservesCallerValueWithoutBearerInterpretation() {
        RequestIdentity identity = RequestIdentity.fromAuthorization("ApiKey tenant=7");

        assertThat(identity.kind()).isEqualTo(RequestIdentity.Kind.OPAQUE_SUBJECT);
        assertThat(identity.authorization()).isEqualTo("ApiKey tenant=7");
        assertThat(RequestIdentity.fromAuthorization(" \t ")).isEqualTo(RequestIdentity.anonymous());
    }

    private void assertResolutionFailure(Map<String, Object> result) {
        FsscriptFunction resolver = mock(FsscriptFunction.class);
        when(resolver.threadSafeAccept(any())).thenReturn(result);

        assertThatThrownBy(() -> service.evaluate(
                queryModel("ProtectedOrders", resolverDefinition(resolver)),
                "sales",
                PermissionAction.EXECUTE,
                RequestIdentity.anonymous(),
                new PermissionEvaluationSession()
        ))
                .isInstanceOf(ModelPermissionException.class)
                .extracting("code")
                .isEqualTo("MODEL_PERMISSION_RESOLUTION_FAILED");
    }

    private static QueryModel queryModel(String name, ModelPermissionsDef permissions) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(name);
        when(model.getModelPermissions()).thenReturn(permissions);
        return model;
    }

    private static ModelPermissionsDef resolverDefinition(FsscriptFunction resolver) {
        ModelPermissionsDef definition = new ModelPermissionsDef();
        definition.setMode("resolver");
        definition.setResolver(resolver);
        return definition;
    }
}

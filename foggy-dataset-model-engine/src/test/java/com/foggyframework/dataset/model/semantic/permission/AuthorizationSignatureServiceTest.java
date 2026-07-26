package com.foggyframework.dataset.model.semantic.permission;

import com.foggyframework.dataset.model.lifecycle.catalog.CatalogResolution;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.SourceRevision;
import com.foggyframework.dataset.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.model.spi.QueryModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationSignatureServiceTest {

    private final AuthorizationSignatureService service = new AuthorizationSignatureService();

    @Test
    void protectedSignatureBindsEffectivePermissionWithoutContainingOpaqueCredential() {
        String opaqueCredential = "ApiKey tenant=7 secret-value";
        ModelResultContext context = context("Orders", PermissionAction.EXECUTE);
        context.setRequestIdentity(RequestIdentity.fromAuthorization(opaqueCredential));
        context.setFieldAccess(Set.of("id", "storeId"));
        context.setPermissionDecision(new PermissionDecision(
                true,
                Map.of("tenantId", 7),
                List.of(PermissionPredicate.provable(
                        PermissionPredicate.Origin.QM_MODEL_PERMISSION,
                        null,
                        "storeId",
                        "=",
                        31
                )),
                "decision-31",
                "policy-v8",
                Instant.now().plusSeconds(300),
                "resolver-v2",
                false
        ));

        AuthorizationSignature signature = service.compute(context).orElseThrow();

        assertThat(signature.value()).startsWith("AUTH:");
        assertThat(signature.value()).doesNotContain("secret-value");
        assertThat(signature.publicIdentity()).isFalse();
    }

    @Test
    void actionFieldAndRowObligationChangesProduceDifferentSignatures() {
        ModelResultContext base = protectedContext(
                PermissionAction.EXECUTE, Set.of("id"), "storeId", 31);
        ModelResultContext actionChanged = protectedContext(
                PermissionAction.MEMBER_QUERY, Set.of("id"), "storeId", 31);
        ModelResultContext fieldChanged = protectedContext(
                PermissionAction.EXECUTE, Set.of("id", "amount"), "storeId", 31);
        ModelResultContext rowChanged = protectedContext(
                PermissionAction.EXECUTE, Set.of("id"), "storeId", 32);

        String baseValue = service.compute(base).orElseThrow().value();

        assertThat(service.compute(actionChanged).orElseThrow().value()).isNotEqualTo(baseValue);
        assertThat(service.compute(fieldChanged).orElseThrow().value()).isNotEqualTo(baseValue);
        assertThat(service.compute(rowChanged).orElseThrow().value()).isNotEqualTo(baseValue);
    }

    @Test
    void publicDecisionHasStablePublicIdentityAndNoExpiry() {
        ModelResultContext first = context("Orders", PermissionAction.EXECUTE);
        first.setRequestIdentity(RequestIdentity.anonymous());
        first.setPermissionDecision(PermissionDecision.publicAllow());
        ModelResultContext second = context("Orders", PermissionAction.EXECUTE);
        second.setRequestIdentity(RequestIdentity.fromAuthorization("opaque caller value"));
        second.setPermissionDecision(PermissionDecision.publicAllow());

        AuthorizationSignature firstSignature = service.compute(first).orElseThrow();
        AuthorizationSignature secondSignature = service.compute(second).orElseThrow();

        assertThat(firstSignature.value()).startsWith("PUBLIC:");
        assertThat(secondSignature).isEqualTo(firstSignature);
        assertThat(firstSignature.expiresAt()).isNull();
    }

    @Test
    void incompleteOrUnboundedProtectedSnapshotDisablesSharedCaching() {
        ModelResultContext missingExpiry = context("Orders", PermissionAction.EXECUTE);
        missingExpiry.setPermissionDecision(new PermissionDecision(
                true, Map.of(), List.of(), "d1", "v1", null, null, false));

        ModelResultContext missingPolicyVersion = context("Orders", PermissionAction.EXECUTE);
        missingPolicyVersion.setPermissionDecision(new PermissionDecision(
                true, Map.of(), List.of(), "d1", null,
                Instant.now().plusSeconds(60), null, false));

        ModelResultContext untracked = new ModelResultContext();
        QueryModel model = queryModel("Orders");
        untracked.pinUntrackedQueryModel(model);
        untracked.setPermissionDecision(PermissionDecision.publicAllow());

        assertThat(service.compute(missingExpiry)).isEmpty();
        assertThat(service.compute(missingPolicyVersion)).isEmpty();
        assertThat(service.compute(untracked)).isEmpty();
    }

    private ModelResultContext protectedContext(
            PermissionAction action,
            Set<String> fields,
            String rowField,
            Object rowValue
    ) {
        ModelResultContext context = context("Orders", action);
        context.setFieldAccess(fields);
        context.setPermissionDecision(new PermissionDecision(
                true,
                Map.of("tenantId", 7),
                List.of(PermissionPredicate.provable(
                        PermissionPredicate.Origin.QM_MODEL_PERMISSION,
                        null,
                        rowField,
                        "=",
                        rowValue
                )),
                "decision-1",
                "policy-v1",
                Instant.parse("2030-01-01T00:00:00Z"),
                "resolver-v1",
                false
        ));
        return context;
    }

    private ModelResultContext context(String modelName, PermissionAction action) {
        QueryModel model = queryModel(modelName);
        CatalogIdentity identity = new CatalogIdentity(
                "tenant-a",
                new CatalogGeneration("catalog:1"),
                new SourceRevision("source:1")
        );
        ModelResultContext context = new ModelResultContext();
        context.setNamespace("tenant-a");
        context.setPermissionAction(action);
        context.pinCatalogResolution(
                new CatalogResolution<>(modelName, model, identity, Map.of(), true),
                "tenant-a"
        );
        return context;
    }

    private QueryModel queryModel(String modelName) {
        QueryModel model = mock(QueryModel.class);
        when(model.getName()).thenReturn(modelName);
        return model;
    }
}

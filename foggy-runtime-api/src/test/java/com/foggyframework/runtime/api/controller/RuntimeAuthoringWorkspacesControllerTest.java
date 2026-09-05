package com.foggyframework.runtime.api.controller;

import com.foggyframework.dataset.model.candidate.CandidateQueryErrorCode;
import com.foggyframework.dataset.model.candidate.CandidateQueryException;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryRequest;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceCreateRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceInfo;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceQueryRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspacePublishRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceRecoverRequest;
import com.foggyframework.runtime.api.dto.AuthoringWorkspaceState;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceException;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspaceService;
import com.foggyframework.runtime.api.service.RuntimeAuthoringWorkspacePublicationService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeAuthoringWorkspacesControllerTest {

    private final RuntimeAuthoringWorkspaceService service =
            mock(RuntimeAuthoringWorkspaceService.class);
    private final RuntimeAuthoringWorkspacePublicationService publications =
            mock(RuntimeAuthoringWorkspacePublicationService.class);
    private final RuntimeAuthoringWorkspacesController controller =
            new RuntimeAuthoringWorkspacesController(
                    new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()),
                    service, publications);

    @Test
    void createReturnsRuntimeEnvelopeAndForwardsHeaderNamespace() {
        AuthoringWorkspaceInfo info = info();
        AuthoringWorkspaceCreateRequest request =
                new AuthoringWorkspaceCreateRequest(
                        null, "managed-sales");
        when(service.create("sales", request)).thenReturn(info);

        var response = controller.create(request, "sales");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isSameAs(info);
        verify(service).create("sales", request);
    }

    @Test
    void mapsStableWorkspaceFailureWithoutLeakingSuppressedCause() {
        RuntimeAuthoringWorkspaceException failure =
                new RuntimeAuthoringWorkspaceException(
                        "WORKSPACE_REVISION_CONFLICT",
                        "workspaces.resources.save",
                        "Workspace candidate revision is no longer current.",
                        "query/Order.qm", true);
        failure.addSuppressed(new IllegalStateException(
                "/private/store/path secret-token"));
        when(service.save(eq("workspace-1"), any())).thenThrow(failure);

        var response = controller.save("workspace-1", null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("WORKSPACE_REVISION_CONFLICT");
        assertThat(response.error().phase())
                .isEqualTo("workspaces.resources.save");
        assertThat(response.error().path()).isEqualTo("query/Order.qm");
        assertThat(response.toString()).doesNotContain(
                "/private/store/path", "secret-token");
    }

    @Test
    void invalidStateFilterUsesStableWorkspaceEnvelope() {
        var response = controller.list(null, "unknown", false);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("WORKSPACE_INVALID_REQUEST");
        assertThat(response.error().phase()).isEqualTo("workspaces.list");
    }

    @Test
    void preservesCandidateErrorFamiliesForWorkspaceQuery() {
        SemanticQueryRequest semantic = new SemanticQueryRequest();
        AuthoringWorkspaceQueryRequest request =
                new AuthoringWorkspaceQueryRequest("sha256:revision", semantic);
        when(service.query(eq("workspace-1"), eq("Order"),
                eq("sha256:revision"), eq(semantic), eq("Bearer business"),
                any())).thenThrow(new CandidateQueryException(
                CandidateQueryErrorCode.CANDIDATE_MODE_UNSUPPORTED,
                "execute", "Ordinary JDBC requests only.", "Order"));

        var response = controller.executeQuery(
                "workspace-1", "Order", request, "Bearer business");

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("CANDIDATE_MODE_UNSUPPORTED");
        assertThat(response.error().phase()).isEqualTo("execute");
        assertThat(response.error().path()).isEqualTo("Order");
    }

    @Test
    void sanitizesUnexpectedQueryFailureIntoExistingQueryFamily() {
        SemanticQueryRequest semantic = new SemanticQueryRequest();
        AuthoringWorkspaceQueryRequest request =
                new AuthoringWorkspaceQueryRequest("sha256:revision", semantic);
        when(service.query(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException(
                        "jdbc:password=must-not-leak"));

        var response = controller.validateQuery(
                "workspace-1", "Order", request, null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("QUERY_VALIDATE_FAILED");
        assertThat(response.error().message())
                .doesNotContain("password", "must-not-leak");
    }

    @Test
    void malformedAuthoringQueryJsonReturnsBadRequestWithoutCallingService() {
        HttpServletResponse httpResponse = mock(HttpServletResponse.class);

        var response = controller.validateQueryRequest(
                "workspace-1", "Order", "{\"request\":", null, httpResponse);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("INVALID_DSL_SYNTAX");
        assertThat(response.error().phase()).isEqualTo("workspaces.query.validate");
        verify(httpResponse).setStatus(400);
        verifyNoInteractions(service);
    }

    @Test
    void publishAndRecoverReturnRuntimeEnvelopeAndForwardPinnedIdentity() {
        AuthoringWorkspaceInfo info = info();
        AuthoringWorkspacePublishRequest publish =
                new AuthoringWorkspacePublishRequest(
                        "sha256:candidate", "sha256:base", "source:base");
        AuthoringWorkspaceRecoverRequest recover =
                new AuthoringWorkspaceRecoverRequest(
                        "sha256:candidate", "attempt-opaque");
        when(publications.publish("workspace-1", publish)).thenReturn(info);
        when(publications.recover("workspace-1", recover)).thenReturn(info);

        var publishResponse = controller.publish("workspace-1", publish);
        var recoverResponse = controller.recoverPublication(
                "workspace-1", recover);

        assertThat(publishResponse.success()).isTrue();
        assertThat(publishResponse.data()).isSameAs(info);
        assertThat(recoverResponse.success()).isTrue();
        assertThat(recoverResponse.data()).isSameAs(info);
        verify(publications).publish("workspace-1", publish);
        verify(publications).recover("workspace-1", recover);
    }

    @Test
    void publicationFailureUsesStableEnvelopeWithoutLeakingCause() {
        RuntimeAuthoringWorkspaceException failure =
                new RuntimeAuthoringWorkspaceException(
                        "WORKSPACE_RECOVERY_CONFLICT",
                        "workspaces.publish.recovery",
                        "Live state does not match the publication attempt.",
                        null, false);
        failure.addSuppressed(new IllegalStateException(
                "/private/published/path auth-code=must-not-leak"));
        when(publications.recover(eq("workspace-1"), any()))
                .thenThrow(failure);

        var response = controller.recoverPublication("workspace-1", null);

        assertThat(response.success()).isFalse();
        assertThat(response.error().code())
                .isEqualTo("WORKSPACE_RECOVERY_CONFLICT");
        assertThat(response.error().phase())
                .isEqualTo("workspaces.publish.recovery");
        assertThat(response.error().safeToAutoRepair()).isFalse();
        assertThat(response.toString()).doesNotContain(
                "/private/published/path", "must-not-leak");
    }

    private static AuthoringWorkspaceInfo info() {
        return new AuthoringWorkspaceInfo(
                "workspace-1", "sales", "managed-sales",
                "runtime-managed", "sha256:" + "1".repeat(64),
                "source:1", "sha256:" + "1".repeat(64),
                AuthoringWorkspaceState.DRAFT,
                "2026-07-31T00:00:00Z", "2026-07-31T00:00:00Z",
                null, List.of());
    }
}

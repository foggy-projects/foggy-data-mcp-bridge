package com.foggyframework.dataset.model.semantic.service;

import com.foggyframework.dataset.model.semantic.port.ComposeExecutionException;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionPort;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.model.semantic.port.ComposeExecutionResult;
import com.foggyframework.dataset.model.semantic.port.ComposeOperation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NativeComposeQueryServiceTest {

    private final ComposeExecutionPort port = mock(ComposeExecutionPort.class);
    private final NativeComposeQueryService service = new NativeComposeQueryService(port);

    @Test
    void shouldMapNativeRequestToComposePort() {
        when(port.execute(any())).thenReturn(new ComposeExecutionResult(
                ComposeOperation.PREVIEW, true, false,
                Map.of("plans", List.of()), "SELECT 1", List.of(), List.of()));

        Map<String, Object> response = service.execute(
                Map.of("script", "return { plans: [] };", "previewMode", true),
                "sales", "Bearer native", Map.of("X-User-Id", "user-7"));

        assertThat(response).containsEntry("status", "success");
        ArgumentCaptor<ComposeExecutionRequest> requestCaptor =
                ArgumentCaptor.forClass(ComposeExecutionRequest.class);
        verify(port).execute(requestCaptor.capture());
        assertThat(requestCaptor.getValue().operation()).isEqualTo(ComposeOperation.PREVIEW);
        assertThat(requestCaptor.getValue().namespace()).isEqualTo("sales");
        assertThat(requestCaptor.getValue().caller().userId()).isEqualTo("user-7");
        assertThat(requestCaptor.getValue().caller().authorizationHint()).isEqualTo("Bearer native");
    }

    @Test
    void shouldKeepNativeAuthorityErrorShape() {
        when(port.execute(any())).thenThrow(new ComposeExecutionException(
                ComposeExecutionException.Kind.AUTHORITY,
                "authority/model-denied", "permission-resolve", "model denied",
                null, "OrderModel", null));

        Map<String, Object> response = service.execute(
                Map.of("script", "return { plans: [] };"),
                "sales", null, Map.of());

        assertThat(response).containsEntry("status", "error");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertThat(data)
                .containsEntry("error_code", "authority/model-denied")
                .containsEntry("phase", "permission-resolve")
                .containsEntry("model", "OrderModel");
    }
}

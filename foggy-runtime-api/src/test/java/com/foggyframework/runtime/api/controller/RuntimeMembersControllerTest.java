package com.foggyframework.runtime.api.controller;

import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.model.PagingResultImpl;
import com.foggyframework.dataset.model.common.query.DimensionDataQueryForm;
import com.foggyframework.dataset.model.common.result.DbDataItem;
import com.foggyframework.dataset.model.semantic.permission.ModelPermissionException;
import com.foggyframework.dataset.model.service.JdbcService;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeMembersControllerTest {

    private final JdbcService jdbcService = mock(JdbcService.class);
    private final RuntimeMembersController controller = new RuntimeMembersController(
            new RuntimeApiResponseFactory(new FoggyRuntimeApiProperties()), jdbcService);

    @Test
    void stableRouteDelegatesToGovernedLegacyServiceWithHeaders() {
        PagingResultImpl<DbDataItem> result = PagingResultImpl.of(
                List.of(new DbDataItem("c-1", "Customer 1")), 0, 20, null, 1);
        when(jdbcService.queryDimensionData(
                any(PagingRequest.class), eq("opaque-member"), eq("sales")))
                .thenReturn(result);

        RuntimeEnvelope<PagingResultImpl<DbDataItem>> response = controller.listMembers(
                "SalesModel", "customer$id", "sales", "opaque-member");

        assertThat(response.success()).isTrue();
        assertThat(response.data().getItems()).hasSize(1);
        assertThat(response.data().getItems().get(0).getId()).isEqualTo("c-1");
        verify(jdbcService).queryDimensionData(
                any(PagingRequest.class), eq("opaque-member"), eq("sales"));
    }

    @Test
    void permissionFailureUsesStableRuntimeError() {
        when(jdbcService.queryDimensionData(
                any(PagingRequest.class), eq("opaque-member"), eq("sales")))
                .thenThrow(ModelPermissionException.denied());

        RuntimeEnvelope<PagingResultImpl<DbDataItem>> response = controller.listMembers(
                "SalesModel", "customer$id", "sales", "opaque-member");

        assertThat(response.success()).isFalse();
        assertThat(response.error().code()).isEqualTo("MODEL_ACCESS_DENIED");
        assertThat(response.error().phase()).isEqualTo("members.list");
    }
}

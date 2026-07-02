package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.ExDefined;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfig;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfigRequest;
import com.foggyframework.dataviewer.service.TableDefaultQueryConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TableDefaultQueryConfigControllerTest {

    @Mock
    private TableDefaultQueryConfigService service;

    private TableDefaultQueryConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new TableDefaultQueryConfigController(service);
    }

    @Test
    void shouldReturnResolvedDefaultConfigAndPassScope() {
        TableDefaultQueryConfig config = TableDefaultQueryConfig.builder()
                .queryModel("TicketModel")
                .tableInstanceId("ticket-list")
                .defaultVisibleColumns(List.of("ticketNo"))
                .build();
        when(service.resolve(any())).thenReturn(Optional.of(config));

        RX<TableDefaultQueryConfig> response = controller.getDefault(
                "TicketModel",
                "ticket-list",
                "u1",
                "tenant-a",
                List.of("ops"),
                true);

        assertEquals(RX.SUCCESS, response.getCode());
        assertSame(config, response.getData());

        ArgumentCaptor<TableDefaultQueryConfigRequest> captor =
                ArgumentCaptor.forClass(TableDefaultQueryConfigRequest.class);
        verify(service).resolve(captor.capture());
        TableDefaultQueryConfigRequest request = captor.getValue();
        assertEquals("TicketModel", request.getQueryModel());
        assertEquals("ticket-list", request.getTableInstanceId());
        assertEquals("u1", request.getUserId());
        assertEquals("tenant-a", request.getTenantId());
        assertEquals(List.of("ops"), request.getRoleIds());
        assertTrue(request.getIncludeFallback());
    }

    @Test
    void shouldReturnSuccessNullWhenDefaultMissing() {
        when(service.resolve(any())).thenReturn(Optional.empty());

        RX<TableDefaultQueryConfig> response = controller.getDefault(
                "TicketModel",
                null,
                null,
                null,
                null,
                true);

        assertEquals(RX.SUCCESS, response.getCode());
        assertNull(response.getData());
    }

    @Test
    void shouldConvertIllegalArgumentToBusinessFailure() {
        when(service.resolve(any())).thenThrow(new IllegalArgumentException("queryModel 不能为空"));

        RX<TableDefaultQueryConfig> response = controller.getDefault(
                " ",
                null,
                null,
                null,
                null,
                true);

        assertEquals(ExDefined.COMMON_ERROR_CODE, response.getCode());
        assertEquals("queryModel 不能为空", response.getMsg());
    }
}

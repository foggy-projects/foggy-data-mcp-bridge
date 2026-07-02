package com.foggyframework.dataviewer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.ListPresetDef;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfig;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfigRequest;
import com.foggyframework.dataviewer.service.listpreset.FileSystemListPresetStore;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableDefaultQueryConfigServiceTest {

    @TempDir
    Path tempDir;

    private ListPresetService listPresetService;
    private TableDefaultQueryConfig fallback;
    private TableDefaultQueryConfigService service;

    @BeforeEach
    void setUp() {
        DataViewerProperties properties = new DataViewerProperties();
        properties.getListPreset().setFileBaseDir(tempDir.toString());
        listPresetService = new ListPresetService(new FileSystemListPresetStore(
                properties,
                new ObjectMapper().findAndRegisterModules()));

        fallback = TableDefaultQueryConfig.builder()
                .tableInstanceId("ticket-list")
                .queryModel("TicketModel")
                .defaultVisibleColumns(List.of("ticketNo"))
                .defaultOrderBy(List.of(order("createdAt", "desc")))
                .defaultPageSize(100)
                .defaultSlices(List.of(slice("active", "=", true)))
                .source("SYSTEM")
                .build();
        service = new TableDefaultQueryConfigService(
                listPresetService,
                List.of(request -> fallback.getQueryModel().equals(request.getQueryModel())
                        ? java.util.Optional.of(fallback)
                        : java.util.Optional.empty()));
    }

    @Test
    void shouldReturnFallbackWhenUserDefaultMissing() {
        TableDefaultQueryConfig resolved = service.resolve(request(null, true)).orElseThrow();

        assertEquals("SYSTEM", resolved.getSource());
        assertEquals("ticket-list", resolved.getTableInstanceId());
        assertEquals("TicketModel", resolved.getQueryModel());
        assertEquals(List.of("ticketNo"), resolved.getDefaultVisibleColumns());
        assertEquals(100, resolved.getDefaultPageSize());
    }

    @Test
    void shouldPreferUserDefaultWhenPresent() {
        createUserDefault();

        TableDefaultQueryConfig resolved = service.resolve(request("u1", true)).orElseThrow();

        assertEquals("USER", resolved.getSource());
        assertEquals(List.of("title"), resolved.getDefaultVisibleColumns());
        assertEquals(20, resolved.getDefaultPageSize());
        assertEquals(List.of("updatedAt"), resolved.getDefaultOrderBy().stream().map(OrderRequestDef::getField).toList());
        assertEquals(List.of("status"), resolved.getDefaultSlices().stream().map(SliceRequestDef::getField).toList());
    }

    @Test
    void shouldResolveUserDefaultWithoutFallbackWhenDisabled() {
        createUserDefault();

        TableDefaultQueryConfig resolved = service.resolve(request("u1", false)).orElseThrow();

        assertEquals("USER", resolved.getSource());
        assertEquals(List.of("title"), resolved.getDefaultVisibleColumns());
        assertEquals(20, resolved.getDefaultPageSize());
    }

    @Test
    void shouldRejectBlankQueryModel() {
        TableDefaultQueryConfigRequest request = TableDefaultQueryConfigRequest.builder()
                .queryModel(" ")
                .build();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.resolve(request));

        assertEquals("queryModel 不能为空", ex.getMessage());
    }

    private void createUserDefault() {
        ListPresetService.SaveListPresetRequest request = new ListPresetService.SaveListPresetRequest();
        request.setTitle("我的工单列表");
        request.setColumns(List.of("title"));
        request.setQuery(new ListPresetDef.QueryConditionPreset(
                List.of(slice("status", "=", "OPEN")),
                List.of(order("updatedAt", "desc"))));
        request.setPageSize(20);
        request.setIsDefault(true);
        listPresetService.create("u1", "TicketModel", "ticket-list", request);
    }

    private TableDefaultQueryConfigRequest request(String userId, boolean includeFallback) {
        return TableDefaultQueryConfigRequest.builder()
                .queryModel("TicketModel")
                .tableInstanceId("ticket-list")
                .userId(userId)
                .includeFallback(includeFallback)
                .build();
    }

    private SliceRequestDef slice(String field, String op, Object value) {
        return new SliceRequestDef(field, op, value);
    }

    private OrderRequestDef order(String field, String dir) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir(dir);
        return order;
    }
}

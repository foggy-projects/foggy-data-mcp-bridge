package com.foggyframework.dataviewer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.ListPresetDef;
import com.foggyframework.dataviewer.service.listpreset.FileSystemListPresetStore;
import com.foggyframework.dataviewer.service.listpreset.ListPresetFieldValidator;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListPresetServiceTest {

    @TempDir
    Path tempDir;

    private ListPresetService service;
    private FileSystemListPresetStore store;

    @BeforeEach
    void setUp() {
        DataViewerProperties properties = new DataViewerProperties();
        properties.getListPreset().setFileBaseDir(tempDir.toString());
        store = new FileSystemListPresetStore(
                properties,
                new ObjectMapper().findAndRegisterModules());
        service = new ListPresetService(store);
    }

    @Nested
    class FileStoreTests {

        @Test
        void shouldCreateAndListPresetByUserModelAndBusinessKey() {
            ListPresetDef created = service.create("u1", "TicketModel", "ticket-list", request("默认列表", true));

            List<ListPresetDef> presets = service.list("u1", "TicketModel", "ticket-list");

            assertEquals(1, presets.size());
            assertEquals(created.getId(), presets.get(0).getId());
            assertEquals("u1", presets.get(0).getOwnerId());
            assertEquals("ticket-list", presets.get(0).getBusinessKey());
        }

        @Test
        void shouldKeepOnlyOneDefaultPresetInSameScope() {
            ListPresetDef first = service.create("u1", "TicketModel", "ticket-list", request("列表一", true));
            ListPresetDef second = service.create("u1", "TicketModel", "ticket-list", request("列表二", true));

            List<ListPresetDef> presets = service.list("u1", "TicketModel", "ticket-list");
            List<ListPresetDef> defaults = presets.stream()
                    .filter(preset -> Boolean.TRUE.equals(preset.getIsDefault()))
                    .toList();

            assertEquals(1, defaults.size());
            assertEquals(second.getId(), defaults.get(0).getId());
            assertFalse(service.get("u1", first.getId()).orElseThrow().getIsDefault());
        }

        @Test
        void shouldIsolatePresetByUserAndBusinessKey() {
            service.create("u1", "TicketModel", "ticket-list", request("用户一", false));
            service.create("u2", "TicketModel", "ticket-list", request("用户二", false));
            service.create("u1", "TicketModel", "feedback-list", request("反馈", false));

            assertEquals(1, service.list("u1", "TicketModel", "ticket-list").size());
            assertEquals(1, service.list("u2", "TicketModel", "ticket-list").size());
            assertEquals(1, service.list("u1", "TicketModel", "feedback-list").size());
            assertTrue(service.list("u2", "TicketModel", "feedback-list").isEmpty());
            assertTrue(service.get("u1", "missing").isEmpty());
        }

        @Test
        void shouldSanitizePathSegmentsForFileStore() {
            service.create("../u1", "Ticket/Model", "../ticket-list", request("列表", false));

            assertFalse(Files.exists(tempDir.resolveSibling("u1")));
            assertEquals(1, service.list("../u1", "Ticket/Model", "../ticket-list").size());
        }

        @Test
        void shouldDeletePreset() {
            ListPresetDef created = service.create("u1", "TicketModel", "ticket-list", request("列表", true));

            assertTrue(service.delete("u1", created.getId()));

            assertTrue(service.list("u1", "TicketModel", "ticket-list").isEmpty());
            assertTrue(service.getDefault("u1", "TicketModel", "ticket-list").isEmpty());
        }

        @Test
        void shouldRejectInvalidFieldNames() {
            ListPresetService.SaveListPresetRequest request = request("列表", false);
            request.setQuery(new ListPresetDef.QueryConditionPreset(
                    List.of(new SliceRequestDef()),
                    List.of()));

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.create("u1", "TicketModel", "ticket-list", request));

            assertEquals("slice 不能包含空字段", ex.getMessage());
        }

        @Test
        void shouldRejectEmptyColumnsOnUpdate() {
            ListPresetDef created = service.create("u1", "TicketModel", "ticket-list", request("列表", false));
            ListPresetService.SaveListPresetRequest update = new ListPresetService.SaveListPresetRequest();
            update.setColumns(List.of());

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.update("u1", created.getId(), update));

            assertEquals("columns 不能为空", ex.getMessage());
        }

        @Test
        void shouldRejectBlankModel() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.list("u1", " ", "ticket-list"));

            assertEquals("model 不能为空", ex.getMessage());
        }

        @Test
        void shouldUseNoopFieldValidatorByDefault() {
            ListPresetService.SaveListPresetRequest request = request("列表", false);
            request.setColumns(List.of("unknownField"));

            ListPresetDef created = service.create("u1", "TicketModel", "ticket-list", request);

            assertEquals(List.of("unknownField"), created.getColumns());
        }

        @Test
        void shouldRejectCreateWhenCustomFieldValidatorRejectsColumn() {
            service = new ListPresetService(store, allowedFieldValidator());
            ListPresetService.SaveListPresetRequest request = request("列表", false);
            request.setColumns(List.of("ticketNo", "unknownField"));

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.create("u1", "TicketModel", "ticket-list", request));

            assertEquals("字段不允许: unknownField", ex.getMessage());
        }

        @Test
        void shouldRejectUpdateWhenCustomFieldValidatorRejectsOrderBy() {
            ListPresetDef created = service.create("u1", "TicketModel", "ticket-list", request("列表", false));
            service = new ListPresetService(store, allowedFieldValidator());
            ListPresetService.SaveListPresetRequest update = new ListPresetService.SaveListPresetRequest();
            com.foggyframework.dataset.model.def.query.request.OrderRequestDef order =
                    new com.foggyframework.dataset.model.def.query.request.OrderRequestDef();
            order.setField("unknownField");
            order.setDir("desc");
            update.setQuery(new ListPresetDef.QueryConditionPreset(List.of(), List.of(order)));

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.update("u1", created.getId(), update));

            assertEquals("字段不允许: unknownField", ex.getMessage());
        }
    }

    private ListPresetService.SaveListPresetRequest request(String title, boolean isDefault) {
        ListPresetService.SaveListPresetRequest request = new ListPresetService.SaveListPresetRequest();
        request.setTitle(title);
        request.setColumns(List.of("ticketNo", "title"));
        request.setQuery(new ListPresetDef.QueryConditionPreset(List.of(), List.of()));
        request.setIsDefault(isDefault);
        return request;
    }

    private ListPresetFieldValidator allowedFieldValidator() {
        return (userId, model, businessKey, request) -> {
            List<String> allowed = List.of("ticketNo", "title");
            if (request.getColumns() != null) {
                request.getColumns().forEach(field -> validateAllowedField(allowed, field));
            }
            if (request.getColumnSettings() != null) {
                request.getColumnSettings().forEach(setting -> validateAllowedField(allowed, setting.getName()));
            }
            if (request.getQuery() != null) {
                if (request.getQuery().getSlice() != null) {
                    request.getQuery().getSlice().forEach(slice -> validateAllowedField(allowed, slice.getField()));
                }
                if (request.getQuery().getOrderBy() != null) {
                    request.getQuery().getOrderBy().forEach(order -> validateAllowedField(allowed, order.getField()));
                }
            }
        };
    }

    private void validateAllowedField(List<String> allowed, String field) {
        if (!allowed.contains(field)) {
            throw new IllegalArgumentException("字段不允许: " + field);
        }
    }
}

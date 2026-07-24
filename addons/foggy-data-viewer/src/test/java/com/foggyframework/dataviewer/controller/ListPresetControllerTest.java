package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.ExDefined;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataviewer.domain.ListPresetDef;
import com.foggyframework.dataviewer.domain.QueryVisibility;
import com.foggyframework.dataviewer.service.ListPresetService;
import com.foggyframework.dataviewer.service.ListPresetService.SaveListPresetRequest;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ListPresetController 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ListPresetControllerTest {

    private static final String USER_ID = "u1";
    private static final String MODEL = "TicketModel";
    private static final String BUSINESS_KEY = "ticket-list";

    @Mock
    private ListPresetService listPresetService;

    private ListPresetController controller;

    @BeforeEach
    void setUp() {
        controller = new ListPresetController(listPresetService);
    }

    @Test
    @DisplayName("应按用户、模型和业务 key 查询自定义列表")
    void shouldListPresetsByScope() {
        ListPresetDef preset = preset("preset-1");
        when(listPresetService.list(USER_ID, MODEL, BUSINESS_KEY)).thenReturn(List.of(preset));

        RX<List<ListPresetDef>> response = controller.list(USER_ID, MODEL, BUSINESS_KEY);

        assertEquals(RX.SUCCESS, response.getCode());
        assertEquals(List.of(preset), response.getData());
        verify(listPresetService).list(USER_ID, MODEL, BUSINESS_KEY);
    }

    @Test
    @DisplayName("应创建自定义列表并透传路径作用域")
    void shouldCreatePresetWithPathScope() {
        SaveListPresetRequest request = request();
        ListPresetDef preset = preset("preset-1");
        when(listPresetService.create(USER_ID, MODEL, BUSINESS_KEY, request)).thenReturn(preset);

        RX<ListPresetDef> response = controller.create(USER_ID, MODEL, BUSINESS_KEY, request);

        assertEquals(RX.SUCCESS, response.getCode());
        assertSame(preset, response.getData());
        verify(listPresetService).create(USER_ID, MODEL, BUSINESS_KEY, request);
    }

    @Test
    @DisplayName("应返回默认自定义列表")
    void shouldReturnDefaultPresetWhenConfigured() {
        ListPresetDef preset = preset("preset-1");
        when(listPresetService.getDefault(USER_ID, MODEL, BUSINESS_KEY)).thenReturn(Optional.of(preset));

        RX<ListPresetDef> response = controller.getDefault(USER_ID, MODEL, BUSINESS_KEY);

        assertEquals(RX.SUCCESS, response.getCode());
        assertSame(preset, response.getData());
    }

    @Test
    @DisplayName("默认自定义列表不存在时应返回成功空数据")
    void shouldReturnNullWhenDefaultMissing() {
        when(listPresetService.getDefault(USER_ID, MODEL, BUSINESS_KEY)).thenReturn(Optional.empty());

        RX<ListPresetDef> response = controller.getDefault(USER_ID, MODEL, BUSINESS_KEY);

        assertEquals(RX.SUCCESS, response.getCode());
        assertNull(response.getData());
    }

    @Test
    @DisplayName("按 ID 查询不存在时应返回 404")
    void shouldReturnNotFoundWhenPresetMissing() {
        when(listPresetService.get(USER_ID, "missing")).thenReturn(Optional.empty());

        RX<ListPresetDef> response = controller.get(USER_ID, "missing");

        assertEquals(404, response.getCode());
    }

    @Test
    @DisplayName("应更新自定义列表")
    void shouldUpdatePreset() {
        SaveListPresetRequest request = request();
        ListPresetDef preset = preset("preset-1");
        when(listPresetService.update(USER_ID, "preset-1", request)).thenReturn(Optional.of(preset));

        RX<ListPresetDef> response = controller.update(USER_ID, "preset-1", request);

        assertEquals(RX.SUCCESS, response.getCode());
        assertSame(preset, response.getData());
        verify(listPresetService).update(USER_ID, "preset-1", request);
    }

    @Test
    @DisplayName("更新不存在的自定义列表时应返回 404")
    void shouldReturnNotFoundWhenUpdateMissingPreset() {
        SaveListPresetRequest request = request();
        when(listPresetService.update(USER_ID, "missing", request)).thenReturn(Optional.empty());

        RX<ListPresetDef> response = controller.update(USER_ID, "missing", request);

        assertEquals(404, response.getCode());
    }

    @Test
    @DisplayName("设置默认时自定义列表不存在应返回 404")
    void shouldReturnNotFoundWhenDefaultPresetMissing() {
        when(listPresetService.setDefault(USER_ID, "missing")).thenReturn(Optional.empty());

        RX<ListPresetDef> response = controller.setDefault(USER_ID, "missing");

        assertEquals(404, response.getCode());
    }

    @Test
    @DisplayName("删除不存在的自定义列表时应返回 404")
    void shouldReturnNotFoundWhenDeleteMissingPreset() {
        when(listPresetService.delete(USER_ID, "missing")).thenReturn(false);

        RX<Void> response = controller.delete(USER_ID, "missing");

        assertEquals(404, response.getCode());
    }

    @Test
    @DisplayName("应将自定义列表设置为默认")
    void shouldSetDefaultPreset() {
        ListPresetDef preset = preset("preset-1");
        when(listPresetService.setDefault(USER_ID, "preset-1")).thenReturn(Optional.of(preset));

        RX<ListPresetDef> response = controller.setDefault(USER_ID, "preset-1");

        assertEquals(RX.SUCCESS, response.getCode());
        assertSame(preset, response.getData());
    }

    @Test
    @DisplayName("应清除模型默认自定义列表")
    void shouldClearDefaultPreset() {
        RX<Void> response = controller.clearDefault(USER_ID, MODEL, BUSINESS_KEY);

        assertEquals(RX.SUCCESS, response.getCode());
        verify(listPresetService).clearDefault(USER_ID, MODEL, BUSINESS_KEY);
    }

    @Test
    @DisplayName("非法参数异常应转换为业务失败响应")
    void shouldConvertIllegalArgumentToBusinessFailure() {
        when(listPresetService.list("", MODEL, BUSINESS_KEY))
                .thenThrow(new IllegalArgumentException("userId 不能为空"));

        RX<List<ListPresetDef>> response = controller.list("", MODEL, BUSINESS_KEY);

        assertEquals(ExDefined.COMMON_ERROR_CODE, response.getCode());
        assertEquals("userId 不能为空", response.getMsg());
    }

    private SaveListPresetRequest request() {
        SaveListPresetRequest request = new SaveListPresetRequest();
        request.setTitle("我的工单列表");
        request.setDescription("常用字段和默认筛选");
        request.setColumns(List.of("ticketNo", "title"));
        request.setColumnSettings(List.of(
                new ListPresetDef.ColumnViewSetting("ticketNo", true, 160, null, "left", 0),
                new ListPresetDef.ColumnViewSetting("title", true, 240, null, null, 1)
        ));
        request.setQuery(new ListPresetDef.QueryConditionPreset(
                List.of(new SliceRequestDef("status", "=", "SUBMITTED")),
                List.of(order("updatedAt", "desc"))
        ));
        request.setPageSize(50);
        request.setVisibility(QueryVisibility.PRIVATE);
        request.setIsDefault(true);
        return request;
    }

    private ListPresetDef preset(String id) {
        Instant now = Instant.parse("2026-05-24T00:00:00Z");
        return ListPresetDef.builder()
                .id(id)
                .model(MODEL)
                .businessKey(BUSINESS_KEY)
                .title("我的工单列表")
                .description("常用字段和默认筛选")
                .columns(List.of("ticketNo", "title"))
                .columnSettings(List.of(
                        new ListPresetDef.ColumnViewSetting("ticketNo", true, 160, null, "left", 0),
                        new ListPresetDef.ColumnViewSetting("title", true, 240, null, null, 1)
                ))
                .query(new ListPresetDef.QueryConditionPreset(
                        List.of(new SliceRequestDef("status", "=", "SUBMITTED")),
                        List.of(order("updatedAt", "desc"))
                ))
                .pageSize(50)
                .visibility(QueryVisibility.PRIVATE)
                .ownerId(USER_ID)
                .isDefault(true)
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private OrderRequestDef order(String field, String dir) {
        OrderRequestDef order = new OrderRequestDef();
        order.setField(field);
        order.setDir(dir);
        return order;
    }
}

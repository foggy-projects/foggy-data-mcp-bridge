package com.foggyframework.dataset.mcp.service;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.mcp.chart.ChartRenderRequest;
import com.foggyframework.dataset.mcp.chart.ChartRenderResult;
import com.foggyframework.dataset.mcp.chart.QueryImageRenderer;
import com.foggyframework.dataset.mcp.storage.ChartStorageAdapter;
import com.foggyframework.dataset.mcp.tools.QueryModelTool;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.mcp.spi.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueryImageExportServiceTest {
    private final QueryModelTool query = mock(QueryModelTool.class);
    private final QueryImageRenderer renderer = mock(QueryImageRenderer.class);
    private final ChartStorageAdapter storage = mock(ChartStorageAdapter.class);
    private final QueryImageExportService service = new QueryImageExportService(query, renderer, storage);
    private final ToolExecutionContext context = ToolExecutionContext.builder().traceId("test-trace")
            .authorization("synthetic-private-identity").namespace("tenant-a").build();

    @Test void forwardsNamespaceAndGovernanceOnceUsesSchemaTitlesAndDoesNotStoreInline() {
        SemanticQueryResponse response = response(List.of(Map.of("month", "1月", "amount", 12)));
        var col = new SemanticQueryResponse.SchemaInfo.ColumnDef();
        col.setName("amount"); col.setTitle("销售额");
        var month = new SemanticQueryResponse.SchemaInfo.ColumnDef();
        month.setName("month"); month.setTitle("月份");
        var schema = new SemanticQueryResponse.SchemaInfo(); schema.setColumns(List.of(month, col));
        response.setSchema(schema);
        stub(response);
        Map<String, Object> args = args();
        args.put("kind", "bar"); args.put("xField", "month"); args.put("yField", "amount"); args.put("unit", "元");
        args.put("systemSlice", List.of(Map.of("field", "month", "value", "1月")));
        Map<String, Object> result = service.export(args, context);
        assertEquals(true, result.get("success"));
        verify(query).executeQuery(eq("Sales"), anyMap(), eq("execute"), eq("test-trace"),
                eq("synthetic-private-identity"), eq("tenant-a"), same(args));
        ArgumentCaptor<ChartRenderRequest> captor = ArgumentCaptor.forClass(ChartRenderRequest.class);
        verify(renderer).render(captor.capture());
        assertEquals(response.getItems(), captor.getValue().data());
        assertEquals("销售额", captor.getValue().config().get("yLabel"));
        assertEquals("月份", captor.getValue().config().get("xLabel"));
        assertEquals("元", captor.getValue().config().get("unit"));
        assertFalse(result.toString().contains("synthetic-private-identity"));
        assertFalse(result.toString().contains("systemSlice"));
        verifyNoInteractions(storage);
    }

    @Test void truncationAndPaginationAreLabelledWithoutChangingQueryOrValues() {
        var rows = List.of(Map.<String,Object>of("month", "1月", "amount", 3), Map.<String,Object>of("month", "2月", "amount", -4));
        var response = response(rows); response.setHasNext(true); response.setTotal(9L); stub(response);
        var args = args(); args.put("maxRows", 1);
        var result = service.export(args, context);
        assertEquals(1, result.get("rowsRendered")); assertEquals(2, result.get("rowsReturned")); assertEquals(true, result.get("truncated"));
        ArgumentCaptor<ChartRenderRequest> captor = ArgumentCaptor.forClass(ChartRenderRequest.class);
        verify(renderer).render(captor.capture());
        assertEquals(rows.subList(0, 1), captor.getValue().data());
        assertTrue(captor.getValue().config().get("footer").toString().contains("部分结果"));
        assertEquals(Map.of("columns", List.of("month", "amount")), args.get("payload"));
        assertTrue(captor.getValue().config().get("footer").toString().contains("查询共 9 行"));
    }

    @Test void unrequestedCountIsNotDisplayedAndChartOnlySelectsItsTwoFields() {
        var row = new LinkedHashMap<String, Object>();
        row.put("month", "1月"); row.put("amount", 3);
        for (int i = 0; i < 15; i++) row.put("extra" + i, i);
        var response = response(List.of(row)); response.setTotal(0L); stub(response);
        var args = args(); args.put("kind", "line"); args.put("xField", "month"); args.put("yField", "amount");
        assertEquals(true, service.export(args, context).get("success"));
        var captor = ArgumentCaptor.forClass(ChartRenderRequest.class);
        verify(renderer).render(captor.capture());
        assertFalse(captor.getValue().config().get("footer").toString().contains("查询共"));
        assertEquals(2, ((List<?>) captor.getValue().config().get("columns")).size());
    }

    @Test void lastPageStillReportsPartialResultsWithoutTotalOrNextPage() {
        var response = response(List.of(Map.of("month", "6月", "amount", 3)));
        response.setTotal(0L); response.setHasNext(false);
        var page = new SemanticQueryResponse.PaginationInfo(); page.setStart(5); page.setHasMore(false);
        response.setPagination(page); stub(response);
        assertEquals(true, service.export(args(), context).get("truncated"));
        response.setPagination(null);
        for (var paging : List.of(Map.of("start", 5), Map.of("cursor", "synthetic-page-cursor"))) {
            var args = args(); var payload = new LinkedHashMap<>((Map<String, Object>) args.get("payload"));
            payload.putAll(paging); args.put("payload", payload);
            assertEquals(true, service.export(args, context).get("truncated"));
        }
        var captor = ArgumentCaptor.forClass(ChartRenderRequest.class);
        verify(renderer, times(3)).render(captor.capture());
        for (var request : captor.getAllValues()) {
            assertTrue(request.config().get("footer").toString().contains("部分结果"));
            assertFalse(request.config().get("footer").toString().contains("synthetic-page-cursor"));
        }
    }

    @Test void invalidRequestsNeverQueryAndErrorsDoNotEchoRequestData() {
        for (var invalid : List.of(Map.of("kind", "pie"), Map.of("data", "synthetic-private-identity"),
                Map.of("width", 500), Map.of("kind", "bar"),
                Map.of("kind", "line", "xField", "month", "yField", "amount", "height", 519),
                Map.of("columns", List.of(Map.of("field", "_sys_meta"))))) {
            var args = args(); args.putAll(invalid);
            var result = service.export(args, context);
            assertEquals(false, result.get("success"));
            assertFalse(result.toString().contains("synthetic-private-identity"));
        }
        verifyNoInteractions(query, renderer, storage);
    }

    @Test void failedAndEmptyQueriesNeverRender() {
        when(query.executeQuery(anyString(), anyMap(), anyString(), any(), any(), any(), anyMap()))
                .thenReturn(RX.failB("jdbc:private://connection?password=synthetic-private-identity"), RX.success(response(List.of())));
        var denied = service.export(args(), context);
        assertEquals(false, denied.get("success"));
        assertFalse(denied.toString().contains("jdbc:"));
        assertFalse(denied.toString().contains("synthetic-private-identity"));
        assertEquals("NO_QUERY_DATA", ((Map<?,?>) service.export(args(), context).get("error")).get("code"));
        verifyNoInteractions(renderer, storage);
    }

    @Test void requestedFieldCannotIntroduceDataOutsideQuery() {
        when(query.executeQuery(anyString(), anyMap(), anyString(), any(), any(), any(), anyMap()))
                .thenReturn(RX.success(response(List.of(Map.of("month", "1月", "amount", 10)))));
        var args = args(); args.put("columns", List.of(Map.of("field", "missing")));
        assertEquals(false, service.export(args, context).get("success"));
        verifyNoInteractions(renderer, storage);
    }

    @Test void flatPivotUsesCopyAndExcludesTotals() {
        var payload = new LinkedHashMap<String,Object>();
        payload.put("pivot", new LinkedHashMap<>(Map.of("rows", List.of("month"))));
        var args = args(); args.put("payload", payload);
        stub(response(List.of(Map.of("month", "1月", "amount", 10), Map.of("month", "总计", "amount", 10, "_sys_meta", Map.of("isGrandTotal", true)))));
        assertEquals(1, service.export(args, context).get("rowsRendered"));
        assertFalse(((Map<?,?>) payload.get("pivot")).containsKey("outputFormat"));
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(query).executeQuery(eq("Sales"), captor.capture(), eq("execute"), any(), any(), any(), anyMap());
        assertEquals("flat", ((Map<?,?>) captor.getValue().get("pivot")).get("outputFormat"));
    }

    @Test void linkDeliveryUsesExistingStorageWithoutBase64() throws Exception {
        stub(response(List.of(Map.of("month", "1月", "amount", 10))));
        when(storage.save(any(byte[].class), eq("png"), anyString())).thenReturn("https://example.invalid/charts/a.png");
        var args = args(); args.put("delivery", "link");
        Map<?,?> image = (Map<?,?>) service.export(args, context).get("image");
        assertEquals("https://example.invalid/charts/a.png", image.get("url")); assertFalse(image.containsKey("data"));
    }

    private void stub(SemanticQueryResponse response) {
        when(query.executeQuery(anyString(), anyMap(), anyString(), any(), any(), any(), anyMap())).thenReturn(RX.success(response));
        when(renderer.render(any())).thenReturn(new ChartRenderResult(new byte[]{1,2,3}, "png", 1200, 700, "table", "查询结果"));
    }
    private SemanticQueryResponse response(List<Map<String,Object>> rows) {
        var response = new SemanticQueryResponse(); response.setItems(rows); return response;
    }
    private Map<String,Object> args() {
        return new LinkedHashMap<>(Map.of("model", "Sales", "payload", Map.of("columns", List.of("month", "amount"))));
    }
}

package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.ExDefined;
import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.MemberQueryRequest;
import com.foggyframework.dataviewer.domain.MemberQueryResponse;
import com.foggyframework.dataviewer.domain.ViewerDataResponse;
import com.foggyframework.dataviewer.domain.ViewerQueryRequest;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.model.api.QueryFacade;
import com.foggyframework.dataset.model.api.QueryFacadeRequest;
import com.foggyframework.dataset.model.api.QueryFacadeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ViewerApiController 单元测试
 * <p>
 * 使用类型安全的请求类和 QueryFacade
 */
@ExtendWith(MockitoExtension.class)
class ViewerApiControllerTest {

    @Mock
    private QueryCacheService cacheService;

    @Mock
    private QueryFacade queryFacade;

    @Mock
    private com.foggyframework.dataviewer.service.MemberQueryService memberQueryService;

    private ViewerApiController controller;

    private CachedQueryContext validContext;

    @BeforeEach
    void setUp() {
        controller = new ViewerApiController(cacheService, queryFacade, new DatasetProperties(), memberQueryService);

        validContext = CachedQueryContext.builder()
                .queryId("test-query-id")
                .model("orders")
                .title("订单查询")
                .columns(List.of("orderId", "customerId", "amount"))
                .slice(List.of(new SliceRequestDef("status", "=", "pending")))
                .tableConfig(CachedQueryContext.TableConfig.builder()
                        .qmModel("orders")
                        .visibleColumns(List.of("orderId", "customerId", "amount"))
                        .build())
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .estimatedRowCount(1000L)
                .build();
    }

    @Nested
    @DisplayName("获取查询元数据测试")
    class GetQueryMetaTests {

        @Test
        @DisplayName("应返回有效查询的元数据")
        void shouldReturnMetaForValidQuery() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            RX response = controller.getQueryMeta("orders", "test-query-id");

            assertEquals(RX.SUCCESS, response.getCode());
            assertNotNull(response.getData());
            ViewerApiController.QueryMetaResponse meta = (ViewerApiController.QueryMetaResponse) response.getData();
            assertEquals("订单查询", meta.title());
            assertNotNull(meta.tableConfig());
            assertEquals("orders", meta.tableConfig().getQmModel());
            assertEquals(3, meta.tableConfig().getVisibleColumns().size());
            assertEquals(1000L, meta.estimatedRowCount());
        }

        @Test
        @DisplayName("应返回404当查询不存在时")
        void shouldReturn404WhenQueryNotFound() {
            when(cacheService.getQuery("non-existent"))
                    .thenReturn(Optional.empty());

            RX response = controller.getQueryMeta("orders", "non-existent");

            assertEquals(404, response.getCode());
        }
    }

    @Nested
    @DisplayName("查询数据测试")
    class QueryDataTests {

        @Test
        @DisplayName("应返回有效的数据响应")
        void shouldReturnValidDataResponse() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            // 模拟 QueryFacade 返回数据
            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(10, 100));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(10);

            RX response = controller.queryData("orders", "test-query-id", null, null, request);

            assertEquals(RX.SUCCESS, response.getCode());
            assertNotNull(response.getData());
            ViewerDataResponse data = (ViewerDataResponse) response.getData();
            assertTrue(data.isSuccess());
            assertNotNull(data.getItems());
            assertEquals(10, data.getItems().size());
        }

        @Test
        @DisplayName("应返回410当查询过期时")
        void shouldReturn410WhenQueryExpired() {
            when(cacheService.getQuery("expired-query"))
                    .thenReturn(Optional.empty());

            ViewerQueryRequest request = new ViewerQueryRequest();

            RX response = controller.queryData("orders", "expired-query", null, null, request);

            assertEquals(410, response.getCode());
            assertNotNull(response.getData());
            ViewerDataResponse data = (ViewerDataResponse) response.getData();
            assertFalse(data.isSuccess());
        }

        @Test
        @DisplayName("应正确处理分页参数")
        void shouldHandlePaginationCorrectly() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(5, 100));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(20);
            request.setLimit(5);

            RX response = controller.queryData("orders", "test-query-id", null, null, request);

            assertEquals(RX.SUCCESS, response.getCode());
            assertNotNull(response.getData());
            ViewerDataResponse data = (ViewerDataResponse) response.getData();
            assertEquals(5, data.getItems().size());
            assertEquals(20, data.getStart());
            assertEquals(5, data.getLimit());
        }

        @Test
        @DisplayName("应使用默认分页参数")
        void shouldUseDefaultPaginationParams() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(50, 100));

            ViewerQueryRequest request = new ViewerQueryRequest();
            // 不设置分页参数

            RX response = controller.queryData("orders", "test-query-id", null, null, request);

            assertEquals(RX.SUCCESS, response.getCode());
            assertNotNull(response.getData());
            ViewerDataResponse data = (ViewerDataResponse) response.getData();
            assertEquals(50, data.getItems().size()); // 默认50条
        }

        @Test
        @DisplayName("应处理查询错误")
        void shouldHandleQueryError() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            ViewerQueryRequest request = new ViewerQueryRequest();

            RX response = controller.queryData("orders", "test-query-id", null, null, request);

            assertEquals(ExDefined.COMMON_ERROR_CODE, response.getCode());
            assertNotNull(response.getData());
            ViewerDataResponse data = (ViewerDataResponse) response.getData();
            assertFalse(data.isSuccess());
            assertTrue(data.getError().contains("Database connection failed"));
        }

        @Test
        @DisplayName("应优先使用请求头namespace执行queryId查询")
        void shouldUseHeaderNamespaceForQueryData() {
            validContext.setNamespace("cached-ns");
            validContext.setAuthorization("Bearer cached-token");
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(1, 1));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setNamespace("body-ns");
            request.setStart(0);
            request.setLimit(10);

            RX response = controller.queryData("orders", "test-query-id", "Bearer request-token", "header-ns", request);

            assertEquals(RX.SUCCESS, response.getCode());
            ArgumentCaptor<QueryFacadeRequest> captor = ArgumentCaptor.forClass(QueryFacadeRequest.class);
            verify(queryFacade).query(captor.capture());
            assertEquals("Bearer request-token", captor.getValue().getAuthorization());
            assertEquals("header-ns", captor.getValue().getNamespace());
            assertTrue(captor.getValue().isNamespaceProvided());
        }

        @Test
        @DisplayName("应在未传请求namespace时使用缓存namespace")
        void shouldUseCachedNamespaceWhenRequestNamespaceMissing() {
            validContext.setNamespace("cached-ns");
            validContext.setAuthorization("Bearer cached-token");
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(1, 1));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(10);

            RX response = controller.queryData("orders", "test-query-id", null, null, request);

            assertEquals(RX.SUCCESS, response.getCode());
            ArgumentCaptor<QueryFacadeRequest> captor = ArgumentCaptor.forClass(QueryFacadeRequest.class);
            verify(queryFacade).query(captor.capture());
            assertEquals("Bearer cached-token", captor.getValue().getAuthorization());
            assertEquals("cached-ns", captor.getValue().getNamespace());
        }

        @Test
        @DisplayName("queryId查询应透传请求extData且不合并到slice")
        void shouldPassRequestExtDataForQueryData() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(1, 1));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(10);
            request.setExtData(Map.of("suggestionSheetId", "2490136163"));

            RX response = controller.queryData("orders", "test-query-id", null, null, request);

            assertEquals(RX.SUCCESS, response.getCode());
            ArgumentCaptor<QueryFacadeRequest> captor = ArgumentCaptor.forClass(QueryFacadeRequest.class);
            verify(queryFacade).query(captor.capture());
            assertEquals(Map.of("suggestionSheetId", "2490136163"), captor.getValue().getQuery().get("extData"));
            List<?> slices = (List<?>) captor.getValue().getQuery().get("slice");
            assertEquals(1, slices.size());
            assertEquals("status", ((Map<?, ?>) slices.get(0)).get("field"));
        }

        @Test
        @DisplayName("queryId查询应合并缓存extData与请求extData")
        void shouldMergeCachedAndRequestExtDataForQueryData() {
            validContext.setExtData(Map.of("tenantRuntime", "T1", "suggestionSheetId", "old"));
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(1, 1));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setExtData(Map.of("suggestionSheetId", "2490136163"));

            RX response = controller.queryData("orders", "test-query-id", null, null, request);

            assertEquals(RX.SUCCESS, response.getCode());
            ArgumentCaptor<QueryFacadeRequest> captor = ArgumentCaptor.forClass(QueryFacadeRequest.class);
            verify(queryFacade).query(captor.capture());
            assertEquals(Map.of("tenantRuntime", "T1", "suggestionSheetId", "2490136163"),
                    captor.getValue().getQuery().get("extData"));
        }
    }

    @Nested
    @DisplayName("直连查询测试")
    class QueryDirectTests {

        @Test
        @DisplayName("应优先使用请求头namespace执行直连查询")
        void shouldUseHeaderNamespaceForDirectQuery() {
            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(1, 1));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setNamespace("body-ns");
            request.setStart(0);
            request.setLimit(10);
            request.setColumns(List.of("orderId", "salesAmountYuan"));
            request.setExtData(Map.of("suggestionSheetId", "2490136163"));

            RX response = controller.queryDirect("orders", "Bearer token", "header-ns", request);

            assertEquals(RX.SUCCESS, response.getCode());
            ArgumentCaptor<QueryFacadeRequest> captor = ArgumentCaptor.forClass(QueryFacadeRequest.class);
            verify(queryFacade).query(captor.capture());
            assertEquals("Bearer token", captor.getValue().getAuthorization());
            assertEquals("header-ns", captor.getValue().getNamespace());
            assertEquals(List.of("orderId", "salesAmountYuan"), captor.getValue().getQuery().get("columns"));
            assertEquals(Map.of("suggestionSheetId", "2490136163"), captor.getValue().getQuery().get("extData"));
            assertNull(captor.getValue().getQuery().get("slice"));
        }

        @Test
        @DisplayName("直连查询不携带extData时保持为空")
        void shouldKeepExtDataNullWhenDirectRequestMissingIt() {
            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(1, 1));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(10);
            request.setColumns(List.of("orderId"));

            RX response = controller.queryDirect("orders", null, null, request);

            assertEquals(RX.SUCCESS, response.getCode());
            ArgumentCaptor<QueryFacadeRequest> captor = ArgumentCaptor.forClass(QueryFacadeRequest.class);
            verify(queryFacade).query(captor.capture());
            assertNull(captor.getValue().getQuery().get("extData"));
        }

        @Test
        @DisplayName("直连查询缺少columns时应返回明确错误")
        void shouldRejectDirectQueryWithoutColumns() {
            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(10);

            RX<ViewerDataResponse> response = controller.queryDirect("orders", null, null, request);

            assertEquals(ExDefined.COMMON_ERROR_CODE, response.getCode());
            assertEquals("columns 不能为空，直连查询必须显式指定输出列", response.getMsg());
            assertNotNull(response.getData());
            assertFalse(response.getData().isSuccess());
            assertEquals("columns 不能为空，直连查询必须显式指定输出列", response.getData().getError());
            verify(queryFacade, never()).query(any(QueryFacadeRequest.class));
        }

        @Test
        @DisplayName("直连查询columns为空集合时应返回明确错误")
        void shouldRejectDirectQueryWithEmptyColumns() {
            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setColumns(List.of());

            RX<ViewerDataResponse> response = controller.queryDirect("orders", null, null, request);

            assertEquals(ExDefined.COMMON_ERROR_CODE, response.getCode());
            assertEquals("columns 不能为空，直连查询必须显式指定输出列", response.getMsg());
            verify(queryFacade, never()).query(any(QueryFacadeRequest.class));
        }

        @Test
        @DisplayName("直连查询columns仅包含空白列名时应返回明确错误")
        void shouldRejectDirectQueryWithBlankColumns() {
            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setColumns(List.of(" ", "\t"));

            RX<ViewerDataResponse> response = controller.queryDirect("orders", null, null, request);

            assertEquals(ExDefined.COMMON_ERROR_CODE, response.getCode());
            assertEquals("columns 不能为空，直连查询必须显式指定输出列", response.getMsg());
            verify(queryFacade, never()).query(any(QueryFacadeRequest.class));
        }

        @Test
        @DisplayName("直连查询应忽略空白列名并传入规范化列")
        void shouldNormalizeDirectQueryColumns() {
            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenReturn(queryResult(1, 1));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setColumns(List.of(" orderId ", "", "salesAmountYuan"));

            RX response = controller.queryDirect("orders", null, null, request);

            assertEquals(RX.SUCCESS, response.getCode());
            ArgumentCaptor<QueryFacadeRequest> captor = ArgumentCaptor.forClass(QueryFacadeRequest.class);
            verify(queryFacade).query(captor.capture());
            assertEquals(List.of("orderId", "salesAmountYuan"), captor.getValue().getQuery().get("columns"));
        }

        @Test
        @DisplayName("直连查询业务异常应保留业务错误码并返回错误数据")
        void shouldReturnBusinessErrorForDirectQueryBusinessException() {
            when(queryFacade.query(any(QueryFacadeRequest.class)))
                    .thenThrow(new ExRuntimeExceptionImpl(46001, "字段不存在: fleetName"));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(10);
            request.setColumns(List.of("vehicleId", "fleetName"));

            RX<ViewerDataResponse> response = controller.queryDirect("VehicleManagementQuery", null, null, request);

            assertEquals(46001, response.getCode());
            assertEquals("字段不存在: fleetName", response.getMsg());
            assertNotNull(response.getData());
            assertFalse(response.getData().isSuccess());
            assertEquals("字段不存在: fleetName", response.getData().getError());
            verify(queryFacade).query(any(QueryFacadeRequest.class));
        }
    }

    @Nested
    @DisplayName("成员查询测试")
    class QueryMembersTests {

        @Test
        @DisplayName("应透传解析后的namespace到成员查询服务")
        void shouldPassNamespaceToMemberQueryService() {
            MemberQueryResponse memberResponse = MemberQueryResponse.builder()
                    .qmModel("orders")
                    .fieldName("customer")
                    .items(List.of())
                    .total(0)
                    .build();
            when(memberQueryService.query(any(MemberQueryRequest.class), eq("header-ns")))
                    .thenReturn(memberResponse);

            MemberQueryRequest request = new MemberQueryRequest();
            request.setQmModel("orders");
            request.setFieldName("customer");
            request.setNamespace("body-ns");

            RX<MemberQueryResponse> response = controller.queryMembers("header-ns", request);

            assertEquals(RX.SUCCESS, response.getCode());
            ArgumentCaptor<MemberQueryRequest> captor = ArgumentCaptor.forClass(MemberQueryRequest.class);
            verify(memberQueryService).query(captor.capture(), eq("header-ns"));
            assertEquals("header-ns", captor.getValue().getNamespace());
        }
    }

    /**
     * 生成模拟数据
     */
    private List<Map<String, Object>> generateMockItems(int count) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("orderId", "ORD-" + i);
            item.put("customerId", "CUST-" + i);
            item.put("amount", i * 100.0);
            items.add(item);
        }
        return items;
    }

    private QueryFacadeResult queryResult(int count, long total) {
        return new QueryFacadeResult(total, count < total, 0, count, generateMockItems(count), null);
    }
}

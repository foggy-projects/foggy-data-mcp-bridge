package com.foggyframework.dataviewer.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.ViewerDataResponse;
import com.foggyframework.dataviewer.domain.ViewerQueryRequest;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private ViewerApiController controller;

    private CachedQueryContext validContext;

    @BeforeEach
    void setUp() {
        controller = new ViewerApiController(cacheService, queryFacade);

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

            RX response = controller.getQueryMeta("test-query-id");

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

            RX response = controller.getQueryMeta("non-existent");

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
            PagingResultImpl mockResult = new PagingResultImpl();
            mockResult.setItems(generateMockItems(10));
            mockResult.setTotal(100);
            when(queryFacade.queryModelData(any(PagingRequest.class)))
                    .thenReturn(mockResult);

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(10);

            RX response = controller.queryData("test-query-id", request);

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

            RX response = controller.queryData("expired-query", request);

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

            PagingResultImpl mockResult = new PagingResultImpl();
            mockResult.setItems(generateMockItems(5));
            mockResult.setTotal(100);
            when(queryFacade.queryModelData(any(PagingRequest.class)))
                    .thenReturn(mockResult);

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(20);
            request.setLimit(5);

            RX response = controller.queryData("test-query-id", request);

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

            PagingResultImpl mockResult = new PagingResultImpl();
            mockResult.setItems(generateMockItems(50));
            mockResult.setTotal(100);
            when(queryFacade.queryModelData(any(PagingRequest.class)))
                    .thenReturn(mockResult);

            ViewerQueryRequest request = new ViewerQueryRequest();
            // 不设置分页参数

            RX response = controller.queryData("test-query-id", request);

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

            when(queryFacade.queryModelData(any(PagingRequest.class)))
                    .thenThrow(new RuntimeException("Database connection failed"));

            ViewerQueryRequest request = new ViewerQueryRequest();

            RX response = controller.queryData("test-query-id", request);

            assertEquals(RX.SUCCESS, response.getCode());
            assertNotNull(response.getData());
            ViewerDataResponse data = (ViewerDataResponse) response.getData();
            assertFalse(data.isSuccess());
            assertTrue(data.getError().contains("Database connection failed"));
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
}

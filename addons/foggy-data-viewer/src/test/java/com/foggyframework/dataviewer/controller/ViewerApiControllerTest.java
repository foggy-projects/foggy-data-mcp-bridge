package com.foggyframework.dataviewer.controller;

import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.domain.ViewerQueryRequest;
import com.foggyframework.dataviewer.service.QueryCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * ViewerApiController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ViewerApiControllerTest {

    @Mock
    private QueryCacheService cacheService;

    @InjectMocks
    private ViewerApiController controller;

    private CachedQueryContext validContext;

    @BeforeEach
    void setUp() {
        validContext = new CachedQueryContext();
        validContext.setQueryId("test-query-id");
        validContext.setModel("orders");
        validContext.setTitle("订单查询");
        validContext.setColumns(List.of("orderId", "customerId", "amount"));
        validContext.setSchema(List.of(
                new CachedQueryContext.ColumnSchema("orderId", "TEXT", "订单ID"),
                new CachedQueryContext.ColumnSchema("customerId", "TEXT", "客户ID"),
                new CachedQueryContext.ColumnSchema("amount", "MONEY", "金额")
        ));
        validContext.setDsl(Map.of("slice", Map.of("status", "pending")));
        validContext.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        validContext.setEstimatedRowCount(1000L);
    }

    @Nested
    @DisplayName("获取查询元数据测试")
    class GetQueryMetaTests {

        @Test
        @DisplayName("应返回有效查询的元数据")
        void shouldReturnMetaForValidQuery() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            ResponseEntity<ViewerApiController.QueryMetaResponse> response =
                    controller.getQueryMeta("test-query-id");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("订单查询", response.getBody().title());
            assertEquals("orders", response.getBody().model());
            assertEquals(3, response.getBody().columns().size());
            assertEquals(3, response.getBody().schema().size());
            assertEquals(1000L, response.getBody().estimatedRowCount());
        }

        @Test
        @DisplayName("应返回404当查询不存在时")
        void shouldReturn404WhenQueryNotFound() {
            when(cacheService.getQuery("non-existent"))
                    .thenReturn(Optional.empty());

            ResponseEntity<ViewerApiController.QueryMetaResponse> response =
                    controller.getQueryMeta("non-existent");

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
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

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(10);

            var response = controller.queryData("test-query-id", request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertNotNull(response.getBody().getItems());
            assertEquals(10, response.getBody().getItems().size());
        }

        @Test
        @DisplayName("应返回410当查询过期时")
        void shouldReturn410WhenQueryExpired() {
            when(cacheService.getQuery("expired-query"))
                    .thenReturn(Optional.empty());

            ViewerQueryRequest request = new ViewerQueryRequest();

            var response = controller.queryData("expired-query", request);

            assertEquals(HttpStatus.GONE, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isExpired());
        }

        @Test
        @DisplayName("应正确处理分页参数")
        void shouldHandlePaginationCorrectly() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(20);
            request.setLimit(5);

            var response = controller.queryData("test-query-id", request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(5, response.getBody().getItems().size());
            assertEquals(20, response.getBody().getStart());
            assertEquals(5, response.getBody().getLimit());
        }

        @Test
        @DisplayName("应使用默认分页参数")
        void shouldUseDefaultPaginationParams() {
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            ViewerQueryRequest request = new ViewerQueryRequest();
            // 不设置分页参数

            var response = controller.queryData("test-query-id", request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(50, response.getBody().getItems().size()); // 默认50条
        }
    }

    @Nested
    @DisplayName("Mock数据生成测试")
    class MockDataGenerationTests {

        @Test
        @DisplayName("应根据列类型生成对应格式的数据")
        void shouldGenerateDataBasedOnColumnType() {
            validContext.setColumns(List.of("orderId", "orderDate", "amount", "customerName"));
            when(cacheService.getQuery("test-query-id"))
                    .thenReturn(Optional.of(validContext));

            ViewerQueryRequest request = new ViewerQueryRequest();
            request.setStart(0);
            request.setLimit(5);

            var response = controller.queryData("test-query-id", request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());

            var items = response.getBody().getItems();
            assertFalse(items.isEmpty());

            var firstItem = items.get(0);
            assertTrue(firstItem.containsKey("orderId"));
            assertTrue(firstItem.containsKey("orderDate"));
            assertTrue(firstItem.containsKey("amount"));
            assertTrue(firstItem.containsKey("customerName"));

            // 验证ID格式
            assertTrue(firstItem.get("orderId").toString().startsWith("ID-"));

            // 验证日期格式
            assertTrue(firstItem.get("orderDate").toString().matches("\\d{4}-\\d{2}-\\d{2}"));

            // 验证金额是数字
            assertTrue(firstItem.get("amount") instanceof Number);

            // 验证名称格式
            assertTrue(firstItem.get("customerName").toString().startsWith("Item "));
        }
    }
}

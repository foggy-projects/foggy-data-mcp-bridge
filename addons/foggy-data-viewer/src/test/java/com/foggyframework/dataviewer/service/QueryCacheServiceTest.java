package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.repository.CachedQueryRepository;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * QueryCacheService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class QueryCacheServiceTest {

    @Mock
    private CachedQueryRepository repository;

    private DataViewerProperties properties;
    private QueryCacheService service;

    @BeforeEach
    void setUp() {
        properties = new DataViewerProperties();
        properties.setBaseUrl("http://localhost:8080/data-viewer");
        properties.setCache(new DataViewerProperties.CacheProperties());
        properties.getCache().setTtlMinutes(60);

        service = new QueryCacheService(repository, properties);
    }

    @Nested
    @DisplayName("查询缓存测试")
    class CacheQueryTests {

        @Test
        @DisplayName("应正确缓存查询并返回CachedQueryContext")
        void shouldCacheQueryAndReturnContext() {
            QueryCacheService.OpenInViewerRequest request = new QueryCacheService.OpenInViewerRequest();
            request.setModel("orders");
            request.setTitle("订单查询");
            request.setColumns(List.of("orderId", "customerId", "amount"));
            request.setSlice(createSlice("customerId", "=", "C001"));

            when(repository.save(any(CachedQueryContext.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CachedQueryContext result = service.cacheQuery(request, "Bearer test-token");

            assertNotNull(result);
            assertEquals("orders", result.getModel());
            assertEquals("订单查询", result.getTitle());
            assertEquals(3, result.getColumns().size());
            assertNotNull(result.getQueryId());
            assertNotNull(result.getExpiresAt());

            verify(repository).save(any(CachedQueryContext.class));
        }

        @Test
        @DisplayName("应正确设置过期时间")
        void shouldSetCorrectExpirationTime() {
            QueryCacheService.OpenInViewerRequest request = new QueryCacheService.OpenInViewerRequest();
            request.setModel("orders");
            request.setTitle("订单查询");
            request.setColumns(List.of("orderId"));
            request.setSlice(createSlice("status", "=", "pending"));

            when(repository.save(any(CachedQueryContext.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Instant beforeCall = Instant.now();
            CachedQueryContext result = service.cacheQuery(request, null);
            Instant afterCall = Instant.now();

            Instant expiresAt = result.getExpiresAt();

            // 验证过期时间在预期范围内（当前时间 + 60分钟）
            Instant expectedMin = beforeCall.plus(60, ChronoUnit.MINUTES);
            Instant expectedMax = afterCall.plus(60, ChronoUnit.MINUTES);

            assertTrue(expiresAt.isAfter(expectedMin) || expiresAt.equals(expectedMin));
            assertTrue(expiresAt.isBefore(expectedMax) || expiresAt.equals(expectedMax));
        }

        @Test
        @DisplayName("应保存授权信息")
        void shouldSaveAuthorization() {
            QueryCacheService.OpenInViewerRequest request = new QueryCacheService.OpenInViewerRequest();
            request.setModel("orders");
            request.setColumns(List.of("orderId"));
            request.setSlice(createSlice("id", "=", "1"));

            when(repository.save(any(CachedQueryContext.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CachedQueryContext result = service.cacheQuery(request, "Bearer my-token");

            assertEquals("Bearer my-token", result.getAuthorization());
        }
    }

    @Nested
    @DisplayName("查询获取测试")
    class GetQueryTests {

        @Test
        @DisplayName("应正确获取存在的查询")
        void shouldGetExistingQuery() {
            String queryId = "test-query-id";
            CachedQueryContext context = new CachedQueryContext();
            context.setQueryId(queryId);
            context.setModel("orders");
            context.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));

            when(repository.findByQueryIdAndExpiresAtAfter(eq(queryId), any(Instant.class)))
                    .thenReturn(Optional.of(context));

            Optional<CachedQueryContext> result = service.getQuery(queryId);

            assertTrue(result.isPresent());
            assertEquals(queryId, result.get().getQueryId());
        }

        @Test
        @DisplayName("应返回空Optional当查询不存在时")
        void shouldReturnEmptyWhenQueryNotFound() {
            String queryId = "non-existent-id";

            when(repository.findByQueryIdAndExpiresAtAfter(eq(queryId), any(Instant.class)))
                    .thenReturn(Optional.empty());

            Optional<CachedQueryContext> result = service.getQuery(queryId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("Schema生成测试")
    class SchemaGenerationTests {

        @Test
        @DisplayName("应为列生成正确的Schema")
        void shouldGenerateCorrectSchema() {
            QueryCacheService.OpenInViewerRequest request = new QueryCacheService.OpenInViewerRequest();
            request.setModel("orders");
            request.setTitle("订单查询");
            request.setColumns(List.of("orderId", "orderDate", "amount", "customerId"));
            request.setSlice(createSlice("status", "=", "pending"));

            when(repository.save(any(CachedQueryContext.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CachedQueryContext result = service.cacheQuery(request, null);

            List<CachedQueryContext.ColumnSchema> schema = result.getSchema();

            assertNotNull(schema);
            assertEquals(4, schema.size());

            // 验证列名
            assertTrue(schema.stream().anyMatch(s -> s.getName().equals("orderId")));
            assertTrue(schema.stream().anyMatch(s -> s.getName().equals("orderDate")));
            assertTrue(schema.stream().anyMatch(s -> s.getName().equals("amount")));
            assertTrue(schema.stream().anyMatch(s -> s.getName().equals("customerId")));
        }
    }

    @Nested
    @DisplayName("QueryId生成测试")
    class QueryIdGenerationTests {

        @Test
        @DisplayName("应生成16字符的QueryId")
        void shouldGenerate16CharQueryId() {
            QueryCacheService.OpenInViewerRequest request = new QueryCacheService.OpenInViewerRequest();
            request.setModel("orders");
            request.setColumns(List.of("id"));
            request.setSlice(createSlice("id", "=", "1"));

            when(repository.save(any(CachedQueryContext.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            CachedQueryContext result = service.cacheQuery(request, null);

            assertEquals(16, result.getQueryId().length());
            assertTrue(result.getQueryId().matches("[a-f0-9]+"));
        }
    }

    @Nested
    @DisplayName("更新预估行数测试")
    class UpdateEstimatedRowCountTests {

        @Test
        @DisplayName("应更新存在的查询的预估行数")
        void shouldUpdateEstimatedRowCount() {
            String queryId = "test-query-id";
            CachedQueryContext context = new CachedQueryContext();
            context.setQueryId(queryId);
            context.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));

            when(repository.findByQueryIdAndExpiresAtAfter(eq(queryId), any(Instant.class)))
                    .thenReturn(Optional.of(context));
            when(repository.save(any(CachedQueryContext.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.updateEstimatedRowCount(queryId, 5000L);

            assertEquals(5000L, context.getEstimatedRowCount());
            verify(repository).save(context);
        }

        @Test
        @DisplayName("当查询不存在时不应更新")
        void shouldNotUpdateWhenQueryNotFound() {
            when(repository.findByQueryIdAndExpiresAtAfter(anyString(), any(Instant.class)))
                    .thenReturn(Optional.empty());

            service.updateEstimatedRowCount("non-existent", 5000L);

            verify(repository, never()).save(any());
        }
    }

    private List<Map<String, Object>> createSlice(String field, String op, String value) {
        List<Map<String, Object>> slice = new ArrayList<>();
        Map<String, Object> filter = new HashMap<>();
        filter.put("field", field);
        filter.put("op", op);
        filter.put("value", value);
        slice.add(filter);
        return slice;
    }
}

package com.foggyframework.dataviewer.plugins;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.CachedQueryContext;
import com.foggyframework.dataviewer.service.QueryCacheService;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.DataSetResultStep;
import com.foggyframework.dataset.db.model.plugins.result_set_filter.ModelResultContext;
import com.foggyframework.dataset.db.model.spi.QueryModel;
import com.foggyframework.dataset.model.PagingResultImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * LargeResultTruncationStep 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LargeResultTruncationStepTest {

    @Mock
    private QueryCacheService queryCacheService;

    @Mock
    private QueryModel queryModel;

    private DataViewerProperties properties;
    private LargeResultTruncationStep step;

    @BeforeEach
    void setUp() {
        properties = new DataViewerProperties();
        properties.setBaseUrl("http://localhost:8080/data-viewer");

        // 配置阈值
        DataViewerProperties.ThresholdProperties thresholds = new DataViewerProperties.ThresholdProperties();
        thresholds.setCellThresholdForTruncation(10000); // 10000 单元格
        thresholds.setTruncatedRowLimit(100); // 截断到 100 行
        properties.setThresholds(thresholds);

        step = new LargeResultTruncationStep(queryCacheService, properties);
    }

    @Nested
    @DisplayName("非 MCP 查询测试")
    class NonMcpQueryTests {

        @Test
        @DisplayName("非 MCP 查询不应触发截断")
        void shouldNotTruncateNonMcpQuery() {
            ModelResultContext ctx = createContext(false, 200, 100);

            int result = step.process(ctx);

            assertEquals(DataSetResultStep.CONTINUE, result);
            assertNull(ctx.getExtData().get("truncationInfo"));
            assertEquals(200, ctx.getPagingResult().getItems().size());
        }

        @Test
        @DisplayName("没有 extData 的查询不应触发截断")
        void shouldNotTruncateWhenExtDataIsNull() {
            ModelResultContext ctx = createContext(false, 200, 100);
            ctx.setExtData(null); // 清空 extData

            int result = step.process(ctx);

            assertEquals(DataSetResultStep.CONTINUE, result);
        }
    }

    @Nested
    @DisplayName("数据量未超过阈值测试")
    class BelowThresholdTests {

        @Test
        @DisplayName("数据量未超过阈值不应触发截断")
        void shouldNotTruncateWhenBelowThreshold() {
            // 100 行 × 10 列 = 1000 单元格 < 10000
            ModelResultContext ctx = createContext(true, 100, 10);

            int result = step.process(ctx);

            assertEquals(DataSetResultStep.CONTINUE, result);
            assertNull(ctx.getExtData().get("truncationInfo"));
            assertEquals(100, ctx.getPagingResult().getItems().size());
        }

        @Test
        @DisplayName("空结果集不应触发截断")
        void shouldNotTruncateEmptyResult() {
            ModelResultContext ctx = createContext(true, 0, 10);

            int result = step.process(ctx);

            assertEquals(DataSetResultStep.CONTINUE, result);
            assertNull(ctx.getExtData().get("truncationInfo"));
        }

        @Test
        @DisplayName("恰好等于阈值不应触发截断")
        void shouldNotTruncateWhenExactlyAtThreshold() {
            // 100 行 × 100 列 = 10000 单元格 = 阈值
            ModelResultContext ctx = createContext(true, 100, 100);

            int result = step.process(ctx);

            assertEquals(DataSetResultStep.CONTINUE, result);
            assertNull(ctx.getExtData().get("truncationInfo"));
            assertEquals(100, ctx.getPagingResult().getItems().size());
        }
    }

    @Nested
    @DisplayName("数据量超过阈值测试")
    class AboveThresholdTests {

        @Test
        @DisplayName("数据量超过阈值应触发截断")
        void shouldTruncateWhenAboveThreshold() {
            // 200 行 × 100 列 = 20000 单元格 > 10000
            ModelResultContext ctx = createContext(true, 200, 100);

            // Mock QueryCacheService
            CachedQueryContext cachedQuery = CachedQueryContext.builder()
                    .queryId("test-query-id-123")
                    .model("test_model")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            when(queryCacheService.cacheQuery(any(), any())).thenReturn(cachedQuery);

            int result = step.process(ctx);

            assertEquals(DataSetResultStep.CONTINUE, result);

            // 验证数据被截断到 100 行
            assertEquals(100, ctx.getPagingResult().getItems().size());

            // 验证 truncationInfo 存在
            assertNotNull(ctx.getExtData().get("truncationInfo"));
            @SuppressWarnings("unchecked")
            Map<String, Object> truncationInfo = (Map<String, Object>) ctx.getExtData().get("truncationInfo");

            assertTrue((Boolean) truncationInfo.get("truncated"));
            assertEquals(200, truncationInfo.get("originalRowCount"));
            assertEquals(100, truncationInfo.get("truncatedRowCount"));
            assertEquals(100, truncationInfo.get("columnCount"));
            assertEquals(20000L, truncationInfo.get("cellCount"));
            assertNotNull(truncationInfo.get("message"));
            assertNotNull(truncationInfo.get("viewerUrl"));
            assertNotNull(truncationInfo.get("apiUrl"));
            assertNotNull(truncationInfo.get("hint"));

            // 验证链接格式
            String viewerUrl = (String) truncationInfo.get("viewerUrl");
            String apiUrl = (String) truncationInfo.get("apiUrl");
            assertTrue(viewerUrl.contains("test-query-id-123"));
            assertTrue(apiUrl.contains("test-query-id-123"));

            // 验证调用了 QueryCacheService
            verify(queryCacheService).cacheQuery(any(), any());
        }

        @Test
        @DisplayName("截断后保留的行数应正确")
        void shouldKeepCorrectNumberOfRows() {
            // 500 行 × 50 列 = 25000 单元格 > 10000
            ModelResultContext ctx = createContext(true, 500, 50);

            CachedQueryContext cachedQuery = CachedQueryContext.builder()
                    .queryId("test-query-id")
                    .model("test_model")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            when(queryCacheService.cacheQuery(any(), any())).thenReturn(cachedQuery);

            step.process(ctx);

            // 应该截断到 100 行
            assertEquals(100, ctx.getPagingResult().getItems().size());

            // 验证保留的是前 100 行
            List<?> items = ctx.getPagingResult().getItems();
            for (int i = 0; i < 100; i++) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) items.get(i);
                assertEquals("row-" + i, row.get("id"));
            }
        }

        @Test
        @DisplayName("截断信息的消息应正确格式化")
        void shouldFormatMessageCorrectly() {
            ModelResultContext ctx = createContext(true, 1000, 20);

            CachedQueryContext cachedQuery = CachedQueryContext.builder()
                    .queryId("test-query-id")
                    .model("test_model")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            when(queryCacheService.cacheQuery(any(), any())).thenReturn(cachedQuery);

            step.process(ctx);

            @SuppressWarnings("unchecked")
            Map<String, Object> truncationInfo = (Map<String, Object>) ctx.getExtData().get("truncationInfo");
            String message = (String) truncationInfo.get("message");

            assertTrue(message.contains("1000 行"));
            assertTrue(message.contains("20 列"));
            assertTrue(message.contains("20000 单元格"));
            assertTrue(message.contains("100 行"));
        }

        @Test
        @DisplayName("缓存服务失败不应影响主流程")
        void shouldContinueWhenCacheServiceFails() {
            ModelResultContext ctx = createContext(true, 200, 100);

            // Mock 缓存服务抛出异常
            when(queryCacheService.cacheQuery(any(), any()))
                    .thenThrow(new RuntimeException("Cache service error"));

            int result = step.process(ctx);

            // 应该继续执行
            assertEquals(DataSetResultStep.CONTINUE, result);

            // 数据仍然被截断
            assertEquals(100, ctx.getPagingResult().getItems().size());

            // truncationInfo 存在，但没有链接
            assertNotNull(ctx.getExtData().get("truncationInfo"));
            @SuppressWarnings("unchecked")
            Map<String, Object> truncationInfo = (Map<String, Object>) ctx.getExtData().get("truncationInfo");

            assertTrue((Boolean) truncationInfo.get("truncated"));
            // 链接字段不存在（因为缓存失败）
            assertNull(truncationInfo.get("viewerUrl"));
            assertNull(truncationInfo.get("apiUrl"));
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("结果集为 null 不应触发截断")
        void shouldNotTruncateWhenResultIsNull() {
            ModelResultContext ctx = createContext(true, 0, 0);
            ctx.setPagingResult(null);

            int result = step.process(ctx);

            assertEquals(DataSetResultStep.CONTINUE, result);
        }

        @Test
        @DisplayName("items 为 null 不应触发截断")
        void shouldNotTruncateWhenItemsIsNull() {
            ModelResultContext ctx = createContext(true, 0, 0);
            ctx.getPagingResult().setItems(null);

            int result = step.process(ctx);

            assertEquals(DataSetResultStep.CONTINUE, result);
        }

        @Test
        @DisplayName("原始行数小于截断限制时不应出错")
        void shouldHandleRowCountLessThanLimit() {
            // 50 行 × 300 列 = 15000 单元格 > 10000，但行数 < 100
            ModelResultContext ctx = createContext(true, 50, 300);

            CachedQueryContext cachedQuery = CachedQueryContext.builder()
                    .queryId("test-query-id")
                    .model("test_model")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            when(queryCacheService.cacheQuery(any(), any())).thenReturn(cachedQuery);

            step.process(ctx);

            // 应该保留所有 50 行（不截断到更少）
            assertEquals(50, ctx.getPagingResult().getItems().size());

            @SuppressWarnings("unchecked")
            Map<String, Object> truncationInfo = (Map<String, Object>) ctx.getExtData().get("truncationInfo");

            assertEquals(50, truncationInfo.get("originalRowCount"));
            assertEquals(100, truncationInfo.get("truncatedRowLimit")); // 配置的限制
        }

        @Test
        @DisplayName("自定义 baseUrl 应正确生成链接")
        void shouldGenerateLinkWithCustomBaseUrl() {
            properties.setBaseUrl("https://example.com/viewer");

            ModelResultContext ctx = createContext(true, 200, 100);

            CachedQueryContext cachedQuery = CachedQueryContext.builder()
                    .queryId("abc123")
                    .model("test_model")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            when(queryCacheService.cacheQuery(any(), any())).thenReturn(cachedQuery);

            step.process(ctx);

            @SuppressWarnings("unchecked")
            Map<String, Object> truncationInfo = (Map<String, Object>) ctx.getExtData().get("truncationInfo");

            String viewerUrl = (String) truncationInfo.get("viewerUrl");
            String apiUrl = (String) truncationInfo.get("apiUrl");

            assertEquals("https://example.com/viewer/view/abc123", viewerUrl);
            assertEquals("https://example.com/viewer/api/query/abc123/data", apiUrl);
        }
    }

    // ========== Helper Methods ==========

    /**
     * 创建测试用的 ModelResultContext
     *
     * @param fromMcp     是否来自 MCP
     * @param rowCount    行数
     * @param columnCount 列数
     * @return ModelResultContext
     */
    private ModelResultContext createContext(boolean fromMcp, int rowCount, int columnCount) {
        ModelResultContext ctx = new ModelResultContext();

        // 设置 extData
        Map<String, Object> extData = new HashMap<>();
        if (fromMcp) {
            extData.put("fromMcp", true);
        }
        ctx.setExtData(extData);

        // 设置查询结果
        PagingResultImpl pagingResult = new PagingResultImpl();
        List<Map<String, Object>> items = new ArrayList<>();

        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            // 第一列作为 id
            row.put("id", "row-" + i);
            // 剩余列（总共 columnCount 列，包括 id）
            for (int j = 1; j < columnCount; j++) {
                row.put("col" + j, "value-" + i + "-" + j);
            }
            items.add(row);
        }

        pagingResult.setItems(items);
        pagingResult.setTotal((long) rowCount);
        pagingResult.setStart(0);
        pagingResult.setLimit(rowCount);
        ctx.setPagingResult(pagingResult);

        // 设置请求
        PagingRequest<DbQueryRequestDef> request = new PagingRequest<>();
        DbQueryRequestDef queryDef = new DbQueryRequestDef();

        List<String> columns = new ArrayList<>();
        for (int i = 0; i < columnCount; i++) {
            columns.add("col" + i);
        }
        queryDef.setColumns(columns);

        request.setParam(queryDef);
        ctx.setRequest(request);

        // 设置 QueryModel
        when(queryModel.getName()).thenReturn("test_model");
        ctx.setQueryModel(queryModel);

        return ctx;
    }
}

package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryRequest;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryResponse;
import com.foggyframework.dataset.mcp.service.ProgressEvent;
import com.foggyframework.dataset.mcp.service.QueryExpertService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NaturalLanguageQueryTool 单元测试
 *
 * 使用 Mockito 模拟 QueryExpertService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NaturalLanguageQueryTool 单元测试")
class NaturalLanguageQueryToolTest {

    @Mock
    private QueryExpertService queryExpertService;

    @InjectMocks
    private NaturalLanguageQueryTool nlQueryTool;

    // ==================== 基本属性测试 ====================

    @Nested
    @DisplayName("工具基本属性")
    class BasicPropertiesTest {

        @Test
        @DisplayName("getName 应返回正确的工具名称")
        void getName_shouldReturnCorrectName() {
            assertEquals("dataset_nl.query", nlQueryTool.getName());
        }

        @Test
        @DisplayName("getCategories 应返回 NATURAL_LANGUAGE 类别")
        void getCategories_shouldReturnNLCategory() {
            assertTrue(nlQueryTool.getCategories().contains(ToolCategory.NATURAL_LANGUAGE));
            assertEquals(1, nlQueryTool.getCategories().size());
        }

        @Test
        @DisplayName("supportsStreaming 应返回 true")
        void supportsStreaming_shouldReturnTrue() {
            assertTrue(nlQueryTool.supportsStreaming());
        }

        // Note: getDescription() and getInputSchema() now load from config files,
        // they are tested in integration tests with ToolConfigLoader
    }

    // ==================== execute 成功场景测试 ====================

    @Nested
    @DisplayName("execute - 成功场景")
    class ExecuteSuccessTest {

        @Test
        @DisplayName("简单查询应成功执行")
        void simpleQuery_shouldExecuteSuccessfully() {
            DatasetNLQueryResponse mockResponse = DatasetNLQueryResponse.builder()
                    .type("result")
                    .items(List.of(
                            Map.of("team", "Team A", "sales", 50000),
                            Map.of("team", "Team B", "sales", 30000)
                    ))
                    .total(2L)
                    .summary("查询完成，共返回 2 条数据")
                    .build();

            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), eq("trace-1"), any()))
                    .thenReturn(mockResponse);

            Map<String, Object> args = Map.of("query", "每个团队的销售额");

            Object result = nlQueryTool.execute(args, "trace-1", null);

            assertNotNull(result);
            assertInstanceOf(DatasetNLQueryResponse.class, result);

            DatasetNLQueryResponse response = (DatasetNLQueryResponse) result;
            assertEquals("result", response.getType());
            assertEquals(2, response.getTotal());

            verify(queryExpertService).processQuery(argThat(req ->
                    req.getQuery().equals("每个团队的销售额")
            ), eq("trace-1"), any());
        }

        @Test
        @DisplayName("带 session_id 的查询应正确传递")
        void queryWithSessionId_shouldPassCorrectly() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.builder().type("result").build());

            Map<String, Object> args = Map.of(
                    "query", "继续上次的查询",
                    "session_id", "session-abc-123"
            );

            nlQueryTool.execute(args, "trace-2", null);

            verify(queryExpertService).processQuery(argThat(req ->
                    "session-abc-123".equals(req.getSessionId())
            ), any(), any());
        }

        @Test
        @DisplayName("带格式参数的查询应正确传递")
        void queryWithFormat_shouldPassCorrectly() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.builder().type("result").build());

            Map<String, Object> args = Map.of(
                    "query", "销售数据概要",
                    "format", "summary"
            );

            nlQueryTool.execute(args, "trace-3", null);

            verify(queryExpertService).processQuery(argThat(req ->
                    "summary".equals(req.getFormat())
            ), any(), any());
        }

        @Test
        @DisplayName("默认格式应为 table")
        void defaultFormat_shouldBeTable() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.builder().type("result").build());

            Map<String, Object> args = Map.of("query", "查询数据");

            nlQueryTool.execute(args, "trace-4", null);

            verify(queryExpertService).processQuery(argThat(req ->
                    "table".equals(req.getFormat())
            ), any(), any());
        }

        @Test
        @DisplayName("带 hints 的查询应正确传递")
        void queryWithHints_shouldPassCorrectly() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.builder().type("result").build());

            Map<String, Object> hints = Map.of(
                    "time_range", Map.of(
                            "preset", "last_7_days",
                            "start", "2025-01-01",
                            "end", "2025-01-07"
                    ),
                    "data_source", "dataset",
                    "preferred_models", List.of("SalesModel", "CustomerModel")
            );

            Map<String, Object> args = Map.of(
                    "query", "最近一周销售数据",
                    "hints", hints
            );

            nlQueryTool.execute(args, "trace-5", null);

            verify(queryExpertService).processQuery(argThat(req -> {
                DatasetNLQueryRequest.QueryHints h = req.getHints();
                return h != null &&
                       h.getTimeRange() != null &&
                       "last_7_days".equals(h.getTimeRange().getPreset()) &&
                       "dataset".equals(h.getDataSource()) &&
                       h.getPreferredModels().contains("SalesModel");
            }), any(), any());
        }

        @Test
        @DisplayName("带分页游标的查询应正确传递")
        void queryWithCursor_shouldPassCorrectly() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.builder().type("result").build());

            Map<String, Object> args = Map.of(
                    "query", "继续加载更多数据",
                    "cursor", "cursor_page_2"
            );

            nlQueryTool.execute(args, "trace-6", null);

            verify(queryExpertService).processQuery(argThat(req ->
                    "cursor_page_2".equals(req.getCursor())
            ), any(), any());
        }

        @Test
        @DisplayName("stream 参数应正确传递")
        void streamParam_shouldPassCorrectly() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.builder().type("result").build());

            Map<String, Object> args = Map.of(
                    "query", "查询数据",
                    "stream", false
            );

            nlQueryTool.execute(args, "trace-7", null);

            verify(queryExpertService).processQuery(argThat(req ->
                    !req.getStream()
            ), any(), any());
        }
    }

    // ==================== execute 错误场景测试 ====================

    @Nested
    @DisplayName("execute - 错误场景")
    class ExecuteErrorTest {

        @Test
        @DisplayName("服务异常应返回错误响应")
        void serviceException_shouldReturnErrorResponse() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenThrow(new RuntimeException("AI service unavailable"));

            Map<String, Object> args = Map.of("query", "测试查询");

            Object result = nlQueryTool.execute(args, "trace-error-1", null);

            assertNotNull(result);
            assertInstanceOf(DatasetNLQueryResponse.class, result);

            DatasetNLQueryResponse response = (DatasetNLQueryResponse) result;
            assertEquals("error", response.getType());
            assertTrue(response.getCode().contains("QUERY_FAILED"));
            assertTrue(response.getMsg().contains("AI service unavailable"));
        }

        @Test
        @DisplayName("空查询应处理（依赖服务验证）")
        void emptyQuery_shouldBeHandledByService() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.error("INVALID_QUERY", "查询不能为空", null));

            Map<String, Object> args = new HashMap<>();
            args.put("query", "");

            Object result = nlQueryTool.execute(args, "trace-error-2", null);

            assertNotNull(result);
            assertInstanceOf(DatasetNLQueryResponse.class, result);

            DatasetNLQueryResponse response = (DatasetNLQueryResponse) result;
            assertEquals("error", response.getType());
        }
    }

    // ==================== executeWithProgress 测试 ====================

    @Nested
    @DisplayName("executeWithProgress - 流式执行")
    class ExecuteWithProgressTest {

        @Test
        @DisplayName("应正确委托给 QueryExpertService")
        void shouldDelegateToQueryExpertService() {
            Flux<ProgressEvent> mockFlux = Flux.just(
                    ProgressEvent.progress("analyzing", 20),
                    ProgressEvent.progress("querying", 50),
                    ProgressEvent.progress("formatting", 80),
                    ProgressEvent.complete(Map.of("data", "result"))
            );

            when(queryExpertService.processQueryWithProgress(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(mockFlux);

            Map<String, Object> args = Map.of("query", "流式查询测试");

            Flux<ProgressEvent> result = nlQueryTool.executeWithProgress(args, "trace-stream-1", null);

            StepVerifier.create(result)
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 20))
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 50))
                    .expectNextMatches(e -> "progress".equals(e.getEventType()) && hasPercent(e, 80))
                    .expectNextMatches(e -> "complete".equals(e.getEventType()))
                    .verifyComplete();

            verify(queryExpertService).processQueryWithProgress(
                    argThat(req -> "流式查询测试".equals(req.getQuery())),
                    eq("trace-stream-1"),
                    any()
            );
        }

        @Test
        @DisplayName("流式执行应正确传递参数")
        void streamingExecution_shouldPassCorrectParams() {
            when(queryExpertService.processQueryWithProgress(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(Flux.just(ProgressEvent.complete(Map.of())));

            Map<String, Object> hints = Map.of(
                    "time_range", Map.of("preset", "today"),
                    "data_source", "olap"
            );

            Map<String, Object> args = Map.of(
                    "query", "今日销售数据",
                    "session_id", "session-xyz",
                    "format", "json",
                    "hints", hints
            );

            nlQueryTool.executeWithProgress(args, "trace-stream-2", null).blockLast();

            verify(queryExpertService).processQueryWithProgress(argThat(req ->
                    "今日销售数据".equals(req.getQuery()) &&
                    "session-xyz".equals(req.getSessionId()) &&
                    "json".equals(req.getFormat()) &&
                    req.getHints() != null &&
                    "olap".equals(req.getHints().getDataSource())
            ), any(), any());
        }

        @Test
        @DisplayName("流式执行错误应正确传播")
        void streamingError_shouldPropagate() {
            when(queryExpertService.processQueryWithProgress(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(Flux.error(new RuntimeException("Streaming error")));

            Map<String, Object> args = Map.of("query", "测试流式错误");

            Flux<ProgressEvent> result = nlQueryTool.executeWithProgress(args, "trace-stream-error", null);

            StepVerifier.create(result)
                    .expectError(RuntimeException.class)
                    .verify();
        }

        @Test
        @DisplayName("部分结果事件应正确传递")
        void partialResultEvents_shouldBeHandled() {
            Flux<ProgressEvent> mockFlux = Flux.just(
                    ProgressEvent.progress("analyzing", 10),
                    ProgressEvent.partialResult(Map.of("model", "SalesModel")),
                    ProgressEvent.progress("executing", 50),
                    ProgressEvent.partialResult(Map.of("rowCount", 100)),
                    ProgressEvent.complete(Map.of("total", 100))
            );

            when(queryExpertService.processQueryWithProgress(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(mockFlux);

            Map<String, Object> args = Map.of("query", "带部分结果的查询");

            Flux<ProgressEvent> result = nlQueryTool.executeWithProgress(args, "trace-partial", null);

            StepVerifier.create(result)
                    .expectNextMatches(e -> hasPercent(e, 10))
                    .expectNextMatches(e -> "partial_result".equals(e.getEventType()))
                    .expectNextMatches(e -> hasPercent(e, 50))
                    .expectNextMatches(e -> "partial_result".equals(e.getEventType()))
                    .expectNextMatches(e -> "complete".equals(e.getEventType()))
                    .verifyComplete();
        }
    }

    // ==================== 请求构建测试 ====================

    @Nested
    @DisplayName("请求构建")
    class RequestBuildingTest {

        @Test
        @DisplayName("完整参数应正确构建请求")
        void fullParams_shouldBuildRequestCorrectly() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.builder().type("result").build());

            Map<String, Object> hints = Map.of(
                    "time_range", Map.of(
                            "preset", "last_month",
                            "start", "2025-01-01",
                            "end", "2025-01-31"
                    ),
                    "data_source", "auto",
                    "preferred_models", List.of("Model1", "Model2")
            );

            Map<String, Object> args = Map.of(
                    "query", "完整参数测试",
                    "session_id", "session-full",
                    "cursor", "cursor-full",
                    "format", "summary",
                    "stream", true,
                    "hints", hints
            );

            nlQueryTool.execute(args, "trace-full", null);

            verify(queryExpertService).processQuery(argThat(req -> {
                // 验证所有参数都正确设置
                boolean basic = "完整参数测试".equals(req.getQuery()) &&
                               "session-full".equals(req.getSessionId()) &&
                               "cursor-full".equals(req.getCursor()) &&
                               "summary".equals(req.getFormat()) &&
                               req.getStream();

                DatasetNLQueryRequest.QueryHints h = req.getHints();
                boolean hintsCorrect = h != null &&
                        "last_month".equals(h.getTimeRange().getPreset()) &&
                        "2025-01-01".equals(h.getTimeRange().getStart()) &&
                        "2025-01-31".equals(h.getTimeRange().getEnd()) &&
                        "auto".equals(h.getDataSource()) &&
                        h.getPreferredModels().size() == 2;

                return basic && hintsCorrect;
            }), any(), any());
        }

        @Test
        @DisplayName("无 hints 时不应构建 hints 对象")
        void noHints_shouldNotBuildHintsObject() {
            when(queryExpertService.processQuery(any(DatasetNLQueryRequest.class), any(), any()))
                    .thenReturn(DatasetNLQueryResponse.builder().type("result").build());

            Map<String, Object> args = Map.of("query", "无 hints 查询");

            nlQueryTool.execute(args, "trace-no-hints", null);

            verify(queryExpertService).processQuery(argThat(req ->
                    req.getHints() == null
            ), any(), any());
        }
    }

    // ==================== 辅助方法 ====================

    @SuppressWarnings("unchecked")
    private boolean hasPercent(ProgressEvent e, int expectedPercent) {
        if (e.getData() instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) e.getData();
            Object percent = data.get("percent");
            return percent != null && ((Number) percent).intValue() == expectedPercent;
        }
        return false;
    }
}

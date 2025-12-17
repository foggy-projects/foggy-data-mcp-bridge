package com.foggyframework.dataset.mcp.tools;

import com.foggyframework.dataset.mcp.enums.ToolCategory;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryRequest;
import com.foggyframework.dataset.mcp.schema.DatasetNLQueryResponse;
import com.foggyframework.dataset.mcp.service.ProgressEvent;
import com.foggyframework.dataset.mcp.service.QueryExpertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 自然语言查询工具 - 智能数据查询
 *
 * 对应 Python 版的 dataset_nl.query
 * 使用 Spring AI 实现自然语言理解和查询构建
 */
@Slf4j
@Component
public class NaturalLanguageQueryTool implements McpTool {

    private final QueryExpertService queryExpertService;

    /**
     * 构造函数
     *
     * 使用 @Lazy 打破循环依赖：
     * QueryExpertService → McpToolDispatcher → NaturalLanguageQueryTool → QueryExpertService
     */
    public NaturalLanguageQueryTool(@Lazy QueryExpertService queryExpertService) {
        this.queryExpertService = queryExpertService;
    }

    @Override
    public String getName() {
        return "dataset_nl.query";
    }

    @Override
    public Set<ToolCategory> getCategories() {
        // 自然语言查询工具，适合普通业务用户
        return EnumSet.of(ToolCategory.NATURAL_LANGUAGE);
    }

    // 注意：getDescription() 和 getInputSchema() 从配置文件加载，不再硬编码

    @Override
    public boolean supportsStreaming() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> arguments, String traceId, String authorization) {
        DatasetNLQueryRequest request = buildRequest(arguments);

        log.info("Processing NL query: query={}, traceId={}", request.getQuery(), traceId);

        try {
            DatasetNLQueryResponse response = queryExpertService.processQuery(request, traceId, authorization);
            log.info("NL query completed: type={}, traceId={}", response.getType(), traceId);
            return response;

        } catch (Exception e) {
            log.error("NL query failed: query={}, error={}, traceId={}",
                    request.getQuery(), e.getMessage(), traceId, e);
            return DatasetNLQueryResponse.error("QUERY_FAILED", e.getMessage(), null);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Flux<ProgressEvent> executeWithProgress(Map<String, Object> arguments, String traceId, String authorization) {
        DatasetNLQueryRequest request = buildRequest(arguments);

        log.info("Processing NL query with progress: query={}, traceId={}", request.getQuery(), traceId);

        return queryExpertService.processQueryWithProgress(request, traceId, authorization);
    }

    @SuppressWarnings("unchecked")
    private DatasetNLQueryRequest buildRequest(Map<String, Object> arguments) {
        DatasetNLQueryRequest.DatasetNLQueryRequestBuilder builder = DatasetNLQueryRequest.builder();

        builder.query((String) arguments.get("query"));
        builder.sessionId((String) arguments.get("session_id"));
        builder.cursor((String) arguments.get("cursor"));
        builder.format((String) arguments.getOrDefault("format", "table"));
        builder.stream((Boolean) arguments.getOrDefault("stream", true));

        // 处理 hints
        Map<String, Object> hintsMap = (Map<String, Object>) arguments.get("hints");
        if (hintsMap != null) {
            DatasetNLQueryRequest.QueryHints.QueryHintsBuilder hintsBuilder =
                    DatasetNLQueryRequest.QueryHints.builder();

            // 时间范围
            Map<String, Object> timeRangeMap = (Map<String, Object>) hintsMap.get("time_range");
            if (timeRangeMap != null) {
                hintsBuilder.timeRange(DatasetNLQueryRequest.TimeRange.builder()
                        .preset((String) timeRangeMap.get("preset"))
                        .start((String) timeRangeMap.get("start"))
                        .end((String) timeRangeMap.get("end"))
                        .build());
            }

            hintsBuilder.dataSource((String) hintsMap.get("data_source"));
            hintsBuilder.preferredModels((List<String>) hintsMap.get("preferred_models"));

            builder.hints(hintsBuilder.build());
        }

        return builder.build();
    }
}

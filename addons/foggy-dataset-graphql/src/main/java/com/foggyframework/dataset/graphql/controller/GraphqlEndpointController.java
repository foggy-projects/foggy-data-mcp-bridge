package com.foggyframework.dataset.graphql.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.client.domain.PagingRequest;
import com.foggyframework.dataset.db.model.def.query.request.DbQueryRequestDef;
import com.foggyframework.dataset.db.model.service.QueryFacade;
import com.foggyframework.dataset.graphql.converter.GraphqlToDslConverter;
import com.foggyframework.dataset.model.PagingResultImpl;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * GraphQL HTTP 端点
 * <p>
 * 提供标准的 GraphQL HTTP 接口，接收 GraphQL 查询并转换为 DSL 执行
 * </p>
 *
 * <p>使用方式：</p>
 * <pre>
 * POST /graphql
 * {
 *   "query": "query { factOrder(where: {...}) { orderId totalAmount } }",
 *   "variables": { "status": "COMPLETED" }
 * }
 * </pre>
 *
 * @author Foggy Framework
 */
@Slf4j
@RestController
@RequestMapping("graphql")
@ConditionalOnProperty(name = "foggy.dataset.graphql.enabled", havingValue = "true", matchIfMissing = true)
public class GraphqlEndpointController {

    private final QueryFacade queryFacade;
    private final GraphqlToDslConverter converter;
    private final ObjectMapper objectMapper;

    public GraphqlEndpointController(QueryFacade queryFacade,
                                     GraphqlToDslConverter converter,
                                     ObjectMapper objectMapper) {
        this.queryFacade = queryFacade;
        this.converter = converter;
        this.objectMapper = objectMapper;
    }

    /**
     * GraphQL 查询端点
     *
     * @param request GraphQL 请求
     * @return 查询结果
     */
    @PostMapping
    @ApiOperation("GraphQL 查询接口")
    public RX<Map<String, Object>> graphql(
            @ApiParam(value = "GraphQL 请求", required = true)
            @RequestBody GraphqlRequest request
    ) {
        try {
            log.info("收到 GraphQL 查询: {}", request.getQuery());

            // 1. 转换 GraphQL → DSL
            PagingRequest<DbQueryRequestDef> dslRequest = converter.convert(
                    request.getQuery(),
                    request.getVariables() != null ? request.getVariables() : new HashMap<>()
            );

            log.debug("转换后的 DSL 请求: {}", objectMapper.writeValueAsString(dslRequest));

            // 2. 执行查询
            PagingResultImpl result = queryFacade.queryModelData(dslRequest);

            // 3. 包装为 GraphQL 响应格式
            Map<String, Object> response = new HashMap<>();
            Map<String, Object> data = new HashMap<>();

            // 提取查询名称（从 query 中解析，如 factOrder）
            String queryName = extractQueryName(request.getQuery());

            // 简化响应（直接返回 items）
            // TODO: 完整实现 Relay Connection 格式
            data.put(queryName, buildSimpleResponse(result));

            response.put("data", data);

            return RX.success(response);

        } catch (Exception e) {
            log.error("GraphQL 查询执行失败", e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("errors", new Object[]{
                    Map.of(
                            "message", e.getMessage(),
                            "extensions", Map.of("code", "INTERNAL_ERROR")
                    )
            });

            RX<Map<String, Object>> errorResult = RX.internalServerError()
                    .msg(e.getMessage())
                    .build();
            errorResult.setData(errorResponse);
            return errorResult;
        }
    }

    /**
     * 构建简化响应
     * <p>
     * 直接返回 items 列表，不包装 Connection
     * </p>
     */
    private Object buildSimpleResponse(PagingResultImpl result) {
        return result.getItems();
    }

    /**
     * 提取查询名称
     * <p>
     * query { factOrder(...) {...} } → factOrder
     * </p>
     */
    private String extractQueryName(String query) {
        // 简单的正则提取（生产环境应使用 GraphQL 解析器）
        String normalized = query.replaceAll("\\s+", " ").trim();

        // 查找第一个 { 后的字段名
        int start = normalized.indexOf('{');
        if (start != -1) {
            int fieldStart = start + 1;
            while (fieldStart < normalized.length() && Character.isWhitespace(normalized.charAt(fieldStart))) {
                fieldStart++;
            }

            int fieldEnd = fieldStart;
            while (fieldEnd < normalized.length() &&
                    (Character.isLetterOrDigit(normalized.charAt(fieldEnd)) || normalized.charAt(fieldEnd) == '_')) {
                fieldEnd++;
            }

            if (fieldEnd > fieldStart) {
                return normalized.substring(fieldStart, fieldEnd);
            }
        }

        return "result";
    }

    /**
     * GraphQL 请求体
     */
    @Data
    public static class GraphqlRequest {
        /**
         * GraphQL 查询字符串
         */
        private String query;

        /**
         * 查询变量
         */
        private Map<String, Object> variables;

        /**
         * 操作名称（可选）
         */
        private String operationName;
    }
}

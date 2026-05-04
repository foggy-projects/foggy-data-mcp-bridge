package com.foggyframework.dataset.mcp.controller;

import com.foggyframework.dataset.mcp.enums.UserRole;
import com.foggyframework.dataset.mcp.schema.McpError;
import com.foggyframework.dataset.mcp.schema.McpRequest;
import com.foggyframework.dataset.mcp.schema.McpRequestContext;
import com.foggyframework.dataset.mcp.schema.McpResponse;
import com.foggyframework.dataset.mcp.service.McpService;
import com.foggyframework.dataset.mcp.service.McpToolDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 数据分析师 MCP JSON-RPC 2.0 Controller
 *
 * 提供专业的 MCP 协议端点，返回数据分析和处理工具（不含自然语言查询）
 * 适用于：数据分析师、专业数据处理人员、需要精确控制查询的用户
 *
 * 可用工具：
 * - 元数据查询工具（MetadataTool、DescriptionModelTool）
 * - 数据查询工具（QueryModelTool）
 * - 数据可视化工具（ChartTool）
 * - 数据导出工具（ExportWithChartTool）
 *
 * 不可用工具：
 * - 自然语言查询工具（NaturalLanguageQueryTool）- 专业人员需要精确控制查询逻辑
 *
 * 端点：
 * - POST /mcp/analyst/rpc - 同步 JSON-RPC 调用
 * - POST /mcp/analyst/stream - 流式 SSE 响应
 *
 * HTTP Header 说明：
 * - X-Trace-Id: AI 会话追踪 ID，一次完整 AI 执行的唯一标识，贯穿多次工具调用
 * - X-Request-Id: HTTP 请求 ID，单次请求的唯一标识
 */
@Slf4j
@RestController
@RequestMapping("/mcp/analyst")
@RequiredArgsConstructor
public class AnalystMcpController {

    private final McpService mcpService;
    private final McpToolDispatcher toolDispatcher;

    /**
     * 用户角色固定为 ANALYST
     */
    private static final UserRole USER_ROLE = UserRole.ANALYST;

    /**
     * 标准 MCP JSON-RPC 端点（同步）
     *
     * Claude Desktop IDE 可使用此端点
     */
    @PostMapping(value = "/rpc", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpResponse> handleRpc(
            @RequestBody McpRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader(value = "X-Foggy-Remote-Compose", required = false) String remoteCompose,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Namespace", required = false) String remoteNamespace,
            @RequestHeader(value = "X-Roles", required = false) String roles,
            @RequestHeader(value = "X-Dept-Id", required = false) String deptId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Policy-Snapshot-Id", required = false) String policySnapshotId
    ) {
        // traceId: AI 会话级，如果没有则生成新的
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        // requestId: HTTP 请求级，每次请求都生成新的
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        if (namespace == null || namespace.isBlank()) {
            log.warn("Analyst MCP RPC: X-NS header is MISSING -- will use default namespace. " +
                    "For Odoo models, set header X-NS: odoo");
        }
        log.info("Analyst MCP RPC request received: method={}, id={}, traceId={}, requestId={}, namespace={}",
                request.getMethod(), request.getId(), traceId, requestId, namespace);

        try {
            // 处理 MCP 内置方法
            if (request.getMethod() != null) {
                switch (request.getMethod()) {
                    case "initialize":
                        return ResponseEntity.ok(mcpService.handleInitialize(request, USER_ROLE));
                    case "tools/list":
                        return ResponseEntity.ok(mcpService.handleToolsList(request, USER_ROLE));
                    case "tools/call":
                        return ResponseEntity.ok(mcpService.handleToolsCall(request,
                                McpRequestContext.of(traceId, requestId, authorization, USER_ROLE, namespace,
                                        remoteComposeHeaders(
                                                remoteCompose, traceId, authorization, userId,
                                                remoteNamespace, namespace, roles, deptId, tenantId,
                                                policySnapshotId))));
                    case "ping":
                        return ResponseEntity.ok(mcpService.handlePing(request));
                    default:
                        // 尝试作为工具调用处理
                        if (request.getMethod().startsWith("dataset") || request.getMethod().startsWith("olap")) {
                            return ResponseEntity.ok(mcpService.handleDirectToolCall(request,
                                    McpRequestContext.of(traceId, requestId, authorization, USER_ROLE, namespace,
                                            remoteComposeHeaders(
                                                    remoteCompose, traceId, authorization, userId,
                                                    remoteNamespace, namespace, roles, deptId, tenantId,
                                                    policySnapshotId))));
                        }
                        return ResponseEntity.ok(McpResponse.error(
                                request.getId(),
                                McpError.METHOD_NOT_FOUND,
                                "Method not found: " + request.getMethod()
                        ));
                }
            }

            return ResponseEntity.ok(McpResponse.error(
                    request.getId(),
                    McpError.INVALID_REQUEST,
                    "Missing method field"
            ));

        } catch (Exception e) {
            log.error("Analyst MCP RPC error: method={}, error={}", request.getMethod(), e.getMessage(), e);
            return ResponseEntity.ok(McpResponse.error(
                    request.getId(),
                    McpError.INTERNAL_ERROR,
                    e.getMessage()
            ));
        }
    }

    /**
     * 流式 SSE 端点
     *
     * 用于 Web 前端获取实时进度
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> handleStream(
            @RequestBody McpRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-NS", required = false) String namespace,
            @RequestHeader(value = "X-Foggy-Remote-Compose", required = false) String remoteCompose,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Namespace", required = false) String remoteNamespace,
            @RequestHeader(value = "X-Roles", required = false) String roles,
            @RequestHeader(value = "X-Dept-Id", required = false) String deptId,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Policy-Snapshot-Id", required = false) String policySnapshotId
    ) {
        // traceId: AI 会话级
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        // requestId: HTTP 请求级
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        if (namespace == null || namespace.isBlank()) {
            log.warn("Analyst MCP Stream: X-NS header is MISSING -- will use default namespace. " +
                    "For Odoo models, set header X-NS: odoo");
        }
        log.info("Analyst MCP Stream request received: method={}, id={}, traceId={}, requestId={}, namespace={}",
                request.getMethod(), request.getId(), traceId, requestId, namespace);

        final String finalTraceId = traceId;
        return toolDispatcher.executeWithProgress(request, traceId, authorization, namespace,
                        remoteComposeHeaders(
                                remoteCompose, traceId, authorization, userId,
                                remoteNamespace, namespace, roles, deptId, tenantId,
                                policySnapshotId))
                .map(event -> ServerSentEvent.<Object>builder()
                        .id(event.getId())
                        .event(event.getEventType())
                        .data(event.getData())
                        .build())
                .doOnComplete(() -> log.info("Analyst MCP Stream completed: traceId={}", finalTraceId))
                .doOnError(e -> log.error("Analyst MCP Stream error: traceId={}, error={}", finalTraceId, e.getMessage()));
    }

    private static Map<String, String> remoteComposeHeaders(
            String remoteCompose,
            String traceId,
            String authorization,
            String userId,
            String remoteNamespace,
            String namespace,
            String roles,
            String deptId,
            String tenantId,
            String policySnapshotId) {
        if (remoteCompose == null) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        putIfNotBlank(headers, "X-Foggy-Remote-Compose", remoteCompose);
        putIfNotBlank(headers, "X-User-Id", userId);
        putIfNotBlank(headers, "X-Namespace", remoteNamespace);
        putIfNotBlank(headers, "X-NS", namespace);
        putIfNotBlank(headers, "X-Roles", roles);
        putIfNotBlank(headers, "X-Dept-Id", deptId);
        putIfNotBlank(headers, "X-Tenant-Id", tenantId);
        putIfNotBlank(headers, "X-Policy-Snapshot-Id", policySnapshotId);
        putIfNotBlank(headers, "X-Trace-Id", traceId);
        putIfNotBlank(headers, "Authorization", authorization);
        return headers;
    }

    private static void putIfNotBlank(Map<String, String> headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(name, value);
        }
    }
}

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
 * 管理员 MCP JSON-RPC 2.0 Controller
 *
 * 提供完整的 MCP 协议端点，返回所有可用工具
 * 适用于：管理员、开发人员、拥有完全访问权限的用户
 *
 * 端点：
 * - POST /mcp/admin/rpc - 同步 JSON-RPC 调用
 * - POST /mcp/admin/stream - 流式 SSE 响应
 *
 * HTTP Header 说明：
 * - X-Trace-Id: AI 会话追踪 ID，一次完整 AI 执行的唯一标识，贯穿多次工具调用
 * - X-Request-Id: HTTP 请求 ID，单次请求的唯一标识
 */
@Slf4j
@RestController
@RequestMapping("/mcp/admin")
@RequiredArgsConstructor
public class AdminMcpController {

    private final McpService mcpService;
    private final McpToolDispatcher toolDispatcher;

    /**
     * 用户角色固定为 ADMIN
     */
    private static final UserRole USER_ROLE = UserRole.ADMIN;

    /**
     * 标准 MCP JSON-RPC 端点（同步）
     *
     * Claude Desktop IDE 必须使用此端点
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
            @RequestHeader(value = "X-Permission-Tags", required = false) String permissionTags,
            @RequestHeader(value = "X-Recipe-Owner-Roles", required = false) String recipeOwnerRoles,
            @RequestHeader(value = "X-Registry-Actor-Role", required = false) String registryActorRole,
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

        log.info("Admin MCP RPC request received: method={}, id={}, traceId={}, requestId={}, namespace={}",
                request.getMethod(), request.getId(), traceId, requestId, namespace);

        // 构建请求上下文
        McpRequestContext context = McpRequestContext.of(traceId, requestId, authorization, USER_ROLE, namespace,
                requestScopedHeaders(
                        remoteCompose, traceId, authorization, userId, remoteNamespace,
                        namespace, roles, permissionTags, recipeOwnerRoles, registryActorRole,
                        deptId, tenantId, policySnapshotId));

        try {
            // 处理 MCP 内置方法
            if (request.getMethod() != null) {
                switch (request.getMethod()) {
                    case "initialize":
                        return ResponseEntity.ok(mcpService.handleInitialize(request, USER_ROLE));
                    case "tools/list":
                        return ResponseEntity.ok(mcpService.handleToolsList(request, context));
                    case "tools/call":
                        return ResponseEntity.ok(mcpService.handleToolsCall(request, context));
                    case "ping":
                        return ResponseEntity.ok(mcpService.handlePing(request));
                    default:
                        // 尝试作为工具调用处理
                        if (request.getMethod().startsWith("dataset") || request.getMethod().startsWith("olap")) {
                            return ResponseEntity.ok(mcpService.handleDirectToolCall(request, context));
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
            log.error("Admin MCP RPC error: method={}, error={}", request.getMethod(), e.getMessage(), e);
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
     * 注意：Claude Desktop IDE 不支持此端点
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
            @RequestHeader(value = "X-Permission-Tags", required = false) String permissionTags,
            @RequestHeader(value = "X-Recipe-Owner-Roles", required = false) String recipeOwnerRoles,
            @RequestHeader(value = "X-Registry-Actor-Role", required = false) String registryActorRole,
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
            log.warn("Admin MCP Stream: X-NS header is missing; configured request default namespace may be applied downstream. " +
                    "Set X-NS explicitly to override.");
        }
        log.info("Admin MCP Stream request received: method={}, id={}, traceId={}, requestId={}, namespace={}",
                request.getMethod(), request.getId(), traceId, requestId, namespace);

        final String finalTraceId = traceId;
        return toolDispatcher.executeWithProgress(request, traceId, authorization, namespace,
                        requestScopedHeaders(
                                remoteCompose, traceId, authorization, userId,
                                remoteNamespace, namespace, roles, permissionTags,
                                recipeOwnerRoles, registryActorRole, deptId, tenantId,
                                policySnapshotId), USER_ROLE.name())
                .map(event -> ServerSentEvent.<Object>builder()
                        .id(event.getId())
                        .event(event.getEventType())
                        .data(event.getData())
                        .build())
                .doOnComplete(() -> log.info("Admin MCP Stream completed: traceId={}", finalTraceId))
                .doOnError(e -> log.error("Admin MCP Stream error: traceId={}, error={}", finalTraceId, e.getMessage()));
    }

    private static Map<String, String> requestScopedHeaders(
            String remoteCompose,
            String traceId,
            String authorization,
            String userId,
            String remoteNamespace,
            String namespace,
            String roles,
            String permissionTags,
            String recipeOwnerRoles,
            String registryActorRole,
            String deptId,
            String tenantId,
            String policySnapshotId) {
        Map<String, String> headers = new LinkedHashMap<>();
        putIfNotBlank(headers, "X-Foggy-Remote-Compose", remoteCompose);
        putIfNotBlank(headers, "X-User-Id", userId);
        putIfNotBlank(headers, "X-Namespace", remoteNamespace);
        putIfNotBlank(headers, "X-NS", namespace);
        putIfNotBlank(headers, "X-Roles", roles);
        putIfNotBlank(headers, "X-Permission-Tags", permissionTags);
        putIfNotBlank(headers, "X-Recipe-Owner-Roles", recipeOwnerRoles);
        putIfNotBlank(headers, "X-Registry-Actor-Role", registryActorRole);
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

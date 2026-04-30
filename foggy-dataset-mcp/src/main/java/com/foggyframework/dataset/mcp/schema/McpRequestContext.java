package com.foggyframework.dataset.mcp.schema;

import com.foggyframework.dataset.mcp.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP请求上下文
 * <p>
 * 封装MCP请求处理所需的上下文信息，避免方法参数过多
 * </p>
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Data
@Builder
public class McpRequestContext {

    /**
     * 追踪ID，用于日志关联和审计
     */
    private String traceId;

    /**
     * 请求ID，用于标识单个请求
     */
    private String requestId;

    /**
     * 授权令牌（从请求头传递）
     */
    private String authorization;

    /**
     * 用户角色
     */
    private UserRole userRole;

    /**
     * 命名空间（用于模型隔离）
     * <p>空字符串或null表示默认命名空间
     */
    private String namespace;

    /**
     * 请求来源IP
     */
    private String sourceIp;

    /**
     * 原始请求头快照，用于工具级协议开关。
     */
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * 创建简单上下文（仅包含traceId）
     */
    public static McpRequestContext of(String traceId) {
        return McpRequestContext.builder()
                .traceId(traceId)
                .build();
    }

    /**
     * 创建完整上下文
     */
    public static McpRequestContext of(String traceId, String requestId, String authorization,
                                        UserRole userRole, String namespace) {
        return of(traceId, requestId, authorization, userRole, namespace, null);
    }

    /**
     * 创建完整上下文（包含请求头快照）
     */
    public static McpRequestContext of(String traceId, String requestId, String authorization,
                                        UserRole userRole, String namespace,
                                        Map<String, String> headers) {
        return McpRequestContext.builder()
                .traceId(traceId)
                .requestId(requestId)
                .authorization(authorization)
                .userRole(userRole)
                .namespace(namespace)
                .headers(headers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(headers))
                .build();
    }
}

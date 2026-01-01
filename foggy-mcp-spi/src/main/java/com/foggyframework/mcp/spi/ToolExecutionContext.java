package com.foggyframework.mcp.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行上下文
 * <p>
 * 封装工具执行时所需的上下文信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionContext {

    /**
     * 追踪ID，用于日志关联和审计
     */
    private String traceId;

    /**
     * 授权令牌（从请求头传递）
     */
    private String authorization;

    /**
     * 用户角色
     */
    private String userRole;

    /**
     * 请求来源IP
     */
    private String sourceIp;

    /**
     * 创建简单上下文
     */
    public static ToolExecutionContext of(String traceId, String authorization) {
        return ToolExecutionContext.builder()
                .traceId(traceId)
                .authorization(authorization)
                .build();
    }
}

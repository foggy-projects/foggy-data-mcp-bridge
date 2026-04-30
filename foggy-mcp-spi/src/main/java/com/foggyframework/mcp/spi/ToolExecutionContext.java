package com.foggyframework.mcp.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
     * 命名空间（用于模型隔离）
     * <p>空字符串或null表示默认命名空间
     */
    private String namespace;

    /**
     * 请求来源IP
     */
    private String sourceIp;

    /**
     * 原始请求头快照。key 保留调用方传入的大小写，读取时按 HTTP 头规则大小写不敏感匹配。
     */
    @Builder.Default
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * 创建简单上下文
     */
    public static ToolExecutionContext of(String traceId, String authorization) {
        return ToolExecutionContext.builder()
                .traceId(traceId)
                .authorization(authorization)
                .build();
    }

    public Map<String, String> getHeaders() {
        return headers == null ? Collections.emptyMap() : headers;
    }

    public String getHeader(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Map<String, String> headerMap = getHeaders();
        String direct = headerMap.get(name);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        if ("Authorization".equalsIgnoreCase(name)) {
            return authorization;
        }
        return null;
    }
}

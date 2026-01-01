package com.foggyframework.mcp.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * 进度事件
 * <p>
 * 用于流式工具执行时的进度反馈
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressEvent {

    /**
     * 事件ID
     */
    private String id;

    /**
     * 事件类型：progress, partial_result, clarify, complete, error
     */
    private String eventType;

    /**
     * 事件数据
     */
    private Object data;

    /**
     * 创建进度事件
     */
    public static ProgressEvent progress(String phase, int percent) {
        return ProgressEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType("progress")
                .data(Map.of("phase", phase, "percent", percent))
                .build();
    }

    /**
     * 创建部分结果事件
     */
    public static ProgressEvent partialResult(Object data) {
        return ProgressEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType("partial_result")
                .data(data)
                .build();
    }

    /**
     * 创建完成事件
     */
    public static ProgressEvent complete(Object result) {
        return ProgressEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType("complete")
                .data(result)
                .build();
    }

    /**
     * 创建错误事件
     */
    public static ProgressEvent error(String code, String message) {
        return ProgressEvent.builder()
                .id(UUID.randomUUID().toString())
                .eventType("error")
                .data(Map.of("code", code, "message", message))
                .build();
    }
}

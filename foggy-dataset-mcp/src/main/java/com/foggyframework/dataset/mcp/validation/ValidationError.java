package com.foggyframework.dataset.mcp.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证错误
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {

    /**
     * 文件名（相对路径）
     */
    private String file;

    /**
     * 文件类型（TM/QM）
     */
    private String type;

    /**
     * 错误行号（如果可用）
     */
    private Integer line;

    /**
     * 错误列号（如果可用）
     */
    private Integer column;

    /**
     * 严重程度
     */
    @Builder.Default
    private String severity = "ERROR";

    /**
     * 错误代码
     */
    private String code;

    /**
     * 错误消息
     */
    private String message;

    /**
     * 建议
     */
    private String suggestion;

    /**
     * 错误分类: MODEL(模型逻辑错误), CASCADING(因上游TM失败导致的级联错误)
     */
    @Builder.Default
    private String category = "MODEL";

    /**
     * 堆栈跟踪（可选，调试用）
     */
    private String stackTrace;

    /**
     * 创建简单错误
     */
    public static ValidationError simple(String file, String type, String message) {
        return ValidationError.builder()
                .file(file)
                .type(type)
                .message(message)
                .build();
    }

    /**
     * 从异常创建错误
     */
    public static ValidationError fromException(String file, String type, Exception e) {
        return ValidationError.builder()
                .file(file)
                .type(type)
                .message(e.getMessage())
                .code(e.getClass().getSimpleName())
                .stackTrace(getStackTraceString(e))
                .build();
    }

    /**
     * 获取堆栈跟踪字符串
     */
    private static String getStackTraceString(Exception e) {
        if (e == null) return null;
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            if (sb.length() > 500) break; // 限制长度
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}

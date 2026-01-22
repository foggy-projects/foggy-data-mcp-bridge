package com.foggyframework.dataset.mcp.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证警告
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationWarning {

    /**
     * 文件名（相对路径）
     */
    private String file;

    /**
     * 文件类型（TM/QM）
     */
    private String type;

    /**
     * 警告行号（如果可用）
     */
    private Integer line;

    /**
     * 严重程度
     */
    @Builder.Default
    private String severity = "WARNING";

    /**
     * 警告代码
     */
    private String code;

    /**
     * 警告消息
     */
    private String message;

    /**
     * 建议
     */
    private String suggestion;

    /**
     * 创建简单警告
     */
    public static ValidationWarning simple(String file, String type, String message) {
        return ValidationWarning.builder()
                .file(file)
                .type(type)
                .message(message)
                .build();
    }
}

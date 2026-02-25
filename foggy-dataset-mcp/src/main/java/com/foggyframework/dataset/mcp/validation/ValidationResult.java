package com.foggyframework.dataset.mcp.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 语义层验证结果
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    /**
     * 验证是否成功（无错误）
     */
    private boolean success;

    /**
     * 命名空间
     */
    private String namespace;

    /**
     * 总文件数
     */
    private int totalFiles;

    /**
     * 有效文件数
     */
    private int validFiles;

    /**
     * 无效文件数
     */
    private int invalidFiles;

    /**
     * 错误列表
     */
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();

    /**
     * 警告列表
     */
    @Builder.Default
    private List<ValidationWarning> warnings = new ArrayList<>();

    /**
     * 级联错误数（因上游TM失败导致的QM错误）
     */
    private int cascadingErrors;

    /**
     * 验证时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 总耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 创建成功的验证结果
     */
    public static ValidationResult success(String namespace, int totalFiles) {
        return ValidationResult.builder()
                .success(true)
                .namespace(namespace)
                .totalFiles(totalFiles)
                .validFiles(totalFiles)
                .invalidFiles(0)
                .build();
    }

    /**
     * 创建失败的验证结果
     */
    public static ValidationResult failure(String namespace, int totalFiles, List<ValidationError> errors) {
        return ValidationResult.builder()
                .success(false)
                .namespace(namespace)
                .totalFiles(totalFiles)
                .validFiles(totalFiles - errors.size())
                .invalidFiles(errors.size())
                .errors(errors)
                .build();
    }
}

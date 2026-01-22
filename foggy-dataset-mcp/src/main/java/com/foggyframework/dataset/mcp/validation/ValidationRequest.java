package com.foggyframework.dataset.mcp.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义层验证请求
 *
 * @author foggy-framework
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationRequest {

    /**
     * 外部目录路径
     * <p>必填，指向包含 TM/QM 文件的目录
     */
    private String path;

    /**
     * 命名空间
     * <p>默认为 "openhands"
     */
    @Builder.Default
    private String namespace = "openhands";

    /**
     * 是否监听文件变化
     * <p>默认为 false
     */
    @Builder.Default
    private boolean watch = false;

    /**
     * 是否清除已注册的同名Bundle
     * <p>默认为 true，避免重复注册
     */
    @Builder.Default
    private boolean clearExisting = true;

    /**
     * 是否返回详细的堆栈跟踪
     * <p>默认为 false，仅开发环境建议开启
     */
    @Builder.Default
    private boolean includeStackTrace = false;
}

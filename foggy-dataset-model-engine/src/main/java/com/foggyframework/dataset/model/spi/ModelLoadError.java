package com.foggyframework.dataset.model.spi;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型加载错误信息
 *
 * <p>用于在模型加载过程中收集和记录错误，而不是立即抛出异常导致加载失败。
 * 这使得模型可以在部分配置有问题的情况下仍然加载成功，为用户提供更友好的错误诊断。
 *
 * <p>使用场景：
 * <ul>
 *   <li>字段在 TM 中定义但在数据库表中不存在</li>
 *   <li>维度引用的表不存在</li>
 *   <li>外键配置错误</li>
 *   <li>计算字段公式错误</li>
 * </ul>
 *
 * @author Foggy Framework
 * @since 8.3.0
 */
@Data
@Builder
public class ModelLoadError {

    /**
     * 错误类型
     */
    private ErrorType errorType;

    /**
     * 错误级别
     */
    private ErrorLevel errorLevel;

    /**
     * 错误位置（如：dimension.customer.property.id_card）
     */
    private String location;

    /**
     * 错误消息（简短描述）
     */
    private String message;

    /**
     * 详细信息（完整的错误说明）
     */
    private String details;

    /**
     * 发生时间
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 原始异常（如果有）
     */
    private Throwable cause;

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /**
         * 字段不存在
         */
        COLUMN_NOT_FOUND("字段不存在"),

        /**
         * 表不存在
         */
        TABLE_NOT_FOUND("表不存在"),

        /**
         * 维度不存在
         */
        DIMENSION_NOT_FOUND("维度不存在"),

        /**
         * 类型不匹配
         */
        TYPE_MISMATCH("类型不匹配"),

        /**
         * 外键无效
         */
        FOREIGN_KEY_INVALID("外键无效"),

        /**
         * 公式错误
         */
        FORMULA_ERROR("公式错误"),

        /**
         * 预聚合配置错误
         */
        PREAGG_CONFIG_ERROR("预聚合配置错误"),

        /**
         * 其他错误
         */
        OTHER("其他错误");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 错误级别枚举
     */
    public enum ErrorLevel {
        /**
         * 警告（不影响使用，仅提示）
         */
        WARNING("警告"),

        /**
         * 错误（部分功能受限）
         */
        ERROR("错误"),

        /**
         * 致命（模型无法正常使用）
         */
        FATAL("致命错误");

        private final String description;

        ErrorLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 获取格式化的错误信息
     *
     * @return 格式化后的错误信息
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorLevel.getDescription()).append("] ");
        sb.append(errorType.getDescription()).append(" - ");
        if (location != null && !location.isEmpty()) {
            sb.append("位置: ").append(location).append(" - ");
        }
        sb.append(message);
        return sb.toString();
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }
}

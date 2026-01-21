package com.foggyframework.dataset.db.model.spi;

/**
 * 模型加载状态枚举
 *
 * <p>表示 TM/QM 模型的加载结果状态，用于区分完全成功、部分成功和失败的情况。
 *
 * @author Foggy Framework
 * @since 8.3.0
 */
public enum ModelLoadStatus {

    /**
     * 完全成功 - 模型加载成功，没有任何错误或警告
     */
    SUCCESS("成功", "模型加载完全成功，所有配置正确"),

    /**
     * 成功但有警告 - 模型加载成功，但有一些警告信息
     */
    SUCCESS_WITH_WARNINGS("成功(有警告)", "模型加载成功，但存在一些可优化的配置"),

    /**
     * 部分成功 - 模型加载成功，但部分功能受限（有错误但不致命）
     */
    PARTIAL_SUCCESS("部分成功", "模型加载成功，但部分字段或功能不可用"),

    /**
     * 失败 - 模型加载失败，存在致命错误无法使用
     */
    FAILED("失败", "模型加载失败，存在致命错误");

    private final String displayName;
    private final String description;

    ModelLoadStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * 获取显示名称
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取描述
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为成功状态（包括部分成功）
     *
     * @return 如果是成功相关状态返回 true
     */
    public boolean isSuccess() {
        return this == SUCCESS || this == SUCCESS_WITH_WARNINGS || this == PARTIAL_SUCCESS;
    }

    /**
     * 判断是否完全成功（无任何问题）
     *
     * @return 如果完全成功返回 true
     */
    public boolean isFullySuccess() {
        return this == SUCCESS;
    }

    /**
     * 判断是否失败
     *
     * @return 如果失败返回 true
     */
    public boolean isFailed() {
        return this == FAILED;
    }
}

package com.foggyframework.dataset.db.model.semantic.exception;

import lombok.Getter;

/**
 * 基数熔断异常
 *
 * <p>当 Pivot 透视请求的预估 cell 数超过 {@code MAX_PIVOT_CELLS} 阈值时抛出。
 * 携带结构化的错误详情，使 LLM 能够根据 suggestion 自动降级。</p>
 */
@Getter
public class TooManyPivotCellsException extends RuntimeException {

    private final long rowDomainSize;
    private final long colDomainSize;
    private final long cellCount;
    private final long maxAllowed;
    private final String suggestion;

    public TooManyPivotCellsException(long rowDomainSize, long colDomainSize,
                                       long cellCount, long maxAllowed) {
        super(String.format(
                "行集合基数(%d) × 列集合基数(%d) = %,d 超过阈值 %,d",
                rowDomainSize, colDomainSize, cellCount, maxAllowed));
        this.rowDomainSize = rowDomainSize;
        this.colDomainSize = colDomainSize;
        this.cellCount = cellCount;
        this.maxAllowed = maxAllowed;
        this.suggestion = "请在行轴或列轴添加 limit 约束，或缩小 slice 范围";
    }
}

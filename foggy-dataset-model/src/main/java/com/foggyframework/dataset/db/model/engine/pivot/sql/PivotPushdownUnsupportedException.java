package com.foggyframework.dataset.db.model.engine.pivot.sql;

/**
 * 专用异常：Pivot SQL Pushdown 能力不满足时抛出
 *
 * <p>此异常表示"当前请求不能被 SQL 下放"，而非"请求本身非法"。
 * PivotPipeline 应捕获此异常并 fallback 到内存路径。</p>
 *
 * <p>与 {@link IllegalArgumentException}（请求非法）和
 * {@link IllegalStateException}（内部状态异常）区分。</p>
 */
public class PivotPushdownUnsupportedException extends RuntimeException {

    public PivotPushdownUnsupportedException(String message) {
        super(message);
    }

    public PivotPushdownUnsupportedException(String message, Throwable cause) {
        super(message, cause);
    }
}

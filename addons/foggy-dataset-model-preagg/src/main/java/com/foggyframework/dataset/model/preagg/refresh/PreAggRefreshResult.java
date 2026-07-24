package com.foggyframework.dataset.model.preagg.refresh;

import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 预聚合刷新结果
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Data
public class PreAggRefreshResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 刷新策略（FULL / INCREMENTAL）
     */
    private String strategy;

    /**
     * 影响的行数
     */
    private long affectedRows;

    /**
     * 刷新开始时间
     */
    private LocalDateTime startTime;

    /**
     * 刷新结束时间
     */
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒）
     */
    private long durationMs;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 异常（如果失败）
     */
    private Throwable exception;

    /**
     * 新的水位线值（物化历史的 exclusive upper bound）
     */
    private Object newWatermark;

    /**
     * 执行的 SQL 语句（用于调试）
     */
    private String executedSql;

    /**
     * 创建成功结果
     */
    public static PreAggRefreshResult success(String strategy, long affectedRows,
                                               LocalDateTime startTime, LocalDateTime endTime) {
        PreAggRefreshResult result = new PreAggRefreshResult();
        result.setSuccess(true);
        result.setStrategy(strategy);
        result.setAffectedRows(affectedRows);
        result.setStartTime(startTime);
        result.setEndTime(endTime);
        result.setDurationMs(Duration.between(startTime, endTime).toMillis());
        return result;
    }

    /**
     * 创建失败结果
     */
    public static PreAggRefreshResult failure(String strategy, String errorMessage, Throwable exception,
                                               LocalDateTime startTime) {
        PreAggRefreshResult result = new PreAggRefreshResult();
        result.setSuccess(false);
        result.setStrategy(strategy);
        result.setErrorMessage(errorMessage);
        result.setException(exception);
        result.setStartTime(startTime);
        result.setEndTime(LocalDateTime.now());
        result.setDurationMs(Duration.between(startTime, result.getEndTime()).toMillis());
        return result;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("RefreshResult{success=true, strategy='%s', rows=%d, duration=%dms}",
                    strategy, affectedRows, durationMs);
        } else {
            return String.format("RefreshResult{success=false, strategy='%s', error='%s'}",
                    strategy, errorMessage);
        }
    }
}

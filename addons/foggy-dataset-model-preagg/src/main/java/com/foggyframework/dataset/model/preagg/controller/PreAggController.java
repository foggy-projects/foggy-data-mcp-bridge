package com.foggyframework.dataset.model.preagg.controller;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.preagg.ddl.PreAggSqlBuilder;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshResult;
import com.foggyframework.dataset.model.preagg.scheduler.PreAggScheduler;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 预聚合管理 REST API
 * <p>
 * 提供预聚合的管理、监控和手动操作接口。
 * 所有接口使用 {@link RX} 统一返回格式。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
@RestController
@RequestMapping("/api/pre-agg")
public class PreAggController {

    @Autowired(required = false)
    private PreAggScheduler scheduler;

    @Autowired(required = false)
    private Map<String, TableModel> tableModelMap;

    private final PreAggSqlBuilder sqlBuilder = new PreAggSqlBuilder();

    /**
     * 列出所有预聚合
     */
    @GetMapping("/list")
    public RX<List<PreAggInfo>> listPreAggregations() {
        List<PreAggInfo> result = new ArrayList<>();

        if (tableModelMap == null || tableModelMap.isEmpty()) {
            return RX.ok(result);
        }

        for (Map.Entry<String, TableModel> entry : tableModelMap.entrySet()) {
            String modelName = entry.getKey();
            TableModel model = entry.getValue();

            if (model.getPreAggregations() == null) {
                continue;
            }

            for (PreAggregation preAgg : model.getPreAggregations()) {
                PreAggInfo info = new PreAggInfo();
                info.setModelName(modelName);
                info.setPreAggName(preAgg.getName());
                info.setCaption(preAgg.getCaption());
                info.setTableName(preAgg.getTableName());
                info.setPriority(preAgg.getPriority());
                info.setEnabled(preAgg.isEnabled());
                info.setDimensions(new ArrayList<>(preAgg.getDimensionNames()));
                info.setMeasures(new ArrayList<>(preAgg.getMeasureAggregations().keySet()));

                // 获取调度状态
                if (scheduler != null) {
                    PreAggScheduler.ScheduledTaskInfo taskInfo = scheduler.getTaskStatus(modelName, preAgg.getName());
                    if (taskInfo != null) {
                        info.setScheduled(true);
                        info.setCronExpression(taskInfo.getCronExpression());
                        info.setLastRefreshTime(taskInfo.getLastRefreshTime() != null ?
                                taskInfo.getLastRefreshTime().toString() : null);
                        if (taskInfo.getLastResult() != null) {
                            info.setLastRefreshSuccess(taskInfo.getLastResult().isSuccess());
                        }
                    }
                }

                result.add(info);
            }
        }

        return RX.ok(result);
    }

    /**
     * 获取预聚合详情
     */
    @GetMapping("/{modelName}/{preAggName}")
    public RX<PreAggDetail> getPreAggregation(
            @PathVariable String modelName,
            @PathVariable String preAggName) {

        PreAggregation preAgg = findPreAggregation(modelName, preAggName);
        if (preAgg == null) {
            return RX.notFound().build();
        }

        TableModel model = tableModelMap.get(modelName);

        PreAggDetail detail = new PreAggDetail();
        detail.setModelName(modelName);
        detail.setPreAggName(preAgg.getName());
        detail.setCaption(preAgg.getCaption());
        detail.setTableName(preAgg.getTableName());
        detail.setSchema(preAgg.getSchema());
        detail.setPriority(preAgg.getPriority());
        detail.setEnabled(preAgg.isEnabled());
        detail.setDimensions(new ArrayList<>(preAgg.getDimensionNames()));
        detail.setGranularities(new HashMap<>());
        preAgg.getGranularities().forEach((k, v) -> detail.getGranularities().put(k, v.getConfigName()));
        detail.setDimensionProperties(new HashMap<>(preAgg.getDimensionProperties()));
        detail.setMeasureAggregations(new HashMap<>());
        preAgg.getMeasureAggregations().forEach((k, v) -> detail.getMeasureAggregations().put(k, v.name()));

        // 生成 DDL
        if (model != null) {
            detail.setCreateTableDdl(sqlBuilder.buildCreateTableDdl(preAgg, model));
        }

        // 获取调度状态
        if (scheduler != null) {
            PreAggScheduler.ScheduledTaskInfo taskInfo = scheduler.getTaskStatus(modelName, preAggName);
            if (taskInfo != null) {
                detail.setScheduled(true);
                detail.setCronExpression(taskInfo.getCronExpression());
                detail.setLastRefreshTime(taskInfo.getLastRefreshTime() != null ?
                        taskInfo.getLastRefreshTime().toString() : null);
                if (taskInfo.getLastResult() != null) {
                    detail.setLastRefreshSuccess(taskInfo.getLastResult().isSuccess());
                    detail.setLastRefreshDurationMs(taskInfo.getLastResult().getDurationMs());
                    detail.setLastRefreshAffectedRows(taskInfo.getLastResult().getAffectedRows());
                }
            }
        }

        return RX.ok(detail);
    }

    /**
     * 手动触发刷新
     */
    @PostMapping("/{modelName}/{preAggName}/refresh")
    public RX<RefreshResponse> triggerRefresh(
            @PathVariable String modelName,
            @PathVariable String preAggName,
            @RequestParam(defaultValue = "false") boolean forceFullRefresh) {

        if (scheduler == null) {
            return RX.failB("Scheduler not configured");
        }

        PreAggregation preAgg = findPreAggregation(modelName, preAggName);
        if (preAgg == null) {
            return RX.notFound().build();
        }

        log.info("Manual refresh triggered for {}:{} (forceFullRefresh={})",
                modelName, preAggName, forceFullRefresh);

        PreAggRefreshResult result = scheduler.triggerRefresh(modelName, preAggName, forceFullRefresh);

        RefreshResponse response = new RefreshResponse();
        response.setSuccess(result.isSuccess());
        response.setStrategy(result.getStrategy());
        response.setAffectedRows(result.getAffectedRows());
        response.setDurationMs(result.getDurationMs());
        response.setErrorMessage(result.getErrorMessage());

        return RX.ok(response);
    }

    /**
     * 获取 DDL
     */
    @GetMapping("/{modelName}/{preAggName}/ddl")
    public RX<DdlResponse> getDdl(
            @PathVariable String modelName,
            @PathVariable String preAggName) {

        PreAggregation preAgg = findPreAggregation(modelName, preAggName);
        if (preAgg == null) {
            return RX.notFound().build();
        }

        TableModel model = tableModelMap.get(modelName);
        if (model == null) {
            return RX.notFound().build();
        }

        DdlResponse response = new DdlResponse();
        response.setTableName(preAgg.getTableName());
        response.setCreateTableDdl(sqlBuilder.buildCreateTableDdl(preAgg, model));

        return RX.ok(response);
    }

    /**
     * 获取所有调度任务状态
     */
    @GetMapping("/tasks")
    public RX<Map<String, PreAggScheduler.ScheduledTaskInfo>> getScheduledTasks() {
        if (scheduler == null) {
            return RX.ok(Collections.emptyMap());
        }
        return RX.ok(scheduler.getScheduledTasks());
    }

    // ==================== 辅助方法 ====================

    private PreAggregation findPreAggregation(String modelName, String preAggName) {
        if (tableModelMap == null) {
            return null;
        }
        TableModel model = tableModelMap.get(modelName);
        if (model == null || model.getPreAggregations() == null) {
            return null;
        }
        for (PreAggregation preAgg : model.getPreAggregations()) {
            if (preAgg.getName().equals(preAggName)) {
                return preAgg;
            }
        }
        return null;
    }

    // ==================== DTO 类 ====================

    @Data
    public static class PreAggInfo {
        private String modelName;
        private String preAggName;
        private String caption;
        private String tableName;
        private int priority;
        private boolean enabled;
        private List<String> dimensions;
        private List<String> measures;
        private boolean scheduled;
        private String cronExpression;
        private String lastRefreshTime;
        private Boolean lastRefreshSuccess;
    }

    @Data
    public static class PreAggDetail extends PreAggInfo {
        private String schema;
        private Map<String, String> granularities;
        private Map<String, Set<String>> dimensionProperties;
        private Map<String, String> measureAggregations;
        private String createTableDdl;
        private Long lastRefreshDurationMs;
        private Long lastRefreshAffectedRows;
    }

    @Data
    public static class RefreshResponse {
        private boolean success;
        private String strategy;
        private long affectedRows;
        private long durationMs;
        private String errorMessage;
    }

    @Data
    public static class DdlResponse {
        private String tableName;
        private String createTableDdl;
    }
}

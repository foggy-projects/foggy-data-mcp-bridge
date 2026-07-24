package com.foggyframework.dataset.model.preagg.scheduler;

import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshContext;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshResult;
import com.foggyframework.dataset.model.preagg.refresh.PreAggRefreshService;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 预聚合调度器
 * <p>
 * 基于 Spring TaskScheduler 实现的预聚合刷新调度器。
 * 支持 Cron 表达式调度和手动触发。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggScheduler {

    private final TaskScheduler taskScheduler;
    private final PreAggRefreshService refreshService;

    /**
     * 已注册的调度任务
     * Key: modelName:preAggName
     */
    private final Map<String, ScheduledTaskInfo> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * 模型和数据源映射
     * Key: modelName
     */
    private final Map<String, ModelDataSourcePair> modelDataSources = new ConcurrentHashMap<>();

    public PreAggScheduler(TaskScheduler taskScheduler, PreAggRefreshService refreshService) {
        this.taskScheduler = taskScheduler;
        this.refreshService = refreshService;
    }

    /**
     * 注册模型的数据源
     */
    public void registerModel(String modelName, TableModel model, DataSource dataSource) {
        modelDataSources.put(modelName, new ModelDataSourcePair(model, dataSource));
        log.info("Registered model '{}' for pre-aggregation scheduling", modelName);
    }

    /**
     * 注册预聚合任务
     *
     * @param modelName 模型名称
     * @param preAgg    预聚合配置
     */
    public void registerPreAggregation(String modelName, PreAggregation preAgg) {
        if (!preAgg.isEnabled()) {
            log.info("Skipping disabled pre-aggregation: {}", preAgg.getName());
            return;
        }

        String cronExpression = getCronExpression(preAgg);
        if (cronExpression == null || cronExpression.isEmpty()) {
            log.info("No schedule configured for pre-aggregation: {}", preAgg.getName());
            return;
        }

        String taskKey = buildTaskKey(modelName, preAgg.getName());

        // 取消已有任务
        cancelTask(taskKey);

        // 创建新任务
        CronTrigger trigger = new CronTrigger(cronExpression);
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeRefresh(modelName, preAgg.getName(), false),
                trigger
        );

        ScheduledTaskInfo taskInfo = new ScheduledTaskInfo();
        taskInfo.setTaskKey(taskKey);
        taskInfo.setModelName(modelName);
        taskInfo.setPreAggName(preAgg.getName());
        taskInfo.setCronExpression(cronExpression);
        taskInfo.setFuture(future);
        taskInfo.setRegisteredAt(LocalDateTime.now());

        scheduledTasks.put(taskKey, taskInfo);

        log.info("Registered pre-aggregation task: {} with schedule '{}'",
                taskKey, cronExpression);
    }

    /**
     * 注销预聚合任务
     */
    public void unregisterPreAggregation(String modelName, String preAggName) {
        String taskKey = buildTaskKey(modelName, preAggName);
        cancelTask(taskKey);
        log.info("Unregistered pre-aggregation task: {}", taskKey);
    }

    /**
     * 手动触发刷新
     *
     * @param modelName     模型名称
     * @param preAggName    预聚合名称
     * @param forceFullRefresh 是否强制全量刷新
     * @return 刷新结果
     */
    public PreAggRefreshResult triggerRefresh(String modelName, String preAggName, boolean forceFullRefresh) {
        return executeRefresh(modelName, preAggName, forceFullRefresh);
    }

    /**
     * 获取所有已注册的任务
     */
    public Map<String, ScheduledTaskInfo> getScheduledTasks() {
        Map<String, ScheduledTaskInfo> snapshots = new ConcurrentHashMap<>();
        scheduledTasks.forEach((key, value) -> snapshots.put(key, snapshot(value)));
        return snapshots;
    }

    /**
     * 获取任务状态
     */
    public ScheduledTaskInfo getTaskStatus(String modelName, String preAggName) {
        String taskKey = buildTaskKey(modelName, preAggName);
        return snapshot(scheduledTasks.get(taskKey));
    }

    // ==================== 私有方法 ====================

    private PreAggRefreshResult executeRefresh(String modelName, String preAggName, boolean forceFullRefresh) {
        log.info("Executing refresh for pre-aggregation: {}:{}", modelName, preAggName);

        ModelDataSourcePair pair = modelDataSources.get(modelName);
        if (pair == null) {
            log.error("Model not registered: {}", modelName);
            return PreAggRefreshResult.failure("UNKNOWN",
                    "Model not registered: " + modelName, null, LocalDateTime.now());
        }

        TableModel model = pair.getModel();
        DataSource dataSource = pair.getDataSource();

        // 查找预聚合配置
        PreAggregation preAgg = findPreAggregation(model, preAggName);
        if (preAgg == null) {
            log.error("Pre-aggregation not found: {}", preAggName);
            return PreAggRefreshResult.failure("UNKNOWN",
                    "Pre-aggregation not found: " + preAggName, null, LocalDateTime.now());
        }

        // 创建上下文
        PreAggRefreshContext context = PreAggRefreshContext.of(modelName, preAggName);
        context.setForceFullRefresh(forceFullRefresh);

        String taskKey = buildTaskKey(modelName, preAggName);
        ScheduledTaskInfo taskInfo = scheduledTasks.get(taskKey);
        if (taskInfo == null) {
            return refreshService.refresh(preAgg, model, dataSource, context);
        }

        // One task lock covers context capture, refresh completion and status
        // publication. The refresh service also serializes on the runtime
        // PreAggregation, but keeping this outer lock prevents an older caller
        // from publishing its scheduler/controller mirror after a newer one.
        synchronized (taskInfo) {
            context.setLastRefreshTime(taskInfo.getLastRefreshTime());
            context.setLastWatermark(taskInfo.getLastWatermark());
            PreAggRefreshResult result =
                    refreshService.refresh(preAgg, model, dataSource, context);
            taskInfo.setLastResult(snapshot(result));
            if (result.isSuccess()) {
                taskInfo.setLastRefreshTime(result.getEndTime());
                if (result.getNewWatermark() != null) {
                    taskInfo.setLastWatermark(result.getNewWatermark());
                }
            }
            return result;
        }
    }

    private ScheduledTaskInfo snapshot(ScheduledTaskInfo taskInfo) {
        if (taskInfo == null) {
            return null;
        }
        synchronized (taskInfo) {
            ScheduledTaskInfo copy = new ScheduledTaskInfo();
            copy.setTaskKey(taskInfo.getTaskKey());
            copy.setModelName(taskInfo.getModelName());
            copy.setPreAggName(taskInfo.getPreAggName());
            copy.setCronExpression(taskInfo.getCronExpression());
            copy.setRunningSnapshot(taskInfo.isRunning());
            copy.setRegisteredAt(taskInfo.getRegisteredAt());
            copy.setLastRefreshTime(taskInfo.getLastRefreshTime());
            copy.setLastWatermark(taskInfo.getLastWatermark());
            copy.setLastResult(snapshot(taskInfo.getLastResult()));
            return copy;
        }
    }

    private PreAggRefreshResult snapshot(PreAggRefreshResult result) {
        if (result == null) {
            return null;
        }
        PreAggRefreshResult copy = new PreAggRefreshResult();
        copy.setSuccess(result.isSuccess());
        copy.setStrategy(result.getStrategy());
        copy.setAffectedRows(result.getAffectedRows());
        copy.setStartTime(result.getStartTime());
        copy.setEndTime(result.getEndTime());
        copy.setDurationMs(result.getDurationMs());
        copy.setErrorMessage(result.getErrorMessage());
        copy.setException(snapshot(result.getException()));
        copy.setNewWatermark(result.getNewWatermark());
        copy.setExecutedSql(result.getExecutedSql());
        return copy;
    }

    private Throwable snapshot(Throwable error) {
        if (error == null) {
            return null;
        }
        IllegalStateException copy = new IllegalStateException(
                error.getClass().getName() + ": " + error.getMessage());
        copy.setStackTrace(error.getStackTrace().clone());
        return copy;
    }

    private PreAggregation findPreAggregation(TableModel model, String preAggName) {
        if (model.getPreAggregations() == null) {
            return null;
        }
        for (PreAggregation preAgg : model.getPreAggregations()) {
            if (preAgg.getName().equals(preAggName)) {
                return preAgg;
            }
        }
        return null;
    }

    private void cancelTask(String taskKey) {
        ScheduledTaskInfo existing = scheduledTasks.remove(taskKey);
        if (existing != null && existing.getFuture() != null) {
            existing.getFuture().cancel(false);
        }
    }

    private String getCronExpression(PreAggregation preAgg) {
        if (preAgg.getRefreshConfig() == null) {
            return null;
        }
        return preAgg.getRefreshConfig().getSchedule();
    }

    private String buildTaskKey(String modelName, String preAggName) {
        return modelName + ":" + preAggName;
    }

    // ==================== 内部类 ====================

    @Data
    public static class ScheduledTaskInfo {
        private String taskKey;
        private String modelName;
        private String preAggName;
        private String cronExpression;
        private ScheduledFuture<?> future;
        private Boolean runningSnapshot;
        private LocalDateTime registeredAt;
        private LocalDateTime lastRefreshTime;
        private Object lastWatermark;
        private PreAggRefreshResult lastResult;

        public boolean isRunning() {
            if (runningSnapshot != null) {
                return runningSnapshot;
            }
            return future != null && !future.isDone() && !future.isCancelled();
        }
    }

    @Data
    private static class ModelDataSourcePair {
        private final TableModel model;
        private final DataSource dataSource;

        public ModelDataSourcePair(TableModel model, DataSource dataSource) {
            this.model = model;
            this.dataSource = dataSource;
        }
    }
}

package com.foggyframework.dataset.model.preagg.refresh;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.utils.DbUtils;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * 预聚合刷新服务
 * <p>
 * 协调预聚合表的刷新操作，根据配置选择合适的刷新策略。
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class PreAggRefreshService {

    private final Map<String, PreAggRefreshStrategy> strategies = new HashMap<>();

    public PreAggRefreshService() {
        // 注册默认策略
        registerStrategy(new FullRefreshStrategy());
        registerStrategy(new IncrementalRefreshStrategy());
    }

    /**
     * 注册刷新策略
     */
    public void registerStrategy(PreAggRefreshStrategy strategy) {
        strategies.put(strategy.getStrategyName(), strategy);
        log.info("Registered pre-aggregation refresh strategy: {}", strategy.getStrategyName());
    }

    /**
     * 执行预聚合刷新
     *
     * @param preAgg      预聚合配置
     * @param sourceModel 源模型
     * @param dataSource  数据源
     * @return 刷新结果
     */
    public PreAggRefreshResult refresh(PreAggregation preAgg,
                                        TableModel sourceModel,
                                        DataSource dataSource) {
        PreAggRefreshContext context = PreAggRefreshContext.of(
                sourceModel.getName(), preAgg.getName());
        return refresh(preAgg, sourceModel, dataSource, context);
    }

    /**
     * 执行预聚合刷新（带上下文）
     *
     * @param preAgg      预聚合配置
     * @param sourceModel 源模型
     * @param dataSource  数据源
     * @param context     刷新上下文
     * @return 刷新结果
     */
    public PreAggRefreshResult refresh(PreAggregation preAgg,
                                        TableModel sourceModel,
                                        DataSource dataSource,
                                        PreAggRefreshContext context) {
        if (context == null) {
            return PreAggRefreshResult.failure("UNKNOWN",
                    "Refresh context is required", null, java.time.LocalDateTime.now());
        }
        if (context.getDialect() == null) {
            try {
                FDialect dialect = DbUtils.getDialect(dataSource);
                context.setDialect(dialect);
            } catch (Exception e) {
                log.error("Failed to resolve refresh dialect for '{}': {}",
                        preAgg != null ? preAgg.getName() : "unknown", e.getMessage());
                return PreAggRefreshResult.failure("UNKNOWN",
                        "Failed to resolve refresh dialect: " + e.getMessage(),
                        e, context.getStartTime() != null
                                ? context.getStartTime() : java.time.LocalDateTime.now());
            }
        }
        // One runtime PreAggregation is the publication identity used by the
        // query matcher. Serialize refresh/commit/publication on that identity
        // so concurrent callers cannot publish an older result after a newer
        // transaction.
        synchronized (preAgg) {
            // The runtime object is the only boundary that has been published
            // after a committed refresh. Caller-supplied context state is only
            // transport data and must never bootstrap an incremental refresh or
            // move the proven boundary backwards/forwards.
            context.setLastWatermark(preAgg.getDataWatermark());
            context.setLastRefreshTime(preAgg.getLastRefreshTime());

            // 选择刷新策略
            PreAggRefreshStrategy strategy = selectStrategy(preAgg, context);

            if (strategy == null) {
                log.error("No suitable refresh strategy found for pre-aggregation: {}", preAgg.getName());
                return PreAggRefreshResult.failure("UNKNOWN",
                        "No suitable refresh strategy found", null, context.getStartTime());
            }

            log.info("Refreshing pre-aggregation '{}' using strategy '{}'",
                    preAgg.getName(), strategy.getStrategyName());

            // Only a committed successful result is visible to query matching.
            PreAggRefreshResult result = strategy.refresh(
                    preAgg, sourceModel, dataSource, context);
            if (result.isSuccess()) {
                preAgg.setLastRefreshTime(result.getEndTime());
                if (result.getNewWatermark() != null) {
                    preAgg.setDataWatermark(result.getNewWatermark());
                }
            }
            return result;
        }
    }

    /**
     * 选择刷新策略
     * <p>
     * 策略选择规则：
     * <ol>
     *   <li>如果强制全量刷新，使用 FULL 策略</li>
     *   <li>如果配置了增量刷新且满足条件，使用 INCREMENTAL 策略</li>
     *   <li>否则使用 FULL 策略</li>
     * </ol>
     * </p>
     */
    private PreAggRefreshStrategy selectStrategy(PreAggregation preAgg, PreAggRefreshContext context) {
        // 强制全量刷新
        if (context.isForceFullRefresh()) {
            return strategies.get("FULL");
        }

        // 检查配置的刷新策略
        PreAggRefreshDef refreshConfig = preAgg.getRefreshConfig();
        if (refreshConfig != null && refreshConfig.isIncrementalRefresh()) {
            if (context.getLastWatermark() == null) {
                log.info("Incremental pre-aggregation '{}' has no published boundary; "
                        + "using FULL refresh to establish complete history", preAgg.getName());
                return strategies.get("FULL");
            }
            PreAggRefreshStrategy incremental = strategies.get("INCREMENTAL");
            if (incremental != null && incremental.supports(preAgg)) {
                return incremental;
            }
            log.warn("Incremental refresh requested but not supported for '{}', falling back to FULL",
                    preAgg.getName());
        }

        // 默认全量刷新
        return strategies.get("FULL");
    }

    /**
     * 批量刷新模型的所有预聚合
     *
     * @param sourceModel 源模型
     * @param dataSource  数据源
     * @return 刷新结果映射（预聚合名 -> 结果）
     */
    public Map<String, PreAggRefreshResult> refreshAll(TableModel sourceModel, DataSource dataSource) {
        Map<String, PreAggRefreshResult> results = new HashMap<>();

        if (sourceModel.getPreAggregations() == null || sourceModel.getPreAggregations().isEmpty()) {
            log.info("No pre-aggregations configured for model: {}", sourceModel.getName());
            return results;
        }

        for (PreAggregation preAgg : sourceModel.getPreAggregations()) {
            if (!preAgg.isEnabled()) {
                log.info("Skipping disabled pre-aggregation: {}", preAgg.getName());
                continue;
            }

            PreAggRefreshResult result = refresh(preAgg, sourceModel, dataSource);
            results.put(preAgg.getName(), result);
        }

        return results;
    }
}

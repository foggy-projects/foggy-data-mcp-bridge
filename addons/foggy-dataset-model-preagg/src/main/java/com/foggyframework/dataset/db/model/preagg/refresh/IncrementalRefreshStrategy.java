package com.foggyframework.dataset.db.model.preagg.refresh;

import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.preagg.ddl.PreAggSqlBuilder;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 增量刷新策略
 * <p>
 * 增量刷新流程：
 * <ol>
 *   <li>获取上次水位线</li>
 *   <li>计算刷新范围（含 lookback）</li>
 *   <li>删除受影响的数据</li>
 *   <li>插入新聚合数据</li>
 *   <li>更新水位线</li>
 * </ol>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class IncrementalRefreshStrategy implements PreAggRefreshStrategy {

    private final PreAggSqlBuilder sqlBuilder;

    public IncrementalRefreshStrategy() {
        this.sqlBuilder = new PreAggSqlBuilder();
    }

    @Override
    public String getStrategyName() {
        return "INCREMENTAL";
    }

    @Override
    public boolean supports(PreAggregation preAgg) {
        // 增量刷新需要配置水位线列
        PreAggRefreshDef refreshConfig = preAgg.getRefreshConfig();
        if (refreshConfig == null) {
            return false;
        }
        String watermarkColumn = refreshConfig.getWatermarkColumn();
        return watermarkColumn != null && !watermarkColumn.isEmpty();
    }

    @Override
    public PreAggRefreshResult refresh(PreAggregation preAgg,
                                        TableModel sourceModel,
                                        DataSource dataSource,
                                        PreAggRefreshContext context) {
        LocalDateTime startTime = LocalDateTime.now();
        String preAggTableName = getFullTableName(preAgg);

        log.info("Starting INCREMENTAL refresh for pre-aggregation '{}' (table: {})",
                preAgg.getName(), preAggTableName);

        try {
            JdbcTemplate jdbcTemplate = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate();
            PreAggRefreshDef refreshConfig = preAgg.getRefreshConfig();

            // 1. 计算刷新范围
            LocalDate startDate = calculateStartDate(context, refreshConfig);
            LocalDate endDate = LocalDate.now();

            log.info("Incremental refresh range: {} to {}", startDate, endDate);

            // 2. 删除受影响的数据
            String deleteSql = sqlBuilder.buildIncrementalDeleteSql(preAgg, refreshConfig, startDate, endDate);
            log.debug("Executing DELETE: {}", deleteSql);
            int deletedRows = jdbcTemplate.update(deleteSql);
            log.info("Deleted {} rows from {} for date range {} to {}",
                    deletedRows, preAggTableName, startDate, endDate);

            // 3. 插入新聚合数据
            String insertSql = sqlBuilder.buildIncrementalInsertSql(preAgg, sourceModel, refreshConfig, startDate, endDate);
            log.debug("Executing INSERT: {}", insertSql);
            int insertedRows = jdbcTemplate.update(insertSql);

            LocalDateTime endTime = LocalDateTime.now();

            log.info("INCREMENTAL refresh completed for '{}': {} rows deleted, {} rows inserted in {}ms",
                    preAgg.getName(), deletedRows, insertedRows,
                    java.time.Duration.between(startTime, endTime).toMillis());

            PreAggRefreshResult result = PreAggRefreshResult.success(
                    getStrategyName(), insertedRows, startTime, endTime);
            result.setExecutedSql(insertSql);
            result.setNewWatermark(endDate);
            return result;

        } catch (Exception e) {
            log.error("INCREMENTAL refresh failed for '{}': {}", preAgg.getName(), e.getMessage(), e);
            return PreAggRefreshResult.failure(getStrategyName(), e.getMessage(), e, startTime);
        }
    }

    /**
     * 计算刷新起始日期
     * <p>
     * 如果有上次水位线，从水位线 - lookbackDays 开始；
     * 否则退化为全量刷新（从很早的日期开始）。
     * </p>
     */
    private LocalDate calculateStartDate(PreAggRefreshContext context, PreAggRefreshDef refreshConfig) {
        int lookbackDays = refreshConfig.getLookbackDays() != null ? refreshConfig.getLookbackDays() : 3;

        if (context.getLastWatermark() instanceof LocalDate lastDate) {
            return lastDate.minusDays(lookbackDays);
        }

        if (context.getLastRefreshTime() != null) {
            return context.getLastRefreshTime().toLocalDate().minusDays(lookbackDays);
        }

        // 没有历史记录，从 30 天前开始（避免全量扫描）
        log.warn("No previous watermark found, using default start date (30 days ago)");
        return LocalDate.now().minusDays(30);
    }

    /**
     * 获取完整表名
     */
    private String getFullTableName(PreAggregation preAgg) {
        String schema = preAgg.getSchema();
        String tableName = preAgg.getTableName();
        if (schema != null && !schema.isEmpty()) {
            return schema + "." + tableName;
        }
        return tableName;
    }
}

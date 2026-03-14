package com.foggyframework.dataset.db.model.preagg.refresh;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.db.model.preagg.ddl.ParameterizedSql;
import com.foggyframework.dataset.db.model.preagg.ddl.PreAggSqlBuilder;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 增量刷新策略
 * <p>
 * 增量刷新流程（原子操作）：
 * <ol>
 *   <li>获取上次水位线</li>
 *   <li>计算刷新范围（含 lookback）</li>
 *   <li>开启事务</li>
 *   <li>删除受影响的数据（参数化 SQL）</li>
 *   <li>插入新聚合数据（参数化 SQL）</li>
 *   <li>提交事务（或异常时回滚）</li>
 *   <li>更新水位线</li>
 * </ol>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class IncrementalRefreshStrategy implements PreAggRefreshStrategy {

    public IncrementalRefreshStrategy() {
    }

    /**
     * 根据上下文中的方言创建 PreAggSqlBuilder
     */
    private PreAggSqlBuilder createSqlBuilder(PreAggRefreshContext context) {
        FDialect dialect = context != null ? context.getDialect() : null;
        return new PreAggSqlBuilder(dialect);
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
        String preAggTableName = preAgg.getQualifiedTableName();

        log.info("Starting INCREMENTAL refresh for pre-aggregation '{}' (table: {})",
                preAgg.getName(), preAggTableName);

        try {
            PreAggRefreshDef refreshConfig = preAgg.getRefreshConfig();

            // 1. 计算刷新范围
            LocalDate startDate = calculateStartDate(context, refreshConfig);
            LocalDate endDate = LocalDate.now();

            log.info("Incremental refresh range: {} to {}", startDate, endDate);

            // 2. 构建参数化 SQL（使用上下文中的方言）
            PreAggSqlBuilder sqlBuilder = createSqlBuilder(context);
            ParameterizedSql deletePSql = sqlBuilder.buildIncrementalDeleteSql(preAgg, refreshConfig, startDate, endDate);
            ParameterizedSql insertPSql = sqlBuilder.buildIncrementalInsertSql(preAgg, sourceModel, refreshConfig, startDate, endDate);

            log.debug("DELETE SQL: {} params: {}", deletePSql.getSql(), deletePSql.getParams());
            log.debug("INSERT SQL: {} params: {}", insertPSql.getSql(), insertPSql.getParams());

            // 3. 使用编程式事务保证原子性
            int deletedRows;
            int insertedRows;
            Connection conn = dataSource.getConnection();
            try {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    // 删除受影响的数据（参数化）
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deletePSql.getSql())) {
                        setParameters(deleteStmt, deletePSql.getParams());
                        deletedRows = deleteStmt.executeUpdate();
                        log.info("Deleted {} rows from {} for date range {} to {}",
                                deletedRows, preAggTableName, startDate, endDate);
                    }

                    // 插入新聚合数据（参数化）
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertPSql.getSql())) {
                        setParameters(insertStmt, insertPSql.getParams());
                        insertedRows = insertStmt.executeUpdate();
                    }

                    conn.commit();
                    log.info("INCREMENTAL refresh committed: {} deleted, {} inserted into {}",
                            deletedRows, insertedRows, preAggTableName);

                } catch (Exception e) {
                    conn.rollback();
                    log.error("INCREMENTAL refresh rolled back for '{}': {}", preAgg.getName(), e.getMessage());
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } finally {
                conn.close();
            }

            LocalDateTime endTime = LocalDateTime.now();

            log.info("INCREMENTAL refresh completed for '{}': {} rows deleted, {} rows inserted in {}ms",
                    preAgg.getName(), deletedRows, insertedRows,
                    java.time.Duration.between(startTime, endTime).toMillis());

            PreAggRefreshResult result = PreAggRefreshResult.success(
                    getStrategyName(), insertedRows, startTime, endTime);
            result.setExecutedSql(insertPSql.getSql());
            result.setNewWatermark(endDate);
            return result;

        } catch (Exception e) {
            log.error("INCREMENTAL refresh failed for '{}': {}", preAgg.getName(), e.getMessage(), e);
            return PreAggRefreshResult.failure(getStrategyName(), e.getMessage(), e, startTime);
        }
    }

    /**
     * 设置 PreparedStatement 参数
     */
    private void setParameters(PreparedStatement stmt, List<Object> params) throws java.sql.SQLException {
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
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

}

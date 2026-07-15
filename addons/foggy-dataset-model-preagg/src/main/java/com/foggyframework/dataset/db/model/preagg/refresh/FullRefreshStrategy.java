package com.foggyframework.dataset.db.model.preagg.refresh;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.engine.preagg.internal.PreAggWatermarkResolver;
import com.foggyframework.dataset.db.model.preagg.ddl.PreAggSqlBuilder;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 全量刷新策略
 * <p>
 * 全量刷新流程（原子操作）：
 * <ol>
 *   <li>开启事务</li>
 *   <li>清空预聚合表（DELETE FROM，非 TRUNCATE，保证可回滚）</li>
 *   <li>从源表聚合数据并插入预聚合表</li>
 *   <li>提交事务（或异常时回滚）</li>
 * </ol>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class FullRefreshStrategy implements PreAggRefreshStrategy {

    public FullRefreshStrategy() {
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
        return "FULL";
    }

    @Override
    public boolean supports(PreAggregation preAgg) {
        // 全量刷新支持所有预聚合
        return true;
    }

    @Override
    public PreAggRefreshResult refresh(PreAggregation preAgg,
                                        TableModel sourceModel,
                                        DataSource dataSource,
                                        PreAggRefreshContext context) {
        LocalDateTime startTime = LocalDateTime.now();
        String preAggTableName = preAgg.getQualifiedTableName();

        log.info("Starting FULL refresh for pre-aggregation '{}' (table: {})",
                preAgg.getName(), preAggTableName);

        try {
            LocalDate exclusiveWatermark = null;
            if (preAgg.supportsHybridQuery()) {
                PreAggWatermarkResolver.Resolution watermarkResolution =
                        PreAggWatermarkResolver.resolve(
                                preAgg, sourceModel, preAgg.getRefreshConfig());
                PreAggWatermarkResolver.requireLocalDateBounds(
                        watermarkResolution, context.getDialect());
                // Watermarks are exclusive upper bounds. A cutoff at today's
                // start keeps the still-open current DATE bucket on the source
                // side of `materialized < wm / source >= wm`.
                exclusiveWatermark = startTime.toLocalDate();
            }

            // 构建 SQL（使用上下文中的方言）
            PreAggSqlBuilder sqlBuilder = createSqlBuilder(context);
            String deleteSql = buildDeleteSql(preAgg);
            String insertSql = sqlBuilder.buildFullRefreshInsertSql(preAgg, sourceModel);
            log.debug("DELETE SQL: {}", deleteSql);
            log.debug("INSERT SQL: {}", insertSql);

            // 使用编程式事务保证原子性
            int affectedRows;
            Connection conn = dataSource.getConnection();
            try {
                boolean originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    // 1. 清空预聚合表（DELETE FROM，DML，可回滚）
                    try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                        int deletedRows = deleteStmt.executeUpdate();
                        log.debug("Deleted {} rows from {}", deletedRows, preAggTableName);
                    }

                    // 2. 插入聚合数据
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        affectedRows = insertStmt.executeUpdate();
                    }

                    conn.commit();
                    log.info("FULL refresh committed: {} rows inserted into {}", affectedRows, preAggTableName);

                } catch (Exception e) {
                    conn.rollback();
                    log.error("FULL refresh rolled back for '{}': {}", preAgg.getName(), e.getMessage());
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            } finally {
                conn.close();
            }

            LocalDateTime endTime = LocalDateTime.now();

            log.info("FULL refresh completed for '{}': {} rows inserted in {}ms",
                    preAgg.getName(), affectedRows,
                    java.time.Duration.between(startTime, endTime).toMillis());

            PreAggRefreshResult result = PreAggRefreshResult.success(
                    getStrategyName(), affectedRows, startTime, endTime);
            result.setExecutedSql(insertSql);
            result.setNewWatermark(exclusiveWatermark);
            return result;

        } catch (Exception e) {
            log.error("FULL refresh failed for '{}': {}", preAgg.getName(), e.getMessage(), e);
            return PreAggRefreshResult.failure(getStrategyName(), e.getMessage(), e, startTime);
        }
    }

    /**
     * 构建 DELETE 语句（替代 TRUNCATE）
     * <p>
     * 使用 DELETE FROM 而非 TRUNCATE TABLE，因为：
     * <ul>
     *   <li>TRUNCATE 在 MySQL 中是 DDL 操作，会隐式提交事务，不可回滚</li>
     *   <li>SQLite 不支持 TRUNCATE TABLE 语法</li>
     *   <li>DELETE FROM 是 DML 操作，在事务中可回滚</li>
     * </ul>
     * </p>
     */
    private String buildDeleteSql(PreAggregation preAgg) {
        String tableName = preAgg.getQualifiedTableName();
        return "DELETE FROM " + tableName;
    }

}

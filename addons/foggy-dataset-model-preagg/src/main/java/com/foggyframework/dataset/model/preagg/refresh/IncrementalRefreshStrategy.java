package com.foggyframework.dataset.model.preagg.refresh;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.model.def.preagg.PreAggRefreshDef;
import com.foggyframework.dataset.model.engine.preagg.internal.PreAggWatermarkResolver;
import com.foggyframework.dataset.model.preagg.ddl.ParameterizedSql;
import com.foggyframework.dataset.model.preagg.ddl.PreAggSqlBuilder;
import com.foggyframework.dataset.model.spi.DbColumnType;
import com.foggyframework.dataset.model.spi.TableModel;
import com.foggyframework.dataset.model.spi.preagg.PreAggregation;
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
            LocalDate endDate = startTime.toLocalDate();
            LocalDate startDate = calculateStartDate(context, refreshConfig, endDate);

            log.info("Incremental refresh range: {} inclusive to {} exclusive", startDate, endDate);

            // 2. 构建参数化 SQL（使用上下文中的方言）
            PreAggWatermarkResolver.Resolution watermarkResolution =
                    PreAggWatermarkResolver.resolve(preAgg, sourceModel, refreshConfig);
            PreAggWatermarkResolver.requireLocalDateBounds(
                    watermarkResolution, context.getDialect());
            boolean bindIsoTextDate = usesSqliteTextDate(
                    watermarkResolution, context.getDialect());
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
                        setParameters(deleteStmt, deletePSql.getParams(), bindIsoTextDate);
                        deletedRows = deleteStmt.executeUpdate();
                        log.info("Deleted {} rows from {} for date range {} to {}",
                                deletedRows, preAggTableName, startDate, endDate);
                    }

                    // 插入新聚合数据（参数化）
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertPSql.getSql())) {
                        setParameters(insertStmt, insertPSql.getParams(), bindIsoTextDate);
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
            // The same exclusive bound is consumed by the query-side
            // `materialized < wm / source >= wm` split.
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
    private void setParameters(PreparedStatement stmt,
                               List<Object> params,
                               boolean bindIsoTextDate) throws java.sql.SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof LocalDate localDate) {
                // Xerial binds setDate as epoch milliseconds. SQLite demo
                // models deliberately store governed DATE captions as ISO
                // TEXT, so those bounds must stay in that same comparison
                // domain. Native DATE columns continue to use JDBC setDate.
                if (bindIsoTextDate) {
                    stmt.setString(i + 1, localDate.toString());
                } else {
                    stmt.setDate(i + 1, java.sql.Date.valueOf(localDate));
                }
            } else if (value instanceof LocalDateTime localDateTime) {
                stmt.setTimestamp(i + 1, java.sql.Timestamp.valueOf(localDateTime));
            } else {
                stmt.setObject(i + 1, value);
            }
        }
    }

    private boolean usesSqliteTextDate(PreAggWatermarkResolver.Resolution resolution,
                                       FDialect dialect) {
        DbColumnType type = resolution.sourceColumn().type();
        return dialect != null
                && dialect.getDbType() == DbType.SQLITE
                && (type == DbColumnType.TEXT || type == DbColumnType.STRING);
    }

    /**
     * 计算刷新起始日期
     * <p>
     * 如果有上次 DATE 水位线，从水位线 - lookbackDays 开始。
     * 无水位线时必须由服务层退化为 FULL，策略本身拒绝猜测范围。
     * </p>
     */
    private LocalDate calculateStartDate(PreAggRefreshContext context,
                                         PreAggRefreshDef refreshConfig,
                                         LocalDate endDate) {
        int lookbackDays = refreshConfig.getLookbackDays() != null ? refreshConfig.getLookbackDays() : 3;
        if (lookbackDays < 0) {
            throw new IllegalArgumentException(
                    "INCREMENTAL refresh lookbackDays must be non-negative");
        }

        if (context.getLastWatermark() instanceof LocalDate lastDate) {
            if (lastDate.isAfter(endDate)) {
                throw new IllegalArgumentException(
                        "INCREMENTAL refresh watermark must not be after its exclusive end");
            }
            return lastDate.minusDays(lookbackDays);
        }

        throw new IllegalStateException(
                "INCREMENTAL refresh requires a published LocalDate watermark; use FULL first");
    }

}

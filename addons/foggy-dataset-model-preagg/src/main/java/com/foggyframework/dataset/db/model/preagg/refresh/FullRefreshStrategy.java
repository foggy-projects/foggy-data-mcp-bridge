package com.foggyframework.dataset.db.model.preagg.refresh;

import com.foggyframework.dataset.db.model.preagg.ddl.PreAggSqlBuilder;
import com.foggyframework.dataset.db.model.spi.TableModel;
import com.foggyframework.dataset.db.model.spi.preagg.PreAggregation;
import com.foggyframework.dataset.utils.DataSourceQueryUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;

/**
 * 全量刷新策略
 * <p>
 * 全量刷新流程：
 * <ol>
 *   <li>清空预聚合表（TRUNCATE 或 DELETE）</li>
 *   <li>从源表聚合数据并插入预聚合表</li>
 * </ol>
 * </p>
 *
 * @author foggy-framework
 * @since 8.2.0
 */
@Slf4j
public class FullRefreshStrategy implements PreAggRefreshStrategy {

    private final PreAggSqlBuilder sqlBuilder;

    public FullRefreshStrategy() {
        this.sqlBuilder = new PreAggSqlBuilder();
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
        String preAggTableName = getFullTableName(preAgg);

        log.info("Starting FULL refresh for pre-aggregation '{}' (table: {})",
                preAgg.getName(), preAggTableName);

        try {
            JdbcTemplate jdbcTemplate = DataSourceQueryUtils.getDatasetTemplate(dataSource).getTemplate();

            // 1. 清空预聚合表
            String truncateSql = buildTruncateSql(preAgg);
            log.debug("Executing TRUNCATE: {}", truncateSql);
            jdbcTemplate.execute(truncateSql);

            // 2. 构建并执行 INSERT ... SELECT 语句
            String insertSql = sqlBuilder.buildFullRefreshInsertSql(preAgg, sourceModel);
            log.debug("Executing INSERT: {}", insertSql);

            int affectedRows = jdbcTemplate.update(insertSql);

            LocalDateTime endTime = LocalDateTime.now();

            log.info("FULL refresh completed for '{}': {} rows inserted in {}ms",
                    preAgg.getName(), affectedRows,
                    java.time.Duration.between(startTime, endTime).toMillis());

            PreAggRefreshResult result = PreAggRefreshResult.success(
                    getStrategyName(), affectedRows, startTime, endTime);
            result.setExecutedSql(insertSql);
            return result;

        } catch (Exception e) {
            log.error("FULL refresh failed for '{}': {}", preAgg.getName(), e.getMessage(), e);
            return PreAggRefreshResult.failure(getStrategyName(), e.getMessage(), e, startTime);
        }
    }

    /**
     * 构建 TRUNCATE 语句
     */
    private String buildTruncateSql(PreAggregation preAgg) {
        String tableName = getFullTableName(preAgg);
        return "TRUNCATE TABLE " + tableName;
    }

    /**
     * 获取完整表名（包含 schema）
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

package com.foggyframework.dataset.db.model.ecommerce;

import com.foggyframework.conversion.FsscriptConversionService;
import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.model.spi.JdbcQueryModel;
import com.foggyframework.dataset.db.model.spi.QueryModelLoader;
import com.foggyframework.dataset.db.model.spi.TableModelLoaderManager;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import com.foggyframework.dataset.db.dialect.DbType;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 电商测试基类
 *
 * <p>提供电商测试数据模型的公共测试支持</p>
 * <p>默认使用 SQLite profile（application.yml 中配置 spring.profiles.active=sqlite）。
 * 可通过 -Dspring.profiles.active=postgres 等参数切换到其他数据库。</p>
 *
 * @author foggy-dataset
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = JdbcModelTestApplication.class)
public abstract class EcommerceTestSupport {

    @Resource
    protected ApplicationContext appCtx;


    /**
     * 数据库类型配置，用于区分SQLite（轻量测试）和MySQL/Docker（完整测试）
     */
    @Value("${test.database.type:docker}")
    protected String databaseType;

    /**
     * 判断是否为轻量级测试模式（SQLite）
     */
    protected boolean isLightweightMode() {
        return "sqlite".equalsIgnoreCase(databaseType);
    }

    /**
     * 获取当前数据库方言标识（小写）。
     * <p>通过实际 DataSource 连接检测，而非配置文件推断。</p>
     *
     * @return 方言标识，如 "sqlite", "postgresql", "mysql", "sqlserver"
     */
    protected String getDialectKey() {
        return DbUtils.getDialect(jdbcTemplate.getDataSource())
                .getDbType().name().toLowerCase();
    }

    @Resource
    protected TableModelLoaderManager tableModelLoaderManager;

    @Resource
    protected QueryModelLoader queryModelLoader;

    @Resource
    protected FileFsscriptLoader fileFsscriptLoader;

    @Resource
    protected JdbcTemplate jdbcTemplate;

    /**
     * 电商模型文件根路径
     */
    protected static final String ECOMMERCE_MODEL_PATH = "classpath:/foggy/templates/ecommerce/model/";

    /**
     * 电商查询模型文件根路径
     */
    protected static final String ECOMMERCE_QUERY_PATH = "classpath:/foggy/templates/ecommerce/query/";

    /**
     * 从FSScript文件中加载对象
     *
     * @param path FSScript文件路径
     * @param cls 目标类型
     * @param name 导出对象名称
     * @param <T> 返回类型
     * @return 加载的对象
     */
    protected <T> T getTestObject(String path, Class<T> cls, String name) {
        Fsscript fScript = fileFsscriptLoader.findLoadFsscript(path);
        ExpEvaluator ee = fScript.eval(appCtx);

        Object model = ee.getExportObject(name);
        if (model == null) {
            throw RX.throwAUserTip(String.format("未能在[%s]中找到[%s]的定义", path, name));
        }

        return FsscriptConversionService.getSharedInstance().convert(model, cls);
    }

    /**
     * 获取查询模型
     *
     * @param queryModelName 查询模型名称
     * @return JdbcQueryModel
     */
    protected JdbcQueryModel getQueryModel(String queryModelName) {
        return (JdbcQueryModel) queryModelLoader.getJdbcQueryModel(queryModelName, null);
    }

    /**
     * 执行SQL查询，返回结果列表
     *
     * @param sql SQL语句
     * @return 查询结果
     */
    protected List<Map<String, Object>> executeQuery(String sql) {
        log.debug("执行SQL: {}", sql);
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 执行SQL查询，返回单个值
     *
     * @param sql SQL语句
     * @param requiredType 返回类型
     * @param <T> 返回类型
     * @return 查询结果
     */
    protected <T> T executeQueryForObject(String sql, Class<T> requiredType) {
        log.debug("执行SQL: {}", sql);
        return jdbcTemplate.queryForObject(sql, requiredType);
    }

    /**
     * 执行SQL查询，返回记录数
     *
     * @param tableName 表名
     * @return 记录数
     */
    protected Long getTableCount(String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        return executeQueryForObject(sql, Long.class);
    }

    protected void resetServiceTicketFixture() {
        String timestampType = timestampColumnType();
        jdbcTemplate.execute("DROP TABLE IF EXISTS service_ticket");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS service_ticket (
                    ticket_id VARCHAR(64) NOT NULL PRIMARY KEY,
                    team_id VARCHAR(64) NOT NULL,
                    created_at %s NOT NULL,
                    first_response_at %s NULL,
                    resolved_at %s NULL,
                    priority VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    channel VARCHAR(32) NOT NULL
                ) %s
                """.formatted(timestampType, timestampType, timestampType, testTableOptions()));
        jdbcTemplate.update("DELETE FROM service_ticket");
        jdbcTemplate.batchUpdate("""
                INSERT INTO service_ticket
                (ticket_id, team_id, created_at, first_response_at, resolved_at, priority, status, channel)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, Arrays.asList(
                new Object[]{"SLA-001", "T002", testDateTime("2026-05-01 08:00:00"), testDateTime("2026-05-01 10:00:00"), testDateTime("2026-05-02 09:00:00"), "P1", "RESOLVED", "WEB"},
                new Object[]{"SLA-002", "T002", testDateTime("2026-05-01 09:00:00"), testDateTime("2026-05-03 10:00:00"), testDateTime("2026-05-04 11:00:00"), "P2", "RESOLVED", "APP"},
                new Object[]{"SLA-003", "T002", testDateTime("2026-05-02 11:00:00"), null, null, "P1", "OPEN", "PHONE"},
                new Object[]{"SLA-004", "T002", testDateTime("2026-05-03 12:00:00"), testDateTime("2026-05-05 11:00:00"), testDateTime("2026-05-06 09:00:00"), "P3", "RESOLVED", "WEB"},
                new Object[]{"SLA-005", "T005", testDateTime("2026-05-01 08:30:00"), testDateTime("2026-05-01 09:00:00"), testDateTime("2026-05-01 18:00:00"), "P2", "RESOLVED", "APP"},
                new Object[]{"SLA-006", "T005", testDateTime("2026-05-04 08:00:00"), testDateTime("2026-05-05 07:00:00"), testDateTime("2026-05-05 20:00:00"), "P2", "RESOLVED", "WEB"},
                new Object[]{"SLA-007", "T005", testDateTime("2026-05-05 10:00:00"), testDateTime("2026-05-08 10:30:00"), testDateTime("2026-05-09 10:00:00"), "P1", "RESOLVED", "PHONE"},
                new Object[]{"SLA-008", "T008", testDateTime("2026-05-02 08:00:00"), testDateTime("2026-05-02 12:00:00"), testDateTime("2026-05-03 12:00:00"), "P3", "RESOLVED", "WEB"},
                new Object[]{"SLA-009", "T008", testDateTime("2026-05-07 14:00:00"), null, null, "P2", "OPEN", "APP"},
                new Object[]{"SLA-010", "T002", testDateTime("2026-04-29 09:00:00"), testDateTime("2026-04-29 10:00:00"), testDateTime("2026-04-30 18:00:00"), "P2", "RESOLVED", "WEB"}
        ));
    }

    protected void resetCrmLeadFixture() {
        String timestampType = timestampColumnType();
        resetCrmBaselineFactOrders();
        jdbcTemplate.execute("DROP TABLE IF EXISTS crm_lead");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_lead (
                    lead_id VARCHAR(64) NOT NULL PRIMARY KEY,
                    created_at %s NOT NULL,
                    lead_source VARCHAR(32) NOT NULL,
                    converted_opportunity_id VARCHAR(64) NULL,
                    converted_order_id VARCHAR(64) NULL
                ) %s
                """.formatted(timestampType, testTableOptions()));
        jdbcTemplate.update("DELETE FROM crm_lead");
        jdbcTemplate.batchUpdate("""
                INSERT INTO crm_lead
                (lead_id, created_at, lead_source, converted_opportunity_id, converted_order_id)
                VALUES (?, ?, ?, ?, ?)
                """, Arrays.asList(
                new Object[]{"CRM-001", testDateTime("2026-05-01 09:00:00"), "WEB", "OPP-001", "ORD20240101000001"},
                new Object[]{"CRM-002", testDateTime("2026-05-02 10:00:00"), "WEB", "OPP-002", null},
                new Object[]{"CRM-003", testDateTime("2026-05-03 11:00:00"), "WEB", null, null},
                new Object[]{"CRM-004", testDateTime("2026-05-04 12:00:00"), "APP", "OPP-004", "ORD20240104000007"},
                new Object[]{"CRM-005", testDateTime("2026-05-05 13:00:00"), "APP", "OPP-005", "ORD20240105000010"},
                new Object[]{"CRM-006", testDateTime("2026-05-06 14:00:00"), "APP", null, null},
                new Object[]{"CRM-007", testDateTime("2026-05-07 15:00:00"), "PHONE", "OPP-007", null},
                new Object[]{"CRM-008", testDateTime("2026-05-08 16:00:00"), "PHONE", null, null},
                new Object[]{"CRM-009", testDateTime("2026-04-30 17:00:00"), "WEB", "OPP-009", "ORD20240101000002"}
        ));
    }

    private void resetCrmBaselineFactOrders() {
        jdbcTemplate.update("""
                DELETE FROM fact_order
                WHERE order_id IN (
                    'ORD20240101000001',
                    'ORD20240101000002',
                    'ORD20240104000007',
                    'ORD20240105000010'
                )
                """);
        jdbcTemplate.batchUpdate("""
                INSERT INTO fact_order
                (order_id, date_key, customer_key, store_key, channel_key, promotion_key, total_quantity,
                 total_amount, discount_amount, freight_amount, pay_amount, order_status, payment_status, order_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, Arrays.asList(
                new Object[]{"ORD20240101000001", 20240101, 1, 1, 1, 2, 2, 10998.00, 1099.80, 0, 9898.20, "COMPLETED", "PAID", testDateTime("2024-01-01 10:30:00")},
                new Object[]{"ORD20240101000002", 20240101, 2, 2, 2, 3, 1, 2999.00, 0, 10, 3009.00, "COMPLETED", "PAID", testDateTime("2024-01-01 12:15:00")},
                new Object[]{"ORD20240104000007", 20240104, 4, 4, 1, 4, 2, 8998.00, 500.00, 0, 8498.00, "COMPLETED", "PAID", testDateTime("2024-01-04 13:30:00")},
                new Object[]{"ORD20240105000010", 20240105, 5, 5, 2, 2, 1, 3999.00, 399.90, 0, 3599.10, "COMPLETED", "PAID", testDateTime("2024-01-05 16:45:00")}
        ));
    }

    private Object testDateTime(String value) {
        if (DbUtils.getDialect(jdbcTemplate.getDataSource()).getDbType() == DbType.SQLITE) {
            return value;
        }
        return Timestamp.valueOf(value);
    }

    private String timestampColumnType() {
        return switch (DbUtils.getDialect(jdbcTemplate.getDataSource()).getDbType()) {
            case POSTGRESQL -> "TIMESTAMP";
            case SQLSERVER -> "DATETIME2";
            case SQLITE -> "TEXT";
            default -> "DATETIME";
        };
    }

    private String testTableOptions() {
        return switch (DbUtils.getDialect(jdbcTemplate.getDataSource()).getDbType()) {
            case MYSQL -> "DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
            default -> "";
        };
    }

    /**
     * 检查当前数据库是否支持窗口函数。
     * MySQL 8.0+、PostgreSQL、SQL Server、SQLite 3.25+ 均支持；MySQL 5.7 不支持。
     */
    protected boolean supportsWindowFunctions() {
        try {
            DataSource ds = jdbcTemplate.getDataSource();
            FDialect dialect = DbUtils.getDialect(ds);
            if (dialect.getDbType() == DbType.MYSQL) {
                try (Connection conn = ds.getConnection()) {
                    return conn.getMetaData().getDatabaseMajorVersion() >= 8;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("无法检测窗口函数支持: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查当前数据库是否支持公共表表达式（WITH）。
     * MySQL 8.0+、PostgreSQL、SQL Server、SQLite 均支持；MySQL 5.7 不支持。
     */
    protected boolean supportsCommonTableExpressions() {
        try {
            DataSource ds = jdbcTemplate.getDataSource();
            FDialect dialect = DbUtils.getDialect(ds);
            if (dialect.getDbType() == DbType.MYSQL) {
                try (Connection conn = ds.getConnection()) {
                    return conn.getMetaData().getDatabaseMajorVersion() >= 8;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("无法检测 CTE 支持: {}", e.getMessage());
            return false;
        }
    }

    protected void assumeCommonTableExpressionsSupported() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                supportsCommonTableExpressions(),
                "Current database does not support common table expressions");
    }

    /**
     * 使用当前数据库方言生成分页SQL。
     * <p>将 MySQL LIMIT 语法替换为各数据库通用的分页语法。</p>
     *
     * @param sql   原始SQL（不含 LIMIT）
     * @param limit 返回记录数
     * @return 分页SQL
     */
    protected String paginateSql(String sql, int limit) {
        FDialect dialect = DbUtils.getDialect(jdbcTemplate.getDataSource());
        return dialect.generatePagingSql(sql, 0, limit);
    }

    /**
     * 打印查询结果
     *
     * @param results 查询结果
     */
    protected void printResults(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            log.info("查询结果为空");
            return;
        }

        log.info("查询结果数量: {}", results.size());
        for (int i = 0; i < Math.min(10, results.size()); i++) {
            log.info("Row {}: {}", i + 1, results.get(i));
        }

        if (results.size() > 10) {
            log.info("... 还有 {} 条记录未显示", results.size() - 10);
        }
    }

    /**
     * 打印SQL语句
     *
     * @param sql SQL语句
     * @param description 描述
     */
    protected void printSql(String sql, String description) {
        log.info("========== {} ==========", description);
        log.info(sql);
        log.info("==========================================");
    }
}

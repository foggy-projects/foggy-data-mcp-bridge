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

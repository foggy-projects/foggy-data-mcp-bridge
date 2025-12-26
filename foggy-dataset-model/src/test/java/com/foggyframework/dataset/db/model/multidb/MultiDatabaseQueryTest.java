package com.foggyframework.dataset.db.model.multidb;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.utils.DbUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多数据库查询测试
 * 测试在不同数据库上的查询兼容性
 *
 * 运行方法:
 * - MySQL: mvn test -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=docker
 * - PostgreSQL: mvn test -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=postgres
 * - SQL Server: mvn test -Dtest=MultiDatabaseQueryTest -Dspring.profiles.active=sqlserver
 */
@SpringBootTest(classes = JdbcModelTestApplication.class)
@DisplayName("多数据库查询测试")
public class MultiDatabaseQueryTest {

    private static final Logger log = LoggerFactory.getLogger(MultiDatabaseQueryTest.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==========================================
    // 连接和方言测试
    // ==========================================

    @Test
    @DisplayName("测试数据库连接和方言检测")
    void testDatabaseConnection() {
        FDialect dialect = DbUtils.getDialect(dataSource);
        assertNotNull(dialect, "方言不应为空");

        log.info("当前数据库类型: {}", dialect.getProductName());
        log.info("数据库类型代码: {}", dialect.getDbType());
        log.info("标识符引用符: {} {}", dialect.openQuote(), dialect.closeQuote());

        // 测试连接
        Integer result = jdbcTemplate.queryForObject(dialect.getValidationQuery(), Integer.class);
        assertEquals(1, result, "验证查询应返回1");
    }

    @Test
    @DisplayName("测试表元数据加载")
    void testTableMetadataLoading() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        // 获取 dim_product 表信息
        SqlTable table = DbUtils.getTableByName(dataSource, "dim_product");

        if (table != null) {
            log.info("表名: {}", table.getName());
            log.info("列数: {}", table.getSqlColumns().size());

            // 验证关键列存在
            assertNotNull(table.getSqlColumn("product_key", true), "应存在 product_key 列");
            assertNotNull(table.getSqlColumn("product_id", true), "应存在 product_id 列");
            assertNotNull(table.getSqlColumn("product_name", true), "应存在 product_name 列");

            // 打印所有列
            for (SqlColumn col : table.getSqlColumns()) {
                log.info("列: {} (类型: {})", col.getName(), col.getJdbcType());
            }
        } else {
            log.warn("未找到 dim_product 表，可能测试数据未初始化");
        }
    }

    // ==========================================
    // 基本查询测试
    // ==========================================

    @Test
    @DisplayName("测试基本SELECT查询")
    void testBasicSelect() {
        FDialect dialect = DbUtils.getDialect(dataSource);
        String tableName = dialect.quoteIdentifier("dim_channel");

        String sql = "SELECT * FROM " + tableName;
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        log.info("dim_channel 表记录数: {}", results.size());

        if (!results.isEmpty()) {
            log.info("第一条记录: {}", results.get(0));
        }
    }

    @Test
    @DisplayName("测试COUNT聚合查询")
    void testCountQuery() {
        FDialect dialect = DbUtils.getDialect(dataSource);
        String tableName = dialect.quoteIdentifier("dict_status");

        String sql = "SELECT COUNT(*) FROM " + tableName;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);

        log.info("dict_status 表记录数: {}", count);
        assertTrue(count > 0, "dict_status 表应有数据");
    }

    @Test
    @DisplayName("测试WHERE条件查询")
    void testWhereQuery() {
        FDialect dialect = DbUtils.getDialect(dataSource);
        String tableName = dialect.quoteIdentifier("dict_status");
        String statusType = dialect.quoteIdentifier("status_type");

        String sql = "SELECT * FROM " + tableName + " WHERE " + statusType + " = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, "ORDER_STATUS");

        log.info("ORDER_STATUS 类型记录数: {}", results.size());
        assertFalse(results.isEmpty(), "应存在订单状态数据");
    }

    // ==========================================
    // 分页查询测试
    // ==========================================

    @Test
    @DisplayName("测试分页查询")
    void testPaginationQuery() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        // 基础SQL
        String baseSql = "SELECT * FROM " + dialect.quoteIdentifier("dict_category");

        // 使用方言生成分页SQL
        String pagedSql = dialect.generatePagingSql(baseSql, 0, 5);
        log.info("生成的分页SQL: {}", pagedSql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(pagedSql);
        log.info("分页查询返回记录数: {}", results.size());

        assertTrue(results.size() <= 5, "分页查询应最多返回5条记录");
    }

    @Test
    @DisplayName("测试带OFFSET的分页查询")
    void testPaginationWithOffset() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        String baseSql = "SELECT * FROM " + dialect.quoteIdentifier("dict_region") +
                " ORDER BY " + dialect.quoteIdentifier("region_id");

        // 第一页
        String page1Sql = dialect.generatePagingSql(baseSql, 0, 3);
        List<Map<String, Object>> page1 = jdbcTemplate.queryForList(page1Sql);

        // 第二页
        String page2Sql = dialect.generatePagingSql(baseSql, 3, 3);
        List<Map<String, Object>> page2 = jdbcTemplate.queryForList(page2Sql);

        log.info("第一页记录数: {}, 第二页记录数: {}", page1.size(), page2.size());

        // 验证两页数据不同
        if (!page1.isEmpty() && !page2.isEmpty()) {
            assertNotEquals(page1.get(0).get("region_id"), page2.get(0).get("region_id"),
                    "分页应返回不同的数据");
        }
    }

    // ==========================================
    // 聚合函数测试
    // ==========================================

    @Test
    @DisplayName("测试字符串聚合函数")
    void testStringAggFunction() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        // 使用方言构建字符串聚合
        String aggFunc = dialect.buildStringAggFunction(
                dialect.quoteIdentifier("category_name"),
                ","
        );

        String sql = "SELECT " + dialect.quoteIdentifier("category_level") + ", " + aggFunc + " AS names " +
                "FROM " + dialect.quoteIdentifier("dict_category") +
                " GROUP BY " + dialect.quoteIdentifier("category_level");

        log.info("字符串聚合SQL: {}", sql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        log.info("聚合结果: {}", results);

        assertFalse(results.isEmpty(), "聚合查询应返回结果");
    }

    @Test
    @DisplayName("测试SUM/AVG聚合")
    void testNumericAggregation() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        // 检查是否有数据
        String countSql = "SELECT COUNT(*) FROM " + dialect.quoteIdentifier("dim_product");
        Long count = jdbcTemplate.queryForObject(countSql, Long.class);

        if (count > 0) {
            String sql = "SELECT COUNT(*) AS cnt, " +
                    "SUM(" + dialect.quoteIdentifier("unit_price") + ") AS total_price, " +
                    "AVG(" + dialect.quoteIdentifier("unit_price") + ") AS avg_price " +
                    "FROM " + dialect.quoteIdentifier("dim_product");

            Map<String, Object> result = jdbcTemplate.queryForMap(sql);
            log.info("数值聚合结果: {}", result);

            assertNotNull(result.get("cnt"));
            assertNotNull(result.get("total_price"));
            assertNotNull(result.get("avg_price"));
        } else {
            log.warn("dim_product 表无数据，跳过数值聚合测试");
        }
    }

    // ==========================================
    // NULL 排序测试
    // ==========================================

    @Test
    @DisplayName("测试NULLS FIRST排序")
    void testNullsFirstOrdering() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        String columnExpr = dialect.quoteIdentifier("parent_id");
        String nullOrderClause = dialect.buildNullOrderClause(columnExpr, true);

        String sql = "SELECT " + dialect.quoteIdentifier("category_id") + ", " +
                dialect.quoteIdentifier("parent_id") + " " +
                "FROM " + dialect.quoteIdentifier("dict_category") +
                " ORDER BY " + nullOrderClause;

        log.info("NULLS FIRST SQL: {}", sql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        if (!results.isEmpty()) {
            // 验证 NULL 值在前
            Object firstParentId = results.get(0).get("parent_id");
            log.info("第一条记录的parent_id: {}", firstParentId);

            // 如果数据中存在NULL，第一条应该是NULL
            boolean hasNull = results.stream()
                    .anyMatch(r -> r.get("parent_id") == null);
            if (hasNull) {
                assertNull(firstParentId, "NULLS FIRST 排序时，NULL应在最前");
            }
        }
    }

    @Test
    @DisplayName("测试NULLS LAST排序")
    void testNullsLastOrdering() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        String columnExpr = dialect.quoteIdentifier("parent_id");
        String nullOrderClause = dialect.buildNullOrderClause(columnExpr, false);

        String sql = "SELECT " + dialect.quoteIdentifier("category_id") + ", " +
                dialect.quoteIdentifier("parent_id") + " " +
                "FROM " + dialect.quoteIdentifier("dict_category") +
                " ORDER BY " + nullOrderClause;

        log.info("NULLS LAST SQL: {}", sql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        if (!results.isEmpty()) {
            // 验证 NULL 值在后
            Object lastParentId = results.get(results.size() - 1).get("parent_id");
            log.info("最后一条记录的parent_id: {}", lastParentId);

            // 如果数据中存在NULL，最后一条应该是NULL
            boolean hasNull = results.stream()
                    .anyMatch(r -> r.get("parent_id") == null);
            if (hasNull) {
                assertNull(lastParentId, "NULLS LAST 排序时，NULL应在最后");
            }
        }
    }

    // ==========================================
    // JOIN 查询测试
    // ==========================================

    @Test
    @DisplayName("测试JOIN查询")
    void testJoinQuery() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        // 检查是否有数据
        String countSql = "SELECT COUNT(*) FROM " + dialect.quoteIdentifier("fact_sales");
        Long count = jdbcTemplate.queryForObject(countSql, Long.class);

        if (count > 0) {
            String sql = "SELECT s." + dialect.quoteIdentifier("sales_key") + ", " +
                    "p." + dialect.quoteIdentifier("product_name") + ", " +
                    "s." + dialect.quoteIdentifier("quantity") + ", " +
                    "s." + dialect.quoteIdentifier("sales_amount") + " " +
                    "FROM " + dialect.quoteIdentifier("fact_sales") + " s " +
                    "INNER JOIN " + dialect.quoteIdentifier("dim_product") + " p " +
                    "ON s." + dialect.quoteIdentifier("product_key") + " = p." + dialect.quoteIdentifier("product_key");

            // 添加分页
            String pagedSql = dialect.generatePagingSql(sql, 0, 10);
            log.info("JOIN查询SQL: {}", pagedSql);

            List<Map<String, Object>> results = jdbcTemplate.queryForList(pagedSql);
            log.info("JOIN查询返回记录数: {}", results.size());

            if (!results.isEmpty()) {
                log.info("第一条记录: {}", results.get(0));
            }
        } else {
            log.warn("fact_sales 表无数据，跳过JOIN测试");
        }
    }

    // ==========================================
    // 子查询测试
    // ==========================================

    @Test
    @DisplayName("测试子查询")
    void testSubQuery() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        // 检查是否有数据
        String countSql = "SELECT COUNT(*) FROM " + dialect.quoteIdentifier("dim_product");
        Long count = jdbcTemplate.queryForObject(countSql, Long.class);

        if (count > 0) {
            String sql = "SELECT * FROM " + dialect.quoteIdentifier("dim_product") +
                    " WHERE " + dialect.quoteIdentifier("unit_price") + " > " +
                    "(SELECT AVG(" + dialect.quoteIdentifier("unit_price") + ") FROM " +
                    dialect.quoteIdentifier("dim_product") + ")";

            String pagedSql = dialect.generatePagingSql(sql, 0, 10);
            log.info("子查询SQL: {}", pagedSql);

            List<Map<String, Object>> results = jdbcTemplate.queryForList(pagedSql);
            log.info("子查询返回记录数: {}", results.size());
        } else {
            log.warn("dim_product 表无数据，跳过子查询测试");
        }
    }
}

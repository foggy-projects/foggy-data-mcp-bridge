package com.foggyframework.dataset.db.model.multidb;

import com.foggyframework.core.ex.ExRuntimeExceptionImpl;
import com.foggyframework.dataset.db.dialect.DbType;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

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
    void testDatabaseConnection() throws Exception {
        FDialect dialect = DbUtils.getDialect(dataSource);
        assertNotNull(dialect, "方言不应为空");
        assertNotNull(dialect.getDbType(), "方言数据库类型不应为空");

        log.info("当前数据库类型: {}", dialect.getProductName());
        log.info("数据库类型代码: {}", dialect.getDbType());
        log.info("标识符引用符: {} {}", dialect.openQuote(), dialect.closeQuote());

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertFalse(connection.isClosed(), "用于矩阵测试的 JDBC 连接必须可用");
            assertFalse(metadata.getDatabaseProductName().isBlank(), "数据库产品名不应为空");
            assertFalse(metadata.getDatabaseProductVersion().isBlank(), "数据库产品版本不应为空");
            assertFalse(metadata.getURL().isBlank(), "JDBC URL 不应为空");

            if (dialect.getDbType() == DbType.MYSQL) {
                boolean mysql8OrNewer = metadata.getDatabaseMajorVersion() >= 8;
                assertEquals(mysql8OrNewer, dialect.supportsCte(),
                        "MySQL 方言的 CTE capability 必须与真实数据库大版本一致");
                assertEquals(mysql8OrNewer, dialect.supportsWindowFunctions(),
                        "MySQL 方言的 window capability 必须与真实数据库大版本一致");
            }
        }

        // 测试连接
        Integer result = jdbcTemplate.queryForObject(dialect.getValidationQuery(), Integer.class);
        assertEquals(1, result, "验证查询应返回1");
    }

    @Test
    @DisplayName("测试表元数据加载")
    void testTableMetadataLoading() {
        // 获取 dim_product 表信息
        SqlTable table = DbUtils.getTableByName(dataSource, "dim_product");

        assertNotNull(table, "矩阵 fixture 必须初始化 dim_product 表元数据");
        assertFalse(table.getSqlColumns().isEmpty(), "dim_product 表元数据必须包含列");
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
    }

    // ==========================================
    // 基本查询测试
    // ==========================================

    @Test
    @DisplayName("测试基本SELECT查询")
    void testBasicSelect() {
        FDialect dialect = DbUtils.getDialect(dataSource);
        String tableName = dialect.quoteIdentifier("dim_channel");

        String sql = "SELECT " + dialect.quoteIdentifier("channel_id") + ", " +
                dialect.quoteIdentifier("channel_name") + " FROM " + tableName +
                " ORDER BY " + dialect.quoteIdentifier("channel_id");
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        log.info("dim_channel 表记录数: {}", results.size());

        assertFalse(results.isEmpty(), "dim_channel fixture 必须非空");
        for (Map<String, Object> row : results) {
            assertFalse(requiredValue(row, "channel_id").toString().isBlank(), "channel_id 不应为空");
            assertFalse(requiredValue(row, "channel_name").toString().isBlank(), "channel_name 不应为空");
        }
        log.info("第一条记录: {}", results.get(0));
    }

    @Test
    @DisplayName("测试COUNT聚合查询")
    void testCountQuery() {
        FDialect dialect = DbUtils.getDialect(dataSource);
        String tableName = dialect.quoteIdentifier("dict_status");

        String sql = "SELECT COUNT(*) FROM " + tableName;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);

        log.info("dict_status 表记录数: {}", count);
        assertNotNull(count, "COUNT(*) 不应返回 NULL");
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
        for (Map<String, Object> row : results) {
            assertEquals("ORDER_STATUS", requiredValue(row, "status_type"));
            assertFalse(requiredValue(row, "status_code").toString().isBlank(), "status_code 不应为空");
        }
    }

    // ==========================================
    // 分页查询测试
    // ==========================================

    @Test
    @DisplayName("测试分页查询")
    void testPaginationQuery() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        // 用唯一键排序冻结分页契约，不依赖数据库默认顺序。
        String categoryId = dialect.quoteIdentifier("category_id");
        String baseSql = "SELECT " + categoryId + " FROM " +
                dialect.quoteIdentifier("dict_category") + " ORDER BY " + categoryId;
        List<Map<String, Object>> allRows = jdbcTemplate.queryForList(baseSql);
        assertTrue(allRows.size() >= 5, "dict_category fixture 至少需要5条数据以验证 limit");

        // 使用方言生成分页SQL
        String pagedSql = dialect.generatePagingSql(baseSql, 0, 5);
        log.info("生成的分页SQL: {}", pagedSql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(pagedSql);
        log.info("分页查询返回记录数: {}", results.size());

        assertEquals(5, results.size(), "非空 fixture 的首页应精确返回5条记录");
        assertEquals(columnValues(allRows.subList(0, 5), "category_id"),
                columnValues(results, "category_id"), "分页首页必须匹配确定性全量基线");
    }

    @Test
    @DisplayName("测试带OFFSET的分页查询")
    void testPaginationWithOffset() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        String baseSql = "SELECT * FROM " + dialect.quoteIdentifier("dict_region") +
                " ORDER BY " + dialect.quoteIdentifier("region_id");
        List<Map<String, Object>> allRows = jdbcTemplate.queryForList(baseSql);
        assertTrue(allRows.size() >= 6, "dict_region fixture 至少需要6条数据以验证 offset");

        // 第一页
        String page1Sql = dialect.generatePagingSql(baseSql, 0, 3);
        List<Map<String, Object>> page1 = jdbcTemplate.queryForList(page1Sql);

        // 第二页
        String page2Sql = dialect.generatePagingSql(baseSql, 3, 3);
        List<Map<String, Object>> page2 = jdbcTemplate.queryForList(page2Sql);

        log.info("第一页记录数: {}, 第二页记录数: {}", page1.size(), page2.size());

        assertEquals(3, page1.size(), "第一页应精确返回3条");
        assertEquals(3, page2.size(), "第二页应精确返回3条");
        assertEquals(columnValues(allRows.subList(0, 3), "region_id"), columnValues(page1, "region_id"),
                "第一页必须匹配确定性全量基线");
        assertEquals(columnValues(allRows.subList(3, 6), "region_id"), columnValues(page2, "region_id"),
                "OFFSET 页必须匹配确定性全量基线");
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
        Long groupCount = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT " +
                dialect.quoteIdentifier("category_level") + ") FROM " +
                dialect.quoteIdentifier("dict_category"), Long.class);
        assertNotNull(groupCount, "分组数不应为 NULL");
        assertEquals(groupCount.intValue(), results.size(), "字符串聚合应每个 category_level 返回一行");
        for (Map<String, Object> row : results) {
            requiredValue(row, "category_level");
            assertFalse(requiredValue(row, "names").toString().isBlank(), "聚合后的 names 不应为空");
        }
    }

    @Test
    @DisplayName("测试SUM/AVG聚合")
    void testNumericAggregation() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        long count = requireRowCount(dialect, "dim_product");
        String sql = "SELECT COUNT(*) AS cnt, " +
                "SUM(" + dialect.quoteIdentifier("unit_price") + ") AS total_price, " +
                "AVG(" + dialect.quoteIdentifier("unit_price") + ") AS avg_price " +
                "FROM " + dialect.quoteIdentifier("dim_product");

        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        log.info("数值聚合结果: {}", result);

        Number aggregatedCount = requiredNumber(result, "cnt");
        Number total = requiredNumber(result, "total_price");
        Number average = requiredNumber(result, "avg_price");
        assertEquals(count, aggregatedCount.longValue(), "COUNT(*) 必须与 fixture 行数一致");
        assertTrue(total.doubleValue() > 0, "SUM(unit_price) 应大于0");
        assertTrue(average.doubleValue() > 0, "AVG(unit_price) 应大于0");
        assertEquals(total.doubleValue() / count, average.doubleValue(),
                Math.max(0.000001d, Math.abs(average.doubleValue()) * 0.000000001d),
                "AVG(unit_price) 必须与 SUM/COUNT 一致");
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
                " ORDER BY " + nullOrderClause + ", " + dialect.quoteIdentifier("category_id");

        log.info("NULLS FIRST SQL: {}", sql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        assertFalse(results.isEmpty(), "dict_category fixture 必须非空");
        List<Object> parentIds = columnValues(results, "parent_id");
        int firstNonNull = firstIndex(parentIds, Objects::nonNull);
        assertTrue(firstNonNull > 0, "NULLS FIRST fixture 必须同时包含 NULL 和非 NULL");
        assertTrue(firstNonNull < parentIds.size(), "NULLS FIRST fixture 必须包含非 NULL");
        for (int i = 0; i < parentIds.size(); i++) {
            if (i < firstNonNull) {
                assertNull(parentIds.get(i), "NULLS FIRST 的 NULL 前缀不可被非 NULL 打断");
            } else {
                assertNotNull(parentIds.get(i), "NULLS FIRST 进入非 NULL 区间后不得再出现 NULL");
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
                " ORDER BY " + nullOrderClause + ", " + dialect.quoteIdentifier("category_id");

        log.info("NULLS LAST SQL: {}", sql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        assertFalse(results.isEmpty(), "dict_category fixture 必须非空");
        List<Object> parentIds = columnValues(results, "parent_id");
        int firstNull = firstIndex(parentIds, Objects::isNull);
        assertTrue(firstNull > 0, "NULLS LAST fixture 必须同时包含非 NULL 和 NULL");
        assertTrue(firstNull < parentIds.size(), "NULLS LAST fixture 必须包含 NULL");
        for (int i = 0; i < parentIds.size(); i++) {
            if (i < firstNull) {
                assertNotNull(parentIds.get(i), "NULLS LAST 的非 NULL 前缀不可被 NULL 打断");
            } else {
                assertNull(parentIds.get(i), "NULLS LAST 进入 NULL 区间后不得再出现非 NULL");
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

        long salesCount = requireRowCount(dialect, "fact_sales");
        requireRowCount(dialect, "dim_product");
        String fromJoin = " FROM " + dialect.quoteIdentifier("fact_sales") + " s " +
                "INNER JOIN " + dialect.quoteIdentifier("dim_product") + " p " +
                "ON s." + dialect.quoteIdentifier("product_key") + " = p." + dialect.quoteIdentifier("product_key");
        Long joinedCount = jdbcTemplate.queryForObject("SELECT COUNT(*)" + fromJoin, Long.class);
        assertNotNull(joinedCount, "JOIN COUNT(*) 不应为 NULL");
        assertEquals(salesCount, joinedCount.longValue(), "fact_sales fixture 的每个 product_key 都必须命中 dim_product");

        String sql = "SELECT s." + dialect.quoteIdentifier("sales_key") + " AS sales_key, " +
                "p." + dialect.quoteIdentifier("product_name") + " AS product_name, " +
                "s." + dialect.quoteIdentifier("quantity") + " AS quantity, " +
                "s." + dialect.quoteIdentifier("sales_amount") + " AS sales_amount" + fromJoin +
                " ORDER BY s." + dialect.quoteIdentifier("sales_key");

        String pagedSql = dialect.generatePagingSql(sql, 0, 10);
        log.info("JOIN查询SQL: {}", pagedSql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(pagedSql);
        log.info("JOIN查询返回记录数: {}", results.size());

        assertEquals((int) Math.min(10, joinedCount), results.size(), "JOIN 分页行数必须与 native count 一致");
        for (Map<String, Object> row : results) {
            requiredValue(row, "sales_key");
            assertFalse(requiredValue(row, "product_name").toString().isBlank(), "product_name 不应为空");
            assertTrue(requiredNumber(row, "quantity").doubleValue() > 0, "quantity 应大于0");
            assertTrue(requiredNumber(row, "sales_amount").doubleValue() > 0, "sales_amount 应大于0");
        }
        log.info("第一条记录: {}", results.get(0));
    }

    // ==========================================
    // 子查询测试
    // ==========================================

    @Test
    @DisplayName("测试子查询")
    void testSubQuery() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        requireRowCount(dialect, "dim_product");
        String productTable = dialect.quoteIdentifier("dim_product");
        String unitPrice = dialect.quoteIdentifier("unit_price");
        Map<String, Object> bounds = jdbcTemplate.queryForMap("SELECT MIN(" + unitPrice + ") AS min_price, " +
                "AVG(" + unitPrice + ") AS avg_price, MAX(" + unitPrice + ") AS max_price FROM " + productTable);
        Number min = requiredNumber(bounds, "min_price");
        Number average = requiredNumber(bounds, "avg_price");
        Number max = requiredNumber(bounds, "max_price");
        assertTrue(min.doubleValue() < average.doubleValue(), "fixture 最低价必须低于平均价");
        assertTrue(average.doubleValue() < max.doubleValue(), "fixture 最高价必须高于平均价");

        String predicate = unitPrice + " > (SELECT AVG(" + unitPrice + ") FROM " + productTable + ")";
        Long aboveAverageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + productTable +
                " WHERE " + predicate, Long.class);
        assertNotNull(aboveAverageCount, "子查询 oracle count 不应为 NULL");
        assertTrue(aboveAverageCount > 0, "fixture 必须包含高于平均价的商品");

        String sql = "SELECT " + dialect.quoteIdentifier("product_key") + ", " + unitPrice +
                " FROM " + productTable + " WHERE " + predicate +
                " ORDER BY " + dialect.quoteIdentifier("product_key");
        String pagedSql = dialect.generatePagingSql(sql, 0, 10);
        log.info("子查询SQL: {}", pagedSql);

        List<Map<String, Object>> results = jdbcTemplate.queryForList(pagedSql);
        log.info("子查询返回记录数: {}", results.size());
        assertEquals((int) Math.min(10, aboveAverageCount), results.size(), "子查询分页行数必须与 native oracle 一致");
        for (Map<String, Object> row : results) {
            requiredValue(row, "product_key");
            assertTrue(requiredNumber(row, "unit_price").doubleValue() > average.doubleValue(),
                    "子查询每行都必须满足 unit_price > AVG(unit_price)");
        }
    }

    // ==========================================
    // 高级分析测试
    // ==========================================

    @Test
    @DisplayName("测试COUNT(DISTINCT)查询")
    void testCountDistinct() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        long count = requireRowCount(dialect, "fact_sales");
        String sql = "SELECT COUNT(DISTINCT " + dialect.quoteIdentifier("product_key") +
                ") AS distinct_products FROM " + dialect.quoteIdentifier("fact_sales");

        log.info("COUNT(DISTINCT) SQL: {}", sql);

        Map<String, Object> result = jdbcTemplate.queryForMap(sql);
        log.info("COUNT(DISTINCT) 结果: {}", result);

        long distinctProducts = requiredNumber(result, "distinct_products").longValue();
        assertTrue(distinctProducts > 0, "去重产品数应大于0");
        assertTrue(distinctProducts <= count, "COUNT(DISTINCT product_key) 不得大于事实行数");
    }

    @Test
    @DisplayName("测试窗口函数 ROW_NUMBER/RANK")
    void testWindowFunctions() throws Exception {
        FDialect dialect = DbUtils.getDialect(dataSource);

        long count = requireRowCount(dialect, "fact_sales");
        String productKey = dialect.quoteIdentifier("product_key");
        String salesAmount = dialect.quoteIdentifier("sales_amount");
        String salesKey = dialect.quoteIdentifier("sales_key");
        // CTE 和 window 放在同一条真实 SQL 中：支持库走正向，MySQL 5.7 必须由数据库明确拒绝。
        String sql = "WITH ranked AS (" +
                "SELECT " + productKey + " AS product_key, " + salesAmount + " AS sales_amount, " +
                "ROW_NUMBER() OVER (ORDER BY " + salesAmount + " DESC, " + salesKey + ") AS rn, " +
                "RANK() OVER (PARTITION BY " + productKey + " ORDER BY " + salesAmount + " DESC) AS rnk " +
                "FROM " + dialect.quoteIdentifier("fact_sales") +
                ") SELECT product_key, sales_amount, rn, rnk FROM ranked WHERE rn <= 10 ORDER BY rn";
        log.info("窗口函数/CTE SQL: {}", sql);

        if (dialect.supportsCte() && dialect.supportsWindowFunctions()) {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            log.info("窗口函数返回记录数: {}", results.size());

            assertEquals((int) Math.min(10, count), results.size(), "CTE/window 行数必须与 fixture 一致");
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> row = results.get(i);
                requiredValue(row, "product_key");
                requiredNumber(row, "sales_amount");
                assertEquals(i + 1L, requiredNumber(row, "rn").longValue(), "ROW_NUMBER 应连续从1开始");
                assertTrue(requiredNumber(row, "rnk").longValue() > 0, "RANK 应大于0");
            }
            log.info("第一条记录: {}", results.get(0));
        } else {
            assertMySql57CapabilityRejected(dialect, sql, "CTE/window");
        }
    }

    @Test
    @DisplayName("测试窗口函数 LAG")
    void testLagWindowFunction() throws Exception {
        FDialect dialect = DbUtils.getDialect(dataSource);

        long count = requireRowCount(dialect, "fact_sales");
        String productKey = dialect.quoteIdentifier("product_key");
        String salesKey = dialect.quoteIdentifier("sales_key");
        String salesAmount = dialect.quoteIdentifier("sales_amount");
        String sql = "SELECT " + productKey + " AS product_key, " + salesKey + " AS sales_key, " +
                salesAmount + " AS sales_amount, " +
                "LAG(" + salesAmount + ", 1) OVER (PARTITION BY " + productKey +
                " ORDER BY " + salesKey + ") AS prev_amount " +
                "FROM " + dialect.quoteIdentifier("fact_sales") +
                " ORDER BY " + productKey + ", " + salesKey;
        String pagedSql = dialect.generatePagingSql(sql, 0, 10);
        log.info("LAG窗口函数SQL: {}", pagedSql);

        if (dialect.supportsWindowFunctions()) {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(pagedSql);
            log.info("LAG窗口函数返回记录数: {}", results.size());

            assertEquals((int) Math.min(10, count), results.size(), "LAG 行数必须与 fixture 一致");
            Object previousProduct = null;
            BigDecimal previousAmount = null;
            boolean firstRow = true;
            for (Map<String, Object> row : results) {
                Object product = requiredValue(row, "product_key");
                requiredValue(row, "sales_key");
                BigDecimal amount = decimal(requiredNumber(row, "sales_amount"));
                Object lagValue = value(row, "prev_amount");
                if (firstRow || !Objects.equals(previousProduct, product)) {
                    assertNull(lagValue, "每个 product partition 的首行 LAG 必须为 NULL");
                } else {
                    assertEquals(0, previousAmount.compareTo(decimal(lagValue)),
                            "LAG 必须等于同 product 上一个 sales_key 的 sales_amount");
                }
                previousProduct = product;
                previousAmount = amount;
                firstRow = false;
            }
            log.info("第一条记录: {}", results.get(0));
        } else {
            assertMySql57CapabilityRejected(dialect, pagedSql, "LAG window");
        }
    }

    @Test
    @DisplayName("测试移动平均窗口帧")
    void testMovingAverageFrame() throws Exception {
        FDialect dialect = DbUtils.getDialect(dataSource);

        long count = requireRowCount(dialect, "fact_sales");
        String productKey = dialect.quoteIdentifier("product_key");
        String salesKey = dialect.quoteIdentifier("sales_key");
        String salesAmount = dialect.quoteIdentifier("sales_amount");
        String sql = "SELECT " + productKey + " AS product_key, " + salesKey + " AS sales_key, " +
                salesAmount + " AS sales_amount, " +
                "AVG(" + salesAmount + ") OVER (PARTITION BY " + productKey +
                " ORDER BY " + salesKey +
                " ROWS BETWEEN 2 PRECEDING AND CURRENT ROW) AS ma3 " +
                "FROM " + dialect.quoteIdentifier("fact_sales") +
                " ORDER BY " + productKey + ", " + salesKey;
        String pagedSql = dialect.generatePagingSql(sql, 0, 10);
        log.info("移动平均SQL: {}", pagedSql);

        if (dialect.supportsWindowFunctions()) {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(pagedSql);
            log.info("移动平均返回记录数: {}", results.size());

            assertEquals((int) Math.min(10, count), results.size(), "移动平均行数必须与 fixture 一致");
            Object currentProduct = null;
            List<BigDecimal> window = new ArrayList<>();
            for (Map<String, Object> row : results) {
                Object product = requiredValue(row, "product_key");
                requiredValue(row, "sales_key");
                if (!Objects.equals(currentProduct, product)) {
                    window.clear();
                    currentProduct = product;
                }
                window.add(decimal(requiredNumber(row, "sales_amount")));
                if (window.size() > 3) {
                    window.remove(0);
                }
                double expectedAverage = window.stream().mapToDouble(BigDecimal::doubleValue).average().orElseThrow();
                double actualAverage = requiredNumber(row, "ma3").doubleValue();
                assertEquals(expectedAverage, actualAverage,
                        Math.max(0.000001d, Math.abs(expectedAverage) * 0.000000001d),
                        "ma3 必须等于当前 product 最近3行的平均值");
            }
            log.info("第一条记录: {}", results.get(0));
        } else {
            assertMySql57CapabilityRejected(dialect, pagedSql, "moving-average window");
        }
    }

    @Test
    @DisplayName("测试STDDEV统计函数")
    void testStddevFunction() {
        FDialect dialect = DbUtils.getDialect(dataSource);

        requireRowCount(dialect, "fact_sales");
        String salesAmount = dialect.quoteIdentifier("sales_amount");
        String factSales = dialect.quoteIdentifier("fact_sales");
        if (dialect.getDbType() == DbType.SQLITE) {
            String expectedMessage = "SQLite 不支持统计函数 STDDEV_POP。" +
                    "请切换到 MySQL、PostgreSQL 或 SQL Server 数据源以使用此功能。";
            ExRuntimeExceptionImpl dialectRefusal = assertThrows(ExRuntimeExceptionImpl.class,
                    () -> dialect.buildStatFunction("STDDEV_POP", salesAmount));
            assertEquals(expectedMessage, dialectRefusal.getMessage(), "SQLite 方言必须返回稳定的 STDDEV 拒绝信息");
            assertEquals(expectedMessage, dialectRefusal.getUserTip(), "SQLite 用户可见拒绝信息不得丢失");

            // 绕过方言的 fail-fast，确认真实 SQLite engine 也会明确拒绝该函数。
            String rawSql = "SELECT STDDEV_POP(" + salesAmount + ") AS stddev_amount FROM " + factSales;
            DataAccessException dbRefusal = assertThrows(DataAccessException.class,
                    () -> jdbcTemplate.queryForList(rawSql));
            SQLException sqlException = requireSqlException(dbRefusal, "SQLite STDDEV_POP");
            assertEquals(SQLiteException.class, sqlException.getClass(),
                    "SQLite 应以驱动的 SQLiteException 拒绝未注册聚合函数");
            SQLiteException sqliteException = (SQLiteException) sqlException;
            assertEquals(SQLiteErrorCode.SQLITE_ERROR, sqliteException.getResultCode());
            assertEquals(1, sqliteException.getErrorCode(), "SQLITE_ERROR vendor code 应为1");
            assertTrue(sqliteException.getMessage().contains("no such function: STDDEV_POP"),
                    "SQLite 应明确报告缺失 STDDEV_POP，实际: " + sqliteException.getMessage());
        } else {
            String funcExpr = dialect.buildStatFunction("STDDEV_POP", salesAmount);
            String productKey = dialect.quoteIdentifier("product_key");
            String sql = "SELECT " + productKey + " AS product_key, " +
                    funcExpr + " AS stddev_amount FROM " + factSales +
                    " GROUP BY " + productKey + " ORDER BY " + productKey;
            Long distinctProducts = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT " + productKey +
                    ") FROM " + factSales, Long.class);
            assertNotNull(distinctProducts, "STDDEV 分组 oracle 不应为 NULL");

            log.info("STDDEV SQL: {}", sql);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            log.info("STDDEV 返回记录数: {}", results.size());

            assertEquals(distinctProducts.intValue(), results.size(), "STDDEV 应每个 product_key 返回一行");
            for (Map<String, Object> row : results) {
                requiredValue(row, "product_key");
                assertTrue(requiredNumber(row, "stddev_amount").doubleValue() >= 0,
                        "STDDEV_POP 应为非负数");
            }
        }
    }

    private long requireRowCount(FDialect dialect, String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " +
                dialect.quoteIdentifier(tableName), Long.class);
        assertNotNull(count, tableName + " COUNT(*) 不应为 NULL");
        assertTrue(count > 0, tableName + " fixture 必须非空");
        return count;
    }

    private List<Object> columnValues(List<Map<String, Object>> rows, String columnName) {
        List<Object> values = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            values.add(value(row, columnName));
        }
        return values;
    }

    private int firstIndex(List<Object> values, Predicate<Object> predicate) {
        for (int i = 0; i < values.size(); i++) {
            if (predicate.test(values.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private Object requiredValue(Map<String, Object> row, String columnName) {
        Object result = value(row, columnName);
        assertNotNull(result, columnName + " 不应为 NULL");
        return result;
    }

    private Number requiredNumber(Map<String, Object> row, String columnName) {
        Object result = requiredValue(row, columnName);
        assertTrue(result instanceof Number,
                columnName + " 应为数值，实际为 " + result.getClass().getName());
        return (Number) result;
    }

    private Object value(Map<String, Object> row, String columnName) {
        String actualKey = row.keySet().stream()
                .filter(key -> key.equalsIgnoreCase(columnName))
                .findFirst()
                .orElse(null);
        assertNotNull(actualKey, "查询结果缺少列 " + columnName + "，实际列: " + row.keySet());
        return row.get(actualKey);
    }

    private BigDecimal decimal(Object value) {
        assertTrue(value instanceof Number, "期望数值，实际为: " +
                (value == null ? "null" : value.getClass().getName()));
        return new BigDecimal(value.toString());
    }

    private void assertMySql57CapabilityRejected(FDialect dialect, String sql, String capability) throws Exception {
        assertEquals(DbType.MYSQL, dialect.getDbType(),
                capability + " 不支持分支只允许出现在 MySQL 5.7 lane");
        assertFalse(dialect.supportsCte(), "MySQL 5.7 方言必须关闭 CTE capability");
        assertFalse(dialect.supportsWindowFunctions(), "MySQL 5.7 方言必须关闭 window capability");
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertEquals("MySQL", metadata.getDatabaseProductName(), "拒绝断言必须运行在真实 MySQL 上");
            assertEquals(5, metadata.getDatabaseMajorVersion(), "拒绝断言只属于 MySQL 5.7");
            assertEquals(7, metadata.getDatabaseMinorVersion(), "拒绝断言只属于 MySQL 5.7");
        }

        DataAccessException refusal = assertThrows(DataAccessException.class,
                () -> jdbcTemplate.queryForList(sql), capability + " 必须由 MySQL 5.7 真实拒绝");
        SQLException sqlException = requireSqlException(refusal, capability);
        assertEquals(SQLSyntaxErrorException.class, sqlException.getClass(),
                capability + " 应返回 JDBC SQLSyntaxErrorException");
        assertEquals("42000", sqlException.getSQLState(), capability + " SQLState 应为42000");
        assertEquals(1064, sqlException.getErrorCode(), capability + " MySQL vendor code 应为1064");
        assertTrue(sqlException.getMessage().toLowerCase(Locale.ROOT).contains("syntax"),
                capability + " 拒绝信息应明确说明语法错误，实际: " + sqlException.getMessage());
    }

    private SQLException requireSqlException(Throwable failure, String capability) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException) {
                return (SQLException) current;
            }
            current = current.getCause();
        }
        fail(capability + " 拒绝链中缺少 SQLException，实际: " + failure);
        throw new AssertionError("unreachable");
    }
}

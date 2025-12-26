package com.foggyframework.dataset.db.model.multidb;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.model.test.JdbcModelTestApplication;
import com.foggyframework.dataset.utils.DbUtils;
import jakarta.annotation.Resource;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

/**
 * 多数据库测试基类
 * 提供各数据库通用测试支持
 */
@SpringBootTest(classes = JdbcModelTestApplication.class)
public abstract class MultiDatabaseTestSupport {

    @Resource
    protected DataSource dataSource;

    /**
     * 获取当前数据库方言
     */
    protected FDialect getDialect() {
        return DbUtils.getDialect(dataSource);
    }

    /**
     * 获取当前数据库类型名称
     */
    protected String getDatabaseType() {
        return getDialect().getProductName();
    }

    /**
     * 检查当前是否为 MySQL
     */
    protected boolean isMySQL() {
        return getDialect() == FDialect.MYSQL_DIALECT;
    }

    /**
     * 检查当前是否为 PostgreSQL
     */
    protected boolean isPostgreSQL() {
        return getDialect() == FDialect.POSTGRES_DIALECT;
    }

    /**
     * 检查当前是否为 SQL Server
     */
    protected boolean isSQLServer() {
        return getDialect() == FDialect.SQLSERVER_DIALECT;
    }

    /**
     * 检查当前是否为 SQLite
     */
    protected boolean isSQLite() {
        return getDialect() == FDialect.SQLITE_DIALECT;
    }
}

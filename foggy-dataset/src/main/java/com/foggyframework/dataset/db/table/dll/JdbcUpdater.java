package com.foggyframework.dataset.db.table.dll;


import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.db.SqlObject;
import com.foggyframework.dataset.db.DbUpdater;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.utils.DbUtils;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JdbcUpdater implements DbUpdater {


    FDialect dialect;

    DataSource dataSource;

    List<String> scripts = new ArrayList<String>();

    public JdbcUpdater(DataSource dataSource) {
        this(DbUtils.getDialect(dataSource), dataSource);
    }

    public JdbcUpdater(FDialect dialect, DataSource dataSource) {
        super();
        this.dialect = dialect;
        this.dataSource = dataSource;
    }

    /**
     * 根据数据库中是否已经存在该对象，判断是更新还是创建
     */
    @Override
    public void addDbObject(SqlObject dbObject) {
        SqlTable tableFromDb = dialect.getTableByName(dataSource, dbObject.getName());
        if (tableFromDb == null) {
            addCreateScript(dbObject);
        } else {
            for (String str : DbUtils.generateAlertSql(dialect, dbObject, tableFromDb)) {
                scripts.add(str);
            }

        }
    }

    @Override
    public void addModifyScript(SqlObject dbObject) {
        SqlTable tableFromDb = dialect.getTableByName(dataSource, dbObject.getName());
        if (tableFromDb == null) {
            throw RX.throwB("");
        }
        for (String str : DbUtils.generateAlertSql(dialect, dbObject, tableFromDb)) {
            scripts.add(str);
        }
    }

    @Override
    public void addCreateScript(SqlObject dbObject) {
        scripts.add(DbUtils.generateCreateSql(dialect, dbObject));
    }

    public void addDropScript(SqlTable st) {
        scripts.add(DbUtils.generateDropSql(dialect, st));
    }

    @Override
    public void clear() {
        scripts.clear();
    }

    @Override
    public void execute(int mode) throws SQLException {
        Connection connection = null;
        Statement stmt = null;

        log.info("exceuting JdbcUpdater");
        try {
            connection = dataSource.getConnection();
            stmt = connection.createStatement();
            for (String sql : scripts) {
                log.debug(sql);

                // SchemaUpdate
                try {
                    stmt.executeUpdate(sql);
                } catch (Throwable e) {
                    log.error("Unsuccessful: " + sql);
//					System.err.println("Unsuccessful: " + sql);
//					logger.error(e.getMessage());
                    if (mode == MODE_SKIP_ERROR) {
                        continue;
                    }
                    throw RX.throwB(e);
                }
            }
            // connection.commit();
            log.info("exceuting JdbcUpdater completed!");
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }

    @Override
    public void addIndex(SqlTable st, SqlColumn column) {
        String sql = DbUtils.generateCreateIndexSql(dialect, st, column);
        scripts.add(sql);
    }

    /**
     * 不会抛出异常
     */
    private void executeX() {
        for (String sql : scripts) {
            exceuteX1(sql);
        }
    }

    private void exceuteX1(String sql) {
        Connection connection = null;
        Statement stmt = null;

        System.err.println("exceuting JdbcUpdater" + sql);
        try {
            connection = dataSource.getConnection();
            stmt = connection.createStatement();
            if (log.isDebugEnabled()) {
                log.debug(sql);
            }


            stmt.executeUpdate(sql);
            if (log.isDebugEnabled()) {
                log.info("exceuting JdbcUpdater completed!");
            }
        } catch (Throwable e1) {
            log.error("Unsuccessful: " + sql);
            System.err.println(e1.getMessage());
        } finally {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                }
            }
        }
    }
}

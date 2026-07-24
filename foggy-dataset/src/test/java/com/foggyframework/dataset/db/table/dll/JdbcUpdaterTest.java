package com.foggyframework.dataset.db.table.dll;

import com.foggyframework.core.utils.UuidUtils;
import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Types;

@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
@ActiveProfiles("mysql57-it")
class JdbcUpdaterTest {

    @Autowired
    DataSource dataSource;

    /**
     * 测试创建表及drop表
     *
     * @throws SQLException SQL异常
     */
    @Test
    void testCreateAndDropTable() throws SQLException {
        JdbcUpdater jdbcUpdater = new JdbcUpdater(dataSource);

        SqlTable table = buildTestSqlTable("A" + UuidUtils.newUuid());

        jdbcUpdater.addCreateScript(table);
        jdbcUpdater.execute(JdbcUpdater.MODE_NORMAL);
        //检查表是否正确创建
        SqlTable tableFromDb = FDialect.MYSQL_DIALECT.getTableByName(dataSource, table.getName());
        Assertions.assertNotNull(tableFromDb);
        jdbcUpdater.clear();

        jdbcUpdater.addDropScript(table);

        jdbcUpdater.execute(JdbcUpdater.MODE_NORMAL);

        tableFromDb = FDialect.MYSQL_DIALECT.getTableByName(dataSource, table.getName());
        Assertions.assertNull(tableFromDb);
    }

    @Test
    void testModifyTable() throws SQLException {
        JdbcUpdater jdbcUpdater = new JdbcUpdater(dataSource);

        SqlTable table = buildTestSqlTable("A" + UuidUtils.newUuid());

        jdbcUpdater.addCreateScript(table);
        jdbcUpdater.execute(JdbcUpdater.MODE_NORMAL);

        jdbcUpdater.clear();

        table.addSqlColumn(new SqlColumn("cxx", "cxx", Types.VARCHAR));
        jdbcUpdater.addModifyScript(table);

        jdbcUpdater.execute(JdbcUpdater.MODE_NORMAL);

        SqlTable tableFromDb = FDialect.MYSQL_DIALECT.getTableByName(dataSource, table.getName());

        Assertions.assertNotNull(tableFromDb.getSqlColumn("cxx", true));

        //drop表
        jdbcUpdater.clear();
        jdbcUpdater.addDropScript(table);
        jdbcUpdater.execute(JdbcUpdater.MODE_NORMAL);

    }

    private SqlTable buildTestSqlTable(String tableName) {
        SqlTable table = new SqlTable(tableName, tableName);

        table.addSqlColumn(new SqlColumn("c1", "c1", Types.VARCHAR));
        table.addSqlColumn(new SqlColumn("c2", "c2", Types.INTEGER));
        table.addSqlColumn(new SqlColumn("c3", "c3", Types.TIMESTAMP));
        table.addSqlColumn(new SqlColumn("c4", "c4", Types.VARCHAR, 128));
        table.addSqlColumn(new SqlColumn("c5", "c5", Types.DOUBLE));
        table.addSqlColumn(new SqlColumn("c6", "c6", Types.DATE));

        return table;
    }
}

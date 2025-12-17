package com.foggyframework.dataset.db.table.dll;

import com.foggyframework.core.utils.FileUtils;
import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.db.table.SqlColumn;
import com.foggyframework.dataset.db.table.SqlTable;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;
import java.sql.Types;

public class TableGeneratorTest {

    /**
     * 测试生成表语句
     *
     * @throws IOException
     */
    @Test
    public void generatorCreate() throws IOException {

        TableGenerator tableGenerator = new TableGenerator(buildTest1SqlTable(), getTestFDialect());

        String createSql = tableGenerator.generatorCreate();
        System.err.println(createSql);

        URL url = TableGeneratorTest.class.getResource("table1_mysql.txt");
        Assert.assertEquals(FileUtils.toString(url.openStream()).trim(), createSql);
    }

    @Test
    public void generatorCreate2() throws IOException {

        TableGenerator tableGenerator = new TableGenerator(buildTest1SqlTable2(), getTestFDialect());

        String createSql = tableGenerator.generatorCreate();
        System.err.println(createSql);

        URL url = TableGeneratorTest.class.getResource("table2_mysql.txt");
        Assert.assertEquals(FileUtils.toString(url.openStream()).trim(), createSql);
    }

    private SqlTable buildTest1SqlTable() {
        SqlTable table = new SqlTable("test1", "test1");
        table.addSqlColumn(new SqlColumn("c1", "c1", Types.VARCHAR));
        table.addSqlColumn(new SqlColumn("c2", "c2", Types.INTEGER));
        table.addSqlColumn(new SqlColumn("c3", "c3", Types.TIMESTAMP));
        table.addSqlColumn(new SqlColumn("c4", "c4", Types.VARCHAR, 128));
        table.addSqlColumn(new SqlColumn("c5", "c5", Types.DOUBLE));
        table.addSqlColumn(new SqlColumn("c6", "c6", Types.DATE));
        return table;
    }

    private SqlTable buildTest1SqlTable2() {
        SqlTable table = new SqlTable("test2", "test2");
        table.setIdColumn(new SqlColumn("c1", "c1", Types.VARCHAR));
        table.addSqlColumn(table.getIdColumn());
        table.addSqlColumn(new SqlColumn("c2", "c2", Types.INTEGER));

        return table;
    }

    private FDialect getTestFDialect() {
        return FDialect.MYSQL_DIALECT;
    }

}
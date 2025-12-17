package com.foggyframework.dataset.db.utils;

import com.foggyframework.core.utils.FileUtils;
import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.dataset.utils.DbUtils;
import com.foggyframework.dataset.utils.JdbcTableUtils;
import com.foggyframework.dataset.utils.SqlTableBuilder;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
public class JdbcTableUtilsTest {

    @Resource
    FileFsscriptLoader fileFsscriptLoader;

    @Resource
    ApplicationContext applicationContext;

    @Resource
    DataSource dataSource;

    @Test
    public void createOrUpdateTable() throws SQLException {
        URL tableDefFsscript = JdbcTableUtilsTest.class.getResource("M_ETL_TEST.fsscript");
        Fsscript fScript = fileFsscriptLoader.findLoadFsscript(tableDefFsscript);
        ExpEvaluator ee = fScript.newInstance(applicationContext);
        fScript.eval(ee);
        Map<String, Object> exportMap = ee.getExportMap();
        Map tableDef = (Map) ((Map) exportMap.get("default")).get("table");

        SqlTable sqlTable = JdbcTableUtils.createOrUpdateTable(dataSource,tableDef);

        SqlTable sqlTableFromDb = DbUtils.getTableByName(dataSource,sqlTable.getName());

        //TODO 检查两个SqlTable是否相等


    }

    @Test
    public void genTableBuilder() throws IOException {
        URL tableDefFsscript = JdbcTableUtilsTest.class.getResource("M_ETL_TEST.fsscript");

        Fsscript fScript = fileFsscriptLoader.findLoadFsscript(tableDefFsscript);
        ExpEvaluator ee = fScript.newInstance(applicationContext);
        fScript.eval(ee);
        Map<String, Object> exportMap = ee.getExportMap();
        Map tableDef = (Map) ((Map) exportMap.get("default")).get("table");

        SqlTableBuilder builder = JdbcTableUtils.genTableBuilder(tableDef);
        SqlTable table = builder.buildSqlTable();

        String str = DbUtils.generateCreateSql(DbUtils.getDialect(dataSource),table);

        URL mysqlOutput = JdbcTableUtilsTest.class.getResource("M_ETL_TEST.mysql.txt");
        Assert.assertEquals(FileUtils.toString(mysqlOutput.openStream()).trim(),str);
    }

    @Test
    public void createOrUpdateTableV2() throws SQLException {
        URL tableDefFsscript = JdbcTableUtilsTest.class.getResource("M_ETL_TEST_V2.fsscript");
        Fsscript fScript = fileFsscriptLoader.findLoadFsscript(tableDefFsscript);
        ExpEvaluator ee = fScript.newInstance(applicationContext);
        fScript.eval(ee);
        Map<String, Object> exportMap = ee.getExportMap();
        Map tableDef = (Map) exportMap.get("table");

        SqlTableBuilder builder = JdbcTableUtils.genTableBuilder(tableDef);
        SqlTable table = builder.buildSqlTable();
        String str = DbUtils.generateCreateSql(DbUtils.getDialect(dataSource),table);

        Assertions.assertEquals(table.getSqlColumn("c4",true).getLength(),1888);
        Assertions.assertEquals(table.getSqlColumn("c5",true).getLength(),1888);

        Assertions.assertEquals(table.getSqlColumn("c3",true).getJdbcType(), Types.INTEGER);
        Assertions.assertEquals(builder.getColumnBuilderByName("c3").isIndex(), true);



    }

    @Test
    public void genTableBuilderV2() throws IOException {
        URL tableDefFsscript = JdbcTableUtilsTest.class.getResource("M_ETL_TEST_V2.fsscript");

        Fsscript fScript = fileFsscriptLoader.findLoadFsscript(tableDefFsscript);
        ExpEvaluator ee = fScript.newInstance(applicationContext);
        fScript.eval(ee);
        Map<String, Object> exportMap = ee.getExportMap();
        Map tableDef = (Map) exportMap.get("table");

        SqlTableBuilder builder = JdbcTableUtils.genTableBuilder(tableDef);
        SqlTable table = builder.buildSqlTable();

        String str = DbUtils.generateCreateSql(DbUtils.getDialect(dataSource),table);

        URL mysqlOutput = JdbcTableUtilsTest.class.getResource("M_ETL_TEST_V2.mysql.txt");
        Assert.assertEquals(FileUtils.toString(mysqlOutput.openStream()).trim(),str);
    }
}
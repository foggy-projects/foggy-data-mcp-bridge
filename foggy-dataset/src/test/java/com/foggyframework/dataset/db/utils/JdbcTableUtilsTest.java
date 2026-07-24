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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Map;

@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
@ActiveProfiles("mysql57-it")
class JdbcTableUtilsTest {

    @Autowired
    FileFsscriptLoader fileFsscriptLoader;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    DataSource dataSource;

    @Test
    void createOrUpdateTable() throws SQLException {
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
    void genTableBuilder() throws IOException {
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
        Assertions.assertEquals(FileUtils.toString(mysqlOutput.openStream()).trim(),str);
    }

    @Test
    void createOrUpdateTableV2() throws SQLException {
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
    void genTableBuilderV2() throws IOException {
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
        Assertions.assertEquals(FileUtils.toString(mysqlOutput.openStream()).trim(),str);
    }
}

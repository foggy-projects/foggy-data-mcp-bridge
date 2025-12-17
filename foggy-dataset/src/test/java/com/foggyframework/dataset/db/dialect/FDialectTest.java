package com.foggyframework.dataset.db.dialect;

import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import com.foggyframework.dataset.db.table.SqlTable;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import javax.sql.DataSource;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
public class FDialectTest {

    @Resource
    DataSource dataSource;


    @Test
    public void getColumnsByTableName() {

        SqlTable table = FDialect.MYSQL_DIALECT.getTableByName(dataSource,"M_ETL_TEST");
        Assert.assertNotNull(table.getSqlColumn("test_id",false));
        Assert.assertNotNull(table.getSqlColumn("c1",false));
        Assert.assertNotNull(table.getSqlColumn("c2",false));
        Assert.assertNotNull(table.getSqlColumn("c3",false));
        Assert.assertNotNull(table.getSqlColumn("c4",false));

    }
    @Test
    public void getColumnsByTableName2() {

        SqlTable table = FDialect.MYSQL_DIALECT.getTableByName(dataSource,"M_ETL_TEST",true);
//        Assert.assertNotNull(table.getSqlColumn("test_id",false));

        Assert.assertEquals(table.getIdColumn().getName(),"test_id");
    }
}
package com.foggyframework.dataset.db.dialect;

import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import com.foggyframework.dataset.db.table.SqlTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
@ActiveProfiles("mysql57-it")
class FDialectTest {

    @Autowired
    DataSource dataSource;


    @Test
    void getColumnsByTableName() {

        SqlTable table = FDialect.MYSQL_DIALECT.getTableByName(dataSource,"M_ETL_TEST");
        Assertions.assertNotNull(table.getSqlColumn("test_id",false));
        Assertions.assertNotNull(table.getSqlColumn("c1",false));
        Assertions.assertNotNull(table.getSqlColumn("c2",false));
        Assertions.assertNotNull(table.getSqlColumn("c3",false));
        Assertions.assertNotNull(table.getSqlColumn("c4",false));

    }
    @Test
    void getColumnsByTableName2() {

        SqlTable table = FDialect.MYSQL_DIALECT.getTableByName(dataSource,"M_ETL_TEST",true);
//        Assertions.assertNotNull(table.getSqlColumn("test_id",false));

        Assertions.assertEquals(table.getIdColumn().getName(),"test_id");
    }
}

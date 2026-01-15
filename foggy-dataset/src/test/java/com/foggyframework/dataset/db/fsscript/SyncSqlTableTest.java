package com.foggyframework.dataset.db.fsscript;

import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import com.foggyframework.dataset.db.table.SqlTable;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
class SyncSqlTableTest {

    @Autowired
    FileFsscriptLoader fileFsscriptLoader;

    @Autowired
    ApplicationContext appCtx;

    @Test
    void execute() {

//        org.springframework.core.io.Resource res = appCtx.getResource("classpath:/com/foggyframework/dataset/db/fscript/SyncSqlTableTest.fsscript");

        Fsscript fScript = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/dataset/db/fsscript/SyncSqlTableTest.fsscript");

        ExpEvaluator ee = fScript.eval(appCtx);

        Object sqlTable = ee.getExportObjectInDefault("sqlTable");
        Object sqlTable2 = ee.getExportObjectInDefault("sqlTable2");

        Assertions.assertTrue(sqlTable instanceof SqlTable);
        Assertions.assertTrue(sqlTable2 instanceof SqlTable);
    }
}

package com.foggyframework.dataset.table.curd;

import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
class BugFixInsertUpdateMapTest {

    @Autowired
    FileFsscriptLoader fileFsscriptLoader;

    @Autowired
    ApplicationContext appCtx;

    @Test
    void execute() {

//        org.springframework.core.io.Resource res = appCtx.getResource("classpath:/com/foggyframework/dataset/db/fscript/SyncSqlTableTest.fsscript");

        Fsscript fScript = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/dataset/db/table/curd/bug_fix_insertUpdateMapData.fsscript");

        ExpEvaluator ee = fScript.eval(appCtx);

//        Object sqlTable = ee.getExportObject("sqlTable");
//        Object sqlTable2 = ee.getExportObject("sqlTable2");
//
//        Assertions.assertTrue(sqlTable instanceof SqlTable);
//        Assertions.assertTrue(sqlTable2 instanceof SqlTable);
    }
}

package com.foggyframework.dataset.table.curd;

import com.foggyframework.dataset.FoggyFrameworkDataSetTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkDataSetTestApplication.class)
public class BugFixInsertUpdateMapTest {

    @Resource
    FileFsscriptLoader fileFsscriptLoader;

    @Resource
    ApplicationContext appCtx;

    @Test
    public void execute() {

//        org.springframework.core.io.Resource res = appCtx.getResource("classpath:/com/foggyframework/dataset/db/fscript/SyncSqlTableTest.fsscript");

        Fsscript fScript = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/dataset/db/table/curd/bug_fix_insertUpdateMapData.fsscript");

        ExpEvaluator ee = fScript.eval(appCtx);

//        Object sqlTable = ee.getExportObject("sqlTable");
//        Object sqlTable2 = ee.getExportObject("sqlTable2");
//
//        Assert.assertTrue(sqlTable instanceof SqlTable);
//        Assert.assertTrue(sqlTable2 instanceof SqlTable);
    }
}
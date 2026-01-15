package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class ImportBeanExpTest {
    @Autowired
    ApplicationContext appCtx;
    @Autowired
    FoggyFrameworkFsscriptTestApplication.PtTest importBeanTest;
    @Autowired
    FoggyFrameworkFsscriptTestApplication.PtTest importBeanTest3;

    @Test
     void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_bean_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assertions.assertEquals("ok",ee.getCurrentFsscriptClosure().getVar("result1"));
        Assertions.assertEquals("testR",ee.getCurrentFsscriptClosure().getVar("result2"));

        Assertions.assertEquals("tx3",ee.getCurrentFsscriptClosure().getVar("result3"));

        Assertions.assertEquals("aaaaa",ee.getCurrentFsscriptClosure().getVar("result4_1"));

        Assertions.assertEquals("1",ee.getCurrentFsscriptClosure().getVar("result4_2"));

        Assertions.assertEquals(2,ee.getCurrentFsscriptClosure().getVar("resultTestArg"));

    }
    @Test
    void evalValue2() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_bean_test2.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assertions.assertEquals("ok",ee.getCurrentFsscriptClosure().getVar("result1"));
        Assertions.assertEquals("testR",ee.getCurrentFsscriptClosure().getVar("result2"));

        Assertions.assertEquals("tx3",ee.getCurrentFsscriptClosure().getVar("result3"));

        Assertions.assertEquals("aaaaa",ee.getCurrentFsscriptClosure().getVar("result4_1"));

        Assertions.assertEquals("1",ee.getCurrentFsscriptClosure().getVar("result4_2"));

        Assertions.assertEquals(2,ee.getCurrentFsscriptClosure().getVar("resultTestArg"));
        Assertions.assertEquals(22,ee.getCurrentFsscriptClosure().getVar("resultTestArg2"));

        FsscriptClosure fc=ee.getCurrentFsscriptClosure();
        Assertions.assertEquals(importBeanTest,fc.getVar("importBeanTest2"));
        Assertions.assertEquals(importBeanTest3,fc.getVar("importBeanTest4"));
    }

    @Test
    void evalValueAs() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_bean_as_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assertions.assertEquals("ok",ee.getCurrentFsscriptClosure().getVar("result1"));
        Assertions.assertEquals("testR",ee.getCurrentFsscriptClosure().getVar("result2"));

        Assertions.assertEquals("tx3",ee.getCurrentFsscriptClosure().getVar("result3"));

        Assertions.assertEquals("aaaaa",ee.getCurrentFsscriptClosure().getVar("result4_1"));

        Assertions.assertEquals("1",ee.getCurrentFsscriptClosure().getVar("result4_2"));

        Assertions.assertEquals(2,ee.getCurrentFsscriptClosure().getVar("resultTestArg"));

    }
}
package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class ImportBeanExpTest {
    @Resource
    ApplicationContext appCtx;
    @Resource
    FoggyFrameworkFsscriptTestApplication.PtTest importBeanTest;
    @Resource
    FoggyFrameworkFsscriptTestApplication.PtTest importBeanTest3;

    @Test
     void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_bean_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assert.assertEquals("ok",ee.getCurrentFsscriptClosure().getVar("result1"));
        Assert.assertEquals("testR",ee.getCurrentFsscriptClosure().getVar("result2"));

        Assert.assertEquals("tx3",ee.getCurrentFsscriptClosure().getVar("result3"));

        Assert.assertEquals("aaaaa",ee.getCurrentFsscriptClosure().getVar("result4_1"));

        Assert.assertEquals("1",ee.getCurrentFsscriptClosure().getVar("result4_2"));

        Assert.assertEquals(2,ee.getCurrentFsscriptClosure().getVar("resultTestArg"));

    }
    @Test
    void evalValue2() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_bean_test2.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assert.assertEquals("ok",ee.getCurrentFsscriptClosure().getVar("result1"));
        Assert.assertEquals("testR",ee.getCurrentFsscriptClosure().getVar("result2"));

        Assert.assertEquals("tx3",ee.getCurrentFsscriptClosure().getVar("result3"));

        Assert.assertEquals("aaaaa",ee.getCurrentFsscriptClosure().getVar("result4_1"));

        Assert.assertEquals("1",ee.getCurrentFsscriptClosure().getVar("result4_2"));

        Assert.assertEquals(2,ee.getCurrentFsscriptClosure().getVar("resultTestArg"));
        Assert.assertEquals(22,ee.getCurrentFsscriptClosure().getVar("resultTestArg2"));

        FsscriptClosure fc=ee.getCurrentFsscriptClosure();
        Assert.assertEquals(importBeanTest,fc.getVar("importBeanTest2"));
        Assert.assertEquals(importBeanTest3,fc.getVar("importBeanTest4"));
    }

    @Test
    void evalValueAs() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_bean_as_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assert.assertEquals("ok",ee.getCurrentFsscriptClosure().getVar("result1"));
        Assert.assertEquals("testR",ee.getCurrentFsscriptClosure().getVar("result2"));

        Assert.assertEquals("tx3",ee.getCurrentFsscriptClosure().getVar("result3"));

        Assert.assertEquals("aaaaa",ee.getCurrentFsscriptClosure().getVar("result4_1"));

        Assert.assertEquals("1",ee.getCurrentFsscriptClosure().getVar("result4_2"));

        Assert.assertEquals(2,ee.getCurrentFsscriptClosure().getVar("resultTestArg"));

    }
}
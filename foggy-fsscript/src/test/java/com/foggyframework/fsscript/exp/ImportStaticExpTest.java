package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import com.foggyframework.fsscript.support.ImportStaticClassTest;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class ImportStaticExpTest {
    @Resource
    ApplicationContext appCtx;
    @Resource
    FoggyFrameworkFsscriptTestApplication.PtTest importBeanTest;
    @Resource
    FoggyFrameworkFsscriptTestApplication.PtTest importBeanTest3;

    @Test
    void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_static_class_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assert.assertEquals("abc", ee.getCurrentFsscriptClosure().getVar("result"));
        Assert.assertEquals("abc1", ee.getCurrentFsscriptClosure().getVar("result1"));

        Assert.assertEquals("abc2", ee.getCurrentFsscriptClosure().getVar("result2"));

        ImportStaticClassTest aa = ee.getExportObject("aa");

        Assertions.assertEquals(aa.getAa(), "a4");

    }

}
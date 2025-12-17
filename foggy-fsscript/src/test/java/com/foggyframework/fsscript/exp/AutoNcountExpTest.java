package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class AutoNcountExpTest {

    @Resource
    ApplicationContext appCtx;

    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/auto_ncount_test_1.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);


        FsscriptFunction x = (FsscriptFunction) ee.getExportObject("x");
        FsscriptFunction x1 = (FsscriptFunction) ee.getExportObject("x1");
        FsscriptFunction x2 = (FsscriptFunction) ee.getExportObject("x2");
        FsscriptFunction x3 = (FsscriptFunction) ee.getExportObject("x3");
        FsscriptFunction x4 = (FsscriptFunction) ee.getExportObject("x4");

        Assert.assertEquals(1,x.autoApply(ee));
        Assert.assertEquals(2,x1.autoApply(ee));
        Assert.assertEquals(1,x2.autoApply(ee));

        Assert.assertEquals(11,((Map)x3.autoApply(ee)).get("b"));

        Assert.assertEquals("c",x4.autoApply(ee));

        Assert.assertEquals("b",ee.getExportObject("aa"));
        Assert.assertEquals("c",ee.getExportObject("cc"));
        Assert.assertEquals("d",ee.getExportObject("dd"));
    }



}
package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Map;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class AutoNcountExpTest {

    @Autowired
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

        Assertions.assertEquals(1,x.autoApply(ee));
        Assertions.assertEquals(2,x1.autoApply(ee));
        Assertions.assertEquals(1,x2.autoApply(ee));

        Assertions.assertEquals(11,((Map)x3.autoApply(ee)).get("b"));

        Assertions.assertEquals("c",x4.autoApply(ee));

        Assertions.assertEquals("b",ee.getExportObject("aa"));
        Assertions.assertEquals("c",ee.getExportObject("cc"));
        Assertions.assertEquals("d",ee.getExportObject("dd"));
    }



}
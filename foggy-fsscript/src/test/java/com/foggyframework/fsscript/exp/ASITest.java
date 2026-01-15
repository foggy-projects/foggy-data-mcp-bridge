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

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class ASITest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    void testASI() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
            .findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/asi_test.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assertions.assertEquals(Integer.valueOf(3), ee.getExportObject("testC"));

        Assertions.assertEquals(Integer.valueOf(6), ee.getExportObject("testD"));

        Assertions.assertEquals(Integer.valueOf(3), ee.getExportObject("testSum"));

        Assertions.assertEquals(12.0, ((Number) ee.getExportObject("testProduct")).doubleValue(), 0.001);

        Assertions.assertEquals("test", ee.getExportObject("testObjName"));

        Assertions.assertEquals(Integer.valueOf(1), ee.getExportObject("testFirst"));

        Assertions.assertEquals(Integer.valueOf(10), ee.getExportObject("testAfterIf"));

        Assertions.assertEquals(Integer.valueOf(3), ee.getExportObject("testAfterFor"));

        Assertions.assertEquals(Integer.valueOf(42), ee.getExportObject("testResult"));

        Assertions.assertEquals(Integer.valueOf(4), ee.getExportObject("testBreak"));
    }
}

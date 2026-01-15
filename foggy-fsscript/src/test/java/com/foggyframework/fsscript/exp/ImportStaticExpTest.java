package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import com.foggyframework.fsscript.support.ImportStaticClassTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class ImportStaticExpTest {
    @Autowired
    ApplicationContext appCtx;
    @Autowired
    FoggyFrameworkFsscriptTestApplication.PtTest importBeanTest;
    @Autowired
    FoggyFrameworkFsscriptTestApplication.PtTest importBeanTest3;

    @Test
    void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_static_class_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assertions.assertEquals("abc", ee.getCurrentFsscriptClosure().getVar("result"));
        Assertions.assertEquals("abc1", ee.getCurrentFsscriptClosure().getVar("result1"));

        Assertions.assertEquals("abc2", ee.getCurrentFsscriptClosure().getVar("result2"));

        ImportStaticClassTest aa = ee.getExportObject("aa");

        Assertions.assertEquals(aa.getAa(), "a4");

    }

}
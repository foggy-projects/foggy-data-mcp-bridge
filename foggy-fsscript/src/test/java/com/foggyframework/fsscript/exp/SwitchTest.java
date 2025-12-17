package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class SwitchTest {
    @Resource
    ApplicationContext appCtx;
    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/switch.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);


        ee.setVar("test",1);
        fScript.eval(ee);
        Assertions.assertEquals(11, (Integer) ee.getExportObject("result"));

        ee.setVar("test",2);
        fScript.eval(ee);
        Assertions.assertEquals(22, (Integer) ee.getExportObject("result"));

        ee.setVar("test","2");
        fScript.eval(ee);
        Assertions.assertEquals("22", (String) ee.getExportObject("result"));

        ee.setVar("test",4);
        fScript.eval(ee);
        Assertions.assertEquals(44, (Integer) ee.getExportObject("result"));

        ee.setVar("test",5);
        fScript.eval(ee);
        Assertions.assertEquals(44, (Integer) ee.getExportObject("result"));

        ee.setVar("test",6);
        fScript.eval(ee);
        Assertions.assertEquals(66, (Integer) ee.getExportObject("result"));


        ee.setVar("test",99);
        fScript.eval(ee);
        Assertions.assertEquals(999, (Integer) ee.getExportObject("result"));

    }

    @Test
    public void evalValue2() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/switch2.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);


//        ee.setVar("test",1);
//        fScript.eval(ee);
//        Assertions.assertEquals(11, (Integer) ee.getExportObject("result"));
//
//        ee.setVar("test",2);
//        fScript.eval(ee);
//        Assertions.assertEquals(22, (Integer) ee.getExportObject("result"));
//
//        ee.setVar("test","2");
//        fScript.eval(ee);
//        Assertions.assertEquals("22", (String) ee.getExportObject("result"));

        ee.setVar("test",4);
        fScript.eval(ee);
        Assertions.assertEquals(44, (Integer) ee.getExportObject("result"));

        ee.setVar("test",5);
        fScript.eval(ee);
        Assertions.assertEquals(44, (Integer) ee.getExportObject("result"));

        ee.setVar("test",6);
        fScript.eval(ee);
        Assertions.assertEquals(66, (Integer) ee.getExportObject("result"));


        ee.setVar("test",99);
        fScript.eval(ee);
        Assertions.assertEquals(999, (Integer) ee.getExportObject("result"));

    }
}
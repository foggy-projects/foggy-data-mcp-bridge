package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
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
public class IfExpTest {

    @Resource
    ApplicationContext appCtx;

    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/if_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assert.assertEquals(1,mm.get("b"));
        Assert.assertEquals(null,mm.get("d"));
        Assert.assertEquals(2,mm.get("c"));
        Assert.assertEquals(null,mm.get("i"));
        Assert.assertEquals(2,mm.get("ee"));
        Assert.assertEquals(null,mm.get("cc"));
        Assert.assertEquals(null,mm.get("dd"));
    }

    @Test
    public void ifTest1() {
        String expStr = "var b= 1;var c= 1;if(true){ b ='b'; var d = 12 ; export var cc = 2; }else{var c = 2 ; export var ee = 2;} export {b,c,d,i} ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);
        Map mm = ee.getExportMap();

        Assert.assertEquals("b",mm.get("b"));
        Assert.assertEquals(null,mm.get("d"));
        Assert.assertEquals(1,mm.get("c"));
        Assert.assertEquals(null,mm.get("i"));
        Assert.assertEquals(null,mm.get("ee"));
        Assert.assertEquals(2,mm.get("cc"));
    }


}
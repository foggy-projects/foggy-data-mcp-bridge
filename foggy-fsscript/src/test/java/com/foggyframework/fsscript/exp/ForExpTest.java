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
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class ForExpTest {

    @Resource
    ApplicationContext appCtx;

    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/for_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assert.assertEquals("b",mm.get("b"));
        Assert.assertEquals(null,mm.get("d"));
        Assert.assertEquals(1,mm.get("c"));
        Assert.assertEquals(null,mm.get("i"));
        Assert.assertEquals(2,mm.get("ee"));
    }

    @Test
    public void forTest1() {
        String expStr = "var b= 1;var c= 1;for(var i=0;i<10;i++){ b ='b'; var d = 12 ; var c = 2 ; export var ee = 2;} export {b,c,d,i} ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);
        Map mm = ee.getExportMap();

        Assert.assertEquals("b",mm.get("b"));
        Assert.assertEquals(null,mm.get("d"));
        Assert.assertEquals(1,mm.get("c"));
        Assert.assertEquals(null,mm.get("i"));
        Assert.assertEquals(2,mm.get("ee"));
    }

    @Test
    public void forTest2() {
        String expStr = "let result = [];let bb=[1,2,3];for(let b in bb){ result.add(b); } export result; ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);
        List mm = (List) ee.getExportMap().get("result");

        Assert.assertEquals(mm.get(0),1);
        Assert.assertEquals(mm.get(1),2);
        Assert.assertEquals(mm.get(2),3);

    }

    @Test
    public void forTest3() {
        String expStr = "let result = [];let bb=[1,2,3];for(let b : bb){ result.add(b); } export result; ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);
        List mm = (List) ee.getExportMap().get("result");

        Assert.assertEquals(mm.get(0),1);
        Assert.assertEquals(mm.get(1),2);
        Assert.assertEquals(mm.get(2),3);

    }

    @Test
    public void evalValue2() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/for_test2.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assert.assertEquals(3,mm.get("v"));

    }
    @Test
    public void evalValue3() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/for_test3.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assert.assertEquals(6,mm.get("v"));

    }
    @Test
    public void evalValue4() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/for_test4.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assert.assertEquals(2,mm.get("v"));

    }
    @Test
    public void vfor(){
        String expStr="(item, index) in state?.roleList";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);
    }
    @Test
    public void vfor2(){
        String expStr="(item, index) in query(133)";
        Exp exp = new ExpParser().compileEl(expStr);

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
//        exp.evalValue(ee);
    }

    @Test
    public void for_in_test1() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/for_in_test1.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        List<Integer> mm = (List<Integer>)ee.getExportMap().get("v");
        Assert.assertArrayEquals(new Integer[]{0,1},mm.toArray(new Integer[]{}));

    }
}
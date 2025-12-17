package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.function.Function;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class ArrayExpTest {
    @Resource
    ApplicationContext appCtx;
    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/array_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        List array = (List) ee.getExportMap().get("array");
        List array2 = (List) ee.getExportMap().get("array2");
        List array3 = (List) ee.getExportMap().get("array3");

        Assertions.assertArrayEquals(array.toArray(new Integer[0]),new Integer[]{1,2,3});
        Assertions.assertArrayEquals(array2.toArray(new Integer[0]),new Integer[]{null,1,2,3});
        Assertions.assertArrayEquals(array3.toArray(new Integer[0]),new Integer[]{null,1,null,2,3});
        System.err.println(array);
    }

    @Test
    public void evalValue2() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/array_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        Function array = (Function) ee.getExportMap().get("functionTest");


        System.err.println(array);
    }

    @Test
    public void evalValue3() {
        String expStr = "return '1,2'.split(',').length; ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);

        Assertions.assertEquals(2, v);
    }

    @Test
    public void evalValue4() {
        String expStr = "return [1,2].length; ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);

        Assertions.assertEquals(2, v);
    }

    @Test
    public void forEach() {

        String expStr = "let b=0; [1,2].forEach((e)=>{b=b+e}); return b;";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);

        Assertions.assertEquals(3, v);
    }

    @Test
    public void filter() {

        String expStr = "[1,2].filter((e)=>{return e==1;})[0];";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);

        Assertions.assertEquals(1, v);
    }
    @Test
    public void filter2() {

        String expStr = "let b = [1,2];b.filter((e)=>{return e==1;})[0];";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);

        Assertions.assertEquals(1, v);
    }

    @Test
    public void testxx() {
        String expStr = "aa.b==cc.d ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
//        Object v = exp.evalResult(ee);


    }


    @Test
    public void test_Array() {
        String expStr = "Array.isArray(); ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);
        Assertions.assertEquals(false, v);
    }

    @Test
    public void test_Array2() {
        String expStr = "Array.isArray([]); ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);
        Assertions.assertEquals(true, v);
    }
    @Test
    public void test_Array3() {
        String expStr = "Array.isArray([1]); ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);
        Assertions.assertEquals(true, v);
    }
    @Test
    public void test_Array4() {
        String expStr = "Array.isArray(1); ";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);
        Assertions.assertEquals(false, v);
    }
}
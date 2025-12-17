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
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class FunctionExpTest {

    @Resource
    ApplicationContext appCtx;

    @Test
    public void functionTest() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/function_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assert.assertEquals("b",mm.get("b"));
        Assert.assertEquals(2,mm.get("cc"));
        Assert.assertEquals(null,mm.get("d"));
        Assert.assertEquals(1,mm.get("c"));
        Assert.assertEquals(2,mm.get("ee"));

        Assert.assertEquals(2,mm.get("dd"));
        Assert.assertEquals("aa",mm.get("ff"));

        Function export1 = (Function) mm.get("export1");
        export1.apply(new Object[0]);

       Function export2 = (Function) mm.get("export2");
        Function export3 = (Function) mm.get("export3");


        Assert.assertEquals(3,export2.apply(new Object[0]));
        Assert.assertEquals(2,export3.apply(new Object[0]));

        Function export4 = (Function) mm.get("export4");
        Assert.assertEquals(4,export4.apply(new Object[0]));
    }


    @Test
    public void functionTest2() {
        String expStr = "function a(bb){bb=1;return 'a';}; export var c=a(bb); export bb;";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);
        Map mm = ee.getExportMap();

        Assert.assertEquals("a",mm.get("c"));
        Assert.assertEquals(null,mm.get("bb"));
    }

    @Test
    public void functionTest3() {
        try {
            Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/function_test_2.fsscript");

            ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
            fScript.eval(ee);

            Assertions.fail();
        }catch (Throwable t){

        }
    }
    @Test
    public void functionTest4() {
            Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/function_test_3.fsscript");

            ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
            fScript.eval(ee);

          List xx = (List) ee.getExportMap().get("result");
        Assertions.assertArrayEquals(xx.toArray(new Integer[0]),new Integer[]{1,2,null,3});
    }
    @Test
    public void retTest() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/fun/ret_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        Object xx =  ee.getExportMap().get("xx");
        Assertions.assertEquals(1,xx);
    }
}
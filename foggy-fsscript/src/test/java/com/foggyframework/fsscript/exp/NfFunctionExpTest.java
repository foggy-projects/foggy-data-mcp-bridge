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
public class NfFunctionExpTest {

    @Resource
    ApplicationContext appCtx;

    @Test
    public void nfFunctionTest() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/nf_function_test.fsscript");

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
    public void nfFunctionTest2() {
        String expStr = "var b = e=>{return 'b';};b();";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object result = exp.evalValue(ee);

        Assert.assertEquals("b",result);

//        Assert.assertEquals(null,mm.get("bb"));
    }


}
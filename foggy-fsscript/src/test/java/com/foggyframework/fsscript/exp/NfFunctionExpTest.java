package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class NfFunctionExpTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    public void nfFunctionTest() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/nf_function_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assertions.assertEquals("b",mm.get("b"));
        Assertions.assertEquals(2,mm.get("cc"));
        Assertions.assertEquals(null,mm.get("d"));
        Assertions.assertEquals(1,mm.get("c"));
        Assertions.assertEquals(2,mm.get("ee"));

        Assertions.assertEquals(2,mm.get("dd"));
        Assertions.assertEquals("aa",mm.get("ff"));

        Function export1 = (Function) mm.get("export1");
        export1.apply(new Object[0]);

       Function export2 = (Function) mm.get("export2");
        Function export3 = (Function) mm.get("export3");


        Assertions.assertEquals(3,export2.apply(new Object[0]));
        Assertions.assertEquals(2,export3.apply(new Object[0]));

        Function export4 = (Function) mm.get("export4");
        Assertions.assertEquals(4,export4.apply(new Object[0]));
    }


    @Test
    public void nfFunctionTest2() {
        String expStr = "var b = e=>{return 'b';};b();";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object result = exp.evalValue(ee);

        Assertions.assertEquals("b",result);

//        Assertions.assertEquals(null,mm.get("bb"));
    }


}
package com.foggyframework.fsscript.exp;

import com.foggyframework.core.utils.StringUtils;
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

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class ExpStringTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    public void test() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/exp_string_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map mm = ee.getExportMap();
        Assertions.assertEquals("1234", StringUtils.trim((String) mm.get("x")));
        Assertions.assertEquals("1234aaabdd",StringUtils.trim((String) mm.get("d")));
        Assertions.assertEquals("1231234123",StringUtils.trim((String) mm.get("bb")));
        Assertions.assertEquals("11",mm.get("cc"));
    }

    @Test
    public void testEq() {
        String expStr = "1===1 ";
        Exp exp = new ExpParser().compileEl(expStr);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Assertions.assertEquals(true,exp.evalResult(ee));
    }
    @Test
    public void testEq2() {
        String expStr = "1===2 ";
        Exp exp = new ExpParser().compileEl(expStr);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Assertions.assertEquals(false,exp.evalResult(ee));
    }

    @Test
    public void testEq3() {
        String expStr = "aa$abc";
        Exp exp = new ExpParser().compileEl(expStr);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Assertions.assertEquals(true,exp instanceof IdExp);
    }

}
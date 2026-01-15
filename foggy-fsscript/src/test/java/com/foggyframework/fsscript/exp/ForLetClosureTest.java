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

import java.util.List;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class ForLetClosureTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    void testForLetClosure() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
            .findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/for_let_closure.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Object aa = ee.getExportObject("aa");
        Object bb = ee.getExportObject("bb");

        System.out.println("aa = " + aa);
        System.out.println("bb = " + bb);

        // 根据 JavaScript 规范，let 在 for 循环中每次迭代创建新的块级作用域
        // aa 应该是 0（第一次迭代时 i=0）
        // bb 应该是 1（第二次迭代时 i=1）
        Assertions.assertEquals(0, ((Number) aa).intValue(), "JavaScript spec: aa should be 0 (captured i from iteration 1)");
        Assertions.assertEquals(1, ((Number) bb).intValue(), "JavaScript spec: bb should be 1 (captured i from iteration 2)");
    }

    @Test
    void testForLetClosure2() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/for_cl.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Object aa = ee.getExportObject("aa");
        Object bb = ee.getExportObject("bb");
        List cc = ee.getExportObject("cc");

        System.out.println("aa = " + aa);
        System.out.println("bb = " + bb);

        // 根据 JavaScript 规范，let 在 for 循环中每次迭代创建新的块级作用域
        // aa 应该是 0（第一次迭代时 i=0）
        // bb 应该是 1（第二次迭代时 i=1）
        Assertions.assertEquals(0, ((Number) aa).intValue(), "JavaScript spec: aa should be 0 (captured i from iteration 1)");
        Assertions.assertEquals(1, ((Number) bb).intValue(), "JavaScript spec: bb should be 1 (captured i from iteration 2)");
    }
}

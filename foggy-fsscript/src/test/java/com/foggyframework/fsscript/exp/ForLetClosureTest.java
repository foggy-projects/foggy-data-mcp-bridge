package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

import java.util.List;

/**
 * 测试 for 循环中 let 的块级作用域与闭包行为
 *
 * JavaScript 规范行为：
 * - let 在 for 循环中每次迭代创建新的块级作用域
 * - 每个闭包捕获的是各自迭代的 i 值
 * - 期望结果：aa = 0, bb = 1
 *
 * FSScript 当前行为：
 * - let 和 var 采用相同处理方案，没有块级作用域
 * - 所有闭包共享同一个 i 变量
 * - 实际结果：aa = 2, bb = 2 (循环结束后 i 的值)
 *
 * TODO: 如需符合 JavaScript 规范，需要实现 let 在 for 循环中的块级作用域
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class ForLetClosureTest {

    @Resource
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
        Assert.assertEquals("JavaScript spec: aa should be 0 (captured i from iteration 1)",
            0, ((Number) aa).intValue());
        Assert.assertEquals("JavaScript spec: bb should be 1 (captured i from iteration 2)",
            1, ((Number) bb).intValue());
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
        Assert.assertEquals("JavaScript spec: aa should be 0 (captured i from iteration 1)",
                0, ((Number) aa).intValue());
        Assert.assertEquals("JavaScript spec: bb should be 1 (captured i from iteration 2)",
                1, ((Number) bb).intValue());
    }
}

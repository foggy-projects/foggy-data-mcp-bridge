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

/**
 * ASI (Automatic Semicolon Insertion) 测试
 * 测试无分号情况下的自动分号插入功能
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class ASITest {

    @Resource
    ApplicationContext appCtx;

    @Test
    void testASI() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
            .findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/asi_test.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        // 测试1: 基本变量声明 c = 1 + 2 = 3
        Assert.assertEquals(Integer.valueOf(3), ee.getExportObject("testC"));

        // 测试2: 多行表达式 d = 1 + 2 + 3 = 6
        Assert.assertEquals(Integer.valueOf(6), ee.getExportObject("testD"));

        // 测试3: 函数调用 sum = add(1, 2) = 3
        Assert.assertEquals(Integer.valueOf(3), ee.getExportObject("testSum"));

        // 测试4: 箭头函数 product = 3 * 4 = 12
        Assert.assertEquals(12.0, ((Number) ee.getExportObject("testProduct")).doubleValue(), 0.001);

        // 测试5: 对象访问 objName = 'test'
        Assert.assertEquals("test", ee.getExportObject("testObjName"));

        // 测试6: 数组访问 first = arr[0] = 1
        Assert.assertEquals(Integer.valueOf(1), ee.getExportObject("testFirst"));

        // 测试7: if 语句后 afterIf = 10
        Assert.assertEquals(Integer.valueOf(10), ee.getExportObject("testAfterIf"));

        // 测试8: for 循环后 afterFor = 0 + 1 + 2 = 3
        Assert.assertEquals(Integer.valueOf(3), ee.getExportObject("testAfterFor"));

        // 测试9: return 语句 result = 42
        Assert.assertEquals(Integer.valueOf(42), ee.getExportObject("testResult"));

        // 测试10: break 语句 breakTest = 4 (循环在 i=5 时 break)
        Assert.assertEquals(Integer.valueOf(4), ee.getExportObject("testBreak"));
    }
}

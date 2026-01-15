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
public class FunctionExpTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    public void functionTest() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/function_test.fsscript");

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
    public void functionTest2() {
        String expStr = "function a(bb){bb=1;return 'a';}; export var c=a(bb); export bb;";
        Exp exp = new ExpParser().compileEl(expStr);

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);
        Map mm = ee.getExportMap();

        Assertions.assertEquals("a",mm.get("c"));
        Assertions.assertEquals(null,mm.get("bb"));
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

    @Test
    public void optionsTest() {
        // 测试1：函数定义带默认参数（数字），不传参数时使用默认值
        String expStr1 = "function test(count = 0) { return count }; export var result = test();";
        Exp exp1 = new ExpParser().compileEl(expStr1);
        ExpEvaluator ee1 = DefaultExpEvaluator.newInstance(appCtx);
        exp1.evalValue(ee1);
        Object result1 = ee1.getExportMap().get("result");
        Assertions.assertEquals(0, ((Number) result1).intValue(), "默认值应该是0");

        // 测试2：传递参数时使用传递的值
        String expStr2 = "function test2(count = 0) { return count }; export var result2 = test2(42);";
        Exp exp2 = new ExpParser().compileEl(expStr2);
        ExpEvaluator ee2 = DefaultExpEvaluator.newInstance(appCtx);
        exp2.evalValue(ee2);
        Object result2 = ee2.getExportMap().get("result2");
        Assertions.assertEquals(42, ((Number) result2).intValue(), "传递的参数应该是42");

        // 测试3：多个参数，部分带默认值
        String expStr3 = "function test3(a, b = 10, c = 'default') { return '' + a + b + c }; export var result3 = test3(1);";
        Exp exp3 = new ExpParser().compileEl(expStr3);
        ExpEvaluator ee3 = DefaultExpEvaluator.newInstance(appCtx);
        exp3.evalValue(ee3);
        Object result3 = ee3.getExportMap().get("result3");
        Assertions.assertEquals("110default", result3);

        // 测试4：export function 语法（非{}默认值）
        String expStr4 = "export function buildDateDim(count = 10) { return count }";
        Exp exp4 = new ExpParser().compileEl(expStr4);
        ExpEvaluator ee4 = DefaultExpEvaluator.newInstance(appCtx);
        exp4.evalValue(ee4);
        Object func = ee4.getExportMap().get("buildDateDim");
        Assertions.assertNotNull(func, "函数应该被导出");

        // 测试5：数组作为默认值
        String expStr5 = "function test5(arr = [1, 2, 3]) { return arr }; export var result5 = test5();";
        Exp exp5 = new ExpParser().compileEl(expStr5);
        ExpEvaluator ee5 = DefaultExpEvaluator.newInstance(appCtx);
        exp5.evalValue(ee5);
        Object result5 = ee5.getExportMap().get("result5");
        Assertions.assertNotNull(result5, "默认值应该是数组");
        Assertions.assertTrue(result5 instanceof java.util.List, "默认值应该是List类型");
    }

    @Test
    public void emptyObjectDefaultTest() {
        // 测试6：空对象{}作为默认值 - 这是Plan B实现的核心测试
        String expStr6 = "function foo(options = {}) { return options }; export var result6 = foo();";
        Exp exp6 = new ExpParser().compileEl(expStr6);
        ExpEvaluator ee6 = DefaultExpEvaluator.newInstance(appCtx);
        exp6.evalValue(ee6);
        Object result6 = ee6.getExportMap().get("result6");
        Assertions.assertNotNull(result6, "默认值应该是空对象");
        Assertions.assertTrue(result6 instanceof java.util.Map, "默认值应该是Map类型");
        Assertions.assertTrue(((java.util.Map<?,?>) result6).isEmpty(), "空对象应该是空的");

        // 测试7：export function 语法配合空对象默认值
        String expStr7 = "export function buildDateDim(options = {}) { return options }";
        Exp exp7 = new ExpParser().compileEl(expStr7);
        ExpEvaluator ee7 = DefaultExpEvaluator.newInstance(appCtx);
        exp7.evalValue(ee7);
        Object func7 = ee7.getExportMap().get("buildDateDim");
        Assertions.assertNotNull(func7, "函数应该被导出");

        // 测试8：非空对象作为默认值
        String expStr8 = "function bar(config = { key: 'value' }) { return config.key }; export var result8 = bar();";
        Exp exp8 = new ExpParser().compileEl(expStr8);
        ExpEvaluator ee8 = DefaultExpEvaluator.newInstance(appCtx);
        exp8.evalValue(ee8);
        Object result8 = ee8.getExportMap().get("result8");
        Assertions.assertEquals("value", result8, "应该返回默认对象的key值");

        // 测试9：传递参数时覆盖默认空对象
        String expStr9 = "function test9(opts = {}) { return opts.x }; export var result9 = test9({ x: 42 });";
        Exp exp9 = new ExpParser().compileEl(expStr9);
        ExpEvaluator ee9 = DefaultExpEvaluator.newInstance(appCtx);
        exp9.evalValue(ee9);
        Object result9 = ee9.getExportMap().get("result9");
        Assertions.assertEquals(42, result9, "传递的参数应该覆盖默认值");
    }

    @Test
    public void destructuringAssignmentTest() {
        // 测试1：简单解构赋值
        String expStr1 = "var options = { name: 'test', value: 42 }; const { name, value } = options; export name; export value;";
        Exp exp1 = new ExpParser().compileEl(expStr1);
        ExpEvaluator ee1 = DefaultExpEvaluator.newInstance(appCtx);
        exp1.evalValue(ee1);
        Assertions.assertEquals("test", ee1.getExportMap().get("name"));
        Assertions.assertEquals(42, ee1.getExportMap().get("value"));

        // 测试2：解构赋值带默认值
        String expStr2 = "var options = { name: 'hello' }; const { name, caption = 'default caption' } = options; export name; export caption;";
        Exp exp2 = new ExpParser().compileEl(expStr2);
        ExpEvaluator ee2 = DefaultExpEvaluator.newInstance(appCtx);
        exp2.evalValue(ee2);
        Assertions.assertEquals("hello", ee2.getExportMap().get("name"));
        Assertions.assertEquals("default caption", ee2.getExportMap().get("caption"));

        // 测试3：多个带默认值的解构
        String expStr3 = "var options = {}; const { name = 'date', foreignKey = 'date_key', caption = '日期' } = options; export name; export foreignKey; export caption;";
        Exp exp3 = new ExpParser().compileEl(expStr3);
        ExpEvaluator ee3 = DefaultExpEvaluator.newInstance(appCtx);
        exp3.evalValue(ee3);
        Assertions.assertEquals("date", ee3.getExportMap().get("name"));
        Assertions.assertEquals("date_key", ee3.getExportMap().get("foreignKey"));
        Assertions.assertEquals("日期", ee3.getExportMap().get("caption"));

        // 测试4：let 解构赋值
        String expStr4 = "var obj = { x: 1, y: 2 }; let { x, y } = obj; export x; export y;";
        Exp exp4 = new ExpParser().compileEl(expStr4);
        ExpEvaluator ee4 = DefaultExpEvaluator.newInstance(appCtx);
        exp4.evalValue(ee4);
        Assertions.assertEquals(1, ee4.getExportMap().get("x"));
        Assertions.assertEquals(2, ee4.getExportMap().get("y"));

        // 测试5：var 解构赋值
        String expStr5 = "var config = { host: 'localhost', port: 8080 }; var { host, port = 3000 } = config; export host; export port;";
        Exp exp5 = new ExpParser().compileEl(expStr5);
        ExpEvaluator ee5 = DefaultExpEvaluator.newInstance(appCtx);
        exp5.evalValue(ee5);
        Assertions.assertEquals("localhost", ee5.getExportMap().get("host"));
        Assertions.assertEquals(8080, ee5.getExportMap().get("port"));
    }

}
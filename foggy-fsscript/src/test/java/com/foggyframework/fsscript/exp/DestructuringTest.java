package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;

/**
 * 验证解构赋值功能
 */
public class DestructuringTest {
    public static void main(String[] args) {
        System.out.println("=== 解构赋值功能测试 ===\n");

        // 测试1：简单解构赋值
        test("测试1：简单解构赋值",
                "var options = { name: 'test', value: 42 }; const { name, value } = options; export name; export value;",
                new String[]{"name", "value"},
                new Object[]{"test", 42});

        // 测试2：解构赋值带默认值
        test("测试2：解构赋值带默认值",
                "var options = { name: 'hello' }; const { name, caption = 'default caption' } = options; export name; export caption;",
                new String[]{"name", "caption"},
                new Object[]{"hello", "default caption"});

        // 测试3：多个带默认值的解构
        test("测试3：多个带默认值的解构",
                "var options = {}; const { name = 'date', foreignKey = 'date_key', caption = '日期' } = options; export name; export foreignKey; export caption;",
                new String[]{"name", "foreignKey", "caption"},
                new Object[]{"date", "date_key", "日期"});

        // 测试4：let 解构赋值
        test("测试4：let 解构赋值",
                "var obj = { x: 1, y: 2 }; let { x, y } = obj; export x; export y;",
                new String[]{"x", "y"},
                new Object[]{1, 2});

        // 测试5：var 解构赋值
        test("测试5：var 解构赋值",
                "var config = { host: 'localhost', port: 8080 }; var { host, port = 3000 } = config; export host; export port;",
                new String[]{"host", "port"},
                new Object[]{"localhost", 8080});

        System.out.println("\n=== 测试完成 ===");
    }

    private static void test(String testName, String code, String[] exportNames, Object[] expectedValues) {
        System.out.println(testName);
        System.out.println("  代码: " + code);
        try {
            Exp exp = new ExpParser().compileEl(code);
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);

            boolean allPassed = true;
            for (int i = 0; i < exportNames.length; i++) {
                Object actual = ee.getExportMap().get(exportNames[i]);
                Object expected = expectedValues[i];
                boolean passed = (expected == null && actual == null) || (expected != null && expected.equals(actual));
                if (!passed) {
                    System.out.println("  ❌ " + exportNames[i] + ": 期望 " + expected + ", 实际 " + actual);
                    allPassed = false;
                } else {
                    System.out.println("  ✓ " + exportNames[i] + " = " + actual);
                }
            }
            if (allPassed) {
                System.out.println("  结果: PASS\n");
            } else {
                System.out.println("  结果: FAIL\n");
            }
        } catch (Exception e) {
            System.out.println("  ❌ 异常: " + e.getMessage());
            System.out.println("  结果: FAIL\n");
            e.printStackTrace();
        }
    }
}

package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 独立运行的 common-dims.fsscript 解析测试
 *
 * <p>不依赖 Spring 上下文，直接运行 main 方法即可测试。
 *
 * <p>测试的 ES6+ 语法特性：
 * <ul>
 *   <li>export function</li>
 *   <li>函数参数默认值 (options = {})</li>
 *   <li>解构赋值带默认值 (const { name = 'date', ... } = options)</li>
 *   <li>模板字符串 (`${prefix}年份`)</li>
 *   <li>箭头函数 (prop => allProperties[prop])</li>
 *   <li>方法链 (.map().filter())</li>
 * </ul>
 */
public class CommonDimsParseDebugTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("common-dims.fsscript 解析测试");
        System.out.println("========================================\n");

        // 测试单个语法特性
        testSyntaxFeatures();

        System.out.println("\n========================================");
        System.out.println("完整文件测试");
        System.out.println("========================================\n");

        // 测试完整文件
        testFullFile();

        System.out.println("\n========================================");
        System.out.println("测试结果汇总");
        System.out.println("========================================");
        System.out.println("通过: " + passed);
        System.out.println("失败: " + failed);
        System.out.println("========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * 测试单个语法特性
     */
    private static void testSyntaxFeatures() {
        System.out.println("--- 语法特性测试 ---\n");

        // 1. 函数参数默认值
        test("函数参数默认值",
                "function test(x = 1) { return x; } export test;");

        // 2. 对象参数默认值
        test("对象参数默认值",
                "function test(options = {}) { return options; } export test;");

        // 3. 解构赋值
        test("简单解构赋值",
                "const { a, b } = { a: 1, b: 2 }; export a; export b;");

        // 4. 解构赋值带默认值
        test("解构赋值带默认值",
                "const { a = 1, b = 2 } = {}; export a; export b;");

        // 5. 多属性解构带默认值
        test("多属性解构带默认值",
                "const { name = 'default', value = 100 } = {}; export name; export value;");

        // 6. 从参数解构
        test("从参数解构",
                "function test(options) { const { name = 'x' } = options; return name; } export test;");

        // 7. 模板字符串
        test("模板字符串",
                "const name = 'world'; const msg = `hello ${name}`; export msg;");

        // 8. 箭头函数
        test("箭头函数",
                "const fn = x => x * 2; export fn;");

        // 9. 箭头函数带对象返回
        test("箭头函数返回对象属性",
                "const obj = { a: 1, b: 2 }; const fn = key => obj[key]; export fn;");

        // 10. 数组 map
        test("数组 map",
                "const arr = [1, 2, 3]; const result = arr.map(x => x * 2); export result;");

        // 11. 数组 filter
        test("数组 filter",
                "const arr = [1, 2, 3, null, 4]; const result = arr.filter(x => x != null); export result;");

        // 12. filter(Boolean)
        test("filter(Boolean)",
                "const arr = [1, null, 2, undefined, 3]; const result = arr.filter(Boolean); export result;");

        // 13. map + filter 链式调用
        test("map + filter 链式调用",
                "const data = { a: 1, b: 2 }; const keys = ['a', 'c']; " +
                        "const result = keys.map(k => data[k]).filter(Boolean); export result;");

        // 14. 复杂解构（类似 common-dims 中的用法）
        test("复杂解构",
                "function build(options = {}) { " +
                        "const { name = 'default', caption = '默认' } = options; " +
                        "return { name: name, caption: caption }; " +
                        "} export build;");
    }

    /**
     * 测试完整文件
     */
    private static void testFullFile() {
        System.out.println("--- 完整文件测试 ---\n");

        String filePath = "classpath:/com/foggyframework/fsscript/exp/common-dims.fsscript";

        System.out.print("加载 common-dims.fsscript: ");
        Fsscript fScript;
        try {
            fScript = FileFsscriptLoader.getInstance().findLoadFsscript(filePath);
            if (fScript == null) {
                System.out.println("FAIL - 文件未找到");
                failed++;
                return;
            }
            System.out.println("PASS");
            passed++;
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            e.printStackTrace();
            failed++;
            return;
        }

        System.out.print("执行脚本: ");
        ExpEvaluator ee;
        try {
            ee = DefaultExpEvaluator.newInstance();
            fScript.eval(ee);
            System.out.println("PASS");
            passed++;
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            e.printStackTrace();
            failed++;
            return;
        }

        // 检查导出的函数
        Map<String, Object> exportMap = ee.getExportMap();
        String[] expectedFunctions = {
                "buildDateDim",
                "buildCustomerDim",
                "buildProductDim",
                "buildStoreDim",
                "buildChannelDim",
                "buildPromotionDim"
        };

        for (String funcName : expectedFunctions) {
            System.out.print("检查导出函数 " + funcName + ": ");
            if (exportMap.containsKey(funcName)) {
                Object func = exportMap.get(funcName);
                if (func instanceof Function) {
                    System.out.println("PASS");
                    passed++;
                } else {
                    System.out.println("FAIL - 不是函数类型: " + func.getClass());
                    failed++;
                }
            } else {
                System.out.println("FAIL - 未导出");
                failed++;
            }
        }

        // 测试调用 buildDateDim
        System.out.print("调用 buildDateDim(): ");
        try {
            Function buildDateDim = (Function) exportMap.get("buildDateDim");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) buildDateDim.apply(new Object[]{});

            if (result == null) {
                System.out.println("FAIL - 返回 null");
                failed++;
            } else {
                System.out.println("PASS");
                System.out.println("  返回值: " + result);
                passed++;

                // 验证关键字段
                System.out.print("  验证 name='date': ");
                if ("date".equals(result.get("name"))) {
                    System.out.println("PASS");
                    passed++;
                } else {
                    System.out.println("FAIL - " + result.get("name"));
                    failed++;
                }

                System.out.print("  验证 tableName='dim_date': ");
                if ("dim_date".equals(result.get("tableName"))) {
                    System.out.println("PASS");
                    passed++;
                } else {
                    System.out.println("FAIL - " + result.get("tableName"));
                    failed++;
                }

                System.out.print("  验证 properties 不为空: ");
                Object props = result.get("properties");
                if (props instanceof List && !((List<?>) props).isEmpty()) {
                    System.out.println("PASS - " + ((List<?>) props).size() + " 个属性");
                    passed++;
                } else {
                    System.out.println("FAIL - " + props);
                    failed++;
                }
            }
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            e.printStackTrace();
            failed++;
        }
    }

    /**
     * 测试单个代码片段
     */
    private static void test(String name, String code) {
        System.out.print(name + ": ");
        try {
            Exp exp = new ExpParser().compileEl(code);
            ExpEvaluator ee = DefaultExpEvaluator.newInstance();
            exp.evalValue(ee);
            System.out.println("PASS");
            passed++;
        } catch (Exception e) {
            System.out.println("FAIL - " + e.getMessage());
            failed++;
        }
    }
}

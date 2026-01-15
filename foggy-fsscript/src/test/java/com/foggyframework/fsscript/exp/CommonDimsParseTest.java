package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 common-dims.fsscript 文件解析
 *
 * <p>该文件使用了多种 ES6+ 语法特性：
 * <ul>
 *   <li>export function</li>
 *   <li>函数参数默认值 (options = {})</li>
 *   <li>解构赋值带默认值 (const { name = 'date', ... } = options)</li>
 *   <li>模板字符串 (`${prefix}年份`)</li>
 *   <li>箭头函数 (prop => allProperties[prop])</li>
 *   <li>方法链 (.map().filter())</li>
 * </ul>
 */
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class CommonDimsParseTest {

    @Autowired
    ApplicationContext appCtx;

    private static final String COMMON_DIMS_PATH = "classpath:/com/foggyframework/fsscript/exp/common-dims.fsscript";

    @Test
    public void testParseCommonDims() {
        // 尝试加载并解析文件
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(COMMON_DIMS_PATH);
        assertNotNull(fScript, "common-dims.fsscript 应该能够被解析");

        log.info("common-dims.fsscript 解析成功");
    }

    @Test
    public void testEvalCommonDims() {
        // 加载文件
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(COMMON_DIMS_PATH);
        assertNotNull(fScript, "common-dims.fsscript 应该能够被解析");

        // 执行脚本
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        // 检查导出的函数
        Map<String, Object> exportMap = ee.getExportMap();
        assertNotNull(exportMap, "exportMap 不应为空");

        log.info("导出内容: {}", exportMap.keySet());

        // 验证 buildDateDim 函数被导出
        assertTrue(exportMap.containsKey("buildDateDim"), "应该导出 buildDateDim 函数");
        assertTrue(exportMap.containsKey("buildCustomerDim"), "应该导出 buildCustomerDim 函数");
        assertTrue(exportMap.containsKey("buildProductDim"), "应该导出 buildProductDim 函数");
        assertTrue(exportMap.containsKey("buildStoreDim"), "应该导出 buildStoreDim 函数");
        assertTrue(exportMap.containsKey("buildChannelDim"), "应该导出 buildChannelDim 函数");
        assertTrue(exportMap.containsKey("buildPromotionDim"), "应该导出 buildPromotionDim 函数");

        log.info("所有函数导出验证通过");
    }

    @Test
    public void testCallBuildDateDimWithDefaults() {
        // 加载文件
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(COMMON_DIMS_PATH);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        Map<String, Object> exportMap = ee.getExportMap();
        Function buildDateDim = (Function) exportMap.get("buildDateDim");
        assertNotNull(buildDateDim, "buildDateDim 应该是函数");

        // 调用函数，不传参数（使用默认值）
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) buildDateDim.apply(new Object[]{});

        assertNotNull(result, "结果不应为空");
        assertEquals("date", result.get("name"), "默认 name 应为 'date'");
        assertEquals("dim_date", result.get("tableName"), "tableName 应为 'dim_date'");
        assertEquals("date_key", result.get("foreignKey"), "默认 foreignKey 应为 'date_key'");
        assertEquals("日期", result.get("caption"), "默认 caption 应为 '日期'");

        log.info("buildDateDim 默认参数调用成功: {}", result);
    }

    @Test
    public void testCallBuildDateDimWithOptions() {
        // 加载文件
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(COMMON_DIMS_PATH);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        Map<String, Object> exportMap = ee.getExportMap();
        Function buildDateDim = (Function) exportMap.get("buildDateDim");

        // 调用函数，传入自定义参数
        Map<String, Object> options = Map.of(
                "name", "salesDate",
                "foreignKey", "sale_date_key",
                "caption", "销售日期",
                "contextPrefix", "销售"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) buildDateDim.apply(new Object[]{options});

        assertNotNull(result, "结果不应为空");
        assertEquals("salesDate", result.get("name"), "name 应为 'salesDate'");
        assertEquals("sale_date_key", result.get("foreignKey"), "foreignKey 应为 'sale_date_key'");
        assertEquals("销售日期", result.get("caption"), "caption 应为 '销售日期'");

        log.info("buildDateDim 自定义参数调用成功: {}", result);
    }

    @Test
    public void testCallBuildProductDim() {
        // 加载文件
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(COMMON_DIMS_PATH);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        Map<String, Object> exportMap = ee.getExportMap();
        Function buildProductDim = (Function) exportMap.get("buildProductDim");
        assertNotNull(buildProductDim, "buildProductDim 应该是函数");

        // 调用函数
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) buildProductDim.apply(new Object[]{});

        assertNotNull(result, "结果不应为空");
        assertEquals("product", result.get("name"), "name 应为 'product'");
        assertEquals("dim_product", result.get("tableName"), "tableName 应为 'dim_product'");

        // 验证 properties 数组
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> properties = (java.util.List<Map<String, Object>>) result.get("properties");
        assertNotNull(properties, "properties 不应为空");
        assertFalse(properties.isEmpty(), "properties 不应为空列表");

        log.info("buildProductDim 调用成功，包含 {} 个属性", properties.size());
    }

    @Test
    public void test1() {
        // 测试1：简单解构赋值
        String expStr1 = "const allProperties = {    year: { column: 'year', caption: '年', description: '123' } };";
        Exp exp1 = new ExpParser().compileEl(expStr1);
    }
    @Test
    public void test2() {
        // 测试：顶层代码（不在函数内）
        String expStr1 = "const {\n" +
                "        name = 'date',\n" +
                "        foreignKey = 'date_key',\n" +
                "        caption = '日期',\n" +
                "        description = '日期信息',\n" +
                "        contextPrefix = '',\n" +
                "        includeProperties = ['year', 'quarter', 'month', 'month_name', 'day_of_week', 'is_weekend']\n" +
                "    } = options;" +
                "const prefix = contextPrefix ? `${contextPrefix}的` : '';" +
                "       const allProperties = {\n" +
                "        year: { column: 'year', caption: '年', description: `${prefix}年份` },\n" +
                "        quarter: { column: 'quarter', caption: '季度', description: `${prefix}季度（1-4）` },\n" +
                "        month: { column: 'month', caption: '月', description: `${prefix}月份（1-12）` },\n" +
                "        month_name: { column: 'month_name', caption: '月份名称', description: `${prefix}月份中文名（一月至十二月）` },\n" +
                "        week_of_year: { column: 'week_of_year', caption: '年度周数', description: `${prefix}是一年中的第几周（1-53）` },\n" +
                "        day_of_week: { column: 'day_of_week', caption: '周几', description: `${prefix}在周几（1=周一）` },\n" +
                "        day_name: { column: 'day_name', caption: '星期名称', description: `${prefix}星期中文名（周一至周日）` },\n" +
                "        is_weekend: { column: 'is_weekend', caption: '是否周末', description: `${prefix}是否在周末` },\n" +
                "        is_holiday: { column: 'is_holiday', caption: '是否节假日', description: `${prefix}是否在节假日` }\n" +
                "    };";
        Exp exp1 = new ExpParser().compileEl(expStr1);
    }

    @Test
    public void test3_codeInFunction() {
        // 测试：将相同代码放在函数内部
        String expStr = "function buildDateDim(options = {}) {\n" +
                "    const {\n" +
                "        name = 'date',\n" +
                "        foreignKey = 'date_key',\n" +
                "        caption = '日期',\n" +
                "        description = '日期信息',\n" +
                "        contextPrefix = '',\n" +
                "        includeProperties = ['year', 'quarter', 'month', 'month_name', 'day_of_week', 'is_weekend']\n" +
                "    } = options;\n" +
                "\n" +
                "    const prefix = contextPrefix ? `${contextPrefix}的` : '';\n" +
                "\n" +
                "    const allProperties = {\n" +
                "        year: { column: 'year', caption: '年', description: `${prefix}年份` },\n" +
                "        quarter: { column: 'quarter', caption: '季度', description: `${prefix}季度（1-4）` }\n" +
                "    };\n" +
                "    return allProperties;\n" +
                "}";
        Exp exp = new ExpParser().compileEl(expStr);
        assertNotNull(exp);
        log.info("函数内部代码解析成功: {}", exp);
    }

    @Test
    public void test4_exportFunction() {
        // 测试：export function 语法
        String expStr = "export function buildDateDim(options = {}) {\n" +
                "    const {\n" +
                "        name = 'date',\n" +
                "        foreignKey = 'date_key'\n" +
                "    } = options;\n" +
                "\n" +
                "    const prefix = contextPrefix ? `${contextPrefix}的` : '';\n" +
                "\n" +
                "    const allProperties = {\n" +
                "        year: { column: 'year', caption: '年', description: `${prefix}年份` }\n" +
                "    };\n" +
                "    return allProperties;\n" +
                "}";
        Exp exp = new ExpParser().compileEl(expStr);
        assertNotNull(exp);
        log.info("export function 解析成功: {}", exp);
    }
}

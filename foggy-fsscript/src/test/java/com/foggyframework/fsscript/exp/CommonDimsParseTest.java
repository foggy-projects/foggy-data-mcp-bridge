package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;
import java.util.function.Function;

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
@RunWith(SpringRunner.class)
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
        Assert.assertNotNull("common-dims.fsscript 应该能够被解析", fScript);

        log.info("common-dims.fsscript 解析成功");
    }

    @Test
    public void testEvalCommonDims() {
        // 加载文件
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(COMMON_DIMS_PATH);
        Assert.assertNotNull("common-dims.fsscript 应该能够被解析", fScript);

        // 执行脚本
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        // 检查导出的函数
        Map<String, Object> exportMap = ee.getExportMap();
        Assert.assertNotNull("exportMap 不应为空", exportMap);

        log.info("导出内容: {}", exportMap.keySet());

        // 验证 buildDateDim 函数被导出
        Assert.assertTrue("应该导出 buildDateDim 函数", exportMap.containsKey("buildDateDim"));
        Assert.assertTrue("应该导出 buildCustomerDim 函数", exportMap.containsKey("buildCustomerDim"));
        Assert.assertTrue("应该导出 buildProductDim 函数", exportMap.containsKey("buildProductDim"));
        Assert.assertTrue("应该导出 buildStoreDim 函数", exportMap.containsKey("buildStoreDim"));
        Assert.assertTrue("应该导出 buildChannelDim 函数", exportMap.containsKey("buildChannelDim"));
        Assert.assertTrue("应该导出 buildPromotionDim 函数", exportMap.containsKey("buildPromotionDim"));

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
        Assert.assertNotNull("buildDateDim 应该是函数", buildDateDim);

        // 调用函数，不传参数（使用默认值）
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) buildDateDim.apply(new Object[]{});

        Assert.assertNotNull("结果不应为空", result);
        Assert.assertEquals("默认 name 应为 'date'", "date", result.get("name"));
        Assert.assertEquals("tableName 应为 'dim_date'", "dim_date", result.get("tableName"));
        Assert.assertEquals("默认 foreignKey 应为 'date_key'", "date_key", result.get("foreignKey"));
        Assert.assertEquals("默认 caption 应为 '日期'", "日期", result.get("caption"));

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

        Assert.assertNotNull("结果不应为空", result);
        Assert.assertEquals("name 应为 'salesDate'", "salesDate", result.get("name"));
        Assert.assertEquals("foreignKey 应为 'sale_date_key'", "sale_date_key", result.get("foreignKey"));
        Assert.assertEquals("caption 应为 '销售日期'", "销售日期", result.get("caption"));

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
        Assert.assertNotNull("buildProductDim 应该是函数", buildProductDim);

        // 调用函数
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) buildProductDim.apply(new Object[]{});

        Assert.assertNotNull("结果不应为空", result);
        Assert.assertEquals("name 应为 'product'", "product", result.get("name"));
        Assert.assertEquals("tableName 应为 'dim_product'", "dim_product", result.get("tableName"));

        // 验证 properties 数组
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> properties = (java.util.List<Map<String, Object>>) result.get("properties");
        Assert.assertNotNull("properties 不应为空", properties);
        Assert.assertFalse("properties 不应为空列表", properties.isEmpty());

        log.info("buildProductDim 调用成功，包含 {} 个属性", properties.size());
    }
}

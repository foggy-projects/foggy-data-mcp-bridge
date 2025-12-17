package com.foggyframework.fsscript.builtin;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * JSON 全局对象测试
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class JsonGlobalTest {

    @Resource
    ApplicationContext appCtx;

    @Test
    public void testJsonStringify() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript("classpath:/com/foggyframework/fsscript/builtin/json_stringify_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        // 验证基本序列化
        String simpleResult = (String) ee.getExportMap().get("simpleResult");
        Assert.assertNotNull(simpleResult);
        Assert.assertTrue(simpleResult.contains("\"name\""));
        Assert.assertTrue(simpleResult.contains("张三"));
        log.info("simpleResult: {}", simpleResult);

        // 验证格式化输出
        String prettyResult = (String) ee.getExportMap().get("prettyResult");
        Assert.assertNotNull(prettyResult);
        Assert.assertTrue(prettyResult.contains("\n")); // 格式化输出应包含换行
        log.info("prettyResult: {}", prettyResult);

        // 验证 null 处理
        String nullResult = (String) ee.getExportMap().get("nullResult");
        Assert.assertEquals("null", nullResult);
        log.info("nullResult: {}", nullResult);

        // 验证数组序列化
        String arrayResult = (String) ee.getExportMap().get("arrayResult");
        Assert.assertNotNull(arrayResult);
        Assert.assertTrue(arrayResult.startsWith("["));
        log.info("arrayResult: {}", arrayResult);
    }

    @Test
    public void testJsonParse() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript("classpath:/com/foggyframework/fsscript/builtin/json_parse_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        // 验证对象解析
        Map<String, Object> parsedObj = (Map<String, Object>) ee.getExportMap().get("parsedObj");
        Assert.assertNotNull(parsedObj);
        Assert.assertEquals("test", parsedObj.get("name"));
        Assert.assertEquals(100, parsedObj.get("value"));
        log.info("parsedObj: {}", parsedObj);

        // 验证数组解析
        List<Object> parsedArray = (List<Object>) ee.getExportMap().get("parsedArray");
        Assert.assertNotNull(parsedArray);
        Assert.assertEquals(3, parsedArray.size());
        log.info("parsedArray: {}", parsedArray);

        // 验证嵌套对象解析
        Map<String, Object> parsedNested = (Map<String, Object>) ee.getExportMap().get("parsedNested");
        Assert.assertNotNull(parsedNested);
        Map<String, Object> inner = (Map<String, Object>) parsedNested.get("inner");
        Assert.assertEquals("nested", inner.get("key"));
        log.info("parsedNested: {}", parsedNested);

        // 验证 null 处理
        Object parsedNull = ee.getExportMap().get("parsedNull");
        Assert.assertNull(parsedNull);
    }

    @Test
    public void testJsonRoundTrip() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript("classpath:/com/foggyframework/fsscript/builtin/json_roundtrip_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        // 验证往返转换
        Boolean roundTripSuccess = (Boolean) ee.getExportMap().get("roundTripSuccess");
        Assert.assertTrue(roundTripSuccess);

        String originalName = (String) ee.getExportMap().get("originalName");
        String parsedName = (String) ee.getExportMap().get("parsedName");
        Assert.assertEquals(originalName, parsedName);
        log.info("Round trip test passed: {} == {}", originalName, parsedName);
    }
}

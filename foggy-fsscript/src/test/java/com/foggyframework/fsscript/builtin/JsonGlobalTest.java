package com.foggyframework.fsscript.builtin;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class JsonGlobalTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    public void testJsonStringify() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript("classpath:/com/foggyframework/fsscript/builtin/json_stringify_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        // 验证基本序列化
        String simpleResult = (String) ee.getExportMap().get("simpleResult");
        Assertions.assertNotNull(simpleResult);
        Assertions.assertTrue(simpleResult.contains("\"name\""));
        Assertions.assertTrue(simpleResult.contains("张三"));
        log.info("simpleResult: {}", simpleResult);

        String prettyResult = (String) ee.getExportMap().get("prettyResult");
        Assertions.assertNotNull(prettyResult);
        Assertions.assertTrue(prettyResult.contains("\n"));
        log.info("prettyResult: {}", prettyResult);

        String nullResult = (String) ee.getExportMap().get("nullResult");
        Assertions.assertEquals("null", nullResult);
        log.info("nullResult: {}", nullResult);

        String arrayResult = (String) ee.getExportMap().get("arrayResult");
        Assertions.assertNotNull(arrayResult);
        Assertions.assertTrue(arrayResult.startsWith("["));
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
        Assertions.assertNotNull(parsedObj);
        Assertions.assertEquals("test", parsedObj.get("name"));
        Assertions.assertEquals(100, parsedObj.get("value"));
        log.info("parsedObj: {}", parsedObj);

        List<Object> parsedArray = (List<Object>) ee.getExportMap().get("parsedArray");
        Assertions.assertNotNull(parsedArray);
        Assertions.assertEquals(3, parsedArray.size());
        log.info("parsedArray: {}", parsedArray);

        Map<String, Object> parsedNested = (Map<String, Object>) ee.getExportMap().get("parsedNested");
        Assertions.assertNotNull(parsedNested);
        Map<String, Object> inner = (Map<String, Object>) parsedNested.get("inner");
        Assertions.assertEquals("nested", inner.get("key"));
        log.info("parsedNested: {}", parsedNested);

        Object parsedNull = ee.getExportMap().get("parsedNull");
        Assertions.assertNull(parsedNull);
    }

    @Test
    public void testJsonRoundTrip() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript("classpath:/com/foggyframework/fsscript/builtin/json_roundtrip_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        // 验证往返转换
        Boolean roundTripSuccess = (Boolean) ee.getExportMap().get("roundTripSuccess");
        Assertions.assertTrue(roundTripSuccess);

        String originalName = (String) ee.getExportMap().get("originalName");
        String parsedName = (String) ee.getExportMap().get("parsedName");
        Assertions.assertEquals(originalName, parsedName);
        log.info("Round trip test passed: {} == {}", originalName, parsedName);
    }
}

package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Map;
import java.util.function.Function;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class ExportExpTest {
    @Autowired
    ApplicationContext appCtx;
    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object obj = fScript.eval(ee);

        log.debug(obj+"");

        //检查export中的内容，是否是我们期望的
        Map<String,Object> exportMap = (Map<String, Object>) ee.getCurrentFsscriptClosure().getVar(FsscriptClosure.EXPORT_MAP_KEY);

        Assertions.assertEquals(1,exportMap.get("d"));
        Assertions.assertEquals(2,exportMap.get("b"));

        Function xxx = (Function) exportMap.get("xxx");
        Object xxxReturn = xxx.apply(new Object[0]);
        Assertions.assertEquals(xxxReturn,3);

        Map<String, Object> defaultExport = (Map<String, Object>) exportMap.get("default");
        Assertions.assertNotNull(defaultExport);
        Assertions.assertEquals(123, defaultExport.get("XX2"));
        Assertions.assertEquals(1111, defaultExport.get("BB2"));

        Assertions.assertEquals(2,exportMap.get("cc"));
    }
    @Test
    public void export_test3() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test3.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);
        Map<String, Object> exportMap = ee.getExportMap();

        Assertions.assertEquals(1231,exportMap.get("XX"));
        Assertions.assertEquals(22,exportMap.get("BB"));
    }

    @Test
    public void export_test4() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test4.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);
        Map<String, Object> exportMap = ee.getExportMap();

        Function ff = (Function) exportMap.get("exportFunction");
        ff.apply(new Object[]{"aa","bb"});

        Assertions.assertEquals("aa",exportMap.get("a"));
        Assertions.assertEquals("bb",exportMap.get("b"));

    }
}
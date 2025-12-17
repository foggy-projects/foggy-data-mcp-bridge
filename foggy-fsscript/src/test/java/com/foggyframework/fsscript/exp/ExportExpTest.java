package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.function.Function;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class ExportExpTest {
    @Resource
    ApplicationContext appCtx;
    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object obj = fScript.eval(ee);

        log.debug(obj+"");

        //检查export中的内容，是否是我们期望的
        Map<String,Object> exportMap = (Map<String, Object>) ee.getCurrentFsscriptClosure().getVar(FsscriptClosure.EXPORT_MAP_KEY);

        Assert.assertEquals(1,exportMap.get("d"));
        Assert.assertEquals(2,exportMap.get("b"));

        Function xxx = (Function) exportMap.get("xxx");
        Object xxxReturn = xxx.apply(new Object[0]);
        Assert.assertEquals(xxxReturn,3);

        // export default 现在放在 "default" key 下（符合 ES6 模块规范）
        // export_test.fsscript 有两个 export default，第二个会覆盖第一个
        Map<String, Object> defaultExport = (Map<String, Object>) exportMap.get("default");
        Assert.assertNotNull(defaultExport);
        Assert.assertEquals(123, defaultExport.get("XX2"));
        Assert.assertEquals(1111, defaultExport.get("BB2"));

        Assert.assertEquals(2,exportMap.get("cc"));
    }
    @Test
    public void export_test3() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test3.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);
        Map<String, Object> exportMap = ee.getExportMap();

        Assert.assertEquals(1231,exportMap.get("XX"));
        Assert.assertEquals(22,exportMap.get("BB"));
    }

    @Test
    public void export_test4() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test4.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);
        Map<String, Object> exportMap = ee.getExportMap();

        Function ff = (Function) exportMap.get("exportFunction");
        ff.apply(new Object[]{"aa","bb"});

        Assert.assertEquals("aa",exportMap.get("a"));
        Assert.assertEquals("bb",exportMap.get("b"));

    }
}
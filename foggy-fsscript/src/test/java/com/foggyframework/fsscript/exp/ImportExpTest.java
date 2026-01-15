package com.foggyframework.fsscript.exp;

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

import java.util.Map;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class ImportExpTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_as_test.fsscript");

//        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Assertions.assertEquals(2,ee.getCurrentFsscriptClosure().getVar("bb"));

//      Map mm = (Map) ee.getCurrentFsscriptClosure().getVar("T");
//
//        Assert.assertEquals(2,mm.get("b"));
//        Assert.assertEquals(1111,mm.get("BB"));
//        Assert.assertEquals(123,mm.get("XX"));
    }

    /**
     * 测试 import * as 命名空间导入语法
     * 对应 README.zh-CN.md 中提到的 import * as utils from 'utils.fsscript';
     */
    @Test
    public void testImportNamespace() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_namespace_test.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        // 验证通过命名空间访问导出的变量
        Assertions.assertEquals(1, ee.getCurrentFsscriptClosure().getVar("testD"));
        Assertions.assertEquals(2, ee.getCurrentFsscriptClosure().getVar("testB"));
        Assertions.assertEquals(2, ee.getCurrentFsscriptClosure().getVar("testCC"));

        // 验证通过命名空间调用导出的函数
        Assertions.assertEquals(3, ee.getCurrentFsscriptClosure().getVar("testXxx"));

        // 验证通过命名空间访问默认导出
        Map defaultExport = (Map) ee.getCurrentFsscriptClosure().getVar("testDefault");
        Assertions.assertNotNull(defaultExport);
        Assertions.assertEquals(123, defaultExport.get("XX2"));
        Assertions.assertEquals(1111, defaultExport.get("BB2"));
    }



}
package com.foggyframework.fsscript.loadder;

import com.foggyframework.core.common.MapBuilder;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.List;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class FileTxtFsscriptLoaderTest {

    @Resource
    ApplicationContext applicationContext;

    @Resource
    FileTxtFsscriptLoader fileTxtFsscriptLoader;

    @Test
    public void test() {

        Fsscript fScript = fileTxtFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/fsscript/exp//txt/export_test.xmltpl");

        String result = (String) fScript.evalResult(applicationContext, MapBuilder.builder().put("a", 1).build());
        Assertions.assertEquals(result.trim(), "<xml><a>1</a></xml>");
    }
    @Test
    public void test2() {

        Fsscript fScript = fileTxtFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/fsscript/exp//txt/export_test2.xmltpl");

        String result = (String) fScript.evalResult(applicationContext, MapBuilder.builder().put("a", 1).build());
        Assertions.assertEquals(result.trim(), "<xml><a>1</a></xml><xml><a>2</a></xml>");
    }

}
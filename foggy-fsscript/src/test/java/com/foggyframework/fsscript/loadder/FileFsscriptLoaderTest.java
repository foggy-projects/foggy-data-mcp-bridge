package com.foggyframework.fsscript.loadder;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class FileFsscriptLoaderTest {
    /**
     * 测试重复加载
     */
    @Test
    public void evalValue2() {

        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_test.fsscript");
        Fsscript fScript2 = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_test.fsscript");

        Assert.assertEquals(fScript, fScript2);
    }
}
package com.foggyframework.fsscript.loadder;

import com.foggyframework.core.utils.FileUtils;
import com.foggyframework.core.utils.UuidUtils;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class FsscriptFileChangeHandlerTest {
    @Autowired
    ApplicationContext appCtx;
    @Test
    public void testChange() throws IOException, InterruptedException {
        String resPath = "classpath:/com/foggyframework/fsscript/exp/test_change.fsscript";
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(resPath);
        Assertions.assertNotNull(fScript);

        Resource res =appCtx.getResource(resPath);

        Fsscript fScript2 = FileFsscriptLoader.getInstance().findLoadFsscript(resPath);
        Assertions.assertEquals(fScript2,fScript);

        FileUtils.save(res.getFile(),"var test=2;//"+ UuidUtils.newUuid());

        Thread.sleep(3000);

         fScript2 = FileFsscriptLoader.getInstance().findLoadFsscript(resPath);
        Assertions.assertNotEquals(fScript2,fScript);

        ExpEvaluator ee = fScript2.newInstance(appCtx);
        fScript2.eval(ee);
        Assertions.assertEquals(ee.getVar("test"),2);
    }
}
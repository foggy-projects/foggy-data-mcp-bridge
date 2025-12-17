package com.foggyframework.fsscript.loadder;

import com.foggyframework.core.utils.FileUtils;
import com.foggyframework.core.utils.UuidUtils;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class FsscriptFileChangeHandlerTest {
    @Autowired
    ApplicationContext appCtx;
    @Test
    public void testChange() throws IOException, InterruptedException {
        String resPath = "classpath:/com/foggyframework/fsscript/exp/test_change.fsscript";
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(resPath);
        Assert.assertNotNull(fScript);

        Resource res =appCtx.getResource(resPath);

        Fsscript fScript2 = FileFsscriptLoader.getInstance().findLoadFsscript(resPath);
        Assert.assertEquals(fScript2,fScript);

        FileUtils.save(res.getFile(),"var test=2;//"+ UuidUtils.newUuid());

        Thread.sleep(3000);

         fScript2 = FileFsscriptLoader.getInstance().findLoadFsscript(resPath);
        Assert.assertNotEquals(fScript2,fScript);

        ExpEvaluator ee = fScript2.newInstance(appCtx);
        fScript2.eval(ee);
        Assert.assertEquals(ee.getVar("test"),2);
    }
}
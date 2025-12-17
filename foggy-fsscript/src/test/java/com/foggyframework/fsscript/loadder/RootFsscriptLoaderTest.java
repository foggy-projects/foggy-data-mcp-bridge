package com.foggyframework.fsscript.loadder;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class RootFsscriptLoaderTest {

    @Resource
    RootFsscriptLoader rootFsscriptLoader;

    @Resource
    ApplicationContext appCtx;

    @Test
    public void getWhoImportMe() {
        rootFsscriptLoader.clear();

        Fsscript import_test2 = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_test2.fsscript");
        Fsscript export_test2 = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test2.fsscript");
        Fsscript export_test = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test.fsscript");

        Assert.assertTrue(rootFsscriptLoader.getWhoImportMe(export_test.getPath()).isEmpty());

        //现在执行下表达式
        import_test2.eval(import_test2.newInstance(appCtx));

        List<Fsscript> export_test_import_list =  rootFsscriptLoader.getWhoImportMe(export_test.getPath());
        Assert.assertEquals(export_test_import_list.size(),2);
//        Assert.assertEquals(export_test_import_list.get(0),import_test2);
//        Assert.assertEquals(export_test_import_list.get(1),export_test2);

        Assert.assertTrue(export_test_import_list.contains(export_test2));
        Assert.assertTrue(export_test_import_list.contains(import_test2));
    }

}
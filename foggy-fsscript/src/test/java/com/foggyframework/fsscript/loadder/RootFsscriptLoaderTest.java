package com.foggyframework.fsscript.loadder;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.List;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class RootFsscriptLoaderTest {

    @Autowired
    RootFsscriptLoader rootFsscriptLoader;

    @Autowired
    ApplicationContext appCtx;

    @Test
    public void getWhoImportMe() {
        rootFsscriptLoader.clear();

        Fsscript import_test2 = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_test2.fsscript");
        Fsscript export_test2 = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test2.fsscript");
        Fsscript export_test = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test.fsscript");

        Assertions.assertTrue(rootFsscriptLoader.getWhoImportMe(export_test.getPath()).isEmpty());

        //现在执行下表达式
        import_test2.eval(import_test2.newInstance(appCtx));

        List<Fsscript> export_test_import_list =  rootFsscriptLoader.getWhoImportMe(export_test.getPath());
        Assertions.assertEquals(export_test_import_list.size(),2);
//        Assertions.assertEquals(export_test_import_list.get(0),import_test2);
//        Assertions.assertEquals(export_test_import_list.get(1),export_test2);

        Assertions.assertTrue(export_test_import_list.contains(export_test2));
        Assertions.assertTrue(export_test_import_list.contains(import_test2));
    }

}
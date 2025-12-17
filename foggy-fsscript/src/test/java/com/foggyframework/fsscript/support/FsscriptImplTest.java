package com.foggyframework.fsscript.support;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class FsscriptImplTest {
    @Resource
    FileFsscriptLoader fileFsscriptLoader;
    @Resource
    RootFsscriptLoader rootFsscriptLoader;
    @Resource
    ApplicationContext appCtx;
    @Test
    public void hasImport() {

        rootFsscriptLoader.clear();

        Fsscript import_test =fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_test.fsscript");
        Fsscript export_test = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test.fsscript");

        //此时仅加载，还没有执行表达式，所以都是false
        Assert.assertFalse(import_test.hasImport(export_test));
        Assert.assertFalse(export_test.hasImport(import_test));

        //现在执行下表达式，应当是true了
        import_test.eval(import_test.newInstance(appCtx));
        Assert.assertTrue(import_test.hasImport(export_test));
        //还是false
        Assert.assertFalse(export_test.hasImport(import_test));

    }

    /**
     * 测试更复杂点的情况，多层import
     */
    @Test
    public void hasImport2() {
        Fsscript import_test2 = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/import_test2.fsscript");
        Fsscript export_test2 = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test2.fsscript");
        Fsscript export_test = fileFsscriptLoader.findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/export_test.fsscript");

        //现在执行下表达式，应当是true了
        import_test2.eval(import_test2.newInstance(appCtx));

        Assert.assertTrue(import_test2.hasImport(export_test2));
        Assert.assertTrue(import_test2.hasImport(export_test));
        Assert.assertTrue(export_test2.hasImport(export_test));


    }
}
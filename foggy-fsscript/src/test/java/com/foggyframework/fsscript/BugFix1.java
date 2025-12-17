package com.foggyframework.fsscript;

import com.foggyframework.core.thread.MultiThreadExecutor;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class BugFix1 {
    @Resource
    ApplicationContext appCtx;

    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/bug_fix/bug_fix_1_1.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);


    }

    @Test
    public void evalValue2() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/bug_fix/bug_fix_1_2.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = fScript.eval(ee);
        System.out.println(v);

    }


    @Test
    public void evalValueImport() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/bug_fix_import1/bug_fix_import1.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx,fScript.getFsscriptClosureDefinition().newFoggyClosure());
        fScript.eval(ee);

        Assertions.assertEquals(4,(int)ee.getExportObject("bb"));
    }

    @Test
    public void evalValueImport_1() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/bug_fix_import1/bug_fix_import1.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx,fScript.getFsscriptClosureDefinition().newFoggyClosure());
        fScript.eval(ee);

        FsscriptFunction test = ee.getExportObject("test");
        Assertions.assertEquals(4,(int)test.autoApply(ee));
    }

    @Test
    public void evalValueImport2() {
        System.setProperty("fsscript_debug","debug");
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/bug_fix_import2/project-hdd-deploy-2.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx,fScript.getFsscriptClosureDefinition().newFoggyClosure());
        fScript.eval(ee);

        FsscriptFunction test = ee.getExportObject("restartAllServices");

        Assertions.assertEquals("dev-2",test.autoApply(ee));
    }

    @Test
    public void bug_fix_accept() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/bug_fix_accept/bug_fix_accept.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx,fScript.getFsscriptClosureDefinition().newFoggyClosure());
        fScript.eval(ee);

        FsscriptFunction bug_fix_accept = ee.getExportObject("bugFixAccept");
        FsscriptFunction bug_fix_accept2 = ee.getExportObject("bugFixAccept2");
        int times = 100;

        Integer [][]test = new Integer[times][4];

        MultiThreadExecutor executor = new MultiThreadExecutor(50);
        for (int j = 0; j < times; j++) {
            int x = j;
            executor.execute(() -> {
                int r = ThreadLocalRandom.current().nextInt();
                int r2 = ThreadLocalRandom.current().nextInt();
                Integer v = (Integer) bug_fix_accept.threadSafeAccept(r);
                Integer v2 = (Integer) bug_fix_accept2.threadSafeAccept(r2);

                test[x][0] = r;
                test[x][1] = v;

                test[x][2] = r2;
                test[x][3] = v2;
            });
        }

        executor.waitAllCompleted(true);

        for (int i = 0; i < times; i++) {
            Assertions.assertEquals(test[i][0],test[i][1]);
            Assertions.assertEquals(test[i][2],test[i][3]);
        }

    }
}

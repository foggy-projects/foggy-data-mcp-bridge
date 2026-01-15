package com.foggyframework.fsscript.exp.fun;

import com.foggyframework.core.common.MapBuilder;
import com.foggyframework.core.utils.DateUtils;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Date;
import java.util.Map;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class CheckDaysRangeTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/fun/checkDaysRange.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        FsscriptFunction f = ee.getExportObject("checkDaysRangeTest");
        FsscriptFunction f2 = ee.getExportObject("checkDaysRangeTest2");

        Assertions.assertEquals(0,f.apply(new Object[]{new Date(),new Date(),10}));
        Assertions.assertEquals(0,f2.apply(new Object[]{new Date(),new Date(),10}));

        Assertions.assertEquals(6,f.apply(new Object[]{DateUtils.addDays(new Date(),-6),new Date(),10}));
        Assertions.assertEquals(6,f2.apply(new Object[]{DateUtils.addDays(new Date(),-6),new Date(),10}));

        try {
            f.apply(new Object[]{DateUtils.addDays(new Date(), -6), new Date(), 3});
            Assertions.fail();
        }catch (Throwable t){

        }
        try {
            f.apply(new Object[]{DateUtils.addDays(new Date(), -32), new Date(), 31});
            Assertions.fail();
        }catch (Throwable t){

        }
    }
    @Test
    public void evalValue2() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/fun/fun_test_args.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        FsscriptFunction f = ee.getExportObject("checkDaysRangeTest");
        FsscriptFunction f2 = ee.getExportObject("checkDaysRangeTest2");

     Map args1= MapBuilder.builder().put("d1",new Date()).put("d1",new Date()).put("d2",new Date()).put("maxTimes",10).build();

        Assertions.assertEquals(0,f.apply(new Object[]{args1}));
        Assertions.assertEquals(0,f2.apply(new Object[]{args1}));

        Map args2= MapBuilder.builder().put("d1",DateUtils.addDays(new Date(),-6)).put("d2",new Date()).put("maxTimes",10).build();
        Assertions.assertEquals(6,f.apply(new Object[]{args2}));
        Assertions.assertEquals(6,f2.apply(new Object[]{args2}));

        try {
            Map args3= MapBuilder.builder().put("d1",DateUtils.addDays(new Date(),-6)).put("d2",new Date()).put("maxTimes",3).build();
            f.apply(new Object[]{args3});
            Assertions.fail();
        }catch (Throwable t){

        }
        try {
            Map args4= MapBuilder.builder().put("d1",DateUtils.addDays(new Date(),-32)).put("d2",new Date()).put("maxTimes",31).build();
            f.apply(new Object[]{args4});
            Assertions.fail();
        }catch (Throwable t){

        }
    }
}

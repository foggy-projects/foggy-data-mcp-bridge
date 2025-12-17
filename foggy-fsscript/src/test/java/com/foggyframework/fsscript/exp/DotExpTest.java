package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
@Slf4j
public class DotExpTest {
    @Resource
    ApplicationContext appCtx;
    @Test
    public void evalValue() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript("classpath:/com/foggyframework/fsscript/exp/dot.fsscript");

        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        fScript.eval(ee);

        Map dot1 = (Map) ee.getExportMap().get("dot1");
        List dot2 = (List) ee.getExportMap().get("dot2");

        Assertions.assertEquals(1,dot1.get("a"));
        Assertions.assertEquals(2,dot1.get("b"));
        Assertions.assertEquals(4,dot1.get("c"));
        Assertions.assertEquals(6,dot1.get("s"));

        Assertions.assertEquals(1,dot2.get(0));
        Assertions.assertEquals(3,dot2.get(1));
        Assertions.assertEquals(5,dot2.get(2));
        Assertions.assertEquals(6,dot2.get(3));

    }
}
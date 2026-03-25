package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosure;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class InstanceofExpTest {

    @Autowired
    ApplicationContext appCtx;

    @Test
    void testInstanceof() {
        Fsscript fScript = FileFsscriptLoader.getInstance().findLoadFsscript(
                "classpath:/com/foggyframework/fsscript/exp/instanceof_test.fsscript");

        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        FsscriptClosure closure = ee.getCurrentFsscriptClosure();

        // obj instanceof MyClass => true
        Assertions.assertEquals(true, closure.getVar("r1"), "imported class instanceof should be true");

        // "hello" instanceof String => true
        Assertions.assertEquals(true, closure.getVar("r2"), "string instanceof String should be true");

        // 123 instanceof Number => true
        Assertions.assertEquals(true, closure.getVar("r3"), "number instanceof Number should be true");

        // true instanceof Boolean => true
        Assertions.assertEquals(true, closure.getVar("r4"), "boolean instanceof Boolean should be true");

        // null instanceof String => false
        Assertions.assertEquals(false, closure.getVar("r5"), "null instanceof should be false");

        // [1,2,3] instanceof Array => true
        Assertions.assertEquals(true, closure.getVar("r6"), "array instanceof Array should be true");

        // {name:'test'} instanceof Map => true
        Assertions.assertEquals(true, closure.getVar("r7"), "map instanceof Map should be true");
    }
}

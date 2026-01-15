package com.foggyframework.fsscript.closure;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MethodFilterFsscriptClosureTest {

    @Test
    public void getArgByName() {
        DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();

        SimpleFsscriptClosure simple = new SimpleFsscriptClosure(null);
        MethodFilterFsscriptClosure m = new MethodFilterFsscriptClosure(new String[]{"a", "b"}, new String[]{"a1", "b1"});

        ee.pushFsscriptClosure(m);
        ee.pushFsscriptClosure(simple);

        MethodFilterFsscriptClosure c=  ee.getContext(MethodFilterFsscriptClosure.class);
        Assertions.assertEquals(c.getArgByName("a"),"a1");
    }
}
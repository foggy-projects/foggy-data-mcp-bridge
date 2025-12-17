package com.foggyframework.fsscript.closure;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class MethodFilterFsscriptClosureTest {

    @Test
    public void getArgByName() {
        DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();

        SimpleFsscriptClosure simple = new SimpleFsscriptClosure(null);
        MethodFilterFsscriptClosure m = new MethodFilterFsscriptClosure(new String[]{"a", "b"}, new String[]{"a1", "b1"});

        ee.pushFsscriptClosure(m);
        ee.pushFsscriptClosure(simple);

        MethodFilterFsscriptClosure c=  ee.getContext(MethodFilterFsscriptClosure.class);
        Assert.assertEquals(c.getArgByName("a"),"a1");
    }
}
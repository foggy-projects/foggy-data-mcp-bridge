package com.foggyframework.fsscript;

import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinition;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import org.junit.Assert;
import org.junit.Test;

public class DefaultExpEvaluatorTest {

    @Test
    public void getVar() {
        DefaultExpEvaluator ee =  DefaultExpEvaluator.newInstance();
        Object test = ee.getVar("test");
        Assert.assertNull(test);

        ee.setVar("test","test11");
        test = ee.getVar("test");
        Assert.assertEquals(test,"test11");
    }

    /**
     * 测试同一个FsscriptClosureDefinitionSpace内是否能拿到
     */
    @Test
    public void getVarClosure() {
        DefaultExpEvaluator ee =  DefaultExpEvaluator.newInstance();

        //生成一个新的闭包，然后注入test = test22；
        ee.pushNewFoggyClosure();
        ee.setVar("test","test22");

        Object test = ee.getVar("test");
        Assert.assertEquals(test,"test22");

        //推出当前闭包，此时应当拿不到test了
        ee.popFsscriptClosure();
        test = ee.getVar("test");
        Assert.assertNull(test);
    }

    /**
     * 测试不同的FsscriptClosureDefinitionSpace
     */
    @Test
    public void getVarDefinitionSpace() {
        DefaultExpEvaluator ee =  DefaultExpEvaluator.newInstance();

        //生成一个新的闭包，然后注入test = test22；
        ee.pushNewFoggyClosure();
        ee.setVar("test","test22");

        //创建一个全新的闭包空间，它与ee中的初值闭包应当是隔离的
        SimpleFsscriptClosureDefinitionSpace newSpace = new SimpleFsscriptClosureDefinitionSpace();
        SimpleFsscriptClosureDefinition newClosureDef = new SimpleFsscriptClosureDefinition(newSpace);
        ee.pushFsscriptClosure(newClosureDef.newFoggyClosure());

        //此时获取的test值应当是空的！
        Object test = ee.getVar("test");
        Assert.assertNull(test);

        //我们现在改变它的值，然后再推出，之前检查test的值是否还是正确的
        ee.setVar("test","test333");
        ee.popFsscriptClosure();
        test = ee.getVar("test");
        Assert.assertEquals(test,"test22");
    }
}
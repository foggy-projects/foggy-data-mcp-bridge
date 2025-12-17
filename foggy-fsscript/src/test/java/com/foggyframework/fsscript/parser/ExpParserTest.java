package com.foggyframework.fsscript.parser;


import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.exp.MapExp;
import com.foggyframework.fsscript.exp.VarExp;
import com.foggyframework.fsscript.parser.spi.Exp;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ExpParserTest {
    @Test
    public void testArray() {
        checkExp("'a,b'.split(',').length", 2);
    }

    /**
     * 测试四则运算
     */
    @Test
    public void testFourArithmetic() {
        checkExp("1+2", 3);
        checkExp("1+2+3", 6);

        checkExp("1+2*3", 7.0);
        checkExp("1-2*3", -5.0);

        checkExp("1-6/3", -1.0);
        checkExp("1-5/3", -0.6666666666666667);

        checkExp("1-6/3+5+5", 9.0);

        checkExp("1-6/3+5+5*2", 14.0);
    }

    @Test
    public void testDDDotList() {
        List<Integer> r1 = (List) evalValue("[...[1,2]]");
        Assertions.assertArrayEquals(new int[]{r1.get(0), r1.get(1)}, new int[]{1, 2});

        List<Integer> r2 = (List) evalValue("[...[1,2],...[3,4],5]");
        Assertions.assertArrayEquals(new int[]{r2.get(0), r2.get(1), r2.get(2), r2.get(3), r2.get(4)}, new int[]{1, 2, 3, 4, 5});

    }

    @Test
    public void testDDDotList2() {
        List<Integer> r1 = (List) evalValue("function b(c){return c;};let ss = [1,2];b([dd,ff,...ss,ee],a)");
        Assertions.assertArrayEquals(new Object[]{null, null, 1, 2, null}, new Object[]{null, null, r1.get(2), r1.get(3), null});


    }

    @Test
    public void testDDDotMap() {
        Map r1 = (Map) evalValue("{...{a:'1',b:2}}");
        Assertions.assertEquals("1", r1.get("a"));
        Assertions.assertEquals(2, r1.get("b"));

        Map r2 = (Map) evalValue("{...{a:'1',b:2},...{b:3,c:4},d:33,e:666}");
        Assertions.assertEquals("1", r2.get("a"));
        Assertions.assertEquals(3, r2.get("b"));
        Assertions.assertEquals(4, r2.get("c"));
        Assertions.assertEquals(33, r2.get("d"));
        Assertions.assertEquals(666, r2.get("e"));
    }

    @Test
    public void testPush() {
        List<Integer> r1 = (List) evalReulst("let bb= [];bb.push(1);return bb;");
        Assertions.assertArrayEquals(new int[]{r1.get(0)}, new int[]{1});

    }

    /**
     * 测试括号()
     */
    @Test
    public void testBrackets() {
        checkExp("(1+2)", 3);
        checkExp("(1+2)*3", 9.0);
        checkExp("(1+(2*2))*3", 15.0);
    }

    @Test
    public void testNf() {
        checkExp("let a = a=>{'b'};a();", "b");
        checkExp("let a = (a)=>{'b'};a();", "b");
    }

    @Test
    public void testNf2() {
        checkExp("let a = a=>{'b'};a();", "b");
    }

    @Test
    public void testNf3() {
        checkExp("let b = {a: e=>{'c'}};let ff = b['a'];ff();", "c");
    }
    @Test
    public void testOf() {
        checkExp("let b = [1,2];let v=0; for(const x of b){ v =v+x;};v;", 3);
    }
    @Test
    public void testIncude() {
        checkExp("let b = [1,2];b.includes(2);", true);
        checkExp("let b = [1,2];b.includes(3);", false);
    }
    /**
     * 测试return
     */
    @Test
    public void testReturn() {
        Object ret = evalValue("return (1+2)*3");
        Assert.assertTrue(ret instanceof Exp.ReturnExpObject);
        Assert.assertEquals(((Exp.ReturnExpObject) ret).value, 9.0);
    }

    /**
     * 测试if
     */
    @Test
    public void testIf() {
        checkReturn(" if (1){ return 'a';} return 'b';", "a");
        checkReturn(" if (0){ return 'a';} return 'b';", "b");
    }

    @Test
    public void testMap() {
        String expStr = "var x = {a:1,b,}";
        Exp exp = new ExpParser().compileEl(expStr);
        Assert.assertNotNull(exp);
        Assert.assertTrue(exp instanceof VarExp);
        MapExp mm = (MapExp) ((VarExp) exp).getExp();
        Assert.assertEquals(mm.getLl().size(), 2);
    }

    @Test
    public void testLet() {
        String expStr = "let x = {a:1,b,}";
        Exp exp = new ExpParser().compileEl(expStr);
        Assert.assertNotNull(exp);
        Assert.assertTrue(exp instanceof VarExp);
        MapExp mm = (MapExp) ((VarExp) exp).getExp();
        Assert.assertEquals(mm.getLl().size(), 2);
    }

    /**
     * 测试if else
     */
    @Test
    public void testIfElse() {
        checkReturn(" if (1){ return 'a';} else {return 'b';}", "a");
        checkReturn(" if (0){ return 'a';} else {return 'b';}", "b");
    }

    @Test
    public void testX1() {
        checkExp(" `12`", "12");
    }

    @Test
    public void testX2() {
        checkExp(" `1\\`2`", "1`2");
    }

    /**
     * 测试if else if
     */
    @Test
    public void testIfElseIf() {
        checkReturn(" if (1){ return 'a';} else if (1){ return 'b';} else {return 'c';}", "a");
        checkReturn(" if (0){ return 'a';} else if (1){ return 'b';} else {return 'c';}", "b");
        checkReturn(" if (0){ return 'a';} else if (0){ return 'b';} else {return 'c';}", "c");
    }

    @Test
    public void testJSON_format2() {
        Map ret = (Map) evalReulst("var a='a';var b='b';return {a,b,c:1}");
        Assert.assertEquals(ret.get("a"), "a");
        Assert.assertEquals(ret.get("b"), "b");
        Assert.assertEquals(ret.get("c"), 1);
    }

    @Test
    public void testJSON_format3() {
        Object ret = evalReulst("{c:1}");
    }

    @Test
    public void testJSON_format4() {
        Object ret = evalReulst("{}");
    }

    @Test
    public void testJSON_format5() {
        Object ret = evalReulst("{c:1,b}");
    }

    @Test
    public void testJSON_format6() {
        Object ret = evalReulst("{a,b}");
    }

    @Test
    public void testEq() {
        checkExp(" 'a'=='b'", false);
        checkExp(" 'a'=='a'", true);
    }

    @Test
    public void testMapVar() {
        checkExp("var {a,b,c} = {a: 1,b:2};return a;", 1);
        checkExp("var {a,b,c} = {a: 1,b:2};return b;", 2);
        checkExp("var {a,b,c} = {a: 1,b:2};return c;", null);
    }

    @Test
    public void testXX() {
        checkExp("insertPages?.length>1?``:2", 2);
    }

    @Test
    public void testXX2() {
        checkExp("config ? config.isBatchInterface || false : false", false);
    }

    @Test
    public void testXX3() {
        checkExp("2 ? 0 || 10 : false", 10);
    }

    @Test
    public void testXX4() {
        checkExp("0 ? 0 || 10 : false", false);
    }

    @Test
    public void testXX5() {
        checkExp("var singleRecord=1;delete singleRecord;return singleRecord;", null);
    }

    @Test
    public void testXX6() {
        checkExp("var singleRecord={batchMetadata:2,x:1};delete singleRecord.batchMetadata;return singleRecord.batchMetadata; ", null);
        checkExp("var singleRecord={batchMetadata:2,x:1};delete singleRecord.batchMetadata;return singleRecord.x; ", 1);
    }
    @Test
    public void testXX6_1() {
        checkExp("var singleRecord={batchMetadata:{l3:1,lx:2},x:1};delete singleRecord.batchMetadata.l3;return singleRecord.batchMetadata.l3; ", null);
    } @Test
    public void testXX6_2() {
        checkExp("var singleRecord={batchMetadata:{l3:1,lx:2},x:1};delete singleRecord.batchMetadata.l3;return singleRecord.batchMetadata.lx; ", 2);
    }
    @Test
    public void testFF() {
        evalValue("function x(){} function x2(){}");
    }
    @Test
    public void testFFx() {
        evalValue("allResults?.filter(r => r.code === 0 || r.code === 200)");
    }
    @Test
    public void testFFx2() {
        checkExp("  r?.code !== 0 && r?.code !== 200",true);
    }
    @Test
    public void testAX() {
        checkExp("const allTimes=[1,2,3];const endTime = allTimes[allTimes.length - 1];",3);
    }
    @Test
    public void testAA() {
        checkExp("const ss={b:1};ss.b++;",1);
    }
    @Test
    public void testAA2() {
        checkExp("const ss={b:1};++ss.b;",2);
    }
    @Test
    public void testNewDate() {
        evalValue("new Date()");
        evalValue("new Date(123)");
    }

    @Test
    public void testListMap() {
        checkExp("[1,2,3].map(e=>e+1)[0];",2);
        checkExp("[1,2,3].map(e=>e+1)[1];",3);
        checkExp("[1,2,3].map(e=>e+1)[2];",4);
    }
    @Test
    public void testStringLength() {
        checkExp("'123'.length;",3);
    }
    @Test
    public void testListMapJoin() {
        checkExp("[1,2,3].map(e=>e+1).join(',');","2,3,4");
    }
    private Object evalReulst(String expStr) {
        Exp exp = new ExpParser().compileEl(expStr);
        DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();
        Object obj = exp.evalResult(ee);
        return obj;
    }

    private Object evalValue(String expStr) {
        Exp exp = new ExpParser().compileEl(expStr);
        DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();
        Object obj = exp.evalValue(ee);
        return obj;
    }

    private void checkReturn(String expStr, Object result) {
        Exp exp = new ExpParser().compileEl(expStr);
        DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();
        Object ret = exp.evalValue(ee);
        Assert.assertTrue(ret instanceof Exp.ReturnExpObject);
        Assert.assertEquals(((Exp.ReturnExpObject) ret).value, result);
    }

    private void checkExp(String expStr, Object result) {
        Exp exp = new ExpParser().compileEl(expStr);
        DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();
        Object obj = exp.evalResult(ee);
        Assert.assertEquals(obj, result);
    }
}
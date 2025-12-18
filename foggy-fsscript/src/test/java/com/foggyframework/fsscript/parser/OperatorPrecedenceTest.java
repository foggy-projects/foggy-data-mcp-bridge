package com.foggyframework.fsscript.parser;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Exp;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

/**
 * 运算符优先级测试
 * 测试三元运算符与其他运算符的优先级和结合性
 */
public class OperatorPrecedenceTest {

    /**
     * 三元运算符优先级测试
     * 三元运算符优先级低于比较运算符和算术运算符
     */
    @Test
    public void testTernaryWithArithmetic() {
        // 1==1?1+2:3+2 应解析为 (1==1)?(1+2):(3+2)
        // = true?3:5 = 3
        checkExp("1==1?1+2:3+2", 3);

        // 1==0?1+2:3+2 应解析为 (1==0)?(1+2):(3+2)
        // = false?3:5 = 5
        checkExp("1==0?1+2:3+2", 5);
    }

    /**
     * 三元运算符与乘法的优先级
     */
    @Test
    public void testTernaryWithMultiplication() {
        // 1==1?2*3:4*5 应解析为 (1==1)?(2*3):(4*5)
        // = true?6:20 = 6
        checkExp("1==1?2*3:4*5", 6.0);

        // 1==0?2*3:4*5 = false?6:20 = 20
        checkExp("1==0?2*3:4*5", 20.0);
    }

    /**
     * 三元运算符与混合算术的优先级
     */
    @Test
    public void testTernaryWithMixedArithmetic() {
        // 1?1+2*3:4 应解析为 1?(1+(2*3)):4 = 1?7:4 = 7
        checkExp("1?1+2*3:4", 7.0);

        // 0?1+2*3:4 = 0?7:4 = 4
        checkExp("0?1+2*3:4", 4);
    }

    /**
     * 三元运算符与逻辑运算符的优先级
     * 三元运算符优先级低于 && 和 ||
     */
    @Test
    public void testTernaryWithLogical() {
        // 注意: 当前实现 && 返回布尔值而非短路值
        // 1&&1?2:3 应解析为 (1&&1)?2:3 = true?2:3 = 2
        // 但由于 true 作为条件会选择 then 分支
        checkExp("true&&true?2:3", 2);

        // 0||1?2:3 应解析为 (0||1)?2:3
        // 当前 || 返回的是短路值 1，1为真选择 then 分支
        checkExp("0||1?2:3", 2);

        // 0&&1?2:3 应解析为 (0&&1)?2:3 = false?2:3 = 3
        checkExp("0&&1?2:3", 3);

        // 使用布尔字面量更明确
        checkExp("true?2:3", 2);
        checkExp("false?2:3", 3);
    }

    /**
     * 三元运算符条件分支中的逻辑运算
     */
    @Test
    public void testTernaryBranchWithLogical() {
        // 1?0||10:5 应解析为 1?(0||10):5 = 1?10:5 = 10
        checkExp("1?0||10:5", 10);

        // 使用更明确的测试验证优先级
        // true?1:0||2 应解析为 true?1:(0||2) = 1
        checkExp("true?1:0||2", 1);

        // false?1:0||2 应解析为 false?1:(0||2) = 2
        checkExp("false?1:0||2", 2);
    }

    /**
     * 嵌套三元运算符（右结合）
     */
    @Test
    public void testNestedTernary() {
        // a?b:c?d:e 应解析为 a?b:(c?d:e)（右结合）
        // 1?2:0?3:4 = 1?2:(0?3:4) = 1?2:4 = 2
        checkExp("1?2:0?3:4", 2);

        // 0?2:1?3:4 = 0?2:(1?3:4) = 0?2:3 = 3
        checkExp("0?2:1?3:4", 3);

        // 0?2:0?3:4 = 0?2:(0?3:4) = 0?2:4 = 4
        checkExp("0?2:0?3:4", 4);
    }

    /**
     * 三元运算符在then分支中嵌套
     */
    @Test
    public void testNestedTernaryInThen() {
        // 1?1?2:3:4 = 1?(1?2:3):4 = 1?2:4 = 2
        checkExp("1?1?2:3:4", 2);

        // 1?0?2:3:4 = 1?(0?2:3):4 = 1?3:4 = 3
        checkExp("1?0?2:3:4", 3);

        // 0?1?2:3:4 = 0?(1?2:3):4 = 4
        checkExp("0?1?2:3:4", 4);
    }

    /**
     * 三元运算符与比较运算符的优先级
     */
    @Test
    public void testTernaryWithComparison() {
        // 2>1?10:20 应解析为 (2>1)?10:20 = true?10:20 = 10
        checkExp("2>1?10:20", 10);

        // 1>2?10:20 = (1>2)?10:20 = false?10:20 = 20
        checkExp("1>2?10:20", 20);

        // 1<2?10:20 = (1<2)?10:20 = true?10:20 = 10
        checkExp("1<2?10:20", 10);

        // 1<=1?10:20 = (1<=1)?10:20 = true?10:20 = 10
        checkExp("1<=1?10:20", 10);

        // 1>=1?10:20 = (1>=1)?10:20 = true?10:20 = 10
        checkExp("1>=1?10:20", 10);
    }

    /**
     * 三元运算符分支中的比较运算
     */
    @Test
    public void testTernaryBranchWithComparison() {
        // 1?2>1:0 应解析为 1?(2>1):0 = 1?true:0 = true
        checkExp("1?2>1:0", true);

        // 0?0:1<2 应解析为 0?0:(1<2) = 0?0:true = true
        checkExp("0?0:1<2", true);
    }

    /**
     * 复杂表达式：多个运算符组合
     */
    @Test
    public void testComplexExpression() {
        // 1+1==2?3*2:4-1 应解析为 ((1+1)==2)?((3*2)):((4-1))
        // = (2==2)?(6):(3) = true?6:3 = 6
        checkExp("1+1==2?3*2:4-1", 6.0);

        // 1+1==3?3*2:4-1 = (2==3)?6:3 = false?6:3 = 3
        checkExp("1+1==3?3*2:4-1", 3);
    }

    /**
     * 带括号的三元表达式
     */
    @Test
    public void testTernaryWithParentheses() {
        // (1==1)?1+2:3+2 与 1==1?1+2:3+2 应相同
        checkExp("(1==1)?1+2:3+2", 3);

        // 1==(1?1:0)+2 应为 1==((1?1:0)+2) = 1==(1+2) = 1==3 = false
        checkExp("1==(1?1:0)+2", false);

        // (1?1:0)+2 = 1+2 = 3
        checkExp("(1?1:0)+2", 3);
    }

    /**
     * 三元运算符与可选链操作符
     */
    @Test
    public void testTernaryWithOptionalChaining() {
        // obj?.prop ? 1 : 0 - 可选链应先求值
        checkExp("null?.x?1:0", 0);
    }

    /**
     * 三元运算符返回不同类型
     */
    @Test
    public void testTernaryReturnTypes() {
        // 返回字符串
        checkExp("1?'yes':'no'", "yes");
        checkExp("0?'yes':'no'", "no");

        // 返回数字
        checkExp("1?100:200", 100);
        checkExp("0?100:200", 200);

        // 返回布尔
        checkExp("1?true:false", true);
        checkExp("0?true:false", false);
    }

    private Object evalResult(String expStr) {
        Exp exp = new ExpParser().compileEl(expStr);
        DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();
        return exp.evalResult(ee);
    }

    private void checkExp(String expStr, Object result) {
        Exp exp = new ExpParser().compileEl(expStr);
        DefaultExpEvaluator ee = DefaultExpEvaluator.newInstance();
        Object obj = exp.evalResult(ee);
        Assert.assertEquals("Expression: " + expStr, result, obj);
    }
}

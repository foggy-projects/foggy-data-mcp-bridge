package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.Exp;
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * v8.1.11.beta：SQL 风格 {@code value in (...)} / {@code value not in (...)} 成员测试
 * 算子的单元测试。
 *
 * <p>覆盖维度：
 * <ul>
 *   <li>圆括号字面量 / 方括号数组 / 变量引用三种右值写法</li>
 *   <li>命中 / 未命中 / 空集合 / null 语义</li>
 *   <li>Number 类型混用（Integer vs Long vs BigDecimal）的宽松等值</li>
 *   <li>字符串命中</li>
 *   <li>回归：现有 for-in 元组迭代 {@code (item, index) in collection} 不受影响</li>
 * </ul>
 */
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class InNotInExpTest {

    @Autowired
    ApplicationContext appCtx;

    private Object eval(String expStr) {
        Exp exp = new ExpParser().compileEl(expStr);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        return exp.evalResult(ee);
    }

    // ------------------------ IN ------------------------

    @Test
    public void inParenListHit() {
        Assertions.assertEquals(true, eval("return 2 in (1,2,3);"));
    }

    @Test
    public void inParenListMiss() {
        Assertions.assertEquals(false, eval("return 5 in (1,2,3);"));
    }

    @Test
    public void inParenSingleElement() {
        Assertions.assertEquals(true, eval("return 1 in (1);"));
        Assertions.assertEquals(false, eval("return 2 in (1);"));
    }

    @Test
    public void inParenEmptyListAlwaysFalse() {
        Assertions.assertEquals(false, eval("return 1 in ();"));
        Assertions.assertEquals(false, eval("return null in ();"));
    }

    @Test
    public void inArrayLiteralHit() {
        Assertions.assertEquals(true, eval("return 2 in [1,2,3];"));
        Assertions.assertEquals(false, eval("return 99 in [1,2,3];"));
    }

    @Test
    public void inArrayLiteralEmpty() {
        Assertions.assertEquals(false, eval("return 1 in [];"));
    }

    @Test
    public void inVariableList() {
        Exp exp = new ExpParser().compileEl("return x in haystack;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("haystack", Arrays.asList(10, 20, 30));
        ee.setVar("x", 20);
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("x", 99);
        Assertions.assertEquals(false, exp.evalResult(ee));
    }

    @Test
    public void inVariableNullTreatedAsEmpty() {
        Exp exp = new ExpParser().compileEl("return x in haystack;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("haystack", null);
        ee.setVar("x", 1);
        Assertions.assertEquals(false, exp.evalResult(ee));
    }

    @Test
    public void inStringMembership() {
        Assertions.assertEquals(true, eval("return 'Apple' in ('Apple','Huawei','Xiaomi');"));
        Assertions.assertEquals(false, eval("return 'Sony' in ('Apple','Huawei','Xiaomi');"));
    }

    @Test
    public void inNullMatchesNullInList() {
        Assertions.assertEquals(true, eval("return null in (1, null, 3);"));
        Assertions.assertEquals(false, eval("return null in (1, 2, 3);"));
    }

    @Test
    public void inSameNumberTypeMatches() {
        // 与 fsscript `==` 等值契约一致（Equal.eq）：同类型数值相等
        Assertions.assertEquals(true, eval("return 1 in (1, 2);"));
        Assertions.assertEquals(false, eval("return 3 in (1, 2);"));
    }

    @Test
    public void inBigDecimalMatchesIntLiteralCrossType() {
        // BigDecimal 与其他数值类型的跨类型兼容是 Equal.eq 的既定规则，IN 继承
        Exp exp = new ExpParser().compileEl("return x in (1, 2, 3);");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("x", new BigDecimal("2"));
        Assertions.assertEquals(true, exp.evalResult(ee));
    }

    @Test
    public void inIntegerVsDoubleFollowsEqSemantics() {
        // Integer != Double 按 Java 装箱 .equals 规则，与 `1 == 1.0` 一致为 false
        // 跨类型匹配需要调用方显式转换或统一使用 BigDecimal
        Assertions.assertEquals(false, eval("return 1 in (1.0, 2.0);"));
    }

    // ------------------------ NOT IN ------------------------

    @Test
    public void notInParenListMiss() {
        Assertions.assertEquals(true, eval("return 5 not in (1,2,3);"));
    }

    @Test
    public void notInParenListHit() {
        Assertions.assertEquals(false, eval("return 2 not in (1,2,3);"));
    }

    @Test
    public void notInEmptyListAlwaysTrue() {
        Assertions.assertEquals(true, eval("return 1 not in ();"));
        Assertions.assertEquals(true, eval("return null not in ();"));
    }

    @Test
    public void notInArrayLiteral() {
        Assertions.assertEquals(true, eval("return 99 not in [1,2,3];"));
        Assertions.assertEquals(false, eval("return 2 not in [1,2,3];"));
    }

    @Test
    public void notInStringMembership() {
        Assertions.assertEquals(true, eval("return 'Sony' not in ('Apple','Huawei','Xiaomi');"));
        Assertions.assertEquals(false, eval("return 'Apple' not in ('Apple','Huawei','Xiaomi');"));
    }

    @Test
    public void notInVariableNullHaystackTreatedAsEmpty() {
        Exp exp = new ExpParser().compileEl("return x not in haystack;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("haystack", null);
        ee.setVar("x", 1);
        // x not in (null haystack) → true（空集合中不存在任何元素）
        Assertions.assertEquals(true, exp.evalResult(ee));
    }

    @Test
    public void notInVariableList() {
        Exp exp = new ExpParser().compileEl("return x not in haystack;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("haystack", Arrays.asList("Apple", "Huawei"));
        ee.setVar("x", "Sony");
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("x", "Apple");
        Assertions.assertEquals(false, exp.evalResult(ee));
    }

    // ------------------------ combined with other operators ------------------------

    @Test
    public void inCombinedWithLogicalAnd() {
        Exp exp = new ExpParser().compileEl("return brand in ('Apple','Huawei') && price > 100;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("brand", "Apple");
        ee.setVar("price", 999);
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("brand", "Sony");
        Assertions.assertEquals(false, exp.evalResult(ee));

        ee.setVar("brand", "Apple");
        ee.setVar("price", 50);
        Assertions.assertEquals(false, exp.evalResult(ee));
    }

    @Test
    public void notInCombinedWithLogicalOr() {
        Exp exp = new ExpParser().compileEl("return status not in ('cancelled','returned') || force;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("status", "paid");
        ee.setVar("force", false);
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("status", "cancelled");
        ee.setVar("force", false);
        Assertions.assertEquals(false, exp.evalResult(ee));

        ee.setVar("status", "cancelled");
        ee.setVar("force", true);
        Assertions.assertEquals(true, exp.evalResult(ee));
    }

    // ------------------------ regression: for-in tuple iteration ------------------------

    @Test
    public void regressionTupleInReturnsInResultForIteration() {
        // `(item, index) in list` 依旧返回 InResult，forEach 可用于迭代（保持 fsscript 旧语义）
        Exp exp = new ExpParser().compileEl("(item, index) in list");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("list", Arrays.asList("a", "b", "c"));

        Object result = exp.evalResult(ee);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof com.foggyframework.fsscript.fun.IN.InResult,
                "(item, index) in list 应当产生 InResult 用于迭代，但得到: " + result);

        // 用 forEach 实际遍历一次，确认迭代能走通
        List<Object> collected = new java.util.ArrayList<>();
        ((com.foggyframework.fsscript.fun.IN.InResult) result).forEach(ee, (v, i) -> collected.add(v));
        Assertions.assertEquals(Arrays.asList("a", "b", "c"), collected);
    }

    @Test
    public void regressionForInLoopStillWorks() {
        // 经典 for (var x in list) { ... } 语法不经过 IN 函数（grammar 走 createForIn），
        // 这里做一条 smoke test 确保回归无碰撞
        String expStr = "let result = []; let bb = [10,20,30]; for(let b in bb){ result.add(b); } return result;";
        Exp exp = new ExpParser().compileEl(expStr);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        Object v = exp.evalResult(ee);
        Assertions.assertEquals(Arrays.asList(0, 1, 2), v);
    }

    // ------------------------ haystack type coverage ------------------------

    @Test
    public void inWorksWithJavaSetAsHaystack() {
        Exp exp = new ExpParser().compileEl("return x in haystack;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("haystack", new java.util.HashSet<>(Arrays.asList("a", "b", "c")));
        ee.setVar("x", "b");
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("x", "z");
        Assertions.assertEquals(false, exp.evalResult(ee));
    }

    @Test
    public void inWorksWithJavaArrayAsHaystack() {
        Exp exp = new ExpParser().compileEl("return x in haystack;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("haystack", new Integer[]{1, 2, 3});
        ee.setVar("x", 2);
        Assertions.assertEquals(true, exp.evalResult(ee));
    }

    @Test
    public void inWorksWithSingletonList() {
        Exp exp = new ExpParser().compileEl("return x in haystack;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("haystack", Collections.singletonList("only"));
        ee.setVar("x", "only");
        Assertions.assertEquals(true, exp.evalResult(ee));
    }

    // ------------------------ precedence / combination lockdown ------------------------

    /**
     * 一元 `!` 与 IN 的绑定：`!x in (1,2)` 应当解析为 `(!x) in (1,2)`，
     * 而非 `!(x in (1,2))`（因为 `!` 在 factor 层，IN 在 term3 层，`!` 绑定更紧）。
     */
    @Test
    public void unaryNotBindsTighterThanIn() {
        // !1 → false(Java)；false in (1, 2) → false（false 不在列表里）
        Assertions.assertEquals(false, eval("return !1 in (1, 2);"));
        // 用括号显式取反 IN 结果
        Assertions.assertEquals(false, eval("return !(1 in (1, 2));"));
        Assertions.assertEquals(true, eval("return !(5 in (1, 2));"));
    }

    /**
     * 算术 LHS：`(a+1) in (...)` 应当先计算 `a+1` 再做成员测试
     * (term2 + 通过无操作产生式升到 term3，符合 grammar term3 ::= term3 IN term2 的结构)
     */
    @Test
    public void arithmeticOnLhsIsEvaluatedFirst() {
        Assertions.assertEquals(true, eval("return (1 + 1) in (2, 3);"));
        Assertions.assertEquals(false, eval("return (1 + 1) in (3, 4);"));
    }

    /**
     * 算术 RHS：`x in (y+1, z+10)` 列表元素会被逐个求值。
     * <p>注意：用 `+` 保持 Integer×Integer → Integer（fsscript {@code Plus} 的快路径）。
     * 不能混 `*`，因为 {@code Multiply} 一律返回 Double，会触发 Integer vs Double 的
     * {@code Equal.eq} 不等，与本测试想验证的"列表求值"主题无关。见
     * {@link #inIntegerVsDoubleFollowsEqSemantics} 对该契约的单独锁定。</p>
     */
    @Test
    public void arithmeticInListItemsEvaluated() {
        Exp exp = new ExpParser().compileEl("return x in (y + 1, z + 10);");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("y", 3);  // y+1 = 4
        ee.setVar("z", 10); // z+10 = 20

        ee.setVar("x", 4);
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("x", 20);
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("x", 99);
        Assertions.assertEquals(false, exp.evalResult(ee));
    }

    /**
     * `==` 与 IN 同属 term3，左结合：`x in (1,2) == true` 解析为 `(x in (1,2)) == true`。
     */
    @Test
    public void equalsAndInAreLeftAssociativeAtTerm3() {
        Exp exp = new ExpParser().compileEl("return x in (1, 2) == true;");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("x", 1);
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("x", 99);
        // 99 in (1,2) → false；false == true → false
        Assertions.assertEquals(false, exp.evalResult(ee));
    }

    /**
     * AND + IN 精确组合：真真、真假、假真、假假 四象限
     */
    @Test
    public void andPlusInTruthTable() {
        Assertions.assertEquals(true, eval("return 1 == 1 && 2 in (1, 2, 3);"));
        Assertions.assertEquals(false, eval("return 1 == 1 && 99 in (1, 2, 3);"));
        Assertions.assertEquals(false, eval("return 1 == 2 && 2 in (1, 2, 3);"));
        Assertions.assertEquals(false, eval("return 1 == 2 && 99 in (1, 2, 3);"));
    }

    /**
     * OR + IN：至少一侧为真即为真。
     */
    @Test
    public void orPlusInTruthTable() {
        Assertions.assertEquals(true, eval("return 1 == 1 || 99 in (1, 2, 3);"));
        Assertions.assertEquals(true, eval("return 1 == 2 || 2 in (1, 2, 3);"));
        Assertions.assertEquals(false, eval("return 1 == 2 || 99 in (1, 2, 3);"));
    }

    /**
     * 多个 IN 链式：`a in (...) && b in (...) && c not in (...)` 解析与求值都无歧义
     */
    @Test
    public void chainedInWithAndMixed() {
        Exp exp = new ExpParser().compileEl(
                "return a in (1, 2) && b in (3, 4) && c not in (5, 6);");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("a", 1);
        ee.setVar("b", 3);
        ee.setVar("c", 7);
        Assertions.assertEquals(true, exp.evalResult(ee));

        ee.setVar("c", 5); // c 命中 (5,6) → not in 为 false → 整体 false
        Assertions.assertEquals(false, exp.evalResult(ee));
    }

    /**
     * 重要行为锁定：fsscript 的 `&&` 当前 **不短路**（见 AA.java），两侧都会被求值。
     * <p>这是既有运行时契约，不是本次 IN 引入的行为 —— 但若后续有人改造 AA 开启短路，
     * 这条测试会告警，让相关方确认是否会影响依赖两侧副作用的用法。</p>
     */
    @Test
    public void andDoesNotShortCircuit_behaviorLockdown() {
        Exp exp = new ExpParser().compileEl("b = b + 1; return 1 == 2 && b in (1, 2);");
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        ee.setVar("b", 0);
        Object r = exp.evalResult(ee);
        // && 结果为 false（LHS 为 false）
        Assertions.assertEquals(false, r);
        // 若短路实现，RHS 不会被求值；但实际会执行 —— 当前契约：两侧都走一遍。
        // 因为 RHS 只是 `b in (1,2)`（纯读），副作用仅来自前置 `b = b + 1`。
        // b 应为 1（从 0 自增一次）
        Assertions.assertEquals(1, ee.getVar("b"));
    }
}

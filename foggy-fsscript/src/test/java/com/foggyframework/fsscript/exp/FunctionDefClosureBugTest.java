package com.foggyframework.fsscript.exp;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.parser.ExpParser;
import com.foggyframework.fsscript.parser.spi.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;

/**
 * FunctionDefExp 闭包栈上下文保存问题复现测试。
 *
 * 对应问题：FunctionDefExp.java 内部类 X 在构造时
 *   savedStack = new ArrayList<>(ee.getStack())   // 浅拷贝
 *   this.ee = ee                                   // 引用
 * 可能导致：
 *   1. 栈上下文重复叠加（ee.clone() 的栈 + savedStack）
 *   2. 闭包浅拷贝导致变量共享/泄漏
 *   3. 独立闭包实例间的变量隔离失败
 *   4. 调用时作用域泄漏到闭包内
 */
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
public class FunctionDefClosureBugTest {

    @Autowired
    ApplicationContext appCtx;

    private static final String BASE = "classpath:/com/foggyframework/fsscript/exp/";

    // ===================================================================
    // Bug 1: 基本闭包 —— 函数返回后，内层函数仍能访问外层局部变量
    // ===================================================================

    @Test
    @DisplayName("Bug1: 基本闭包 - outer 返回后 inner 仍能访问 outer 的局部变量")
    void testBasicClosureAfterReturn() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript(BASE + "closure_bug_1_stale_capture.fsscript");
        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Object result = ee.getExportMap().get("result");
        Assertions.assertNotNull(result, "闭包返回值不应为 null");
        Assertions.assertEquals(10, ((Number) result).intValue(),
                "inner() 应返回 outer 的局部变量 x=10，闭包栈上下文应正确保存");
    }

    // ===================================================================
    // Bug 2: 浅拷贝 —— 定义后修改外层变量，闭包看到修改后的值
    // ===================================================================

    @Test
    @DisplayName("Bug2: 浅拷贝 - 闭包定义后外层变量被修改，验证闭包是否看到新值")
    void testMutationAfterCapture() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript(BASE + "closure_bug_2_mutation_after_capture.fsscript");
        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map<String, Object> mm = ee.getExportMap();
        Object beforeCall = mm.get("beforeCall");
        Object result = mm.get("result");

        Assertions.assertEquals(99, ((Number) beforeCall).intValue());
        // JS 语义：闭包捕获变量绑定（引用），x 被改为 99 后 getX() 应返回 99
        Assertions.assertEquals(99, ((Number) result).intValue(),
                "JS 语义下闭包应看到修改后的值 99（capture-by-reference）");
    }

    // ===================================================================
    // Bug 3: 多层嵌套 + 函数工厂 —— 不同闭包实例参数隔离
    // ===================================================================

    @Test
    @DisplayName("Bug3: 函数工厂 - makeAdder(5) 和 makeAdder(10) 产生的闭包互不干扰")
    void testNestedClosureFactory() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript(BASE + "closure_bug_3_nested_return.fsscript");
        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map<String, Object> mm = ee.getExportMap();

        // add5(3) = 5 + 3 = 8
        Assertions.assertEquals(8, ((Number) mm.get("r1")).intValue(),
                "add5(3) 应为 8：base=5 被正确捕获");
        // add10(3) = 10 + 3 = 13
        Assertions.assertEquals(13, ((Number) mm.get("r2")).intValue(),
                "add10(3) 应为 13：base=10 被正确捕获");
        // add5(0) = 5
        Assertions.assertEquals(5, ((Number) mm.get("r3")).intValue(),
                "add5(0) 应为 5：base=5 不应被 add10 的调用污染");
        // add10(0) = 10
        Assertions.assertEquals(10, ((Number) mm.get("r4")).intValue(),
                "add10(0) 应为 10：base=10 不应被 add5 的调用污染");
    }

    // ===================================================================
    // Bug 4: 闭包计数器 —— 多次调用同一闭包，状态累积
    // ===================================================================

    @Test
    @DisplayName("Bug4: 闭包计数器 - 多次调用同一闭包应累积状态")
    void testClosureCounter() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript(BASE + "closure_bug_4_counter.fsscript");
        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map<String, Object> mm = ee.getExportMap();

        Assertions.assertEquals(1, ((Number) mm.get("v1")).intValue(),
                "第一次调用 counter() 应返回 1");
        Assertions.assertEquals(2, ((Number) mm.get("v2")).intValue(),
                "第二次调用 counter() 应返回 2");
        Assertions.assertEquals(3, ((Number) mm.get("v3")).intValue(),
                "第三次调用 counter() 应返回 3");
    }

    // ===================================================================
    // Bug 5: 两个独立计数器 —— 验证不同工厂实例的变量隔离
    // 这是核心 bug 复现：如果 savedStack 浅拷贝导致 closure 对象共享，
    // counterA 和 counterB 会共享同一个 count，互相干扰
    // ===================================================================

    @Test
    @DisplayName("Bug5: 独立计数器 - 两个 makeCounter() 产生的闭包应完全独立")
    void testIndependentCounters() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript(BASE + "closure_bug_5_independent_counters.fsscript");
        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Map<String, Object> mm = ee.getExportMap();

        int a1 = ((Number) mm.get("a1")).intValue();
        int a2 = ((Number) mm.get("a2")).intValue();
        int b1 = ((Number) mm.get("b1")).intValue();
        int a3 = ((Number) mm.get("a3")).intValue();
        int b2 = ((Number) mm.get("b2")).intValue();

        System.out.println("a1=" + a1 + " a2=" + a2 + " b1=" + b1 + " a3=" + a3 + " b2=" + b2);

        // counterA: 1, 2, 3
        Assertions.assertEquals(1, a1, "counterA 第1次调用应为 1");
        Assertions.assertEquals(2, a2, "counterA 第2次调用应为 2");
        Assertions.assertEquals(3, a3, "counterA 第3次调用应为 3");

        // counterB: 1, 2 —— 与 counterA 完全独立
        Assertions.assertEquals(1, b1,
                "counterB 第1次调用应为 1（与 counterA 隔离）。" +
                "如果返回 3，说明 savedStack 浅拷贝导致两个闭包共享了同一个 count VarDef");
        Assertions.assertEquals(2, b2,
                "counterB 第2次调用应为 2（与 counterA 隔离）");
    }

    // ===================================================================
    // Bug 6: 作用域泄漏 —— 闭包不应看到调用时上下文的变量
    // apply() 中 ee.clone() 带入调用时栈 + pushFsscriptClosure(savedStack)
    // 如果两者叠加且 BeanDefinitionSpace 相同，函数可穿透到调用方变量
    // ===================================================================

    @Test
    @DisplayName("Bug6: 作用域泄漏 - 闭包不应访问到调用方作用域的同名变量")
    void testScopeLeakPrevention() {
        Fsscript fScript = FileFsscriptLoader.getInstance()
                .findLoadFsscript(BASE + "closure_bug_6_scope_leak.fsscript");
        ExpEvaluator ee = fScript.newInstance(appCtx);
        fScript.eval(ee);

        Object result = ee.getExportMap().get("result");
        Assertions.assertEquals("defined_scope", result,
                "capture() 应返回定义时作用域的 secret='defined_scope'，" +
                "而非调用方 callInNewScope 中的 secret='call_scope'。" +
                "如果返回 'call_scope'，说明 ee.clone() + savedStack 叠加导致栈穿透");
    }

    // ===================================================================
    // Bug 7: 直接操作 evaluator 验证栈结构 —— 纯 Java 级别复现
    // 不依赖 .fsscript 文件，直接构造 FunctionDefExp 场景
    // ===================================================================

    @Test
    @DisplayName("Bug7: 栈结构验证 - clone() + pushSavedStack 导致栈大小异常膨胀")
    void testStackDuplicationOnClone() {
        String script = "function outer() { var x = 1; function inner() { return x; }; return inner; }; " +
                "var fn = outer(); export var result = fn();";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        Object result = ee.getExportMap().get("result");
        Assertions.assertEquals(1, ((Number) result).intValue(),
                "inner() 通过闭包应能访问 outer 的 x=1");
    }

    @Test
    @DisplayName("Bug7b: 栈结构验证 - 三层嵌套闭包的栈膨胀")
    void testTripleNestedClosureStackGrowth() {
        // 三层嵌套：每层 apply 都会 clone + push savedStack
        // 如果存在栈重复，栈大小会指数级增长
        String script =
                "function level1() { " +
                "  var a = 'L1'; " +
                "  function level2() { " +
                "    var b = 'L2'; " +
                "    function level3() { " +
                "      return a + b; " +
                "    }; " +
                "    return level3; " +
                "  }; " +
                "  return level2; " +
                "}; " +
                "var f2 = level1()(); " +  // level1() returns level2, level2() returns level3
                "export var result = f2();";  // level3() returns 'L1L2'

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        Object result = ee.getExportMap().get("result");
        Assertions.assertEquals("L1L2", result,
                "三层嵌套闭包: level3 应能访问 level1 的 a='L1' 和 level2 的 b='L2'");
    }

    // ===================================================================
    // Bug 8: 内联表达式 —— 高阶函数 + 闭包（简化版工厂模式）
    // ===================================================================

    @Test
    @DisplayName("Bug8: 高阶函数 - 函数返回函数，参数正确绑定")
    void testHigherOrderFunctionBinding() {
        String script =
                "function multiply(a) { " +
                "  function inner(b) { return a * b; }; " +
                "  return inner; " +
                "}; " +
                "var double = multiply(2); " +
                "var triple = multiply(3); " +
                "export var d5 = double(5); " +    // 2*5=10
                "export var t5 = triple(5); " +    // 3*5=15
                "export var d10 = double(10); " +  // 2*10=20
                "export var t1 = triple(1);";      // 3*1=3

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        Map<String, Object> mm = ee.getExportMap();
        Assertions.assertEquals(10, ((Number) mm.get("d5")).intValue(), "double(5)=10");
        Assertions.assertEquals(15, ((Number) mm.get("t5")).intValue(), "triple(5)=15");
        Assertions.assertEquals(20, ((Number) mm.get("d10")).intValue(), "double(10)=20");
        Assertions.assertEquals(3, ((Number) mm.get("t1")).intValue(), "triple(1)=3");
    }

    // ===================================================================
    // Bug 9: 循环中创建闭包 - var 经典闭包陷阱
    // ===================================================================

    @Test
    @DisplayName("Bug9: 循环中创建闭包 - var 变量共享（JS经典陷阱）")
    void testClosureInLoopWithVar() {
        String script =
                "var fns = []; " +
                "for (var i = 0; i < 3; i++) { " +
                "  function capture() { return i; }; " +
                "  fns.push(capture); " +
                "}; " +
                "export var r0 = fns[0](); " +
                "export var r1 = fns[1](); " +
                "export var r2 = fns[2](); " +
                "export var finalI = i;";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        Map<String, Object> mm = ee.getExportMap();
        int r0 = ((Number) mm.get("r0")).intValue();
        int r1 = ((Number) mm.get("r1")).intValue();
        int r2 = ((Number) mm.get("r2")).intValue();

        System.out.println("var loop: r0=" + r0 + " r1=" + r1 + " r2=" + r2);
        // JS 语义：var 共享同一个 i，循环结束后 i=3，三个闭包都应返回 3
        Assertions.assertEquals(3, r0, "JS var 闭包陷阱: 应返回循环结束后的 i=3");
        Assertions.assertEquals(3, r1, "JS var 闭包陷阱: 应返回循环结束后的 i=3");
        Assertions.assertEquals(3, r2, "JS var 闭包陷阱: 应返回循环结束后的 i=3");
    }

    // ===================================================================
    // Bug 10: 作用域泄漏精确测试
    // 闭包能否访问到定义时作用域中根本不存在的变量？
    // 这是 ee.clone() 栈 + savedStack 叠加的核心验证
    // ===================================================================

    @Test
    @DisplayName("Bug10: 精确作用域泄漏 - 闭包不应能访问定义时不存在的调用方局部变量")
    void testPreciseScopeLeak() {
        // capture() 定义在全局作用域，其定义时作用域中没有 'leaked' 变量
        // callWithLeak() 有局部变量 'leaked'，在其内部调用 capture()
        // 如果 ee.clone() 的调用时栈泄漏到 capture() 中，capture() 就能看到 'leaked'
        String script =
                "function capture() { " +
                "  return leaked; " +       // 'leaked' 在定义作用域中不存在
                "}; " +
                "function callWithLeak() { " +
                "  var leaked = 'BUG_SCOPE_LEAK'; " +
                "  return capture(); " +
                "}; " +
                "export var result = callWithLeak();";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        Object result = ee.getExportMap().get("result");

        System.out.println("Scope leak test: result = " + result);
        // 正确 JS 语义: ReferenceError (FSScript 中返回 null)
        // 如果返回 'BUG_SCOPE_LEAK' → 说明 ee.clone() 栈泄漏了调用方变量
        Assertions.assertNull(result,
                "capture() 的定义作用域中没有 'leaked' 变量，应返回 null。" +
                "如果返回 'BUG_SCOPE_LEAK'，说明 ee.clone() 带入了调用方的栈上下文（栈泄漏确认）");
    }

    // ===================================================================
    // Bug 11: ee 引用漂移验证
    // ee 保存的是 evaluator 引用，ee.stack 是 mutable 的
    // 在函数定义后 stack 发生变化，ee.clone() 会拿到不同的栈
    // ===================================================================

    @Test
    @DisplayName("Bug11: ee引用漂移 - 验证 ee.clone() 在调用时是否拿到了定义时以外的栈内容")
    void testEeReferenceDrift() {
        // defineAndReturn() 在自己的作用域中定义 getVal()
        // getVal() 捕获 defineAndReturn 的局部变量 val
        // defineAndReturn 返回 getVal，然后 defineAndReturn 的作用域被 pop
        // 再在全局作用域定义 val = 'wrong'
        // getVal() 被调用时，应该返回 'correct'（定义时的值），不是 'wrong'
        String script =
                "function defineAndReturn() { " +
                "  var val = 'correct'; " +
                "  function getVal() { return val; }; " +
                "  return getVal; " +
                "}; " +
                "var fn = defineAndReturn(); " +
                "var val = 'wrong'; " +     // 全局也有个 val
                "export var result = fn();";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        Object result = ee.getExportMap().get("result");
        System.out.println("ee drift test: result = " + result);
        // getVal 的 savedStack 包含 defineAndReturn 的局部 closure（val='correct'）
        // ee.clone() 的栈是全局栈（有 val='wrong'）
        // 变量查找从 savedStack 先找到 val='correct'
        Assertions.assertEquals("correct", result,
                "getVal() 应返回定义时捕获的 val='correct'，" +
                "不应被全局的 val='wrong' 干扰");
    }

    // ===================================================================
    // Bug 12: 深层递归栈膨胀测试
    // 每次递归调用 pushFsscriptClosure(savedStack)，栈大小 O(N * savedStack.size)
    // ===================================================================

    @Test
    @DisplayName("Bug12: 递归栈膨胀 - 递归调用应不会导致异常栈溢出")
    void testRecursionStackGrowth() {
        // 尾递归求和: sum(100) = 100 + 99 + ... + 1 = 5050
        String script =
                "function sum(n) { " +
                "  if (n <= 0) { return 0; }; " +
                "  return n + sum(n - 1); " +
                "}; " +
                "export var result = sum(100);";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        try {
            exp.evalValue(ee);
            Object result = ee.getExportMap().get("result");
            System.out.println("Recursion test: sum(100) = " + result);
            Assertions.assertEquals(5050, ((Number) result).intValue(),
                    "sum(100) 应为 5050");
        } catch (StackOverflowError e) {
            System.err.println("StackOverflowError: 递归 100 层就溢出，" +
                    "说明 ee.clone()+savedStack 导致栈膨胀过快");
            Assertions.fail("递归 100 层不应导致 StackOverflowError，" +
                    "如果溢出说明每次调用栈增长过大（savedStack 重复叠加）");
        }
    }

    @Test
    @DisplayName("Bug12b: 深递归压力测试 - 500 层递归")
    void testDeepRecursionStress() {
        String script =
                "function countdown(n) { " +
                "  if (n <= 0) { return 'done'; }; " +
                "  return countdown(n - 1); " +
                "}; " +
                "export var result = countdown(500);";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);

        try {
            exp.evalValue(ee);
            Object result = ee.getExportMap().get("result");
            Assertions.assertEquals("done", result, "countdown(500) 应正常完成");
            System.out.println("Deep recursion 500 levels: OK");
        } catch (StackOverflowError e) {
            System.err.println("StackOverflowError at 500 levels: " +
                    "savedStack 每层叠加导致 JVM 栈溢出");
            Assertions.fail("500 层递归不应溢出。" +
                    "如果溢出，说明 ee.clone() + pushFsscriptClosure(savedStack) " +
                    "导致每层调用的栈大小线性增长 O(N*savedStack.size)");
        }
    }

    // ===================================================================
    // Bug 13: autoApply 路径 —— 不经过 clone 的执行路径
    // FsscriptFunction.executeFunction 是 public 的，
    // autoApply 直接用传入的 evaluator 而非 clone
    // ===================================================================

    @Test
    @DisplayName("Bug13: executeFunction 直接调用 - 验证不经 clone 的路径")
    void testExecuteFunctionDirectly() {
        // 通过 Java 直接调用 FsscriptFunction.executeFunction
        // 使用不同的 evaluator（模拟 autoApply 的情况）
        String script =
                "export function add(a, b) { return a + b; };";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        FsscriptFunction addFn = (FsscriptFunction) ee.getExportMap().get("add");
        Assertions.assertNotNull(addFn, "add 函数应被导出");

        // 通过 apply (走 clone 路径)
        Object result1 = addFn.apply(new Object[]{3, 4});
        Assertions.assertEquals(7, ((Number) result1).intValue(), "apply: 3+4=7");

        // 通过 executeFunction 传入当前 evaluator（模拟不 clone 的路径）
        Object result2 = addFn.executeFunction(ee, 10, 20);
        Assertions.assertEquals(30, ((Number) result2).intValue(), "executeFunction: 10+20=30");

        // 验证 executeFunction 后 evaluator 的栈没有被永久污染
        // （finally 块应该正确 pop 回来）
        Stack<FsscriptClosure> stack = ee.getStack();
        System.out.println("Stack size after executeFunction: " + stack.size());
        Assertions.assertEquals(1, stack.size(),
                "executeFunction 执行后栈应恢复到初始状态（只有根闭包）。" +
                "如果栈变大，说明 pushFsscriptClosure(savedStack) 和 pop 不匹配");
    }

    // ===================================================================
    // Bug 14: 栈冗余量化测试（反射检查 savedStack）
    //
    // apply() 流程:
    //   ee.clone()           → 复制 ee 当前栈 (与 savedStack 内容重叠)
    //   push(savedStack)     → 再推一遍定义时栈快照
    //   push(newClosure)     → 函数局部作用域
    //
    // 每层嵌套函数定义时捕获的 savedStack 包含上层的冗余栈，
    // 导致 savedStack.size = 2N-1 (N=嵌套层级)，理想值 = N
    // ===================================================================

    /**
     * 反射获取 FunctionDefExp.X 内部类的 savedStack 字段
     */
    @SuppressWarnings("unchecked")
    private List<FsscriptClosure> getSavedStack(Object fn) throws Exception {
        Field f = fn.getClass().getDeclaredField("savedStack");
        f.setAccessible(true);
        return (List<FsscriptClosure>) f.get(fn);
    }

    /**
     * 反射获取 FunctionDefExp.X 内部类的 ee 字段
     */
    private ExpEvaluator getInnerEe(Object fn) throws Exception {
        Field f = fn.getClass().getDeclaredField("ee");
        f.setAccessible(true);
        return (ExpEvaluator) f.get(fn);
    }

    @Test
    @DisplayName("Bug14: 栈冗余量化 - 三层嵌套函数的 savedStack 大小递增规律")
    void testSavedStackRedundancyGrowth() throws Exception {
        String script =
                "export function level1() { " +
                "  var a = 'L1'; " +
                "  function level2() { " +
                "    var b = 'L2'; " +
                "    function level3() { " +
                "      return a + b; " +
                "    }; " +
                "    return level3; " +
                "  }; " +
                "  return level2; " +
                "};";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        // --- level1: 直接从 export 获取 ---
        FsscriptFunction level1Fn = (FsscriptFunction) ee.getExportMap().get("level1");
        List<FsscriptClosure> ss1 = getSavedStack(level1Fn);

        System.out.println("=== 栈冗余量化 ===");
        System.out.println("level1.savedStack.size = " + ss1.size());
        // level1 在全局作用域定义, savedStack = [G], 理想=1
        Assertions.assertEquals(1, ss1.size(), "level1 定义在全局，savedStack 应只有 1 个全局闭包");

        // --- level2: 调用 level1() 得到 ---
        FsscriptFunction level2Fn = (FsscriptFunction) level1Fn.apply(new Object[0]);
        List<FsscriptClosure> ss2 = getSavedStack(level2Fn);
        ExpEvaluator ee2 = getInnerEe(level2Fn);

        System.out.println("level2.savedStack.size = " + ss2.size());
        System.out.println("level2.ee.getStack().size = " + ee2.getStack().size());
        // level2 在 level1 的 executeFunction 中定义
        // 此时 evaluator 栈 = [G(clone)] + [G(savedStack)] + [L1(new)] = 3
        // 理想: [G, L1] = 2
        System.out.println("level2.savedStack 理想值 = 2, 实际值 = " + ss2.size() +
                " → 冗余 " + (ss2.size() - 2) + " 个闭包");

        // --- level3: 调用 level2() 得到 ---
        FsscriptFunction level3Fn = (FsscriptFunction) level2Fn.apply(new Object[0]);
        List<FsscriptClosure> ss3 = getSavedStack(level3Fn);
        ExpEvaluator ee3 = getInnerEe(level3Fn);

        System.out.println("level3.savedStack.size = " + ss3.size());
        System.out.println("level3.ee.getStack().size = " + ee3.getStack().size());
        // 理想: [G, L1, L2] = 3
        System.out.println("level3.savedStack 理想值 = 3, 实际值 = " + ss3.size() +
                " → 冗余 " + (ss3.size() - 3) + " 个闭包");

        // --- 验证功能正确性 ---
        Object result = level3Fn.apply(new Object[0]);
        Assertions.assertEquals("L1L2", result, "level3 功能应正确: 返回 a+b = 'L1L2'");

        // --- 断言无冗余（修复后） ---
        // 修复前: ss2.size=3, ss3.size=5 (规律 2N-1)
        // 修复后: ss2.size=2, ss3.size=3 (理想值 = N)
        if (ss2.size() == 2 && ss3.size() == 3) {
            System.out.println("✓ 无栈冗余（已修复）");
        } else {
            System.out.println("✗ 仍有栈冗余！");
            System.out.println("  level2: 实际=" + ss2.size() + " 理想=2");
            System.out.println("  level3: 实际=" + ss3.size() + " 理想=3");
        }

        // 修复后 savedStack.size 应等于嵌套层级 N
        Assertions.assertEquals(2, ss2.size(),
                "修复后 level2.savedStack.size 应为 2（[G, L1Local]），实际=" + ss2.size());
        Assertions.assertEquals(3, ss3.size(),
                "修复后 level3.savedStack.size 应为 3（[G, L1Local, L2Local]），实际=" + ss3.size());
    }

    @Test
    @DisplayName("Bug14b: 栈冗余中重复闭包对象验证 - 同一个全局闭包在 savedStack 中出现多次")
    void testDuplicateClosureObjectsInSavedStack() throws Exception {
        String script =
                "export function outer() { " +
                "  var x = 1; " +
                "  function inner() { return x; }; " +
                "  return inner; " +
                "};";

        Exp exp = new ExpParser().compileEl(script);
        ExpEvaluator ee = DefaultExpEvaluator.newInstance(appCtx);
        exp.evalValue(ee);

        FsscriptFunction outerFn = (FsscriptFunction) ee.getExportMap().get("outer");
        FsscriptFunction innerFn = (FsscriptFunction) outerFn.apply(new Object[0]);

        List<FsscriptClosure> innerSavedStack = getSavedStack(innerFn);

        System.out.println("=== 重复闭包对象验证 ===");
        System.out.println("inner.savedStack.size = " + innerSavedStack.size());

        // 统计不同的闭包对象（用 identity 而非 equals）
        Set<Integer> identities = new HashSet<>();
        int duplicates = 0;
        for (FsscriptClosure closure : innerSavedStack) {
            if (!identities.add(System.identityHashCode(closure))) {
                duplicates++;
            }
        }

        System.out.println("不同闭包对象数 = " + identities.size());
        System.out.println("重复的闭包引用数 = " + duplicates);

        if (duplicates > 0) {
            System.out.println("✗ 同一闭包对象在 savedStack 中出现多次！");
            System.out.println("  savedStack 条目: " + innerSavedStack.size());
            System.out.println("  唯一对象: " + identities.size());
            System.out.println("  冗余条目: " + duplicates);

            // 打印每个闭包的 identity 和包含的变量
            for (int i = 0; i < innerSavedStack.size(); i++) {
                FsscriptClosure c = innerSavedStack.get(i);
                System.out.println("  [" + i + "] id=" + System.identityHashCode(c) +
                        " class=" + c.getClass().getSimpleName());
            }
        }

        // 功能验证
        Object result = innerFn.apply(new Object[0]);
        Assertions.assertEquals(1, ((Number) result).intValue(), "inner() 应返回 1");

        // 修复后: inner.savedStack 应为 [G, outerLocal] = 2 个不同对象，无重复
        Assertions.assertEquals(2, innerSavedStack.size(),
                "修复后 inner.savedStack.size 应为 2（[G, outerLocal]），实际=" + innerSavedStack.size());
        Assertions.assertEquals(0, duplicates,
                "修复后不应有重复闭包引用，实际重复数=" + duplicates);
    }

    @Test
    @DisplayName("Bug14c: 栈冗余对递归深度的影响 - 相同递归对比不同栈深度")
    void testStackRedundancyImpactOnRecursion() throws Exception {
        // 全局定义的递归函数: savedStack = [G] (size 1)
        // 修复后每次调用: savedStack [G] + local = 2 (无 ee.clone 栈冗余)
        String scriptFlat =
                "export function flatSum(n) { " +
                "  if (n <= 0) { return 0; }; " +
                "  return n + flatSum(n - 1); " +
                "};";

        // 嵌套定义的递归: savedStack 更大, 冗余更明显
        String scriptNested =
                "export function wrapper() { " +
                "  function nestedSum(n) { " +
                "    if (n <= 0) { return 0; }; " +
                "    return n + nestedSum(n - 1); " +
                "  }; " +
                "  return nestedSum; " +
                "};";

        // --- flat version ---
        Exp expFlat = new ExpParser().compileEl(scriptFlat);
        ExpEvaluator eeFlat = DefaultExpEvaluator.newInstance(appCtx);
        expFlat.evalValue(eeFlat);
        FsscriptFunction flatSum = (FsscriptFunction) eeFlat.getExportMap().get("flatSum");
        List<FsscriptClosure> flatSavedStack = getSavedStack(flatSum);

        // --- nested version ---
        Exp expNested = new ExpParser().compileEl(scriptNested);
        ExpEvaluator eeNested = DefaultExpEvaluator.newInstance(appCtx);
        expNested.evalValue(eeNested);
        FsscriptFunction wrapper = (FsscriptFunction) eeNested.getExportMap().get("wrapper");
        FsscriptFunction nestedSum = (FsscriptFunction) wrapper.apply(new Object[0]);
        List<FsscriptClosure> nestedSavedStack = getSavedStack(nestedSum);

        System.out.println("=== 栈冗余对递归深度的影响 ===");
        System.out.println("flatSum.savedStack.size = " + flatSavedStack.size());
        System.out.println("nestedSum.savedStack.size = " + nestedSavedStack.size());

        // 每次递归调用的栈增量 = savedStack.size + 1 (new closure)
        // flatSum: 增量 = 1 + 1 = 2 per call
        // nestedSum: 增量 = nestedSavedStack.size + 1 per call
        // 相同递归次数, nestedSum 消耗更多栈空间
        int flatOverheadPerCall = flatSavedStack.size() + 1;
        int nestedOverheadPerCall = nestedSavedStack.size() + 1;
        System.out.println("flatSum 每次递归栈增量 = " + flatOverheadPerCall);
        System.out.println("nestedSum 每次递归栈增量 = " + nestedOverheadPerCall);
        System.out.println("嵌套版本每次递归额外开销 = +" + (nestedOverheadPerCall - flatOverheadPerCall) + " 个闭包");

        // 验证功能正确性
        Object flatResult = flatSum.apply(new Object[]{50});
        Object nestedResult = nestedSum.apply(new Object[]{50});
        Assertions.assertEquals(1275, ((Number) flatResult).intValue(), "flatSum(50)=1275");
        Assertions.assertEquals(1275, ((Number) nestedResult).intValue(), "nestedSum(50)=1275");

        // 修复后: flatSum.savedStack = [G] = 1, nestedSum.savedStack = [G, wrapperLocal] = 2
        // 嵌套版本仍然比全局版本大，但没有冗余
        Assertions.assertEquals(1, flatSavedStack.size(),
                "修复后 flatSum.savedStack.size 应为 1（仅全局闭包）");
        Assertions.assertEquals(2, nestedSavedStack.size(),
                "修复后 nestedSum.savedStack.size 应为 2（[G, wrapperLocal]）");
        // 修复前 nestedOverheadPerCall=4, 修复后=3; flatOverheadPerCall 修复前=2, 修复后=2
        Assertions.assertEquals(2, flatOverheadPerCall,
                "flat 每次递归栈增量应为 2（savedStack=1 + newClosure=1）");
        Assertions.assertEquals(3, nestedOverheadPerCall,
                "nested 每次递归栈增量应为 3（savedStack=2 + newClosure=1），无冗余");
    }
}

/*******************************************************************************
 * Copyright 2024 Foggy Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.foggyframework.fsscript.jsr223;

import com.foggyframework.fsscript.FoggyFrameworkFsscriptTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import javax.script.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSR-223 ScriptEngine 单元测试。
 */
@SpringBootTest(classes = FoggyFrameworkFsscriptTestApplication.class)
class FsscriptScriptEngineTest {

    @Resource
    ScriptEngine fsscriptEngine;

    @Resource
    ScriptEngineFactory fsscriptEngineFactory;

    // ============ 基本功能测试 ============

    @Test
    public void testBasicEval() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.put("a", 10);
        engine.put("b", 20);
        engine.eval("export let sum = a + b;");

        assertEquals(30, engine.get("sum"));
    }

    @Test
    public void testTemplateString() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.put("name", "World");
        engine.eval("export let greeting = `Hello ${name}!`;");

        assertEquals("Hello World!", engine.get("greeting"));
    }

    @Test
    public void testMultipleExports() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval("export let a = 1; export let b = 2; export let c = a + b;");

        assertEquals(1, engine.get("a"));
        assertEquals(2, engine.get("b"));
        assertEquals(3, engine.get("c"));
    }

    @Test
    public void testFunctionDefinition() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval("export function add(x, y) { return x + y; }");

        Object func = engine.get("add");
        assertNotNull(func);
    }

    // ============ Bindings 测试 ============

    @Test
    public void testCreateBindings() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        Bindings bindings = engine.createBindings();

        assertNotNull(bindings);
        assertTrue(bindings instanceof SimpleBindings);
    }

    @Test
    public void testEvalWithBindings() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        Bindings bindings = engine.createBindings();
        bindings.put("x", 100);
        bindings.put("y", 200);

        engine.eval("export let result = x + y;", bindings);

        assertEquals(300, bindings.get("result"));
    }

    // ============ Compilable 接口测试 ============

    @Test
    public void testCompile() throws ScriptException {
        Compilable compilable = (Compilable) fsscriptEngineFactory.getScriptEngine();
        CompiledScript compiled = compilable.compile("export let sum = a + b;");

        assertNotNull(compiled);
    }

    @Test
    public void testCompiledScriptExecution() throws ScriptException {
        Compilable compilable = (Compilable) fsscriptEngineFactory.getScriptEngine();
        CompiledScript compiled = compilable.compile("export let sum = a + b;");

        // 第一次执行
        Bindings bindings1 = compiled.getEngine().createBindings();
        bindings1.put("a", 10);
        bindings1.put("b", 20);
        compiled.eval(bindings1);
        assertEquals(30, bindings1.get("sum"));

        // 第二次执行（复用编译结果）
        Bindings bindings2 = compiled.getEngine().createBindings();
        bindings2.put("a", 100);
        bindings2.put("b", 200);
        compiled.eval(bindings2);
        assertEquals(300, bindings2.get("sum"));
    }

    @Test
    public void testCompiledScriptWithFunction() throws ScriptException {
        Compilable compilable = (Compilable) fsscriptEngineFactory.getScriptEngine();
        CompiledScript compiled = compilable.compile(
                "export function multiply(x, y) { return x * y; }" +
                "export let result = multiply(a, b);"
        );

        Bindings bindings = compiled.getEngine().createBindings();
        bindings.put("a", 5);
        bindings.put("b", 6);
        compiled.eval(bindings);

        // FSScript 返回 Double 类型
        assertEquals(30.0, bindings.get("result"));
    }

    // ============ Invocable 接口测试 ============

    @Test
    public void testInvokeFunction() throws ScriptException, NoSuchMethodException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval("export function add(a, b) { return a + b; }");

        Invocable invocable = (Invocable) engine;
        Object result = invocable.invokeFunction("add", 10, 20);

        assertEquals(30, result);
    }

    @Test
    public void testInvokeFunctionWithNoArgs() throws ScriptException, NoSuchMethodException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval("export function getMessage() { return 'Hello'; }");

        Invocable invocable = (Invocable) engine;
        Object result = invocable.invokeFunction("getMessage");

        assertEquals("Hello", result);
    }

    @Test
    void testInvokeFunctionNotFound() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval("export let x = 1;");

        Invocable invocable = (Invocable) engine;
        assertThrows(NoSuchMethodException.class, () -> {
            invocable.invokeFunction("nonExistentFunction");
        });
    }

    @Test
    public void testInvokeMethod() throws ScriptException, NoSuchMethodException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        Invocable invocable = (Invocable) engine;

        // 测试调用 String 对象的方法
        String str = "hello world";
        Object result = invocable.invokeMethod(str, "toUpperCase");

        assertEquals("HELLO WORLD", result);
    }

    @Test
    public void testInvokeMethodWithArgs() throws ScriptException, NoSuchMethodException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        Invocable invocable = (Invocable) engine;

        String str = "hello";
        Object result = invocable.invokeMethod(str, "concat", " world");

        assertEquals("hello world", result);
    }

    // ============ getInterface 测试 ============

    /**
     * 测试用接口 - 使用 Number 以兼容 FSScript 返回的 Double
     */
    public interface Calculator {
        Number add(Number a, Number b);
        Number multiply(Number a, Number b);
    }

    @Test
    public void testGetInterface() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval(
                "export function add(a, b) { return a + b; }" +
                "export function multiply(a, b) { return a * b; }"
        );

        Invocable invocable = (Invocable) engine;
        Calculator calc = invocable.getInterface(Calculator.class);

        assertNotNull(calc);
        assertEquals(30.0, calc.add(10, 20).doubleValue());
        assertEquals(50.0, calc.multiply(5, 10).doubleValue());
    }

    /**
     * 单方法接口测试
     */
    public interface Greeter {
        String greet(String name);
    }

    @Test
    public void testGetInterfaceSingleMethod() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval("export function greet(name) { return `Hello ${name}!`; }");

        Invocable invocable = (Invocable) engine;
        Greeter greeter = invocable.getInterface(Greeter.class);

        assertNotNull(greeter);
        assertEquals("Hello World!", greeter.greet("World"));
    }

    @Test
    public void testGetInterfaceMissingFunction() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        // 只定义 add，没有定义 multiply
        engine.eval("export function add(a, b) { return a + b; }");

        Invocable invocable = (Invocable) engine;
        Calculator calc = invocable.getInterface(Calculator.class);

        // 缺少函数时应返回 null
        assertNull(calc);
    }

    // ============ Spring Bean 导入测试 ============

    @Test
    public void testImportSpringBean() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval(
                "import {test} from '@importBeanTest';" +
                "export let result = test();"
        );

        assertEquals("ok", engine.get("result"));
    }

    @Test
    public void testImportSpringBeanWithArgs() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.eval(
                "import {test2} from '@importBeanTest';" +
                "export let result = test2('hello');"
        );

        assertEquals("hello", engine.get("result"));
    }

    // ============ ScriptEngineFactory 测试 ============

    @Test
    public void testFactoryMetadata() {
        assertEquals("Foggy FSScript", fsscriptEngineFactory.getEngineName());
        assertEquals("FSScript", fsscriptEngineFactory.getLanguageName());
        assertTrue(fsscriptEngineFactory.getNames().contains("fsscript"));
        assertTrue(fsscriptEngineFactory.getExtensions().contains("fsscript"));
        assertTrue(fsscriptEngineFactory.getMimeTypes().contains("application/x-fsscript"));
    }

    @Test
    public void testFactoryGetScriptEngine() {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();

        assertNotNull(engine);
        assertTrue(engine instanceof FsscriptScriptEngine);
        assertTrue(engine instanceof Compilable);
        assertTrue(engine instanceof Invocable);
    }

    // ============ ScriptEngineManager SPI 发现测试 ============

    @Test
    public void testScriptEngineManagerDiscovery() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("fsscript");

        // 注意：通过 SPI 发现的引擎没有 ApplicationContext
        assertNotNull(engine);
        assertTrue(engine instanceof FsscriptScriptEngine);
    }

    @Test
    public void testScriptEngineManagerByExtension() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByExtension("fsscript");

        assertNotNull(engine);
    }

    @Test
    public void testScriptEngineManagerByMimeType() {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByMimeType("application/x-fsscript");

        assertNotNull(engine);
    }

    // ============ 边界情况测试 ============

    @Test
    public void testEvalEmptyScript() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        Object result = engine.eval("");

        // 空脚本应返回 null
        assertNull(result);
    }

    @Test
    public void testEvalNullBindings() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        engine.put("x", 10);
        engine.eval("export let y = x * 2;");

        // FSScript 返回 Double 类型
        assertEquals(20.0, engine.get("y"));
    }

    @Test
    void testCompileError() {
        Compilable compilable = (Compilable) fsscriptEngineFactory.getScriptEngine();
        // 未闭合的括号会导致语法错误
        assertThrows(ScriptException.class, () -> {
            compilable.compile("export let x = (1 + 2");
        });
    }

    // ============ 复杂脚本测试 ============

    @Test
    public void testComplexScript() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        // 使用 Integer[] 而非 int[]，FSScript 需要 Object[]
        engine.put("items", new Integer[]{1, 2, 3, 4, 5});

        engine.eval(
                "let sum = 0;" +
                "for (let i = 0; i < items.length; i++) {" +
                "    sum = sum + items[i];" +
                "}" +
                "export let total = sum;"
        );

        assertEquals(15, engine.get("total"));
    }

    @Test
    public void testArrowFunction() throws ScriptException, NoSuchMethodException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        // FSScript 箭头函数需要显式 return
        engine.eval("export let double = (x) => { return x * 2; };");

        Invocable invocable = (Invocable) engine;
        Object result = invocable.invokeFunction("double", 21);

        assertEquals(42.0, result);
    }

    @Test
    public void testNestedFunction() throws ScriptException {
        ScriptEngine engine = fsscriptEngineFactory.getScriptEngine();
        // 测试闭包：内部函数可以访问外部函数的变量
        engine.eval(
                "function createCounter() {" +
                "    let count = 0;" +
                "    return function() {" +
                "        count = count + 1;" +
                "        return count;" +
                "    };" +
                "}" +
                "export let counter = createCounter();" +
                "export let result1 = counter();" +
                "export let result2 = counter();"
        );

        // FSScript 闭包中的整数运算返回 Integer
        assertEquals(1, engine.get("result1"));
        assertEquals(2, engine.get("result2"));
    }
}

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
 *******************************************************************************/
package com.foggyframework.fsscript.jsr223;

import com.foggyframework.fsscript.DefaultExpEvaluator;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinition;
import com.foggyframework.fsscript.closure.SimpleFsscriptClosureDefinitionSpace;
import com.foggyframework.fsscript.exp.FsscriptFunction;
import com.foggyframework.fsscript.parser.spi.*;
import com.foggyframework.fsscript.support.FsscriptImpl;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.springframework.context.ApplicationContext;

import javax.script.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * JSR-223 ScriptEngine 实现，提供 FSScript 脚本执行能力。
 * <p>
 * 支持的特性：
 * <ul>
 *   <li>标准 eval() 方法执行脚本</li>
 *   <li>Compilable 接口预编译脚本</li>
 *   <li>Invocable 接口调用导出函数</li>
 *   <li>Bindings 变量绑定</li>
 *   <li>Spring Bean 导入（需要 ApplicationContext）</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * ScriptEngine engine = new FsscriptEngineFactory(appCtx).getScriptEngine();
 * engine.put("name", "World");
 * engine.eval("export let greeting = `Hello ${name}!`;");
 * String greeting = (String) engine.get("greeting");
 * </pre>
 *
 * @author foggy
 * @since 8.0.0
 */
public class FsscriptScriptEngine extends AbstractScriptEngine implements Compilable, Invocable {

    /**
     * ScriptContext 属性键：ApplicationContext
     */
    public static final String ATTR_APPLICATION_CONTEXT = "applicationContext";

    private final ScriptEngineFactory factory;
    private final ApplicationContext factoryApplicationContext;

    /**
     * 创建 FsscriptScriptEngine 实例。
     *
     * @param factory              创建此引擎的工厂
     * @param applicationContext   Spring ApplicationContext，可为 null
     */
    public FsscriptScriptEngine(ScriptEngineFactory factory, ApplicationContext applicationContext) {
        this.factory = factory;
        this.factoryApplicationContext = applicationContext;
    }

    /**
     * 获取当前有效的 ApplicationContext。
     * <p>
     * 优先级：
     * <ol>
     *   <li>ScriptContext 属性中的 applicationContext</li>
     *   <li>Factory 构造时传入的 applicationContext</li>
     * </ol>
     *
     * @param context 脚本上下文
     * @return ApplicationContext，可能为 null
     */
    protected ApplicationContext getApplicationContext(ScriptContext context) {
        // 1. 优先从 ScriptContext 属性获取
        if (context != null) {
            Object ctxAttr = context.getAttribute(ATTR_APPLICATION_CONTEXT, ScriptContext.ENGINE_SCOPE);
            if (ctxAttr instanceof ApplicationContext) {
                return (ApplicationContext) ctxAttr;
            }
        }
        // 2. 使用 Factory 注入的
        return factoryApplicationContext;
    }

    /**
     * 获取 Factory 注入的 ApplicationContext。
     *
     * @return ApplicationContext，可能为 null
     */
    public ApplicationContext getApplicationContext() {
        return factoryApplicationContext;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
        try {
            ApplicationContext appCtx = getApplicationContext(context);

            // 1. 创建闭包定义空间
            SimpleFsscriptClosureDefinitionSpace space = new SimpleFsscriptClosureDefinitionSpace();
            FsscriptClosureDefinition def = space.newFsscriptClosureDefinition();

            // 2. 编译脚本
            Exp exp = ExpUtils.compileEl(def, script, null);
            if (exp == null) {
                return null;
            }
            Fsscript fsscript = new FsscriptImpl(def, exp);

            // 3. 创建执行环境
            ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(appCtx,
                    def.newFoggyClosure());

            // 4. 从 Bindings 绑定变量
            Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
            if (bindings != null) {
                for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                    evaluator.setVar(entry.getKey(), entry.getValue());
                }
            }

            // 5. 执行脚本
            Object result = fsscript.evalResult(evaluator);

            // 6. 将导出变量写回 Bindings
            if (bindings != null) {
                Map<String, Object> exportMap = evaluator.getExportMap();
                if (exportMap != null) {
                    for (Map.Entry<String, Object> entry : exportMap.entrySet()) {
                        bindings.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            return result;

        } catch (CompileException e) {
            throw new ScriptException(e.getMessage());
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
        try {
            String script = readFully(reader);
            return eval(script, context);
        } catch (IOException e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public Bindings createBindings() {
        return new SimpleBindings();
    }

    @Override
    public ScriptEngineFactory getFactory() {
        return factory;
    }

    // ============ Compilable 接口实现 ============

    @Override
    public CompiledScript compile(String script) throws ScriptException {
        try {
            SimpleFsscriptClosureDefinitionSpace space = new SimpleFsscriptClosureDefinitionSpace();
            FsscriptClosureDefinition def = space.newFsscriptClosureDefinition();

            Exp exp = ExpUtils.compileEl(def, script, null);
            if (exp == null) {
                throw new ScriptException("Failed to compile script: empty result");
            }
            Fsscript fsscript = new FsscriptImpl(def, exp);

            return new FsscriptCompiledScript(this, fsscript, def);

        } catch (CompileException e) {
            throw new ScriptException(e.getMessage());
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public CompiledScript compile(Reader reader) throws ScriptException {
        try {
            String script = readFully(reader);
            return compile(script);
        } catch (IOException e) {
            throw new ScriptException(e);
        }
    }

    // ============ Invocable 接口实现 ============

    @Override
    public Object invokeMethod(Object thiz, String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        if (thiz == null) {
            throw new IllegalArgumentException("Object 'thiz' cannot be null");
        }

        // 1. 如果对象是 Map，尝试获取函数
        if (thiz instanceof Map) {
            Object func = ((Map<?, ?>) thiz).get(name);
            if (func instanceof FsscriptFunction) {
                try {
                    return ((FsscriptFunction) func).apply(args);
                } catch (Exception e) {
                    throw new ScriptException(e);
                }
            }
        }

        // 2. 使用反射调用对象方法
        try {
            Class<?>[] argTypes = null;
            if (args != null && args.length > 0) {
                argTypes = new Class<?>[args.length];
                for (int i = 0; i < args.length; i++) {
                    argTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
                }
            }

            Method method = findMethod(thiz.getClass(), name, argTypes);
            if (method == null) {
                throw new NoSuchMethodException("Method not found: " + name + " on " + thiz.getClass().getName());
            }

            method.setAccessible(true);
            return method.invoke(thiz, args);

        } catch (NoSuchMethodException e) {
            throw e;
        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    /**
     * 查找方法，支持参数类型的兼容匹配。
     */
    private Method findMethod(Class<?> clazz, String name, Class<?>[] argTypes) {
        // 先尝试精确匹配
        try {
            if (argTypes == null || argTypes.length == 0) {
                return clazz.getMethod(name);
            }
            return clazz.getMethod(name, argTypes);
        } catch (NoSuchMethodException e) {
            // 精确匹配失败，尝试兼容匹配
        }

        // 遍历所有方法寻找兼容的
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            Class<?>[] paramTypes = method.getParameterTypes();
            if (argTypes == null && paramTypes.length == 0) {
                return method;
            }
            if (argTypes != null && paramTypes.length == argTypes.length) {
                boolean compatible = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    if (argTypes[i] != null && !isAssignable(paramTypes[i], argTypes[i])) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) {
                    return method;
                }
            }
        }
        return null;
    }

    /**
     * 判断参数类型是否兼容。
     */
    private boolean isAssignable(Class<?> paramType, Class<?> argType) {
        if (paramType.isAssignableFrom(argType)) {
            return true;
        }
        // 处理基本类型与包装类型的兼容
        if (paramType.isPrimitive()) {
            if (paramType == int.class && argType == Integer.class) return true;
            if (paramType == long.class && argType == Long.class) return true;
            if (paramType == double.class && argType == Double.class) return true;
            if (paramType == float.class && argType == Float.class) return true;
            if (paramType == boolean.class && argType == Boolean.class) return true;
            if (paramType == byte.class && argType == Byte.class) return true;
            if (paramType == short.class && argType == Short.class) return true;
            if (paramType == char.class && argType == Character.class) return true;
        }
        return false;
    }

    @Override
    public Object invokeFunction(String name, Object... args)
            throws ScriptException, NoSuchMethodException {
        Bindings bindings = getBindings(ScriptContext.ENGINE_SCOPE);
        if (bindings == null) {
            throw new NoSuchMethodException("No bindings available, function not found: " + name);
        }

        Object func = bindings.get(name);
        if (func == null) {
            throw new NoSuchMethodException("Function not found: " + name);
        }

        if (func instanceof FsscriptFunction) {
            try {
                return ((FsscriptFunction) func).apply(args);
            } catch (Exception e) {
                throw new ScriptException(e);
            }
        }

        throw new NoSuchMethodException(
                "Object '" + name + "' is not a function: " + func.getClass().getName());
    }

    @Override
    public <T> T getInterface(Class<T> clasz) {
        if (clasz == null || !clasz.isInterface()) {
            throw new IllegalArgumentException("Class must be a non-null interface");
        }

        Bindings bindings = getBindings(ScriptContext.ENGINE_SCOPE);
        if (bindings == null) {
            return null;
        }

        // 检查所有接口方法是否都有对应的函数
        for (Method method : clasz.getMethods()) {
            // 跳过 Object 的默认方法
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }
            // 跳过 default 方法
            if (method.isDefault()) {
                continue;
            }
            Object func = bindings.get(method.getName());
            if (!(func instanceof FsscriptFunction)) {
                return null;  // 缺少对应的函数，返回 null
            }
        }

        // 创建动态代理
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
                clasz.getClassLoader(),
                new Class<?>[]{clasz},
                new FsscriptInterfaceHandler(bindings)
        );
        return proxy;
    }

    @Override
    public <T> T getInterface(Object thiz, Class<T> clasz) {
        if (thiz == null) {
            throw new IllegalArgumentException("Object 'thiz' cannot be null");
        }
        if (clasz == null || !clasz.isInterface()) {
            throw new IllegalArgumentException("Class must be a non-null interface");
        }

        // 创建动态代理，委托给对象方法
        @SuppressWarnings("unchecked")
        T proxy = (T) Proxy.newProxyInstance(
                clasz.getClassLoader(),
                new Class<?>[]{clasz},
                new ObjectInterfaceHandler(thiz, this)
        );
        return proxy;
    }

    /**
     * 基于 Bindings 的接口代理处理器。
     * 将接口方法调用委托给 Bindings 中同名的 FsscriptFunction。
     */
    private static class FsscriptInterfaceHandler implements InvocationHandler {
        private final Bindings bindings;

        FsscriptInterfaceHandler(Bindings bindings) {
            this.bindings = bindings;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            // 处理 Object 的方法
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(name)) {
                    return "FsscriptProxy[" + bindings + "]";
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name)) {
                    return proxy == args[0];
                }
                return method.invoke(this, args);
            }

            // 处理 default 方法
            if (method.isDefault()) {
                // Java 8+ 调用 default 方法
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            // 调用 FsscriptFunction
            Object func = bindings.get(name);
            if (func instanceof FsscriptFunction) {
                return ((FsscriptFunction) func).apply(args != null ? args : new Object[0]);
            }

            throw new NoSuchMethodException("Function not found in bindings: " + name);
        }
    }

    /**
     * 基于对象的接口代理处理器。
     * 将接口方法调用委托给对象的同名方法。
     */
    private static class ObjectInterfaceHandler implements InvocationHandler {
        private final Object target;
        private final FsscriptScriptEngine engine;

        ObjectInterfaceHandler(Object target, FsscriptScriptEngine engine) {
            this.target = target;
            this.engine = engine;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();

            // 处理 Object 的方法
            if (method.getDeclaringClass() == Object.class) {
                if ("toString".equals(name)) {
                    return "FsscriptProxy[" + target + "]";
                }
                if ("hashCode".equals(name)) {
                    return System.identityHashCode(proxy);
                }
                if ("equals".equals(name)) {
                    return proxy == args[0];
                }
                return method.invoke(target, args);
            }

            // 处理 default 方法
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }

            // 委托给 invokeMethod
            try {
                return engine.invokeMethod(target, name, args != null ? args : new Object[0]);
            } catch (ScriptException e) {
                throw e.getCause() != null ? e.getCause() : e;
            }
        }
    }

    // ============ 工具方法 ============

    /**
     * 从 Reader 读取全部内容。
     */
    private String readFully(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader buffered = (reader instanceof BufferedReader)
                ? (BufferedReader) reader
                : new BufferedReader(reader);

        char[] buffer = new char[8192];
        int read;
        while ((read = buffered.read(buffer)) != -1) {
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }
}

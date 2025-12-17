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
import com.foggyframework.fsscript.parser.spi.ExpEvaluator;
import com.foggyframework.fsscript.parser.spi.Fsscript;
import com.foggyframework.fsscript.parser.spi.FsscriptClosureDefinition;
import org.springframework.context.ApplicationContext;

import javax.script.*;
import java.util.Map;

/**
 * JSR-223 CompiledScript 实现，表示预编译的 FSScript 脚本。
 * <p>
 * 预编译脚本可以重复执行，避免重复解析开销：
 * <pre>
 * Compilable compilable = (Compilable) engine;
 * CompiledScript compiled = compilable.compile("export let sum = a + b;");
 *
 * // 可以多次执行，每次使用不同的参数
 * Bindings bindings1 = engine.createBindings();
 * bindings1.put("a", 1);
 * bindings1.put("b", 2);
 * compiled.eval(bindings1);  // sum = 3
 *
 * Bindings bindings2 = engine.createBindings();
 * bindings2.put("a", 10);
 * bindings2.put("b", 20);
 * compiled.eval(bindings2);  // sum = 30
 * </pre>
 *
 * @author foggy
 * @since 8.0.0
 */
public class FsscriptCompiledScript extends CompiledScript {

    private final FsscriptScriptEngine engine;
    private final Fsscript fsscript;
    private final FsscriptClosureDefinition closureDefinition;

    /**
     * 创建 FsscriptCompiledScript 实例。
     *
     * @param engine            关联的脚本引擎
     * @param fsscript          编译后的脚本对象
     * @param closureDefinition 闭包定义
     */
    public FsscriptCompiledScript(FsscriptScriptEngine engine, Fsscript fsscript,
                                  FsscriptClosureDefinition closureDefinition) {
        this.engine = engine;
        this.fsscript = fsscript;
        this.closureDefinition = closureDefinition;
    }

    @Override
    public Object eval(ScriptContext context) throws ScriptException {
        try {
            // 1. 获取 ApplicationContext
            ApplicationContext appCtx = engine.getApplicationContext(context);

            // 2. 创建新的执行环境（每次 eval 都创建新实例，保证线程安全）
            ExpEvaluator evaluator = DefaultExpEvaluator.newInstance(appCtx,
                    closureDefinition.newFoggyClosure());

            // 3. 从 Bindings 绑定变量
            Bindings bindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
            if (bindings != null) {
                for (Map.Entry<String, Object> entry : bindings.entrySet()) {
                    evaluator.setVar(entry.getKey(), entry.getValue());
                }
            }

            // 4. 执行脚本
            Object result = fsscript.evalResult(evaluator);

            // 5. 将导出变量写回 Bindings
            if (bindings != null) {
                Map<String, Object> exportMap = evaluator.getExportMap();
                if (exportMap != null) {
                    for (Map.Entry<String, Object> entry : exportMap.entrySet()) {
                        bindings.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            return result;

        } catch (Exception e) {
            throw new ScriptException(e);
        }
    }

    @Override
    public ScriptEngine getEngine() {
        return engine;
    }

    /**
     * 获取编译后的 Fsscript 对象。
     *
     * @return Fsscript 实例
     */
    public Fsscript getFsscript() {
        return fsscript;
    }
}

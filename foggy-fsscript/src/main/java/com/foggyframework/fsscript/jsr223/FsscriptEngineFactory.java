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

import org.springframework.context.ApplicationContext;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JSR-223 ScriptEngineFactory 实现，用于创建 FSScript 脚本引擎。
 * <p>
 * 支持通过标准 ScriptEngineManager 发现和使用：
 * <pre>
 * ScriptEngineManager manager = new ScriptEngineManager();
 * ScriptEngine engine = manager.getEngineByName("fsscript");
 * </pre>
 * <p>
 * 在 Spring 环境中，推荐使用带 ApplicationContext 的构造函数，以支持 @bean 导入：
 * <pre>
 * &#64;Bean
 * public ScriptEngine fsscriptEngine(ApplicationContext ctx) {
 *     return new FsscriptEngineFactory(ctx).getScriptEngine();
 * }
 * </pre>
 *
 * @author foggy
 * @since 8.0.0
 */
public class FsscriptEngineFactory implements ScriptEngineFactory {

    private static final String ENGINE_NAME = "Foggy FSScript";
    private static final String ENGINE_VERSION = "8.0.0";
    private static final String LANGUAGE_NAME = "FSScript";
    private static final String LANGUAGE_VERSION = "1.0";

    private static final List<String> NAMES = Collections.unmodifiableList(
            Arrays.asList("fsscript", "FSScript", "FScript", "fscript")
    );

    private static final List<String> EXTENSIONS = Collections.unmodifiableList(
            Arrays.asList("fsscript", "ftxt")
    );

    private static final List<String> MIME_TYPES = Collections.unmodifiableList(
            Arrays.asList("application/x-fsscript", "text/x-fsscript")
    );

    private final ApplicationContext applicationContext;

    /**
     * 无参构造函数，供 SPI 服务发现使用。
     * <p>
     * 通过此构造函数创建的引擎不支持 import '@bean' 语法。
     */
    public FsscriptEngineFactory() {
        this(null);
    }

    /**
     * 带 ApplicationContext 的构造函数，用于 Spring 环境。
     * <p>
     * 通过此构造函数创建的引擎完整支持所有 FSScript 特性，
     * 包括 import '@bean' 导入 Spring Bean。
     *
     * @param applicationContext Spring 应用上下文，可为 null
     */
    public FsscriptEngineFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public String getEngineName() {
        return ENGINE_NAME;
    }

    @Override
    public String getEngineVersion() {
        return ENGINE_VERSION;
    }

    @Override
    public List<String> getExtensions() {
        return EXTENSIONS;
    }

    @Override
    public List<String> getMimeTypes() {
        return MIME_TYPES;
    }

    @Override
    public List<String> getNames() {
        return NAMES;
    }

    @Override
    public String getLanguageName() {
        return LANGUAGE_NAME;
    }

    @Override
    public String getLanguageVersion() {
        return LANGUAGE_VERSION;
    }

    @Override
    public Object getParameter(String key) {
        switch (key) {
            case ScriptEngine.ENGINE:
                return ENGINE_NAME;
            case ScriptEngine.ENGINE_VERSION:
                return ENGINE_VERSION;
            case ScriptEngine.LANGUAGE:
                return LANGUAGE_NAME;
            case ScriptEngine.LANGUAGE_VERSION:
                return LANGUAGE_VERSION;
            case ScriptEngine.NAME:
                return NAMES.get(0);
            case "THREADING":
                // 每次 eval 创建独立的 ExpEvaluator，线程安全
                return "MULTITHREADED";
            default:
                return null;
        }
    }

    @Override
    public String getMethodCallSyntax(String obj, String method, String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(obj).append(".").append(method).append("(");
        if (args != null && args.length > 0) {
            sb.append(String.join(", ", args));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public String getOutputStatement(String toDisplay) {
        // FSScript 使用 console.log 或 debug 函数输出
        return "debug(" + toDisplay + ")";
    }

    @Override
    public String getProgram(String... statements) {
        if (statements == null || statements.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String statement : statements) {
            sb.append(statement);
            if (!statement.endsWith(";") && !statement.endsWith("}")) {
                sb.append(";");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Override
    public ScriptEngine getScriptEngine() {
        return new FsscriptScriptEngine(this, applicationContext);
    }

    /**
     * 获取此工厂关联的 ApplicationContext。
     *
     * @return ApplicationContext，可能为 null
     */
    public ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}

package com.foggyframework.fsscript;

import com.foggyframework.fsscript.exp.DefaultExpFactory;
import com.foggyframework.fsscript.exp.FunTable;
import com.foggyframework.fsscript.jsr223.FsscriptEngineFactory;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileTxtFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import com.foggyframework.fsscript.loadder.RootFsscriptLoader;
import com.foggyframework.fsscript.parser.spi.ExpFactory;
import com.foggyframework.fsscript.spring.cloud.GetFunDef;
import com.foggyframework.fsscript.spring.cloud.PostFunDef;
import com.foggyframework.fsscript.utils.ExpUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.client.RestTemplate;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;


@Configuration
public class FoggyFscriptAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name="fsscriptFunTable")
    public FunTable fsscriptFunTable(DefaultExpFactory fsscriptExpFactory) {
        return (FunTable) fsscriptExpFactory.getFunctionSet();
    }

    @Bean
    @ConditionalOnMissingBean(name="fsscriptFileChangeHandler")
    public FsscriptFileChangeHandler fsscriptFileChangeHandler(RootFsscriptLoader rootFsscriptLoader) {

        return new FsscriptFileChangeHandler(rootFsscriptLoader);
    }

    @Bean
    @ConditionalOnMissingBean(name="rootFsscriptLoader")
    public RootFsscriptLoader rootFsscriptLoader(ApplicationContext appCtx) {
        return new RootFsscriptLoader(appCtx);
    }

    @Bean
    @ConditionalOnClass(RestTemplate.class)
    public PostFunDef postFunDef(FunTable funTable) {
        PostFunDef postFunDef = new PostFunDef();
        funTable.append(postFunDef);
        return postFunDef;
    }

    @Bean
    @ConditionalOnClass(RestTemplate.class)
    public GetFunDef getFunDef(FunTable funTable) {
        GetFunDef getFunDef = new GetFunDef();
        funTable.append(getFunDef);
        return getFunDef;
    }

    @Bean
    @ConditionalOnMissingBean(name="fileFsscriptLoader")
    public FileFsscriptLoader fileFsscriptLoader(ApplicationContext appCtx, RootFsscriptLoader rootFsscriptLoader, FsscriptFileChangeHandler fsscriptFileChangeHandler) {
        FileFsscriptLoader fileFsscriptLoader = new FileFsscriptLoader(appCtx, rootFsscriptLoader, fsscriptFileChangeHandler);
        FileFsscriptLoader.setInstance(fileFsscriptLoader);
        return fileFsscriptLoader;
    }

    @Bean
    @ConditionalOnMissingBean(name="fileTxtFsscriptLoader")
    public FileTxtFsscriptLoader fileTxtFsscriptLoader(ApplicationContext appCtx, RootFsscriptLoader rootFsscriptLoader, FsscriptFileChangeHandler fsscriptFileChangeHandler) {
        FileTxtFsscriptLoader fileTxtFsscriptLoader = new FileTxtFsscriptLoader(appCtx, rootFsscriptLoader, fsscriptFileChangeHandler);
        FileTxtFsscriptLoader.setInstance(fileTxtFsscriptLoader);
        return fileTxtFsscriptLoader;
    }


//    @Bean
//    @ConditionalOnMissingBean
//    public DefaultExpFactory defaultExpFactory(FunTable funTable) {
//        return new DefaultExpFactory();
//    }

    @Bean
    public ExpUtils expUtils() {
        return new ExpUtils();
    }

    @Bean
   @ConfigurationProperties(prefix = "foggy.fsscript")
    public DefaultExpFactory fsscriptExpFactory(){
        return DefaultExpFactory.DEFAULT;
    }

    // ============ JSR-223 ScriptEngine 支持 ============

    /**
     * 创建 FSScript 脚本引擎工厂。
     * <p>
     * 自动注入 ApplicationContext，使引擎支持 import '@bean' 语法。
     */
    @Bean
    @ConditionalOnMissingBean(name = "fsscriptEngineFactory")
    public ScriptEngineFactory fsscriptEngineFactory(ApplicationContext applicationContext) {
        return new FsscriptEngineFactory(applicationContext);
    }

    /**
     * 创建 FSScript 脚本引擎实例。
     * <p>
     * 可直接注入使用：
     * <pre>
     * &#64;Resource
     * private ScriptEngine fsscriptEngine;
     *
     * public void example() {
     *     fsscriptEngine.put("name", "World");
     *     fsscriptEngine.eval("export let greeting = `Hello ${name}!`;");
     * }
     * </pre>
     */
    @Bean
    @ConditionalOnMissingBean(name = "fsscriptEngine")
    public ScriptEngine fsscriptEngine(ScriptEngineFactory fsscriptEngineFactory) {
        return fsscriptEngineFactory.getScriptEngine();
    }
}

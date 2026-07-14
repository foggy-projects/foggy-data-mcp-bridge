package com.foggyframework.dataset.vector;

import com.foggyframework.dataset.vector.funs.VectorFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * 向量数据库自动配置
 */
@Slf4j
@AutoConfiguration(afterName = {
        "com.foggyframework.fsscript.FoggyFscriptAutoConfiguration",
        "org.springframework.ai.autoconfigure.vectorstore.milvus.MilvusVectorStoreAutoConfiguration",
        "org.springframework.ai.vectorstore.milvus.autoconfigure.MilvusVectorStoreAutoConfiguration"
})
@ConditionalOnClass(name = "org.springframework.ai.vectorstore.VectorStore")
@ConditionalOnProperty(prefix = "foggy.vector", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(
        value = {FileFsscriptLoader.class, FsscriptFileChangeHandler.class},
        type = "org.springframework.ai.vectorstore.VectorStore")
public class DataSetVectorAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.vectorstore", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(VectorFileFsscriptLoader.class)
    public VectorFileFsscriptLoader vectorFileFsscriptLoader(
            ApplicationContext applicationContext,
            FileFsscriptLoader fsscriptLoader,
            FsscriptFileChangeHandler changeHandler) {
        log.info("Initializing VectorFileFsscriptLoader");
        return new VectorFileFsscriptLoader(applicationContext, fsscriptLoader, changeHandler);
    }
}

package com.foggyframework.dataset.vector;

import com.foggyframework.dataset.vector.funs.VectorFileFsscriptLoader;
import com.foggyframework.fsscript.loadder.FsscriptFileChangeHandler;
import com.foggyframework.fsscript.loadder.FsscriptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量数据库自动配置
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.vectorstore", name = "enabled", havingValue = "true")
@ConditionalOnBean(VectorStore.class)
@RequiredArgsConstructor
public class DataSetVectorAutoConfiguration {

    private final ApplicationContext applicationContext;
    private final FsscriptLoader fsscriptLoader;

    @Bean
    public VectorFileFsscriptLoader vectorFileFsscriptLoader() {
        log.info("Initializing VectorFileFsscriptLoader");
        FsscriptFileChangeHandler changeHandler = applicationContext.getBean(FsscriptFileChangeHandler.class);
        return new VectorFileFsscriptLoader(applicationContext, fsscriptLoader, changeHandler);
    }
}

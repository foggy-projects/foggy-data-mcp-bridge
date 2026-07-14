package com.foggyframework.dataset.db.model.vector;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.db.model.DbModelAutoConfiguration;
import com.foggyframework.dataset.db.model.impl.vector.TmVectorModelLoaderImpl;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = DbModelAutoConfiguration.class)
@ConditionalOnClass(name = {
        "io.milvus.v2.client.MilvusClientV2",
        "org.springframework.web.reactive.function.client.WebClient"
})
@ConditionalOnProperty(prefix = "foggy.vector", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean({SystemBundlesContext.class, FileFsscriptLoader.class})
public class VectorModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TmVectorModelLoaderImpl.class)
    public TmVectorModelLoaderImpl tmVectorModelLoader(
            SystemBundlesContext systemBundlesContext,
            FileFsscriptLoader fileFsscriptLoader) {
        return new TmVectorModelLoaderImpl(systemBundlesContext, fileFsscriptLoader);
    }
}

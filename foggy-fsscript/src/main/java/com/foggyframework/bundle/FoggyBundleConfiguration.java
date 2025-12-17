package com.foggyframework.bundle;

import com.foggyframework.bundle.external.ExternalBundleLoader;
import com.foggyframework.bundle.external.ExternalBundleProperties;
import com.foggyframework.bundle.fsscript.BundleFsscriptLoader;
import com.foggyframework.bundle.loader.ClassBundleLoader;
import com.foggyframework.core.bundle.BundleDefinition;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(ExternalBundleProperties.class)
public class FoggyBundleConfiguration {

    @Autowired(required = false)
    private ExternalBundleProperties externalBundleProperties;

    @Bean
    public ClassBundleLoader classBundleLoader(List<BundleDefinition> bundleDefinitions) {
        return new ClassBundleLoader(bundleDefinitions);
    }

    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public SystemBundlesContextImpl systemBundlesContext(ClassBundleLoader classBundleLoader) {
        List loaders = new ArrayList<>();
        loaders.add(classBundleLoader);

        // 添加外部Bundle加载器（如果配置了）
        ExternalBundleLoader externalLoader = ExternalBundleLoader.fromProperties(externalBundleProperties);
        if (externalLoader != null) {
            loaders.add(externalLoader);
        }

        return new SystemBundlesContextImpl(loaders);
    }

    @Bean
    public BundleFsscriptLoader bundleFsscriptLoader(FileFsscriptLoader fileFsscriptLoader) {
        return new BundleFsscriptLoader(fileFsscriptLoader);
    }
}

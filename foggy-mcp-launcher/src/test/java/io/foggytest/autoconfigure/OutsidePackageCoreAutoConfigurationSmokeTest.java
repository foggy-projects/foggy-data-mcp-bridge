package io.foggytest.autoconfigure;

import com.foggyframework.dataset.model.service.AdvancedQueryFacade;
import com.foggyframework.dataset.fsscript.DataSetFsscriptUtils;
import com.foggyframework.dataset.utils.DataSourceFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class OutsidePackageCoreAutoConfigurationSmokeTest {

    private static final String[] EXCLUDED_AUTO_CONFIGURATIONS = {
            "com.foggyframework.dataset.model.DbModelAutoConfiguration",
            "com.foggyframework.dataset.mcp.DatasetMcpAutoConfiguration",
            "com.foggyframework.dataset.model.demo.JdbcModelDemoAutoConfiguration",
            "com.foggyframework.dataset.model.memorygrid.bridge.MemoryGridBridgeConfiguration",
            "com.foggyframework.odoo.bridge.OdooBridgeAutoConfiguration",
            "com.foggyframework.dataviewer.config.DataViewerAutoConfiguration",
            "com.foggyframework.dataset.mcp.storage.cloud.CloudStorageAutoConfiguration",
            "com.foggyframework.dataset.mongo.DataSetMongoAutoConfiguration",
            "com.foggyframework.dataset.model.mongo.MongoModelAutoConfiguration",
            "com.foggyframework.dataset.vector.DataSetVectorAutoConfiguration",
            "com.foggyframework.dataset.model.vector.VectorModelAutoConfiguration",
            "com.foggyframework.dataset.model.cache.config.QueryCacheAutoConfiguration",
            "com.foggyframework.dataset.model.cache.config.QueryCacheBackendProviderAutoConfiguration",
            "com.foggyframework.dataset.model.cache.config.QueryCacheEvictionAutoConfiguration",
            "com.foggyframework.dataset.model.cache.config.QueryCacheWebAutoConfiguration",
            "com.foggyframework.dataset.model.starter.ModelBackendAutoConfiguration",
            "com.foggyframework.dataset.model.web.ModelBackendWebAutoConfiguration",
            "com.foggyframework.dataset.graphql.GraphqlAddonAutoConfiguration",
            "com.foggyframework.dataset.model.preagg.config.PreAggAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration"
    };

    @Test
    void applicationOutsideFoggyRootDiscoversCoreAutoConfigurationsWithoutComponentScanning() {
        assertThat(OutsidePackageApplication.class.getPackageName())
                .doesNotStartWith("com.foggyframework");

        new ApplicationContextRunner()
                .withUserConfiguration(OutsidePackageApplication.class)
                .withPropertyValues(
                        "spring.autoconfigure.exclude=" + String.join(",", EXCLUDED_AUTO_CONFIGURATIONS),
                        "spring.ai.openai.api-key=test-key",
                        "spring.ai.model.chat=none",
                        "spring.ai.model.embedding=none",
                        "spring.ai.model.image=none",
                        "spring.ai.model.audio.transcription=none",
                        "spring.ai.model.audio.speech=none",
                        "spring.ai.model.moderation=none")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DataSetFsscriptUtils.class);
                    assertThat(context).hasSingleBean(DataSourceFactory.class);
                    assertThat(context).doesNotHaveBean(AdvancedQueryFacade.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class OutsidePackageApplication {
    }
}

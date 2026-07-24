package io.foggytest.autoconfigure.modelmongo;

import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.dataset.model.impl.mongo.TmMongoModelLoaderImpl;
import com.foggyframework.dataset.model.mongo.MongoModelAutoConfiguration;
import com.foggyframework.fsscript.loadder.FileFsscriptLoader;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MongoModelAutoConfigurationContractTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MongoModelAutoConfiguration.class))
            .withBean(SystemBundlesContext.class, () -> mock(SystemBundlesContext.class))
            .withBean(FileFsscriptLoader.class, () -> mock(FileFsscriptLoader.class))
            .withBean(DataSource.class, () -> mock(DataSource.class));

    @Test
    void loaderIsNotDiscoveredAsAnUnconditionalService() {
        assertThat(TmMongoModelLoaderImpl.class.getAnnotation(Service.class)).isNull();
    }

    @Test
    void bootThreeImportsRegistersTheModelMongoAutoConfiguration() throws IOException {
        assertThat(autoConfigurationImports())
                .contains("com.foggyframework.dataset.model.mongo.MongoModelAutoConfiguration");
    }

    @Test
    void disabledConfigurationCreatesNoLoader() {
        completeMongoContext()
                .withPropertyValues("foggy.dataset.mongo.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TmMongoModelLoaderImpl.class);
                });
    }

    @Test
    void missingMongoClassesSafelySkipAutoConfiguration() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.data.mongodb", "com.mongodb"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TmMongoModelLoaderImpl.class);
                });
    }

    @Test
    void completeConditionsCreateExactlyOneLoaderWithoutConnectingToMongo() {
        autoConfiguredDataSourceMongoContext().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataSource.class);
            assertThat(context).hasSingleBean(TmMongoModelLoaderImpl.class);
        });
    }

    @Test
    void userLoaderBacksOffDefaultLoader() {
        TmMongoModelLoaderImpl customLoader = mock(TmMongoModelLoaderImpl.class);

        completeMongoContext()
                .withBean(TmMongoModelLoaderImpl.class, () -> customLoader)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TmMongoModelLoaderImpl.class);
                    assertThat(context.getBean(TmMongoModelLoaderImpl.class)).isSameAs(customLoader);
                });
    }

    private ApplicationContextRunner completeMongoContext() {
        return contextRunner
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class))
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class));
    }

    private ApplicationContextRunner autoConfiguredDataSourceMongoContext() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        MongoModelAutoConfiguration.class,
                        DataSourceAutoConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/foggy_test",
                        "spring.datasource.username=foggy",
                        "spring.datasource.password=foggy")
                .withBean(SystemBundlesContext.class, () -> mock(SystemBundlesContext.class))
                .withBean(FileFsscriptLoader.class, () -> mock(FileFsscriptLoader.class))
                .withBean(MongoClient.class, () -> mock(MongoClient.class))
                .withBean(MongoDatabaseFactory.class, () -> mock(MongoDatabaseFactory.class))
                .withBean(MongoTemplate.class, () -> mock(MongoTemplate.class));
    }

    private static String autoConfigurationImports() throws IOException {
        var resources = TmMongoModelLoaderImpl.class.getClassLoader().getResources(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        StringBuilder content = new StringBuilder();
        for (URL resource : Collections.list(resources)) {
            content.append(new String(resource.openStream().readAllBytes(), StandardCharsets.UTF_8));
            content.append('\n');
        }
        return content.toString();
    }
}

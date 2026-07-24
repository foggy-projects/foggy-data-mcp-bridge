package io.foggytest.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationRegistrationUniquenessTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private static final List<String> AUTO_CONFIGURATIONS = List.of(
            "com.foggyframework.dataset.DataSetAutoConfiguration",
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
            "com.foggyframework.dataset.model.preagg.config.PreAggAutoConfiguration");

    @Test
    void eachAutoConfigurationHasExactlyOneBootThreeImportsEntry() throws IOException {
        List<String> registrations = loadImportRegistrations();

        for (String autoConfiguration : AUTO_CONFIGURATIONS) {
            assertThat(registrations)
                    .describedAs("Boot 3 imports registration for %s", autoConfiguration)
                    .containsOnlyOnce(autoConfiguration);
        }
    }

    @Test
    void migratedAutoConfigurationsAreAbsentFromLegacyEnableAutoConfigurationFactories() throws IOException {
        List<String> legacyRegistrations = loadLegacyEnableAutoConfigurationRegistrations();

        assertThat(legacyRegistrations).doesNotContainAnyElementsOf(AUTO_CONFIGURATIONS);
    }

    private static List<String> loadImportRegistrations() throws IOException {
        List<String> registrations = new ArrayList<>();
        var resources = classLoader().getResources(IMPORTS_RESOURCE);
        for (URL resource : Collections.list(resources)) {
            try (InputStream stream = resource.openStream()) {
                new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        .lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(registrations::add);
            }
        }
        return registrations;
    }

    private static List<String> loadLegacyEnableAutoConfigurationRegistrations() throws IOException {
        List<String> registrations = new ArrayList<>();
        var resources = classLoader().getResources("META-INF/spring.factories");
        for (URL resource : Collections.list(resources)) {
            Properties properties = new Properties();
            try (InputStream stream = resource.openStream()) {
                properties.load(stream);
            }
            String value = properties.getProperty(EnableAutoConfiguration.class.getName());
            if (value == null) {
                continue;
            }
            for (String registration : value.split(",")) {
                String trimmed = registration.trim();
                if (!trimmed.isEmpty()) {
                    registrations.add(trimmed);
                }
            }
        }
        return registrations;
    }

    private static ClassLoader classLoader() {
        return AutoConfigurationRegistrationUniquenessTest.class.getClassLoader();
    }
}

package com.foggyframework.mcp.launcher.demopreview;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoPreviewConfigurationTest {

    private final DemoPreviewConfiguration configuration = new DemoPreviewConfiguration();

    @Test
    void allowsConfiguredLoopbackPort() {
        DemoPreviewProperties properties = configuredProperties("http://127.0.0.1:18166");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "18166");

        assertDoesNotThrow(() -> configuration.demoPreviewLoopbackGuard(properties, environment)
                .afterSingletonsInstantiated());
    }

    @Test
    void rejectsUrlsUsingDifferentPort() {
        DemoPreviewProperties properties = configuredProperties("http://127.0.0.1:18172");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.address", "127.0.0.1")
                .withProperty("server.port", "18166");

        assertThrows(IllegalStateException.class, () -> configuration.demoPreviewLoopbackGuard(properties, environment)
                .afterSingletonsInstantiated());
    }

    @Test
    void rejectsNonLoopbackBinding() {
        DemoPreviewProperties properties = configuredProperties("http://127.0.0.1:18166");
        MockEnvironment environment = new MockEnvironment()
                .withProperty("server.address", "0.0.0.0")
                .withProperty("server.port", "18166");

        assertThrows(IllegalStateException.class, () -> configuration.demoPreviewLoopbackGuard(properties, environment)
                .afterSingletonsInstantiated());
    }

    private DemoPreviewProperties configuredProperties(String origin) {
        DemoPreviewProperties properties = new DemoPreviewProperties();
        properties.setInternalBaseUrl(origin);
        properties.setPublicOpenUrl(origin + "/data-viewer/open");
        properties.setUsers(Map.of("coordinator", new DemoPreviewProperties.DemoUser()));
        properties.setCredentialPrincipals(Map.of("fingerprint", "coordinator"));
        return properties;
    }
}

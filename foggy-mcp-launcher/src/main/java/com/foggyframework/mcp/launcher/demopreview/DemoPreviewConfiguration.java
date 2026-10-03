package com.foggyframework.mcp.launcher.demopreview;

import com.foggyframework.dataviewer.service.QueryCacheService;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.time.Clock;

/** Demo-only wiring. Kept in the Launcher so the reusable DataViewer has no login or IAM product UI. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoPreviewProperties.class)
@ConditionalOnProperty(prefix = "foggy.demo-preview", name = "enabled", havingValue = "true")
public class DemoPreviewConfiguration {

    @Bean
    DemoPreviewSessionService demoPreviewSessionService(DemoPreviewProperties properties,
                                                        QueryCacheService queryCacheService) {
        return new DemoPreviewSessionService(properties, queryCacheService);
    }

    @Bean
    SmartInitializingSingleton demoPreviewLoopbackGuard(DemoPreviewProperties properties, Environment environment) {
        return () -> {
            String address = environment.getProperty("server.address", "").trim();
            boolean loopback;
            try {
                loopback = !address.isEmpty() && InetAddress.getByName(address).isLoopbackAddress();
            } catch (Exception ignored) {
                loopback = false;
            }
            if (!loopback) {
                throw new IllegalStateException("foggy.demo-preview requires server.address to be loopback-only");
            }
            String expectedOrigin = "http://127.0.0.1:" + environment.getRequiredProperty("server.port");
            if (!expectedOrigin.equals(properties.getInternalBaseUrl())
                    || !properties.getPublicOpenUrl().equals(expectedOrigin + "/data-viewer/open")) {
                throw new IllegalStateException("demo preview URLs must use the configured loopback HTTP port");
            }
            if (properties.getUsers().isEmpty() || properties.getCredentialPrincipals().isEmpty()) {
                throw new IllegalStateException("demo preview requires explicitly configured service scopes and credential fingerprints");
            }
        };
    }
}

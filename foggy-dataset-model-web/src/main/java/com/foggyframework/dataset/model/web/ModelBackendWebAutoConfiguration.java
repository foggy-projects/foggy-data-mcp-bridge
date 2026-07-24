package com.foggyframework.dataset.model.web;

import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

/** Opt-in HTTP assembly for model SPI v2 diagnostics. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({RestController.class, BackendProviderCatalog.class})
@ConditionalOnProperty(prefix = "foggy.model.backends.web", name = "enabled", havingValue = "true")
public class ModelBackendWebAutoConfiguration {

    @Bean
    @ConditionalOnBean(BackendProviderCatalog.class)
    @ConditionalOnMissingBean
    public BackendProviderCatalogController backendProviderCatalogController(
            BackendProviderCatalog catalog) {
        return new BackendProviderCatalogController(catalog);
    }
}

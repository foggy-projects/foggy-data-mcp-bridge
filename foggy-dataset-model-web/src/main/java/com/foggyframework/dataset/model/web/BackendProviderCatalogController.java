package com.foggyframework.dataset.model.web;

import com.foggyframework.dataset.model.core.backend.BackendProviderCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Read-only, opt-in diagnostics endpoint for discovered SPI v2 backends. */
@RestController
@RequestMapping("/foggy/model/backends")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(BackendProviderCatalog.class)
@ConditionalOnProperty(prefix = "foggy.model.backends.web", name = "enabled", havingValue = "true")
public final class BackendProviderCatalogController {

    private final BackendProviderCatalog catalog;

    public BackendProviderCatalogController(BackendProviderCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<BackendProviderView> listBackends() {
        return catalog.descriptors().stream()
                .map(BackendProviderView::from)
                .sorted(Comparator.comparing(BackendProviderView::backendId))
                .toList();
    }
}

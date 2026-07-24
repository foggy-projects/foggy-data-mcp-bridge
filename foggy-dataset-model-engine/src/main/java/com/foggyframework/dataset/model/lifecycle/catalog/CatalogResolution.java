package com.foggyframework.dataset.model.lifecycle.catalog;

import com.foggyframework.dataset.model.lifecycle.identity.CatalogIdentity;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.Map;
import java.util.Objects;

/** Model plus the exact immutable identities that supplied it. */
public record CatalogResolution<T>(
        String canonicalName,
        T model,
        CatalogIdentity catalogIdentity,
        Map<String, DatasourceBindingIdentity> dependencyBindings,
        boolean bindingIdentityComplete
) {
    public CatalogResolution {
        if (canonicalName == null || canonicalName.isBlank()) {
            throw new IllegalArgumentException("canonicalName must not be blank");
        }
        canonicalName = canonicalName.trim();
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(catalogIdentity, "catalogIdentity");
        dependencyBindings = dependencyBindings == null ? Map.of() : Map.copyOf(dependencyBindings);
        dependencyBindings.forEach((bindingKey, identity) -> {
            if (identity == null || !bindingKey.equals(identity.bindingKey())) {
                throw new IllegalArgumentException(
                        "dependency binding map key must equal identity.bindingKey");
            }
        });
        if (model instanceof QueryModel queryModel
                && queryModel.getName() != null
                && !canonicalName.equals(queryModel.getName())) {
            throw new IllegalArgumentException("resolved model name does not match canonicalName");
        }
    }
}

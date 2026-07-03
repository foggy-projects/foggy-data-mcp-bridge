package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.spi.NamedDataSourceResolver;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Service
@Primary
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeNamedDataSourceResolver implements NamedDataSourceResolver {

    private final RuntimeDatasourceRegistryService registryService;
    private final ListableBeanFactory beanFactory;

    public RuntimeNamedDataSourceResolver(
            RuntimeDatasourceRegistryService registryService,
            ListableBeanFactory beanFactory
    ) {
        this.registryService = registryService;
        this.beanFactory = beanFactory;
    }

    @Override
    public DataSource resolve(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return registryService.resolve(name)
                .map(RuntimeDatasourceRegistryService.ResolvedDatasource::dataSource)
                .orElseGet(() -> resolveFromOtherResolver(name));
    }

    @Override
    public DataSource resolveDefault(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return null;
        }
        return registryService.getNamespaceDatasource(namespace)
                .flatMap(registryService::resolve)
                .map(RuntimeDatasourceRegistryService.ResolvedDatasource::dataSource)
                .orElse(null);
    }

    @Override
    public boolean isConfigured(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        if (registryService.isConfigured(name)) {
            return true;
        }
        for (NamedDataSourceResolver resolver : otherResolvers()) {
            if (resolver.isConfigured(name)) {
                return true;
            }
        }
        return false;
    }

    private DataSource resolveFromOtherResolver(String name) {
        for (NamedDataSourceResolver resolver : otherResolvers()) {
            DataSource dataSource = resolver.resolve(name);
            if (dataSource != null) {
                return dataSource;
            }
        }
        return null;
    }

    private NamedDataSourceResolver[] otherResolvers() {
        return java.util.Arrays.stream(beanFactory.getBeanNamesForType(NamedDataSourceResolver.class))
                .map(beanName -> beanFactory.getBean(beanName, NamedDataSourceResolver.class))
                .filter(resolver -> resolver != this)
                .toArray(NamedDataSourceResolver[]::new);
    }
}

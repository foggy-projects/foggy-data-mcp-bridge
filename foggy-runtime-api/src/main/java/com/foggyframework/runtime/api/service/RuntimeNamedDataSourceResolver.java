package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.db.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.db.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.db.model.lifecycle.port.DatasourceBindingResolver;
import com.foggyframework.dataset.db.model.lifecycle.port.ResolvedDatasourceBinding;
import com.foggyframework.dataset.db.model.spi.NamedDataSourceResolver;
import com.foggyframework.dataset.db.model.spi.ProcessLocalDefaultDataSourceResolver;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@Primary
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeNamedDataSourceResolver implements NamedDataSourceResolver, DatasourceBindingResolver,
        ProcessLocalDefaultDataSourceResolver {

    private final RuntimeDatasourceRegistryService registryService;
    private final ListableBeanFactory beanFactory;
    private final ThreadLocal<Boolean> externalDelegation = new ThreadLocal<>();

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
        ResolvedDatasourceBinding binding = resolveBinding(name);
        return binding != null ? binding.dataSource() : null;
    }

    @Override
    public DataSource resolveDefault(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return null;
        }
        ResolvedDatasourceBinding binding = resolveDefaultBinding(namespace);
        return binding != null ? binding.dataSource() : null;
    }

    @Override
    public ResolvedDatasourceBinding resolveBinding(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        RuntimeDatasourceRegistryService.RuntimeResolvedBinding managed =
                registryService.resolveRuntimeBinding(name).orElse(null);
        if (managed != null) {
            return coreBinding(managed);
        }
        ResolvedDatasourceBinding strongFallback = resolveFromOtherBindingResolver(name);
        if (strongFallback != null) {
            return strongFallback;
        }
        DataSource fallback = resolveFromOtherResolver(name);
        return fallback != null ? ResolvedDatasourceBinding.untracked(fallback) : null;
    }

    @Override
    public ResolvedDatasourceBinding resolveDefaultBinding(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return null;
        }
        RuntimeDatasourceRegistryService.RuntimeResolvedBinding managed =
                registryService.resolveRuntimeDefaultBinding(namespace).orElse(null);
        return managed != null ? coreBinding(managed) : null;
    }

    @Override
    public ResolvedDatasourceBinding resolveProcessLocalDefaultBinding() {
        if (externalDelegationActive()) {
            return null;
        }
        return Arrays.stream(
                        beanFactory.getBeanNamesForType(ProcessLocalDefaultDataSourceResolver.class))
                .map(beanName -> beanFactory.getBean(
                        beanName, ProcessLocalDefaultDataSourceResolver.class))
                .filter(resolver -> !isSelfResolver(resolver))
                .map(resolver -> callExternal(
                        resolver::resolveProcessLocalDefaultBinding))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Override
    public BindingCurrentness currentness(DatasourceBindingIdentity identity) {
        BindingCurrentness managed = registryService.currentness(identity);
        return managed != BindingCurrentness.UNKNOWN
                ? managed
                : currentnessFromOtherBindingResolvers(identity);
    }

    @Override
    public <T> T publishIfCurrent(
            Collection<DatasourceBindingIdentity> identities,
            Supplier<T> publication
    ) {
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(publication, "publication");
        if (identities.isEmpty()) {
            return publication.get();
        }
        return publishGuarded(bindingGuardDomains(identities), 0, publication);
    }

    @Override
    public boolean isConfigured(String name) {
        if (!StringUtils.hasText(name)) {
            return false;
        }
        if (registryService.isConfigured(name)) {
            return true;
        }
        if (externalDelegationActive()) {
            return false;
        }
        for (NamedDataSourceResolver resolver : otherResolvers()) {
            if (callExternal(() -> resolver.isConfigured(name))) {
                return true;
            }
        }
        return false;
    }

    private DataSource resolveFromOtherResolver(String name) {
        if (externalDelegationActive()) {
            return null;
        }
        for (NamedDataSourceResolver resolver : otherResolvers()) {
            DataSource dataSource = callExternal(() -> resolver.resolve(name));
            if (dataSource != null) {
                return dataSource;
            }
        }
        return null;
    }

    private ResolvedDatasourceBinding resolveFromOtherBindingResolver(String name) {
        if (externalDelegationActive()) {
            return null;
        }
        return Arrays.stream(beanFactory.getBeanNamesForType(DatasourceBindingResolver.class))
                .map(beanName -> beanFactory.getBean(beanName, DatasourceBindingResolver.class))
                .filter(resolver -> !isSelfResolver(resolver))
                .map(resolver -> callExternal(() -> resolver.resolveBinding(name)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private BindingCurrentness currentnessFromOtherBindingResolvers(
            DatasourceBindingIdentity identity
    ) {
        if (externalDelegationActive()) {
            return BindingCurrentness.UNKNOWN;
        }
        BindingCurrentness result = BindingCurrentness.UNKNOWN;
        for (DatasourceBindingResolver resolver : otherBindingResolvers()) {
            BindingCurrentness candidate = callExternal(
                    () -> resolver.currentness(identity));
            if (candidate == BindingCurrentness.CURRENT) {
                return candidate;
            }
            if (candidate == BindingCurrentness.STALE) {
                result = candidate;
            }
        }
        return result;
    }

    private ResolvedDatasourceBinding coreBinding(
            RuntimeDatasourceRegistryService.RuntimeResolvedBinding binding
    ) {
        if (!StringUtils.hasText(binding.bindingKey())
                || !StringUtils.hasText(binding.backendId())
                || !StringUtils.hasText(binding.generation())) {
            return ResolvedDatasourceBinding.untracked(binding.dataSource());
        }
        DatasourceBindingIdentity identity = new DatasourceBindingIdentity(
                binding.bindingKey(),
                binding.backendId(),
                new DatasourceBindingGeneration(binding.generation())
        );
        return new ResolvedDatasourceBinding(binding.dataSource(), identity, binding.cacheable());
    }

    private NamedDataSourceResolver[] otherResolvers() {
        return Arrays.stream(beanFactory.getBeanNamesForType(NamedDataSourceResolver.class))
                .map(beanName -> beanFactory.getBean(beanName, NamedDataSourceResolver.class))
                .filter(resolver -> !isSelfResolver(resolver))
                .toArray(NamedDataSourceResolver[]::new);
    }

    private DatasourceBindingResolver[] otherBindingResolvers() {
        return Arrays.stream(beanFactory.getBeanNamesForType(DatasourceBindingResolver.class))
                .map(beanName -> beanFactory.getBean(beanName, DatasourceBindingResolver.class))
                .filter(resolver -> !isSelfResolver(resolver))
                .toArray(DatasourceBindingResolver[]::new);
    }

    private List<BindingGuardDomain> bindingGuardDomains(
            Collection<DatasourceBindingIdentity> identities
    ) {
        if (externalDelegationActive()) {
            throw new IllegalStateException(
                    "DATASOURCE_BINDING_RESOLVER_RECURSION");
        }
        List<NamedBindingResolver> externalResolvers = Arrays.stream(
                        beanFactory.getBeanNamesForType(DatasourceBindingResolver.class))
                .sorted()
                .map(beanName -> new NamedBindingResolver(
                        beanName,
                        beanFactory.getBean(beanName, DatasourceBindingResolver.class)))
                .filter(named -> !isSelfResolver(named.resolver()))
                .toList();

        List<DatasourceBindingIdentity> local = new ArrayList<>();
        List<BindingGuardDomain> external = new ArrayList<>();
        for (DatasourceBindingIdentity identity : identities) {
            Objects.requireNonNull(identity, "datasource binding identity");
            if (registryService.currentness(identity) != BindingCurrentness.UNKNOWN) {
                local.add(identity);
                continue;
            }
            NamedBindingResolver owner = externalResolvers.stream()
                    .filter(named -> callExternal(
                            () -> named.resolver().currentness(identity))
                            != BindingCurrentness.UNKNOWN)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: "
                                    + identity.bindingKey()));
            BindingGuardDomain domain = external.stream()
                    .filter(candidate -> candidate.orderKey().equals(
                            "1:" + owner.beanName()))
                    .findFirst()
                    .orElse(null);
            if (domain == null) {
                domain = new BindingGuardDomain(
                        "1:" + owner.beanName(), owner.resolver(), new ArrayList<>());
                external.add(domain);
            }
            domain.identities().add(identity);
        }

        List<BindingGuardDomain> domains = new ArrayList<>();
        if (!local.isEmpty()) {
            domains.add(new BindingGuardDomain(
                    "0:runtime-registry", null, local));
        }
        external.sort(Comparator.comparing(BindingGuardDomain::orderKey));
        domains.addAll(external);
        return List.copyOf(domains);
    }

    private <T> T publishGuarded(
            List<BindingGuardDomain> domains,
            int index,
            Supplier<T> publication
    ) {
        if (index == domains.size()) {
            return publication.get();
        }
        BindingGuardDomain domain = domains.get(index);
        Supplier<T> remainder = () -> continueComposite(
                () -> publishGuarded(domains, index + 1, publication));
        return domain.resolver() == null
                ? registryService.publishIfCurrent(domain.identities(), remainder)
                : callExternal(() -> domain.resolver().publishIfCurrent(
                        domain.identities(), remainder));
    }

    private boolean isSelfResolver(Object resolver) {
        return resolver == this
                || RuntimeNamedDataSourceResolver.class.isAssignableFrom(
                        AopUtils.getTargetClass(resolver));
    }

    private boolean externalDelegationActive() {
        return Boolean.TRUE.equals(externalDelegation.get());
    }

    private <T> T callExternal(Supplier<T> invocation) {
        if (externalDelegationActive()) {
            throw new IllegalStateException(
                    "DATASOURCE_BINDING_RESOLVER_RECURSION");
        }
        externalDelegation.set(Boolean.TRUE);
        try {
            return invocation.get();
        } finally {
            externalDelegation.remove();
        }
    }

    /**
     * An external guard invokes its continuation while still holding its own
     * mutation lock. Suspend only the recursion marker for that intentional
     * continuation so the next deterministically ordered guard domain can be
     * acquired; restore it before returning control to the outer provider.
     */
    private <T> T continueComposite(Supplier<T> continuation) {
        Boolean suspended = externalDelegation.get();
        if (suspended != null) {
            externalDelegation.remove();
        }
        try {
            return continuation.get();
        } finally {
            if (suspended != null) {
                externalDelegation.set(suspended);
            }
        }
    }

    private record NamedBindingResolver(
            String beanName,
            DatasourceBindingResolver resolver
    ) {
    }

    private record BindingGuardDomain(
            String orderKey,
            DatasourceBindingResolver resolver,
            List<DatasourceBindingIdentity> identities
    ) {
    }
}

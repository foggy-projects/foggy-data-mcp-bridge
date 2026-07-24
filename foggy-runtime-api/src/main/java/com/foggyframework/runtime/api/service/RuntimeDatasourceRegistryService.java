package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.dataset.model.lifecycle.catalog.CatalogSnapshotStore;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingGeneration;
import com.foggyframework.dataset.model.lifecycle.identity.DatasourceBindingIdentity;
import com.foggyframework.dataset.model.lifecycle.port.BindingCurrentness;
import com.foggyframework.dataset.model.lifecycle.port.RevokeMode;
import com.foggyframework.dataset.model.lifecycle.port.StaleDatasourceBindingException;
import com.foggyframework.dataset.model.lifecycle.refresh.CatalogRefreshCoordinator;
import com.foggyframework.dataset.model.lifecycle.refresh.DatasourceCatalogConvergence;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.DatasourceInfo;
import com.foggyframework.runtime.api.dto.DatasourcePoolInfo;
import com.foggyframework.runtime.api.service.ManagedDataSourcePoolManager.ManagedDataSourcePoolState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeDatasourceRegistryService {

    public static final String DEFAULT_DATASOURCE_NAME = "default";
    private static final String NAMED_BINDING_KEY_PREFIX = "runtime:named:";
    private static final String NAMESPACE_BINDING_KEY_PREFIX = "runtime:namespace-default:";
    private static final String BACKEND_ID_PREFIX = "runtime-registry:";

    private final FoggyRuntimeApiProperties properties;
    private final ObjectProvider<DataSource> defaultDataSourceProvider;
    private final ObjectMapper objectMapper;
    private final ManagedDataSourcePoolManager poolManager;
    private final Map<String, RuntimeDatasourceRecord> records = new LinkedHashMap<>();
    private final Map<String, String> namespaceBindings = new LinkedHashMap<>();
    private final Map<String, String> namespaceBindingGenerations = new LinkedHashMap<>();
    private volatile DatasourceCatalogConvergence catalogConvergence;
    private volatile ObjectProvider<CatalogSnapshotStore> catalogSnapshotStoreProvider;
    private volatile ObjectProvider<CatalogRefreshCoordinator> catalogRefreshCoordinatorProvider;
    private String registryEpoch;
    private long nextSequence = 1L;
    private boolean loaded;

    public RuntimeDatasourceRegistryService(
            FoggyRuntimeApiProperties properties,
            ObjectProvider<DataSource> defaultDataSourceProvider,
            ObjectMapper objectMapper,
            ManagedDataSourcePoolManager poolManager
    ) {
        this.properties = properties;
        this.defaultDataSourceProvider = defaultDataSourceProvider;
        this.objectMapper = objectMapper;
        this.poolManager = poolManager;
    }

    /** Providers avoid a startup cycle through the Runtime named datasource resolver. */
    @Autowired
    void configureCatalogConvergenceProviders(
            ObjectProvider<CatalogSnapshotStore> snapshotStoreProvider,
            ObjectProvider<CatalogRefreshCoordinator> refreshCoordinatorProvider
    ) {
        this.catalogSnapshotStoreProvider = snapshotStoreProvider;
        this.catalogRefreshCoordinatorProvider = refreshCoordinatorProvider;
    }

    /** Deterministic direct wiring seam retained for non-Spring callers and tests. */
    void configureCatalogConvergence(
            CatalogSnapshotStore snapshotStore,
            CatalogRefreshCoordinator refreshCoordinator
    ) {
        this.catalogConvergence = new DatasourceCatalogConvergence(
                snapshotStore, refreshCoordinator);
    }

    public synchronized List<RuntimeDatasourceRecord> listRecords() {
        loadIfNeeded();
        return List.copyOf(records.values());
    }

    public synchronized Optional<RuntimeDatasourceRecord> find(String name) {
        loadIfNeeded();
        String canonicalName = canonicalLookupValue(name);
        return canonicalName == null
                ? Optional.empty()
                : Optional.ofNullable(records.get(canonicalName));
    }

    public RuntimeDatasourceRecord save(RuntimeDatasourceRecord record) {
        return save(record, RevokeMode.DRAIN);
    }

    public RuntimeDatasourceRecord save(RuntimeDatasourceRecord record, RevokeMode revokeMode) {
        RuntimeDatasourceRecord normalized;
        Set<String> catalogsToRefresh;
        synchronized (this) {
            loadIfNeeded();
            RuntimeDatasourceRecord canonicalRecord = canonicalRecord(record);
            String generation = generation(registryEpoch, nextSequence);
            long candidateNextSequence = advanceSequence(nextSequence);
            normalized = canonicalRecord.withLifecycle(
                    generation,
                    Instant.now().toString()
            );
            RuntimeDatasourceRecord previous = records.get(normalized.name());
            Map<String, RuntimeDatasourceRecord> candidateRecords = new LinkedHashMap<>(records);
            Map<String, String> candidateNamespaceGenerations =
                    new LinkedHashMap<>(namespaceBindingGenerations);
            candidateRecords.put(normalized.name(), normalized);
            List<String> affectedNamespaces = namespaceBindings.entrySet().stream()
                    .filter(entry -> normalized.name().equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            for (String namespace : affectedNamespaces) {
                candidateNamespaceGenerations.put(
                        namespace, generation(registryEpoch, candidateNextSequence));
                candidateNextSequence = advanceSequence(candidateNextSequence);
            }
            persist(candidateRecords, namespaceBindings, candidateNamespaceGenerations,
                    registryEpoch, candidateNextSequence);
            replaceRecords(candidateRecords);
            replaceNamespaceBindingGenerations(candidateNamespaceGenerations);
            nextSequence = candidateNextSequence;
            // The persisted registry generation is already committed. Close catalog admission
            // before any pool callback can block or fail, so readers cannot observe a committed
            // binding rotation while the old catalog is still ACTIVE.
            catalogsToRefresh = blockAffectedCatalogs(
                    affectedNamespaces, Set.of(namedBindingKey(normalized.name())));
            poolManager.onRecordSaved(previous, normalized, effectiveMode(revokeMode));
            affectedNamespaces.forEach(namespace ->
                    poolManager.onNamespaceBindingChanged(
                            namespace, effectiveMode(revokeMode)));
        }
        refreshCatalogs(catalogsToRefresh);
        return normalized;
    }

    public boolean remove(String name) {
        return remove(name, RevokeMode.DRAIN);
    }

    public boolean remove(String name, RevokeMode revokeMode) {
        String canonicalName = canonicalLookupValue(name);
        if (canonicalName == null) {
            return false;
        }
        Set<String> catalogsToRefresh;
        synchronized (this) {
            loadIfNeeded();
            RuntimeDatasourceRecord removed = records.get(canonicalName);
            if (removed == null) {
                return false;
            }
            Map<String, RuntimeDatasourceRecord> candidateRecords = new LinkedHashMap<>(records);
            Map<String, String> candidateNamespaceBindings = new LinkedHashMap<>(namespaceBindings);
            Map<String, String> candidateNamespaceGenerations =
                    new LinkedHashMap<>(namespaceBindingGenerations);
            candidateRecords.remove(canonicalName);
            List<String> affectedNamespaces = candidateNamespaceBindings.entrySet().stream()
                    .filter(entry -> canonicalName.equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            affectedNamespaces.forEach(namespace -> {
                candidateNamespaceBindings.remove(namespace);
                candidateNamespaceGenerations.remove(namespace);
            });
            // Consume one sequence for the removal boundary even though no current
            // record remains. A later recreate can therefore never reuse the old
            // or removal sequence.
            long candidateNextSequence = advanceSequence(nextSequence);
            persist(candidateRecords, candidateNamespaceBindings, candidateNamespaceGenerations,
                    registryEpoch, candidateNextSequence);
            replaceRecords(candidateRecords);
            replaceNamespaceBindings(candidateNamespaceBindings);
            replaceNamespaceBindingGenerations(candidateNamespaceGenerations);
            nextSequence = candidateNextSequence;
            catalogsToRefresh = blockAffectedCatalogs(
                    affectedNamespaces, Set.of(namedBindingKey(canonicalName)));
            affectedNamespaces.forEach(namespace ->
                    poolManager.onNamespaceBindingChanged(
                            namespace, effectiveMode(revokeMode)));
            poolManager.remove(canonicalName, effectiveMode(revokeMode));
        }
        refreshCatalogs(catalogsToRefresh);
        return true;
    }

    public synchronized Optional<String> getNamespaceDatasource(String namespace) {
        loadIfNeeded();
        String canonicalNamespace = canonicalLookupValue(namespace);
        return canonicalNamespace == null
                ? Optional.empty()
                : Optional.ofNullable(namespaceBindings.get(canonicalNamespace));
    }

    public synchronized Map<String, String> listNamespaceBindings() {
        loadIfNeeded();
        return Map.copyOf(namespaceBindings);
    }

    public void bindNamespace(String namespace, String dataSource) {
        bindNamespace(namespace, dataSource, RevokeMode.DRAIN);
    }

    public void bindNamespace(String namespace, String dataSource, RevokeMode revokeMode) {
        String canonicalNamespace = requireCanonicalValue(namespace, "namespace");
        String canonicalDataSource = requireCanonicalValue(dataSource, "dataSource");
        Set<String> catalogsToRefresh;
        synchronized (this) {
            loadIfNeeded();
            RuntimeDatasourceRecord target = records.get(canonicalDataSource);
            boolean springDefault = DEFAULT_DATASOURCE_NAME.equals(canonicalDataSource)
                    && hasDefaultDataSource();
            if (!springDefault && (target == null || !target.enabled())) {
                throw new IllegalArgumentException(
                        "Runtime-managed dataSource is not configured or enabled: "
                                + canonicalDataSource);
            }
            Map<String, String> candidateBindings = new LinkedHashMap<>(namespaceBindings);
            Map<String, String> candidateGenerations =
                    new LinkedHashMap<>(namespaceBindingGenerations);
            candidateBindings.put(canonicalNamespace, canonicalDataSource);
            candidateGenerations.put(
                    canonicalNamespace, generation(registryEpoch, nextSequence));
            long candidateNextSequence = advanceSequence(nextSequence);
            persist(records, candidateBindings, candidateGenerations,
                    registryEpoch, candidateNextSequence);
            replaceNamespaceBindings(candidateBindings);
            replaceNamespaceBindingGenerations(candidateGenerations);
            nextSequence = candidateNextSequence;
            catalogsToRefresh = blockAffectedCatalogs(
                    Set.of(canonicalNamespace), Set.of());
            poolManager.onNamespaceBindingChanged(
                    canonicalNamespace, effectiveMode(revokeMode));
        }
        refreshCatalogs(catalogsToRefresh);
    }

    public RuntimeDatasourceRecord newRecord(
            String name,
            String type,
            String jdbcUrl,
            String username,
            String password,
            String passwordRef,
            boolean enabled
    ) {
        String now = Instant.now().toString();
        return new RuntimeDatasourceRecord(
                requireCanonicalValue(name, "dataSource name"),
                type, jdbcUrl, username, password, passwordRef,
                enabled, now, now, null);
    }

    public List<DatasourceInfo> listInfos() {
        List<DatasourceInfo> datasources = new ArrayList<>();
        if (hasDefaultDataSource()) {
            datasources.add(defaultDatasourceInfo());
        }
        for (RuntimeDatasourceRecord record : listRecords()) {
            datasources.add(infoFromRecord(record, record.enabled() ? "active" : "disabled", null));
        }
        return List.copyOf(datasources);
    }

    public boolean hasDefaultDataSource() {
        return defaultDataSourceProvider.getIfAvailable() != null;
    }

    public synchronized boolean isConfigured(String name) {
        String normalized = canonicalOrDefault(name);
        if (DEFAULT_DATASOURCE_NAME.equals(normalized)) {
            return hasDefaultDataSource();
        }
        loadIfNeeded();
        RuntimeDatasourceRecord record = records.get(normalized);
        return record != null && record.enabled();
    }

    public synchronized Optional<ResolvedDatasource> resolve(String name) {
        String normalized = canonicalOrDefault(name);
        if (DEFAULT_DATASOURCE_NAME.equals(normalized)) {
            DataSource dataSource = defaultDataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedDatasource(DEFAULT_DATASOURCE_NAME, dataSource));
        }
        loadIfNeeded();
        RuntimeDatasourceRecord record = records.get(normalized);
        if (record == null || !record.enabled()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedDatasource(record.name(), poolManager.resolve(record)));
    }

    /**
     * Resolve a generation-pinned logical binding without exposing physical
     * JDBC configuration in its identity.
     */
    public synchronized Optional<RuntimeResolvedBinding> resolveRuntimeBinding(String name) {
        String normalized = canonicalOrDefault(name);
        if (DEFAULT_DATASOURCE_NAME.equals(normalized)) {
            DataSource dataSource = defaultDataSourceProvider.getIfAvailable();
            return dataSource == null
                    ? Optional.empty()
                    : Optional.of(new RuntimeResolvedBinding(
                    DEFAULT_DATASOURCE_NAME,
                    dataSource,
                    null,
                    null,
                    null,
                    false
            ));
        }
        loadIfNeeded();
        RuntimeDatasourceRecord record = records.get(normalized);
        if (record == null || !record.enabled()) {
            return Optional.empty();
        }
        BindingDescriptor descriptor = new BindingDescriptor(
                record,
                namedBindingKey(record.name()),
                record.bindingGeneration(),
                registryEnabled()
        );
        DataSource handle = poolManager.resolve(
                descriptor.record(),
                descriptor.bindingKey(),
                descriptor.generation()
        );
        return Optional.of(new RuntimeResolvedBinding(
                descriptor.record().name(),
                handle,
                descriptor.bindingKey(),
                backendId(descriptor.record().name()),
                descriptor.generation(),
                descriptor.cacheable() && StringUtils.hasText(descriptor.generation())
        ));
    }

    public synchronized Optional<RuntimeResolvedBinding> resolveRuntimeDefaultBinding(String namespace) {
        String canonicalNamespace = canonicalLookupValue(namespace);
        if (canonicalNamespace == null) {
            return Optional.empty();
        }
        loadIfNeeded();
        String dataSourceName = namespaceBindings.get(canonicalNamespace);
        if (DEFAULT_DATASOURCE_NAME.equals(dataSourceName)) {
            DataSource dataSource = defaultDataSourceProvider.getIfAvailable();
            return dataSource == null
                    ? Optional.empty()
                    : Optional.of(new RuntimeResolvedBinding(
                    DEFAULT_DATASOURCE_NAME,
                    dataSource,
                    null,
                    null,
                    null,
                    false
            ));
        }
        RuntimeDatasourceRecord record = dataSourceName != null ? records.get(dataSourceName) : null;
        if (record == null || !record.enabled()) {
            return Optional.empty();
        }
        BindingDescriptor descriptor = new BindingDescriptor(
                record,
                namespaceBindingKey(canonicalNamespace),
                namespaceBindingGenerations.get(canonicalNamespace),
                registryEnabled()
        );
        DataSource handle = poolManager.resolve(
                descriptor.record(),
                descriptor.bindingKey(),
                descriptor.generation()
        );
        return Optional.of(new RuntimeResolvedBinding(
                descriptor.record().name(),
                handle,
                descriptor.bindingKey(),
                backendId(descriptor.record().name()),
                descriptor.generation(),
                descriptor.cacheable() && StringUtils.hasText(descriptor.generation())
        ));
    }

    public synchronized Optional<String> getNamespaceBindingGeneration(String namespace) {
        loadIfNeeded();
        String canonicalNamespace = canonicalLookupValue(namespace);
        return canonicalNamespace == null
                ? Optional.empty()
                : Optional.ofNullable(namespaceBindingGenerations.get(canonicalNamespace));
    }

    /**
     * Compares a captured logical binding identity with the committed registry
     * state. This method never opens a datasource connection and never uses
     * physical JDBC configuration as identity input.
     */
    public synchronized BindingCurrentness currentness(DatasourceBindingIdentity identity) {
        if (identity == null || !identity.backendId().startsWith(BACKEND_ID_PREFIX)) {
            return BindingCurrentness.UNKNOWN;
        }
        String bindingKey = identity.bindingKey();
        if (bindingKey.startsWith(NAMED_BINDING_KEY_PREFIX)) {
            String name = bindingKey.substring(NAMED_BINDING_KEY_PREFIX.length());
            if (!StringUtils.hasText(name)) {
                return BindingCurrentness.UNKNOWN;
            }
            if (!backendId(name).equals(identity.backendId())) {
                return BindingCurrentness.UNKNOWN;
            }
            loadIfNeeded();
            RuntimeDatasourceRecord record = records.get(name);
            if (record == null || !record.enabled()
                    || !StringUtils.hasText(record.bindingGeneration())) {
                return BindingCurrentness.STALE;
            }
            DatasourceBindingIdentity current = new DatasourceBindingIdentity(
                    namedBindingKey(record.name()),
                    backendId(record.name()),
                    new DatasourceBindingGeneration(record.bindingGeneration())
            );
            return current.equals(identity)
                    ? BindingCurrentness.CURRENT
                    : BindingCurrentness.STALE;
        }
        if (bindingKey.startsWith(NAMESPACE_BINDING_KEY_PREFIX)) {
            String namespace = bindingKey.substring(NAMESPACE_BINDING_KEY_PREFIX.length());
            if (!StringUtils.hasText(namespace)) {
                return BindingCurrentness.UNKNOWN;
            }
            loadIfNeeded();
            String dataSourceName = namespaceBindings.get(namespace);
            RuntimeDatasourceRecord record = dataSourceName == null
                    ? null
                    : records.get(dataSourceName);
            String generation = namespaceBindingGenerations.get(namespace);
            if (record == null || !record.enabled() || !StringUtils.hasText(generation)) {
                return BindingCurrentness.STALE;
            }
            DatasourceBindingIdentity current = new DatasourceBindingIdentity(
                    namespaceBindingKey(namespace),
                    backendId(record.name()),
                    new DatasourceBindingGeneration(generation)
            );
            return current.equals(identity)
                    ? BindingCurrentness.CURRENT
                    : BindingCurrentness.STALE;
        }
        return BindingCurrentness.UNKNOWN;
    }

    /**
     * Holds the registry mutation monitor across the final logical-currentness
     * check and catalog publication callback. All save/remove/namespace bind
     * commits use the same monitor, closing the check-then-publish race.
     */
    public synchronized <T> T publishIfCurrent(
            Collection<DatasourceBindingIdentity> identities,
            Supplier<T> publication
    ) {
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(publication, "publication");
        for (DatasourceBindingIdentity identity : identities) {
            Objects.requireNonNull(identity, "datasource binding identity");
            BindingCurrentness state = currentness(identity);
            if (state == BindingCurrentness.STALE) {
                throw new StaleDatasourceBindingException(identity.bindingKey());
            }
            if (state != BindingCurrentness.CURRENT) {
                throw new IllegalStateException(
                        "DATASOURCE_BINDING_CURRENTNESS_UNKNOWN: "
                                + identity.bindingKey());
            }
        }
        return publication.get();
    }

    public synchronized String registryEpoch() {
        loadIfNeeded();
        return registryEpoch;
    }

    public synchronized long nextSequence() {
        loadIfNeeded();
        return nextSequence;
    }

    private static String namedBindingKey(String name) {
        return NAMED_BINDING_KEY_PREFIX + name;
    }

    private static String namespaceBindingKey(String namespace) {
        return NAMESPACE_BINDING_KEY_PREFIX + namespace;
    }

    private static String backendId(String name) {
        return BACKEND_ID_PREFIX + name;
    }

    private Set<String> blockAffectedCatalogs(
            Collection<String> directNamespaces,
            Collection<String> changedBindingKeys
    ) {
        DatasourceCatalogConvergence convergence = catalogConvergence();
        return convergence == null
                ? Set.of()
                : convergence.blockAffectedNamespaces(
                        directNamespaces, changedBindingKeys);
    }

    private void refreshCatalogs(Collection<String> namespaces) {
        DatasourceCatalogConvergence convergence = catalogConvergence();
        if (convergence != null && namespaces != null && !namespaces.isEmpty()) {
            convergence.refresh(namespaces);
        }
    }

    private DatasourceCatalogConvergence catalogConvergence() {
        DatasourceCatalogConvergence current = catalogConvergence;
        if (current != null) {
            return current;
        }
        ObjectProvider<CatalogSnapshotStore> storeProvider = catalogSnapshotStoreProvider;
        ObjectProvider<CatalogRefreshCoordinator> coordinatorProvider =
                catalogRefreshCoordinatorProvider;
        if (storeProvider == null || coordinatorProvider == null) {
            return null;
        }
        CatalogSnapshotStore store = storeProvider.getIfAvailable();
        CatalogRefreshCoordinator coordinator = coordinatorProvider.getIfAvailable();
        if (store == null || coordinator == null) {
            return null;
        }
        DatasourceCatalogConvergence resolved =
                new DatasourceCatalogConvergence(store, coordinator);
        catalogConvergence = resolved;
        return resolved;
    }

    public DatasourceInfo defaultDatasourceInfo() {
        return new DatasourceInfo(
                DEFAULT_DATASOURCE_NAME,
                "spring",
                null,
                null,
                null,
                true,
                "config",
                false,
                false,
                false,
                true,
                "active",
                null,
                defaultPoolInfo()
        );
    }

    public DatasourceInfo infoFromRecord(RuntimeDatasourceRecord record, String status, String message) {
        return new DatasourceInfo(
                record.name(),
                record.type(),
                record.jdbcUrl(),
                record.username(),
                record.passwordRef(),
                record.enabled(),
                "runtime-registry",
                true,
                true,
                true,
                record.enabled(),
                status,
                message,
                poolInfo(record)
        );
    }

    private boolean registryEnabled() {
        return properties.getDatasourceRegistry() == null || properties.getDatasourceRegistry().isEnabled();
    }

    public boolean isRegistryEnabled() {
        return registryEnabled();
    }

    public Path resolvedRegistryPath() {
        return registryPath();
    }

    public boolean registryFileExists() {
        return Files.exists(resolvedRegistryPath());
    }

    public Long registrySizeBytes() {
        Path path = resolvedRegistryPath();
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.size(path);
        } catch (IOException e) {
            return null;
        }
    }

    public String registryLastModifiedAt() {
        Path path = resolvedRegistryPath();
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.getLastModifiedTime(path).toInstant().toString();
        } catch (IOException e) {
            return null;
        }
    }

    private DatasourcePoolInfo defaultPoolInfo() {
        return new DatasourcePoolInfo(
                resolvedRegistryPath().toString(),
                "config",
                true,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private DatasourcePoolInfo poolInfo(RuntimeDatasourceRecord record) {
        ManagedDataSourcePoolState state = poolManager.state(record.name()).orElse(null);
        ManagedDataSourcePoolSettings settings = state != null ? state.settings() : poolManager.settingsFor(record);
        return new DatasourcePoolInfo(
                resolvedRegistryPath().toString(),
                state != null ? state.lifecycleStatus() : "not-created",
                state != null && state.poolExists(),
                state != null && state.poolClosed(),
                state != null ? state.activeConnections() : 0,
                state != null ? state.lastBorrowedAt() : null,
                state != null ? state.lastReturnedAt() : null,
                state != null ? state.lastCloseReason() : null,
                state != null ? state.lastCloseError() : null,
                settings.idlePoolCloseMinutes(),
                settings.cleanupIntervalMinutes(),
                settings.maximumPoolSize(),
                settings.minimumIdle(),
                settings.connectionTimeoutMs(),
                settings.idleTimeoutMs(),
                settings.maxLifetimeMs(),
                settings.driverClassName()
        );
    }

    private void loadIfNeeded() {
        if (loaded) {
            return;
        }
        Map<String, RuntimeDatasourceRecord> loadedRecords = new LinkedHashMap<>();
        Map<String, String> loadedBindings = new LinkedHashMap<>();
        Map<String, String> loadedBindingGenerations = new LinkedHashMap<>();
        String loadedEpoch = UUID.randomUUID().toString();
        long loadedNextSequence = 1L;
        if (!registryEnabled()) {
            registryEpoch = loadedEpoch;
            nextSequence = loadedNextSequence;
            loaded = true;
            return;
        }
        Path path = registryPath();
        if (!Files.exists(path)) {
            registryEpoch = loadedEpoch;
            nextSequence = loadedNextSequence;
            loaded = true;
            return;
        }
        try {
            RegistryFile registry = objectMapper.readValue(path.toFile(), RegistryFile.class);
            boolean needsCanonicalRewrite = false;
            if (registry != null && registry.datasources() != null) {
                for (RuntimeDatasourceRecord record : registry.datasources()) {
                    if (record != null && StringUtils.hasText(record.name())) {
                        RuntimeDatasourceRecord canonicalRecord = canonicalRecord(record);
                        RuntimeDatasourceRecord previous = loadedRecords.putIfAbsent(
                                canonicalRecord.name(), canonicalRecord);
                        if (previous != null) {
                            throw new IllegalStateException(
                                    "Runtime datasource registry contains duplicate canonical dataSource name: "
                                            + canonicalRecord.name());
                        }
                        needsCanonicalRewrite |= !canonicalRecord.name().equals(record.name());
                    }
                }
            }
            if (registry != null && registry.namespaceBindings() != null) {
                for (Map.Entry<String, String> entry
                        : registry.namespaceBindings().entrySet()) {
                    String canonicalNamespace = requireCanonicalValue(
                            entry.getKey(), "namespace");
                    String canonicalDataSource = requireCanonicalValue(
                            entry.getValue(), "dataSource");
                    String previous = loadedBindings.putIfAbsent(
                            canonicalNamespace, canonicalDataSource);
                    if (previous != null) {
                        throw new IllegalStateException(
                                "Runtime datasource registry contains duplicate canonical namespace: "
                                        + canonicalNamespace);
                    }
                    needsCanonicalRewrite |= !canonicalNamespace.equals(entry.getKey())
                            || !canonicalDataSource.equals(entry.getValue());
                }
            }
            if (registry != null && registry.namespaceBindingGenerations() != null) {
                for (Map.Entry<String, String> entry
                        : registry.namespaceBindingGenerations().entrySet()) {
                    String canonicalNamespace = requireCanonicalValue(
                            entry.getKey(), "namespace binding generation namespace");
                    String previous = loadedBindingGenerations.putIfAbsent(
                            canonicalNamespace, entry.getValue());
                    if (previous != null) {
                        throw new IllegalStateException(
                                "Runtime datasource registry contains duplicate canonical namespace binding generation: "
                                        + canonicalNamespace);
                    }
                    needsCanonicalRewrite |= !canonicalNamespace.equals(entry.getKey());
                }
            }

            if (registry == null) {
                throw new IllegalStateException("Runtime datasource registry payload is empty");
            }
            if (registry.version() > RegistryFile.CURRENT_VERSION) {
                throw new IllegalStateException("Unsupported runtime datasource registry version: "
                        + registry.version());
            }
            boolean needsMigration = registry.version() < RegistryFile.CURRENT_VERSION;
            if (needsMigration) {
                loadedEpoch = UUID.randomUUID().toString();
                long sequence = 1L;
                Map<String, RuntimeDatasourceRecord> migratedRecords = new LinkedHashMap<>();
                for (RuntimeDatasourceRecord record : loadedRecords.values().stream()
                        .sorted(Comparator.comparing(RuntimeDatasourceRecord::name))
                        .toList()) {
                    migratedRecords.put(record.name(), record.withBindingGeneration(generation(loadedEpoch, sequence)));
                    sequence = advanceSequence(sequence);
                }
                Map<String, String> migratedBindingGenerations = new LinkedHashMap<>();
                for (String namespace : loadedBindings.keySet().stream().sorted().toList()) {
                    migratedBindingGenerations.put(namespace, generation(loadedEpoch, sequence));
                    sequence = advanceSequence(sequence);
                }
                loadedRecords = migratedRecords;
                loadedBindingGenerations = migratedBindingGenerations;
                loadedNextSequence = sequence;
                persist(loadedRecords, loadedBindings, loadedBindingGenerations, loadedEpoch, loadedNextSequence);
            } else {
                loadedEpoch = registry.registryEpoch();
                loadedNextSequence = registry.nextSequence();
                validateV2Registry(loadedRecords, loadedBindings, loadedBindingGenerations,
                        loadedEpoch, loadedNextSequence);
                if (needsCanonicalRewrite) {
                    persist(loadedRecords, loadedBindings, loadedBindingGenerations,
                            loadedEpoch, loadedNextSequence);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read runtime datasource registry: " + path, e);
        }
        replaceRecords(loadedRecords);
        replaceNamespaceBindings(loadedBindings);
        replaceNamespaceBindingGenerations(loadedBindingGenerations);
        registryEpoch = loadedEpoch;
        nextSequence = loadedNextSequence;
        loaded = true;
    }

    private void persist(
            Map<String, RuntimeDatasourceRecord> datasourceRecords,
            Map<String, String> bindings,
            Map<String, String> bindingGenerations,
            String epoch,
            long next
    ) {
        if (!registryEnabled()) {
            return;
        }
        Path path = registryPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(temp.toFile(), new RegistryFile(
                            RegistryFile.CURRENT_VERSION,
                            epoch,
                            next,
                            new ArrayList<>(datasourceRecords.values()),
                            new LinkedHashMap<>(bindings),
                            new LinkedHashMap<>(bindingGenerations)
                    ));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException("Failed to write runtime datasource registry: " + path, e);
        }
    }

    private void validateV2Registry(
            Map<String, RuntimeDatasourceRecord> datasourceRecords,
            Map<String, String> bindings,
            Map<String, String> bindingGenerations,
            String epoch,
            long next
    ) {
        if (!StringUtils.hasText(epoch)) {
            throw new IllegalStateException("Runtime datasource registry is missing registryEpoch");
        }
        if (next < 1L) {
            throw new IllegalStateException("Runtime datasource registry nextSequence must be positive");
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        long maxSequence = 0L;
        for (RuntimeDatasourceRecord record : datasourceRecords.values()) {
            maxSequence = Math.max(maxSequence,
                    validateGeneration(record.bindingGeneration(), epoch, seen));
        }
        for (String namespace : bindings.keySet()) {
            String generation = bindingGenerations.get(namespace);
            maxSequence = Math.max(maxSequence, validateGeneration(generation, epoch, seen));
        }
        if (bindingGenerations.keySet().stream().anyMatch(key -> !bindings.containsKey(key))) {
            throw new IllegalStateException("Runtime datasource registry contains an orphan namespace binding generation");
        }
        if (next <= maxSequence) {
            throw new IllegalStateException("Runtime datasource registry nextSequence would reuse a binding generation");
        }
    }

    private long validateGeneration(String value, String epoch, Map<String, Boolean> seen) {
        if (!StringUtils.hasText(value) || seen.put(value, Boolean.TRUE) != null) {
            throw new IllegalStateException("Runtime datasource registry contains a missing or duplicate binding generation");
        }
        String prefix = epoch + ":";
        if (!value.startsWith(prefix)) {
            throw new IllegalStateException("Runtime datasource registry binding generation is outside its epoch");
        }
        try {
            long sequence = Long.parseLong(value.substring(prefix.length()));
            if (sequence < 1L) {
                throw new IllegalStateException("Runtime datasource registry binding generation sequence must be positive");
            }
            return sequence;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Runtime datasource registry binding generation is malformed", e);
        }
    }

    private static String generation(String epoch, long sequence) {
        if (!StringUtils.hasText(epoch) || sequence < 1L) {
            throw new IllegalStateException("Runtime datasource registry generation allocator is not initialized");
        }
        return epoch + ":" + sequence;
    }

    private static long advanceSequence(long sequence) {
        try {
            return Math.addExact(sequence, 1L);
        } catch (ArithmeticException e) {
            throw new IllegalStateException("Runtime datasource registry generation sequence is exhausted", e);
        }
    }

    private static RevokeMode effectiveMode(RevokeMode mode) {
        return mode != null ? mode : RevokeMode.DRAIN;
    }

    private static RuntimeDatasourceRecord canonicalRecord(
            RuntimeDatasourceRecord record
    ) {
        RuntimeDatasourceRecord required = Objects.requireNonNull(record, "record");
        String canonicalName = requireCanonicalValue(
                required.name(), "dataSource name");
        if (canonicalName.equals(required.name())) {
            return required;
        }
        return new RuntimeDatasourceRecord(
                canonicalName,
                required.type(),
                required.jdbcUrl(),
                required.username(),
                required.password(),
                required.passwordRef(),
                required.enabled(),
                required.createdAt(),
                required.updatedAt(),
                required.bindingGeneration()
        );
    }

    private static String canonicalOrDefault(String value) {
        String canonical = canonicalLookupValue(value);
        return canonical == null ? DEFAULT_DATASOURCE_NAME : canonical;
    }

    private static String canonicalLookupValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String requireCanonicalValue(String value, String label) {
        String canonical = canonicalLookupValue(value);
        if (canonical == null) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return canonical;
    }

    private void replaceRecords(Map<String, RuntimeDatasourceRecord> replacements) {
        records.clear();
        records.putAll(replacements);
    }

    private void replaceNamespaceBindings(Map<String, String> replacements) {
        namespaceBindings.clear();
        namespaceBindings.putAll(replacements);
    }

    private void replaceNamespaceBindingGenerations(Map<String, String> replacements) {
        namespaceBindingGenerations.clear();
        namespaceBindingGenerations.putAll(replacements);
    }

    private Path registryPath() {
        String configuredPath = properties.getDatasourceRegistry() != null ? properties.getDatasourceRegistry().getPath() : null;
        String path = StringUtils.hasText(configuredPath) ? configuredPath : ".foggy-runtime/runtime-datasources.json";
        return Path.of(path).toAbsolutePath().normalize();
    }

    public record ResolvedDatasource(String name, DataSource dataSource) {
    }

    public record RuntimeResolvedBinding(
            String name,
            DataSource dataSource,
            String bindingKey,
            String backendId,
            String generation,
            boolean cacheable
    ) {
    }

    private record BindingDescriptor(
            RuntimeDatasourceRecord record,
            String bindingKey,
            String generation,
            boolean cacheable
    ) {
    }

    public record RuntimeDatasourceRecord(
            String name,
            String type,
            String jdbcUrl,
            String username,
            String password,
            String passwordRef,
            boolean enabled,
            String createdAt,
            String updatedAt,
            String bindingGeneration
    ) {
        /**
         * Compatibility constructor for the v1 registry and existing callers.
         * The registry assigns a persisted generation before publication.
         */
        public RuntimeDatasourceRecord(
                String name,
                String type,
                String jdbcUrl,
                String username,
                String password,
                String passwordRef,
                boolean enabled,
                String createdAt,
                String updatedAt
        ) {
            this(name, type, jdbcUrl, username, password, passwordRef, enabled,
                    createdAt, updatedAt, null);
        }

        public RuntimeDatasourceRecord withUpdatedAt(String updatedAt) {
            return new RuntimeDatasourceRecord(name, type, jdbcUrl, username, password, passwordRef,
                    enabled, createdAt, updatedAt, bindingGeneration);
        }

        public RuntimeDatasourceRecord withBindingGeneration(String bindingGeneration) {
            return new RuntimeDatasourceRecord(name, type, jdbcUrl, username, password, passwordRef,
                    enabled, createdAt, updatedAt, bindingGeneration);
        }

        public RuntimeDatasourceRecord withLifecycle(String bindingGeneration, String updatedAt) {
            return new RuntimeDatasourceRecord(name, type, jdbcUrl, username, password, passwordRef,
                    enabled, createdAt, updatedAt, bindingGeneration);
        }
    }

    public record RegistryFile(
            int version,
            String registryEpoch,
            long nextSequence,
            List<RuntimeDatasourceRecord> datasources,
            Map<String, String> namespaceBindings,
            Map<String, String> namespaceBindingGenerations
    ) {
        public static final int CURRENT_VERSION = 2;

        /** Compatibility constructor for code that still creates a v1 shape. */
        public RegistryFile(
                int version,
                List<RuntimeDatasourceRecord> datasources,
                Map<String, String> namespaceBindings
        ) {
            this(version, null, 0L, datasources, namespaceBindings, Map.of());
        }

        public RegistryFile() {
            this(CURRENT_VERSION, null, 0L, List.of(), Map.of(), Map.of());
        }
    }
}

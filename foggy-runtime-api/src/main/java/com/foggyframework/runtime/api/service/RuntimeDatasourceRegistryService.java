package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.DatasourceInfo;
import com.foggyframework.runtime.api.dto.DatasourcePoolInfo;
import com.foggyframework.runtime.api.service.ManagedDataSourcePoolManager.ManagedDataSourcePoolState;
import org.springframework.beans.factory.ObjectProvider;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeDatasourceRegistryService {

    public static final String DEFAULT_DATASOURCE_NAME = "default";

    private final FoggyRuntimeApiProperties properties;
    private final ObjectProvider<DataSource> defaultDataSourceProvider;
    private final ObjectMapper objectMapper;
    private final ManagedDataSourcePoolManager poolManager;
    private final Map<String, RuntimeDatasourceRecord> records = new LinkedHashMap<>();
    private final Map<String, String> namespaceBindings = new LinkedHashMap<>();
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

    public synchronized List<RuntimeDatasourceRecord> listRecords() {
        loadIfNeeded();
        return List.copyOf(records.values());
    }

    public synchronized Optional<RuntimeDatasourceRecord> find(String name) {
        loadIfNeeded();
        return Optional.ofNullable(records.get(name));
    }

    public synchronized RuntimeDatasourceRecord save(RuntimeDatasourceRecord record) {
        loadIfNeeded();
        RuntimeDatasourceRecord normalized = record.withUpdatedAt(Instant.now().toString());
        RuntimeDatasourceRecord previous = records.get(normalized.name());
        records.put(normalized.name(), normalized);
        persist();
        poolManager.onRecordSaved(previous, normalized);
        return normalized;
    }

    public synchronized boolean remove(String name) {
        loadIfNeeded();
        RuntimeDatasourceRecord removed = records.remove(name);
        if (removed == null) {
            return false;
        }
        namespaceBindings.entrySet().removeIf(entry -> name.equals(entry.getValue()));
        persist();
        poolManager.remove(name);
        return true;
    }

    public synchronized Optional<String> getNamespaceDatasource(String namespace) {
        loadIfNeeded();
        return Optional.ofNullable(namespaceBindings.get(namespace));
    }

    public synchronized Map<String, String> listNamespaceBindings() {
        loadIfNeeded();
        return Map.copyOf(namespaceBindings);
    }

    public synchronized void bindNamespace(String namespace, String dataSource) {
        loadIfNeeded();
        namespaceBindings.put(namespace, dataSource);
        persist();
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
        return new RuntimeDatasourceRecord(name, type, jdbcUrl, username, password, passwordRef, enabled, now, now);
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
        String normalized = StringUtils.hasText(name) ? name : DEFAULT_DATASOURCE_NAME;
        if (DEFAULT_DATASOURCE_NAME.equals(normalized)) {
            return hasDefaultDataSource();
        }
        loadIfNeeded();
        RuntimeDatasourceRecord record = records.get(normalized);
        return record != null && record.enabled();
    }

    public Optional<ResolvedDatasource> resolve(String name) {
        String normalized = StringUtils.hasText(name) ? name : DEFAULT_DATASOURCE_NAME;
        if (DEFAULT_DATASOURCE_NAME.equals(normalized)) {
            DataSource dataSource = defaultDataSourceProvider.getIfAvailable();
            if (dataSource == null) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedDatasource(DEFAULT_DATASOURCE_NAME, dataSource));
        }
        RuntimeDatasourceRecord record = find(normalized).orElse(null);
        if (record == null || !record.enabled()) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedDatasource(record.name(), poolManager.resolve(record)));
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
        loaded = true;
        records.clear();
        namespaceBindings.clear();
        if (!registryEnabled()) {
            return;
        }
        Path path = registryPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            RegistryFile registry = objectMapper.readValue(path.toFile(), RegistryFile.class);
            if (registry != null && registry.datasources() != null) {
                for (RuntimeDatasourceRecord record : registry.datasources()) {
                    if (record != null && StringUtils.hasText(record.name())) {
                        records.put(record.name(), record);
                    }
                }
            }
            if (registry != null && registry.namespaceBindings() != null) {
                namespaceBindings.putAll(registry.namespaceBindings());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read runtime datasource registry: " + path, e);
        }
    }

    private void persist() {
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
                            1,
                            new ArrayList<>(records.values()),
                            new LinkedHashMap<>(namespaceBindings)
                    ));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException("Failed to write runtime datasource registry: " + path, e);
        }
    }

    private Path registryPath() {
        String configuredPath = properties.getDatasourceRegistry() != null ? properties.getDatasourceRegistry().getPath() : null;
        String path = StringUtils.hasText(configuredPath) ? configuredPath : ".foggy-runtime/runtime-datasources.json";
        return Path.of(path).toAbsolutePath().normalize();
    }

    public record ResolvedDatasource(String name, DataSource dataSource) {
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
            String updatedAt
    ) {
        public RuntimeDatasourceRecord withUpdatedAt(String updatedAt) {
            return new RuntimeDatasourceRecord(name, type, jdbcUrl, username, password, passwordRef, enabled, createdAt, updatedAt);
        }
    }

    public record RegistryFile(
            int version,
            List<RuntimeDatasourceRecord> datasources,
            Map<String, String> namespaceBindings
    ) {
        public RegistryFile() {
            this(1, List.of(), Map.of());
        }
    }
}

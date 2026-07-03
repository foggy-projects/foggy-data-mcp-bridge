package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.DatasourceInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeDatasourceRegistryService {

    public static final String DEFAULT_DATASOURCE_NAME = "default";

    private final FoggyRuntimeApiProperties properties;
    private final ObjectProvider<DataSource> defaultDataSourceProvider;
    private final ObjectMapper objectMapper;
    private final Map<String, RuntimeDatasourceRecord> records = new LinkedHashMap<>();
    private final Map<String, String> namespaceBindings = new LinkedHashMap<>();
    private boolean loaded;

    public RuntimeDatasourceRegistryService(
            FoggyRuntimeApiProperties properties,
            ObjectProvider<DataSource> defaultDataSourceProvider,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.defaultDataSourceProvider = defaultDataSourceProvider;
        this.objectMapper = objectMapper;
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
        records.put(normalized.name(), normalized);
        persist();
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
        return true;
    }

    public synchronized Optional<String> getNamespaceDatasource(String namespace) {
        loadIfNeeded();
        return Optional.ofNullable(namespaceBindings.get(namespace));
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
        return Optional.of(new ResolvedDatasource(record.name(), buildDataSource(record)));
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
                null
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
                message
        );
    }

    public DataSource buildDataSource(RuntimeDatasourceRecord record) {
        String jdbcUrl = record.jdbcUrl();
        if (!StringUtils.hasText(jdbcUrl) || !jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            throw new IllegalArgumentException("Runtime-managed dataSource requires jdbcUrl starting with jdbc:");
        }
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        String driverClassName = driverClassNameFor(jdbcUrl);
        if (StringUtils.hasText(driverClassName)) {
            dataSource.setDriverClassName(driverClassName);
        }
        dataSource.setUrl(jdbcUrl);
        if (StringUtils.hasText(record.username())) {
            dataSource.setUsername(record.username());
        }
        String password = resolvePassword(record);
        if (password != null) {
            dataSource.setPassword(password);
        }
        return dataSource;
    }

    private String driverClassNameFor(String jdbcUrl) {
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (lower.startsWith("jdbc:sqlserver:")) {
            return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
        }
        if (lower.startsWith("jdbc:sqlite:")) {
            return "org.sqlite.JDBC";
        }
        if (lower.startsWith("jdbc:oracle:")) {
            return "oracle.jdbc.OracleDriver";
        }
        if (lower.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        if (lower.startsWith("jdbc:mariadb:")) {
            return "org.mariadb.jdbc.Driver";
        }
        return null;
    }

    private String resolvePassword(RuntimeDatasourceRecord record) {
        if (record.password() != null) {
            return record.password();
        }
        String ref = record.passwordRef();
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        String resolved = resolvePasswordRef(ref);
        if (resolved == null) {
            throw new IllegalArgumentException("Runtime-managed dataSource passwordRef could not be resolved: " + ref);
        }
        return resolved;
    }

    private String resolvePasswordRef(String ref) {
        if (ref.startsWith("env:")) {
            return System.getenv(ref.substring("env:".length()));
        }
        if (ref.startsWith("system:")) {
            return System.getProperty(ref.substring("system:".length()));
        }
        if (ref.startsWith("sys:")) {
            return System.getProperty(ref.substring("sys:".length()));
        }
        String envValue = System.getenv(ref);
        if (envValue != null) {
            return envValue;
        }
        return System.getProperty(ref);
    }

    private boolean registryEnabled() {
        return properties.getDatasourceRegistry() == null || properties.getDatasourceRegistry().isEnabled();
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

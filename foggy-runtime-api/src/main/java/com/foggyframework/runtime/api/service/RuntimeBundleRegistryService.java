package com.foggyframework.runtime.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foggyframework.bundle.SystemBundlesContext;
import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
public class RuntimeBundleRegistryService {

    private final FoggyRuntimeApiProperties properties;
    private final SystemBundlesContext systemBundlesContext;
    private final ObjectMapper objectMapper;
    private final RuntimeAuthoringStorePathPolicy authoringPathPolicy;
    private final Map<String, RuntimeBundleRecord> records = new LinkedHashMap<>();
    private boolean loaded;

    public RuntimeBundleRegistryService(
            FoggyRuntimeApiProperties properties,
            SystemBundlesContext systemBundlesContext,
            ObjectMapper objectMapper
    ) {
        this(properties, systemBundlesContext, objectMapper, null);
    }

    @Autowired
    public RuntimeBundleRegistryService(
            FoggyRuntimeApiProperties properties,
            SystemBundlesContext systemBundlesContext,
            ObjectMapper objectMapper,
            RuntimeAuthoringStorePathPolicy authoringPathPolicy
    ) {
        this.properties = properties;
        this.systemBundlesContext = systemBundlesContext;
        this.objectMapper = objectMapper;
        this.authoringPathPolicy = authoringPathPolicy;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void restoreOnReady() {
        if (!registryEnabled()) {
            if (authoringPathPolicy != null) {
                authoringPathPolicy.assertStoreDisjoint(List.of());
            }
            return;
        }
        loadIfNeeded();
        if (authoringPathPolicy != null) {
            authoringPathPolicy.assertStoreDisjoint(records.values());
        }
        for (RuntimeBundleRecord record : records.values()) {
            if (!record.enabled()) {
                continue;
            }
            if (systemBundlesContext.containBundle(record.name())) {
                continue;
            }
            systemBundlesContext.addExternalBundle(record.name(), record.namespace(), record.path(), record.watch());
        }
    }

    public synchronized List<RuntimeBundleRecord> listRecords() {
        loadIfNeeded();
        return List.copyOf(records.values());
    }

    public synchronized Optional<RuntimeBundleRecord> find(String name) {
        loadIfNeeded();
        return Optional.ofNullable(records.get(name));
    }

    public synchronized RuntimeBundleRecord save(RuntimeBundleRecord record) {
        loadIfNeeded();
        RuntimeBundleRecord normalized = record.withUpdatedAt(Instant.now().toString());
        RuntimeBundleRecord previous = records.put(normalized.name(), normalized);
        try {
            persist();
        } catch (RuntimeException persistenceFailure) {
            if (previous == null) {
                records.remove(normalized.name());
            } else {
                records.put(previous.name(), previous);
            }
            throw persistenceFailure;
        }
        return normalized;
    }

    public synchronized boolean remove(String name) {
        loadIfNeeded();
        RuntimeBundleRecord removed = records.remove(name);
        if (removed != null) {
            try {
                persist();
            } catch (RuntimeException persistenceFailure) {
                records.put(removed.name(), removed);
                throw persistenceFailure;
            }
            return true;
        }
        return false;
    }

    public RuntimeBundleRecord newRecord(String name, String namespace, String path, boolean watch, boolean enabled) {
        String now = Instant.now().toString();
        return new RuntimeBundleRecord(name, namespace, path, watch, enabled, now, now);
    }

    private boolean registryEnabled() {
        return properties.getBundleRegistry() == null || properties.getBundleRegistry().isEnabled();
    }

    private void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;
        records.clear();
        if (!registryEnabled()) {
            return;
        }
        Path path = registryPath();
        if (!Files.exists(path)) {
            return;
        }
        try {
            RegistryFile registry = objectMapper.readValue(path.toFile(), RegistryFile.class);
            if (registry != null && registry.bundles() != null) {
                for (RuntimeBundleRecord record : registry.bundles()) {
                    if (record != null && StringUtils.hasText(record.name())) {
                        records.put(record.name(), record);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read runtime bundle registry: " + path, e);
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
                    .writeValue(temp.toFile(), new RegistryFile(1, new ArrayList<>(records.values())));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException e) {
            throw new IllegalStateException("Failed to write runtime bundle registry: " + path, e);
        }
    }

    private Path registryPath() {
        String configuredPath = properties.getBundleRegistry() != null ? properties.getBundleRegistry().getPath() : null;
        String path = StringUtils.hasText(configuredPath) ? configuredPath : ".foggy-runtime/runtime-bundles.json";
        return Path.of(path).toAbsolutePath().normalize();
    }

    public record RuntimeBundleRecord(
            String name,
            String namespace,
            String path,
            boolean watch,
            boolean enabled,
            String createdAt,
            String updatedAt
    ) {
        public RuntimeBundleRecord withUpdatedAt(String updatedAt) {
            return new RuntimeBundleRecord(name, namespace, path, watch, enabled, createdAt, updatedAt);
        }
    }

    public record RegistryFile(
            int version,
            List<RuntimeBundleRecord> bundles
    ) {
        public RegistryFile() {
            this(1, List.of());
        }
    }
}

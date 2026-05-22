package com.foggyframework.dataset.mcp.experience;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "foggy.mcp.experience-recipe.registry", name = "store", havingValue = "memory", matchIfMissing = true)
public class InMemoryExperienceRecipeRegistryStore implements ExperienceRecipeRegistryStore {
    private final Map<String, ExperienceRecipeRegistryEntry> registry = new ConcurrentHashMap<>();
    private final Map<String, ExperienceRecipeRegistryEvent> eventsByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public Optional<ExperienceRecipeRegistryEntry> findByRegistryKey(String registryKey) {
        ExperienceRecipeRegistryEntry entry = registry.get(registryKey);
        return Optional.ofNullable(entry == null ? null : entry.copy());
    }

    @Override
    public Optional<ExperienceRecipeRegistryEvent> findEventByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(eventsByIdempotencyKey.get(idempotencyKey));
    }

    @Override
    public void save(ExperienceRecipeRegistryEntry entry) {
        ExperienceRecipeRegistryEntry current = registry.get(entry.getRegistryKey());
        Long expectedRecordVersion = entry.getRecordVersion();
        if (expectedRecordVersion == null && current != null) {
            expectedRecordVersion = current.getRecordVersion();
        }
        if (!saveWithVersionCheck(entry, expectedRecordVersion)) {
            throw new IllegalStateException(
                    "Experience recipe record version changed: " + entry.getRegistryKey());
        }
    }

    @Override
    public synchronized boolean saveWithVersionCheck(
            ExperienceRecipeRegistryEntry entry,
            Long expectedRecordVersion) {
        ExperienceRecipeRegistryEntry current = registry.get(entry.getRegistryKey());
        if (current == null) {
            if (expectedRecordVersion != null && expectedRecordVersion > 0) {
                return false;
            }
            entry.setRecordVersion(1L);
            registry.put(entry.getRegistryKey(), entry.copy());
            return true;
        }
        Long currentVersion = current.getRecordVersion() == null ? 0L : current.getRecordVersion();
        if (expectedRecordVersion == null || !currentVersion.equals(expectedRecordVersion)) {
            return false;
        }
        entry.setRecordVersion(currentVersion + 1);
        registry.put(entry.getRegistryKey(), entry.copy());
        return true;
    }

    @Override
    public void appendEvent(ExperienceRecipeRegistryEvent event) {
        if (event.getIdempotencyKey() != null && !event.getIdempotencyKey().isBlank()) {
            eventsByIdempotencyKey.put(event.getIdempotencyKey(), event);
        }
    }

    @Override
    public List<ExperienceRecipeRegistryEntry> findDiscoverable() {
        return registry.values().stream()
                .filter(ExperienceRecipeRegistryEntry::discoverable)
                .sorted(Comparator.comparing(ExperienceRecipeRegistryEntry::getRegistryKey))
                .map(ExperienceRecipeRegistryEntry::copy)
                .toList();
    }
}

package com.foggyframework.dataset.mcp.experience;

import java.util.List;
import java.util.Optional;

public interface ExperienceRecipeRegistryStore {

    Optional<ExperienceRecipeRegistryEntry> findByRegistryKey(String registryKey);

    Optional<ExperienceRecipeRegistryEvent> findEventByIdempotencyKey(String idempotencyKey);

    void save(ExperienceRecipeRegistryEntry entry);

    default boolean saveWithVersionCheck(ExperienceRecipeRegistryEntry entry, Long expectedRecordVersion) {
        save(entry);
        return true;
    }

    void appendEvent(ExperienceRecipeRegistryEvent event);

    List<ExperienceRecipeRegistryEntry> findDiscoverable();
}

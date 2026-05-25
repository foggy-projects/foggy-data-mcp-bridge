package com.foggyframework.dataviewer.service.listpreset;

import com.foggyframework.dataviewer.domain.ListPresetDef;
import com.foggyframework.dataviewer.repository.ListPresetRepository;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

/**
 * Mongo 自定义列表存储。
 */
@RequiredArgsConstructor
public class MongoListPresetStore implements ListPresetStore {

    private final ListPresetRepository repository;

    @Override
    public ListPresetDef save(ListPresetDef preset) {
        return repository.save(preset);
    }

    @Override
    public List<ListPresetDef> list(String userId, String model, String businessKey) {
        return repository.findByOwnerIdAndModelAndBusinessKeyOrderByUpdatedAtDesc(userId, model, businessKey);
    }

    @Override
    public Optional<ListPresetDef> findById(String userId, String presetId) {
        return repository.findByIdAndOwnerId(presetId, userId);
    }

    @Override
    public Optional<ListPresetDef> findDefault(String userId, String model, String businessKey) {
        return repository.findFirstByOwnerIdAndModelAndBusinessKeyAndIsDefaultTrue(userId, model, businessKey);
    }

    @Override
    public void clearDefault(String userId, String model, String businessKey) {
        List<ListPresetDef> presets = repository.findByOwnerIdAndModelAndBusinessKeyAndIsDefaultTrue(
                userId,
                model,
                businessKey);
        for (ListPresetDef preset : presets) {
            preset.setIsDefault(false);
        }
        repository.saveAll(presets);
    }

    @Override
    public void delete(ListPresetDef preset) {
        repository.delete(preset);
    }
}

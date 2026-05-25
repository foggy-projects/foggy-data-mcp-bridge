package com.foggyframework.dataviewer.service.listpreset;

import com.foggyframework.dataviewer.domain.ListPresetDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

import java.util.List;
import java.util.Optional;

/**
 * Mongo 优先，失败时降级到文件系统存储。
 */
@Slf4j
public class FallbackListPresetStore implements ListPresetStore {

    private final ListPresetStore primary;
    private final ListPresetStore fallback;

    public FallbackListPresetStore(ListPresetStore primary, ListPresetStore fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public ListPresetDef save(ListPresetDef preset) {
        return execute(store -> store.save(preset));
    }

    @Override
    public List<ListPresetDef> list(String userId, String model, String businessKey) {
        return execute(store -> store.list(userId, model, businessKey));
    }

    @Override
    public Optional<ListPresetDef> findById(String userId, String presetId) {
        return execute(store -> store.findById(userId, presetId));
    }

    @Override
    public Optional<ListPresetDef> findDefault(String userId, String model, String businessKey) {
        return execute(store -> store.findDefault(userId, model, businessKey));
    }

    @Override
    public void clearDefault(String userId, String model, String businessKey) {
        execute(store -> {
            store.clearDefault(userId, model, businessKey);
            return null;
        });
    }

    @Override
    public void delete(ListPresetDef preset) {
        execute(store -> {
            store.delete(preset);
            return null;
        });
    }

    private <T> T execute(StoreOperation<T> operation) {
        if (primary == null) {
            return operation.apply(fallback);
        }
        try {
            return operation.apply(primary);
        } catch (DataAccessException | IllegalStateException ex) {
            log.warn("List preset Mongo store unavailable, falling back to file store: {}", ex.getMessage());
            return operation.apply(fallback);
        }
    }

    private interface StoreOperation<T> {
        T apply(ListPresetStore store);
    }
}

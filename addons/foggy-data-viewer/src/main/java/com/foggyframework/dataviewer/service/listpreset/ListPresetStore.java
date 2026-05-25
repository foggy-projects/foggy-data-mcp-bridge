package com.foggyframework.dataviewer.service.listpreset;

import com.foggyframework.dataviewer.domain.ListPresetDef;

import java.util.List;
import java.util.Optional;

/**
 * 自定义列表存储抽象。
 */
public interface ListPresetStore {

    ListPresetDef save(ListPresetDef preset);

    List<ListPresetDef> list(String userId, String model, String businessKey);

    Optional<ListPresetDef> findById(String userId, String presetId);

    Optional<ListPresetDef> findDefault(String userId, String model, String businessKey);

    void clearDefault(String userId, String model, String businessKey);

    void delete(ListPresetDef preset);
}

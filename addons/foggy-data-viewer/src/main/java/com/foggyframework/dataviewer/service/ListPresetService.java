package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.ListPresetDef;
import com.foggyframework.dataviewer.domain.QueryVisibility;
import com.foggyframework.dataviewer.service.listpreset.ListPresetFieldValidator;
import com.foggyframework.dataviewer.service.listpreset.ListPresetStore;
import com.foggyframework.dataset.model.def.query.request.CondRequestDef;
import com.foggyframework.dataset.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.model.def.query.request.SliceRequestDef;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 自定义列表服务。
 */
@Slf4j
public class ListPresetService {

    public static final int MAX_USER_CONFIGURED_FIELDS = 50;
    public static final int MAX_USER_CONDITION_LEAVES = 20;

    private final ListPresetStore store;
    private final ListPresetFieldValidator fieldValidator;

    public ListPresetService(ListPresetStore store) {
        this(store, ListPresetFieldValidator.noop());
    }

    public ListPresetService(ListPresetStore store, ListPresetFieldValidator fieldValidator) {
        this.store = store;
        this.fieldValidator = fieldValidator != null ? fieldValidator : ListPresetFieldValidator.noop();
    }

    public List<ListPresetDef> list(String userId, String model, String businessKey) {
        validateScope(userId, model);
        return store.list(userId, model, normalizeBusinessKey(businessKey));
    }

    public Optional<ListPresetDef> getDefault(String userId, String model, String businessKey) {
        validateScope(userId, model);
        return store.findDefault(userId, model, normalizeBusinessKey(businessKey));
    }

    public ListPresetDef create(String userId, String model, String businessKey, SaveListPresetRequest request) {
        validateScope(userId, model);
        validateRequest(request, true);

        Instant now = Instant.now();
        String normalizedBusinessKey = normalizeBusinessKey(businessKey);
        fieldValidator.validate(userId, model, normalizedBusinessKey, request);
        ListPresetDef preset = ListPresetDef.builder()
                .id(UUID.randomUUID().toString())
                .model(model)
                .businessKey(normalizedBusinessKey)
                .title(request.getTitle())
                .description(request.getDescription())
                .columns(request.getColumns())
                .columnSettings(request.getColumnSettings())
                .query(defaultQuery(request.getQuery()))
                .pageSize(request.getPageSize())
                .visibility(request.getVisibility() != null ? request.getVisibility() : QueryVisibility.PRIVATE)
                .ownerId(userId)
                .isDefault(Boolean.TRUE.equals(request.getIsDefault()))
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .build();

        if (Boolean.TRUE.equals(preset.getIsDefault())) {
            store.clearDefault(userId, model, normalizedBusinessKey);
        }
        log.info("Saving list preset '{}' for user {} on model {}", preset.getTitle(), userId, model);
        return store.save(preset);
    }

    public Optional<ListPresetDef> get(String userId, String presetId) {
        validateUserId(userId);
        return store.findById(userId, presetId);
    }

    public Optional<ListPresetDef> update(String userId, String presetId, SaveListPresetRequest request) {
        validateUserId(userId);
        validateRequest(request, false);
        return store.findById(userId, presetId).map(preset -> {
            SaveListPresetRequest merged = mergeRequest(preset, request);
            validateRequest(merged, false);
            fieldValidator.validate(userId, preset.getModel(), preset.getBusinessKey(), merged);
            preset.setTitle(merged.getTitle());
            preset.setDescription(merged.getDescription());
            preset.setColumns(merged.getColumns());
            preset.setColumnSettings(merged.getColumnSettings());
            preset.setQuery(defaultQuery(merged.getQuery()));
            preset.setPageSize(merged.getPageSize());
            preset.setVisibility(merged.getVisibility());
            preset.setIsDefault(merged.getIsDefault());
            preset.setUpdatedAt(Instant.now());
            if (Boolean.TRUE.equals(preset.getIsDefault())) {
                store.clearDefault(userId, preset.getModel(), preset.getBusinessKey());
            }
            return store.save(preset);
        });
    }

    public boolean delete(String userId, String presetId) {
        validateUserId(userId);
        return store.findById(userId, presetId)
                .map(preset -> {
                    store.delete(preset);
                    log.info("Deleted list preset '{}' by user {}", preset.getTitle(), userId);
                    return true;
                })
                .orElse(false);
    }

    public Optional<ListPresetDef> setDefault(String userId, String presetId) {
        validateUserId(userId);
        return store.findById(userId, presetId).map(preset -> {
            store.clearDefault(userId, preset.getModel(), preset.getBusinessKey());
            preset.setIsDefault(true);
            preset.setUpdatedAt(Instant.now());
            return store.save(preset);
        });
    }

    public void clearDefault(String userId, String model, String businessKey) {
        validateScope(userId, model);
        store.clearDefault(userId, model, normalizeBusinessKey(businessKey));
    }

    private ListPresetDef.QueryConditionPreset defaultQuery(ListPresetDef.QueryConditionPreset query) {
        if (query == null) {
            return new ListPresetDef.QueryConditionPreset(List.of(), List.of());
        }
        if (query.getSlice() == null) {
            query.setSlice(List.of());
        }
        if (query.getOrderBy() == null) {
            query.setOrderBy(List.of());
        }
        return query;
    }

    private void validateScope(String userId, String model) {
        validateUserId(userId);
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model 不能为空");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
    }

    private void validateRequest(SaveListPresetRequest request, boolean create) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (create && (request.getTitle() == null || request.getTitle().isBlank())) {
            throw new IllegalArgumentException("title 不能为空");
        }
        if (create && (request.getColumns() == null || request.getColumns().isEmpty())) {
            throw new IllegalArgumentException("columns 不能为空");
        }
        if (request.getColumns() != null) {
            if (request.getColumns().isEmpty()) {
                throw new IllegalArgumentException("columns 不能为空");
            }
            for (String column : request.getColumns()) {
                validateFieldName(column, "columns 不能包含空字段");
            }
        }
        if (request.getColumnSettings() != null) {
            for (ListPresetDef.ColumnViewSetting setting : request.getColumnSettings()) {
                if (setting == null) throw new IllegalArgumentException("columnSettings 不能包含空配置");
                validateFieldName(setting.getName(), "columnSettings 不能包含空字段");
                if (setting.getFixed() != null
                        && !"left".equals(setting.getFixed())
                        && !"right".equals(setting.getFixed())) {
                    throw new IllegalArgumentException("fixed 只能是 left 或 right");
                }
            }
        }
        if (request.getQuery() != null) {
            validateSlices(request.getQuery().getSlice());
            validateOrderBy(request.getQuery().getOrderBy());
        }
        validateConfiguredFieldCount(request.getColumns(), request.getColumnSettings());
        if (request.getPageSize() != null && request.getPageSize() <= 0) {
            throw new IllegalArgumentException("pageSize 必须大于 0");
        }
    }

    private void validateSlices(List<SliceRequestDef> slices) {
        if (slices == null) {
            return;
        }
        int leafCount = 0;
        for (SliceRequestDef slice : slices) {
            validateCondition(slice);
            leafCount += countConditionLeaves(slice);
        }
        if (leafCount > MAX_USER_CONDITION_LEAVES) {
            throw new IllegalArgumentException("slice 最多包含 " + MAX_USER_CONDITION_LEAVES
                    + " 个叶子条件，当前 " + leafCount + " 个");
        }
    }

    private int countConditionLeaves(CondRequestDef condition) {
        if (condition._isLogicalGroup()) {
            List<CondRequestDef> children = condition._getGroupChildren();
            int count = 0;
            for (CondRequestDef child : children) {
                count += countConditionLeaves(child);
            }
            return count;
        }
        return 1;
    }

    private void validateConfiguredFieldCount(
            List<String> columns,
            List<ListPresetDef.ColumnViewSetting> columnSettings) {
        Set<String> fields = new LinkedHashSet<>();
        if (columns != null) {
            fields.addAll(columns);
        }
        if (columnSettings != null) {
            for (ListPresetDef.ColumnViewSetting setting : columnSettings) {
                if (setting != null && setting.getName() != null) {
                    fields.add(setting.getName());
                }
            }
        }
        if (fields.size() > MAX_USER_CONFIGURED_FIELDS) {
            throw new IllegalArgumentException("自定义查询最多配置 " + MAX_USER_CONFIGURED_FIELDS
                    + " 个字段，当前 " + fields.size() + " 个");
        }
    }

    private SaveListPresetRequest mergeRequest(ListPresetDef preset, SaveListPresetRequest request) {
        SaveListPresetRequest merged = new SaveListPresetRequest();
        merged.setTitle(request.getTitle() != null ? request.getTitle() : preset.getTitle());
        merged.setDescription(request.getDescription() != null ? request.getDescription() : preset.getDescription());
        merged.setColumns(request.getColumns() != null ? request.getColumns() : preset.getColumns());
        merged.setColumnSettings(request.getColumnSettings() != null ? request.getColumnSettings() : preset.getColumnSettings());
        merged.setQuery(request.getQuery() != null ? request.getQuery() : preset.getQuery());
        merged.setPageSize(request.getPageSize() != null ? request.getPageSize() : preset.getPageSize());
        merged.setVisibility(request.getVisibility() != null ? request.getVisibility() : preset.getVisibility());
        merged.setIsDefault(request.getIsDefault() != null ? request.getIsDefault() : preset.getIsDefault());
        return merged;
    }

    private void validateCondition(CondRequestDef condition) {
        if (condition == null) {
            throw new IllegalArgumentException("slice 不能包含空条件");
        }
        if (condition.getOr() != null && condition.getAnd() != null) {
            throw new IllegalArgumentException("slice 条件组只能使用一种逻辑运算");
        }
        if ((condition.getOr() != null && condition.getOr().isEmpty())
                || (condition.getAnd() != null && condition.getAnd().isEmpty())) {
            throw new IllegalArgumentException("slice 条件组不能为空");
        }
        if (condition._isLogicalGroup()) {
            List<CondRequestDef> children = condition._getGroupChildren();
            if (children == null || children.isEmpty()) {
                throw new IllegalArgumentException("slice 条件组不能为空");
            }
            for (CondRequestDef child : children) {
                validateCondition(child);
            }
            return;
        }
        if (condition._isExpressionCondition()) {
            return;
        }
        validateFieldName(condition.getField(), "slice 不能包含空字段");
        if (condition._isFieldReference()) {
            validateFieldName(condition._getReferencedField(), "slice 不能包含空字段引用");
        }
    }

    private void validateOrderBy(List<OrderRequestDef> orderBy) {
        if (orderBy == null) {
            return;
        }
        for (OrderRequestDef order : orderBy) {
            validateFieldName(order.getField(), "orderBy 不能包含空字段");
        }
    }

    private void validateFieldName(String fieldName, String message) {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeBusinessKey(String businessKey) {
        return businessKey == null || businessKey.isBlank() ? "" : businessKey;
    }

    @Data
    public static class SaveListPresetRequest {
        private String title;
        private String description;
        private List<String> columns;
        private List<ListPresetDef.ColumnViewSetting> columnSettings;
        private ListPresetDef.QueryConditionPreset query;
        private Integer pageSize;
        private QueryVisibility visibility;
        private Boolean isDefault;
    }
}

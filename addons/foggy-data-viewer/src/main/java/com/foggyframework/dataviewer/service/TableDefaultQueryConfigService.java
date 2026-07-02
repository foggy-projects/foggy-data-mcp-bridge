package com.foggyframework.dataviewer.service;

import com.foggyframework.dataviewer.domain.ListPresetDef;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfig;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfigRequest;
import com.foggyframework.dataviewer.service.tabledefault.TableDefaultQueryConfigProvider;
import com.foggyframework.dataset.db.model.def.query.request.OrderRequestDef;
import com.foggyframework.dataset.db.model.def.query.request.SliceRequestDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 解析表格实例默认查询配置。
 */
public class TableDefaultQueryConfigService {

    private final ListPresetService listPresetService;
    private final List<TableDefaultQueryConfigProvider> providers;

    public TableDefaultQueryConfigService(
            ListPresetService listPresetService,
            List<TableDefaultQueryConfigProvider> providers) {
        this.listPresetService = listPresetService;
        this.providers = providers != null ? providers : List.of();
    }

    public Optional<TableDefaultQueryConfig> resolve(TableDefaultQueryConfigRequest request) {
        validateRequest(request);

        Optional<TableDefaultQueryConfig> fallback = Boolean.FALSE.equals(request.getIncludeFallback())
                ? Optional.empty()
                : resolveFallback(request);
        Optional<TableDefaultQueryConfig> userDefault = resolveUserDefault(request);

        if (userDefault.isPresent()) {
            return Optional.of(mergeUserDefault(userDefault.get(), fallback.orElse(null), request));
        }
        return fallback.map(config -> normalize(config, request));
    }

    private Optional<TableDefaultQueryConfig> resolveUserDefault(TableDefaultQueryConfigRequest request) {
        if (listPresetService == null || isBlank(request.getUserId())) {
            return Optional.empty();
        }
        return listPresetService
                .getDefault(request.getUserId(), request.getQueryModel(), request.getTableInstanceId())
                .map(preset -> fromListPreset(preset, request));
    }

    private Optional<TableDefaultQueryConfig> resolveFallback(TableDefaultQueryConfigRequest request) {
        for (TableDefaultQueryConfigProvider provider : providers) {
            Optional<TableDefaultQueryConfig> resolved = provider.resolve(request)
                    .map(config -> normalize(config, request));
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

    private TableDefaultQueryConfig fromListPreset(
            ListPresetDef preset,
            TableDefaultQueryConfigRequest request) {
        ListPresetDef.QueryConditionPreset query = preset.getQuery();
        return TableDefaultQueryConfig.builder()
                .tableInstanceId(firstNonBlank(preset.getBusinessKey(), request.getTableInstanceId()))
                .queryModel(preset.getModel())
                .defaultVisibleColumns(safeStringList(preset.getColumns()))
                .defaultOrderBy(query != null ? safeOrderList(query.getOrderBy()) : List.of())
                .defaultPageSize(preset.getPageSize())
                .defaultSlices(query != null ? safeSliceList(query.getSlice()) : List.of())
                .version(preset.getVersion())
                .source("USER")
                .build();
    }

    private TableDefaultQueryConfig mergeUserDefault(
            TableDefaultQueryConfig userConfig,
            TableDefaultQueryConfig fallback,
            TableDefaultQueryConfigRequest request) {
        TableDefaultQueryConfig normalizedUser = normalize(userConfig, request);
        if (fallback != null && normalizedUser.getDefaultPageSize() == null) {
            TableDefaultQueryConfig normalizedFallback = normalize(fallback, request);
            normalizedUser.setDefaultPageSize(normalizedFallback.getDefaultPageSize());
        }
        return normalizedUser;
    }

    private TableDefaultQueryConfig normalize(
            TableDefaultQueryConfig config,
            TableDefaultQueryConfigRequest request) {
        TableDefaultQueryConfig normalized = TableDefaultQueryConfig.builder()
                .tableInstanceId(firstNonBlank(config.getTableInstanceId(), request.getTableInstanceId()))
                .queryModel(firstNonBlank(config.getQueryModel(), request.getQueryModel()))
                .defaultVisibleColumns(safeStringList(config.getDefaultVisibleColumns()))
                .defaultOrderBy(safeOrderList(config.getDefaultOrderBy()))
                .defaultPageSize(config.getDefaultPageSize())
                .defaultSlices(safeSliceList(config.getDefaultSlices()))
                .version(config.getVersion() != null ? config.getVersion() : 1)
                .source(firstNonBlank(config.getSource(), "FALLBACK"))
                .build();
        return normalized;
    }

    private void appendUnique(List<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null && !target.contains(trimmed)) {
                target.add(trimmed);
            }
        }
    }

    private List<String> safeStringList(List<String> values) {
        List<String> result = new ArrayList<>();
        appendUnique(result, values);
        return result;
    }

    private List<OrderRequestDef> safeOrderList(List<OrderRequestDef> values) {
        return values != null ? values : List.of();
    }

    private List<SliceRequestDef> safeSliceList(List<SliceRequestDef> values) {
        return values != null ? values : List.of();
    }

    private void validateRequest(TableDefaultQueryConfigRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (isBlank(request.getQueryModel())) {
            throw new IllegalArgumentException("queryModel 不能为空");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }
}

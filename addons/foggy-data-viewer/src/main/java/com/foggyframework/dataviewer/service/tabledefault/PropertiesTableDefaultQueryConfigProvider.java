package com.foggyframework.dataviewer.service.tabledefault;

import com.foggyframework.dataviewer.config.DataViewerProperties;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfig;
import com.foggyframework.dataviewer.domain.TableDefaultQueryConfigRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 foggy.data-viewer.table-defaults 的内置 fallback provider。
 */
public class PropertiesTableDefaultQueryConfigProvider implements TableDefaultQueryConfigProvider {

    private final DataViewerProperties properties;

    public PropertiesTableDefaultQueryConfigProvider(DataViewerProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<TableDefaultQueryConfig> resolve(TableDefaultQueryConfigRequest request) {
        DataViewerProperties.TableDefaultProperties tableDefaults = properties.getTableDefaults();
        if (tableDefaults == null) {
            return Optional.empty();
        }

        Optional<TableDefaultQueryConfig> tenantConfig = selectScoped(
                tableDefaults.getTenants(), request.getTenantId(), request)
                .map(config -> withSource(config, "TENANT"));
        if (tenantConfig.isPresent()) {
            return tenantConfig;
        }

        for (String roleId : safeList(request.getRoleIds())) {
            Optional<TableDefaultQueryConfig> roleConfig = selectScoped(
                    tableDefaults.getRoles(), roleId, request)
                    .map(config -> withSource(config, "ROLE"));
            if (roleConfig.isPresent()) {
                return roleConfig;
            }
        }

        return select(tableDefaults.getSystem(), request)
                .map(config -> withSource(config, "SYSTEM"));
    }

    private Optional<TableDefaultQueryConfig> selectScoped(
            Map<String, Map<String, TableDefaultQueryConfig>> scopedConfigs,
            String scopeId,
            TableDefaultQueryConfigRequest request) {
        if (scopeId == null || scopeId.isBlank() || scopedConfigs == null) {
            return Optional.empty();
        }
        return select(scopedConfigs.get(scopeId), request);
    }

    private Optional<TableDefaultQueryConfig> select(
            Map<String, TableDefaultQueryConfig> configs,
            TableDefaultQueryConfigRequest request) {
        if (configs == null || configs.isEmpty()) {
            return Optional.empty();
        }

        String tableInstanceId = trimToNull(request.getTableInstanceId());
        if (tableInstanceId != null) {
            TableDefaultQueryConfig exact = configs.get(tableInstanceId);
            if (matchesQueryModel(exact, request.getQueryModel())) {
                return Optional.of(exact);
            }
        }

        TableDefaultQueryConfig byModel = configs.get(request.getQueryModel());
        if (matchesQueryModel(byModel, request.getQueryModel())) {
            return Optional.of(byModel);
        }

        return configs.values().stream()
                .filter(config -> matchesQueryModel(config, request.getQueryModel()))
                .filter(config -> tableInstanceId == null
                        || tableInstanceId.equals(config.getTableInstanceId()))
                .findFirst();
    }

    private boolean matchesQueryModel(TableDefaultQueryConfig config, String queryModel) {
        return config != null
                && (config.getQueryModel() == null
                || config.getQueryModel().isBlank()
                || config.getQueryModel().equals(queryModel));
    }

    private TableDefaultQueryConfig withSource(TableDefaultQueryConfig config, String source) {
        return TableDefaultQueryConfig.builder()
                .tableInstanceId(config.getTableInstanceId())
                .queryModel(config.getQueryModel())
                .defaultVisibleColumns(config.getDefaultVisibleColumns())
                .defaultOrderBy(config.getDefaultOrderBy())
                .defaultPageSize(config.getDefaultPageSize())
                .defaultSlices(config.getDefaultSlices())
                .version(config.getVersion())
                .source(config.getSource() == null || config.getSource().isBlank()
                        ? source
                        : config.getSource())
                .build();
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}

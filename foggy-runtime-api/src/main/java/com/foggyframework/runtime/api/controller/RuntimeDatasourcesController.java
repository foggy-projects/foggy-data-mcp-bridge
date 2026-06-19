package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.DatasourceInfo;
import com.foggyframework.runtime.api.dto.DatasourceListResponse;
import com.foggyframework.runtime.api.dto.DatasourceMutationResponse;
import com.foggyframework.runtime.api.dto.DatasourceRequest;
import com.foggyframework.runtime.api.dto.DatasourceTestResponse;
import com.foggyframework.runtime.api.dto.NamespaceDatasourceRequest;
import com.foggyframework.runtime.api.dto.NamespaceDatasourceResponse;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeError;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.ResolvedDatasource;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeDatasourcesController {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties runtimeApiProperties;
    private final RuntimeDatasourceRegistryService registryService;

    public RuntimeDatasourcesController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            RuntimeDatasourceRegistryService registryService
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.registryService = registryService;
    }

    @GetMapping("/datasources")
    public RuntimeEnvelope<DatasourceListResponse> listDatasources() {
        return RuntimeEnvelope.ok(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                new DatasourceListResponse(registryService.listInfos(), List.of())
        );
    }

    @PostMapping("/datasources")
    public RuntimeEnvelope<DatasourceMutationResponse> addDatasource(@RequestBody(required = false) DatasourceRequest request) {
        return upsertDatasource(null, request, false);
    }

    @PutMapping("/datasources/{name}")
    public RuntimeEnvelope<DatasourceMutationResponse> updateDatasource(
            @PathVariable String name,
            @RequestBody(required = false) DatasourceRequest request
    ) {
        return upsertDatasource(name, request, true);
    }

    @DeleteMapping("/datasources/{name}")
    public RuntimeEnvelope<DatasourceMutationResponse> removeDatasource(@PathVariable String name) {
        String normalizedName = blankToNull(name);
        if (normalizedName == null) {
            return fail("INVALID_REQUEST", "datasources.remove", "Missing required path variable: name",
                    "Provide a runtime-managed dataSource name.", false);
        }
        RuntimeDatasourceRecord record = registryService.find(normalizedName).orElse(null);
        if (record == null) {
            return fail("DATASOURCE_NOT_MANAGED", "datasources.remove",
                    "DataSource is not managed by Runtime API: " + normalizedName,
                    "Only runtime-managed dataSources can be removed through Runtime API.", false);
        }
        registryService.remove(normalizedName);
        DatasourceInfo info = registryService.infoFromRecord(record, "removed", null);
        return RuntimeEnvelope.ok(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                new DatasourceMutationResponse(info, List.of())
        );
    }

    @PostMapping("/datasources/{name}/test")
    public RuntimeEnvelope<DatasourceTestResponse> testDatasource(@PathVariable String name) {
        String normalizedName = blankToNull(name);
        if (normalizedName == null) {
            return fail("INVALID_REQUEST", "datasources.test", "Missing required path variable: name",
                    "Provide a dataSource name.", false);
        }
        ResolvedDatasource resolved = registryService.resolve(normalizedName).orElse(null);
        if (resolved == null) {
            return fail("DATASOURCE_NOT_FOUND", "datasources.test", "DataSource not found or disabled: " + normalizedName,
                    "Add or enable the dataSource, then retry.", false);
        }
        try (Connection connection = resolved.dataSource().getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            DatasourceTestResponse response = new DatasourceTestResponse(
                    resolved.name(),
                    true,
                    meta.getDatabaseProductName(),
                    meta.getDatabaseProductVersion(),
                    meta.getDriverName(),
                    meta.getURL(),
                    List.of()
            );
            return RuntimeEnvelope.ok(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), response);
        } catch (Exception e) {
            return fail("DATASOURCE_TEST_FAILED", "datasources.test", e.getMessage(),
                    "Check the dataSource jdbcUrl and driver, then retry.", false);
        }
    }

    @GetMapping("/namespaces/{namespace}/datasource")
    public RuntimeEnvelope<NamespaceDatasourceResponse> getNamespaceDatasource(@PathVariable String namespace) {
        String normalizedNamespace = blankToNull(namespace);
        if (normalizedNamespace == null) {
            return fail("INVALID_REQUEST", "datasources.bind", "Missing required path variable: namespace",
                    "Provide a namespace.", false);
        }
        String dataSource = registryService.getNamespaceDatasource(normalizedNamespace).orElse(null);
        return RuntimeEnvelope.ok(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                new NamespaceDatasourceResponse(normalizedNamespace, dataSource, List.of())
        );
    }

    @PutMapping("/namespaces/{namespace}/datasource")
    public RuntimeEnvelope<NamespaceDatasourceResponse> bindNamespaceDatasource(
            @PathVariable String namespace,
            @RequestBody(required = false) NamespaceDatasourceRequest request
    ) {
        String normalizedNamespace = blankToNull(request != null && StringUtils.hasText(request.namespace())
                ? request.namespace()
                : namespace);
        if (normalizedNamespace == null) {
            return fail("INVALID_REQUEST", "datasources.bind", "Missing required field: namespace",
                    "Provide a namespace.", false);
        }
        String dataSource = blankToNull(request != null ? request.dataSource() : null);
        if (dataSource == null) {
            return fail("INVALID_REQUEST", "datasources.bind", "Missing required field: dataSource",
                    "Provide a dataSource name.", false);
        }
        if (registryService.resolve(dataSource).isEmpty()) {
            return fail("DATASOURCE_NOT_FOUND", "datasources.bind", "DataSource not found or disabled: " + dataSource,
                    "Add or enable the dataSource before binding it to a namespace.", false);
        }
        registryService.bindNamespace(normalizedNamespace, dataSource);
        return RuntimeEnvelope.ok(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                new NamespaceDatasourceResponse(normalizedNamespace, dataSource, List.of())
        );
    }

    private RuntimeEnvelope<DatasourceMutationResponse> upsertDatasource(
            String pathName,
            DatasourceRequest request,
            boolean update
    ) {
        String name = blankToNull(pathName != null ? pathName : request != null ? request.name() : null);
        if (name == null) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    "Missing required field: name", "Provide a dataSource name.", false);
        }
        if (RuntimeDatasourceRegistryService.DEFAULT_DATASOURCE_NAME.equals(name)) {
            return fail("DATASOURCE_NAME_CONFLICT", update ? "datasources.update" : "datasources.add",
                    "The default dataSource is configured by Spring and cannot be managed by Runtime API.",
                    "Choose a non-default runtime-managed dataSource name.", false);
        }
        String type = stringOr(request != null ? request.type() : null, "sqlite").toLowerCase();
        if (!"sqlite".equals(type)) {
            return fail("DATASOURCE_TYPE_UNSUPPORTED", update ? "datasources.update" : "datasources.add",
                    "Only sqlite runtime-managed dataSources are supported in this phase: " + type,
                    "Use type=sqlite for local demo and model exploration.", false);
        }
        String jdbcUrl = blankToNull(request != null ? request.jdbcUrl() : null);
        if (jdbcUrl == null) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    "Missing required field: jdbcUrl", "Provide a jdbc:sqlite: URL.", false);
        }
        if (!jdbcUrl.startsWith("jdbc:sqlite:")) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    "SQLite dataSource requires jdbcUrl starting with jdbc:sqlite:",
                    "Provide a valid SQLite JDBC URL.", false);
        }
        if (request != null && StringUtils.hasText(request.password())) {
            return fail("DATASOURCE_PASSWORD_UNSUPPORTED", update ? "datasources.update" : "datasources.add",
                    "Plaintext dataSource passwords are not supported in this development Runtime API phase.",
                    "Use passwordRef in a later secured phase; SQLite demos should not require passwords.", false);
        }

        RuntimeDatasourceRecord existingRecord = registryService.find(name).orElse(null);
        boolean replace = update || booleanOr(request != null ? request.replace() : null, false);
        if (existingRecord != null && !replace) {
            return fail("DATASOURCE_ALREADY_EXISTS", "datasources.add",
                    "Runtime-managed dataSource already exists: " + name,
                    "Use replace=true or datasources update.", true);
        }
        if (existingRecord == null && update) {
            return fail("DATASOURCE_NOT_MANAGED", "datasources.update",
                    "DataSource is not managed by Runtime API: " + name,
                    "Add the dataSource before updating it.", false);
        }

        RuntimeDatasourceRecord record = registryService.newRecord(
                name,
                type,
                jdbcUrl,
                blankToNull(request != null ? request.username() : null),
                blankToNull(request != null ? request.passwordRef() : null),
                booleanOr(request != null ? request.enabled() : null, true)
        );
        record = registryService.save(record);
        DatasourceInfo info = registryService.infoFromRecord(record, record.enabled() ? "active" : "disabled", null);
        return RuntimeEnvelope.ok(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                new DatasourceMutationResponse(info, List.of())
        );
    }

    private <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        RuntimeError error = new RuntimeError(
                code,
                phase,
                message,
                null,
                null,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
        return RuntimeEnvelope.fail(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), error, RuntimeDiagnostics.empty());
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static String stringOr(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static boolean booleanOr(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }
}

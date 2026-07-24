package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.RuntimeApiRoutes;
import com.foggyframework.runtime.api.dto.DatasourceDiagnosticsResponse;
import com.foggyframework.runtime.api.dto.DatasourceInfo;
import com.foggyframework.runtime.api.dto.DatasourceListResponse;
import com.foggyframework.runtime.api.dto.DatasourceMutationResponse;
import com.foggyframework.runtime.api.dto.DatasourceRequest;
import com.foggyframework.runtime.api.dto.DatasourceTestResponse;
import com.foggyframework.runtime.api.dto.NamespaceDatasourceRequest;
import com.foggyframework.runtime.api.dto.NamespaceDatasourceResponse;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.service.RuntimeApiResponseFactory;
import com.foggyframework.dataset.model.lifecycle.port.RevokeMode;
import com.foggyframework.runtime.api.service.ManagedDataSourcePoolManager;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping(RuntimeApiRoutes.API_V1)
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeDatasourcesController {

    private final RuntimeApiResponseFactory responses;
    private final RuntimeDatasourceRegistryService registryService;

    public RuntimeDatasourcesController(
            RuntimeApiResponseFactory responses,
            RuntimeDatasourceRegistryService registryService
    ) {
        this.responses = responses;
        this.registryService = registryService;
    }

    @GetMapping(RuntimeApiRoutes.V1.DATASOURCES)
    public RuntimeEnvelope<DatasourceListResponse> listDatasources() {
        return responses.ok(new DatasourceListResponse(registryService.listInfos(), List.of()));
    }

    @GetMapping(RuntimeApiRoutes.V1.DATASOURCES_DIAGNOSTICS)
    public RuntimeEnvelope<DatasourceDiagnosticsResponse> datasourceDiagnostics() {
        return responses.ok(new DatasourceDiagnosticsResponse(
                        registryService.isRegistryEnabled(),
                        registryService.resolvedRegistryPath().toString(),
                        registryService.registryFileExists(),
                        registryService.registrySizeBytes(),
                        registryService.registryLastModifiedAt(),
                        registryService.listRecords().size(),
                        registryService.listNamespaceBindings(),
                        registryService.listInfos(),
                        List.of()
                ));
    }

    @PostMapping(RuntimeApiRoutes.V1.DATASOURCES)
    public RuntimeEnvelope<DatasourceMutationResponse> addDatasource(@RequestBody(required = false) DatasourceRequest request) {
        return upsertDatasource(null, request, false);
    }

    @PutMapping(RuntimeApiRoutes.V1.DATASOURCE_BY_NAME)
    public RuntimeEnvelope<DatasourceMutationResponse> updateDatasource(
            @PathVariable String name,
            @RequestBody(required = false) DatasourceRequest request
    ) {
        return upsertDatasource(name, request, true);
    }

    @DeleteMapping(RuntimeApiRoutes.V1.DATASOURCE_BY_NAME)
    public RuntimeEnvelope<DatasourceMutationResponse> removeDatasource(
            @PathVariable String name,
            @RequestParam(required = false) String revokeMode
    ) {
        String normalizedName = canonicalIdentifier(name);
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
        RevokeMode effectiveRevokeMode;
        try {
            effectiveRevokeMode = parseRevokeMode(revokeMode);
        } catch (IllegalArgumentException e) {
            return fail("INVALID_REQUEST", "datasources.remove", e.getMessage(),
                    "Use revokeMode DRAIN or HARD.", false);
        }
        registryService.remove(normalizedName, effectiveRevokeMode);
        DatasourceInfo info = registryService.infoFromRecord(record, "removed", null);
        return responses.ok(new DatasourceMutationResponse(info, List.of()));
    }

    /** Compatibility entry point for callers compiled against the original controller API. */
    public RuntimeEnvelope<DatasourceMutationResponse> removeDatasource(String name) {
        return removeDatasource(name, null);
    }

    @PostMapping(RuntimeApiRoutes.V1.DATASOURCE_TEST)
    public RuntimeEnvelope<DatasourceTestResponse> testDatasource(@PathVariable String name) {
        String normalizedName = canonicalIdentifier(name);
        if (normalizedName == null) {
            return fail("INVALID_REQUEST", "datasources.test", "Missing required path variable: name",
                    "Provide a dataSource name.", false);
        }
        ResolvedDatasource resolved;
        try {
            resolved = registryService.resolve(normalizedName).orElse(null);
        } catch (IllegalArgumentException e) {
            return fail(datasourceResolveFailureCode(e), "datasources.test", e.getMessage(),
                    datasourceResolveFailureSuggestion(e), false);
        }
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
            return responses.ok(response);
        } catch (Exception e) {
            return fail("DATASOURCE_TEST_FAILED", "datasources.test", e.getMessage(),
                    "Check the dataSource jdbcUrl and driver, then retry.", false);
        }
    }

    @GetMapping(RuntimeApiRoutes.V1.NAMESPACE_DATASOURCE)
    public RuntimeEnvelope<NamespaceDatasourceResponse> getNamespaceDatasource(@PathVariable String namespace) {
        String normalizedNamespace = canonicalIdentifier(namespace);
        if (normalizedNamespace == null) {
            return fail("INVALID_REQUEST", "datasources.bind", "Missing required path variable: namespace",
                    "Provide a namespace.", false);
        }
        String dataSource = registryService.getNamespaceDatasource(normalizedNamespace).orElse(null);
        return responses.ok(new NamespaceDatasourceResponse(normalizedNamespace, dataSource, List.of()));
    }

    @PutMapping(RuntimeApiRoutes.V1.NAMESPACE_DATASOURCE)
    public RuntimeEnvelope<NamespaceDatasourceResponse> bindNamespaceDatasource(
            @PathVariable String namespace,
            @RequestBody(required = false) NamespaceDatasourceRequest request
    ) {
        String normalizedNamespace = canonicalIdentifier(request != null && StringUtils.hasText(request.namespace())
                ? request.namespace()
                : namespace);
        if (normalizedNamespace == null) {
            return fail("INVALID_REQUEST", "datasources.bind", "Missing required field: namespace",
                    "Provide a namespace.", false);
        }
        String dataSource = canonicalIdentifier(request != null ? request.dataSource() : null);
        if (dataSource == null) {
            return fail("INVALID_REQUEST", "datasources.bind", "Missing required field: dataSource",
                    "Provide a dataSource name.", false);
        }
        if (!registryService.isConfigured(dataSource)) {
            return fail("DATASOURCE_NOT_FOUND", "datasources.bind", "DataSource not found or disabled: " + dataSource,
                    "Add or enable the dataSource before binding it to a namespace.", false);
        }
        RevokeMode effectiveRevokeMode;
        try {
            effectiveRevokeMode = parseRevokeMode(request != null ? request.revokeMode() : null);
        } catch (IllegalArgumentException e) {
            return fail("INVALID_REQUEST", "datasources.bind", e.getMessage(),
                    "Use revokeMode DRAIN or HARD.", false);
        }
        registryService.bindNamespace(normalizedNamespace, dataSource, effectiveRevokeMode);
        return responses.ok(new NamespaceDatasourceResponse(normalizedNamespace, dataSource, List.of()));
    }

    private RuntimeEnvelope<DatasourceMutationResponse> upsertDatasource(
            String pathName,
            DatasourceRequest request,
            boolean update
    ) {
        String name = canonicalIdentifier(
                pathName != null ? pathName : request != null ? request.name() : null);
        if (name == null) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    "Missing required field: name", "Provide a dataSource name.", false);
        }
        if (RuntimeDatasourceRegistryService.DEFAULT_DATASOURCE_NAME.equals(name)) {
            return fail("DATASOURCE_NAME_CONFLICT", update ? "datasources.update" : "datasources.add",
                    "The default dataSource is configured by Spring and cannot be managed by Runtime API.",
                    "Choose a non-default runtime-managed dataSource name.", false);
        }
        String jdbcUrl = blankToNull(request != null ? request.jdbcUrl() : null);
        if (jdbcUrl == null) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    "Missing required field: jdbcUrl", "Provide a JDBC URL starting with jdbc:.", false);
        }
        if (!jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    "Runtime-managed dataSource requires jdbcUrl starting with jdbc:",
                    "Provide a valid JDBC URL.", false);
        }
        String type = normalizeType(request != null ? request.type() : null, jdbcUrl);
        String password = blankToNull(request != null ? request.password() : null);
        String passwordRef = blankToNull(request != null ? request.passwordRef() : null);
        if (password != null && passwordRef != null) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    "Provide either password or passwordRef, not both.",
                    "Use passwordRef for environment-backed secrets or password for local development only.", false);
        }
        String passwordRefValidationError = ManagedDataSourcePoolManager.validatePasswordRef(passwordRef);
        if (passwordRefValidationError != null) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    passwordRefValidationError,
                    "Use passwordRef with env:, system:, sys:, or a bare environment/system property name.", false);
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
                password,
                passwordRef,
                booleanOr(request != null ? request.enabled() : null, true)
        );
        RevokeMode effectiveRevokeMode;
        try {
            effectiveRevokeMode = parseRevokeMode(request != null ? request.revokeMode() : null);
        } catch (IllegalArgumentException e) {
            return fail("INVALID_REQUEST", update ? "datasources.update" : "datasources.add",
                    e.getMessage(), "Use revokeMode DRAIN or HARD.", false);
        }
        record = registryService.save(record, effectiveRevokeMode);
        DatasourceInfo info = registryService.infoFromRecord(record, record.enabled() ? "active" : "disabled", null);
        return responses.ok(new DatasourceMutationResponse(info, List.of()));
    }

    private <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return responses.fail(
                code,
                phase,
                message,
                null,
                null,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static String canonicalIdentifier(String value) {
        String present = blankToNull(value);
        return present == null ? null : present.trim();
    }

    private static String normalizeType(String value, String jdbcUrl) {
        String requested = canonicalIdentifier(value);
        if (requested != null) {
            return requested.toLowerCase(Locale.ROOT);
        }
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        int start = "jdbc:".length();
        int end = lower.indexOf(':', start);
        if (end > start) {
            return lower.substring(start, end);
        }
        return "jdbc";
    }

    private static boolean booleanOr(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private static RevokeMode parseRevokeMode(String value) {
        if (!StringUtils.hasText(value)) {
            return RevokeMode.DRAIN;
        }
        try {
            return RevokeMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported revokeMode; expected DRAIN or HARD", e);
        }
    }

    private static String datasourceResolveFailureCode(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
        if (message.contains("passwordref")) {
            return "DATASOURCE_CREDENTIAL_UNRESOLVED";
        }
        return "DATASOURCE_RESOLVE_FAILED";
    }

    private static String datasourceResolveFailureSuggestion(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
        if (message.contains("passwordref")) {
            return "Update the dataSource passwordRef to env:, system:, sys:, or a resolvable bare key, then retry.";
        }
        return "Check the runtime-managed dataSource configuration, then retry.";
    }
}

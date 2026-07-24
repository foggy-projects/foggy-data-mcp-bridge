package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.db.model.semantic.port.ComposeCaller;
import com.foggyframework.dataset.db.model.semantic.port.ComposeExecutionRequest;
import com.foggyframework.dataset.db.model.semantic.port.ComposeOperation;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeComposeContextFactory {

    private final DatasetProperties datasetProperties;
    private final RuntimeComposeDialectResolver dialectResolver;
    private final String defaultDialect;

    public RuntimeComposeContextFactory(
            ObjectProvider<DatasetProperties> datasetPropertiesProvider,
            RuntimeComposeDialectResolver dialectResolver,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect
    ) {
        this.datasetProperties = datasetPropertiesProvider.getIfAvailable();
        this.dialectResolver = dialectResolver;
        this.defaultDialect = defaultDialect != null ? defaultDialect : "mysql";
    }

    public RuntimeComposeContext create(
            String requestNamespace,
            String requestTraceId,
            Map<String, Object> params,
            Map<String, Object> options,
            String headerNamespace,
            String authorization,
            Map<String, String> headers
    ) {
        Map<String, String> safeHeaders = headers != null ? headers : Map.of();
        String namespace = DatasetRequestNamespaceResolver.resolve(
                datasetProperties, headerNamespace, requestNamespace);
        String traceId = firstNonBlank(header(safeHeaders, "X-Trace-Id"), requestTraceId);
        RuntimeComposeDialectResolver.ResolvedDialect dialect =
                dialectResolver.resolveDetails(defaultDialect, namespace, options);
        return new RuntimeComposeContext(
                namespace,
                traceId,
                params,
                caller(safeHeaders, authorization),
                dialect);
    }

    private static ComposeCaller caller(Map<String, String> headers, String authorization) {
        return new ComposeCaller(
                firstNonBlank(header(headers, "X-User-Id"), "runtime-api"),
                header(headers, "X-Tenant-Id"),
                parseRoles(header(headers, "X-Roles")),
                header(headers, "X-Dept-Id"),
                authorization,
                header(headers, "X-Policy-Snapshot-Id"));
    }

    private static List<String> parseRoles(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers.containsKey(name)) {
            return headers.get(name);
        }
        return headers.get(name.toLowerCase());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record RuntimeComposeContext(
            String namespace,
            String traceId,
            Map<String, Object> params,
            ComposeCaller caller,
            RuntimeComposeDialectResolver.ResolvedDialect dialect
    ) {
        public ComposeExecutionRequest toExecutionRequest(
                ComposeOperation operation,
                String script
        ) {
            return new ComposeExecutionRequest(
                    operation,
                    script,
                    namespace,
                    traceId,
                    params,
                    caller,
                    dialect.resolvedDialect());
        }

        public RuntimeDiagnostics diagnostics() {
            return new RuntimeDiagnostics(null, null, List.of(), diagnosticsAttributes());
        }

        public Map<String, Object> diagnosticsAttributes() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            putIfNotNull(attributes, "namespace", namespace);
            putIfNotNull(attributes, "resolvedDialect", dialect.resolvedDialect());
            putIfNotNull(attributes, "dialectSource", dialect.source());
            putIfNotNull(attributes, "namespaceDatasourceId", dialect.namespaceDatasourceId());
            putIfNotNull(attributes, "namespaceDatasourceStatus", dialect.namespaceDatasourceStatus());
            putIfNotNull(attributes, "datasourceType", dialect.datasourceType());
            putIfNotNull(attributes, "defaultDialect", dialect.defaultDialect());
            return Map.copyOf(attributes);
        }

        private static void putIfNotNull(Map<String, Object> attributes, String key, Object value) {
            if (value != null) {
                attributes.put(key, value);
            }
        }
    }
}

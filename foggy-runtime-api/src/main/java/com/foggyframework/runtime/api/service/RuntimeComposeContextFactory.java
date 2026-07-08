package com.foggyframework.runtime.api.service;

import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.dataset.db.model.engine.compose.context.ComposeQueryContext;
import com.foggyframework.dataset.db.model.engine.compose.context.Principal;
import com.foggyframework.dataset.db.model.engine.compose.runtime.ComposeScriptService;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolution;
import com.foggyframework.dataset.db.model.engine.compose.security.AuthorityResolver;
import com.foggyframework.dataset.db.model.engine.compose.security.ModelBinding;
import com.foggyframework.dataset.db.model.semantic.service.SemanticQueryServiceV3;
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

    private final AuthorityResolver authorityResolver;
    private final DatasetProperties datasetProperties;
    private final RuntimeComposeDialectResolver dialectResolver;
    private final String defaultDialect;

    public RuntimeComposeContextFactory(
            ObjectProvider<AuthorityResolver> authorityResolvers,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider,
            RuntimeComposeDialectResolver dialectResolver,
            @Value("${foggy.compose.dialect:mysql}") String defaultDialect
    ) {
        this.authorityResolver = authorityResolvers.orderedStream()
                .findFirst()
                .orElse(RuntimeComposeContextFactory::allowAll);
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
        ComposeQueryContext queryContext = ComposeQueryContext.builder()
                .principal(principal(safeHeaders, authorization))
                .namespace(namespace)
                .traceId(firstNonBlank(header(safeHeaders, "X-Trace-Id"), requestTraceId))
                .params(params)
                .authorityResolver(authorityResolver)
                .build();
        RuntimeComposeDialectResolver.ResolvedDialect dialect =
                dialectResolver.resolveDetails(defaultDialect, namespace, options);
        return new RuntimeComposeContext(queryContext, dialect);
    }

    private static Principal principal(Map<String, String> headers, String authorization) {
        return Principal.builder()
                .userId(firstNonBlank(header(headers, "X-User-Id"), "runtime-api"))
                .tenantId(header(headers, "X-Tenant-Id"))
                .roles(parseRoles(header(headers, "X-Roles")))
                .deptId(header(headers, "X-Dept-Id"))
                .authorizationHint(authorization)
                .policySnapshotId(header(headers, "X-Policy-Snapshot-Id"))
                .build();
    }

    private static AuthorityResolution allowAll(
            com.foggyframework.dataset.db.model.engine.compose.security.AuthorityRequest request) {
        Map<String, ModelBinding> bindings = new LinkedHashMap<>();
        for (String model : request.modelNames()) {
            bindings.put(model, ModelBinding.builder()
                    .deniedColumns(List.of())
                    .systemSlice(List.of())
                    .build());
        }
        return AuthorityResolution.builder().bindings(bindings).build();
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
            ComposeQueryContext queryContext,
            RuntimeComposeDialectResolver.ResolvedDialect dialect
    ) {
        public ComposeScriptService.ComposeScriptRequest toScriptRequest(
                ComposeScriptService.Mode mode,
                String script,
                SemanticQueryServiceV3 semanticService
        ) {
            return ComposeScriptService.ComposeScriptRequest.builder()
                    .mode(mode)
                    .script(script)
                    .ctx(queryContext)
                    .semanticService(semanticService)
                    .dialect(dialect.resolvedDialect())
                    .build();
        }

        public RuntimeDiagnostics diagnostics() {
            return new RuntimeDiagnostics(null, null, List.of(), diagnosticsAttributes());
        }

        public Map<String, Object> diagnosticsAttributes() {
            Map<String, Object> attributes = new LinkedHashMap<>();
            putIfNotNull(attributes, "namespace", queryContext.namespace());
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

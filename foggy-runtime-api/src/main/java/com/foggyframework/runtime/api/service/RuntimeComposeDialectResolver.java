package com.foggyframework.runtime.api.service;

import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.RuntimeDatasourceRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeComposeDialectResolver {

    private final RuntimeDatasourceRegistryService registryService;

    public RuntimeComposeDialectResolver(RuntimeDatasourceRegistryService registryService) {
        this.registryService = registryService;
    }

    public String resolve(String defaultDialect, String namespace, Map<String, Object> options) {
        return resolveDetails(defaultDialect, namespace, options).resolvedDialect();
    }

    public ResolvedDialect resolveDetails(String defaultDialect, String namespace, Map<String, Object> options) {
        String normalizedDefault = normalizeDialect(defaultDialect, "mysql");
        BoundDatasource bound = resolveBoundDatasource(namespace).orElse(BoundDatasource.unbound());
        String requestedDialect = stringOption(options, "dialect");
        if (StringUtils.hasText(requestedDialect)) {
            return new ResolvedDialect(
                    normalizeDialect(requestedDialect, normalizedDefault),
                    "request-options",
                    bound.name(),
                    bound.status(),
                    bound.type(),
                    normalizedDefault
            );
        }
        if (bound.record() != null && bound.record().enabled()) {
            String dialect = normalizeDatasourceType(bound.record().type());
            if (dialect != null) {
                return new ResolvedDialect(
                        dialect,
                        "namespace-datasource-type",
                        bound.name(),
                        bound.status(),
                        bound.type(),
                        normalizedDefault
                );
            }
            dialect = normalizeJdbcUrl(bound.record().jdbcUrl());
            if (dialect != null) {
                return new ResolvedDialect(
                        dialect,
                        "namespace-datasource-jdbc-url",
                        bound.name(),
                        bound.status(),
                        bound.type(),
                        normalizedDefault
                );
            }
        }
        return new ResolvedDialect(
                normalizedDefault,
                "default",
                bound.name(),
                bound.status(),
                bound.type(),
                normalizedDefault
        );
    }

    private Optional<BoundDatasource> resolveBoundDatasource(String namespace) {
        if (!StringUtils.hasText(namespace)) {
            return Optional.empty();
        }
        return registryService.getNamespaceDatasource(namespace)
                .map(name -> {
                    RuntimeDatasourceRecord record = registryService.find(name).orElse(null);
                    if (record == null) {
                        return new BoundDatasource(name, "missing", null);
                    }
                    return new BoundDatasource(name, record.enabled() ? "active" : "disabled", record);
                });
    }

    private static String normalizeDatasourceType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "mssql", "sqlserver", "sql_server", "microsoft-sql-server" -> "sqlserver";
            case "postgres", "postgresql" -> "postgresql";
            case "sqlite" -> "sqlite";
            case "mysql", "mysql57", "mysql8" -> value.trim().toLowerCase(Locale.ROOT);
            case "mariadb" -> "mysql";
            default -> null;
        };
    }

    private static String normalizeJdbcUrl(String jdbcUrl) {
        if (!StringUtils.hasText(jdbcUrl)) {
            return null;
        }
        String lower = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:sqlserver:")) {
            return "sqlserver";
        }
        if (lower.startsWith("jdbc:postgresql:")) {
            return "postgresql";
        }
        if (lower.startsWith("jdbc:sqlite:")) {
            return "sqlite";
        }
        if (lower.startsWith("jdbc:mysql:")) {
            return "mysql";
        }
        if (lower.startsWith("jdbc:mariadb:")) {
            return "mysql";
        }
        return null;
    }

    private static String normalizeDialect(String value, String fallback) {
        String normalized = normalizeDatasourceType(value);
        if (normalized != null) {
            return normalized;
        }
        if (StringUtils.hasText(value)) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
        return StringUtils.hasText(fallback) ? fallback.trim().toLowerCase(Locale.ROOT) : "mysql";
    }

    private static String stringOption(Map<String, Object> options, String key) {
        if (options == null || !options.containsKey(key)) {
            return null;
        }
        Object value = options.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    public record ResolvedDialect(
            String resolvedDialect,
            String source,
            String namespaceDatasourceId,
            String namespaceDatasourceStatus,
            String datasourceType,
            String defaultDialect
    ) {
    }

    private record BoundDatasource(String name, String status, RuntimeDatasourceRecord record) {
        private static BoundDatasource unbound() {
            return new BoundDatasource(null, "unbound", null);
        }

        private String type() {
            return record != null ? record.type() : null;
        }
    }
}

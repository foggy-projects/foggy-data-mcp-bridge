package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.dataset.db.model.config.DatasetProperties;
import com.foggyframework.dataset.db.model.config.DatasetRequestNamespaceResolver;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.SqlColumnInfo;
import com.foggyframework.runtime.api.dto.SqlQueryRequest;
import com.foggyframework.runtime.api.dto.SqlQueryResponse;
import com.foggyframework.runtime.api.dto.TableColumnInfo;
import com.foggyframework.runtime.api.dto.TableForeignKeyInfo;
import com.foggyframework.runtime.api.dto.TableInfo;
import com.foggyframework.runtime.api.dto.TableIndexInfo;
import com.foggyframework.runtime.api.dto.TableInspectRequest;
import com.foggyframework.runtime.api.dto.TableInspectResponse;
import com.foggyframework.runtime.api.dto.TableListRequest;
import com.foggyframework.runtime.api.dto.TableListResponse;
import com.foggyframework.runtime.api.dto.TablePrimaryKeyInfo;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService;
import com.foggyframework.runtime.api.service.RuntimeDatasourceRegistryService.ResolvedDatasource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeTablesController {

    private static final String ENGINE = "java";
    private static final int DEFAULT_SQL_MAX_ROWS = 100;
    private static final int HARD_SQL_MAX_ROWS = 500;
    private static final int DEFAULT_SQL_TIMEOUT_SECONDS = 10;
    private static final int HARD_SQL_TIMEOUT_SECONDS = 30;
    private static final Set<String> FORBIDDEN_SQL_KEYWORDS = Set.of(
            "alter",
            "attach",
            "call",
            "create",
            "delete",
            "detach",
            "drop",
            "execute",
            "grant",
            "insert",
            "merge",
            "pragma",
            "replace",
            "revoke",
            "truncate",
            "update",
            "vacuum"
    );

    private final FoggyRuntimeApiProperties runtimeApiProperties;
    private final RuntimeDatasourceRegistryService datasourceRegistryService;
    private final DatasetProperties datasetProperties;

    public RuntimeTablesController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            RuntimeDatasourceRegistryService datasourceRegistryService,
            ObjectProvider<DatasetProperties> datasetPropertiesProvider
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.datasourceRegistryService = datasourceRegistryService;
        this.datasetProperties = datasetPropertiesProvider.getIfAvailable();
    }

    @PostMapping("/tables/list")
    public RuntimeEnvelope<TableListResponse> listTables(
            @RequestBody(required = false) TableListRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        String requestedDataSource = resolveRequestedDataSource(
                request != null ? request.dataSource() : null,
                resolveNamespace(namespace, request != null ? request.namespace() : null)
        );
        ResolvedDatasource resolved;
        try {
            resolved = datasourceRegistryService.resolve(requestedDataSource).orElse(null);
        } catch (IllegalArgumentException e) {
            return fail(datasourceResolveFailureCode(e), "tables.list", e.getMessage(),
                    datasourceResolveFailureSuggestion(e), false);
        }
        if (resolved == null) {
            return fail("DATASOURCE_NOT_FOUND", "tables.list", "DataSource not found or disabled: " + requestedDataSource,
                    "Start the engine with a configured default DataSource or add a runtime-managed dataSource.", false);
        }

        String schema = blankToNull(request != null ? request.schema() : null);
        String pattern = stringOr(request != null ? request.pattern() : null, "%");
        boolean includeViews = booleanOr(request != null ? request.includeViews() : null, true);
        String[] tableTypes = includeViews ? new String[]{"TABLE", "VIEW"} : new String[]{"TABLE"};

        try (Connection connection = resolved.dataSource().getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String catalog = connection.getCatalog();
            List<TableInfo> tables = new ArrayList<>();
            try (ResultSet resultSet = meta.getTables(catalog, schema, pattern, tableTypes)) {
                while (resultSet.next()) {
                    tables.add(new TableInfo(
                            safeGetString(resultSet, "TABLE_SCHEM"),
                            safeGetString(resultSet, "TABLE_NAME"),
                            safeGetString(resultSet, "TABLE_TYPE"),
                            safeGetString(resultSet, "REMARKS")
                    ));
                }
            }
            return RuntimeEnvelope.ok(ENGINE, runtimeApiProperties.getRuntimeApiVersion(),
                    new TableListResponse(resolved.name(), schema, List.copyOf(tables), List.of()));
        } catch (Exception e) {
            return fail("TABLE_LIST_FAILED", "tables.list", e.getMessage(),
                    "Check the DataSource and schema, then retry.", false);
        }
    }

    @PostMapping("/tables/inspect")
    public RuntimeEnvelope<TableInspectResponse> inspectTable(
            @RequestBody(required = false) TableInspectRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        String table = blankToNull(request != null ? request.table() : null);
        if (table == null) {
            return fail("INVALID_REQUEST", "tables.inspect", "Missing required body field: table",
                    null, "Provide a database table name.", false);
        }

        String requestedDataSource = resolveRequestedDataSource(
                request != null ? request.dataSource() : null,
                resolveNamespace(namespace, request != null ? request.namespace() : null)
        );
        ResolvedDatasource resolved;
        try {
            resolved = datasourceRegistryService.resolve(requestedDataSource).orElse(null);
        } catch (IllegalArgumentException e) {
            return fail(datasourceResolveFailureCode(e), "tables.inspect", e.getMessage(),
                    datasourceResolveFailureSuggestion(e), false);
        }
        if (resolved == null) {
            return fail("DATASOURCE_NOT_FOUND", "tables.inspect", "DataSource not found or disabled: " + requestedDataSource,
                    null, "Start the engine with a configured default DataSource or add a runtime-managed dataSource.", false);
        }

        String schema = blankToNull(request != null ? request.schema() : null);
        try (Connection connection = resolved.dataSource().getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String catalog = connection.getCatalog();
            String tableType = findTableType(meta, catalog, schema, table);
            List<TableColumnInfo> columns = inspectColumns(meta, catalog, schema, table);
            TablePrimaryKeyInfo primaryKey = inspectPrimaryKey(meta, catalog, schema, table);
            List<TableForeignKeyInfo> foreignKeys = booleanOr(request != null ? request.includeForeignKeys() : null, true)
                    ? inspectForeignKeys(meta, catalog, schema, table)
                    : List.of();
            List<TableIndexInfo> indexes = booleanOr(request != null ? request.includeIndexes() : null, false)
                    ? inspectIndexes(meta, catalog, schema, table)
                    : List.of();

            TableInspectResponse response = new TableInspectResponse(
                    resolved.name(),
                    schema,
                    table,
                    tableType,
                    columns,
                    primaryKey,
                    foreignKeys,
                    indexes
            );
            return RuntimeEnvelope.ok(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), response);
        } catch (Exception e) {
            return fail("TABLE_INSPECT_FAILED", "tables.inspect", e.getMessage(),
                    null, "Check the DataSource and table name, then retry.", false);
        }
    }

    @PostMapping("/sql/query")
    public RuntimeEnvelope<SqlQueryResponse> querySql(
            @RequestBody(required = false) SqlQueryRequest request,
            @RequestHeader(value = "X-NS", required = false) String namespace
    ) {
        String requestedDataSource = resolveRequestedDataSource(
                request != null ? request.dataSource() : null,
                resolveNamespace(namespace, request != null ? request.namespace() : null)
        );
        ResolvedDatasource resolved;
        try {
            resolved = datasourceRegistryService.resolve(requestedDataSource).orElse(null);
        } catch (IllegalArgumentException e) {
            return fail(datasourceResolveFailureCode(e), "sql.query", e.getMessage(),
                    datasourceResolveFailureSuggestion(e), false);
        }
        if (resolved == null) {
            return fail("DATASOURCE_NOT_FOUND", "sql.query", "DataSource not found or disabled: " + requestedDataSource,
                    "Start the engine with a configured default DataSource or add a runtime-managed dataSource.", false);
        }

        String sql = normalizeSql(request != null ? request.sql() : null);
        String rejection = rejectReason(sql);
        if (rejection != null) {
            return fail("SQL_QUERY_REJECTED", "sql.query", rejection,
                    "Provide one read-only SELECT or WITH ... SELECT statement.", false);
        }

        int maxRows = boundedInt(request != null ? request.maxRows() : null, DEFAULT_SQL_MAX_ROWS, 1, HARD_SQL_MAX_ROWS);
        int timeoutSeconds = boundedInt(request != null ? request.timeoutSeconds() : null,
                DEFAULT_SQL_TIMEOUT_SECONDS, 1, HARD_SQL_TIMEOUT_SECONDS);

        try (Connection connection = resolved.dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.setMaxRows(maxRows + 1);
            statement.setQueryTimeout(timeoutSeconds);
            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) {
                return fail("SQL_QUERY_REJECTED", "sql.query", "SQL did not return a result set.",
                        "Provide one read-only SELECT or WITH ... SELECT statement.", false);
            }

            try (ResultSet resultSet = statement.getResultSet()) {
                ResultSetMetaData meta = resultSet.getMetaData();
                List<SqlColumnInfo> columns = sqlColumns(meta);
                List<Map<String, Object>> rows = new ArrayList<>();
                boolean truncated = false;
                while (resultSet.next()) {
                    if (rows.size() >= maxRows) {
                        truncated = true;
                        break;
                    }
                    rows.add(sqlRow(resultSet, meta));
                }
                List<String> warnings = truncated
                        ? List.of("Result was truncated to maxRows=" + maxRows + ".")
                        : List.of();
                SqlQueryResponse response = new SqlQueryResponse(
                        resolved.name(),
                        sql,
                        maxRows,
                        truncated,
                        columns,
                        rows,
                        rows.size(),
                        warnings
                );
                return RuntimeEnvelope.ok(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), response);
            }
        } catch (Exception e) {
            return fail("SQL_QUERY_FAILED", "sql.query", e.getMessage(),
                    "Check the SQL, table names, and selected DataSource, then retry.", false);
        }
    }

    private String findTableType(DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
        try (ResultSet tables = meta.getTables(catalog, schema, table, new String[]{"TABLE", "VIEW"})) {
            if (tables.next()) {
                return stringOr(safeGetString(tables, "TABLE_TYPE"), "TABLE");
            }
        }
        throw new IllegalArgumentException("Table not found: " + table);
    }

    private List<TableColumnInfo> inspectColumns(DatabaseMetaData meta, String catalog, String schema, String table)
            throws SQLException {
        List<TableColumnInfo> columns = new ArrayList<>();
        try (ResultSet cols = meta.getColumns(catalog, schema, table, null)) {
            while (cols.next()) {
                columns.add(new TableColumnInfo(
                        safeGetString(cols, "COLUMN_NAME"),
                        safeGetString(cols, "TYPE_NAME"),
                        safeGetInteger(cols, "DATA_TYPE"),
                        safeGetInteger(cols, "COLUMN_SIZE"),
                        safeGetInteger(cols, "DECIMAL_DIGITS"),
                        nullableValue(safeGetString(cols, "IS_NULLABLE")),
                        safeGetString(cols, "COLUMN_DEF"),
                        safeGetInteger(cols, "ORDINAL_POSITION")
                ));
            }
        }
        return List.copyOf(columns);
    }

    private TablePrimaryKeyInfo inspectPrimaryKey(DatabaseMetaData meta, String catalog, String schema, String table)
            throws SQLException {
        List<String> columns = new ArrayList<>();
        String name = null;
        try (ResultSet pk = meta.getPrimaryKeys(catalog, schema, table)) {
            while (pk.next()) {
                if (name == null) {
                    name = safeGetString(pk, "PK_NAME");
                }
                String column = safeGetString(pk, "COLUMN_NAME");
                if (column != null) {
                    columns.add(column);
                }
            }
        }
        return new TablePrimaryKeyInfo(name, List.copyOf(columns));
    }

    private List<TableForeignKeyInfo> inspectForeignKeys(DatabaseMetaData meta, String catalog, String schema, String table)
            throws SQLException {
        List<TableForeignKeyInfo> foreignKeys = new ArrayList<>();
        try (ResultSet fk = meta.getImportedKeys(catalog, schema, table)) {
            while (fk.next()) {
                foreignKeys.add(new TableForeignKeyInfo(
                        safeGetString(fk, "FK_NAME"),
                        safeGetString(fk, "FKCOLUMN_NAME"),
                        safeGetString(fk, "PKTABLE_SCHEM"),
                        safeGetString(fk, "PKTABLE_NAME"),
                        safeGetString(fk, "PKCOLUMN_NAME")
                ));
            }
        }
        return List.copyOf(foreignKeys);
    }

    private List<TableIndexInfo> inspectIndexes(DatabaseMetaData meta, String catalog, String schema, String table)
            throws SQLException {
        Map<String, IndexBuilder> indexes = new LinkedHashMap<>();
        try (ResultSet idx = meta.getIndexInfo(catalog, schema, table, false, false)) {
            while (idx.next()) {
                String name = safeGetString(idx, "INDEX_NAME");
                String column = safeGetString(idx, "COLUMN_NAME");
                if (name == null || column == null) {
                    continue;
                }
                IndexBuilder builder = indexes.computeIfAbsent(name, key -> new IndexBuilder(name, !safeGetBoolean(idx, "NON_UNIQUE")));
                builder.columns().add(column);
            }
        }
        List<TableIndexInfo> result = new ArrayList<>();
        for (IndexBuilder builder : indexes.values()) {
            result.add(new TableIndexInfo(builder.name(), builder.unique(), List.copyOf(builder.columns())));
        }
        return List.copyOf(result);
    }

    private List<SqlColumnInfo> sqlColumns(ResultSetMetaData meta) throws SQLException {
        List<SqlColumnInfo> columns = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            columns.add(new SqlColumnInfo(
                    stringOr(meta.getColumnLabel(i), meta.getColumnName(i)),
                    meta.getColumnTypeName(i),
                    meta.getColumnType(i),
                    meta.getColumnDisplaySize(i),
                    meta.getPrecision(i),
                    meta.getScale(i),
                    nullableColumn(meta.isNullable(i))
            ));
        }
        return List.copyOf(columns);
    }

    private Map<String, Object> sqlRow(ResultSet resultSet, ResultSetMetaData meta) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        Set<String> seenNames = new HashSet<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String baseName = stringOr(meta.getColumnLabel(i), meta.getColumnName(i));
            String name = uniqueColumnName(baseName, seenNames);
            row.put(name, jsonFriendlyValue(resultSet.getObject(i)));
        }
        return row;
    }

    private String uniqueColumnName(String baseName, Set<String> seenNames) {
        String name = stringOr(baseName, "column");
        if (seenNames.add(name)) {
            return name;
        }
        int index = 2;
        while (!seenNames.add(name + "_" + index)) {
            index++;
        }
        return name + "_" + index;
    }

    private Object jsonFriendlyValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return String.valueOf(value);
    }

    private String normalizeSql(String sql) {
        String normalized = blankToNull(sql);
        if (normalized == null) {
            return null;
        }
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String rejectReason(String sql) {
        if (sql == null) {
            return "Missing required body field: sql";
        }
        String sanitized = sanitizeSqlForKeywordScan(sql);
        if (sanitized.contains(";")) {
            return "Only one SQL statement is allowed.";
        }
        String lower = sanitized.trim().toLowerCase();
        if (!startsWithKeyword(lower, "select") && !startsWithKeyword(lower, "with")) {
            return "Only read-only SELECT or WITH queries are allowed.";
        }
        for (String keyword : FORBIDDEN_SQL_KEYWORDS) {
            if (containsKeyword(lower, keyword)) {
                return "SQL keyword is not allowed in read-only probe: " + keyword;
            }
        }
        return null;
    }

    private String sanitizeSqlForKeywordScan(String sql) {
        StringBuilder builder = new StringBuilder(sql.length());
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    builder.append(current);
                } else {
                    builder.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    builder.append("  ");
                    i++;
                } else {
                    builder.append(' ');
                }
                continue;
            }
            if (!singleQuote && !doubleQuote && current == '-' && next == '-') {
                lineComment = true;
                builder.append("  ");
                i++;
                continue;
            }
            if (!singleQuote && !doubleQuote && current == '/' && next == '*') {
                blockComment = true;
                builder.append("  ");
                i++;
                continue;
            }
            if (!doubleQuote && current == '\'') {
                singleQuote = !singleQuote;
                builder.append(' ');
                continue;
            }
            if (!singleQuote && current == '"') {
                doubleQuote = !doubleQuote;
                builder.append(' ');
                continue;
            }
            builder.append(singleQuote || doubleQuote ? ' ' : current);
        }
        return builder.toString();
    }

    private boolean startsWithKeyword(String sql, String keyword) {
        if (!sql.startsWith(keyword)) {
            return false;
        }
        return sql.length() == keyword.length() || !isIdentifierChar(sql.charAt(keyword.length()));
    }

    private boolean containsKeyword(String sql, String keyword) {
        int index = sql.indexOf(keyword);
        while (index >= 0) {
            boolean before = index == 0 || !isIdentifierChar(sql.charAt(index - 1));
            int end = index + keyword.length();
            boolean after = end >= sql.length() || !isIdentifierChar(sql.charAt(end));
            if (before && after) {
                return true;
            }
            index = sql.indexOf(keyword, index + 1);
        }
        return false;
    }

    private boolean isIdentifierChar(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private int boundedInt(Integer value, int fallback, int min, int max) {
        int resolved = value != null ? value : fallback;
        return Math.max(min, Math.min(max, resolved));
    }

    private String resolveRequestedDataSource(String dataSource, String namespace) {
        String normalizedDataSource = blankToNull(dataSource);
        if (normalizedDataSource != null) {
            return normalizedDataSource;
        }
        String normalizedNamespace = blankToNull(namespace);
        if (normalizedNamespace != null) {
            return datasourceRegistryService.getNamespaceDatasource(normalizedNamespace)
                    .orElse(RuntimeDatasourceRegistryService.DEFAULT_DATASOURCE_NAME);
        }
        return RuntimeDatasourceRegistryService.DEFAULT_DATASOURCE_NAME;
    }

    private String resolveNamespace(String headerNamespace, String bodyNamespace) {
        return DatasetRequestNamespaceResolver.resolve(datasetProperties, headerNamespace, bodyNamespace);
    }

    private static String datasourceResolveFailureCode(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (message.contains("passwordref")) {
            return "DATASOURCE_CREDENTIAL_UNRESOLVED";
        }
        return "DATASOURCE_RESOLVE_FAILED";
    }

    private static String datasourceResolveFailureSuggestion(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (message.contains("passwordref")) {
            return "Update the dataSource passwordRef to env:, system:, sys:, or a resolvable bare key, then retry.";
        }
        return "Check the runtime-managed dataSource configuration, then retry.";
    }

    private Boolean nullableColumn(int nullable) {
        return switch (nullable) {
            case ResultSetMetaData.columnNullable -> true;
            case ResultSetMetaData.columnNoNulls -> false;
            default -> null;
        };
    }

    private <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return fail(code, phase, message, null, suggestedNextAction, safeToAutoRepair);
    }

    private <T> RuntimeEnvelope<T> fail(
            String code,
            String phase,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return RuntimeEnvelope.fail(
                ENGINE,
                runtimeApiProperties.getRuntimeApiVersion(),
                code,
                phase,
                message,
                model,
                null,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
    }

    private static String safeGetString(ResultSet resultSet, String column) {
        try {
            return blankToNull(resultSet.getString(column));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer safeGetInteger(ResultSet resultSet, String column) {
        try {
            int value = resultSet.getInt(column);
            return resultSet.wasNull() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean safeGetBoolean(ResultSet resultSet, String column) {
        try {
            return resultSet.getBoolean(column);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Boolean nullableValue(String nullable) {
        if (nullable == null) {
            return null;
        }
        return "YES".equalsIgnoreCase(nullable);
    }

    private static String stringOr(String value, String fallback) {
        String normalized = blankToNull(value);
        return normalized != null ? normalized : fallback;
    }

    private static boolean booleanOr(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record IndexBuilder(String name, Boolean unique, List<String> columns) {
        private IndexBuilder(String name, Boolean unique) {
            this(name, unique, new ArrayList<>());
        }
    }
}

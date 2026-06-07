package com.foggyframework.runtime.api.controller;

import com.foggyframework.runtime.api.config.FoggyRuntimeApiProperties;
import com.foggyframework.runtime.api.dto.RuntimeDiagnostics;
import com.foggyframework.runtime.api.dto.RuntimeEnvelope;
import com.foggyframework.runtime.api.dto.RuntimeError;
import com.foggyframework.runtime.api.dto.TableColumnInfo;
import com.foggyframework.runtime.api.dto.TableForeignKeyInfo;
import com.foggyframework.runtime.api.dto.TableIndexInfo;
import com.foggyframework.runtime.api.dto.TableInspectRequest;
import com.foggyframework.runtime.api.dto.TableInspectResponse;
import com.foggyframework.runtime.api.dto.TablePrimaryKeyInfo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "foggy.runtime-api", name = "enabled", havingValue = "true")
public class RuntimeTablesController {

    private static final String ENGINE = "java";

    private final FoggyRuntimeApiProperties runtimeApiProperties;
    private final ObjectProvider<DataSource> dataSourceProvider;

    public RuntimeTablesController(
            FoggyRuntimeApiProperties runtimeApiProperties,
            ObjectProvider<DataSource> dataSourceProvider
    ) {
        this.runtimeApiProperties = runtimeApiProperties;
        this.dataSourceProvider = dataSourceProvider;
    }

    @PostMapping("/tables/inspect")
    public RuntimeEnvelope<TableInspectResponse> inspectTable(@RequestBody(required = false) TableInspectRequest request) {
        String table = blankToNull(request != null ? request.table() : null);
        if (table == null) {
            return fail("INVALID_REQUEST", "tables.inspect", "Missing required body field: table",
                    null, "Provide a database table name.", false);
        }

        String requestedDataSource = blankToNull(request != null ? request.dataSource() : null);
        if (requestedDataSource != null && !"default".equals(requestedDataSource)) {
            return fail("INVALID_REQUEST", "tables.inspect",
                    "Named dataSource is not supported by foggy-runtime-api yet: " + requestedDataSource,
                    null, "Use the default Spring DataSource or add a runtime dataSource selector.", false);
        }

        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return fail("TABLE_INSPECT_FAILED", "tables.inspect", "No Spring DataSource bean is available.",
                    null, "Start the engine with a configured DataSource.", false);
        }

        String schema = blankToNull(request != null ? request.schema() : null);
        try (Connection connection = dataSource.getConnection()) {
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
                    requestedDataSource != null ? requestedDataSource : "default",
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

    private RuntimeEnvelope<TableInspectResponse> fail(
            String code,
            String phase,
            String message,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        return fail(code, phase, message, null, suggestedNextAction, safeToAutoRepair);
    }

    private RuntimeEnvelope<TableInspectResponse> fail(
            String code,
            String phase,
            String message,
            String model,
            String suggestedNextAction,
            boolean safeToAutoRepair
    ) {
        RuntimeError error = new RuntimeError(
                code,
                phase,
                message,
                model,
                null,
                null,
                suggestedNextAction,
                safeToAutoRepair
        );
        return RuntimeEnvelope.fail(ENGINE, runtimeApiProperties.getRuntimeApiVersion(), error, RuntimeDiagnostics.empty());
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

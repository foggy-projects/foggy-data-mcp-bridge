package com.foggyframework.dataset.model.memorygrid.duckdb;

import com.foggyframework.core.ex.RX;
import com.foggyframework.dataset.model.semantic.domain.SemanticRequestContext;
import com.foggyframework.dataset.model.semantic.memorygrid.GridSqlContractValidator;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridDialectDescriptor;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridEngine;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridExecutionResult;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridInputBinding;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridRequest;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridResultResolver;
import com.foggyframework.dataset.model.semantic.memorygrid.MemoryGridValidation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Optional DuckDB-backed Grid SQL engine over governed bounded result handles.
 */
public final class DuckDbMemoryGridEngine implements MemoryGridEngine {

    public static final String ENGINE_ID = "memory-grid-duckdb";
    public static final String DIALECT_ID = "duckdb-grid-sql-v1";
    public static final String RESULT_HANDLE_NOT_FOUND = "MEMORY_GRID_RESULT_HANDLE_NOT_FOUND";
    public static final String RESULT_SCHEMA_MISMATCH = "MEMORY_GRID_RESULT_SCHEMA_MISMATCH";
    public static final String RESULT_GOVERNANCE_MISMATCH = "MEMORY_GRID_RESULT_GOVERNANCE_MISMATCH";
    public static final String SQL_EXECUTION_FAILED = "MEMORY_GRID_DUCKDB_SQL_EXECUTION_FAILED";
    public static final String PLAN_NOT_SUPPORTED = "MEMORY_GRID_DUCKDB_PLAN_NOT_SUPPORTED";
    public static final String OUTPUT_LIMIT_INVALID = "MEMORY_GRID_OUTPUT_LIMIT_INVALID";

    private static final int DEFAULT_OUTPUT_LIMIT = 500;
    private static final int DEFAULT_MAX_OUTPUT_LIMIT = 1000;
    private static final MemoryGridDialectDescriptor DIALECT = new MemoryGridDialectDescriptor(
            ENGINE_ID,
            DIALECT_ID,
            false,
            true,
            List.of("bounded result_handle aliases", "projection/filter/order/limit",
                    "inner joins", "numeric derived expressions", "CTE over bound aliases"),
            List.of("memory_grid_plan", "physical tables", "DML/DDL", "external files/functions",
                    "unbounded rows"));

    private final MemoryGridResultResolver resultResolver;
    private final int defaultOutputLimit;
    private final int maxOutputLimit;

    public DuckDbMemoryGridEngine(MemoryGridResultResolver resultResolver) {
        this(resultResolver, DEFAULT_OUTPUT_LIMIT, DEFAULT_MAX_OUTPUT_LIMIT);
    }

    public DuckDbMemoryGridEngine(MemoryGridResultResolver resultResolver,
                                  int defaultOutputLimit,
                                  int maxOutputLimit) {
        this.resultResolver = resultResolver;
        this.defaultOutputLimit = defaultOutputLimit;
        this.maxOutputLimit = maxOutputLimit;
    }

    @Override
    public MemoryGridDialectDescriptor dialect() {
        return DIALECT;
    }

    @Override
    public MemoryGridValidation validate(MemoryGridRequest request, SemanticRequestContext context) {
        Map<String, Object> evidence = validationEvidence(request);
        evidence.put("grid_sql_engine_status", "DUCKDB_READY");
        return new MemoryGridValidation(evidence);
    }

    @Override
    public MemoryGridExecutionResult execute(MemoryGridRequest request, SemanticRequestContext context) {
        Map<String, Object> validation = validationEvidence(request);
        Map<String, MemoryGridResultResolver.ResolvedResult> inputs = resolveInputs(request, context);
        int outputLimit = outputLimit(request);

        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            harden(connection);
            for (Map.Entry<String, MemoryGridResultResolver.ResolvedResult> input : inputs.entrySet()) {
                registerTable(connection, input.getKey(), input.getValue());
            }

            QueryRows queryRows = query(connection, request.gridSql(), outputLimit);
            Map<String, Object> summary = executionSummary(inputs, queryRows, outputLimit);
            validation.put("memory_grid_execution_summary", summary);
            return new MemoryGridExecutionResult(queryRows.rows(), validation, summary);
        } catch (SQLException ex) {
            throw RX.throwB(SQL_EXECUTION_FAILED + ": DuckDB Grid SQL execution failed.", ex);
        }
    }

    private Map<String, Object> validationEvidence(MemoryGridRequest request) {
        if (request == null) {
            throw RX.throwB(GridSqlContractValidator.SQL_NOT_DECLARED + ": grid_sql request is required.");
        }
        if (request.plan() != null && !request.plan().isEmpty()) {
            throw RX.throwB(PLAN_NOT_SUPPORTED + ": DuckDB engine only supports grid_sql requests.");
        }
        if (resultResolver == null) {
            throw RX.throwB(RESULT_HANDLE_NOT_FOUND + ": no MemoryGridResultResolver is configured.");
        }
        Map<String, Object> evidence = new LinkedHashMap<>(GridSqlContractValidator.validate(request));
        evidence.put("memory_grid_engine", dialect().engineId());
        evidence.put("memory_grid_dialect", dialect().dialectId());
        evidence.put("memory_grid_plan_supported", dialect().planSupported());
        evidence.put("memory_grid_grid_sql_supported", dialect().gridSqlSupported());
        appendOutputLimitEvidence(evidence, request);
        return evidence;
    }

    private Map<String, MemoryGridResultResolver.ResolvedResult> resolveInputs(MemoryGridRequest request,
                                                                               SemanticRequestContext context) {
        Map<String, MemoryGridResultResolver.ResolvedResult> inputs = new LinkedHashMap<>();
        for (MemoryGridInputBinding binding : request.bindings()) {
            MemoryGridResultResolver.ResolvedResult result = resultResolver.resolve(binding.resultHandle(), context);
            validateResolvedBinding(binding, result);
            inputs.put(binding.alias(), result);
        }
        return inputs;
    }

    private void validateResolvedBinding(MemoryGridInputBinding binding,
                                         MemoryGridResultResolver.ResolvedResult result) {
        if (result == null) {
            throw RX.throwB(RESULT_HANDLE_NOT_FOUND + ": " + binding.resultHandle());
        }
        if (!binding.resultHandle().equals(result.resultHandle())) {
            throw RX.throwB(RESULT_GOVERNANCE_MISMATCH + ": resolver returned mismatched result_handle.");
        }
        if (result.schema() == null || result.schema().isEmpty()) {
            throw RX.throwB(RESULT_SCHEMA_MISMATCH + ": resolver schema is missing for " + binding.resultHandle() + ".");
        }
        if (result.rows() == null) {
            throw RX.throwB(RESULT_GOVERNANCE_MISMATCH + ": resolver rows are missing for " + binding.resultHandle() + ".");
        }
        String actualRoute = normalize(result.sourceRoute());
        String expectedRoute = normalize(binding.sourceRoute());
        if (actualRoute != null && expectedRoute != null && !actualRoute.equals(expectedRoute)) {
            throw RX.throwB(RESULT_GOVERNANCE_MISMATCH
                    + ": resolver source_route does not match binding for " + binding.resultHandle() + ".");
        }
    }

    private void harden(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET enable_external_access=false");
        }
    }

    private void registerTable(Connection connection,
                               String alias,
                               MemoryGridResultResolver.ResolvedResult result) throws SQLException {
        List<String> columns = List.copyOf(result.schema().keySet());
        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSql(alias, columns, result.schema()));
        }
        if (result.rows().isEmpty()) {
            return;
        }
        String insertSql = insertSql(alias, columns.size());
        try (PreparedStatement statement = connection.prepareStatement(insertSql)) {
            for (Map<String, Object> row : result.rows()) {
                for (int index = 0; index < columns.size(); index++) {
                    statement.setObject(index + 1, row == null ? null : row.get(columns.get(index)));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private String createTableSql(String alias,
                                  List<String> columns,
                                  Map<String, MemoryGridResultResolver.Column> schema) {
        StringBuilder sql = new StringBuilder("CREATE TEMP TABLE ");
        sql.append(quoteIdentifier(alias)).append(" (");
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                sql.append(", ");
            }
            MemoryGridResultResolver.Column column = schema.get(columns.get(index));
            sql.append(quoteIdentifier(columns.get(index))).append(' ').append(sqlType(column == null ? null : column.type()));
        }
        sql.append(')');
        return sql.toString();
    }

    private String insertSql(String alias, int columnCount) {
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(quoteIdentifier(alias)).append(" VALUES (");
        for (int index = 0; index < columnCount; index++) {
            if (index > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
        sql.append(')');
        return sql.toString();
    }

    private QueryRows query(Connection connection, String gridSql, int outputLimit) throws SQLException {
        String sql = "SELECT * FROM (" + stripTrailingSemicolon(gridSql) + ") AS foggy_grid_sql_result LIMIT "
                + (outputLimit + 1);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                rows.add(row(resultSet, metaData));
            }
        }
        boolean truncated = rows.size() > outputLimit;
        if (truncated) {
            rows = new ArrayList<>(rows.subList(0, outputLimit));
        }
        return new QueryRows(rows, truncated);
    }

    private Map<String, Object> row(ResultSet resultSet, ResultSetMetaData metaData) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            String label = metaData.getColumnLabel(index);
            if (label == null || label.isBlank()) {
                label = metaData.getColumnName(index);
            }
            String key = uniqueKey(row, label, index);
            row.put(key, resultSet.getObject(index));
        }
        return row;
    }

    private Map<String, Object> executionSummary(Map<String, MemoryGridResultResolver.ResolvedResult> inputs,
                                                 QueryRows queryRows,
                                                 int outputLimit) {
        Map<String, Integer> inputRows = new LinkedHashMap<>();
        Map<String, Object> handleAudit = new LinkedHashMap<>();
        for (Map.Entry<String, MemoryGridResultResolver.ResolvedResult> entry : inputs.entrySet()) {
            MemoryGridResultResolver.ResolvedResult result = entry.getValue();
            inputRows.put(entry.getKey(), result.rows().size());
            handleAudit.put(entry.getKey(), audit(result));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("memory_grid_engine", dialect().engineId());
        summary.put("memory_grid_dialect", dialect().dialectId());
        summary.put("grid_sql_dialect", "foggy-grid-sql-v1");
        summary.put("grid_sql_runtime", "duckdb");
        summary.put("aliases", List.copyOf(inputs.keySet()));
        summary.put("input_row_count", inputRows);
        summary.put("output_row_count", queryRows.rows().size());
        summary.put("output_limit", outputLimit);
        summary.put("output_truncated", queryRows.truncated());
        summary.put("result_handle_audit", handleAudit);
        return summary;
    }

    private Map<String, Object> audit(MemoryGridResultResolver.ResolvedResult result) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("result_handle", result.resultHandle());
        audit.put("source_route", result.sourceRoute());
        audit.put("namespace", result.namespace());
        audit.put("row_count", result.rows().size());
        MemoryGridResultResolver.ResultHandleMetadata metadata = result.metadata();
        if (metadata != null) {
            audit.put("query_hash", metadata.queryHash());
            audit.put("source_model_refs", metadata.sourceModelRefs());
            audit.put("expires_at", metadata.expiresAt() == null ? null : metadata.expiresAt().toString());
            audit.put("read_count", metadata.readCount());
            audit.put("storage_ref_redacted", metadata.storageRef() != null && !metadata.storageRef().isBlank());
        }
        return audit;
    }

    private void appendOutputLimitEvidence(Map<String, Object> evidence, MemoryGridRequest request) {
        int requested = requestedOutputLimit(request);
        int outputLimit = Math.min(requested, maxOutputLimit);
        evidence.put("output_limit", outputLimit);
        if (requested != outputLimit) {
            evidence.put("requested_output_limit", requested);
            evidence.put("output_limit_capped", true);
        }
    }

    private int outputLimit(MemoryGridRequest request) {
        return Math.min(requestedOutputLimit(request), maxOutputLimit);
    }

    private int requestedOutputLimit(MemoryGridRequest request) {
        Object value = request.hints().get("outputLimit");
        if (value == null) {
            return defaultOutputLimit;
        }
        if (value instanceof Number number) {
            int limit = number.intValue();
            if (limit > 0) {
                return limit;
            }
        }
        if (value instanceof String text) {
            try {
                int limit = Integer.parseInt(text.trim());
                if (limit > 0) {
                    return limit;
                }
            } catch (NumberFormatException ignored) {
                // handled below
            }
        }
        throw RX.throwB(OUTPUT_LIMIT_INVALID + ": outputLimit must be a positive integer.");
    }

    private String sqlType(String type) {
        if (type == null) {
            return "VARCHAR";
        }
        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "number", "decimal", "double", "float" -> "DOUBLE";
            case "integer", "int", "long", "bigint" -> "BIGINT";
            case "boolean", "bool" -> "BOOLEAN";
            case "date", "datetime", "timestamp" -> "TIMESTAMP";
            default -> "VARCHAR";
        };
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String stripTrailingSemicolon(String sql) {
        String stripped = sql == null ? "" : sql.trim();
        while (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        return stripped;
    }

    private String uniqueKey(Map<String, Object> row, String label, int index) {
        String key = label == null || label.isBlank() ? "column_" + index : label;
        if (!row.containsKey(key)) {
            return key;
        }
        int suffix = 2;
        while (row.containsKey(key + "_" + suffix)) {
            suffix++;
        }
        return key + "_" + suffix;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private record QueryRows(List<Map<String, Object>> rows, boolean truncated) {
    }
}

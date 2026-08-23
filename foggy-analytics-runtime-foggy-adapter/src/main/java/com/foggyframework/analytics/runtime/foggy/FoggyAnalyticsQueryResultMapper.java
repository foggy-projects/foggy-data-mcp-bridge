package com.foggyframework.analytics.runtime.foggy;

import com.foggyframework.analytics.definition.api.AnalyticsColumnSchema;
import com.foggyframework.analytics.definition.api.AnalyticsQuerySpec;
import com.foggyframework.analytics.runtime.core.query.QueryExecutionResult;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse;
import com.foggyframework.dataset.model.semantic.domain.SemanticQueryResponse.SchemaInfo.ColumnDef;
import com.foggyframework.dataset.model.spi.DbColumnType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.QUERY_NOT_EXECUTED;
import static com.foggyframework.analytics.runtime.foggy.FoggyAnalyticsAdapterException.Code.QUERY_SCHEMA_INVALID;

/** Security-conscious projection from an engine response to a bounded RenderModel result. */
final class FoggyAnalyticsQueryResultMapper {

    private static final String GENERIC_WARNING = "FOGGY_QUERY_WARNING";

    QueryExecutionResult map(
            AnalyticsQuerySpec querySpec,
            SemanticQueryResponse response,
            int rowLimit) {
        rejectNonExecutedResponse(response);
        Map<String, ColumnDef> schemaByName = schemaByName(response);
        List<AnalyticsColumnSchema> columns = querySpec.columns().stream()
                .map(name -> columnSchema(name, schemaByName.get(name)))
                .toList();

        List<Map<String, Object>> sourceRows = response.getItems() == null
                ? List.of()
                : response.getItems();
        int boundedSize = Math.min(sourceRows.size(), rowLimit);
        List<Map<String, Object>> rows = new ArrayList<>(boundedSize);
        for (int index = 0; index < boundedSize; index++) {
            Map<String, Object> source = sourceRows.get(index);
            if (source == null) {
                throw failure(QUERY_SCHEMA_INVALID, "Foggy query returned a null row");
            }
            Map<String, Object> projected = new LinkedHashMap<>();
            for (String column : querySpec.columns()) {
                projected.put(column, source.get(column));
            }
            rows.add(projected);
        }

        boolean truncated = sourceRows.size() > rowLimit
                || Boolean.TRUE.equals(response.getHasNext())
                || response.getPagination() != null
                && Boolean.TRUE.equals(response.getPagination().getHasMore())
                || response.getTruncationInfo() != null
                && !response.getTruncationInfo().isEmpty();
        List<String> diagnostics = response.getWarnings() == null
                || response.getWarnings().isEmpty()
                ? List.of()
                : List.of(GENERIC_WARNING);
        return new QueryExecutionResult(columns, rows, truncated, diagnostics);
    }

    private static Map<String, ColumnDef> schemaByName(SemanticQueryResponse response) {
        if (response.getSchema() == null || response.getSchema().getColumns() == null) {
            return Map.of();
        }
        Map<String, ColumnDef> result = new LinkedHashMap<>();
        for (ColumnDef column : response.getSchema().getColumns()) {
            if (column == null || column.getName() == null || column.getName().isBlank()) {
                throw failure(QUERY_SCHEMA_INVALID, "Foggy query returned invalid column metadata");
            }
            String name = column.getName().trim();
            if (!name.equals(column.getName()) || result.put(name, column) != null) {
                throw failure(QUERY_SCHEMA_INVALID, "Foggy query returned ambiguous column metadata");
            }
        }
        return result;
    }

    private static AnalyticsColumnSchema columnSchema(String name, ColumnDef column) {
        DbColumnType type = column == null ? null : column.getDataType();
        return new AnalyticsColumnSchema(name, analyticsType(type), true);
    }

    private static String analyticsType(DbColumnType type) {
        if (type == null) {
            return "unknown";
        }
        return switch (type) {
            case MONEY, NUMBER -> "decimal";
            case INTEGER -> "integer";
            case BIGINT -> "long";
            case DAY -> "date";
            case DATETIME -> "datetime";
            case TEXT, STRING -> "string";
            case BOOL -> "boolean";
            case DICT -> "dictionary";
            case VECTOR -> "array";
            case UNKNOWN -> "unknown";
        };
    }

    private static void rejectNonExecutedResponse(SemanticQueryResponse response) {
        SemanticQueryResponse.ExecutionInfo execution = response.getExecution();
        if (execution == null) {
            return;
        }
        if (hasValue(execution.getErrorCode())
                || "REJECT".equalsIgnoreCase(execution.getRoute())
                || "CLARIFY".equalsIgnoreCase(execution.getRoute())
                || "REJECT".equalsIgnoreCase(execution.getStatus())
                || "CLARIFY".equalsIgnoreCase(execution.getStatus())
                || "COMPILE_ERROR".equalsIgnoreCase(execution.getStatus())) {
            throw failure(QUERY_NOT_EXECUTED, "Foggy query did not reach an executable result");
        }
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private static FoggyAnalyticsAdapterException failure(
            FoggyAnalyticsAdapterException.Code code,
            String message) {
        return new FoggyAnalyticsAdapterException(code, message);
    }
}

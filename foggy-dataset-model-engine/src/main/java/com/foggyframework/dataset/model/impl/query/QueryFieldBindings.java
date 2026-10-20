package com.foggyframework.dataset.model.impl.query;

import com.foggyframework.dataset.db.dialect.FDialect;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.model.spi.DbQueryColumn;
import com.foggyframework.dataset.model.spi.QueryModel;

import java.util.*;

/** Query identity to output identity at projection boundaries; aliases are not input synonyms. */
public final class QueryFieldBindings {
    private QueryFieldBindings() { }

    /** Source is present for a model column; calculated/AS expressions retain their expression instead. */
    public record Binding(String queryName, String expression, String outputName, DbQueryColumn source) { }

    public record Projection(List<Binding> bindings) {
        public Projection { bindings = List.copyOf(bindings); }

        public List<Map<String, Object>> logicalRows(List<Map<String, Object>> rows) {
            if (rows == null) return List.of();
            List<Map<String, Object>> result = new ArrayList<>(rows.size());
            for (var row : rows) {
                Map<String, Object> mapped = new LinkedHashMap<>();
                for (Binding binding : bindings) mapped.put(binding.queryName(), row.get(binding.outputName()));
                result.add(mapped);
            }
            return result;
        }

        public String logicalSql(String sql, FDialect dialect) {
            if (bindings.stream().allMatch(b -> b.queryName().equals(b.outputName()))) return sql;
            List<String> columns = new ArrayList<>();
            for (Binding binding : bindings) columns.add("fb." + dialect.quoteIdentifier(binding.outputName())
                    + " AS " + dialect.quoteIdentifier(binding.queryName()));
            return "SELECT " + String.join(", ", columns) + " FROM (" + sql + ") fb";
        }
    }

    public static Binding bind(QueryModel model, String field, List<CalculatedFieldDef> calculated) {
        return bind(model, field, calculatedIndex(calculated));
    }

    private static Binding bind(QueryModel model, String field, Map<String, CalculatedFieldDef> calculated) {
        var parts = AliasExtractor.extract(field);
        if (parts.hasAlias()) return new Binding(field, parts.expression(), parts.outputName(), null);
        var calc = calculated.get(field);
        if (calc != null) {
            String output = calc.getAlias() == null || calc.getAlias().isBlank() ? field : calc.getAlias();
            return new Binding(field, field, output, null);
        }
        var column = model == null ? null : model.findJdbcColumnForSelectByName(field, false);
        String output = column == null || column.getAlias() == null ? field : column.getAlias();
        return new Binding(field, field, output, column);
    }

    private static Map<String, CalculatedFieldDef> calculatedIndex(List<CalculatedFieldDef> calculated) {
        Map<String, CalculatedFieldDef> fields = new LinkedHashMap<>();
        if (calculated != null) for (var field : calculated) fields.putIfAbsent(field.getName(), field);
        return fields;
    }

    public static Projection compile(QueryModel model, List<String> fields, List<CalculatedFieldDef> calculated) {
        var index = calculatedIndex(calculated);
        List<Binding> bindings = new ArrayList<>(fields.size());
        for (String field : fields) bindings.add(bind(model, field, index));
        return new Projection(bindings);
    }

    public static String outputName(QueryModel model, String field, List<CalculatedFieldDef> calculated) {
        return bind(model, field, calculated).outputName();
    }

    public static List<Map<String, Object>> logicalRows(List<Map<String, Object>> rows, QueryModel model,
                                                       List<String> fields, List<CalculatedFieldDef> calculated) {
        if (rows == null) return List.of();
        if (rows.isEmpty()) return new ArrayList<>();
        return compile(model, fields, calculated).logicalRows(rows);
    }

    public static String logicalSql(String sql, FDialect dialect, QueryModel model,
                                    List<String> fields, List<CalculatedFieldDef> calculated) {
        return compile(model, fields, calculated).logicalSql(sql, dialect);
    }
}

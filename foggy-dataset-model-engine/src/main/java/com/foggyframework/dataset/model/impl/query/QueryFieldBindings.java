package com.foggyframework.dataset.model.impl.query;

import com.foggyframework.dataset.model.spi.QueryModel;
import com.foggyframework.dataset.model.def.query.request.CalculatedFieldDef;
import com.foggyframework.dataset.model.engine.compose.schema.AliasExtractor;
import com.foggyframework.dataset.db.dialect.FDialect;
import java.util.*;

/** Query identity to output identity at projection boundaries; aliases are not input synonyms. */
public final class QueryFieldBindings {
    private QueryFieldBindings() { }
    public static String outputName(QueryModel model, String field, List<CalculatedFieldDef> calculated) {
        var parts = AliasExtractor.extract(field);
        if (parts.hasAlias()) return parts.outputName();
        if (calculated != null) for (var calc : calculated) {
            if (field.equals(calc.getName())) return calc.getAlias() == null || calc.getAlias().isBlank() ? field : calc.getAlias();
        }
        if (model == null) return field;
        var column = model.findJdbcColumnForSelectByName(field, false);
        return column == null || column.getAlias() == null ? field : column.getAlias();
    }
    public static List<Map<String, Object>> logicalRows(List<Map<String, Object>> rows, QueryModel model,
                                                       List<String> fields, List<CalculatedFieldDef> calculated) {
        if (rows == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (var row : rows) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            for (String field : fields) mapped.put(field, row.get(outputName(model, field, calculated)));
            result.add(mapped);
        }
        return result;
    }
    public static String logicalSql(String sql, FDialect dialect, QueryModel model,
                                    List<String> fields, List<CalculatedFieldDef> calculated) {
        if (fields.stream().allMatch(f -> f.equals(outputName(model, f, calculated)))) return sql;
        List<String> projection = new ArrayList<>();
        for (String field : fields) projection.add("fb." + dialect.quoteIdentifier(outputName(model, field, calculated))
                + " AS " + dialect.quoteIdentifier(field));
        return "SELECT " + String.join(", ", projection) + " FROM (" + sql + ") fb";
    }
}

package com.foggyframework.dataset.model.engine.pivot.transport;

import com.foggyframework.dataset.db.dialect.FDialect;
import java.util.ArrayList;
import java.util.List;

public class SqliteCteDomainRenderer implements DomainRelationRenderer {

    @Override
    public DomainRelationRenderResult render(FDialect dialect, String databaseVersion, DomainTransportPlan plan) throws DomainTransportRefusalException {
        plan.validateForRender();

        int paramCount = plan.parameterCount();
        if (paramCount > 30000) {
            throw new DomainTransportRefusalException("SQLite bind parameter limit exceeded: " + paramCount);
        }

        StringBuilder sql = new StringBuilder();
        sql.append(plan.getRelationName()).append("(");
        List<String> fieldNames = new ArrayList<>();
        for (DomainTransportField field : plan.getFields()) {
            fieldNames.add(dialect.quoteIdentifier(field.getName()));
        }
        sql.append(String.join(", ", fieldNames));
        sql.append(") AS (\n  VALUES ");

        List<Object> params = new ArrayList<>();
        List<String> tupleStrings = new ArrayList<>();
        for (DomainTransportTuple tuple : plan.getTuples()) {
            List<String> placeholders = new ArrayList<>();
            for (Object val : tuple.getValues()) {
                placeholders.add("?");
                params.add(val);
            }
            tupleStrings.add("(" + String.join(", ", placeholders) + ")");
        }
        sql.append(String.join(",\n         ", tupleStrings));
        sql.append("\n)");

        StringBuilder joinPredicate = new StringBuilder();
        List<String> joinParts = new ArrayList<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            String col = fieldNames.get(i);
            // SQLite uses IS for null-safe equality
            joinParts.add("_base." + col + " IS _d." + col);
        }
        joinPredicate.append(String.join(" AND ", joinParts));

        return DomainRelationRenderResult.builder()
                .sqlFragment(sql.toString())
                .params(params)
                .joinPredicate(joinPredicate.toString())
                .placement(DomainTransportPlacement.CTE)
                .build();
    }
}
